package local.aquantancee;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationWorldContextTest {
    @Test
    void activatesOnlyMatchingAndAlwaysOnEntries() {
        Map<String, Object> card = Map.of("worldEntries", List.of(
            Map.of("title", "皇城", "keywords", "宫门,皇城", "content", "北方的宫殿", "enabled", true),
            Map.of("title", "规则", "keywords", "", "content", "每轮生效", "enabled", true),
            Map.of("title", "禁用", "keywords", "", "content", "不应出现", "enabled", false),
            Map.of("title", "海港", "keywords", "码头", "content", "南方港口", "enabled", true)));
        String result = ConversationService.worldContext(card, "打开宫门", List.of());
        assertTrue(result.contains("【皇城】北方的宫殿"));
        assertTrue(result.contains("【规则】每轮生效"));
        assertFalse(result.contains("禁用"));
        assertFalse(result.contains("海港"));
    }

    @Test
    void recentConversationCanTriggerAnEntry() {
        Map<String, Object> card = Map.of("worldEntries", List.of(
            Map.of("title", "旧事", "keywords", "密信，旧案", "content", "十年前的秘密", "enabled", true)));
        String result = ConversationService.worldContext(card, "继续调查", List.of(Map.of("content", "我找到一封密信")));
        assertTrue(result.contains("十年前的秘密"));
    }
}
