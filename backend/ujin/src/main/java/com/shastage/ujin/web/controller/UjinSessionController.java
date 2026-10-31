package com.shastage.ujin.web.controller;

import com.shastage.ujin.datasource.client.UjinApiClient;
import com.shastage.ujin.datasource.client.UjinApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class UjinSessionController {
    private final UjinApiClient client;
    private final String app;
    private final HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    public UjinSessionController(@Value("${ujin.api.base-url}") URI baseUrl,
                                 @Value("${ujin.api.app}") String app) {
        this.client = new UjinApiClient(baseUrl);
        this.app = app;
    }

    @GetMapping("/csrf")
    public String csrf(CsrfToken csrfToken) {
        return csrfToken.getToken();
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody Credentials credentials, HttpServletRequest request,
                                      HttpServletResponse response) {
        if (credentials == null || credentials.login() == null || credentials.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String token;
        try {
            token = client.authenticate(credentials.login(), credentials.password(), app);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        } catch (UjinApiException error) {
            throw new ResponseStatusException(switch (error.kind()) {
                case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
                case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                case SIGNAL_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case BAD_RESPONSE -> HttpStatus.BAD_GATEWAY;
            });
        }
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("UJIN_TOKEN", token);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                credentials.login(), null, java.util.List.of());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }

    public record Credentials(String login, String password) {}
}
