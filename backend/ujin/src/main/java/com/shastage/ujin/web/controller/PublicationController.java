package com.shastage.ujin.web.controller;

import com.shastage.ujin.domain.service.PublicationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/publications")
public class PublicationController {
    private final PublicationService service;
    public PublicationController(PublicationService service) { this.service = service; }
    private String owner() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private String token(HttpSession session) {
        return session.getAttribute("UJIN_TOKEN") instanceof String token ? token : null;
    }
    @PostMapping
    public PublicationService.Receipt publish(@PathVariable UUID deviceId,
            @RequestBody PublicationService.Write write, HttpSession session) {
        return service.publish(deviceId, write, owner(), token(session));
    }
    @GetMapping
    public List<PublicationService.Receipt> list(@PathVariable UUID deviceId) {
        return service.list(deviceId, owner());
    }
    @GetMapping("/{publicationId}")
    public PublicationService.Receipt get(@PathVariable UUID deviceId, @PathVariable UUID publicationId) {
        return service.get(deviceId, publicationId, owner());
    }
    @PostMapping("/{publicationId}/readback")
    public PublicationService.Receipt check(@PathVariable UUID deviceId,
            @PathVariable UUID publicationId, HttpSession session) {
        return service.check(deviceId, publicationId, owner(), token(session));
    }
}
