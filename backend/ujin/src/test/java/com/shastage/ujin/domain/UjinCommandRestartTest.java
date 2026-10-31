package com.shastage.ujin.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.shastage.ujin.UjinApplication;
import com.shastage.ujin.datasource.client.PlatformTask;
import com.shastage.ujin.datasource.entity.BindingSignal;
import com.shastage.ujin.datasource.entity.PlatformBindingEntity;
import com.shastage.ujin.datasource.entity.CommandEntity;
import com.shastage.ujin.datasource.repository.jpa.CommandJpaRepository;
import com.shastage.ujin.datasource.repository.jpa.PlatformBindingJpaRepository;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.service.CatalogService;
import com.shastage.ujin.domain.service.UjinCommandService;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class UjinCommandRestartTest {
    @TempDir Path directory;

    private HttpServer upstream;

    private HttpServer startUpstream() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
                        + directory.resolve("commands").toAbsolutePath().toString().replace('\\', '/'),
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--ujin.api.base-url=http://127.0.0.1:" + upstream.getAddress().getPort() + "/api",
                "--ujin.commands.enabled=true",
                "--ujin.commands.task-fetch-enabled=true");
    }

    @Test
    void taskAckAndCommandStatusSurviveRestart() throws Exception {
        upstream = startUpstream();
        UUID device;
        try (var context = start()) {
            var catalog = context.getBean(CatalogService.class);
            var commands = context.getBean(UjinCommandService.class);
            device =
                    catalog.create(
                                    new DeviceWrite(
                                            "Lamp",
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
                                    "owner")
                            .id();
            var binding = new PlatformBindingEntity();
            binding.setDeviceId(device);
            binding.setOwner("owner");
            binding.setSerialNumber("sn-restart");
            binding.getSignals().add(new BindingSignal("lamp", "lamp"));
            context.getBean(PlatformBindingJpaRepository.class).saveAndFlush(binding);

            UUID commandId = UUID.randomUUID();
            var receipt = commands.send(device, commandId, "lamp", 1.0, "owner", "session-token");
            assertEquals("PENDING", receipt.get("status"));

            var outcome =
                    commands.applyTask(
                            device, "owner", new PlatformTask("lamp", "1", "t-restart", "0"), "session-token");
            assertEquals(UjinCommandService.ApplyOutcome.APPLIED, outcome);
        }
        try (var context = start()) {
            var commands = context.getBean(UjinCommandService.class);
            var repo = context.getBean(CommandJpaRepository.class);
            var again =
                    commands.applyTask(
                            device, "owner", new PlatformTask("lamp", "1", "t-restart", "0"), "session-token");
            assertEquals(UjinCommandService.ApplyOutcome.SKIPPED_DUPLICATE, again);
            CommandEntity command = repo.findAll().stream().findFirst().orElseThrow();
            assertEquals("CONFIRMED", command.getStatus());
            assertEquals("TASK_APPLIED", command.getReason());
        } finally {
            upstream.stop(0);
        }
    }
}
