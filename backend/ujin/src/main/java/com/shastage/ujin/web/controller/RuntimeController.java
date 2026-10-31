package com.shastage.ujin.web.controller;

import com.shastage.ujin.domain.service.LocalRuntimeService;
import com.shastage.ujin.web.dto.request.*;
import com.shastage.ujin.web.dto.response.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RuntimeController {
    private final LocalRuntimeService service;

    public RuntimeController(LocalRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/devices/{deviceId}/state")
    public Map<String, Object> deviceState(
            @PathVariable UUID deviceId,
            @RequestParam(defaultValue = "LOCAL") String mode
    ) {
        return service.getDeviceState(deviceId, mode);
    }

    @PostMapping("/devices/{deviceId}/stimuli")
    public ResponseEntity<Map<String, Object>> stimulus(
            @PathVariable UUID deviceId,
            @RequestBody StimulusRequest request
    ) {
        var result = service.processStimulus(deviceId, request.eventId(), request.kind(), request.value());
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/events/{eventId}")
    public Map<String, Object> event(@PathVariable UUID eventId) {
        return service.getEvent(eventId);
    }

    @PostMapping("/devices/{deviceId}/commands")
    public ResponseEntity<Map<String, Object>> command(
            @PathVariable UUID deviceId,
            @RequestBody CommandRequest request
    ) {
        var result = service.processCommand(
                deviceId, request.commandId(), request.signalKey(), request.value(), request.executionMode()
        );
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/commands/{commandId}")
    public Map<String, Object> command(@PathVariable UUID commandId) {
        return service.getCommand(commandId);
    }

    @GetMapping("/runs")
    public Map<String, Object> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) UUID scenarioId
    ) {
        return service.getRuns(page, size, eventId, scenarioId);
    }

    @GetMapping("/runs/{runId}")
    public Map<String, Object> run(@PathVariable UUID runId) {
        return service.getRun(runId);
    }
}
