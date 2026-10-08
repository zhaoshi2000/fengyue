package local.aquantancee;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CardService {
    private final JdbcTemplate jdbc;
    private static final List<String> FIELDS = List.of("personality", "scenario", "firstMessage", "exampleDialogue", "authorCss", "backgroundUrl", "quickReplies");

    public CardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> get(String itemId) {
        List<Map<String, Object>> rows = jdbc.query("SELECT personality,scenario,first_message,example_dialogue,author_css,background_url,quick_replies FROM item_cards WHERE item_id=?",
            (rs, row) -> {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("personality", rs.getString("personality"));
                card.put("scenario", rs.getString("scenario"));
                card.put("firstMessage", rs.getString("first_message"));
                card.put("exampleDialogue", rs.getString("example_dialogue"));
                card.put("authorCss", rs.getString("author_css"));
                card.put("backgroundUrl", rs.getString("background_url"));
                card.put("quickReplies", rs.getString("quick_replies"));
                return card;
            }, itemId);
        Map<String, Object> card = rows.isEmpty() ? new LinkedHashMap<>() : rows.getFirst();
        if (rows.isEmpty()) for (String field : FIELDS) card.put(field, "");
        card.put("worldEntries", jdbc.query("SELECT title,keywords,content,enabled FROM item_world_entries WHERE item_id=? ORDER BY position",
            (rs, row) -> Map.<String, Object>of("title", rs.getString("title"), "keywords", rs.getString("keywords"),
                "content", rs.getString("content"), "enabled", rs.getBoolean("enabled")), itemId));
        return card;
    }

    public void save(String itemId, Map<String, Object> body) {
        if (FIELDS.stream().noneMatch(body::containsKey) && !body.containsKey("worldEntries")) return;
        if (FIELDS.stream().anyMatch(body::containsKey)) saveCard(itemId, body);
        if (body.containsKey("worldEntries")) saveWorldEntries(itemId, body.get("worldEntries"));
    }

    private void saveCard(String itemId, Map<String, Object> body) {
        String personality = value(body, "personality", 4000);
        String scenario = value(body, "scenario", 4000);
        String firstMessage = value(body, "firstMessage", 3000);
        String exampleDialogue = value(body, "exampleDialogue", 4000);
        String authorCss = value(body, "authorCss", 12000);
        String backgroundUrl = value(body, "backgroundUrl", 500);
        String quickReplies = value(body, "quickReplies", 1000);
        if (!backgroundUrl.isEmpty() && !backgroundUrl.matches("https://[^\\s<>'\"]+")
                && !backgroundUrl.matches("/media/[0-9a-f-]{36}\\.(png|jpg)"))
            throw new ApiException(400, "背景图须为已上传图片或 HTTPS 地址");
        if (authorCss.matches("(?is).*<\\s*/?\\s*(script|style|iframe|html|body).*"))
            throw new ApiException(400, "自定义样式仅支持 CSS，不支持 HTML 或脚本");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM item_cards WHERE item_id=?", Integer.class, itemId) == 0) {
            jdbc.update("INSERT INTO item_cards(item_id,personality,scenario,first_message,example_dialogue,author_css,background_url,quick_replies,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                itemId, personality, scenario, firstMessage, exampleDialogue, authorCss, backgroundUrl, quickReplies, LocalDateTime.now());
        } else {
            jdbc.update("UPDATE item_cards SET personality=?,scenario=?,first_message=?,example_dialogue=?,author_css=?,background_url=?,quick_replies=?,updated_at=? WHERE item_id=?",
                personality, scenario, firstMessage, exampleDialogue, authorCss, backgroundUrl, quickReplies, LocalDateTime.now(), itemId);
        }
    }

    private void saveWorldEntries(String itemId, Object source) {
        if (!(source instanceof List<?> entries) || entries.size() > 30)
            throw new ApiException(400, "世界书最多支持 30 条设定");
        List<Map<String, Object>> clean = new ArrayList<>();
        int total = 0;
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> raw)) throw new ApiException(400, "世界书格式无效");
            String title = field(raw, "title", 80);
            String keywords = field(raw, "keywords", 200);
            String content = field(raw, "content", 3000);
            if (title.isEmpty() || content.isEmpty()) throw new ApiException(400, "世界书标题和内容不能为空");
            total += title.length() + keywords.length() + content.length();
            if (total > 12000) throw new ApiException(400, "世界书总内容不能超过 12000 字");
            clean.add(Map.of("title", title, "keywords", keywords, "content", content,
                "enabled", !Boolean.FALSE.equals(raw.get("enabled"))));
        }
        jdbc.update("DELETE FROM item_world_entries WHERE item_id=?", itemId);
        for (int position = 0; position < clean.size(); position++) {
            Map<String, Object> entry = clean.get(position);
            jdbc.update("INSERT INTO item_world_entries(item_id,position,title,keywords,content,enabled) VALUES(?,?,?,?,?,?)",
                itemId, position, entry.get("title"), entry.get("keywords"), entry.get("content"), entry.get("enabled"));
        }
    }

    private String field(Map<?, ?> raw, String name, int max) {
        Object source = raw.get(name);
        if (source != null && !(source instanceof String)) throw new ApiException(400, "世界书格式无效");
        String value = source == null ? "" : ((String) source).trim();
        if (value.length() > max) throw new ApiException(400, "世界书" + name + "内容过长");
        return value;
    }

    private String value(Map<String, Object> body, String field, int max) {
        String value = AuthService.text(body, field).trim();
        if (value.length() > max) throw new ApiException(400, field + " 内容过长");
        return value;
    }
}
