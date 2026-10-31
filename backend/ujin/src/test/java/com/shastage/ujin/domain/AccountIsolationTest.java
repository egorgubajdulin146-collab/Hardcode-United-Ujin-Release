package com.shastage.ujin.domain;

import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AccountIsolationTest {

    @Autowired CatalogService catalog;
    @Autowired LocalRuntimeService runtime;

    private DeviceWrite deviceWrite(String title) {
        return new DeviceWrite(title, "light", List.of(
            new Signal("lamp", "Lamp", "capability", "rw", "NUMBER", "switch", null, 0.0, 1.0, null)
        ), List.of(), null);
    }

    @Test
    void accountBCannotSeeAccountAsDevices() {
        // A создаёт устройство
        var deviceA = catalog.create(deviceWrite("Device A"), "userA");

        // B не видит устройство A
        var devicesB = catalog.devices(0, 20, "userB");
        assertEquals(0, devicesB.items().size());

        // B не получает устройство A по UUID
        assertThrows(CatalogException.class, () -> catalog.device(deviceA.id(), "userB"));
    }

    @Test
    void accountBCannotUseAccountAsCommandId() {
        // A создаёт команду
        var deviceA = catalog.create(deviceWrite("Light A"), "userA2");
        var commandId = UUID.randomUUID();
        runtime.processCommand(deviceA.id(), commandId, "lamp", 1.0, "LOCAL", "userA2");

        // B создаёт команду с тем же commandId
        var deviceB = catalog.create(deviceWrite("Light B"), "userB2");
        var error = assertThrows(CatalogException.class, () ->
            runtime.processCommand(deviceB.id(), commandId, "lamp", 0.0, "LOCAL", "userB2"));
        assertEquals("COMMAND_NOT_FOUND", error.code);

        // Квитанция A сохраняется
        var receiptA = runtime.getCommand(commandId, "userA2");
        assertEquals("CONFIRMED", receiptA.get("status"));

        // B не видит квитанцию A
        assertThrows(CatalogException.class, () -> runtime.getCommand(commandId, "userB2"));
    }

    @Test
    void accountBCannotUseAccountAsEventId() {
        // A создаёт событие
        var deviceA = catalog.create(new DeviceWrite("Leak A", "leak",
            List.of(new Signal("leak", "Leak", "property", "r", "NUMBER", "list", null, null, null, List.of(0.0, 1.0))),
            List.of(new Event("leak_changed", "Leak changed", "leak", "CHANGE")), null), "userA3");
        var eventId = UUID.randomUUID();
        runtime.processStimulus(deviceA.id(), eventId, "WET", 1, "userA3");

        // B создаёт событие с тем же eventId
        var deviceB = catalog.create(new DeviceWrite("Leak B", "leak",
            List.of(new Signal("leak", "Leak", "property", "r", "NUMBER", "list", null, null, null, List.of(0.0, 1.0))),
            List.of(new Event("leak_changed", "Leak changed", "leak", "CHANGE")), null), "userB3");
        var error = assertThrows(CatalogException.class, () ->
            runtime.processStimulus(deviceB.id(), eventId, "WET", 1, "userB3"));
        assertEquals("EVENT_NOT_FOUND", error.code);

        // Квитанция A сохраняется
        var receiptA = runtime.getEvent(eventId, "userA3");
        assertEquals("PROCESSED", receiptA.get("status"));

        // B не видит квитанцию A
        assertThrows(CatalogException.class, () -> runtime.getEvent(eventId, "userB3"));
    }

    @Test
    void filtersAndTotalsDoNotLeak() {
        catalog.create(deviceWrite("Device A4"), "userA4");

        var devicesB = catalog.devices(0, 20, "userB4");
        assertEquals(0L, devicesB.total());

        var runsB = runtime.getRuns(0, 20, null, null, "userB4");
        assertEquals(0L, runsB.get("total"));
    }
}
