package com.shastage.ujin.web.dto.response;

import java.util.*;

public record DeviceStateResponse(UUID deviceId, String executionMode, List<SignalValueDto> values, String availability) {
    public record SignalValueDto(String signalKey, double value, String updatedAt) {}
}
