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
    private final CatalogService catalogService;

    public LocalRuntimeService(
            LocalSignalStateJpaRepository stateRepo,
            EventJpaRepository eventRepo,
            CommandJpaRepository commandRepo,
            RunJpaRepository runRepo,
            CatalogService catalogService
    ) {
        this.stateRepo = stateRepo;
        this.eventRepo = eventRepo;
        this.commandRepo = commandRepo;
        this.runRepo = runRepo;
        this.catalogService = catalogService;
    }

    // Получение состояния устройства
    @Transactional(readOnly = true)
    public Map<String, Object> getDeviceState(UUID deviceId, String mode) {
        var device = catalogService.device(deviceId);
        var states = stateRepo.findByDeviceId(deviceId);

        var values = states.stream().map(s -> Map.of(
                "signalKey", s.getSignalKey(),
                "value", s.getValue(),
                "updatedAt", s.getUpdatedAt().toString()
        )).toList();

        return Map.of(
                "deviceId", deviceId.toString(),
                "executionMode", mode,
                "values", values,
                "availability", "ONLINE"
        );
    }

    // Обработка стимула (WET)
    public Map<String, Object> processStimulus(UUID deviceId, UUID eventId, String kind, int value) {
        var device = catalogService.device(deviceId);

        // Идемпотентность: проверяем существующий event
        var existing = eventRepo.findById(eventId);
        if (existing.isPresent()) {
            return buildEventReceipt(existing.get());
        }

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

        // Создаём событие
        var event = new EventEntity();
        event.setEventId(eventId);
        event.setDeviceId(deviceId);
        event.setKind(kind);
        event.setValue(value);
        event.setStatus("ACCEPTED");
        event.setCreatedAt(Instant.now());
        eventRepo.save(event);

        // Обновляем состояние сигнала leak
        updateSignalState(deviceId, "leak", value);

        // Запускаем подходящие LOCAL-сценарии
        var runIds = processScenarios(deviceId, "leak", value, eventId);

        event.setStatus("PROCESSED");
        event.setProcessedAt(Instant.now());
        eventRepo.save(event);

        return Map.of(
                "eventId", eventId.toString(),
                "status", "PROCESSED",
                "runIds", runIds
        );
    }

    // Обработка команды
    public Map<String, Object> processCommand(UUID deviceId, UUID commandId, String signalKey, double value, String mode) {
        var device = catalogService.device(deviceId);

        // Идемпотентность
        var existing = commandRepo.findById(commandId);
        if (existing.isPresent()) {
            return buildCommandReceipt(existing.get());
        }

        // Валидация: сигнал должен существовать и быть управляемым
        var signal = device.signals().stream()
                .filter(s -> s.key().equals(signalKey))
                .findFirst()
                .orElseThrow(() -> new CatalogException(422, "VALIDATION_ERROR", "Unknown signal: " + signalKey));

        if ("r".equals(signal.management())) {
            throw new CatalogException(422, "SIGNAL_READ_ONLY", "Signal is read-only: " + signalKey);
        }

        // Проверка диапазона
        if (signal.min() != null && value < signal.min()) {
            throw new CatalogException(422, "VALIDATION_ERROR", "Value below minimum");
        }
        if (signal.max() != null && value > signal.max()) {
            throw new CatalogException(422, "VALIDATION_ERROR", "Value above maximum");
        }
        if (signal.enumValues() != null && !signal.enumValues().isEmpty() && !signal.enumValues().contains(value)) {
            throw new CatalogException(422, "VALIDATION_ERROR", "Value not in enumValues");
        }

        // Создаём команду
        var command = new CommandEntity();
        command.setCommandId(commandId);
        command.setDeviceId(deviceId);
        command.setSignalKey(signalKey);
        command.setValue(value);
        command.setExecutionMode(mode);
        command.setStatus("CONFIRMED");
        command.setCreatedAt(Instant.now());
        command.setConfirmedAt(Instant.now());
        commandRepo.save(command);

        // Обновляем состояние
        updateSignalState(deviceId, signalKey, value);

        return buildCommandReceipt(command);
    }

    // Запуск сценариев для события
    private List<UUID> processScenarios(UUID sourceDeviceId, String signalKey, double value, UUID eventId) {
        var scenarios = catalogService.scenarios(0, 1000).items();
        var runIds = new ArrayList<UUID>();
        var now = Instant.now();

        for (var scenario : scenarios) {
            // Только LOCAL-сценарии
            if (!"LOCAL".equals(scenario.executionMode())) continue;

            // Только включённые
            if (!scenario.enabled()) {
                createSkippedRun(scenario, eventId, "SCENARIO_DISABLED", now);
                continue;
            }

            // Проверяем триггер
            var trigger = scenario.trigger();
            if (!sourceDeviceId.equals(trigger.deviceId()) || !signalKey.equals(trigger.signalKey())) {
                continue;
            }

            // Проверяем условие
            if ("EQ".equals(trigger.operator())) {
                if (Math.abs(value - trigger.value()) > 0.001) {
                    createSkippedRun(scenario, eventId, "CONDITION_FALSE", now);
                    continue;
                }
            }

            // Выполняем действие
            var run = executeAction(scenario, eventId, signalKey, value, now);
            runIds.add(run.getId());
        }

        return runIds;
    }

    // Выполнение действия сценария
    private RunEntity executeAction(Catalog.Scenario scenario, UUID eventId, String inputSignalKey, double inputValue, Instant now) {
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

        // Проверка условия
        run.addEntry("CONDITION", "Условие " + scenario.trigger().signalKey() + " EQ " + scenario.trigger().value() + " — истинно", now);

        // Действие
        var action = scenario.action();
        run.setActionDeviceId(action.deviceId());
        run.setActionSignalKey(action.signalKey());
        run.setActionValue(action.value());

        try {
            // Проверяем доступность устройства назначения
            var targetDevice = catalogService.device(action.deviceId());
            var targetSignal = targetDevice.signals().stream()
                    .filter(s -> s.key().equals(action.signalKey()))
                    .findFirst()
                    .orElseThrow(() -> new CatalogException(422, "VALIDATION_ERROR", "Unknown signal"));

            if ("r".equals(targetSignal.management())) {
                run.addEntry("ACTION", "Сигнал " + action.signalKey() + " доступен только для чтения", now);
                run.setStatus("FAILED");
                run.setReason("SIGNAL_READ_ONLY");
            } else {
                run.addEntry("ACTION", "Команда " + action.signalKey() + "=" + action.value(), now);
                updateSignalState(action.deviceId(), action.signalKey(), action.value());
                run.addEntry("RESULT", "Состояние " + action.signalKey() + "=" + action.value() + " подтверждено", now);
                run.setStatus("SUCCEEDED");
            }
        } catch (CatalogException e) {
            run.addEntry("ACTION", "Ошибка: " + e.getMessage(), now);
            run.setStatus("BLOCKED");
            run.setReason(e.getCode());
        }

        runRepo.save(run);
        return run;
    }

    // Пропущенный запуск
    private void createSkippedRun(Catalog.Scenario scenario, UUID eventId, String reason, Instant now) {
        var run = new RunEntity();
        run.setId(UUID.randomUUID());
        run.setEventId(eventId);
        run.setScenarioId(scenario.id());
        run.setScenarioVersion(scenario.version());
        run.setExecutionMode("LOCAL");
        run.setCreatedAt(now);
        run.setStatus("SKIPPED");
        run.setReason(reason);
        run.addEntry("EVENT", "Событие получено", now);
        run.addEntry("CONDITION", "Причина: " + reason, now);
        runRepo.save(run);
    }

    // Обновление состояния сигнала
    private void updateSignalState(UUID deviceId, String signalKey, double value) {
        var state = stateRepo.findByDeviceIdAndSignalKey(deviceId, signalKey)
                .orElseGet(() -> {
                    var s = new LocalSignalStateEntity();
                    s.setDeviceId(deviceId);
                    s.setSignalKey(signalKey);
                    return s;
                });
        state.setValue(value);
        state.setUpdatedAt(Instant.now());
        stateRepo.save(state);
    }

    // Получение события
    @Transactional(readOnly = true)
    public Map<String, Object> getEvent(UUID eventId) {
        var event = eventRepo.findById(eventId)
                .orElseThrow(() -> new CatalogException(404, "EVENT_NOT_FOUND", "Event not found"));
        return buildEventReceipt(event);
    }

    // Получение команды
    @Transactional(readOnly = true)
    public Map<String, Object> getCommand(UUID commandId) {
        var command = commandRepo.findById(commandId)
                .orElseThrow(() -> new CatalogException(404, "COMMAND_NOT_FOUND", "Command not found"));
        return buildCommandReceipt(command);
    }

    // Список запусков
    @Transactional(readOnly = true)
    public Map<String, Object> getRuns(int page, int size, UUID eventId, UUID scenarioId) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<RunEntity> runPage;

        if (eventId != null && scenarioId != null) {
            runPage = runRepo.findByEventIdAndScenarioId(eventId, scenarioId, pageable);
        } else if (eventId != null) {
            runPage = runRepo.findByEventId(eventId, pageable);
        } else if (scenarioId != null) {
            runPage = runRepo.findByScenarioId(scenarioId, pageable);
        } else {
            runPage = runRepo.findAll(pageable);
        }

        var items = runPage.getContent().stream().map(this::buildRunResponse).toList();
        return Map.of(
                "items", items,
                "page", page,
                "size", size,
                "total", runPage.getTotalElements()
        );
    }

    // Карточка запуска
    @Transactional(readOnly = true)
    public Map<String, Object> getRun(UUID runId) {
        var run = runRepo.findById(runId)
                .orElseThrow(() -> new CatalogException(404, "RUN_NOT_FOUND", "Run not found"));
        return buildRunResponse(run);
    }

    // Построение DTO
    private Map<String, Object> buildEventReceipt(EventEntity event) {
        var runs = runRepo.findByEventId(event.getEventId(), org.springframework.data.domain.PageRequest.of(0, 100));
        var runIds = runs.getContent().stream().map(RunEntity::getId).map(UUID::toString).toList();
        return Map.of(
                "eventId", event.getEventId().toString(),
                "status", event.getStatus(),
                "runIds", runIds
        );
    }

    private Map<String, Object> buildCommandReceipt(CommandEntity command) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commandId", command.getCommandId().toString());
        result.put("deviceId", command.getDeviceId().toString());
        result.put("status", command.getStatus());
        result.put("executionMode", command.getExecutionMode());
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
            result.put("input", Map.of(
                    "signalKey", run.getInputSignalKey(),
                    "value", run.getInputValue(),
                    "updatedAt", run.getInputUpdatedAt().toString()
            ));
        }

        if (run.getActionSignalKey() != null) {
            result.put("action", Map.of(
                    "deviceId", run.getActionDeviceId().toString(),
                    "signalKey", run.getActionSignalKey(),
                    "value", run.getActionValue()
            ));
        }

        var entries = run.getEntries().stream().map(e -> Map.of(
                "at", e.getAt().toString(),
                "stage", e.getStage(),
                "message", e.getMessage()
        )).toList();
        result.put("entries", entries);

        return result;
    }
}
