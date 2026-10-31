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
}
