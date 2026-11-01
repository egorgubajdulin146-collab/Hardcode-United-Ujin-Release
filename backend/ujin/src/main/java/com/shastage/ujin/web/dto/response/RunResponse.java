package com.shastage.ujin.web.dto.response;

import java.util.*;

public record RunResponse(
    UUID id,
    UUID eventId,
    UUID scenarioId,
    long scenarioVersion,
    String executionMode,
    String status,
    String reason,
    String createdAt,
    InputDto input,
    ActionDto action,
    List<EntryDto> entries
) {
    public record InputDto(String signalKey, double value, String updatedAt) {}
    public record ActionDto(UUID deviceId, String signalKey, double value) {}
    public record EntryDto(String at, String stage, String message) {}
}
