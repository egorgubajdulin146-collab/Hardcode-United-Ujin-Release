package com.shastage.ujin.web.demo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class GuestDemoController {
    private final GuestDemoService guests;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
    public GuestDemoController(GuestDemoService guests) { this.guests = guests; }

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        var scope = guests.scope(request.getSession(false));
        return Map.of("authenticated", authenticated, "demo", authenticated && scope != null,
                "demoAvailable", guests.enabled(), "demoLifetimeMinutes", 30);
    }
    @PostMapping("/demo")
    public ResponseEntity<Void> demo(HttpServletRequest request, HttpServletResponse response) {
        if (!guests.enabled()) return ResponseEntity.notFound().build();
        var current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated() && !(current instanceof org.springframework.security.authentication.AnonymousAuthenticationToken))
            return guests.scope(request.getSession(false)) != null
                    ? ResponseEntity.noContent().build() : ResponseEntity.status(409).build();
        var scope = guests.create();
        if (scope == null) return ResponseEntity.status(429).build();
        var oldSession = request.getSession(false);
        if (oldSession != null) oldSession.invalidate();
        var session = request.getSession(true);
        session.setMaxInactiveInterval(GuestDemoService.LIFETIME_SECONDS);
        session.setAttribute(GuestDemoService.ATTRIBUTE, scope);
        // No UJIN_TOKEN, credentials or client-supplied owner enter this session.
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                scope.owner, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST_DEMO")));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        response.setHeader("Cache-Control", "no-store");
        return ResponseEntity.noContent().build();
    }
}
