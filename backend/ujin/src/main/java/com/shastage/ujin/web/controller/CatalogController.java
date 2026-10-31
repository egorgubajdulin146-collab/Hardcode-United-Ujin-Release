package com.shastage.ujin.web.controller;

import com.shastage.ujin.domain.service.CatalogService;
import com.shastage.ujin.web.dto.request.*;
import com.shastage.ujin.web.dto.response.*;
import com.shastage.ujin.web.mapper.CatalogDtoMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService service;
    private final CatalogDtoMapper mapper;

    private String currentOwner() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }

    public CatalogController(CatalogService service, CatalogDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/devices")
    public PageResponse<DeviceResponse> devices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return mapper.toResponse(service.devices(page, size, currentOwner()), mapper::toResponse);
    }

    @GetMapping("/devices/{id}")
    public DeviceResponse device(@PathVariable UUID id) {
        return mapper.toResponse(service.device(id, currentOwner()));
    }

    @PostMapping("/devices")
    public ResponseEntity<DeviceResponse> create(@RequestBody DeviceCreateRequest data) {
        var device = service.create(mapper.toDomain(data), currentOwner());
        return ResponseEntity.created(URI.create("/api/v1/devices/" + device.id()))
                .body(mapper.toResponse(device));
    }

    @PutMapping("/devices/{id}")
    public DeviceResponse update(@PathVariable UUID id, @RequestBody DeviceUpdateRequest data) {
        return mapper.toResponse(service.update(id, mapper.toDomain(data), currentOwner()));
    }

    @GetMapping("/devices/{id}/dependencies")
    public DeviceDependenciesResponse dependencies(@PathVariable UUID id) {
        return mapper.toResponse(service.dependencies(id, currentOwner()));
    }

    @DeleteMapping("/devices/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam long expectedVersion) {
        service.delete(id, expectedVersion, currentOwner());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scenarios")
    public PageResponse<ScenarioResponse> scenarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return mapper.toResponse(service.scenarios(page, size, currentOwner()), mapper::toResponse);
    }

    @GetMapping("/scenarios/{id}")
    public ScenarioResponse scenario(@PathVariable UUID id) {
        return mapper.toResponse(service.scenario(id, currentOwner()));
    }

    @PostMapping("/scenarios")
    public ResponseEntity<ScenarioResponse> create(@RequestBody ScenarioCreateRequest data) {
        var scenario = service.create(mapper.toDomain(data), currentOwner());
        return ResponseEntity.created(URI.create("/api/v1/scenarios/" + scenario.id()))
                .body(mapper.toResponse(scenario));
    }

    @PutMapping("/scenarios/{id}")
    public ScenarioResponse update(@PathVariable UUID id, @RequestBody ScenarioUpdateRequest data) {
        return mapper.toResponse(service.update(id, mapper.toDomain(data), currentOwner()));
    }
}
