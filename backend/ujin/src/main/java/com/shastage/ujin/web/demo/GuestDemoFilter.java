package com.shastage.ujin.web.demo;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Fail-closed Java LOCAL API allowlist: future platform/Python endpoints are denied too. */
public final class GuestDemoFilter extends OncePerRequestFilter {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> AUTH = Set.of("/api/auth/csrf", "/api/auth/session", "/api/auth/demo", "/api/auth/login", "/api/auth/logout");
    private final GuestDemoService guests;
    public GuestDemoFilter(GuestDemoService guests) { this.guests = guests; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isGuest = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_GUEST_DEMO"));
        if (!isGuest) { chain.doFilter(request, response); return; }
        var session = request.getSession(false);
        var scope = guests.scope(session);
        if (scope == null || !guests.active(scope) || !auth.getName().equals(scope.owner)) {
            expire(request, response, chain); return;
        }
        synchronized (scope) {
            if (!guests.enabled() || scope.closed || scope.expiresAt.isBefore(java.time.Instant.now())) {
                expire(request, response, chain); return;
            }
            String path = request.getRequestURI(); String method = request.getMethod();
            if (AUTH.contains(path)) { chain.doFilter(request, response); return; }
            if (method.equals("GET") && path.equals("/api/v1/integrations/ujin/status")) {
                response.setContentType("application/json;charset=UTF-8");
                var blocked = java.util.Map.of("status", "BLOCKED", "reason", "Гостевое демо работает только с локальными Java-эмуляторами, без доступа к Ujin.");
                response.getWriter().write(JSON.writeValueAsString(java.util.Map.of("read", blocked, "publish", blocked, "downlink", blocked, "autoscripts", blocked))); return;
            }
            if (!allowed(path, method) || (path.endsWith("/state") && request.getParameter("mode") != null && !request.getParameter("mode").equals("LOCAL"))) {
                reject(response, 403, "DEMO_LOCAL_ONLY", "В гостевом демо доступны только локальные Java-эмуляторы."); return;
            }
            HttpServletRequest forwarded = request;
            if (!method.equals("GET")) {
                byte[] body = request.getInputStream().readNBytes(65537);
                if (body.length > 65536) { reject(response, 413, "DEMO_BODY_TOO_LARGE", "Слишком большой запрос для демо."); return; }
                if (!method.equals("DELETE")) {
                    tools.jackson.databind.JsonNode parsed;
                    try { parsed = JSON.readTree(body); }
                    catch (RuntimeException failure) { reject(response, 400, "INVALID_REQUEST", "Нужен корректный JSON."); return; }
                    if (parsed == null || !parsed.isObject()) { reject(response, 400, "INVALID_REQUEST", "Нужен объект JSON."); return; }
                    if ((path.matches("/api/v1/scenarios(?:/[^/]+)?") || path.endsWith("/commands") || path.endsWith("/availability")) && !"LOCAL".equals(parsed.path("executionMode").asString(""))) {
                        reject(response, 403, "DEMO_LOCAL_ONLY", "Демо не отправляет команды и сценарии в Ujin."); return;
                    }
                    if (path.matches("/api/v1/devices(?:/[^/]+)?") && (parsed.path("signals").size() > 20 || parsed.path("events").size() > 20)) {
                        reject(response, 422, "DEMO_QUOTA", "В демо у устройства не больше 20 сигналов и событий."); return;
                    }
                }
                if (!guests.permitWrite(scope, method, path)) {
                    reject(response, 429, "DEMO_QUOTA", "Лимит демо: 8 устройств, 8 сценариев, 60 изменений в минуту и 300 за сессию."); return;
                }
                forwarded = new BodyRequest(request, body);
            }
            response.setHeader("Cache-Control", "no-store"); chain.doFilter(forwarded, response);
        }
    }
    private void expire(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        if (request.getRequestURI().equals("/api/auth/session")) chain.doFilter(request, response);
        else reject(response, 401, "DEMO_SESSION_EXPIRED", "Время демонстрации истекло. Начните новую.");
    }
    private boolean allowed(String path, String method) {
        if (method.equals("GET")) return path.matches("/api/v1/(?:devices|scenarios|runs)(?:/[^/]+)?") || path.matches("/api/v1/devices/[^/]+/(?:state|dependencies)") || path.matches("/api/v1/(?:events|commands)/[^/]+");
        if (method.equals("POST")) return path.equals("/api/v1/devices") || path.equals("/api/v1/scenarios") || path.matches("/api/v1/devices/[^/]+/(?:commands|stimuli)");
        if (method.equals("PUT")) return path.matches("/api/v1/(?:devices|scenarios)/[^/]+") || path.matches("/api/v1/devices/[^/]+/availability");
        return method.equals("DELETE") && path.matches("/api/v1/(?:devices|scenarios)/[^/]+");
    }
    private void reject(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter().write(JSON.writeValueAsString(java.util.Map.of("status", status, "code", code, "detail", detail)));
    }
    private static class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                public int read() { return input.read(); }
                public boolean isFinished() { return input.available() == 0; }
                public boolean isReady() { return true; }
                public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
