package com.shastage.ujin.web.controller;

import com.shastage.ujin.domain.service.LocalRuntimeService;
import com.shastage.ujin.domain.service.PlatformBindingService;
import com.shastage.ujin.domain.service.UjinCommandService;
import com.shastage.ujin.web.dto.request.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/v1")
public class RuntimeController {
    private final LocalRuntimeService service;
    private final PlatformBindingService bindingService;
    private final UjinCommandService commandService;

    private String currentOwner() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }

    private static String ujinToken(HttpSession session) {
        Object token = session.getAttribute("UJIN_TOKEN");
        return token instanceof String value ? value : null;
    }

    public RuntimeController(
            LocalRuntimeService service,
            PlatformBindingService bindingService,
            UjinCommandService commandService) {
        this.service = service;
        this.bindingService = bindingService;
        this.commandService = commandService;
    }

    @GetMapping("/devices/{deviceId}/state")
    public Map<String, Object> deviceState(
            @PathVariable UUID deviceId,
            @RequestParam(defaultValue = "LOCAL") String mode,
            HttpSession session
    ) {
        if ("UJIN".equals(mode)) {
            Object token = session.getAttribute("UJIN_TOKEN");
            return bindingService.readUjinState(
                    deviceId, currentOwner(), token instanceof String value ? value : null);
        }
        return service.getDeviceState(deviceId, mode, currentOwner());
    }

    @PutMapping("/devices/{deviceId}/availability")
    public Map<String, Object> availability(@PathVariable UUID deviceId, @RequestBody AvailabilityRequest request) {
        if (request == null)
            throw new com.shastage.ujin.domain.exception.CatalogException(400, "INVALID_REQUEST", "Request body is required");
        return service.updateAvailability(deviceId, request.availability(), request.executionMode(), currentOwner());
    }

    @PostMapping("/devices/{deviceId}/stimuli")
    public ResponseEntity<Map<String, Object>> stimulus(
            @PathVariable UUID deviceId,
            @RequestBody StimulusRequest request
    ) {
        if (request == null) {
            throw new com.shastage.ujin.domain.exception.CatalogException(400, "INVALID_REQUEST", "Request body is required");
        }
        var result = service.processStimulus(deviceId, request.eventId(), request.kind(), request.value(), currentOwner());
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/events/{eventId}")
    public Map<String, Object> event(@PathVariable UUID eventId) {
        return service.getEvent(eventId, currentOwner());
    }

    @PostMapping("/devices/{deviceId}/commands")
    public ResponseEntity<Map<String, Object>> command(
            @PathVariable UUID deviceId,
            @RequestBody CommandRequest request,
            HttpSession session
    ) {
        if (request == null) {
            throw new com.shastage.ujin.domain.exception.CatalogException(400, "INVALID_REQUEST", "Request body is required");
        }
        if ("UJIN".equals(request.executionMode())) {
            var result = commandService.send(
                    deviceId, request.commandId(), request.signalKey(), request.value(),
                    currentOwner(), ujinToken(session));
            return ResponseEntity.accepted().body(result);
        }
        var result = service.processCommand(
                deviceId, request.commandId(), request.signalKey(), request.value(), request.executionMode(), currentOwner()
        );
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/commands/{commandId}")
    public Map<String, Object> command(@PathVariable UUID commandId) {
        return service.getCommand(commandId, currentOwner());
    }

    @PostMapping("/commands/{commandId}/check")
    public Map<String, Object> checkCommand(
            @PathVariable UUID commandId, HttpSession session) {
        return commandService.check(commandId, currentOwner(), ujinToken(session));
    }

    @PostMapping("/commands/{commandId}/resolution")
    public Map<String, Object> resolveCommand(
            @PathVariable UUID commandId, @RequestBody UnknownResolutionRequest request) {
        if (request == null) {
            throw new com.shastage.ujin.domain.exception.CatalogException(400, "INVALID_REQUEST", "Request body is required");
        }
        return commandService.resolveUnknown(commandId, request.note(), currentOwner());
    }

    @GetMapping("/runs")
    public Map<String, Object> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) UUID scenarioId
    ) {
        return service.getRuns(page, size, eventId, scenarioId, currentOwner());
    }

    @GetMapping("/runs/{runId}")
    public Map<String, Object> run(@PathVariable UUID runId) {
        return service.getRun(runId, currentOwner());
    }
}
