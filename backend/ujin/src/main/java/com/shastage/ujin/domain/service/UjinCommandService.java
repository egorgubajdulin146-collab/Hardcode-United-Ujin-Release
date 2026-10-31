package com.shastage.ujin.domain.service;

import com.shastage.ujin.datasource.client.PlatformTask;
import com.shastage.ujin.datasource.client.UjinApiClient;
import com.shastage.ujin.datasource.client.UjinApiException;
import com.shastage.ujin.datasource.entity.CommandEntity;
import com.shastage.ujin.datasource.entity.TaskAckEntity;
import com.shastage.ujin.datasource.repository.jpa.CommandJpaRepository;
import com.shastage.ujin.datasource.repository.jpa.TaskAckJpaRepository;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.model.Catalog.Device;
import com.shastage.ujin.domain.model.Catalog.Signal;
import com.shastage.ujin.domain.model.PlatformBinding;
import com.shastage.ujin.domain.repository.CatalogStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * Доставка команд UJIN: apartment/send-signal → очередь платформы → задача
 * task/task_dt → применение к локальному Java-эмулятору → публикация факта.
 * Приём запроса платформой не равен исполнению: CONFIRMED выставляется только
 * после применения задачи.
 */
@Service
public class UjinCommandService {
    public enum ApplyOutcome {
        APPLIED,
        SKIPPED_DUPLICATE,
        SKIPPED_NOT_PENDING,
        SKIPPED_UNMAPPED,
        FAILED
    }

    public record ApplyReport(int applied, int skipped, int failed, String sourceStatus) {}

    private record Prepared(CommandEntity entity, boolean created, String serialNumber, String platformName) {}

    private final CommandJpaRepository commands;
    private final TaskAckJpaRepository acks;
    private final CatalogService catalog;
    private final CatalogStore store;
    private final PlatformBindingService binding;
    private final LocalRuntimeService runtime;
    private final PublicationService publication;
    private final CommandProperties config;
    private final TransactionTemplate tx;
    private final UjinApiClient client;

    public UjinCommandService(
            CommandJpaRepository commands,
            TaskAckJpaRepository acks,
            CatalogService catalog,
            CatalogStore store,
            PlatformBindingService binding,
            LocalRuntimeService runtime,
            PublicationService publication,
            CommandProperties config,
            PlatformTransactionManager transactions,
            @Value("${ujin.api.base-url}") URI baseUrl) {
        this.commands = commands;
        this.acks = acks;
        this.catalog = catalog;
        this.store = store;
        this.binding = binding;
        this.runtime = runtime;
        this.publication = publication;
        this.config = config;
        this.tx = new TransactionTemplate(transactions);
        this.client = new UjinApiClient(baseUrl);
    }

    // ===== POST /devices/{id}/commands (executionMode=UJIN) =====
    public Map<String, Object> send(
            UUID deviceId, UUID commandId, String signalKey, Double value, String owner, String ujinToken) {
        if (commandId == null) throw error(422, "VALIDATION_ERROR", "commandId is required");
        if (signalKey == null || signalKey.isBlank())
            throw error(422, "VALIDATION_ERROR", "signalKey is required");
        if (value == null || !Double.isFinite(value))
            throw error(422, "VALIDATION_ERROR", "value must be a finite number");
        if (!config.isEnabled())
            throw error(503, "COMMAND_DELIVERY_DISABLED", "Доставка команд Ujin выключена на сервере");
        requireToken(ujinToken);
        // Reservation commits before any network call; the catalogue lock is never
        // held while waiting for the platform.
        Prepared prepared = tx.execute(status -> prepare(deviceId, commandId, signalKey, value, owner));
        if (!prepared.created()) return receipt(prepared.entity());
        try {
            client.sendSignal(ujinToken, prepared.serialNumber(), prepared.platformName(), value);
        } catch (UjinApiException failure) {
            boolean refused =
                    switch (failure.kind()) {
                        case UNAUTHORIZED, FORBIDDEN, ACCESS_DENIED, PLATFORM_ERROR, SIGNAL_NOT_FOUND ->
                                true;
                        default -> false;
                    };
            return finish(
                    commandId,
                    owner,
                    refused ? "FAILED" : "UNKNOWN",
                    "UJIN_" + failure.kind().name());
        }
        // error=0 means the request was accepted, not that the device executed it.
        return finish(commandId, owner, "PENDING", "ACCEPTED_AWAITING_APPLY");
    }

