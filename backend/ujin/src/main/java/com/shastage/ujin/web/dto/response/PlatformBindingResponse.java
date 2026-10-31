package com.shastage.ujin.web.dto.response;

import java.util.List;

public record PlatformBindingResponse(
        String deviceId, String serialNumber, List<Mapping> signalMappings, long version) {

    public record Mapping(String signalKey, String platformName) {}
}
