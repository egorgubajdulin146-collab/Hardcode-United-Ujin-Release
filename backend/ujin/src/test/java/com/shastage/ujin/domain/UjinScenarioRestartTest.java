package com.shastage.ujin.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.shastage.ujin.UjinApplication;
import com.shastage.ujin.datasource.entity.BindingSignal;
import com.shastage.ujin.datasource.entity.PlatformBindingEntity;
import com.shastage.ujin.datasource.repository.jpa.PlatformBindingJpaRepository;
import com.shastage.ujin.datasource.repository.jpa.ScenarioObservationJpaRepository;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.service.CatalogService;
import com.shastage.ujin.domain.service.ScenarioObservationService;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class UjinScenarioRestartTest {
    @TempDir Path directory;

    private HttpServer upstream;
    private volatile String snapshot =
            "{\"error\":0,\"data\":{\"devices\":[{\"id\":\"sn-keep\",\"signals\":[{\"name\":\"leak\",\"value\":0}]}]}}";

    private HttpServer startUpstream() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/apartment/get-device-signals/",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    byte[] body = snapshot.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        server.createContext(
                "/api/apartment/send-signal/",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    byte[] body = "{\"error\":0,\"data\":{}}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        server.start();
        return server;
    }

    private ConfigurableApplicationContext start() {
        var application = new SpringApplication(UjinApplication.class);
        application.setRegisterShutdownHook(false);
        return application.run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:file:"
                        + directory.resolve("observation").toAbsolutePath().toString().replace('\\', '/'),
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--ujin.api.base-url=http://127.0.0.1:" + upstream.getAddress().getPort() + "/api",
                "--ujin.scenarios.observation-enabled=true",
                "--ujin.commands.enabled=true");
    }

    @Test
    void baselineAndPollResultsSurviveRestart() throws Exception {
        upstream = startUpstream();
        UUID scenarioId;
        UUID firstPoll = UUID.randomUUID();
        try (var context = start()) {
            var catalog = context.getBean(CatalogService.class);
            var observation = context.getBean(ScenarioObservationService.class);
            var bindings = context.getBean(PlatformBindingJpaRepository.class);
            var source =
                    catalog.create(
                            new DeviceWrite(
                                    "Source",
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
                                    List.of(new Event("leak_changed", "Leak changed", "leak", "CHANGE")),
                                    null),
                            "owner");
            var target =
                    catalog.create(
                            new DeviceWrite(
                                    "Target",
                                    "light",
                                    List.of(
                                            new Signal(
                                                    "lamp",
                                                    "Lamp",
                                                    "capability",
                                                    "rw",
                                                    "NUMBER",
                                                    "switch",
                                                    null,
                                                    0.0,
                                                    1.0,
                                                    null)),
                                    List.of(),
                                    null),
                            "owner");
            for (var pair :
                    List.of(
                            new Object[] {source.id(), "leak"},
                            new Object[] {target.id(), "lamp"})) {
                var binding = new PlatformBindingEntity();
                binding.setDeviceId((UUID) pair[0]);
                binding.setOwner("owner");
                binding.setSerialNumber("sn-keep");
                binding.getSignals().add(new BindingSignal((String) pair[1], (String) pair[1]));
                bindings.saveAndFlush(binding);
            }
            scenarioId =
                    catalog.create(
                                    new ScenarioWrite(
                                            "Rule",
                                            true,
                                            "UJIN",
                                            new Trigger(source.id(), "leak", "EQ", 1.0, "leak_changed"),
                                            new Action(target.id(), "lamp", 1.0),
                                            null),
                                    "owner")
                            .id();
            var baseline = observation.poll(scenarioId, firstPoll, "owner", "token");
            assertEquals("BASELINE_STORED", baseline.get("status"));
            snapshot =
                    "{\"error\":0,\"data\":{\"devices\":[{\"id\":\"sn-keep\",\"signals\":[{\"name\":\"leak\",\"value\":1}]}]}}";
            var changed = observation.poll(scenarioId, UUID.randomUUID(), "owner", "token");
            assertEquals("RUN_CREATED", changed.get("status"));
        }
        try (var context = start()) {
            var observation = context.getBean(ScenarioObservationService.class);
            var observations = context.getBean(ScenarioObservationJpaRepository.class);
            // Baseline пережил рестарт: повторное чтение того же значения не запускает правило.
            var replay = observation.poll(scenarioId, firstPoll, "owner", "token");
            assertEquals("BASELINE_STORED", replay.get("status"));
            assertEquals(
                    1.0,
                    observations.findByScenarioIdAndOwner(scenarioId, "owner").orElseThrow().getBaselineValue());
            var sameValue = observation.poll(scenarioId, UUID.randomUUID(), "owner", "token");
            assertEquals("NO_CHANGE", sameValue.get("status"));
            assertNull(sameValue.get("runId"));
        } finally {
            upstream.stop(0);
        }
    }
}
