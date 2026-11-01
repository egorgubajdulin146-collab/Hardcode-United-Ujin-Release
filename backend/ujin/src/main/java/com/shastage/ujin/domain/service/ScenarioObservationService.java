package com.shastage.ujin.domain.service;

import com.shastage.ujin.datasource.entity.EventEntity;
import com.shastage.ujin.datasource.entity.RunEntity;
import com.shastage.ujin.datasource.entity.ScenarioObservationEntity;
import com.shastage.ujin.datasource.entity.ScenarioPollEntity;
import com.shastage.ujin.datasource.repository.jpa.EventJpaRepository;
import com.shastage.ujin.datasource.repository.jpa.RunJpaRepository;
import com.shastage.ujin.datasource.repository.jpa.ScenarioObservationJpaRepository;
import com.shastage.ujin.datasource.repository.jpa.ScenarioPollJpaRepository;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.model.Catalog;
import com.shastage.ujin.domain.model.PlatformBinding;
import com.shastage.ujin.domain.repository.CatalogStore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Наблюдение показаний Ujin для сценария: одно чтение привязанного входа за
 * запрос. Первое корректное чтение устанавливает baseline, изменение создаёт
 * событие и запускает правило, одинаковое значение действие не повторяет.
 * Исполнение — наш backend; autoscripts платформы не используются.
 */
@Service
public class ScenarioObservationService {

    private record Snapshot(
            UUID scenarioId,
            long version,
            boolean enabled,
            Catalog.Trigger trigger,
            Catalog.Action action,
            String serialNumber,
            String platformName,
            long bindingVersion) {}

    private record ReadOutcome(boolean ok, Double value, String reason, String measuredAt) {}

    private record DispatchPlan(
            UUID pollId,
            UUID runId,
            UUID commandId,
            UUID deviceId,
            String signalKey,
            double value) {}

    private final ScenarioPollJpaRepository polls;
    private final ScenarioObservationJpaRepository observations;
    private final EventJpaRepository events;
    private final RunJpaRepository runs;
    private final CatalogService catalog;
    private final CatalogStore store;
    private final PlatformBindingService binding;
    private final UjinCommandService commands;
    private final ScenarioObservationProperties config;
    private final TransactionTemplate tx;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.shastage.ujin.datasource.repository.jpa.CommandJpaRepository commandRows;

    public ScenarioObservationService(
            ScenarioPollJpaRepository polls,
            ScenarioObservationJpaRepository observations,
            EventJpaRepository events,
            RunJpaRepository runs,
            CatalogService catalog,
            CatalogStore store,
            PlatformBindingService binding,
            UjinCommandService commands,
            ScenarioObservationProperties config,
            PlatformTransactionManager transactions,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            com.shastage.ujin.datasource.repository.jpa.CommandJpaRepository commandRows) {
        this.polls = polls;
        this.observations = observations;
        this.events = events;
        this.runs = runs;
        this.catalog = catalog;
        this.store = store;
        this.binding = binding;
        this.commands = commands;
        this.config = config;
        this.tx = new TransactionTemplate(transactions);
        this.jdbc = jdbc;
        this.commandRows = commandRows;
    }

    // ===== POST /api/v1/scenarios/{scenarioId}/poll =====
    public Map<String, Object> poll(UUID scenarioId, UUID pollId, String owner, String ujinToken) {
        if (pollId == null) throw error(422, "VALIDATION_ERROR", "pollId is required");
        if (!config.isObservationEnabled())
            throw error(
                    503,
                    "SCENARIO_OBSERVATION_DISABLED",
                    "Наблюдение показаний Ujin выключено на сервере");
        requireToken(ujinToken);
        // Резерв результата до сети; блокировка каталога не держится на чтении.
        Object[] reserved = new Object[2];
        ScenarioPollEntity replay = tx.execute(status -> reserve(scenarioId, pollId, owner, reserved));
        // Existing commands are checked, never re-sent, including on unchanged input/replay.
        // This happens outside the reservation transaction and global catalogue lock.
        reconcile(scenarioId, owner, ujinToken);
        if (replay != null) return view(owned(pollId, owner));
        Snapshot snapshot = (Snapshot) reserved[0];
        ReadOutcome read = readInput(snapshot, owner, ujinToken);
        DispatchPlan plan = tx.execute(status -> accept(snapshot, read, pollId, owner));
        if (plan == null) return view(owned(pollId, owner));
        return dispatch(plan, owner, ujinToken);
    }

