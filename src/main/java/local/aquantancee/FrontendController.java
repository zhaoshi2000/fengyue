package local.aquantancee;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class FrontendController {
    @GetMapping("/en") public String englishLanding() { return "forward:/index.html"; }
    @GetMapping("/zh/explore/installed/{id}") public String chatPage(@PathVariable String id) { return "forward:/chat.html"; }
    @GetMapping("/admin") public String adminPage() { return "forward:/admin.html"; }
    @GetMapping("/tts") public String ttsPage() { return "forward:/tts.html"; }
}
