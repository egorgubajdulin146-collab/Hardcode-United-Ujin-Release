package com.shastage.ujin.web.dto.request;

import java.util.List;

public record PlatformBindingRequest(
        String serialNumber, List<Mapping> signalMappings, Long expectedVersion) {

    public record Mapping(String signalKey, String platformName) {}
}
