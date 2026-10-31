package com.shastage.ujin.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.shastage.ujin.UjinApplication;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.model.PlatformBinding;
import com.shastage.ujin.domain.service.CatalogService;
import com.shastage.ujin.domain.service.PlatformBindingService;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

class PlatformBindingRestartTest {
    @TempDir Path directory;

    private HttpServer upstream;

    private HttpServer startUpstream() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/apartment/get-device-signals/",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    byte[] body =
                            """
                            {"error":0,"data":{"devices":[{"id":"sn-keep","signals":[
                              {"name":"leak","value":1}]}]}}"""
                                    .getBytes(StandardCharsets.UTF_8);
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
                        + directory.resolve("binding").toAbsolutePath().toString().replace('\\', '/'),
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--ujin.api.base-url=http://127.0.0.1:" + upstream.getAddress().getPort() + "/api");
    }

    @Test
    void bindingSurvivesApplicationRestart() throws Exception {
        upstream = startUpstream();
        UUIDHolder device;
        try (var context = start()) {
            var catalog = context.getBean(CatalogService.class);
            var binding = context.getBean(PlatformBindingService.class);
            device =
                    new UUIDHolder(
                            catalog.create(
                                            new DeviceWrite(
                                                    "Leak",
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
                                                    null),
                                            "owner")
                                    .id());
            var saved =
                    binding.putBinding(
                            device.id,
                            new PlatformBinding.Write(
                                    "sn-keep",
                                    List.of(new PlatformBinding.Mapping("leak", "leak")),
                                    null),
                            "owner",
                            "session-token");
            assertTrue(saved.created());
        }
        try (var context = start()) {
            var binding = context.getBean(PlatformBindingService.class);
            var catalog = context.getBean(CatalogService.class);
            var view = binding.getBinding(device.id, "owner");
            assertEquals("sn-keep", view.serialNumber());
            assertEquals(
                    List.of(new PlatformBinding.Mapping("leak", "leak")), view.signalMappings());
            assertEquals(1, view.version());
            assertEquals("BOUND", catalog.device(device.id, "owner").platformLink());
        } finally {
            upstream.stop(0);
        }
    }

    private record UUIDHolder(java.util.UUID id) {}
}