    private Prepared prepare(
            UUID deviceId, UUID commandId, String signalKey, Double value, String owner) {
        store.lock();
        if (commands.existsByCommandId(commandId)) {
            var existing = commands.findByCommandIdAndOwner(commandId, owner);
            if (existing.isEmpty()) throw error(404, "COMMAND_NOT_FOUND", "Command not found");
            var old = existing.get();
            if (!old.getDeviceId().equals(deviceId)
                    || !old.getSignalKey().equals(signalKey)
                    || old.getValue() != value
                    || !"UJIN".equals(old.getExecutionMode()))
                throw error(409, "IDEMPOTENCY_CONFLICT", "commandId already used with different parameters");
            return new Prepared(old, false, null, null);
        }
        Device device = catalog.device(deviceId, owner);
        Signal signal =
                device.signals().stream()
                        .filter(s -> s.key().equals(signalKey))
                        .findFirst()
                        .orElseThrow(() -> error(422, "VALIDATION_ERROR", "Unknown signal: " + signalKey));
        if ("r".equals(signal.management()))
            throw error(422, "SIGNAL_READ_ONLY", "Signal is read-only: " + signalKey);
        inDomain(signal, value);
        var bound = binding.getBinding(deviceId, owner);
        String platformName =
                bound.signalMappings().stream()
                        .filter(m -> m.signalKey().equals(signalKey))
                        .map(PlatformBinding.Mapping::platformName)
                        .findFirst()
                        .orElseThrow(() -> error(422, "SIGNAL_NOT_BOUND", "Signal has no platform mapping"));
        boolean unresolved =
                commands.findByDeviceIdAndOwnerAndSignalKeyAndStatusIn(
                                deviceId, owner, signalKey, List.of("PENDING", "UNKNOWN")).stream()
                        .anyMatch(c -> c.getResolutionNote() == null);
        if (unresolved)
            throw error(409, "COMMAND_UNRESOLVED", "Проверьте предыдущую команду перед новой");
        String now = Instant.now().toString();
        var command = new CommandEntity();
        command.setCommandId(commandId);
        command.setDeviceId(deviceId);
        command.setSignalKey(signalKey);
        command.setValue(value);
        command.setExecutionMode("UJIN");
        command.setStatus("PENDING");
        command.setReason("DISPATCHING");
        command.setCreatedAt(now);
        command.setUpdatedAt(now);
        command.setOwner(owner);
        return new Prepared(commands.saveAndFlush(command), true, bound.serialNumber(), platformName);
    }

    // ===== POST /commands/{commandId}/check: опрос задач и оценка результата =====
    public Map<String, Object> check(UUID commandId, String owner, String ujinToken) {
        requireToken(ujinToken);
        CommandEntity command = tx.execute(s -> owned(commandId, owner));
        if (isTerminal(command) || command.getResolutionNote() != null) return receipt(command);
        ApplyReport report = pollAndApply(command.getDeviceId(), owner, ujinToken);
        CommandEntity current = tx.execute(s -> owned(commandId, owner));
        if (isTerminal(current) || current.getResolutionNote() != null) return receipt(current);
        Instant created = Instant.parse(current.getCreatedAt());
        if (created.plusSeconds(config.getObservationWindowSeconds()).isBefore(Instant.now()))
            return finish(
                    commandId,
                    owner,
                    "UNKNOWN",
                    "OK".equals(report.sourceStatus())
                            ? "OBSERVATION_WINDOW_EXPIRED"
                            : "TASK_SOURCE_BLOCKED");
        return receipt(current);
    }

