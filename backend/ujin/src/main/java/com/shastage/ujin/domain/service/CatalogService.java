package com.shastage.ujin.domain.service;

import com.shastage.ujin.datasource.repository.jpa.LocalSignalStateJpaRepository;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.repository.CatalogStore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class CatalogService {
    private final CatalogStore repository;
    private final LocalSignalStateJpaRepository stateRepo;

    public CatalogService(CatalogStore repository, LocalSignalStateJpaRepository stateRepo) {
        this.repository = repository;
        this.stateRepo = stateRepo;
    }

    public Page<Device> devices(int page, int size, String owner) {
        validatePage(page, size);
        return repository.devices(page, size, owner);
    }

    public Page<Scenario> scenarios(int page, int size, String owner) {
        validatePage(page, size);
        return repository.scenarios(page, size, owner);
    }

    public Device device(UUID id, String owner) {
        return repository
                .device(id, owner)
                .orElseThrow(
                        () -> new CatalogException(404, "DEVICE_NOT_FOUND", "Device not found"));
    }

    public Scenario scenario(UUID id, String owner) {
        return repository
                .scenario(id, owner)
                .orElseThrow(() ->
                        new CatalogException(404, "SCENARIO_NOT_FOUND", "Scenario not found"));
    }

    public Device create(DeviceWrite data, String owner) {
        repository.lock();
        validate(data);
        var device =
                new Device(
                        UUID.randomUUID(),
                        1,
                        data.title(),
                        data.family(),
                        data.signals(),
                        data.events(),
                        "NOT_CONFIGURED");
        return repository.insert(device, owner);
    }

    public Device update(UUID id, DeviceWrite data, String owner) {
        repository.lock();
        var old = device(id, owner);
        version(data.expectedVersion(), old.version());
        validate(data);
        if (!dependencies(id, owner).scenarioIds().isEmpty()
                && (!old.signals().equals(data.signals())
                        || !old.events().equals(data.events())
                        || !old.family().equals(data.family())))
            throw new CatalogException(
                    409, "DEVICE_IN_USE", "Device declaration is referenced by scenarios");
        var device =
                new Device(
                        id,
                        old.version() + 1,
                        data.title(),
                        data.family(),
                        data.signals(),
                        data.events(),
                        old.platformLink());
        return repository.update(device, owner);
    }

    public Dependencies dependencies(UUID id, String owner) {
        device(id, owner);
        var ids = repository.dependencies(id, owner);
        return new Dependencies(id, ids, List.of());
    }

    public void delete(UUID id, long expectedVersion, String owner) {
        repository.lock();
        var old = device(id, owner);
        version(expectedVersion, old.version());
        if (!dependencies(id, owner).scenarioIds().isEmpty())
            throw new CatalogException(409, "DEVICE_IN_USE", "Device is referenced by scenarios");
        // Очищаем локальное состояние сигналов
        stateRepo.deleteByDeviceIdAndOwner(id, owner);
        repository.deleteDevice(id, owner);
    }

    public Scenario create(ScenarioWrite data, String owner) {
        repository.lock();
        validate(data, null, owner);
        var scenario =
                new Scenario(
                        UUID.randomUUID(),
                        1,
                        data.title(),
                        data.enabled(),
                        data.executionMode(),
                        data.trigger(),
                        data.action());
        return repository.insert(scenario, owner);
    }

    public Scenario update(UUID id, ScenarioWrite data, String owner) {
        repository.lock();
        var old = scenario(id, owner);
        version(data.expectedVersion(), old.version());
        validate(data, id, owner);
        var scenario =
                new Scenario(
                        id,
                        old.version() + 1,
                        data.title(),
                        data.enabled(),
                        data.executionMode(),
                        data.trigger(),
                        data.action());
        return repository.update(scenario, owner);
    }

    private static void validatePage(int page, int size) {
        require(
                page >= 0 && size >= 1 && size <= 100 && (long) page * size <= Integer.MAX_VALUE,
                "Invalid page or size");
    }

    private static void version(Long expected, long actual) {
        require(expected != null && expected > 0, "expectedVersion is required and positive");
        if (expected != actual)
            throw new CatalogException(
                    409, "VERSION_CONFLICT", "Object has changed; reload before saving");
    }

    private static void validate(DeviceWrite data) {
        text(data.title(), 120);
        text(data.family(), 64);
        key(data.family());
        require(
                data.signals() != null
                        && !data.signals().isEmpty()
                        && data.signals().size() <= 100
                        && data.events() != null
                        && data.events().size() <= 100,
                "signals and events are required");
        Set<String> keys = new HashSet<>();
        for (var s : data.signals()) {
            require(s != null, "Invalid signal");
            text(s.key(), 64);
            key(s.key());
            text(s.title(), 120);
            text(s.control(), 40);
            require(s.unit() == null || s.unit().length() <= 24, "Unit too long");
            require(keys.add(s.key()), "Duplicate signal key");
            require("NUMBER".equals(s.valueType()), "Only NUMBER signals are supported");
            require(
                    ("property".equals(s.group()) && "r".equals(s.management()))
                            || ("capability".equals(s.group())
                                    && Set.of("rw", "w")
                                            .contains(Objects.toString(s.management(), "")))
                            || ("setting".equals(s.group()) && "rw".equals(s.management())),
                    "Invalid group/management");
            require(
                    (s.min() == null || Double.isFinite(s.min()))
                            && (s.max() == null || Double.isFinite(s.max())),
                    "Non-finite bounds");
            require(s.min() == null || s.max() == null || s.min() <= s.max(), "min exceeds max");
            if (s.enumValues() != null) {
                require(
                        !s.enumValues().isEmpty()
                                && new HashSet<>(s.enumValues()).size() == s.enumValues().size(),
                        "Invalid enum values");
                for (Double v : s.enumValues()) value(s, v);
            }
        }
        Set<String> events = new HashSet<>();
        for (var e : data.events()) {
            require(e != null, "Invalid event");
            text(e.key(), 64);
            key(e.key());
            text(e.title(), 120);
            require(
                    events.add(e.key()) && "CHANGE".equals(e.emission()),
                    "Invalid or duplicate event");
            var signal =
                    data.signals().stream()
                            .filter(s -> s.key().equals(e.signalKey()))
                            .findFirst()
                            .orElseThrow(() -> invalid("Unknown event signal"));
            require(!"w".equals(signal.management()), "Event requires readable signal");
        }
    }

    private void validate(ScenarioWrite data, UUID excludedId, String owner) {
        text(data.title(), 120);
        require(
                data.enabled() != null
                        && Set.of("LOCAL", "UJIN")
                                .contains(Objects.toString(data.executionMode(), "")),
                "Invalid enabled or executionMode");
        if (data.enabled() && "UJIN".equals(data.executionMode()))
            throw new CatalogException(503, "MODE_UNAVAILABLE", "Ujin scenario execution is not connected; save a disabled draft or use LOCAL");
        require(data.trigger() != null && data.action() != null, "trigger and action are required");
        var t = data.trigger();
        var a = data.action();
        require(t.deviceId() != null && a.deviceId() != null, "Device IDs are required");
        var source = device(t.deviceId(), owner);
        var target = device(a.deviceId(), owner);
        var input = signal(source, t.signalKey());
        var output = signal(target, a.signalKey());
        require("EQ".equals(t.operator()) && !"w".equals(input.management()), "Invalid trigger");
        require(
                source.events().stream()
                        .anyMatch(e ->
                                e.key().equals(t.eventKey())
                                        && e.signalKey().equals(t.signalKey())),
                "Unknown trigger event");
        if ("r".equals(output.management()))
            throw new CatalogException(422, "SIGNAL_READ_ONLY", "Action signal is read-only");
        value(input, t.value());
        value(output, a.value());
        if (t.deviceId().equals(a.deviceId()) && t.signalKey().equals(a.signalKey()))
            throw new CatalogException(422, "SCENARIO_CYCLE", "A signal cannot trigger itself");
        var adjacency = new HashMap<String, Set<String>>();
        for (var s : repository.scenarios(owner)) {
            if (s.id().equals(excludedId)) continue;
            adjacency
                    .computeIfAbsent(
                            s.trigger().deviceId() + ":" + s.trigger().signalKey(),
                            k -> new HashSet<>())
                    .add(s.action().deviceId() + ":" + s.action().signalKey());
        }
        if (reachable(
                adjacency,
                a.deviceId() + ":" + a.signalKey(),
                t.deviceId() + ":" + t.signalKey(),
                new HashSet<>()))
            throw new CatalogException(422, "SCENARIO_CYCLE", "Scenario introduces a cycle");
    }

    private static boolean reachable(
            Map<String, Set<String>> graph, String from, String to, Set<String> seen) {
        if (from.equals(to)) return true;
        if (!seen.add(from)) return false;
        return graph.getOrDefault(from, Set.of()).stream()
                .anyMatch(next -> reachable(graph, next, to, seen));
    }

    private static Signal signal(Device d, String key) {
        return d.signals().stream()
                .filter(s -> s.key().equals(key))
                .findFirst()
                .orElseThrow(() -> invalid("Unknown signal"));
    }

    private static void value(Signal s, Double v) {
        require(
                v != null
                        && Double.isFinite(v)
                        && (s.min() == null || v >= s.min())
                        && (s.max() == null || v <= s.max())
                        && (s.enumValues() == null || s.enumValues().contains(v)),
                "Value outside signal domain");
    }

    private static void key(String value) {
        require(value.matches("^[a-z][a-z0-9_]*$"), "Invalid key");
    }

    private static void text(String value, int max) {
        require(
                value != null && !value.isBlank() && value.length() <= max,
                "Invalid or missing text");
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw invalid(detail);
    }

    private static CatalogException invalid(String detail) {
        return new CatalogException(422, "VALIDATION_ERROR", detail);
    }
}
