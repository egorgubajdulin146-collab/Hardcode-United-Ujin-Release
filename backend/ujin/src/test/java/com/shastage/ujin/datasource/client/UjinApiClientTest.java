package com.shastage.ujin.datasource.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

class UjinApiClientTest {
    private HttpServer server;
    private UjinApiClient client;
    private final AtomicReference<String> authBody = new AtomicReference<>();
    private final AtomicReference<String> readQuery = new AtomicReference<>();
    private final AtomicReference<String> rejectedAuth = new AtomicReference<>();
    private final AtomicReference<String> acceptedAuth =
            new AtomicReference<>(
                    "{\"error\":0,\"data\":{\"user\":{\"token\":\"session-token\"}}}");
    // Фиксированный ответ для чтения сигнала
    private final AtomicReference<String> readResponse =
            new AtomicReference<>(
                    "{\"error\":0,\"data\":{\"devices\":[{\"id\":\"sn-1\",\"signals\":[{\"name\":\"lamp\",\"value\":\"1\"}]}]}}");
    private final java.util.List<String> authRequests =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/auth/authenticate/",
                exchange -> {
                    authBody.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    authRequests.add(authBody.get());
                    respond(
                            exchange,
                            authBody.get().contains("&app=") && rejectedAuth.get() != null
                                    ? rejectedAuth.get()
                                    : acceptedAuth.get());
                });
        server.createContext(
                "/api/apartment/get-device-signals/",
                exchange -> {
                    readQuery.set(exchange.getRequestURI().getRawQuery());
                    respond(exchange, readResponse.get());
                });
        server.start();
        client =
                new UjinApiClient(
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api"));
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
        assertEquals("1", client.readSignal(token, "sn-1", "lamp"));
        assertTrue(readQuery.get().contains("token=session-token"));
    }

    @Test
    void residentRetriesWithoutEmployeeApp() {
        rejectedAuth.set("{\"error\":1,\"data\":{}}");
        assertEquals("session-token", client.authenticate("resident", "pass word", "staff-app"));
    }

    @Test
    void accessDeniedIsClassified() {
        readResponse.set("{\"error\":1,\"error_code\":\"Access denied\",\"message\":\"Access denied\"}");
        var error = assertThrows(UjinApiException.class,
                () -> client.readSignal("tok", "sn-1", "lamp"));
        assertEquals(UjinApiException.Kind.ACCESS_DENIED, error.kind());
    }

    @Test
    void platformErrorIsClassified() {
        readResponse.set("{\"error\":1,\"error_code\":\"unknown\",\"message\":\"Ошибка\"}");
        var error = assertThrows(UjinApiException.class,
                () -> client.readSignal("tok", "sn-1", "lamp"));
        assertEquals(UjinApiException.Kind.PLATFORM_ERROR, error.kind());
    }

    @Test
    void signalNotFound() {
        readResponse.set("{\"error\":0,\"data\":{\"devices\":[{\"id\":\"sn-1\",\"signals\":[{\"name\":\"other\",\"value\":1}]}]}}");
        var error = assertThrows(UjinApiException.class,
                () -> client.readSignal("tok", "sn-1", "lamp"));
        assertEquals(UjinApiException.Kind.SIGNAL_NOT_FOUND, error.kind());
    }

    @Test
    void deviceNotFound() {
        readResponse.set("{\"error\":0,\"data\":{\"devices\":[{\"id\":\"other\",\"signals\":[{\"name\":\"lamp\",\"value\":1}]}]}}");
        var error = assertThrows(UjinApiException.class,
                () -> client.readSignal("tok", "sn-1", "lamp"));
        assertEquals(UjinApiException.Kind.SIGNAL_NOT_FOUND, error.kind());
    }

    @Test
    void successfulRead() {
        readResponse.set("{\"error\":0,\"data\":{\"devices\":[{\"id\":\"sn-1\",\"signals\":[{\"name\":\"lamp\",\"value\":\"1\"}]}]}}");
        var value = client.readSignal("tok", "sn-1", "lamp");
        assertEquals("1", value);
    }

    @Test
    void networkFailureIsUnavailable() {
        server.stop(0);
        var error = assertThrows(UjinApiException.class,
                () -> client.readSignal("tok", "sn-1", "lamp"));
        assertEquals(UjinApiException.Kind.UNAVAILABLE, error.kind());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
