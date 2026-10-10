package local.aquantancee;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tts")
public class TtsController {
    private final EdgeTtsService speech;
    private final AuthService auth;

    public TtsController(EdgeTtsService speech, AuthService auth) {
        this.speech = speech;
        this.auth = auth;
    }

    @GetMapping("/voices")
    public Map<String, Object> voices() {
        return speech.voices();
    }

    @PostMapping(value = "/speech", produces = "audio/mpeg")
    public ResponseEntity<byte[]> speak(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        AuthService.User user = auth.require(request);
        byte[] audio = speech.speak(AuthService.text(body, "conversationId"),
            AuthService.text(body, "messageId"), AuthService.text(body, "voice"), user);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg"))
            .cacheControl(CacheControl.noStore()).body(audio);
    }

    @PostMapping(value = "/preview", produces = "audio/mpeg")
    public ResponseEntity<byte[]> preview(@RequestBody Map<String, Object> body) {
        byte[] audio = speech.preview(AuthService.text(body, "text"), AuthService.text(body, "voice"));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg"))
            .cacheControl(CacheControl.noStore()).body(audio);
    }
}
