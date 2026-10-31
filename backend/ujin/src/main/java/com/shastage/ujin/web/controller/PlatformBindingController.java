package com.shastage.ujin.web.controller;

import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.model.PlatformBinding;
import com.shastage.ujin.domain.service.PlatformBindingService;
import com.shastage.ujin.web.dto.request.PlatformBindingRequest;
import com.shastage.ujin.web.dto.response.PlatformBindingResponse;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
public class PlatformBindingController {
    private final PlatformBindingService service;

    public PlatformBindingController(PlatformBindingService service) {
        this.service = service;
    }

    private String currentOwner() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }

    private static String ujinToken(HttpSession session) {
        Object token = session.getAttribute("UJIN_TOKEN");
        return token instanceof String value ? value : null;
    }

    @GetMapping("/{deviceId}/platform-binding")
    public PlatformBindingResponse binding(@PathVariable UUID deviceId) {
        return toResponse(service.getBinding(deviceId, currentOwner()));
    }

    @PutMapping("/{deviceId}/platform-binding")
    public ResponseEntity<PlatformBindingResponse> save(
            @PathVariable UUID deviceId,
            @RequestBody PlatformBindingRequest request,
            HttpSession session) {
        if (request == null) {
            throw new CatalogException(400, "INVALID_REQUEST", "Request body is required");
        }
        var saved =
                service.putBinding(deviceId, toWrite(request), currentOwner(), ujinToken(session));
        return ResponseEntity.status(saved.created() ? 201 : 200).body(toResponse(saved.view()));
    }

    private static PlatformBinding.Write toWrite(PlatformBindingRequest request) {
        var mappings =
                request.signalMappings() == null
                        ? null
                        : request.signalMappings().stream()
                                .map(m ->
                                        m == null
                                                ? null
                                                : new PlatformBinding.Mapping(
                                                        m.signalKey(), m.platformName()))
                                .toList();
        return new PlatformBinding.Write(request.serialNumber(), mappings, request.expectedVersion());
    }

    private static PlatformBindingResponse toResponse(PlatformBinding.View view) {
        return new PlatformBindingResponse(
                view.deviceId().toString(),
                view.serialNumber(),
                view.signalMappings().stream()
                        .map(m -> new PlatformBindingResponse.Mapping(m.signalKey(), m.platformName()))
                        .toList(),
                view.version());
    }
}
