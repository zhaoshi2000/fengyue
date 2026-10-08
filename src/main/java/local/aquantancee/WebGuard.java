package local.aquantancee;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class WebGuard extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        String method = request.getMethod();
        String origin = request.getHeader("Origin");
        if ((method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE")) && origin != null) {
            try {
                URI source = URI.create(origin);
                int sourcePort = source.getPort() < 0 ? ("https".equals(source.getScheme()) ? 443 : 80) : source.getPort();
                if (!source.getHost().equalsIgnoreCase(request.getServerName()) || sourcePort != request.getServerPort()) {
                    reject(response); return;
                }
            } catch (RuntimeException e) { reject(response); return; }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"跨站请求被拒绝\"}");
    }
}