    private ScenarioPollEntity reserve(
            UUID scenarioId, UUID pollId, String owner, Object[] snapshotOut) {
        store.lock();
        expireStaleDispatches(scenarioId, owner, Instant.now().toString());
        var existing = polls.findById(pollId);
        if (existing.isPresent()) {
            var old = existing.get();
            if (!owner.equals(old.getOwner()) || !scenarioId.equals(old.getScenarioId()))
                throw error(409, "IDEMPOTENCY_CONFLICT", "pollId already used with different parameters");
            if ("PROCESSING".equals(old.getStatus())
                    && Instant.parse(old.getCreatedAt())
                            .plusSeconds(60)
                            .isBefore(Instant.now())) {
                old.setStatus("UNKNOWN");
                old.setReason("OBSERVATION_WINDOW_EXPIRED");
                polls.saveAndFlush(old);
            }
            return old;
        }
        Catalog.Scenario scenario = catalog.scenario(scenarioId, owner);
        if (!"UJIN".equals(scenario.executionMode()))
            throw error(422, "VALIDATION_ERROR", "Poll observes Ujin readings of UJIN scenarios");
        var trigger = scenario.trigger();
        PlatformBinding.View bound = binding.getBinding(trigger.deviceId(), owner);
        String platformName =
                bound.signalMappings().stream()
                        .filter(m -> m.signalKey().equals(trigger.signalKey()))
                        .map(PlatformBinding.Mapping::platformName)
                        .findFirst()
                        .orElseThrow(() ->
                                error(422, "SIGNAL_NOT_BOUND", "Trigger signal has no platform mapping"));
        String now = Instant.now().toString();
        var pollRow = new ScenarioPollEntity();
        pollRow.setPollId(pollId);
        pollRow.setScenarioId(scenarioId);
        pollRow.setOwner(owner);
        pollRow.setStatus("PROCESSING");
        pollRow.setCreatedAt(now);
        pollRow.setReadOrder(jdbc.queryForObject("SELECT nextval('scenario_poll_read_order_seq')", Long.class));
        polls.saveAndFlush(pollRow);
        snapshotOut[0] =
                new Snapshot(
                        scenarioId,
                        scenario.version(),
                        scenario.enabled(),
                        trigger,
                        scenario.action(),
                        bound.serialNumber(),
                        platformName,
                        bound.version());
        return null;
    }

    private ReadOutcome readInput(Snapshot snapshot, String owner, String ujinToken) {
        try {
            var state = binding.readUjinState(snapshot.trigger().deviceId(), owner, ujinToken);
            @SuppressWarnings("unchecked")
            var issues = (List<Map<String, Object>>) state.getOrDefault("signalIssues", List.of());
            for (Map<String, Object> issue : issues) {
                if (snapshot.trigger().signalKey().equals(issue.get("signalKey")))
                    return new ReadOutcome(false, null, String.valueOf(issue.get("status")), null);
            }
            @SuppressWarnings("unchecked")
            var values = (List<Map<String, Object>>) state.get("values");
            for (Map<String, Object> value : values) {
                if (snapshot.trigger().signalKey().equals(value.get("signalKey"))) {
                    Object raw = value.get("value");
                    if (raw instanceof Number number && Double.isFinite(number.doubleValue()))
                        return new ReadOutcome(true, number.doubleValue(), null, value.get("measuredAt") instanceof String dt ? dt : null);
                    return new ReadOutcome(false, null, "BAD_SCHEMA", null);
                }
            }
            return new ReadOutcome(false, null, "MISSING_SIGNAL", null);
        } catch (CatalogException failure) {
            return new ReadOutcome(false, null, failure.code, null);
        }
    }

