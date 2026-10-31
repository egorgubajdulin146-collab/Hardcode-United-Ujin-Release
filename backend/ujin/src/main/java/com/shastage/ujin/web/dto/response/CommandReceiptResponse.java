package com.shastage.ujin.web.dto.response;

import java.util.UUID;

public record CommandReceiptResponse(UUID commandId, UUID deviceId, String status, String executionMode, ResolutionDto resolution) {
    public record ResolutionDto(String note, String resolvedAt, String kind) {}
}
