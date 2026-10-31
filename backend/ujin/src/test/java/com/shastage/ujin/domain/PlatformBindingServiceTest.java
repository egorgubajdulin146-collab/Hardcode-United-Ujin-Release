package com.shastage.ujin.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.shastage.ujin.datasource.entity.BindingSignal;
import com.shastage.ujin.datasource.entity.PlatformBindingEntity;
import com.shastage.ujin.datasource.repository.jpa.PlatformBindingJpaRepository;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.model.PlatformBinding;
import com.shastage.ujin.domain.service.CatalogService;
import com.shastage.ujin.domain.service.PlatformBindingService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@SpringBootTest
@Transactional
class PlatformBindingServiceTest {

    @Autowired CatalogService catalog;
    @Autowired PlatformBindingService bindingService;
    @Autowired PlatformBindingJpaRepository bindings;

    private DeviceWrite deviceWrite(String title) {
        return new DeviceWrite(
                title,
                "leak",
                List.of(
                        new Signal(
                                "leak",
                                "Leak",
                                "property",
                                "r",
                                "NUMBER",
                                "list",
                                null,
                                0.0,
                                1.0,
                                null)),
                List.of(),
                null);
    }

    private static PlatformBinding.Mapping mapping(String key, String name) {
        return new PlatformBinding.Mapping(key, name);
    }

    @Test
    void activeUjinSessionIsRequiredForBindingAndState() {
        var device = catalog.create(deviceWrite("Leak"), "owner1");
        var error =
                assertThrows(
                        CatalogException.class,
                        () ->
                                bindingService.putBinding(
                                        device.id(),
                                        new PlatformBinding.Write(
                                                "sn-x", List.of(mapping("leak", "leak")), null),
                                        "owner1",
                                        null));
        assertEquals(401, error.status);
        assertEquals("UJIN_AUTH_REQUIRED", error.code);

        var entity = new PlatformBindingEntity();
        entity.setDeviceId(device.id());
        entity.setOwner("owner1");
        entity.setSerialNumber("sn-x");
        entity.getSignals().add(new BindingSignal("leak", "leak"));
        bindings.saveAndFlush(entity);

        var state =
                assertThrows(
                        CatalogException.class,
                        () -> bindingService.readUjinState(device.id(), "owner1", null));
        assertEquals(401, state.status);
        assertEquals("UJIN_AUTH_REQUIRED", state.code);
    }

    @Test
    void unboundDeviceHasNoBindingAndNoUjinState() {
        var device = catalog.create(deviceWrite("Leak2"), "owner2");
        var missing =
                assertThrows(
                        CatalogException.class, () -> bindingService.getBinding(device.id(), "owner2"));
        assertEquals(404, missing.status);
        assertEquals("PLATFORM_NOT_BOUND", missing.code);

        var state =
                assertThrows(
                        CatalogException.class,
                        () -> bindingService.readUjinState(device.id(), "owner2", "token"));
        assertEquals(409, state.status);
        assertEquals("PLATFORM_NOT_BOUND", state.code);
    }

    @Test
    void foreignDeviceIsNotFoundWithoutLeakingBinding() {
        var device = catalog.create(deviceWrite("Leak3"), "owner3");
        var entity = new PlatformBindingEntity();
        entity.setDeviceId(device.id());
        entity.setOwner("owner3");
        entity.setSerialNumber("sn-secret");
        entity.getSignals().add(new BindingSignal("leak", "leak"));
        bindings.saveAndFlush(entity);

        for (var error :
                List.of(
                        assertThrows(
                                CatalogException.class,
                                () -> bindingService.getBinding(device.id(), "owner4")),
                        assertThrows(
                                CatalogException.class,
                                () ->
                                        bindingService.putBinding(
                                                device.id(),
                                                new PlatformBinding.Write(
                                                        "sn-other",
                                                        List.of(mapping("leak", "leak")),
                                                        null),
                                                "owner4",
                                                "token")),
                        assertThrows(
                                CatalogException.class,
                                () ->
                                        bindingService.readUjinState(
                                                device.id(), "owner4", "token")))) {
            assertEquals(404, error.status);
            assertEquals("DEVICE_NOT_FOUND", error.code);
            assertFalse(error.getMessage().contains("sn-secret"));
        }
    }

    @Test
    void serialNumberIsStoredAndReturnedAsExactString() {
        var device = catalog.create(deviceWrite("Leak4"), "owner5");
        var entity = new PlatformBindingEntity();
        entity.setDeviceId(device.id());
        entity.setOwner("owner5");
        entity.setSerialNumber("9007199254740993123");
        entity.getSignals().add(new BindingSignal("leak", "leak"));
        bindings.saveAndFlush(entity);

        var view = bindingService.getBinding(device.id(), "owner5");
        assertEquals("9007199254740993123", view.serialNumber());
        assertEquals(List.of(mapping("leak", "leak")), view.signalMappings());
        assertEquals(1, view.version());
    }

    @Test
    void deleteDeviceRemovesItsBinding() {
        var device = catalog.create(deviceWrite("Leak5"), "owner6");
        var entity = new PlatformBindingEntity();
        entity.setDeviceId(device.id());
        entity.setOwner("owner6");
        entity.setSerialNumber("sn-del");
        entity.getSignals().add(new BindingSignal("leak", "leak"));
        bindings.saveAndFlush(entity);

        catalog.delete(device.id(), device.version(), "owner6");
        assertTrue(bindings.findByDeviceIdAndOwner(device.id(), "owner6").isEmpty());
    }
}
