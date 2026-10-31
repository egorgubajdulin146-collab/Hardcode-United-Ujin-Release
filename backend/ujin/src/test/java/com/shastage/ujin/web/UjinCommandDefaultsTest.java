package com.shastage.ujin.web;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.json.JsonMapper;

import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Флаг доставки команд выключен по умолчанию: ничего не отправляется наружу. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UjinCommandDefaultsTest {
    private static final HttpServer upstream;

    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstream.createContext(
                    "/api/auth/authenticate/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        byte[] body =
                                "{\"error\":0,\"data\":{\"user\":{\"token\":\"private-token\"}}}"
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(body);
                        }
                    });
            upstream.start();
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "ujin.api.base-url",
                () -> "http://127.0.0.1:" + upstream.getAddress().getPort() + "/api");
        registry.add("ujin.api.app", () -> "test-app");
    }

    @AfterAll
    static void stop() {
        upstream.stop(0);
    }

    @LocalServerPort int port;

    private HttpResponse<String> request(
            HttpClient browser, String path, String method, String body, String csrf)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf != null) builder.header("X-XSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        return browser.send(
                builder.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void commandDeliveryIsDisabledByDefault() throws Exception {
        var browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String csrf = request(browser, "/api/auth/csrf", "GET", null, null).body();
        assertEquals(
                204,
                request(
                                browser,
                                "/api/auth/login",
                                "POST",
                                "{\"login\":\"user\",\"password\":\"private-password\"}",
                                csrf)
                        .statusCode());
        var status = request(browser, "/api/v1/integrations/ujin/status", "GET", null, null);
        var root = JsonMapper.builder().build().readTree(status.body());
        assertEquals("BLOCKED", root.path("downlink").path("status").asString());
        assertFalse(root.path("downlink").path("reason").asString().isBlank());

        var send =
                request(
                        browser,
                        "/api/v1/devices/" + java.util.UUID.randomUUID() + "/commands",
                        "POST",
                        "{\"commandId\":\"" + java.util.UUID.randomUUID()
                                + "\",\"signalKey\":\"lamp\",\"value\":1,\"executionMode\":\"UJIN\"}",
                        csrf);
        // Флаг проверяется до обращения к каталогу и сети.
        assertEquals(503, send.statusCode(), send.body());
        assertTrue(send.body().contains("COMMAND_DELIVERY_DISABLED"), send.body());
    }
}