    /** Приём наблюдения после чтения: повторная проверка владельца, версии и привязки. */
    private DispatchPlan accept(Snapshot snapshot, ReadOutcome read, UUID pollId, String owner) {
        store.lock();
        ScenarioPollEntity pollRow = owned(pollId, owner);
        if (!"PROCESSING".equals(pollRow.getStatus())) return null;
        if (!read.ok()) {
            pollRow.setStatus("READ_ERROR");
            pollRow.setReason(read.reason());
            polls.saveAndFlush(pollRow);
            return null;
        }
        pollRow.setObservedValue(read.value());
        Catalog.Scenario scenario;
        try {
            scenario = catalog.scenario(snapshot.scenarioId(), owner);
        } catch (CatalogException missing) {
            return reject(pollRow, "SCENARIO_NOT_FOUND");
        }
        if (scenario.version() != snapshot.version()
                || !scenario.trigger().equals(snapshot.trigger())
                || !scenario.action().equals(snapshot.action()))
            return reject(pollRow, "SCENARIO_CHANGED");
        try {
            var bound = binding.getBinding(snapshot.trigger().deviceId(), owner);
            boolean sameMapping =
                    bound.version() == snapshot.bindingVersion()
                            && bound.serialNumber().equals(snapshot.serialNumber())
                            && bound.signalMappings().stream()
                                    .anyMatch(m ->
                                            m.signalKey().equals(snapshot.trigger().signalKey())
                                                    && m.platformName().equals(snapshot.platformName()));
            if (!sameMapping) return reject(pollRow, "BINDING_CHANGED");
        } catch (CatalogException missing) {
            return reject(pollRow, "BINDING_CHANGED");
        }
        String now = Instant.now().toString();
        var baseline =
                observations.findByScenarioIdAndOwner(snapshot.scenarioId(), owner).orElse(null);
        String identity = sourceIdentity(snapshot);
        long order = Objects.requireNonNullElse(pollRow.getReadOrder(), 0L);
        if (baseline == null || !identity.equals(baseline.getSourceIdentity())) {
            var row = baseline == null ? new ScenarioObservationEntity() : baseline;
            row.setScenarioId(snapshot.scenarioId());
            row.setOwner(owner);
            row.setInputSignalKey(snapshot.trigger().signalKey());
            row.setSourceIdentity(identity);
            row.setBaselineValue(read.value());
            row.setAcceptedReadOrder(order);
            row.setMeasuredAt(read.measuredAt());
            row.setUpdatedAt(now);
            observations.saveAndFlush(row);
            pollRow.setStatus("BASELINE_STORED");
            pollRow.setReason(baseline == null ? "FIRST_READ" : "SOURCE_CHANGED");
            polls.saveAndFlush(pollRow);
            return null;
        }
        if (order <= baseline.getAcceptedReadOrder()) {
            // Equal stale values are harmless, but must not move the watermark backwards.
            pollRow.setStatus(Double.compare(baseline.getBaselineValue(), read.value()) == 0 ? "NO_CHANGE" : "REJECTED");
            pollRow.setReason("STALE_OBSERVATION");
            polls.saveAndFlush(pollRow);
            return null;
        }
        if (read.measuredAt() != null && baseline.getMeasuredAt() != null
                && Instant.parse(read.measuredAt()).isBefore(Instant.parse(baseline.getMeasuredAt())))
            return reject(pollRow, "STALE_MEASUREMENT");
        baseline.setAcceptedReadOrder(order);
        baseline.setUpdatedAt(now);
        if (read.measuredAt() != null) baseline.setMeasuredAt(read.measuredAt());
        if (Double.compare(baseline.getBaselineValue(), read.value()) == 0) {
            observations.saveAndFlush(baseline);
            pollRow.setStatus("NO_CHANGE");
            pollRow.setReason("VALUE_UNCHANGED");
            polls.saveAndFlush(pollRow);
            return null;
        }
        baseline.setBaselineValue(read.value());
        observations.saveAndFlush(baseline);

        var event = new EventEntity();
        event.setEventId(UUID.randomUUID());
        event.setDeviceId(snapshot.trigger().deviceId());
        event.setKind("OBSERVED");
        event.setValue(read.value());
        event.setStatus("PROCESSED");
        event.setCreatedAt(now);
        event.setProcessedAt(now);
        event.setOwner(owner);
        events.saveAndFlush(event);

        var run = new RunEntity();
        run.setId(UUID.randomUUID());
        run.setEventId(event.getEventId());
        run.setScenarioId(snapshot.scenarioId());
        run.setScenarioVersion(scenario.version());
        run.setExecutionMode("UJIN");
        run.setCreatedAt(now);
        run.setInputSignalKey(snapshot.trigger().signalKey());
        run.setInputValue(read.value());
        run.setInputUpdatedAt(now);
        run.addEntry("EVENT", "Наблюдение " + snapshot.trigger().signalKey() + "=" + read.value(), now);
        run.setOwner(owner);

        if (!scenario.enabled()) {
            return skip(run, pollRow, "SCENARIO_DISABLED", "Сценарий выключен", now);
        }
        boolean eventMatches =
                catalog.device(snapshot.trigger().deviceId(), owner).events().stream()
                        .anyMatch(e ->
                                e.key().equals(snapshot.trigger().eventKey())
                                        && e.signalKey().equals(snapshot.trigger().signalKey()));
        if (!eventMatches) {
            return skip(run, pollRow, "EVENT_MISMATCH", "Событие не соответствует триггеру", now);
        }
        if (Double.compare(read.value(), snapshot.trigger().value()) != 0) {
            return skip(run, pollRow, "CONDITION_FALSE", "Условие ложно", now);
        }
        run.addEntry("CONDITION", "Условие EQ " + snapshot.trigger().value() + " — истинно", now);
        run.setActionDeviceId(snapshot.action().deviceId());
        run.setActionSignalKey(snapshot.action().signalKey());
        run.setActionValue(snapshot.action().value());
        UUID commandId = UUID.randomUUID();
        run.setCommandId(commandId);
        run.setStatus("PENDING");
        run.setReason("DISPATCHING");
        run.addEntry("ACTION", "Команда " + snapshot.action().signalKey() + "=" + snapshot.action().value(), now);
        runs.saveAndFlush(run);
        pollRow.setStatus("RUN_CREATED");
        pollRow.setRunId(run.getId());
        pollRow.setReason("DISPATCHING");
        polls.saveAndFlush(pollRow);
        return new DispatchPlan(
                pollId,
                run.getId(),
                commandId,
                snapshot.action().deviceId(),
                snapshot.action().signalKey(),
                snapshot.action().value());
    }

