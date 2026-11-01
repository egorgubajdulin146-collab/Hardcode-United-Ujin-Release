package com.shastage.ujin.domain.service;

import com.shastage.ujin.datasource.entity.*;
import com.shastage.ujin.datasource.repository.jpa.*;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.model.Catalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class LocalRuntimeService {
    private final LocalSignalStateJpaRepository stateRepo;
    private final EventJpaRepository eventRepo;
    private final CommandJpaRepository commandRepo;
    private final RunJpaRepository runRepo;
    private final LocalAvailabilityJpaRepository availabilityRepo;
    private final CatalogService catalogService;
    private final com.shastage.ujin.domain.repository.CatalogStore catalogStore;

    public LocalRuntimeService(
            LocalSignalStateJpaRepository stateRepo,
            EventJpaRepository eventRepo,
            CommandJpaRepository commandRepo,
            RunJpaRepository runRepo,
            LocalAvailabilityJpaRepository availabilityRepo,
            CatalogService catalogService,
            com.shastage.ujin.domain.repository.CatalogStore catalogStore
    ) {
        this.stateRepo = stateRepo;
        this.eventRepo = eventRepo;
        this.commandRepo = commandRepo;
        this.runRepo = runRepo;
        this.availabilityRepo = availabilityRepo;
        this.catalogService = catalogService;
        this.catalogStore = catalogStore;
    }

    private PythonEmulatorProperties python;
    @org.springframework.beans.factory.annotation.Autowired
    public void setPythonEmulatorProperties(PythonEmulatorProperties properties) { python = properties; }
    private boolean pythonTarget(UUID id, String owner) { return python != null && python.target(id, owner); }
    private void requireNoPendingPython(UUID id, String owner) {
        if (commandRepo.findByDeviceIdAndOwnerAndStatusIn(id, owner, List.of("PENDING", "UNKNOWN")).stream()
                .anyMatch(c -> c.getResolutionNote() == null))
            throw new CatalogException(409, "COMMAND_UNRESOLVED", "Есть незавершённая команда Python-эмулятору");
    }
    private void queuePython(UUID id, UUID commandId, String signalKey, double value, String owner, String now) {
        if (!"lamp".equals(signalKey) || (value != 0 && value != 1))
            throw new CatalogException(422, "PYTHON_SIGNAL_UNSUPPORTED", "Python-стенд поддерживает только lamp 0/1");
        requireNoPendingPython(id, owner);
        var command = new CommandEntity(); command.setCommandId(commandId); command.setDeviceId(id);
        command.setSignalKey(signalKey); command.setValue(value); command.setExecutionMode("LOCAL");
        command.setStatus("PENDING"); command.setReason("PYTHON_QUEUED"); command.setCreatedAt(now);command.setUpdatedAt(now);command.setOwner(owner);
        commandRepo.saveAndFlush(command);
    }

    // ===== GET /devices/{id}/state =====
    @Transactional(readOnly = true)
    public Map<String, Object> getDeviceState(UUID deviceId, String mode, String owner) {
        if (!"LOCAL".equals(mode)) {
            throw new CatalogException(503, "MODE_UNAVAILABLE", "Mode " + mode + " is not available in this runtime");
        }
        var device = catalogService.device(deviceId, owner);
        var states = stateRepo.findByDeviceIdAndOwner(deviceId, owner);

        var values = states.stream().map(s -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("signalKey", s.getSignalKey());
            v.put("value", s.getValue());
            v.put("updatedAt", s.getUpdatedAt().toString());
            return v;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceId", deviceId.toString());
        result.put("executionMode", mode);
        result.put("values", values);
        result.put("availability", availability(deviceId));
        return result;
    }

    private String availability(UUID deviceId) {
        return availabilityRepo.findById(deviceId).map(LocalAvailabilityEntity::getAvailability).orElse("ONLINE");
    }

    public Map<String, Object> updateAvailability(UUID deviceId, String status, String mode, String owner) {
        if (!"LOCAL".equals(mode)) {
            if ("UJIN".equals(mode))
                throw new CatalogException(503, "MODE_UNAVAILABLE", "Only local emulator availability can be changed");
            throw new CatalogException(422, "VALIDATION_ERROR", "executionMode must be LOCAL");
        }
        if (!"ONLINE".equals(status) && !"OFFLINE".equals(status))
            throw new CatalogException(422, "VALIDATION_ERROR", "availability must be ONLINE or OFFLINE");
        setAvailability(deviceId, status, owner);
        return getDeviceState(deviceId, "LOCAL", owner);
    }

    // Internal emulator state also permits UNKNOWN for runtime diagnostics.
    public void setAvailability(UUID deviceId, String status, String owner) {
        catalogStore.lock();
        catalogService.device(deviceId, owner);
        if (status == null || !Set.of("ONLINE", "OFFLINE", "UNKNOWN").contains(status))
            throw new CatalogException(422, "VALIDATION_ERROR", "Invalid availability");
        availabilityRepo.save(new LocalAvailabilityEntity(deviceId, status));
    }

    private void requireOnline(UUID deviceId) {
        if (!"ONLINE".equals(availability(deviceId)))
            throw new CatalogException(409, "DEVICE_OFFLINE", "Device is not online");
    }

    // ===== POST /devices/{id}/stimuli =====
    public Map<String, Object> processStimulus(UUID deviceId, UUID eventId, String kind, Integer value, String owner) {
        catalogStore.lock();
        // Валидация обязательных полей
        if (eventId == null) throw new CatalogException(422, "VALIDATION_ERROR", "eventId is required");
        if (kind == null) throw new CatalogException(422, "VALIDATION_ERROR", "kind is required");
        if (value == null) throw new CatalogException(422, "VALIDATION_ERROR", "value is required");

        var device = catalogService.device(deviceId, owner);

        // Проверка глобальной коллизии eventId
        if (eventRepo.existsByEventId(eventId)) {
            var existing = eventRepo.findByEventIdAndOwner(eventId, owner);
            if (existing.isPresent()) {
                var ex = existing.get();
                if (!ex.getDeviceId().equals(deviceId) || !ex.getKind().equals(kind) || ex.getValue() != value) {
                    throw new CatalogException(409, "IDEMPOTENCY_CONFLICT", "eventId already used with different parameters");
                }
                return buildEventReceipt(ex);
            }
            // eventId принадлежит другому пользователю
            throw new CatalogException(404, "EVENT_NOT_FOUND", "Event not found");
        }

        requireOnline(deviceId);
        // Валидация: WET только для leak, value 0/1
        if (!"WET".equals(kind)) {
            throw new CatalogException(422, "STIMULUS_UNSUPPORTED", "Unknown stimulus kind: " + kind);
        }
        if (value != 0 && value != 1) {
            throw new CatalogException(422, "VALIDATION_ERROR", "WET value must be 0 or 1");
        }
        if (!"leak".equals(device.family())) {
            throw new CatalogException(422, "STIMULUS_UNSUPPORTED", "WET only supported for leak devices");
        }

        // Проверяем, что сигнал leak существует в декларации
        var leakSignal = device.signals().stream()
                .filter(s -> "leak".equals(s.key()))
                .findFirst()
                .orElseThrow(() -> new CatalogException(422, "VALIDATION_ERROR", "Device has no 'leak' signal"));

        // Проверяем диапазон
        if (leakSignal.min() != null && value < leakSignal.min()) {
            throw new CatalogException(422, "VALIDATION_ERROR", "Value below minimum");
        }
        if (leakSignal.max() != null && value > leakSignal.max()) {
            throw new CatalogException(422, "VALIDATION_ERROR", "Value above maximum");
        }

        if ("w".equals(leakSignal.management()) || (leakSignal.enumValues() != null && !leakSignal.enumValues().contains(value.doubleValue())))
            throw new CatalogException(422, "VALIDATION_ERROR", "Invalid stimulus signal domain");
        if (device.events().stream().noneMatch(e -> e.signalKey().equals("leak") && e.emission().equals("CHANGE")))
            throw new CatalogException(422, "VALIDATION_ERROR", "Stimulus requires a declared change event");

        // Создаём событие
        var event = new EventEntity();
        event.setEventId(eventId);
        event.setDeviceId(deviceId);
        event.setKind(kind);
        event.setValue(value);
        event.setStatus("ACCEPTED");
        event.setCreatedAt(java.time.Instant.now().toString());
        event.setOwner(owner);
        eventRepo.save(event);

        // Compare persisted owner-scoped state under the catalog lock before updating it.
        boolean changed = stateRepo.findByDeviceIdAndSignalKeyAndOwner(deviceId, "leak", owner)
                .map(previous -> previous.getValue() != value.doubleValue())
                .orElse(true);
        if (changed) updateSignalState(deviceId, "leak", value, owner);

        // Запускаем подходящие LOCAL-сценарии
        var runIds = processScenarios(deviceId, "leak", value, eventId, owner, changed);

        event.setStatus("PROCESSED");
        event.setProcessedAt(java.time.Instant.now().toString());
        event.setOwner(owner);
        eventRepo.save(event);

        return buildEventReceipt(event);
    }

    // ===== POST /devices/{id}/commands =====
    public Map<String, Object> processCommand(UUID deviceId, UUID commandId, String signalKey, Double value, String mode, String owner) {
        catalogStore.lock();
        // Валидация обязательных полей
        if (commandId == null) throw new CatalogException(422, "VALIDATION_ERROR", "commandId is required");
        if (signalKey == null || signalKey.isBlank()) throw new CatalogException(422, "VALIDATION_ERROR", "signalKey is required");
        if (value == null || !Double.isFinite(value)) throw new CatalogException(422, "VALIDATION_ERROR", "value must be a finite number");
        if (mode == null) throw new CatalogException(422, "VALIDATION_ERROR", "executionMode is required");

        // UJIN не поддерживается в LOCAL-контуре
        if ("UJIN".equals(mode)) {
            throw new CatalogException(503, "MODE_UNAVAILABLE", "UJIN mode is not available in this runtime");
        }
        if (!"LOCAL".equals(mode)) {
            throw new CatalogException(422, "VALIDATION_ERROR", "Unknown executionMode: " + mode);
        }

        var device = catalogService.device(deviceId, owner);

        if (python != null && python.source(deviceId, owner))
            throw new CatalogException(422, "PYTHON_SIGNAL_UNSUPPORTED", "Python-датчик не принимает команды");

        // Проверка глобальной коллизии commandId
        if (commandRepo.existsByCommandId(commandId)) {
            var existing = commandRepo.findByCommandIdAndOwner(commandId, owner);
            if (existing.isPresent()) {
                var ex = existing.get();
                if (!ex.getDeviceId().equals(deviceId) || !ex.getSignalKey().equals(signalKey) || ex.getValue() != value || !ex.getExecutionMode().equals(mode)) {
                    throw new CatalogException(409, "IDEMPOTENCY_CONFLICT", "commandId already used with different parameters");
                }
                return buildCommandReceipt(ex);
            }
            // commandId принадлежит другому пользователю
            throw new CatalogException(404, "COMMAND_NOT_FOUND", "Command not found");
        }

        requireOnline(deviceId);
        // Валидация сигнала
        var signal = device.signals().stream()
                .filter(s -> s.key().equals(signalKey))
                .findFirst()
                .orElseThrow(() -> new CatalogException(422, "VALIDATION_ERROR", "Unknown signal: " + signalKey));

        if ("r".equals(signal.management())) {
            throw new CatalogException(422, "SIGNAL_READ_ONLY", "Signal is read-only: " + signalKey);
        }

        if (signal.min() != null && value < signal.min()) {
            throw new CatalogException(422, "VALIDATION_ERROR", "Value below minimum");
        }
        if (signal.max() != null && value > signal.max()) {
            throw new CatalogException(422, "VALIDATION_ERROR", "Value above maximum");
        }
        if (signal.enumValues() != null && !signal.enumValues().isEmpty()) {
            boolean match = signal.enumValues().stream().anyMatch(v -> Double.compare(v, value) == 0);
            if (!match) {
                throw new CatalogException(422, "VALIDATION_ERROR", "Value not in enumValues");
            }
        }

        if (pythonTarget(deviceId, owner)) {
            queuePython(deviceId, commandId, signalKey, value, owner, Instant.now().toString());
            return buildCommandReceipt(commandRepo.findByCommandIdAndOwner(commandId, owner).orElseThrow());
        }
        // Создаём команду
        var command = new CommandEntity();
        command.setCommandId(commandId);
        command.setDeviceId(deviceId);
        command.setSignalKey(signalKey);
        command.setValue(value);
        command.setExecutionMode(mode);
        command.setStatus("CONFIRMED");
        command.setCreatedAt(java.time.Instant.now().toString());
        command.setConfirmedAt(java.time.Instant.now().toString());
        command.setOwner(owner);
        commandRepo.save(command);

        // Обновляем состояние
        updateSignalState(deviceId, signalKey, value, owner);

        return buildCommandReceipt(command);
    }

    // ===== Обработка сценариев =====
    private List<UUID> processScenarios(UUID sourceDeviceId, String signalKey, double value, UUID eventId, String owner, boolean changed) {
        var scenarios = loadAllScenarios(owner);
        var runIds = new ArrayList<UUID>();
        var now = java.time.Instant.now().toString();

        for (var scenario : scenarios) {
            var trigger = scenario.trigger();

            // Проверяем триггер ДО проверки enabled
            if (!sourceDeviceId.equals(trigger.deviceId()) || !signalKey.equals(trigger.signalKey())) {
                continue;
            }

            // Только LOCAL-сценарии
            if (!"LOCAL".equals(scenario.executionMode())) {
                continue;
            }

            if (!changed) {
                createSkippedRun(scenario, eventId, "NO_STATE_CHANGE", signalKey, value, now, owner);
                continue;
            }

            // Выключенные — SKIPPED
            if (!scenario.enabled()) {
                createSkippedRun(scenario, eventId, "SCENARIO_DISABLED", signalKey, value, now, owner);
                continue;
            }

            // Проверяем событие (eventKey)
            var sourceDevice = catalogService.device(sourceDeviceId, owner);
            boolean eventMatches = sourceDevice.events().stream()
                    .anyMatch(e -> e.key().equals(trigger.eventKey()) && e.signalKey().equals(trigger.signalKey()));
            if (!eventMatches) {
                createSkippedRun(scenario, eventId, "EVENT_MISMATCH", signalKey, value, now, owner);
                continue;
            }

            // Проверяем условие EQ — точное равенство
            if ("EQ".equals(trigger.operator())) {
                if (value != trigger.value()) {
                    createSkippedRun(scenario, eventId, "CONDITION_FALSE", signalKey, value, now, owner);
                    continue;
                }
            }

            // Выполняем действие
            var run = executeAction(scenario, eventId, signalKey, value, now, owner);
            runIds.add(run.getId());
        }

        return runIds;
    }

    // Загрузка всех сценариев с пагинацией
    private List<Catalog.Scenario> loadAllScenarios(String owner) {
        var all = new ArrayList<Catalog.Scenario>();
        int page = 0;
        int size = 100;
        for (;;) {
            var resp = catalogService.scenarios(page, size, owner);
            all.addAll(resp.items());
            if (all.size() >= resp.total() || resp.items().isEmpty()) break;
            page++;

        }
        return all;
    }

    // Выполнение действия
    private RunEntity executeAction(Catalog.Scenario scenario, UUID eventId, String inputSignalKey, double inputValue, String now, String owner) {
        var run = new RunEntity();
        run.setId(UUID.randomUUID());
        run.setEventId(eventId);
        run.setScenarioId(scenario.id());
        run.setScenarioVersion(scenario.version());
        run.setExecutionMode("LOCAL");
        run.setCreatedAt(now);
        run.setInputSignalKey(inputSignalKey);
        run.setInputValue(inputValue);
        run.setInputUpdatedAt(now);

        run.addEntry("EVENT", "Событие " + inputSignalKey + "=" + inputValue, now);
        run.addEntry("CONDITION", "Условие " + scenario.trigger().signalKey() + " EQ " + scenario.trigger().value() + " — истинно", now);

        var action = scenario.action();
        run.setActionDeviceId(action.deviceId());
        run.setActionSignalKey(action.signalKey());
        run.setActionValue(action.value());

        try {
            requireOnline(action.deviceId());
            var targetDevice = catalogService.device(action.deviceId(), owner);
            var targetSignal = targetDevice.signals().stream()
                    .filter(s -> s.key().equals(action.signalKey()))
                    .findFirst()
                    .orElseThrow(() -> new CatalogException(422, "VALIDATION_ERROR", "Unknown signal"));

            if ("r".equals(targetSignal.management())) {
                run.addEntry("ACTION", "Сигнал " + action.signalKey() + " доступен только для чтения", now);
                run.setStatus("FAILED");
                run.setReason("SIGNAL_READ_ONLY");
            } else if (pythonTarget(action.deviceId(), owner)) {
                queuePython(action.deviceId(), run.getId(), action.signalKey(), action.value(), owner, now);
                run.addEntry("ACTION", "Команда ожидает отправки внешнему Python-эмулятору", now);
                run.setStatus("PENDING");run.setReason("PYTHON_PENDING");
            } else {
                run.addEntry("ACTION", "Команда " + action.signalKey() + "=" + action.value(), now);
                updateSignalState(action.deviceId(), action.signalKey(), action.value(), owner);
                run.addEntry("RESULT", "Состояние " + action.signalKey() + "=" + action.value() + " подтверждено", now);
                run.setStatus("SUCCEEDED");
            }
        } catch (CatalogException e) {
            run.addEntry("ACTION", "Ошибка: " + e.getMessage(), now);
            run.setStatus("BLOCKED");
            run.setReason(e.code);
        }

        run.setOwner(owner);
        runRepo.save(run);
        return run;
    }

    // Пропущенный запуск
    private void createSkippedRun(Catalog.Scenario scenario, UUID eventId, String reason, String signalKey, double value, String now, String owner) {
        var run = new RunEntity();
        run.setId(UUID.randomUUID());
        run.setEventId(eventId);
        run.setScenarioId(scenario.id());
        run.setScenarioVersion(scenario.version());
        run.setExecutionMode("LOCAL");
        run.setCreatedAt(now);
        run.setInputSignalKey(signalKey);
        run.setInputValue(value);
        run.setInputUpdatedAt(now);
        run.setStatus("SKIPPED");
        run.setReason(reason);
        run.addEntry("EVENT", "Событие " + signalKey + "=" + value, now);
        run.addEntry("CONDITION", "Причина: " + reason, now);
        run.setOwner(owner);
        runRepo.save(run);
    }

    // Обновление состояния
    private void updateSignalState(UUID deviceId, String signalKey, double value, String owner) {
        var state = stateRepo.findByDeviceIdAndSignalKeyAndOwner(deviceId, signalKey, owner)
                .orElseGet(() -> {
                    var s = new LocalSignalStateEntity();
                    s.setDeviceId(deviceId);
                    s.setSignalKey(signalKey);
                    return s;
                });
        state.setValue(value);
        state.setUpdatedAt(java.time.Instant.now().toString());
        state.setOwner(owner);
        stateRepo.save(state);
    }

    /**
     * Общая операция применения команды к Java-эмулятору состояния.
     * Локальный контур и применение задач Ujin используют один путь записи.
     */
    public void applyEmulatorValue(UUID deviceId, String signalKey, double value, String owner) {
        updateSignalState(deviceId, signalKey, value, owner);
    }

    // ===== GET /events/{eventId} =====
    @Transactional(readOnly = true)
    public Map<String, Object> getEvent(UUID eventId, String owner) {
        var event = eventRepo.findByEventIdAndOwner(eventId, owner)
                .orElseThrow(() -> new CatalogException(404, "EVENT_NOT_FOUND", "Event not found"));
        return buildEventReceipt(event);
    }

    // ===== GET /commands/{commandId} =====
    @Transactional(readOnly = true)
    public Map<String, Object> getCommand(UUID commandId, String owner) {
        var command = commandRepo.findByCommandIdAndOwner(commandId, owner)
                .orElseThrow(() -> new CatalogException(404, "COMMAND_NOT_FOUND", "Command not found"));
        return buildCommandReceipt(command);
    }

    // ===== GET /runs =====
    @Transactional(readOnly = true)
    public Map<String, Object> getRuns(int page, int size, UUID eventId, UUID scenarioId, String owner) {
        if (page < 0) throw new CatalogException(400, "INVALID_REQUEST", "page must be >= 0");
        if ((long) page * size > Integer.MAX_VALUE) throw new CatalogException(400, "INVALID_REQUEST", "Page offset exceeds maximum");
        if (size < 1 || size > 100) throw new CatalogException(400, "INVALID_REQUEST", "size must be 1-100");

        var pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"), org.springframework.data.domain.Sort.Order.desc("id")));
        org.springframework.data.domain.Page<RunEntity> runPage;

        if (eventId != null && scenarioId != null) {
            runPage = runRepo.findByOwnerAndEventIdAndScenarioId(owner, eventId, scenarioId, pageable);
        } else if (eventId != null) {
            runPage = runRepo.findByOwnerAndEventId(owner, eventId, pageable);
        } else if (scenarioId != null) {
            runPage = runRepo.findByOwnerAndScenarioId(owner, scenarioId, pageable);
        } else {
            runPage = runRepo.findByOwner(owner, pageable);
        }

        var items = runPage.getContent().stream().map(this::buildRunResponse).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        result.put("total", runPage.getTotalElements());
        return result;
    }

    // ===== GET /runs/{runId} =====
    @Transactional(readOnly = true)
    public Map<String, Object> getRun(UUID runId, String owner) {
        var run = runRepo.findByIdAndOwner(runId, owner)
                .orElseThrow(() -> new CatalogException(404, "RUN_NOT_FOUND", "Run not found"));
        return buildRunResponse(run);
    }

    // ===== Построение DTO =====
    private Map<String, Object> buildEventReceipt(EventEntity event) {
        var runIds = new ArrayList<String>();
        int page = 0;
        org.springframework.data.domain.Page<RunEntity> runs;
        do {
            runs = runRepo.findByOwnerAndEventId(event.getOwner(), event.getEventId(), org.springframework.data.domain.PageRequest.of(page++, 100, org.springframework.data.domain.Sort.by("id")));
            runs.forEach(run -> runIds.add(run.getId().toString()));
        } while (runs.hasNext());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", event.getEventId().toString());
        result.put("status", event.getStatus());
        result.put("runIds", runIds);
        return result;
    }

    private Map<String, Object> buildCommandReceipt(CommandEntity command) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commandId", command.getCommandId().toString());
        result.put("deviceId", command.getDeviceId().toString());
        result.put("status", command.getStatus());
        result.put("executionMode", command.getExecutionMode());
        if (command.getReason() != null) result.put("reason", command.getReason());
        if (command.getResolutionNote() != null) {
            Map<String, Object> resolution = new LinkedHashMap<>();
            resolution.put("note", command.getResolutionNote());
            resolution.put("resolvedAt", command.getResolvedAt());
            resolution.put("kind", "ACKNOWLEDGED_UNKNOWN");
            result.put("resolution", resolution);
        }
        return result;
    }

    private Map<String, Object> buildRunResponse(RunEntity run) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", run.getId().toString());
        result.put("eventId", run.getEventId().toString());
        result.put("scenarioId", run.getScenarioId().toString());
        result.put("scenarioVersion", run.getScenarioVersion());
        result.put("executionMode", run.getExecutionMode());
        result.put("status", run.getStatus());
        if (run.getReason() != null) result.put("reason", run.getReason());
        result.put("createdAt", run.getCreatedAt().toString());

        if (run.getInputSignalKey() != null) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("signalKey", run.getInputSignalKey());
            input.put("value", run.getInputValue());
            input.put("updatedAt", run.getInputUpdatedAt().toString());
            result.put("input", input);
        }

        if (run.getActionSignalKey() != null) {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("deviceId", run.getActionDeviceId().toString());
            action.put("signalKey", run.getActionSignalKey());
            action.put("value", run.getActionValue());
            result.put("action", action);
        }

        if (run.getCommandId() != null) result.put("commandId", run.getCommandId().toString());

        var entries = run.getEntries().stream().map(e -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("at", e.getAt().toString());
            entry.put("stage", e.getStage());
            entry.put("message", e.getMessage());
            return entry;
        }).toList();
        result.put("entries", entries);

        return result;
    }
}
