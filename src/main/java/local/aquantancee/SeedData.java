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

        String missionId = "82e261bc-2d95-4c41-8913-e0cf2756fc04";
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_meta WHERE name='mission-seeded'", Integer.class) == 0) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE id=?", Integer.class, missionId) == 0) {
                LocalDateTime now = LocalDateTime.now();
                jdbc.update("INSERT INTO items(id,author_id,author,title,category,summary,icon,theme,views,likes,created_at,updated_at) VALUES(?,NULL,?,?,?,?,?,?,0,0,?,?)",
                    missionId, "风月剧场", "【无限流】主神空间 · 多元试炼", "幻想",
                    "醒来时，你已置身主神空间。每个世界都有任务、资源与代价；你可以决定如何结盟、探索和生存。", "◈", "indigo", now, now);
            }
            if (jdbc.queryForObject("SELECT COUNT(*) FROM item_cards WHERE item_id=?", Integer.class, missionId) == 0) {
                String opening = """
                    # 主神空间 · 初始档案

                    > 白光散去，你站在一座悬浮于星海的圆形大厅。中央光幕缓缓亮起，第一场试炼正在等待选择。

                    ## 轮回者状态
                    - **身份**：新晋轮回者
                    - **生命**：100 / 100
                    - **积分**：0
                    - **随行人物**：暂无

                    ---

                    ## 当前任务
                    **世界：失落的观测站**

                    - 主线：在日落前找到观测站的核心日志。
                    - 奖励：基础积分 300 点。
                    - 提示：每一次选择都会留下新的线索。

                    :::details 羁绊人物
                    尚未结识其他轮回者。你可以在任务中观察、交谈与结盟。
                    :::

                    :::details 剧情记忆
                    这是你的第一场试炼，所有经历都会记录在当前会话中。
                    :::

                    光幕上出现两条通道：左侧通往寂静的档案室，右侧传来断续的求救声。你准备怎么做？
                    """;
                String css = """
                    .scene{background:radial-gradient(circle at 55% 43%,#71d5ff55,transparent 18%),radial-gradient(circle at 55% 43%,#8272ff55,transparent 38%),linear-gradient(145deg,#080f2c,#21204d 55%,#080f28)}
                    .scene:after{content:'◈';position:absolute;left:50%;top:45%;transform:translate(-50%,-50%);font-size:32vmin;color:#a9d7ff22;text-shadow:0 0 65px #72cfff;animation:portalPulse 7s ease-in-out infinite}
                    .scene .aura{left:35%;bottom:20%;border-color:#86caff77;box-shadow:0 0 80px #6daeff55}
                    .card{background:linear-gradient(145deg,#0d1530,#28336a);border:1px solid #7dbaff;color:#e9f6ff;box-shadow:0 15px 45px #090e2acc}
                    .card h2{color:#bceaff}
                    @keyframes portalPulse{50%{opacity:.4;filter:blur(5px);transform:translate(-50%,-50%) scale(1.12)}}
                    """;
                jdbc.update("INSERT INTO item_cards(item_id,personality,scenario,first_message,example_dialogue,author_css,background_url,quick_replies,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    missionId,
                    "你是主神空间的叙事系统。记录轮回者的状态、任务、资源和人物关系；尊重玩家行动，不替玩家做决定。",
                    "多元世界中的生存试炼。每次回复先用一段情节叙述，再按需要用 Markdown 更新状态与任务。可用 :::details 标题 和单独一行的 ::: 提供可折叠资料。状态变化必须与剧情一致，不凭空扣除积分。",
                    opening,
                    "用户：我走向档案室。\n系统：门后的终端还亮着，日志被分成三份。你可以先检索编号，也可以检查异常的声音。",
                    css, "", "查看状态\n前往档案室\n寻找求救声\n询问任务规则", LocalDateTime.now());
            }
            jdbc.update("INSERT INTO app_meta(name,meta_value) VALUES('mission-seeded','1')");
        }
    }
}