    private DispatchPlan skip(RunEntity run, ScenarioPollEntity pollRow, String reason, String detail, String now) {
        run.addEntry("CONDITION", detail, now);
        run.setStatus("SKIPPED");
        run.setReason(reason);
        runs.saveAndFlush(run);
        pollRow.setStatus("RUN_CREATED");
        pollRow.setRunId(run.getId());
        pollRow.setReason(reason);
        polls.saveAndFlush(pollRow);
        return null;
    }

    private DispatchPlan reject(ScenarioPollEntity pollRow, String reason) {
        pollRow.setStatus("REJECTED");
        pollRow.setReason(reason);
        polls.saveAndFlush(pollRow);
        return null;
    }

    /** Отправка команды вне блокировки каталога; статус Run следует за квитанцией. */
    private Map<String, Object> dispatch(DispatchPlan plan, String owner, String ujinToken) {
        String status;
        String reason;
        try {
            var receipt =
                    commands.send(plan.deviceId(), plan.commandId(), plan.signalKey(), plan.value(), owner, ujinToken);
            String commandStatus = String.valueOf(receipt.get("status"));
            status = "CONFIRMED".equals(commandStatus) ? "SUCCEEDED" : commandStatus;
            reason = receipt.get("reason") == null ? "COMMAND_ACCEPTED" : String.valueOf(receipt.get("reason"));
        } catch (CatalogException failure) {
            status = runStatusFor(failure.code);
            reason = failure.code;
        }
        String finalStatus = status;
        String finalReason = reason;
        return tx.execute(transaction -> {
            store.lock();
            RunEntity run = runs.findById(plan.runId()).orElseThrow();
            if (!"SUCCEEDED".equals(run.getStatus())) {
                run.setStatus(finalStatus);
                run.setReason(finalReason);
                run.addEntry("RESULT", "Команда: " + finalStatus + " (" + finalReason + ")", Instant.now().toString());
                runs.saveAndFlush(run);
            }
            ScenarioPollEntity pollRow = owned(plan.pollId(), owner);
            if ("RUN_CREATED".equals(pollRow.getStatus())) {
                pollRow.setReason(finalReason);
                polls.saveAndFlush(pollRow);
            }
            return view(pollRow);
        });
    }

    private static String runStatusFor(String code) {
        return switch (code) {
            case "IDEMPOTENCY_CONFLICT", "VALIDATION_ERROR", "SIGNAL_READ_ONLY", "COMMAND_NOT_FOUND" ->
                    "FAILED";
            default -> "BLOCKED";
        };
    }

