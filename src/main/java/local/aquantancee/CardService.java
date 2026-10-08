package local.aquantancee;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        if (!rows.isEmpty()) return rows.getFirst();
        Map<String, Object> empty = new LinkedHashMap<>();
        for (String field : FIELDS) empty.put(field, "");
        return empty;
    }

    public void save(String itemId, Map<String, Object> body) {
        if (FIELDS.stream().noneMatch(body::containsKey)) return;
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

    private String value(Map<String, Object> body, String field, int max) {
        String value = AuthService.text(body, field).trim();
        if (value.length() > max) throw new ApiException(400, field + " 内容过长");
        return value;
    }
}
