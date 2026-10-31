package com.shastage.ujin.domain.model;

import java.util.List;
import java.util.UUID;

public final class Catalog {
    private Catalog() {}

    public record Signal(
            String key,
            String title,
            String group,
            String management,
            String valueType,
            String control,
            String unit,
            Double min,
            Double max,
            List<Double> enumValues) {}

    public record Event(String key, String title, String signalKey, String emission) {}

    public record DeviceWrite(
            String title,
            String family,
            List<Signal> signals,
            List<Event> events,
            Long expectedVersion) {}

    public record Device(
            UUID id,
            long version,
            String title,
            String family,
            List<Signal> signals,
            List<Event> events,
            String platformLink) {}

    public record Trigger(
            UUID deviceId, String signalKey, String operator, Double value, String eventKey) {}

    public record Action(UUID deviceId, String signalKey, Double value) {}

    public record ScenarioWrite(
            String title,
            Boolean enabled,
            String executionMode,
            Trigger trigger,
            Action action,
            Long expectedVersion) {}

    public record Scenario(
            UUID id,
            long version,
            String title,
            boolean enabled,
            String executionMode,
            Trigger trigger,
            Action action) {}

    public record Page<T>(List<T> items, int page, int size, long total) {}

    public record Dependencies(
            UUID deviceId, List<UUID> scenarioIds, List<UUID> pendingOperationIds) {}
}