    private void expireStaleDispatches(UUID scenarioId, String owner, String now) {
        for (RunEntity run :
                runs.findByScenarioIdAndOwnerAndStatusAndReason(scenarioId, owner, "PENDING", "DISPATCHING")) {
            if (Instant.parse(run.getCreatedAt()).plusSeconds(60).isBefore(Instant.now())) {
                run.setStatus("UNKNOWN");
                run.setReason("DISPATCH_INTERRUPTED");
                run.addEntry("RESULT", "Отправка не была завершена; повтор не выполняется", now);
                runs.saveAndFlush(run);
                updatePollReason(run, "DISPATCH_INTERRUPTED");
            }
        }
    }

    private void updatePollReason(RunEntity run, String reason) {
        for (var poll : polls.findByRunId(run.getId())) {
            if (run.getOwner().equals(poll.getOwner())) {
                poll.setReason(reason);
                polls.saveAndFlush(poll);
            }
        }
    }

    private void reconcile(UUID scenarioId, String owner, String token) {
        for (var candidate : runs.findByScenarioIdAndOwnerAndStatusIn(scenarioId, owner, List.of("PENDING", "UNKNOWN"))) {
            if (candidate.getCommandId() == null) continue;
            var command = commandRows.findByCommandIdAndOwner(candidate.getCommandId(), owner).orElse(null);
            // No row means reservation may still be in progress. Expiry handles an orphan; never resend it.
            if (command == null || command.getResolutionNote() != null) continue;
            if (Set.of("PENDING", "UNKNOWN").contains(command.getStatus())) {
                try { commands.check(command.getCommandId(), owner, token); }
                catch (CatalogException unavailable) { continue; }
            }
            tx.execute(status -> {
                store.lock();
                var current = commandRows.findByCommandIdAndOwner(candidate.getCommandId(), owner).orElse(null);
                var run = runs.findById(candidate.getId()).filter(r -> owner.equals(r.getOwner())).orElse(null);
                if (current == null || run == null || "SUCCEEDED".equals(run.getStatus())) return null;
                String actual = switch (current.getStatus()) {
                    case "CONFIRMED" -> "SUCCEEDED";
                    case "FAILED" -> "FAILED";
                    case "UNKNOWN" -> "UNKNOWN";
                    default -> "PENDING";
                };
                // A late pending receipt must not erase known uncertainty.
                if ("UNKNOWN".equals(run.getStatus()) && "PENDING".equals(actual)) return null;
                if (!actual.equals(run.getStatus()) || !Objects.equals(current.getReason(), run.getReason())) {
                    run.setStatus(actual); run.setReason(current.getReason());
                    run.addEntry("RESULT", "Команда: " + actual + " (" + current.getReason() + ")", Instant.now().toString());
                    runs.saveAndFlush(run);
                }
                updatePollReason(run, current.getReason());
                return null;
            });
        }
    }

    private static String sourceIdentity(Snapshot snapshot) {
        // Length prefixes make arbitrary platform names unambiguous without retaining identifiers in logs.
        String data = List.of(snapshot.scenarioId().toString(), Long.toString(snapshot.version()),
                snapshot.trigger().deviceId().toString(), snapshot.trigger().signalKey(), snapshot.trigger().eventKey(),
                snapshot.trigger().operator(), Double.toString(snapshot.trigger().value()), snapshot.serialNumber(),
                snapshot.platformName(), Long.toString(snapshot.bindingVersion())).stream()
                .map(value -> value.length() + ":" + value).collect(java.util.stream.Collectors.joining());
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private ScenarioPollEntity owned(UUID pollId, String owner) {
        return polls
                .findByPollIdAndOwner(pollId, owner)
                .orElseThrow(() -> error(404, "POLL_NOT_FOUND", "Poll result not found"));
    }

    private static Map<String, Object> view(ScenarioPollEntity poll) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pollId", poll.getPollId().toString());
        result.put("scenarioId", poll.getScenarioId().toString());
        result.put("status", poll.getStatus());
        if (poll.getReason() != null) result.put("reason", poll.getReason());
        if (poll.getObservedValue() != null) result.put("observedValue", poll.getObservedValue());
        if (poll.getRunId() != null) result.put("runId", poll.getRunId().toString());
        return result;
    }

    private static void requireToken(String token) {
        if (token == null || token.isBlank())
            throw error(401, "UJIN_AUTH_REQUIRED", "Active Ujin session required");
    }

    private static CatalogException error(int status, String code, String detail) {
        return new CatalogException(status, code, detail);
    }
}
