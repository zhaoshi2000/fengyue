package local.aquantancee;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(11);
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record User(String id, String name, String passwordHash, int points, LocalDate lastCheckin) {}

    private User findUser(String sql, Object... args) {
        List<User> rows = jdbc.query(sql, (rs, n) -> new User(rs.getString("id"), rs.getString("name"),
                rs.getString("password_hash"), rs.getInt("points"), rs.getObject("last_checkin", LocalDate.class)), args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public User current(HttpServletRequest request) {
        String token = token(request);
        if (!token.matches("[0-9a-f]{64}")) return null;
        return findUser("SELECT u.* FROM users u JOIN sessions s ON s.user_id=u.id WHERE s.token_hash=? AND s.expires_at > NOW(3)", hash(token));
    }

    public User require(HttpServletRequest request) {
        User user = current(request);
        if (user == null) throw new ApiException(401, "请先登录");
        return user;
    }

    public User refresh(String id) { return findUser("SELECT * FROM users WHERE id=?", id); }

    public boolean isAdmin(User user) {
        return user != null && !jdbc.queryForList("SELECT user_id FROM admin_users WHERE user_id=?", String.class, user.id()).isEmpty();
    }

    public Map<String, Object> publicUser(User user) {
        if (user == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.id()); result.put("name", user.name()); result.put("points", user.points());
        result.put("admin", isAdmin(user));
        result.put("lastCheckin", user.lastCheckin() == null ? null : user.lastCheckin().toString());
        result.put("favorites", jdbc.queryForList("SELECT item_id FROM favorites WHERE user_id=? ORDER BY created_at DESC", String.class, user.id()));
        result.put("following", jdbc.queryForList("SELECT author FROM follows WHERE user_id=? ORDER BY created_at DESC", String.class, user.id()));
        result.put("history", jdbc.queryForList("SELECT item_id FROM history WHERE user_id=? ORDER BY visited_at DESC LIMIT 100", String.class, user.id()));
        return result;
    }

    @Transactional
    public Map<String, Object> register(Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        String name = text(body, "name").trim();
        String password = text(body, "password");
        if (name.length() < 2 || name.length() > 24 || name.matches(".*[<>\\p{Cntrl}].*") || password.length() < 8 || password.length() > 128)
            throw new ApiException(400, "昵称须为 2–24 字，密码须为 8–128 位");
        if (!jdbc.queryForList("SELECT id FROM users WHERE name=?", String.class, name).isEmpty()) throw new ApiException(409, "昵称已被使用");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(id,name,password_hash,points,created_at) VALUES(?,?,?,20,?)", id, name, encoder.encode(password), LocalDateTime.now());
        session(id, request, response);
        return publicUser(findUser("SELECT * FROM users WHERE id=?", id));
    }

    public Map<String, Object> login(Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        String name = text(body, "name").trim();
        String password = text(body, "password");
        User user = findUser("SELECT * FROM users WHERE name=?", name);
        if (user == null || !encoder.matches(password, user.passwordHash())) throw new ApiException(401, "昵称或密码错误");
        session(user.id(), request, response);
        return publicUser(user);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = token(request);
        if (token.matches("[0-9a-f]{64}")) jdbc.update("DELETE FROM sessions WHERE token_hash=?", hash(token));
        response.addHeader("Set-Cookie", cookie("", 0, request).toString());
    }

    @Transactional
    public void changePassword(Map<String, Object> body, HttpServletRequest request) {
        User user = require(request);
        String current = text(body, "currentPassword"), next = text(body, "newPassword");
        if (next.length() < 8 || next.length() > 128) throw new ApiException(400, "新密码须为 8–128 位");
        if (!encoder.matches(current, user.passwordHash())) throw new ApiException(401, "当前密码错误");
        jdbc.update("UPDATE users SET password_hash=? WHERE id=?", encoder.encode(next), user.id());
        jdbc.update("DELETE FROM sessions WHERE user_id=? AND token_hash<>?", user.id(), hash(token(request)));
    }

    private void session(String userId, HttpServletRequest request, HttpServletResponse response) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        jdbc.update("INSERT INTO sessions(token_hash,user_id,expires_at,created_at) VALUES(?,?,?,?)",
                hash(token), userId, LocalDateTime.now().plusDays(30), LocalDateTime.now());
        response.addHeader("Set-Cookie", cookie(token, 30 * 86400, request).toString());
    }

    private ResponseCookie cookie(String token, long ageSeconds, HttpServletRequest request) {
        return ResponseCookie.from("session", token).httpOnly(true).sameSite("Lax").secure(request.isSecure())
                .path("/").maxAge(Duration.ofSeconds(ageSeconds)).build();
    }

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) return "";
        for (Cookie cookie : request.getCookies()) if (cookie.getName().equals("session")) return cookie.getValue();
        return "";
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static String text(Map<String, Object> body, String field) {
        Object value = body.get(field);
        return value instanceof String string ? string : "";
    }
}
