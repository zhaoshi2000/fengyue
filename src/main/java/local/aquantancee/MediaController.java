package local.aquantancee;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {
    private final AuthService auth;
    private final Path directory = Path.of("data", "uploads").toAbsolutePath().normalize();

    public MediaController(AuthService auth) { this.auth = auth; }

    @PostMapping(path = "/api/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws IOException {
        auth.require(request);
        if (file.isEmpty() || file.getSize() > 8_000_000) throw new ApiException(400, "图片须小于 8 MB");
        byte[] bytes = file.getBytes();
        boolean png = bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        boolean jpeg = bytes.length > 4 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
        if (!png && !jpeg) throw new ApiException(400, "只支持 PNG 或 JPEG 图片");
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() < 50 || image.getHeight() < 50 || image.getWidth() > 5000 || image.getHeight() > 5000)
            throw new ApiException(400, "图片尺寸须在 50–5000 像素之间");
        Files.createDirectories(directory);
        String name = UUID.randomUUID() + (png ? ".png" : ".jpg");
        Files.write(directory.resolve(name), bytes);
        return Map.of("url", "/media/" + name);
    }

    @GetMapping("/media/{name}")
    public ResponseEntity<byte[]> image(@PathVariable String name) throws IOException {
        if (!name.matches("[0-9a-f-]{36}\\.(png|jpg)")) throw new ApiException(404, "图片不存在");
        Path file = directory.resolve(name).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) throw new ApiException(404, "图片不存在");
        return ResponseEntity.ok().contentType(name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noCache()).header("X-Content-Type-Options", "nosniff")
            .body(Files.readAllBytes(file));
    }
}
