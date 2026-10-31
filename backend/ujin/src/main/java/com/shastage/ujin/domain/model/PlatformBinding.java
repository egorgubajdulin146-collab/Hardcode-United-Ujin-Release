package com.shastage.ujin.domain.model;

import java.util.List;
import java.util.UUID;

public final class PlatformBinding {
    private PlatformBinding() {}

    public record Mapping(String signalKey, String platformName) {}

    public record Write(String serialNumber, List<Mapping> signalMappings, Long expectedVersion) {}

    public record View(UUID deviceId, String serialNumber, List<Mapping> signalMappings, long version) {}

    public record Saved(View view, boolean created) {}
}
