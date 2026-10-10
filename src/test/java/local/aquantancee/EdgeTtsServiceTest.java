package local.aquantancee;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EdgeTtsServiceTest {
    @Test
    void stripsFormattingBeforeSpeaking() {
        String markdown = "# 第一章\n[查看线索](https://example.com)\n:::details 背景\n旧王朝的秘密。\n:::";
        assertEquals("第一章\n查看线索\n背景\n旧王朝的秘密。",
            EdgeTtsService.spokenText(markdown));
    }
}
