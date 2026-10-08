package local.aquantancee;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryService {
    public static final List<String> CATEGORIES = List.of("推荐", "纯聊", "幻想", "剧情", "治愈", "冒险", "校园");
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final CardService cards;

    public StoryService(JdbcTemplate jdbc, AuthService auth, CardService cards) { this.jdbc = jdbc; this.auth = auth; this.cards = cards; }

    private record Item(String id, String authorId, String author, String title, String category, String summary,
                        String icon, String theme, long views, long likes, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    private Item mapItem(ResultSet rs, int row) throws SQLException {
        return new Item(rs.getString("id"), rs.getString("author_id"), rs.getString("author"), rs.getString("title"),
                rs.getString("category"), rs.getString("summary"), rs.getString("icon"), rs.getString("theme"),
                rs.getLong("views"), rs.getLong("likes"), rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
    }

    private Item item(String id) {
        List<Item> rows = jdbc.query("SELECT * FROM items WHERE id=?", this::mapItem, id);
        if (rows.isEmpty()) throw new ApiException(404, "内容不存在");
        return rows.getFirst();
    }

    private boolean exists(String sql, Object... args) { return !jdbc.queryForList(sql, args).isEmpty(); }

    private Map<String, Object> itemJson(Item item, AuthService.User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.id()); result.put("title", item.title()); result.put("author", item.author());
        result.put("category", item.category()); result.put("summary", item.summary()); result.put("icon", item.icon());
        result.put("theme", item.theme()); result.put("views", item.views()); result.put("likes", item.likes());
        result.put("createdAt", item.createdAt().toString()); result.put("updatedAt", item.updatedAt().toString());
        result.put("favorite", user != null && exists("SELECT 1 FROM favorites WHERE user_id=? AND item_id=?", user.id(), item.id()));
        result.put("following", user != null && exists("SELECT 1 FROM follows WHERE user_id=? AND author=?", user.id(), item.author()));
        result.put("editable", user != null && user.id().equals(item.authorId()));
        result.put("card", cards.get(item.id()));
        return result;
    }

    public Map<String, Object> bootstrap(AuthService.User user) {
        List<Map<String, Object>> featured = jdbc.query("SELECT * FROM items ORDER BY likes DESC, views DESC LIMIT 4", this::mapItem)
                .stream().map(item -> itemJson(item, user)).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", CATEGORIES); result.put("featured", featured); result.put("user", auth.publicUser(user));
        return result;
    }

    public Map<String, Object> list(String query, String category, String sort, String view, int page, int limit, AuthService.User user) {
        if (!CATEGORIES.contains(category) || !List.of("recommended", "latest", "popular", "liked").contains(sort)
                || !List.of("", "favorites", "following", "history").contains(view)) throw new ApiException(400, "筛选参数无效");
        if (page < 1 || page > 10_000 || limit < 1 || limit > 100) throw new ApiException(400, "分页参数无效");
        if (!view.isEmpty() && user == null) throw new ApiException(401, "请先登录");
        StringBuilder from = new StringBuilder(" FROM items i");
        List<Object> args = new ArrayList<>();
        switch (view) {
            case "favorites" -> { from.append(" JOIN favorites f ON f.item_id=i.id AND f.user_id=?"); args.add(user.id()); }
            case "following" -> { from.append(" JOIN follows fo ON fo.author=i.author AND fo.user_id=?"); args.add(user.id()); }
            case "history" -> { from.append(" JOIN history h ON h.item_id=i.id AND h.user_id=?"); args.add(user.id()); }
            default -> {}
        }
        List<String> filters = new ArrayList<>();
        if (!category.equals("推荐")) { filters.add("i.category=?"); args.add(category); }
        String q = query == null ? "" : query.trim();
        if (q.length() > 100) q = q.substring(0, 100);
        if (!q.isEmpty()) {
            filters.add("(LOCATE(?,i.title)>0 OR LOCATE(?,i.author)>0 OR LOCATE(?,i.summary)>0 OR LOCATE(?,i.category)>0)");
            for (int i = 0; i < 4; i++) args.add(q);
        }
        if (!filters.isEmpty()) from.append(" WHERE ").append(String.join(" AND ", filters));
        String order = switch (sort) {
            case "latest" -> "i.created_at DESC";
            case "popular" -> "i.views DESC, i.created_at DESC";
            case "liked" -> "i.likes DESC, i.created_at DESC";
            default -> view.equals("history") ? "h.visited_at DESC" : "i.likes + i.views / 25.0 DESC, i.created_at DESC";
        };
        int total = jdbc.queryForObject("SELECT COUNT(*)" + from, Integer.class, args.toArray());
        List<Object> paged = new ArrayList<>(args); paged.add(limit); paged.add((page - 1) * limit);
        List<Map<String, Object>> items = jdbc.query("SELECT i.*" + from + " ORDER BY " + order + " LIMIT ? OFFSET ?", this::mapItem, paged.toArray())
                .stream().map(item -> itemJson(item, user)).toList();
        return Map.of("items", items, "total", total, "page", page, "limit", limit, "hasMore", page * limit < total);
    }

    public Map<String, Object> detail(String id, AuthService.User user) { return Map.of("item", itemJson(item(id), user)); }

    private record Content(String title, String category, String summary, String icon) {}
    private Content content(Map<String, Object> body) {
        String title = AuthService.text(body, "title").trim(), summary = AuthService.text(body, "summary").trim();
        String category = AuthService.text(body, "category"), icon = AuthService.text(body, "icon").trim();
        if (title.length() < 2 || title.length() > 40 || summary.length() < 10 || summary.length() > 240 || !CATEGORIES.subList(1, CATEGORIES.size()).contains(category))
            throw new ApiException(400, "请填写标题、10–240 字简介和有效分区");
        return new Content(title, category, summary, icon.isEmpty() ? "✨" : icon.substring(0, Math.min(icon.length(), 4)));
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthService.User user) {
        if (user == null) throw new ApiException(401, "请先登录");
        Content content = content(body); String id = UUID.randomUUID().toString(); LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO items(id,author_id,author,title,category,summary,icon,theme,views,likes,created_at,updated_at) VALUES(?,?,?,?,?,?,?,'violet',0,0,?,?)",
                id, user.id(), user.name(), content.title(), content.category(), content.summary(), content.icon(), now, now);
        cards.save(id, body);
        return Map.of("item", itemJson(item(id), user));
    }

    private void owner(Item item, AuthService.User user) {
        if (user == null) throw new ApiException(401, "请先登录");
        if (!user.id().equals(item.authorId())) throw new ApiException(403, "只能修改自己发布的内容");
    }

    @Transactional
    public Map<String, Object> update(String id, Map<String, Object> body, AuthService.User user) {
        owner(item(id), user); Content content = content(body);
        jdbc.update("UPDATE items SET title=?,category=?,summary=?,icon=?,updated_at=? WHERE id=?",
                content.title(), content.category(), content.summary(), content.icon(), LocalDateTime.now(), id);
        cards.save(id, body);
        return Map.of("item", itemJson(item(id), user));
    }

    public void delete(String id, AuthService.User user) { owner(item(id), user); jdbc.update("DELETE FROM items WHERE id=?", id); }

    @Transactional
    public Map<String, Object> visit(String id, AuthService.User user) {
        item(id);
        jdbc.update("UPDATE items SET views=views+1 WHERE id=?", id);
        if (user != null) jdbc.update("INSERT INTO history(user_id,item_id,visited_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE visited_at=VALUES(visited_at)", user.id(), id, LocalDateTime.now());
        return Map.of("item", itemJson(item(id), user));
    }

    @Transactional
    public Map<String, Object> favorite(String id, AuthService.User user) {
        if (user == null) throw new ApiException(401, "请先登录");
        item(id);
        boolean found = exists("SELECT 1 FROM favorites WHERE user_id=? AND item_id=?", user.id(), id);
        if (found) jdbc.update("DELETE FROM favorites WHERE user_id=? AND item_id=?", user.id(), id);
        else jdbc.update("INSERT INTO favorites(user_id,item_id,created_at) VALUES(?,?,?)", user.id(), id, LocalDateTime.now());
        jdbc.update("UPDATE items SET likes=GREATEST(0,likes+?) WHERE id=?", found ? -1 : 1, id);
        return Map.of("item", itemJson(item(id), user), "user", auth.publicUser(user));
    }

    @Transactional
    public Map<String, Object> follow(String author, AuthService.User user) {
        if (user == null) throw new ApiException(401, "请先登录");
        if (!exists("SELECT 1 FROM items WHERE author=? LIMIT 1", author)) throw new ApiException(404, "作者不存在");
        boolean found = exists("SELECT 1 FROM follows WHERE user_id=? AND author=?", user.id(), author);
        if (found) jdbc.update("DELETE FROM follows WHERE user_id=? AND author=?", user.id(), author);
        else jdbc.update("INSERT INTO follows(user_id,author,created_at) VALUES(?,?,?)", user.id(), author, LocalDateTime.now());
        return Map.of("user", auth.publicUser(user), "following", !found);
    }

    public Map<String, Object> checkin(AuthService.User user) {
        if (user == null) throw new ApiException(401, "请先登录");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        int changed = jdbc.update("UPDATE users SET last_checkin=?,points=points+10 WHERE id=? AND (last_checkin IS NULL OR last_checkin<>?)", today, user.id(), today);
        if (changed == 0) throw new ApiException(409, "今天已经签到过了");
        return Map.of("user", auth.publicUser(auth.refresh(user.id())), "reward", 10);
    }

    public Map<String, Object> messages(String id, AuthService.User user, int limit) {
        if (user == null) throw new ApiException(401, "请先登录");
        item(id);
        if (limit < 1 || limit > 100) throw new ApiException(400, "分页参数无效");
        List<Map<String, Object>> messages = jdbc.query("SELECT id,message,reply,created_at FROM chats WHERE user_id=? AND item_id=? ORDER BY created_at DESC LIMIT ?",
                (rs, n) -> Map.of("id", rs.getString("id"), "message", rs.getString("message"), "reply", rs.getString("reply"), "createdAt", rs.getObject("created_at", LocalDateTime.class).toString()), user.id(), id, limit);
        Collections.reverse(messages);
        return Map.of("messages", messages);
    }

    public Map<String, Object> chat(String id, Map<String, Object> body, AuthService.User user) {
        if (user == null) throw new ApiException(401, "请先登录");
        Item item = item(id); String message = AuthService.text(body, "message").trim();
        if (message.isEmpty() || message.length() > 1000) throw new ApiException(400, "消息长度须为 1–1000 字");
        String reply = "听你说“" + message.substring(0, Math.min(24, message.length())) + (message.length() > 24 ? "…" : "") + "”，我很想继续了解。" + item.summary();
        String chatId = UUID.randomUUID().toString(); LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO chats(id,user_id,item_id,message,reply,created_at) VALUES(?,?,?,?,?,?)", chatId, user.id(), id, message, reply, now);
        return Map.of("exchange", Map.of("id", chatId, "message", message, "reply", reply, "createdAt", now.toString()));
    }

    public Map<String, Object> myItems(AuthService.User user) {
        if (user == null) throw new ApiException(401, "请先登录");
        return Map.of("items", jdbc.query("SELECT * FROM items WHERE author_id=? ORDER BY created_at DESC", this::mapItem, user.id())
                .stream().map(item -> itemJson(item, user)).toList());
    }
}
