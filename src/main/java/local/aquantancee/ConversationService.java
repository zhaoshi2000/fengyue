package local.aquantancee;

import java.time.LocalDateTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class ConversationService {
    private final JdbcTemplate jdbc;
    private final CardService cards;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final RestClient client;

    public ConversationService(JdbcTemplate jdbc, CardService cards,
            @Value("${AI_API_KEY:}") String apiKey,
            @Value("${AI_API_URL:https://api.deepseek.com/chat/completions}") String apiUrl,
            @Value("${AI_MODEL:deepseek-flash}") String model) {
        this.jdbc = jdbc;
        this.cards = cards;
        this.apiKey = apiKey.trim();
        this.apiUrl = apiUrl.trim();
        this.model = model.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(90_000);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public Map<String, Object> config() {
        return Map.of("mode", apiKey.isEmpty() ? "demo" : "model", "model", apiKey.isEmpty() ? "本地演示" : model);
    }

    private record Conversation(String id, String userId, String itemId, String title, String itemTitle, String summary,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {}
    private record Reply(String content, String model, Integer promptTokens, Integer completionTokens, Integer totalTokens) {}

    private Conversation owned(String id, AuthService.User user) {
        List<Conversation> rows = jdbc.query("SELECT c.*,i.title AS item_title,i.summary FROM conversations c JOIN items i ON i.id=c.item_id WHERE c.id=? AND c.user_id=?",
                (rs, row) -> new Conversation(rs.getString("id"), rs.getString("user_id"), rs.getString("item_id"),
                    rs.getString("title"), rs.getString("item_title"), rs.getString("summary"),
                    rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class)), id, user.id());
        if (rows.isEmpty()) throw new ApiException(404, "会话不存在");
        return rows.getFirst();
    }

    private Map<String, Object> json(Conversation c) {
        return Map.of("id", c.id(), "itemId", c.itemId(), "title", c.title(),
            "createdAt", c.createdAt().toString(), "updatedAt", c.updatedAt().toString());
    }

    public Map<String, Object> list(String itemId, AuthService.User user) {
        if (jdbc.queryForList("SELECT id FROM items WHERE id=?", String.class, itemId).isEmpty()) throw new ApiException(404, "内容不存在");
        List<Map<String, Object>> rows = jdbc.query("SELECT c.*,i.title AS item_title,i.summary FROM conversations c JOIN items i ON i.id=c.item_id WHERE c.user_id=? AND c.item_id=? ORDER BY c.updated_at DESC LIMIT 100",
                (rs, row) -> json(new Conversation(rs.getString("id"), rs.getString("user_id"), rs.getString("item_id"),
                    rs.getString("title"), rs.getString("item_title"), rs.getString("summary"),
                    rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class))), user.id(), itemId);
        return Map.of("conversations", rows);
    }

    @Transactional
    public Map<String, Object> create(String itemId, AuthService.User user) {
        if (jdbc.queryForList("SELECT id FROM items WHERE id=?", String.class, itemId).isEmpty()) throw new ApiException(404, "内容不存在");
        String id = UUID.randomUUID().toString(); LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO conversations(id,user_id,item_id,title,created_at,updated_at) VALUES(?,?,?,'新对话',?,?)",
                id, user.id(), itemId, now, now);
        String greeting = String.valueOf(cards.get(itemId).get("firstMessage"));
        if (!greeting.isBlank()) jdbc.update("INSERT INTO conversation_messages(id,conversation_id,role,content,created_at) VALUES(?,?,'assistant',?,?)",
                UUID.randomUUID().toString(), id, greeting, now);
        return Map.of("conversation", json(owned(id, user)));
    }

    public Map<String, Object> detail(String id, AuthService.User user) {
        Conversation c = owned(id, user);
        List<Map<String, Object>> messages = jdbc.query("""
                SELECT m.id,m.role,m.content,m.created_at,u.model,u.prompt_tokens,u.completion_tokens,u.total_tokens
                FROM conversation_messages m LEFT JOIN conversation_message_usage u ON u.message_id=m.id
                WHERE m.conversation_id=? ORDER BY m.created_at,m.id LIMIT 200
                """, (rs, row) -> messageJson(rs), id);
        return Map.of("conversation", json(c), "messages", messages);
    }

    private Map<String, Object> messageJson(ResultSet rs) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", rs.getString("id"));
        value.put("role", rs.getString("role"));
        value.put("content", rs.getString("content"));
        value.put("createdAt", rs.getObject("created_at", LocalDateTime.class).toString());
        String usedModel = rs.getString("model");
        if (usedModel != null) {
            value.put("model", usedModel);
            Map<String, Object> usage = new LinkedHashMap<>();
            Integer prompt = (Integer) rs.getObject("prompt_tokens");
            Integer completion = (Integer) rs.getObject("completion_tokens");
            Integer total = (Integer) rs.getObject("total_tokens");
            if (prompt != null) usage.put("promptTokens", prompt);
            if (completion != null) usage.put("completionTokens", completion);
            if (total != null) usage.put("totalTokens", total);
            if (!usage.isEmpty()) value.put("usage", usage);
        }
        return value;
    }

    private void saveUsage(String messageId, Reply reply) {
        jdbc.update("DELETE FROM conversation_message_usage WHERE message_id=?", messageId);
        jdbc.update("INSERT INTO conversation_message_usage(message_id,model,prompt_tokens,completion_tokens,total_tokens) VALUES(?,?,?,?,?)",
                messageId, reply.model(), reply.promptTokens(), reply.completionTokens(), reply.totalTokens());
    }

    @Transactional
    public Map<String, Object> send(String id, Map<String, Object> body, AuthService.User user) {
        Conversation c = owned(id, user);
        String message = AuthService.text(body, "message").trim();
        if (message.isEmpty() || message.length() > 2000) throw new ApiException(400, "消息长度须为 1–2000 字");
        List<Map<String, Object>> prior = jdbc.query("SELECT role,content FROM conversation_messages WHERE conversation_id=? ORDER BY created_at DESC,id DESC LIMIT 20",
                (rs, row) -> Map.of("role", rs.getString("role"), "content", rs.getString("content")), id);
        java.util.Collections.reverse(prior);
        Reply generated = apiKey.isEmpty() ? new Reply(demoReply(c, message, prior.size(), false), "本地演示", null, null, null)
                : modelReply(c, message, prior);
        String reply = generated.content();
        if (reply.length() > 8000) reply = reply.substring(0, 8000);
        LocalDateTime now = LocalDateTime.now();
        String userMessageId = UUID.randomUUID().toString(), replyId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO conversation_messages(id,conversation_id,role,content,created_at) VALUES(?,?,'user',?,?)", userMessageId, id, message, now);
        jdbc.update("INSERT INTO conversation_messages(id,conversation_id,role,content,created_at) VALUES(?,?,'assistant',?,?)", replyId, id, reply, now.plusNanos(1_000_000));
        saveUsage(replyId, generated);
        jdbc.update("UPDATE conversations SET title=IF(title='新对话',?,title),updated_at=? WHERE id=?",
                message.length() > 28 ? message.substring(0, 28) + "…" : message, now, id);
        return Map.of("conversation", json(owned(id, user)), "messages", List.of(
            Map.of("id", userMessageId, "role", "user", "content", message, "createdAt", now.toString()),
            Map.of("id", replyId, "role", "assistant", "content", reply, "createdAt", now.plusNanos(1_000_000).toString(), "model", generated.model())));
    }

    private String demoReply(Conversation c, String message, int priorCount, boolean alternate) {
        if (alternate) return "故事换了一个角度展开。你刚才提到“" + message.substring(0, Math.min(28, message.length())) + "”，周围的线索也随之发生变化。你想先观察眼前的人，还是探索新的地点？";
        if (c.itemId().equals("6a46cbbf-a5f5-47fa-8568-44903607d0bf")) {
            if (message.contains("信")) return "信纸边缘留着一枚细小的印记，像是一朵未开的花。字迹只写着：‘今夜，旧事会有答案。’你打算沿着印记追查吗？";
            if (message.contains("人") || message.contains("谁")) return "廊下传来脚步声，一位守夜人停在灯影外。他似乎知道更多，却在等你先开口。你想问他什么？";
        }
        if (priorCount == 0) return "《" + c.itemTitle() + "》的故事从这里继续。" + c.summary() + "你准备先做什么？";
        return "你的选择让情节有了新的变化。" + c.summary() + "接下来你想继续追查，还是换个方向？";
    }

    @SuppressWarnings("unchecked")
    private Reply modelReply(Conversation c, String message, List<Map<String, Object>> prior) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> card = cards.get(c.itemId());
        messages.add(Map.of("role", "system", "content", "你是互动中文故事《" + c.itemTitle() + "》的叙事助手。故事简介：" + c.summary()
            + "。人物设定：" + card.get("personality") + "。场景与世界观：" + card.get("scenario")
            + "。对话示例：" + card.get("exampleDialogue")
            + "。用生动的中文推动情节，尊重用户选择，每次回复留一个可继续互动的线索。若角色卡包含任务、属性或状态系统，可用 Markdown 标题、列表、分隔线展示状态，并用 :::details 标题、内容、::: 单独一行的格式提供可折叠的背景资料。普通故事保持简洁。不要输出 HTML 或 CSS。避免露骨性内容和性暴力描写。"));
        messages.addAll(prior);
        messages.add(Map.of("role", "user", "content", message));
        try {
            Map<String, Object> result = client.post().uri(apiUrl).header("Authorization", "Bearer " + apiKey)
                .body(Map.of("model", model, "messages", messages, "stream", false))
                .retrieve().body(Map.class);
            if (result != null && result.get("choices") instanceof List<?> choices && !choices.isEmpty()
                    && choices.getFirst() instanceof Map<?, ?> choice
                    && choice.get("message") instanceof Map<?, ?> response
                    && response.get("content") instanceof String content && !content.isBlank()) {
                Map<?, ?> usage = result.get("usage") instanceof Map<?, ?> values ? values : Map.of();
                String returnedModel = result.get("model") instanceof String value && !value.isBlank() ? value : model;
                return new Reply(content.trim(), returnedModel.substring(0, Math.min(returnedModel.length(), 100)),
                    tokens(usage.get("prompt_tokens")), tokens(usage.get("completion_tokens")), tokens(usage.get("total_tokens")));
            }
            throw new ApiException(502, "模型没有返回可用内容");
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw new ApiException(502, "模型服务暂时不可用，请稍后重试"); }
    }

    private Integer tokens(Object value) {
        return value instanceof Number number && number.longValue() >= 0 && number.longValue() <= Integer.MAX_VALUE
            ? number.intValue() : null;
    }

    public void delete(String id, AuthService.User user) {
        owned(id, user);
        jdbc.update("DELETE FROM conversations WHERE id=? AND user_id=?", id, user.id());
    }

    @Transactional
    public Map<String, Object> editMessage(String conversationId, String messageId, Map<String, Object> body, AuthService.User user) {
        owned(conversationId, user);
        String content = AuthService.text(body, "content").trim();
        if (content.isEmpty() || content.length() > 8000) throw new ApiException(400, "消息长度须为 1–8000 字");
        int changed = jdbc.update("UPDATE conversation_messages SET content=? WHERE id=? AND conversation_id=?", content, messageId, conversationId);
        if (changed == 0) throw new ApiException(404, "消息不存在");
        jdbc.update("DELETE FROM conversation_message_usage WHERE message_id=?", messageId);
        jdbc.update("UPDATE conversations SET updated_at=? WHERE id=?", LocalDateTime.now(), conversationId);
        return detail(conversationId, user);
    }

    @Transactional
    public Map<String, Object> deleteMessage(String conversationId, String messageId, AuthService.User user) {
        owned(conversationId, user);
        int changed = jdbc.update("DELETE FROM conversation_messages WHERE id=? AND conversation_id=?", messageId, conversationId);
        if (changed == 0) throw new ApiException(404, "消息不存在");
        jdbc.update("UPDATE conversations SET updated_at=? WHERE id=?", LocalDateTime.now(), conversationId);
        return detail(conversationId, user);
    }

    @Transactional
    public Map<String, Object> regenerate(String conversationId, AuthService.User user) {
        Conversation c = owned(conversationId, user);
        List<Map<String, Object>> assistant = jdbc.query("SELECT id,content,created_at FROM conversation_messages WHERE conversation_id=? AND role='assistant' ORDER BY created_at DESC,id DESC LIMIT 1",
            (rs, row) -> Map.of("id", rs.getString("id"), "content", rs.getString("content"), "createdAt", rs.getObject("created_at", LocalDateTime.class)), conversationId);
        if (assistant.isEmpty()) throw new ApiException(409, "还没有可重新生成的回复");
        LocalDateTime assistantTime = (LocalDateTime) assistant.getFirst().get("createdAt");
        List<Map<String, Object>> latestUser = jdbc.query("SELECT id,content,created_at FROM conversation_messages WHERE conversation_id=? AND role='user' AND created_at<? ORDER BY created_at DESC,id DESC LIMIT 1",
            (rs, row) -> Map.of("id", rs.getString("id"), "content", rs.getString("content"), "createdAt", rs.getObject("created_at", LocalDateTime.class)), conversationId, assistantTime);
        if (latestUser.isEmpty()) throw new ApiException(409, "开场白无需重新生成");
        LocalDateTime userTime = (LocalDateTime) latestUser.getFirst().get("createdAt");
        List<Map<String, Object>> prior = jdbc.query("SELECT role,content FROM conversation_messages WHERE conversation_id=? AND created_at<? ORDER BY created_at DESC,id DESC LIMIT 20",
            (rs, row) -> Map.of("role", rs.getString("role"), "content", rs.getString("content")), conversationId, userTime);
        java.util.Collections.reverse(prior);
        String message = String.valueOf(latestUser.getFirst().get("content"));
        Reply generated = apiKey.isEmpty() ? new Reply(demoReply(c, message, prior.size(), true), "本地演示", null, null, null)
                : modelReply(c, message, prior);
        String reply = generated.content();
        if (reply.length() > 8000) reply = reply.substring(0, 8000);
        jdbc.update("UPDATE conversation_messages SET content=? WHERE id=? AND conversation_id=?", reply, assistant.getFirst().get("id"), conversationId);
        saveUsage(String.valueOf(assistant.getFirst().get("id")), generated);
        jdbc.update("UPDATE conversations SET updated_at=? WHERE id=?", LocalDateTime.now(), conversationId);
        return detail(conversationId, user);
    }
}
