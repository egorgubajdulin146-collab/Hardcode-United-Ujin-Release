package com.shastage.ujin.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.shastage.ujin.UjinApplication;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.service.CatalogService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;

class CatalogRestartTest {
    @TempDir Path directory;

    private ConfigurableApplicationContext start() {
        var application = new SpringApplication(UjinApplication.class);
        application.setRegisterShutdownHook(false);
        return application.run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:file:"
                        + directory
                                .resolve("catalog")
                                .toAbsolutePath()
                                .toString()
                                .replace('\\', '/'),
                "--spring.datasource.username=sa",
                "--spring.datasource.password=");
    }

    @Test
    void declarationsAndRulesSurviveApplicationRestart() {
        Device source;
        Device target;
        Scenario rule;
        var signals =
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
                                List.of(0.0, 1.0)));
        var events = List.of(new Event("changed", "Changed", "lamp", "CHANGE"));
        try (var context = start()) {
            var service = context.getBean(CatalogService.class);
            source = service.create(new DeviceWrite("Source", "light", signals, events, null), "owner");
            target = service.create(new DeviceWrite("Target", "light", signals, events, null), "owner");
            rule =
                    service.create(
                            new ScenarioWrite(
                                    "Persistent rule",
                                    false,
                                    "UJIN",
                                    new Trigger(source.id(), "lamp", "EQ", 1.0, "changed"),
                                    new Action(target.id(), "lamp", 1.0),
                                    null), "owner");
        }
        try (var context = start()) {
            var service = context.getBean(CatalogService.class);
            assertEquals(source, service.device(source.id(), "owner"));
            assertEquals(target, service.device(target.id(), "owner"));
            assertEquals(rule, service.scenario(rule.id(), "owner"));
            assertEquals(List.of(rule.id()), service.dependencies(target.id(), "owner").scenarioIds());
            assertEquals(
                    2,
                    service.update(
                                    rule.id(),
                                    new ScenarioWrite(
                                            "Updated rule",
                                            true,
                                            "LOCAL",
                                            rule.trigger(),
                                            rule.action(),
                                            1L),
                                    "owner")
                            .version());
        }
    }
    @Test
    void changeDetectionAndOriginalReceiptSurviveRestart() {
        java.util.UUID sensorId;
        var originalId = java.util.UUID.randomUUID();
        java.util.Map<String, Object> original;
        try (var context = start()) {
            var catalog = context.getBean(CatalogService.class);
            var runtime = context.getBean(com.shastage.ujin.domain.service.LocalRuntimeService.class);
            var sensor = catalog.create(new DeviceWrite("Persistent leak", "leak", List.of(
                new Signal("leak", "Leak", "property", "r", "NUMBER", "switch", null, 0.0, 1.0, null)),
                List.of(new Event("changed", "Changed", "leak", "CHANGE")), null), "owner");
            sensorId = sensor.id();
            var target = catalog.create(new DeviceWrite("Persistent lamp", "light", List.of(
                new Signal("lamp", "Lamp", "capability", "rw", "NUMBER", "switch", null, 0.0, 1.0, null)), List.of(), null), "owner");
            catalog.create(new ScenarioWrite("Persistent CHANGE", true, "LOCAL", new Trigger(sensorId, "leak", "EQ", 1.0, "changed"), new Action(target.id(), "lamp", 1.0), null), "owner");
            original = runtime.processStimulus(sensorId, originalId, "WET", 1, "owner");
        }
        try (var context = start()) {
            var runtime = context.getBean(com.shastage.ujin.domain.service.LocalRuntimeService.class);
            assertEquals(original, runtime.processStimulus(sensorId, originalId, "WET", 1, "owner"));
            var repeated = runtime.processStimulus(sensorId, java.util.UUID.randomUUID(), "WET", 1, "owner");
            var runId = ((List<?>) repeated.get("runIds")).getFirst().toString();
            var run = runtime.getRun(java.util.UUID.fromString(runId), "owner");
            assertEquals("SKIPPED", run.get("status"));
            assertEquals("NO_STATE_CHANGE", run.get("reason"));
            runtime.processStimulus(sensorId, java.util.UUID.randomUUID(), "WET", 0, "owner");
            var changed = runtime.processStimulus(sensorId, java.util.UUID.randomUUID(), "WET", 1, "owner");
            var changedId = ((List<?>) changed.get("runIds")).getFirst().toString();
            assertEquals("SUCCEEDED", runtime.getRun(java.util.UUID.fromString(changedId), "owner").get("status"));
        }
    }
}
