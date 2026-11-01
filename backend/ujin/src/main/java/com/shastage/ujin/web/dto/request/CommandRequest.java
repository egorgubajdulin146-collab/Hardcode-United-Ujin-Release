package com.shastage.ujin.web.dto.request;

import java.util.UUID;

public record CommandRequest(UUID commandId, String signalKey, Double value, String executionMode) {}
