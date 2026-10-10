package local.aquantancee;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EdgeTtsService {
    public record Voice(String id, String label) {}

    private static final List<Voice> VOICES = List.of(
        new Voice("zh-CN-XiaoxiaoNeural", "晓晓 · 女声"),
        new Voice("zh-CN-XiaoyiNeural", "晓伊 · 女声"),
        new Voice("zh-CN-YunjianNeural", "云健 · 男声"),
        new Voice("zh-CN-YunxiNeural", "云希 · 男声"),
        new Voice("zh-CN-YunxiaNeural", "云夏 · 男声"),
        new Voice("zh-CN-YunyangNeural", "云扬 · 男声"),
        new Voice("zh-CN-liaoning-XiaobeiNeural", "晓北 · 辽宁女声"),
        new Voice("zh-CN-shaanxi-XiaoniNeural", "晓妮 · 陕西女声"),
        new Voice("zh-HK-HiuGaaiNeural", "曉佳 · 粤语女声"),
        new Voice("zh-HK-HiuMaanNeural", "曉曼 · 粤语女声"),
        new Voice("zh-HK-WanLungNeural", "雲龍 · 粤语男声"),
        new Voice("zh-TW-HsiaoChenNeural", "曉臻 · 台湾女声"),
        new Voice("zh-TW-HsiaoYuNeural", "曉雨 · 台湾女声"),
        new Voice("zh-TW-YunJheNeural", "雲哲 · 台湾男声")
    );
    private static final Set<String> VOICE_IDS = VOICES.stream().map(Voice::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final int MAX_AUDIO_BYTES = 20_000_000;
    private static final Semaphore SLOTS = new Semaphore(3);
    private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "edge-tts-timeouts");
        thread.setDaemon(true);
        return thread;
    });

    private final JdbcTemplate jdbc;
    private final String python;
    private final Path bridge;

    public EdgeTtsService(JdbcTemplate jdbc, @Value("${TTS_PYTHON:python}") String python) {
        this.jdbc = jdbc;
        this.python = python;
        try (InputStream source = new ClassPathResource("tts_bridge.py").getInputStream()) {
            bridge = Files.createTempFile("fengyue-edge-tts-", ".py");
            Files.copy(source, bridge, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            bridge.toFile().deleteOnExit();
        } catch (IOException error) {
            throw new IllegalStateException("无法加载语音生成脚本", error);
        }
    }

    public Map<String, Object> voices() {
        return Map.of("voices", VOICES, "defaultVoice", VOICES.getFirst().id());
    }

    public byte[] speak(String conversationId, String messageId, String voice, AuthService.User user) {
        if (!VOICE_IDS.contains(voice)) throw new ApiException(400, "请选择可用音色");
        List<String> rows = jdbc.queryForList("""
            SELECT m.content FROM conversation_messages m
            JOIN conversations c ON c.id=m.conversation_id
            WHERE c.id=? AND c.user_id=? AND m.id=? AND m.role='assistant'
            """, String.class, conversationId, user.id(), messageId);
        if (rows.isEmpty()) throw new ApiException(404, "找不到可朗读的 AI 消息");
        String text = spokenText(rows.getFirst());
        if (text.isBlank()) throw new ApiException(400, "这条消息没有可朗读的文字");
        if (!SLOTS.tryAcquire()) throw new ApiException(429, "语音生成繁忙，请稍后再试");
        try {
            return generate(text, voice);
        } finally {
            SLOTS.release();
        }
    }

    static String spokenText(String markdown) {
        return markdown.replaceAll("(?s)```.*?```", " ")
            .replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")
            .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
            .replaceAll("(?m)^\\s*:::details\\s*", "")
            .replaceAll("(?m)^\\s*:::\\s*$", "")
            .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
            .replaceAll("<[^>]+>", "")
            .replaceAll("[*_`~>]", "")
            .replaceAll("(?m)^\\s*[-+]\\s+", "")
            .replaceAll("[\\t ]{2,}", " ")
            .trim();
    }

    private byte[] generate(String text, String voice) {
        Process process = null;
        ScheduledFuture<?> timeout = null;
        try {
            process = new ProcessBuilder(python, bridge.toString(), voice)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Process running = process;
            timeout = TIMEOUTS.schedule(running::destroyForcibly, 120, TimeUnit.SECONDS);
            try (var input = process.getOutputStream()) {
                input.write(text.getBytes(StandardCharsets.UTF_8));
            }
            byte[] audio;
            try (var output = process.getInputStream()) {
                audio = output.readNBytes(MAX_AUDIO_BYTES + 1);
            }
            if (audio.length > MAX_AUDIO_BYTES) {
                process.destroyForcibly();
                throw new ApiException(502, "语音内容过长，请缩短消息后重试");
            }
            int exit = process.waitFor();
            if (exit != 0 || audio.length == 0) {
                throw new ApiException(502, "Edge TTS 暂时无法生成语音，请稍后重试");
            }
            return audio;
        } catch (ApiException error) {
            throw error;
        } catch (IOException error) {
            throw new ApiException(503, process == null
                ? "语音服务未就绪，请运行 setup-edge-tts.ps1 并重启服务"
                : "语音连接中断，请稍后重试");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "语音生成已中断");
        } finally {
            if (timeout != null) timeout.cancel(false);
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
