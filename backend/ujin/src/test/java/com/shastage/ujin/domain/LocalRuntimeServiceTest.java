package com.shastage.ujin.domain;

import com.shastage.ujin.datasource.entity.*;
import com.shastage.ujin.datasource.repository.jpa.*;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.service.CatalogService;
import com.shastage.ujin.domain.service.LocalRuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class LocalRuntimeServiceTest {

    @Autowired LocalRuntimeService runtimeService;
    @Autowired CatalogService catalogService;
    @Autowired LocalSignalStateJpaRepository stateRepo;
    @Autowired EventJpaRepository eventRepo;
    @Autowired CommandJpaRepository commandRepo;
    @Autowired RunJpaRepository runRepo;

    UUID leakDeviceId;
    UUID lightDeviceId;
    UUID scenarioId;

    @BeforeEach
    void setup() {
        // Создаём датчик протечки
        var leakSignals = List.of(
            new Signal("leak", "Протечка", "property", "r", "NUMBER", "list", null, null, null, List.of(0.0, 1.0)),
            new Signal("battery", "Батарея", "property", "r", "NUMBER", "counter", "%", 0.0, 100.0, null)
        );
        var leakEvents = List.of(new Event("leak_changed", "Протечка изменилась", "leak", "CHANGE"));
        var leakWrite = new DeviceWrite("Датчик протечки", "leak", leakSignals, leakEvents, null);
        var leakDevice = catalogService.create(leakWrite, "owner");
        leakDeviceId = leakDevice.id();

        // Создаём светильник
        var lightSignals = List.of(
            new Signal("lamp", "Свет", "capability", "rw", "NUMBER", "switch", null, null, null, List.of(0.0, 1.0)),
            new Signal("brightness", "Яркость", "capability", "rw", "NUMBER", "slider", null, 0.0, 100.0, null)
        );
        var lightWrite = new DeviceWrite("Светильник", "light", lightSignals, List.of(), null);
        var lightDevice = catalogService.create(lightWrite, "owner");
        lightDeviceId = lightDevice.id();

        // Создаём сценарий: leak EQ 1 → lamp=1
        var trigger = new Trigger(leakDeviceId, "leak", "EQ", 1.0, "leak_changed");
        var action = new Action(lightDeviceId, "lamp", 1.0);
        var scenarioWrite = new ScenarioWrite("Протечка — включить свет", true, "LOCAL", trigger, action, null);
        var scenario = catalogService.create(scenarioWrite, "owner");
        scenarioId = scenario.id();
    }

    @Test
    void journalReturnsNewestPageAndExactEventBeyondFirstTwenty() {
        UUID latestEvent = null;
        for (int i = 0; i < 21; i++) {
            UUID event = UUID.randomUUID();
            runtimeService.processStimulus(leakDeviceId, event, "WET", 1, "owner");
            var run = runRepo.findAll().stream().filter(r -> event.equals(r.getEventId())).findFirst().orElseThrow();
            run.setCreatedAt(java.time.Instant.parse("2026-10-31T12:00:00Z").plusSeconds(i).toString());
            runRepo.saveAndFlush(run);
            latestEvent = event;
        }
        var first = runtimeService.getRuns(0, 20, null, null, "owner");
        var firstItems = (List<?>) first.get("items");
        assertEquals(21L, first.get("total"));
        assertEquals(20, firstItems.size());
        assertEquals(latestEvent.toString(), ((Map<?, ?>) firstItems.get(0)).get("eventId"));
        assertEquals(1, ((List<?>) runtimeService.getRuns(1, 20, null, null, "owner").get("items")).size());
        var filtered = runtimeService.getRuns(0, 20, latestEvent, scenarioId, "owner");
        assertEquals(1L, filtered.get("total"));
        assertEquals(latestEvent.toString(), ((Map<?, ?>) ((List<?>) filtered.get("items")).get(0)).get("eventId"));
    }

    @Test
    void successfulChain() {
        var eventId = UUID.randomUUID();
        var result = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1, "owner");

        assertEquals("PROCESSED", result.get("status"));
        var runIds = (List<?>) result.get("runIds");
        assertEquals(1, runIds.size());

        // Проверяем состояние светильника
        var state = runtimeService.getDeviceState(lightDeviceId, "LOCAL", "owner");
        var values = (List<?>) state.get("values");
        assertTrue(values.stream().anyMatch(v -> {
            var map = (Map<?, ?>) v;
            return "lamp".equals(map.get("signalKey")) && ((Number) map.get("value")).doubleValue() == 1.0;
        }));
    }

    @Test
    void wetZeroIsRealZero() {
        var eventId = UUID.randomUUID();
        runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1, "owner");

        var eventId2 = UUID.randomUUID();
        runtimeService.processStimulus(leakDeviceId, eventId2, "WET", 0, "owner");

        var state = runtimeService.getDeviceState(leakDeviceId, "LOCAL", "owner");
        var values = (List<?>) state.get("values");
        assertTrue(values.stream().anyMatch(v -> {
            var map = (Map<?, ?>) v;
            return "leak".equals(map.get("signalKey")) && ((Number) map.get("value")).doubleValue() == 0.0;
        }));
    }

    @Test
    void conditionFalseSkipsRun() {
        var eventId = UUID.randomUUID();
        var result = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 0, "owner");

        var runIds = (List<?>) result.get("runIds");
        // При value=0 условие EQ 1 не выполняется → SKIPPED
        assertFalse(runIds.isEmpty());

        var run = runtimeService.getRun(UUID.fromString(runIds.get(0).toString()), "owner");
        assertEquals("SKIPPED", run.get("status"));
        assertEquals("CONDITION_FALSE", run.get("reason"));
    }

    @Test
    void disabledScenarioIsSkipped() {
        // Выключаем сценарий
        var scenario = catalogService.scenario(scenarioId, "owner");
        catalogService.update(scenarioId, new ScenarioWrite(
            scenario.title(), false, "LOCAL", scenario.trigger(), scenario.action(), scenario.version()
        ), "owner");

        var eventId = UUID.randomUUID();
        var result = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1, "owner");

        var runIds = (List<?>) result.get("runIds");
        assertFalse(runIds.isEmpty());
        var run = runtimeService.getRun(UUID.fromString(runIds.get(0).toString()), "owner");
        assertEquals("SKIPPED", run.get("status"));
        assertEquals("SCENARIO_DISABLED", run.get("reason"));
    }

    @Test
    void readOnlyActionFails() {
        // Создаём сценарий с действием в read-only сигнал
        var trigger = new Trigger(leakDeviceId, "leak", "EQ", 1.0, "leak_changed");
        var action = new Action(leakDeviceId, "leak", 1.0); // read-only!
        var failure = assertThrows(CatalogException.class, () ->
            catalogService.create(new ScenarioWrite("Bad", true, "LOCAL", trigger, action, null), "owner"));
        assertEquals("SIGNAL_READ_ONLY", failure.code);
        assertThrows(CatalogException.class, () ->
            runtimeService.processCommand(leakDeviceId, UUID.randomUUID(), "leak", 1.0, "LOCAL", "owner"));
    }

    @Test
    void ujinModeRejected() {
        var commandId = UUID.randomUUID();
        assertThrows(CatalogException.class, () ->
            runtimeService.processCommand(lightDeviceId, commandId, "lamp", 1.0, "UJIN", "owner")
        );
    }

    @Test
    void duplicateEventIdReturnsSameResult() {
        var eventId = UUID.randomUUID();
        var result1 = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1, "owner");
        var result2 = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1, "owner");

        assertEquals(result1.get("eventId"), result2.get("eventId"));
        assertEquals(result1.get("runIds"), result2.get("runIds"));
    }

    @Test
    void duplicateEventIdDifferentParamsConflict() {
        var eventId = UUID.randomUUID();
        runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1, "owner");

        assertThrows(CatalogException.class, () ->
            runtimeService.processStimulus(leakDeviceId, eventId, "WET", 0, "owner")
        );
    }

    @Test
    void duplicateCommandIdReturnsSameResult() {
        var commandId = UUID.randomUUID();
        var result1 = runtimeService.processCommand(lightDeviceId, commandId, "lamp", 1.0, "LOCAL", "owner");
        var result2 = runtimeService.processCommand(lightDeviceId, commandId, "lamp", 1.0, "LOCAL", "owner");

        assertEquals(result1.get("commandId"), result2.get("commandId"));
    }

    @Test
    void duplicateCommandIdDifferentParamsConflict() {
        var commandId = UUID.randomUUID();
        runtimeService.processCommand(lightDeviceId, commandId, "lamp", 1.0, "LOCAL", "owner");

        assertThrows(CatalogException.class, () ->
            runtimeService.processCommand(lightDeviceId, commandId, "lamp", 0.0, "LOCAL", "owner")
        );
    }

    @Test
    void invalidValueRejected() {
        assertThrows(CatalogException.class, () ->
            runtimeService.processCommand(lightDeviceId, UUID.randomUUID(), "brightness", 200.0, "LOCAL", "owner")
        );
    }

    @Test
    void runsPaginationValidation() {
        assertThrows(CatalogException.class, () -> runtimeService.getRuns(-1, 20, null, null, "owner"));
        assertThrows(CatalogException.class, () -> runtimeService.getRuns(0, 0, null, null, "owner"));
        assertThrows(CatalogException.class, () -> runtimeService.getRuns(0, 101, null, null, "owner"));
    }
    @Test
    void offlineTargetIsBlockedAndReplayPreservesSkippedRuns() {
        runtimeService.setAvailability(lightDeviceId, "OFFLINE", "owner");
        assertEquals("OFFLINE", runtimeService.getDeviceState(lightDeviceId, "LOCAL", "owner").get("availability"));
        var eventId = UUID.randomUUID();
        var receipt = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1, "owner");
        var ids = (List<?>) receipt.get("runIds");
        var run = runtimeService.getRun(UUID.fromString(ids.get(0).toString()), "owner");
        assertEquals("BLOCKED", run.get("status"));
        assertEquals("DEVICE_OFFLINE", run.get("reason"));
        assertEquals(receipt, runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1, "owner"));
        var dryId = UUID.randomUUID();
        var dry = runtimeService.processStimulus(leakDeviceId, dryId, "WET", 0, "owner");
        assertEquals(dry, runtimeService.processStimulus(leakDeviceId, dryId, "WET", 0, "owner"));
    }
    private Map<String, Object> observedRun(Map<String, Object> receipt) {
        assertEquals("PROCESSED", receipt.get("status"));
        var ids = (List<?>) receipt.get("runIds");
        assertEquals(1, ids.size());
        return runtimeService.getRun(UUID.fromString(ids.getFirst().toString()), "owner");
    }

    @Test
    void unchangedNewEventDoesNotRepeatActionButTransitionsStillExecute() {
        assertEquals("CONDITION_FALSE", observedRun(runtimeService.processStimulus(leakDeviceId, UUID.randomUUID(), "WET", 0, "owner")).get("reason"));
        var firstId = UUID.randomUUID();
        var first = runtimeService.processStimulus(leakDeviceId, firstId, "WET", 1, "owner");
        assertEquals("SUCCEEDED", observedRun(first).get("status"));
        runtimeService.processCommand(lightDeviceId, UUID.randomUUID(), "lamp", 0.0, "LOCAL", "owner");
        var timestamp = stateRepo.findByDeviceIdAndSignalKeyAndOwner(leakDeviceId, "leak", "owner").orElseThrow().getUpdatedAt();
        var repeatedId = UUID.randomUUID();
        var repeated = runtimeService.processStimulus(leakDeviceId, repeatedId, "WET", 1, "owner");
        assertEquals("SKIPPED", observedRun(repeated).get("status"));
        assertEquals("NO_STATE_CHANGE", observedRun(repeated).get("reason"));
        assertEquals(0.0, stateRepo.findByDeviceIdAndSignalKeyAndOwner(lightDeviceId, "lamp", "owner").orElseThrow().getValue());
        assertEquals(timestamp, stateRepo.findByDeviceIdAndSignalKeyAndOwner(leakDeviceId, "leak", "owner").orElseThrow().getUpdatedAt());
        assertEquals(repeated, runtimeService.processStimulus(leakDeviceId, repeatedId, "WET", 1, "owner"));
        assertEquals(409, assertThrows(CatalogException.class, () -> runtimeService.processStimulus(leakDeviceId, repeatedId, "WET", 0, "owner")).status);
        assertEquals("CONDITION_FALSE", observedRun(runtimeService.processStimulus(leakDeviceId, UUID.randomUUID(), "WET", 0, "owner")).get("reason"));
        assertEquals(first, runtimeService.processStimulus(leakDeviceId, firstId, "WET", 1, "owner"));
        assertEquals(0.0, stateRepo.findByDeviceIdAndSignalKeyAndOwner(leakDeviceId, "leak", "owner").orElseThrow().getValue());
        assertEquals("SUCCEEDED", observedRun(runtimeService.processStimulus(leakDeviceId, UUID.randomUUID(), "WET", 1, "owner")).get("status"));
        assertEquals(1.0, stateRepo.findByDeviceIdAndSignalKeyAndOwner(lightDeviceId, "lamp", "owner").orElseThrow().getValue());
    }

    @Test
    void unchangedZeroIsNotConfusedWithConditionFalse() {
        assertEquals("CONDITION_FALSE", observedRun(runtimeService.processStimulus(leakDeviceId, UUID.randomUUID(), "WET", 0, "owner")).get("reason"));
        assertEquals("NO_STATE_CHANGE", observedRun(runtimeService.processStimulus(leakDeviceId, UUID.randomUUID(), "WET", 0, "owner")).get("reason"));
        assertTrue(stateRepo.findByDeviceIdAndSignalKeyAndOwner(lightDeviceId, "lamp", "owner").isEmpty());
    }

    @Test
    void restoringOfflineTargetRequiresANewSourceTransition() {
        runtimeService.setAvailability(lightDeviceId, "OFFLINE", "owner");
        assertEquals("BLOCKED", observedRun(runtimeService.processStimulus(leakDeviceId, UUID.randomUUID(), "WET", 1, "owner")).get("status"));
        runtimeService.setAvailability(lightDeviceId, "ONLINE", "owner");
        assertEquals("NO_STATE_CHANGE", observedRun(runtimeService.processStimulus(leakDeviceId, UUID.randomUUID(), "WET", 1, "owner")).get("reason"));
        runtimeService.processStimulus(leakDeviceId, UUID.randomUUID(), "WET", 0, "owner");
        assertEquals("SUCCEEDED", observedRun(runtimeService.processStimulus(leakDeviceId, UUID.randomUUID(), "WET", 1, "owner")).get("status"));
    }
}
