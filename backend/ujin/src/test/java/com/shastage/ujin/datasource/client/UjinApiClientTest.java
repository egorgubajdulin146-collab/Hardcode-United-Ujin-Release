package com.shastage.ujin.datasource.client;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UjinApiClientTest {
    private HttpServer server;
    private UjinApiClient client;
    private final AtomicReference<String> authBody = new AtomicReference<>();
    private final AtomicReference<String> readQuery = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/authenticate/", exchange -> {
            authBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"error\":0,\"data\":{\"user\":{\"token\":\"session-token\"}}}");
        });
        server.createContext("/api/bmsdevice/get-device-info/", exchange -> {
            readQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, "{\"error\":0,\"data\":{\"signals\":[{\"name\":\"lamp\",\"state\":{\"value\":1}}]}}");
        });
        server.start();
        client = new UjinApiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api"));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void authenticatesThenReadsAssignedSignal() {
        String token = client.authenticate("employee", "pass word", "staff-app");
        assertEquals("session-token", token);
        assertEquals("login=employee&password=pass+word&app=staff-app", authBody.get());
        assertEquals("1", client.readSignal(token, "device-1", "lamp"));
        assertTrue(readQuery.get().contains("token=session-token"));
        assertTrue(readQuery.get().contains("serialnumber=device-1"));
    }

    @Test
    void missingSignalIsNotReportedAsSuccess() {
        assertEquals("Requested signal was not found", assertThrows(IllegalStateException.class,
                () -> client.readSignal("session-token", "device-1", "leak")).getMessage());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
