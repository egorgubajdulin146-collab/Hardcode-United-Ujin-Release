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
    private final AtomicReference<String> rejectedAuth = new AtomicReference<>();
    private final AtomicReference<String> acceptedAuth = new AtomicReference<>("{\"error\":0,\"data\":{\"user\":{\"token\":\"session-token\"}}}");
    private final java.util.List<String> authRequests = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/authenticate/", exchange -> {
            authBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authRequests.add(authBody.get());
            respond(exchange, authBody.get().contains("&app=") && rejectedAuth.get() != null
                    ? rejectedAuth.get() : acceptedAuth.get());
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
    void residentRetriesWithoutEmployeeApp() {
        rejectedAuth.set("{\"error\":1,\"data\":{}}");
        assertEquals("session-token", client.authenticate("resident", "pass word", "staff-app"));
        assertEquals(java.util.List.of("login=resident&password=pass+word&app=staff-app",
                "login=resident&password=pass+word"), authRequests);
    }

    @Test
    void residentCanAuthenticateWithoutConfiguredApp() {
        assertEquals("session-token", client.authenticate("resident", "password", ""));
        assertEquals(java.util.List.of("login=resident&password=password"), authRequests);
    }

    @Test
    void invalidCredentialsStayUnauthorized() {
        acceptedAuth.set("{\"error\":1,\"data\":{}}");
        assertEquals(UjinApiException.Kind.UNAUTHORIZED, assertThrows(UjinApiException.class,
                () -> client.authenticate("resident", "wrong", "staff-app")).kind());
        assertEquals(2, authRequests.size());
    }

    @Test
    void malformedUpstreamDoesNotTriggerAnotherLogin() {
        acceptedAuth.set("not json");
        assertEquals(UjinApiException.Kind.BAD_RESPONSE, assertThrows(UjinApiException.class,
                () -> client.authenticate("resident", "password", "staff-app")).kind());
        assertEquals(1, authRequests.size());
    }

    @Test
    void missingSignalIsNotReportedAsSuccess() {
        assertEquals(UjinApiException.Kind.SIGNAL_NOT_FOUND, assertThrows(UjinApiException.class,
                () -> client.readSignal("session-token", "device-1", "leak")).kind());
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