    // ===== Опрос и применение задач платформы =====
    public ApplyReport pollAndApply(UUID deviceId, String owner, String ujinToken) {
        if (!config.isEnabled() || !config.isTaskFetchEnabled())
            return new ApplyReport(0, 0, 0, "TASK_SOURCE_BLOCKED");
        requireToken(ujinToken);
        Device device = catalog.device(deviceId, owner);
        var bound = binding.getBinding(deviceId, owner);
        List<PlatformTask> tasks;
        try {
            // The documented task source is historically denied to the resident
            // role. A denial is reported as-is; roles and credentials are never
            // rotated to work around it.
            tasks = client.fetchDeviceInfoTasks(ujinToken, bound.serialNumber());
        } catch (UjinApiException failure) {
            return new ApplyReport(0, 0, 0, "UJIN_" + failure.kind().name());
        }
        int applied = 0;
        int skipped = 0;
        int failed = 0;
        for (PlatformTask task : tasks) {
            ApplyOutcome outcome = applyTask(device, bound, task, owner, ujinToken);
            if (outcome == ApplyOutcome.APPLIED) applied++;
            else if (outcome == ApplyOutcome.FAILED) failed++;
            else skipped++;
        }
        return new ApplyReport(applied, skipped, failed, "OK");
    }

    /** Применение одной задачи. Видно тестам: дубли, отказы применения, конкуренция. */
    public ApplyOutcome applyTask(UUID deviceId, String owner, PlatformTask task, String ujinToken) {
        Device device = catalog.device(deviceId, owner);
        var bound = binding.getBinding(deviceId, owner);
        return applyTask(device, bound, task, owner, ujinToken);
    }

    private ApplyOutcome applyTask(
            Device device, PlatformBinding.View bound, PlatformTask task, String owner, String ujinToken) {
        if (task == null || task.taskValue() == null || task.taskValue().isBlank())
            return ApplyOutcome.SKIPPED_NOT_PENDING;
        if (task.currentValue() != null && task.taskValue().equals(task.currentValue()))
            return ApplyOutcome.SKIPPED_NOT_PENDING;
        String signalKey = resolveSignalKey(bound, task.signalName());
        if (signalKey == null) return ApplyOutcome.SKIPPED_UNMAPPED;
        String key = task.dedupKey();
        Double value = strictNumber(task.taskValue());
        if (value == null) return ApplyOutcome.FAILED;
        ApplyOutcome outcome =
                tx.execute(
                        status -> {
                            store.lock();
                            var ack = acks.findByIdDeviceIdAndIdSignalKey(device.id(), signalKey);
                            if (ack.isPresent() && key.equals(ack.get().getTaskKey()))
                                return ApplyOutcome.SKIPPED_DUPLICATE;
                            Signal signal =
                                    device.signals().stream()
                                            .filter(s -> s.key().equals(signalKey))
                                            .findFirst()
                                            .orElse(null);
                            if (signal == null || "r".equals(signal.management()))
                                return ApplyOutcome.FAILED;
                            try {
                                inDomain(signal, value);
                            } catch (CatalogException refused) {
                                return ApplyOutcome.FAILED;
                            }
                            // Применение к Java-эмулятору и запись дедупа только после успеха.
                            runtime.applyEmulatorValue(device.id(), signalKey, value, owner);
                            var row = ack.orElseGet(() -> new TaskAckEntity(device.id(), signalKey));
                            row.setTaskKey(key);
                            row.setAppliedAt(Instant.now().toString());
                            acks.saveAndFlush(row);
                            confirmPending(device.id(), signalKey, value, owner);
                            return ApplyOutcome.APPLIED;
                        });
        if (outcome == ApplyOutcome.APPLIED) publishFact(device.id(), signalKey, value, owner, ujinToken);
        return outcome;
    }

    private void confirmPending(UUID deviceId, String signalKey, double value, String owner) {
        for (CommandEntity command :
                commands.findByDeviceIdAndOwnerAndSignalKeyAndStatusIn(
                        deviceId, owner, signalKey, List.of("PENDING", "UNKNOWN"))) {
            if (command.getResolutionNote() != null) continue;
            if (Double.compare(command.getValue(), value) != 0) continue;
            command.setStatus("CONFIRMED");
            command.setReason("TASK_APPLIED");
            command.setConfirmedAt(Instant.now().toString());
            command.setUpdatedAt(Instant.now().toString());
            commands.saveAndFlush(command);
        }
    }

