package com.shastage.ujin.domain;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class RuntimeConcurrencyTest {
    @Autowired CatalogService catalog;
    @Autowired LocalRuntimeService runtime;
    @Test void concurrentReplayReturnsOneCommand() throws Exception {
        var d = catalog.create(new DeviceWrite("Concurrent light", "light", List.of(
            new Signal("lamp", "Lamp", "capability", "rw", "NUMBER", "switch", null, 0.0, 1.0, null)), List.of(), null), "owner");
        var id = UUID.randomUUID();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Map<String,Object>> task = () -> { ready.countDown(); start.await(); return runtime.processCommand(d.id(), id, "lamp", 1.0, "LOCAL", "owner"); };
            var a = pool.submit(task); var b = pool.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals("CONFIRMED", runtime.getCommand(id, "owner").get("status"));
        }
    }
    @Test void concurrentDistinctEventsForSameValueExecuteActionOnce() throws Exception {
        var sensor = catalog.create(new DeviceWrite("Concurrent leak", "leak", List.of(
            new Signal("leak", "Leak", "property", "r", "NUMBER", "switch", null, 0.0, 1.0, null)),
            List.of(new Event("changed", "Changed", "leak", "CHANGE")), null), "owner");
        var target = catalog.create(new DeviceWrite("Concurrent target", "light", List.of(
            new Signal("lamp", "Lamp", "capability", "rw", "NUMBER", "switch", null, 0.0, 1.0, null)), List.of(), null), "owner");
        catalog.create(new ScenarioWrite("Concurrent CHANGE", true, "LOCAL", new Trigger(sensor.id(), "leak", "EQ", 1.0, "changed"), new Action(target.id(), "lamp", 1.0), null), "owner");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Map<String,Object>> task = () -> { ready.countDown(); start.await(); return runtime.processStimulus(sensor.id(), UUID.randomUUID(), "WET", 1, "owner"); };
            var a = pool.submit(task); var b = pool.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            var statuses = new ArrayList<String>();
            for (var receipt : List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))) {
                var id = ((List<?>) receipt.get("runIds")).getFirst().toString();
                var run = runtime.getRun(UUID.fromString(id), "owner");
                statuses.add(run.get("status").toString());
                if ("SKIPPED".equals(run.get("status"))) assertEquals("NO_STATE_CHANGE", run.get("reason"));
            }
            Collections.sort(statuses);
            assertEquals(List.of("SKIPPED", "SUCCEEDED"), statuses);
        }
    }
}
