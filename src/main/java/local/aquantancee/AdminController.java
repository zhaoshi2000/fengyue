package local.aquantancee;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;

    public AdminController(AdminService admin) { this.admin = admin; }

    @GetMapping("/overview") public Map<String, Object> overview(HttpServletRequest request) { return admin.overview(request); }
    @GetMapping("/users") public Map<String, Object> users(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page, HttpServletRequest request) { return admin.users(q, page, request); }
    @GetMapping("/items") public Map<String, Object> items(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page, HttpServletRequest request) { return admin.items(q, page, request); }
    @DeleteMapping("/items/{id}") public Map<String, Boolean> deleteItem(@PathVariable String id, HttpServletRequest request) {
        admin.deleteItem(id, request); return Map.of("ok", true);
    }
    @DeleteMapping("/users/{id}") public Map<String, Boolean> deleteUser(@PathVariable String id, HttpServletRequest request) {
        admin.deleteUser(id, request); return Map.of("ok", true);
    }
}