    private void publishFact(
            UUID deviceId, String signalKey, double value, String owner, String ujinToken) {
        try {
            publication.publish(
                    deviceId,
                    new PublicationService.Write(UUID.randomUUID(), signalKey, value),
                    owner,
                    ujinToken);
        } catch (RuntimeException ignored) {
            // Публикация факта — best-effort поверх уже применённого состояния;
            // её собственные квитанции ведут свой журнал.
        }
    }

    // ===== POST /commands/{commandId}/resolution: отметка ACKNOWLEDGED_UNKNOWN =====
    public Map<String, Object> resolveUnknown(UUID commandId, String note, String owner) {
        if (note == null || note.isBlank() || note.length() > 1000)
            throw error(422, "VALIDATION_ERROR", "note is required (1..1000 characters)");
        CommandEntity command = tx.execute(s -> owned(commandId, owner));
        if (command.getResolutionNote() != null) {
            if (command.getResolutionNote().equals(note)) return receipt(command);
            throw error(409, "RESOLUTION_CONFLICT", "Command already resolved with a different note");
        }
        if (!"UNKNOWN".equals(command.getStatus()))
            throw error(409, "RESOLUTION_CONFLICT", "Only UNKNOWN commands can be acknowledged");
        return finish(commandId, owner, "UNKNOWN", "ACKNOWLEDGED_UNKNOWN", note);
    }

    // ===== Вспомогательные =====

    private String resolveSignalKey(PlatformBinding.View bound, String signalName) {
        if (signalName == null || signalName.isBlank()) return null;
        String prefix = config.getTaskSignalPrefix();
        for (PlatformBinding.Mapping mapping : bound.signalMappings()) {
            if (signalName.equals(mapping.platformName())) return mapping.signalKey();
            if (!prefix.isEmpty() && signalName.equals(prefix + mapping.platformName()))
                return mapping.signalKey();
        }
        return null;
    }

    private CommandEntity owned(UUID commandId, String owner) {
        return commands
                .findByCommandIdAndOwner(commandId, owner)
                .orElseThrow(() -> error(404, "COMMAND_NOT_FOUND", "Command not found"));
    }

    private static boolean isTerminal(CommandEntity command) {
        return "CONFIRMED".equals(command.getStatus()) || "FAILED".equals(command.getStatus());
    }

    private Map<String, Object> finish(UUID commandId, String owner, String status, String reason) {
        return finish(commandId, owner, status, reason, null);
    }

    private Map<String, Object> finish(
            UUID commandId, String owner, String status, String reason, String resolutionNote) {
        return tx.execute(
                transaction -> {
                    store.lock();
                    CommandEntity command = owned(commandId, owner);
                    if (!isTerminal(command)) {
                        command.setStatus(status);
                        command.setReason(reason);
                        command.setUpdatedAt(Instant.now().toString());
                        if ("CONFIRMED".equals(status)) command.setConfirmedAt(Instant.now().toString());
                        if (resolutionNote != null && command.getResolutionNote() == null) {
                            command.setResolutionNote(resolutionNote);
                            command.setResolvedAt(Instant.now().toString());
                        }
                        commands.saveAndFlush(command);
                    }
                    return receipt(command);
                });
    }

    static Map<String, Object> receipt(CommandEntity command) {
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

    private static void inDomain(Signal signal, double value) {
        if ((signal.min() != null && value < signal.min())
                || (signal.max() != null && value > signal.max())
                || (signal.enumValues() != null
                        && !signal.enumValues().isEmpty()
                        && signal.enumValues().stream().noneMatch(v -> Double.compare(v, value) == 0)))
            throw error(422, "VALIDATION_ERROR", "Value is outside the signal declaration");
    }

    private static Double strictNumber(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (!text.matches("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$")) return null;
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static void requireToken(String token) {
        if (token == null || token.isBlank())
            throw error(401, "UJIN_AUTH_REQUIRED", "Active Ujin session required");
    }

    private static CatalogException error(int status, String code, String detail) {
        return new CatalogException(status, code, detail);
    }
}
