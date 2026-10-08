package local.aquantancee;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeedData implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public SeedData(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_meta WHERE name='seeded'", Integer.class) == 0
                && jdbc.queryForObject("SELECT COUNT(*) FROM items", Integer.class) == 0) {
            List<String[]> rows = List.of(
                new String[]{"雨夜便利店","落雨时分","纯聊","深夜的便利店里，总有人记得你喜欢的那杯热茶。","🌧️","blue","26940","1982"},
                new String[]{"月光下的图书馆","眠月","剧情","闭馆后的图书馆，藏着只为你开启的故事。","🌙","violet","21480","1640"},
                new String[]{"和风铃一起旅行","风铃","冒险","收好车票，下一站去看海。","🚃","peach","18830","1208"},
                new String[]{"星际来信","星屿","幻想","来自遥远星系的信，偏偏写着你的名字。","🪐","indigo","16770","1135"},
                new String[]{"街角的花店","柚子糖","治愈","每一束花都有故事，今天这一束送给你。","🌷","rose","15330","983"},
                new String[]{"夏日摄影社","阿澈","校园","在快门按下之前，先记住这个夏天。","📷","mint","14260","882"},
                new String[]{"午夜电台","听风","纯聊","如果睡不着，就让声音陪你聊一会儿。","📻","purple","13250","735"},
                new String[]{"云端咖啡馆","小鹿","治愈","这里的云朵很软，烦恼可以暂时寄存。","☕","amber","12190","689"},
                new String[]{"浮岛纪事","北北","冒险","在天空之上的群岛，寻找遗失的地图。","🏝️","cyan","11410","625"},
                new String[]{"许愿事务所","暮色","幻想","你说出愿望，我陪你寻找实现它的方法。","✨","plum","10680","581"},
                new String[]{"下一站，初雪","白川","剧情","列车停靠的地方，恰好下起了第一场雪。","❄️","sky","9720","542"},
                new String[]{"周末料理课","栗子","纯聊","从第一道家常菜开始，慢慢熟悉彼此。","🍰","coral","8610","490"}
            );
            for (int i = 0; i < rows.size(); i++) {
                String[] row = rows.get(i); LocalDateTime created = LocalDateTime.now().minusDays(i);
                jdbc.update("INSERT INTO items(id,author_id,author,title,category,summary,icon,theme,views,likes,created_at,updated_at) VALUES(?,NULL,?,?,?,?,?,?,?,?,?,?)",
                    String.valueOf(i + 1), row[1], row[0], row[2], row[3], row[4], row[5], Long.parseLong(row[6]), Long.parseLong(row[7]), created, created);
            }
        }
        jdbc.update("INSERT IGNORE INTO app_meta(name,meta_value) VALUES('seeded','1')");
        String featuredId = "6a46cbbf-a5f5-47fa-8568-44903607d0bf";
        if (jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE id=?", Integer.class, featuredId) == 0) {
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("INSERT INTO items(id,author_id,author,title,category,summary,icon,theme,views,likes,created_at,updated_at) VALUES(?,NULL,?,?,?,?,?,?,0,0,?,?)",
                featuredId, "风月剧场", "醉花台·宫廷迷局", "剧情", "一场宫廷风波将你卷入权谋迷局。旧日誓言与新的盟友交织，故事从深宫的一封密信开始。", "🌸", "rose", now, now);
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM item_cards WHERE item_id=?", Integer.class, featuredId) == 0) {
            String css = ".card{background:linear-gradient(135deg,#1d282bd9,#47302da6),url('/palace-mystery-bg.png') center/cover;color:#fff;border:1px solid #dbb985;border-radius:22px;box-shadow:0 16px 36px #0008;animation:cardGlow 5s ease-in-out infinite}.card h2{color:#ffe5b8}.card .sigil{animation:sigilSpin 16s linear infinite}@keyframes cardGlow{50%{box-shadow:0 18px 48px #d7a26988}}@keyframes sigilSpin{to{transform:rotate(360deg)}}.scene:after{content:'✿';position:absolute;right:9%;top:12%;font-size:46px;color:#ffc6d777;animation:petalFloat 7s ease-in-out infinite}@keyframes petalFloat{50%{transform:translateY(-26px) rotate(18deg)}}";
            jdbc.update("INSERT INTO item_cards(item_id,personality,scenario,first_message,example_dialogue,author_css,background_url,quick_replies,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                featuredId, "一位谨慎的宫廷调查者，善于从细节中寻找线索。", "深夜宫廷，一封密信牵出旧日誓言。角色之间存在不同立场，玩家的选择决定调查方向。",
                "夜风掠过灯火，一封署着你名字的密信静静放在案上。廊外有人停下了脚步。你现在想怎么做？",
                "用户：我先查看信封。\n角色：封蜡上有一枚陌生的花纹，似乎指向宫中的旧花园。", css,
                "/palace-mystery-bg.png", "打开密信\n询问守夜人\n查看窗外\n前往旧花园", LocalDateTime.now());
        }
    }
}
