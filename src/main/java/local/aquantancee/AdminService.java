package local.aquantancee;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public AdminService(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    private AuthService.User requireAdmin(HttpServletRequest request) {
        AuthService.User user = auth.require(request);
        if (jdbc.queryForList("SELECT user_id FROM admin_users WHERE user_id=?", String.class, user.id()).isEmpty())
            throw new ApiException(403, "需要管理员权限");
        return user;
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    public Map<String, Object> overview(HttpServletRequest request) {
        requireAdmin(request);
        return Map.of("users", count("users"), "items", count("items"),
            "conversations", count("conversations"), "messages", count("conversation_messages"));
    }

    private String search(String value) {
        String q = value == null ? "" : value.trim();
        return "%" + q.substring(0, Math.min(q.length(), 80)) + "%";
    }

    private int page(int page) {
        if (page < 1 || page > 10000) throw new ApiException(400, "页码无效");
        return page;
    }

    private Map<String, Object> userRow(ResultSet rs, int row) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", rs.getString("id"));
        value.put("name", rs.getString("name"));
        value.put("points", rs.getInt("points"));
        value.put("admin", rs.getBoolean("is_admin"));
        value.put("cards", rs.getInt("card_count"));
        value.put("conversations", rs.getInt("conversation_count"));
        value.put("createdAt", rs.getObject("created_at", LocalDateTime.class).toString());
        return value;
    }

    public Map<String, Object> users(String query, int page, HttpServletRequest request) {
        requireAdmin(request);
        int offset = (page(page) - 1) * 30;
        String q = search(query);
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE name LIKE ?", Integer.class, q);
        List<Map<String, Object>> rows = jdbc.query("""
            SELECT u.id,u.name,u.points,u.created_at,
              EXISTS(SELECT 1 FROM admin_users a WHERE a.user_id=u.id) AS is_admin,
              (SELECT COUNT(*) FROM items i WHERE i.author_id=u.id) AS card_count,
              (SELECT COUNT(*) FROM conversations c WHERE c.user_id=u.id) AS conversation_count
            FROM users u WHERE u.name LIKE ? ORDER BY u.created_at DESC LIMIT 30 OFFSET ?
            """, this::userRow, q, offset);
        return Map.of("users", rows, "total", total, "page", page);
    }

    private Map<String, Object> itemRow(ResultSet rs, int row) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", rs.getString("id"));
        value.put("title", rs.getString("title"));
        value.put("author", rs.getString("author"));
        value.put("category", rs.getString("category"));
        value.put("views", rs.getLong("views"));
        value.put("likes", rs.getLong("likes"));
        value.put("customStyle", rs.getBoolean("custom_style"));
        value.put("createdAt", rs.getObject("created_at", LocalDateTime.class).toString());
        return value;
    }

    public Map<String, Object> items(String query, int page, HttpServletRequest request) {
        requireAdmin(request);
        int offset = (page(page) - 1) * 30;
        String q = search(query);
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE title LIKE ? OR author LIKE ?", Integer.class, q, q);
        List<Map<String, Object>> rows = jdbc.query("""
            SELECT i.id,i.title,i.author,i.category,i.views,i.likes,i.created_at,
              (c.author_css IS NOT NULL AND c.author_css<>'' OR c.background_url IS NOT NULL AND c.background_url<>'') AS custom_style
            FROM items i LEFT JOIN item_cards c ON c.item_id=i.id
            WHERE i.title LIKE ? OR i.author LIKE ? ORDER BY i.created_at DESC LIMIT 30 OFFSET ?
            """, this::itemRow, q, q, offset);
        return Map.of("items", rows, "total", total, "page", page);
    }

    public void deleteItem(String id, HttpServletRequest request) {
        requireAdmin(request);
        if (jdbc.update("DELETE FROM items WHERE id=?", id) == 0) throw new ApiException(404, "作品不存在");
    }

    public void deleteUser(String id, HttpServletRequest request) {
        AuthService.User admin = requireAdmin(request);
        if (admin.id().equals(id)) throw new ApiException(400, "不能删除当前管理员账号");
        if (!jdbc.queryForList("SELECT user_id FROM admin_users WHERE user_id=?", String.class, id).isEmpty())
            throw new ApiException(400, "不能删除其他管理员账号");
        if (jdbc.update("DELETE FROM users WHERE id=?", id) == 0) throw new ApiException(404, "用户不存在");
    }
}
