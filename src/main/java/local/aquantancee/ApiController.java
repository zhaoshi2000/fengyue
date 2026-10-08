package local.aquantancee;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Controller;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AuthService auth;
    private final StoryService stories;
    private final ConversationService conversations;

    public ApiController(AuthService auth, StoryService stories, ConversationService conversations) {
        this.auth = auth; this.stories = stories; this.conversations = conversations;
    }

    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "ok", "storage", "mysql", "backend", "spring-boot"); }
    @GetMapping("/bootstrap") public Map<String, Object> bootstrap(HttpServletRequest request) { return stories.bootstrap(auth.current(request)); }
    @GetMapping("/me") public Map<String, Object> me(HttpServletRequest request) {
        return new java.util.LinkedHashMap<>() {{ put("user", auth.publicUser(auth.current(request))); }};
    }
    @GetMapping("/me/items") public Map<String, Object> myItems(HttpServletRequest request) { return stories.myItems(auth.current(request)); }
    @PutMapping("/me/password") public Map<String, Boolean> password(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.changePassword(body, request); return Map.of("ok", true);
    }

    @PostMapping("/auth/register") public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("user", auth.register(body, request, response)));
    }
    @PostMapping("/auth/login") public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        return Map.of("user", auth.login(body, request, response));
    }
    @PostMapping("/auth/logout") public Map<String, Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request, response); return Map.of("ok", true);
    }

    @GetMapping("/items") public Map<String, Object> items(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "推荐") String category, @RequestParam(defaultValue = "recommended") String sort,
            @RequestParam(defaultValue = "") String view, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "24") int limit, HttpServletRequest request) {
        return stories.list(q, category, sort, view, page, limit, auth.current(request));
    }
    @PostMapping("/items") public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stories.create(body, auth.current(request)));
    }
    @GetMapping("/items/{id}") public Map<String, Object> detail(@PathVariable String id, HttpServletRequest request) {
        return stories.detail(id, auth.current(request));
    }
    @PutMapping("/items/{id}") public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return stories.update(id, body, auth.current(request));
    }
    @DeleteMapping("/items/{id}") public Map<String, Boolean> delete(@PathVariable String id, HttpServletRequest request) {
        stories.delete(id, auth.current(request)); return Map.of("ok", true);
    }
    @PostMapping("/items/{id}/visit") public Map<String, Object> visit(@PathVariable String id, HttpServletRequest request) {
        return stories.visit(id, auth.current(request));
    }
    @PostMapping("/items/{id}/favorite") public Map<String, Object> favorite(@PathVariable String id, HttpServletRequest request) {
        return stories.favorite(id, auth.current(request));
    }
    @PostMapping("/follow/{author}") public Map<String, Object> follow(@PathVariable String author, HttpServletRequest request) {
        return stories.follow(author, auth.current(request));
    }
    @PostMapping("/checkin") public Map<String, Object> checkin(HttpServletRequest request) { return stories.checkin(auth.current(request)); }
    @GetMapping("/items/{id}/chat") public Map<String, Object> messages(@PathVariable String id, @RequestParam(defaultValue = "100") int limit, HttpServletRequest request) {
        return stories.messages(id, auth.current(request), limit);
    }
    @PostMapping("/items/{id}/chat") public ResponseEntity<Map<String, Object>> chat(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stories.chat(id, body, auth.current(request)));
    }
    @GetMapping("/chat/config") public Map<String, Object> chatConfig() { return conversations.config(); }
    @GetMapping("/items/{id}/conversations") public Map<String, Object> conversations(@PathVariable String id, HttpServletRequest request) {
        return conversations.list(id, auth.require(request));
    }
    @PostMapping("/items/{id}/conversations") public ResponseEntity<Map<String, Object>> newConversation(@PathVariable String id, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversations.create(id, auth.require(request)));
    }
    @GetMapping("/conversations/{id}") public Map<String, Object> conversation(@PathVariable String id, HttpServletRequest request) {
        return conversations.detail(id, auth.require(request));
    }
    @PostMapping("/conversations/{id}/messages") public ResponseEntity<Map<String, Object>> sendMessage(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversations.send(id, body, auth.require(request)));
    }
    @DeleteMapping("/conversations/{id}") public Map<String, Boolean> deleteConversation(@PathVariable String id, HttpServletRequest request) {
        conversations.delete(id, auth.require(request)); return Map.of("ok", true);
    }
    @PatchMapping("/conversations/{id}") public Map<String, Object> renameConversation(@PathVariable String id,
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return conversations.rename(id, body, auth.require(request));
    }
    @GetMapping(value = "/conversations/{id}/export", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> exportConversation(@PathVariable String id, HttpServletRequest request) {
        String content = conversations.export(id, auth.require(request));
        return ResponseEntity.ok().contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=conversation-" + id + ".txt")
            .body(content);
    }
    @PatchMapping("/conversations/{id}/messages/{messageId}") public Map<String, Object> editMessage(@PathVariable String id, @PathVariable String messageId,
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return conversations.editMessage(id, messageId, body, auth.require(request));
    }
    @DeleteMapping("/conversations/{id}/messages/{messageId}") public Map<String, Object> deleteMessage(@PathVariable String id,
            @PathVariable String messageId, HttpServletRequest request) {
        return conversations.deleteMessage(id, messageId, auth.require(request));
    }
    @PostMapping("/conversations/{id}/regenerate") public Map<String, Object> regenerate(@PathVariable String id, HttpServletRequest request) {
        return conversations.regenerate(id, auth.require(request));
    }
}
