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
        var leakDevice = catalogService.create(leakWrite);
        leakDeviceId = leakDevice.id();

        // Создаём светильник
        var lightSignals = List.of(
            new Signal("lamp", "Свет", "capability", "rw", "NUMBER", "switch", null, null, null, List.of(0.0, 1.0)),
            new Signal("brightness", "Яркость", "capability", "rw", "NUMBER", "slider", null, 0.0, 100.0, null)
        );
        var lightWrite = new DeviceWrite("Светильник", "light", lightSignals, List.of(), null);
        var lightDevice = catalogService.create(lightWrite);
        lightDeviceId = lightDevice.id();

        // Создаём сценарий: leak EQ 1 → lamp=1
        var trigger = new Trigger(leakDeviceId, "leak", "EQ", 1.0, "leak_changed");
        var action = new Action(lightDeviceId, "lamp", 1.0);
        var scenarioWrite = new ScenarioWrite("Протечка — включить свет", true, "LOCAL", trigger, action, null);
        var scenario = catalogService.create(scenarioWrite);
        scenarioId = scenario.id();
    }

    @Test
    void successfulChain() {
        var eventId = UUID.randomUUID();
        var result = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1);

        assertEquals("PROCESSED", result.get("status"));
        var runIds = (List<?>) result.get("runIds");
        assertEquals(1, runIds.size());

        // Проверяем состояние светильника
        var state = runtimeService.getDeviceState(lightDeviceId, "LOCAL");
        var values = (List<?>) state.get("values");
        assertTrue(values.stream().anyMatch(v -> {
            var map = (Map<?, ?>) v;
            return "lamp".equals(map.get("signalKey")) && ((Number) map.get("value")).doubleValue() == 1.0;
        }));
    }

    @Test
    void wetZeroIsRealZero() {
        var eventId = UUID.randomUUID();
        runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1);

        var eventId2 = UUID.randomUUID();
        runtimeService.processStimulus(leakDeviceId, eventId2, "WET", 0);

        var state = runtimeService.getDeviceState(leakDeviceId, "LOCAL");
        var values = (List<?>) state.get("values");
        assertTrue(values.stream().anyMatch(v -> {
            var map = (Map<?, ?>) v;
            return "leak".equals(map.get("signalKey")) && ((Number) map.get("value")).doubleValue() == 0.0;
        }));
    }

    @Test
    void conditionFalseSkipsRun() {
        var eventId = UUID.randomUUID();
        var result = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 0);

        var runIds = (List<?>) result.get("runIds");
        // При value=0 условие EQ 1 не выполняется → SKIPPED
        assertFalse(runIds.isEmpty());

        var run = runtimeService.getRun((UUID) runIds.get(0));
        assertEquals("SKIPPED", run.get("status"));
        assertEquals("CONDITION_FALSE", run.get("reason"));
    }

    @Test
    void disabledScenarioIsSkipped() {
        // Выключаем сценарий
        var scenario = catalogService.scenario(scenarioId);
        catalogService.update(scenarioId, new ScenarioWrite(
            scenario.title(), false, "LOCAL", scenario.trigger(), scenario.action(), scenario.version()
        ));

        var eventId = UUID.randomUUID();
        var result = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1);

        var runIds = (List<?>) result.get("runIds");
        assertFalse(runIds.isEmpty());
        var run = runtimeService.getRun((UUID) runIds.get(0));
        assertEquals("SKIPPED", run.get("status"));
        assertEquals("SCENARIO_DISABLED", run.get("reason"));
    }

    @Test
    void readOnlyActionFails() {
        // Создаём сценарий с действием в read-only сигнал
        var trigger = new Trigger(leakDeviceId, "leak", "EQ", 1.0, "leak_changed");
        var action = new Action(leakDeviceId, "leak", 1.0); // read-only!
        catalogService.create(new ScenarioWrite("Bad", true, "LOCAL", trigger, action, null));

        var eventId = UUID.randomUUID();
        var result = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1);

        var runIds = (List<?>) result.get("runIds");
        assertFalse(runIds.isEmpty());
        // Один из запусков должен быть FAILED
        var statuses = runIds.stream().map(id -> runtimeService.getRun((UUID) id).get("status")).toList();
        assertTrue(statuses.contains("FAILED"));
    }

    @Test
    void ujinModeRejected() {
        var commandId = UUID.randomUUID();
        assertThrows(CatalogException.class, () ->
            runtimeService.processCommand(lightDeviceId, commandId, "lamp", 1.0, "UJIN")
        );
    }

    @Test
    void duplicateEventIdReturnsSameResult() {
        var eventId = UUID.randomUUID();
        var result1 = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1);
        var result2 = runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1);

        assertEquals(result1.get("eventId"), result2.get("eventId"));
        assertEquals(result1.get("runIds"), result2.get("runIds"));
    }

    @Test
    void duplicateEventIdDifferentParamsConflict() {
        var eventId = UUID.randomUUID();
        runtimeService.processStimulus(leakDeviceId, eventId, "WET", 1);

        assertThrows(CatalogException.class, () ->
            runtimeService.processStimulus(leakDeviceId, eventId, "WET", 0)
        );
    }

    @Test
    void duplicateCommandIdReturnsSameResult() {
        var commandId = UUID.randomUUID();
        var result1 = runtimeService.processCommand(lightDeviceId, commandId, "lamp", 1.0, "LOCAL");
        var result2 = runtimeService.processCommand(lightDeviceId, commandId, "lamp", 1.0, "LOCAL");

        assertEquals(result1.get("commandId"), result2.get("commandId"));
    }

    @Test
    void duplicateCommandIdDifferentParamsConflict() {
        var commandId = UUID.randomUUID();
        runtimeService.processCommand(lightDeviceId, commandId, "lamp", 1.0, "LOCAL");

        assertThrows(CatalogException.class, () ->
            runtimeService.processCommand(lightDeviceId, commandId, "lamp", 0.0, "LOCAL")
        );
    }

    @Test
    void invalidValueRejected() {
        assertThrows(CatalogException.class, () ->
            runtimeService.processCommand(lightDeviceId, UUID.randomUUID(), "brightness", 200.0, "LOCAL")
        );
    }

    @Test
    void runsPaginationValidation() {
        assertThrows(CatalogException.class, () -> runtimeService.getRuns(-1, 20, null, null));
        assertThrows(CatalogException.class, () -> runtimeService.getRuns(0, 0, null, null));
        assertThrows(CatalogException.class, () -> runtimeService.getRuns(0, 101, null, null));
    }
}
