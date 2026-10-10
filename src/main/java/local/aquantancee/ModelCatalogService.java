package local.aquantancee;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ModelCatalogService {
    public record Model(String id, String type) {}

    private final String apiKey;
    private final String defaultModel;
    private final String modelsUrl;
    private final RestClient client;
    private volatile List<Model> cached;
    private volatile boolean catalogAvailable;
    private volatile long refreshAt;

    public ModelCatalogService(@Value("${AI_API_KEY:}") String apiKey,
            @Value("${AI_API_URL:https://api.deepseek.com/chat/completions}") String apiUrl,
            @Value("${AI_MODEL:deepseek-flash}") String defaultModel) {
        this.apiKey = apiKey.trim();
        this.defaultModel = defaultModel.trim();
        this.modelsUrl = apiUrl.trim().replaceFirst("/chat/completions/?$", "/models");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(15_000);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public Map<String, Object> models() {
        List<Model> available = current();
        return Map.of("models", available, "defaultModel", defaultModel,
            "available", catalogAvailable);
    }

    public String selectedChatModel(String requested) {
        if (requested == null || requested.isBlank()) return defaultModel;
        String choice = requested.trim();
        if (current().stream().anyMatch(model -> model.id().equals(choice) && model.type().equals("chat")))
            return choice;
        throw new ApiException(400, "该模型不能用于文字聊天，请选择聊天模型");
    }

    private synchronized List<Model> current() {
        long now = System.currentTimeMillis();
        if (cached != null && now < refreshAt) return cached;
        List<Model> fetched = fetch();
        if (!fetched.isEmpty()) { cached = fetched; catalogAvailable = true; }
        else if (cached == null) cached = List.of(new Model(defaultModel, "chat"));
        refreshAt = now + (fetched.isEmpty() ? TimeUnit.MINUTES.toMillis(1) : TimeUnit.MINUTES.toMillis(10));
        return cached;
    }

    @SuppressWarnings("unchecked")
    private List<Model> fetch() {
        if (apiKey.isEmpty()) return List.of();
        try {
            Map<String, Object> result = client.get().uri(modelsUrl).header("Authorization", "Bearer " + apiKey)
                .retrieve().body(Map.class);
            if (result == null || !(result.get("data") instanceof List<?> entries)) return List.of();
            List<Model> models = new ArrayList<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> value) || !(value.get("id") instanceof String id)
                        || !id.matches("[A-Za-z0-9._:-]{1,100}")) continue;
                models.add(new Model(id, typeOf(id)));
            }
            if (models.stream().noneMatch(model -> model.id().equals(defaultModel)))
                models.add(new Model(defaultModel, "chat"));
            models.sort(Comparator.comparing((Model item) -> !item.id().equals(defaultModel))
                .thenComparing(Model::type).thenComparing(Model::id));
            return List.copyOf(models);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static String typeOf(String id) {
        String name = id.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("image") || name.contains("dall-e")) return "image";
        if (name.contains("audio") || name.contains("realtime")) return "audio";
        if (name.contains("auto-review")) return "other";
        return "chat";
    }
}
