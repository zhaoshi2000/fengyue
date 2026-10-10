package local.aquantancee;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    public record Voice(String id, String label, String locale, String gender) {}

    private static final List<Voice> VOICES = List.of(
        voice("zh-CN-XiaoxiaoNeural", "晓晓 · 女声"),
        voice("zh-CN-XiaoyiNeural", "晓伊 · 女声"),
        voice("zh-CN-YunjianNeural", "云健 · 男声"),
        voice("zh-CN-YunxiNeural", "云希 · 男声"),
        voice("zh-CN-YunxiaNeural", "云夏 · 男声"),
        voice("zh-CN-YunyangNeural", "云扬 · 男声"),
        voice("zh-CN-liaoning-XiaobeiNeural", "晓北 · 辽宁女声"),
        voice("zh-CN-shaanxi-XiaoniNeural", "晓妮 · 陕西女声"),
        voice("zh-HK-HiuGaaiNeural", "曉佳 · 粤语女声"),
        voice("zh-HK-HiuMaanNeural", "曉曼 · 粤语女声"),
        voice("zh-HK-WanLungNeural", "雲龍 · 粤语男声"),
        voice("zh-TW-HsiaoChenNeural", "曉臻 · 台湾女声"),
        voice("zh-TW-HsiaoYuNeural", "曉雨 · 台湾女声"),
        voice("zh-TW-YunJheNeural", "雲哲 · 台湾男声")
    );
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
    private volatile List<Voice> availableVoices;
    private volatile long nextVoiceRefresh;

    private static Voice voice(String id, String label) {
        String[] parts = id.split("-");
        return new Voice(id, label, parts[0] + "-" + parts[1], label.contains("女") ? "Female" : "Male");
    }

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
        return Map.of("voices", currentVoices(), "defaultVoice", VOICES.getFirst().id());
    }

    private synchronized List<Voice> currentVoices() {
        long now = System.currentTimeMillis();
        if (availableVoices != null && now < nextVoiceRefresh) return availableVoices;
        List<Voice> fetched = fetchVoices();
        if (!fetched.isEmpty()) availableVoices = fetched;
        else if (availableVoices == null) availableVoices = VOICES;
        nextVoiceRefresh = now + (fetched.isEmpty() ? TimeUnit.MINUTES.toMillis(10) : TimeUnit.HOURS.toMillis(6));
        return availableVoices;
    }

    private List<Voice> fetchVoices() {
        Process process = null;
        ScheduledFuture<?> timeout = null;
        try {
            process = new ProcessBuilder(python, bridge.toString(), "--voices")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            Process running = process;
            timeout = TIMEOUTS.schedule(running::destroyForcibly, 20, TimeUnit.SECONDS);
            byte[] data;
            try (var output = process.getInputStream()) {
                data = output.readNBytes(512_001);
            }
            if (data.length > 512_000 || process.waitFor() != 0) return List.of();
            List<Voice> result = new ArrayList<>();
            Map<String, Voice> chineseNames = VOICES.stream().collect(java.util.stream.Collectors.toMap(Voice::id, v -> v));
            for (String line : new String(data, StandardCharsets.UTF_8).split("\\R")) {
                String[] parts = line.split("\\t", 4);
                if (parts.length != 4 || !parts[0].matches("[A-Za-z0-9-]{1,80}")
                        || !parts[1].matches("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,15})+")) continue;
                String name = parts[0].startsWith(parts[1] + "-")
                    ? parts[0].substring(parts[1].length() + 1).replaceFirst("Neural$", "") : parts[0];
                String label = chineseNames.containsKey(parts[0]) ? chineseNames.get(parts[0]).label()
                    : name + " · " + ("Female".equals(parts[2]) ? "女声" : "男声");
                result.add(new Voice(parts[0], label, parts[1], parts[2]));
            }
            result.sort(Comparator.comparing((Voice v) -> !v.locale().startsWith("zh-"))
                .thenComparing(Voice::locale).thenComparing(Voice::id));
            return List.copyOf(result);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return List.of();
        } finally {
            if (timeout != null) timeout.cancel(false);
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    public byte[] speak(String conversationId, String messageId, String voice, AuthService.User user) {
        requireVoice(voice);
        List<String> rows = jdbc.queryForList("""
            SELECT m.content FROM conversation_messages m
            JOIN conversations c ON c.id=m.conversation_id
            WHERE c.id=? AND c.user_id=? AND m.id=? AND m.role='assistant'
            """, String.class, conversationId, user.id(), messageId);
        if (rows.isEmpty()) throw new ApiException(404, "找不到可朗读的 AI 消息");
        String text = spokenText(rows.getFirst());
        if (text.isBlank()) throw new ApiException(400, "这条消息没有可朗读的文字");
        return speakText(text, voice);
    }

    public byte[] preview(String text, String voice) {
        requireVoice(voice);
        String sample = text == null ? "" : text.trim();
        if (sample.isEmpty() || sample.length() > 200) throw new ApiException(400, "试听文字须为 1–200 字");
        return speakText(sample, voice);
    }

    private void requireVoice(String voice) {
        if (currentVoices().stream().noneMatch(entry -> entry.id().equals(voice)))
            throw new ApiException(400, "请选择可用音色");
    }

    private byte[] speakText(String text, String voice) {
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
