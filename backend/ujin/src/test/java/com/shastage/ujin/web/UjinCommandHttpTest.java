package com.shastage.ujin.web;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UjinCommandHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SNAPSHOT =
            """
            {"error":0,"data":{"devices":[{"id":"sn-target","signals":[
              {"name":"lamp","value":0}]}]}}""";
    private static final String ACCEPTED = "{\"error\":0,\"data\":{}}";

    private static final HttpServer upstream;
    private static volatile String sendBody = ACCEPTED;
    private static volatile String infoBody = "{\"error\":0,\"data\":{\"signals\":[]}}";
    private static volatile String authBody =
            "{\"error\":0,\"data\":{\"user\":{\"token\":\"private-token\"}}}";
    private static volatile String sendMode = "normal";
    private static volatile String infoMode = "normal";
    private static final AtomicInteger sendCalls = new AtomicInteger();
    private static final CopyOnWriteArrayList<String> sendQueries = new CopyOnWriteArrayList<>();
    private static final AtomicInteger infoCalls = new AtomicInteger();

    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstream.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            upstream.createContext(
                    "/api/auth/authenticate/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        respond(exchange, authBody);
                    });
            upstream.createContext(
                    "/api/apartment/get-device-signals/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        respond(exchange, SNAPSHOT);
                    });
            upstream.createContext(
                    "/api/apartment/send-signal/",
                    exchange -> {
                        String form =
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8);
                        sendCalls.incrementAndGet();
                        sendQueries.add(form);
                        if (sendMode.equals("disconnect")) {
                            exchange.close();
                            return;
                        }
                        respond(exchange, sendBody);
                    });
            upstream.createContext(
                    "/api/bmsdevice/get-device-info/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        infoCalls.incrementAndGet();
                        if (infoMode.equals("disconnect")) {
                            exchange.close();
                            return;
                        }
                        respond(exchange, infoBody);
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
        registry.add("ujin.commands.enabled", () -> "true");
        registry.add("ujin.commands.task-fetch-enabled", () -> "true");
        registry.add("ujin.commands.observation-window-seconds", () -> "60");
    }

    @AfterAll
    static void stop() {
        upstream.stop(0);
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

    @LocalServerPort int port;

    @org.springframework.beans.factory.annotation.Autowired
    com.shastage.ujin.domain.service.CommandProperties commandsConfig;

    private HttpClient userA;
    private HttpClient userB;

    @BeforeEach
    void reset() {
        userA = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        userB = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        sendBody = ACCEPTED;
        infoBody = "{\"error\":0,\"data\":{\"signals\":[]}}";
        authBody = "{\"error\":0,\"data\":{\"user\":{\"token\":\"private-token\"}}}";
        sendMode = "normal";
        infoMode = "normal";
        sendCalls.set(0);
        sendQueries.clear();
        infoCalls.set(0);
    }

    private HttpResponse<String> request(
            HttpClient user, String path, String method, String body, String csrf)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf != null) builder.header("X-XSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        return user.send(
                builder.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String csrf(HttpClient user) throws Exception {
        var response = request(user, "/api/auth/csrf", "GET", null, null);
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private void login(HttpClient user, String name) throws Exception {
        var response =
                request(
                        user,
                        "/api/auth/login",
                        "POST",
                        "{\"login\":\"" + name + "\",\"password\":\"private-password\"}",
                        csrf(user));
        assertEquals(204, response.statusCode());
    }

    private String createDevice(HttpClient user, String title) throws Exception {
        String body =
                """
                {"title":"%s","family":"light","signals":[
                  {"key":"lamp","title":"Lamp","group":"capability","management":"rw","valueType":"NUMBER",
                   "control":"switch","min":0,"max":1},
                  {"key":"extra","title":"Extra","group":"capability","management":"rw","valueType":"NUMBER",
                   "control":"switch","min":0,"max":1},
                  {"key":"mode","title":"Mode","group":"property","management":"r","valueType":"NUMBER",
                   "control":"input"}
                ],"events":[]}"""
                        .formatted(title);
        var response = request(user, "/api/v1/devices", "POST", body, csrf(user));
        assertEquals(201, response.statusCode(), response.body());
        return JSON.readTree(response.body()).path("id").asString();
    }

    private void bind(HttpClient user, String deviceId) throws Exception {
        String body =
                """
                {"serialNumber":"sn-target","signalMappings":[
                  {"signalKey":"lamp","platformName":"lamp"},{"signalKey":"mode","platformName":"mode"}]}""";
        var response =
                request(
                        user,
                        "/api/v1/devices/" + deviceId + "/platform-binding",
                        "PUT",
                        body,
                        csrf(user));
        assertEquals(201, response.statusCode(), response.body());
    }

    private String sendCommand(HttpClient user, String deviceId, String value) throws Exception {
        return sendCommand(user, deviceId, UUID.randomUUID().toString(), value, "lamp");
    }

    private String sendCommand(
            HttpClient user, String deviceId, String commandId, String value) throws Exception {
        return sendCommand(user, deviceId, commandId, value, "lamp");
    }

    private String sendCommand(
            HttpClient user, String deviceId, String commandId, String value, String key)
            throws Exception {
        String body =
                "{\"commandId\":\""
                        + commandId
                        + "\",\"signalKey\":\""
                        + key
                        + "\",\"value\":"
                        + value
                        + ",\"executionMode\":\"UJIN\"}";
        return request(user, "/api/v1/devices/" + deviceId + "/commands", "POST", body, csrf(user))
                .body();
    }

    private HttpResponse<String> check(HttpClient user, String commandId) throws Exception {
        return request(user, "/api/v1/commands/" + commandId + "/check", "POST", null, csrf(user));
    }

    private void assertSafe(HttpResponse<String> response) {
        String exposed = response.body() + response.headers();
        assertFalse(exposed.contains("private-token"), exposed);
        assertFalse(exposed.contains("private-password"), exposed);
    }

    private String prepareBoundDevice(HttpClient user, String title) throws Exception {
        String device = createDevice(user, title);
        bind(user, device);
        return device;
    }

    @Test
    void acceptanceIsNotExecution() throws Exception {
        login(userA, "userA");
        String device = prepareBoundDevice(userA, "Leak A");
        var send = request(
                userA,
                "/api/v1/devices/" + device + "/commands",
                "POST",
                "{\"commandId\":\"" + UUID.randomUUID()
                        + "\",\"signalKey\":\"lamp\",\"value\":1,\"executionMode\":\"UJIN\"}",
                csrf(userA));
        assertEquals(202, send.statusCode(), send.body());
        var receipt = JSON.readTree(send.body());
        assertEquals("PENDING", receipt.path("status").asString());
        assertEquals("ACCEPTED_AWAITING_APPLY", receipt.path("reason").asString());
        assertNotEquals("CONFIRMED", receipt.path("status").asString());
        assertEquals(1, sendCalls.get());
        String form = sendQueries.getFirst();
        assertTrue(form.contains("serialnumber=sn-target"), form);
        assertTrue(form.contains("signal=lamp"), form);
        assertTrue(form.contains("value=1"), form);
        assertFalse(form.contains("private-password"));

        // Проверка без применения задачи не превращает приём в исполнение.
        var checked = check(userA, receipt.path("commandId").asString());
        assertEquals("PENDING", JSON.readTree(checked.body()).path("status").asString());
        assertSafe(checked);
    }

    @Test
    void explicitDenialFailsUnknownStaysUnknown() throws Exception {
        login(userA, "userA1");
        String device = prepareBoundDevice(userA, "Leak B");
        sendBody = "{\"error\":1,\"error_code\":\"Access denied\"}";
        var denied = JSON.readTree(sendCommand(userA, device, "0.5"));
        assertEquals("FAILED", denied.path("status").asString());
        assertEquals("UJIN_ACCESS_DENIED", denied.path("reason").asString());

        sendBody = "{\"error\":2,\"error_code\":\"unknown\"}";
        var refused = JSON.readTree(sendCommand(userA, device, "0.2"));
        assertEquals("FAILED", refused.path("status").asString());
        assertEquals("UJIN_PLATFORM_ERROR", refused.path("reason").asString());

        sendBody = "not json";
        var unknown = JSON.readTree(sendCommand(userA, device, "0.7"));
        assertEquals("UNKNOWN", unknown.path("status").asString());
        assertEquals("UJIN_BAD_RESPONSE", unknown.path("reason").asString());
    }

    @Test
    void timeoutAfterSendIsUnknownWithoutResend() throws Exception {
        login(userA, "userA2");
        String device = prepareBoundDevice(userA, "Leak C");
        sendMode = "disconnect";
        String commandId = UUID.randomUUID().toString();
        var lost = JSON.readTree(sendCommand(userA, device, commandId, "1"));
        assertEquals("UNKNOWN", lost.path("status").asString());
        assertEquals("UJIN_UNAVAILABLE", lost.path("reason").asString());
        assertEquals(1, sendCalls.get());

        var replay = JSON.readTree(sendCommand(userA, device, commandId, "1"));
        assertEquals("UNKNOWN", replay.path("status").asString());
        assertEquals(1, sendCalls.get(), "replay must not resend");
    }

    @Test
    void replaySamePayloadReturnsReceiptWithoutSend() throws Exception {
        login(userA, "userA3");
        String device = prepareBoundDevice(userA, "Leak D");
        String commandId = UUID.randomUUID().toString();
        var first = JSON.readTree(sendCommand(userA, device, commandId, "1"));
        var second = JSON.readTree(sendCommand(userA, device, commandId, "1"));
        assertEquals(first.path("commandId").asString(), second.path("commandId").asString());
        assertEquals(first.path("status").asString(), second.path("status").asString());
        assertEquals(1, sendCalls.get());
    }

    @Test
    void conflictingPayloadIsRejected() throws Exception {
        login(userA, "userA4");
        String device = prepareBoundDevice(userA, "Leak E");
        String commandId = UUID.randomUUID().toString();
        sendCommand(userA, device, commandId, "1");
        var response =
                request(
                        userA,
                        "/api/v1/devices/" + device + "/commands",
                        "POST",
                        "{\"commandId\":\"" + commandId
                                + "\",\"signalKey\":\"lamp\",\"value\":0,\"executionMode\":\"UJIN\"}",
                        csrf(userA));
        assertEquals(409, response.statusCode());
        assertTrue(response.body().contains("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void foreignUserCannotSeeOrTouchCommand() throws Exception {
        login(userA, "userA5");
        login(userB, "userB5");
        String device = prepareBoundDevice(userA, "Leak F");
        String commandId = UUID.randomUUID().toString();
        sendCommand(userA, device, commandId, "1");
        for (String path :
                java.util.List.of(
                        "/api/v1/commands/" + commandId,
                        "/api/v1/devices/" + device + "/state?mode=LOCAL")) {
            var foreign = request(userB, path, "GET", null, null);
            assertNotEquals(200, foreign.statusCode(), path);
            assertFalse(foreign.body().contains(commandId) && path.contains("commands"), foreign.body());
        }
        var foreignCheck = check(userB, commandId);
        assertEquals(404, foreignCheck.statusCode());
        var foreignResolve =
                request(
                        userB,
                        "/api/v1/commands/" + commandId + "/resolution",
                        "POST",
                        "{\"note\":\"чужая отметка\"}",
                        csrf(userB));
        assertEquals(404, foreignResolve.statusCode());
        var foreignReplay = request(
                userB,
                "/api/v1/devices/" + device + "/commands",
                "POST",
                "{\"commandId\":\"" + commandId
                        + "\",\"signalKey\":\"lamp\",\"value\":1,\"executionMode\":\"UJIN\"}",
                csrf(userB));
        assertEquals(404, foreignReplay.statusCode());
        assertSafe(foreignCheck);
    }

    @Test
    void taskApplyConfirmsCommandAndUpdatesEmulator() throws Exception {
        login(userA, "userA6");
        String device = prepareBoundDevice(userA, "Leak G");
        String commandId = UUID.randomUUID().toString();
        var send = JSON.readTree(sendCommand(userA, device, commandId, "1"));
        assertEquals("PENDING", send.path("status").asString());

        infoBody =
                """
                {"error":0,"data":{"signals":[
                  {"name":"lamp","task":1,"signal_task_dt":"t-1","state":{"value":0}}]}}""";
        var checked = check(userA, commandId);
        var receipt = JSON.readTree(checked.body());
        assertEquals("CONFIRMED", receipt.path("status").asString(), checked.body());
        assertEquals("TASK_APPLIED", receipt.path("reason").asString());

        var local = request(userA, "/api/v1/devices/" + device + "/state?mode=LOCAL", "GET", null, null);
        var values = JSON.readTree(local.body()).path("values");
        boolean lampOne = false;
        for (int i = 0; i < values.size(); i++) {
            if ("lamp".equals(values.get(i).path("signalKey").asString()))
                lampOne = values.get(i).path("value").asDouble() == 1;
        }
        assertTrue(lampOne, local.body());
    }

    @Test
    void staleEqualValueDoesNotConfirmCommand() throws Exception {
        login(userA, "userA7");
        String device = prepareBoundDevice(userA, "Leak H");
        // Локальное состояние уже равно желаемому значению до команды.
        var localSet = request(
                userA,
                "/api/v1/devices/" + device + "/commands",
                "POST",
                "{\"commandId\":\"" + UUID.randomUUID()
                        + "\",\"signalKey\":\"lamp\",\"value\":1,\"executionMode\":\"LOCAL\"}",
                csrf(userA));
        assertEquals(202, localSet.statusCode(), localSet.body());

        String commandId = UUID.randomUUID().toString();
        var send = JSON.readTree(sendCommand(userA, device, commandId, "1"));
        assertEquals("PENDING", send.path("status").asString());

        // Задача отсутствует: совпавшее старое значение не доказывает исполнение.
        var checked = JSON.readTree(check(userA, commandId).body());
        assertEquals("PENDING", checked.path("status").asString(), checked.toString());

        // Задача с текущим значением тоже не новая — исполнение не подтверждается.
        infoBody =
                """
                {"error":0,"data":{"signals":[
                  {"name":"lamp","task":1,"signal_task_dt":"t-x","state":{"value":1}}]}}""";
        var second = JSON.readTree(check(userA, commandId).body());
        assertEquals("PENDING", second.path("status").asString(), second.toString());
    }

    @Test
    void duplicateTaskDtIsAppliedOnce() throws Exception {
        login(userA, "userA8");
        String device = prepareBoundDevice(userA, "Leak I");
        String commandId = UUID.randomUUID().toString();
        sendCommand(userA, device, commandId, "1");
        infoBody =
                """
                {"error":0,"data":{"signals":[
                  {"name":"lamp","task":1,"signal_task_dt":"t-dup","state":{"value":0}}]}}""";
        assertEquals("CONFIRMED", JSON.readTree(check(userA, commandId).body()).path("status").asString());

        // Новая команда с тем же task_dt не исполняется повторно.
        String second = UUID.randomUUID().toString();
        var send = JSON.readTree(sendCommand(userA, device, second, "0"));
        assertEquals("PENDING", send.path("status").asString());
        infoBody =
                """
                {"error":0,"data":{"signals":[
                  {"name":"lamp","task":1,"signal_task_dt":"t-dup","state":{"value":0}}]}}""";
        var checked = JSON.readTree(check(userA, second).body());
        assertNotEquals("CONFIRMED", checked.path("status").asString(), checked.toString());
    }

    @Test
    void taskSourceDenialIsReportedNotBypassed() throws Exception {
        login(userA, "userA9");
        String device = prepareBoundDevice(userA, "Leak J");
        String commandId = UUID.randomUUID().toString();
        sendCommand(userA, device, commandId, "1");
        infoBody = "{\"error\":1,\"error_code\":\"Access denied\"}";
        int before = infoCalls.get();
        var checked = JSON.readTree(check(userA, commandId).body());
        assertNotEquals("CONFIRMED", checked.path("status").asString(), checked.toString());
        assertEquals(before + 1, infoCalls.get());
        // Внутри окна команда остаётся PENDING; отказ источника не лечится ролями.
        assertEquals("PENDING", checked.path("status").asString(), checked.toString());
    }

    @Test
    void observationWindowExpiryIsUnknownWithReason() throws Exception {
        login(userA, "userA10");
        String device = prepareBoundDevice(userA, "Leak K");
        String commandId = UUID.randomUUID().toString();
        sendCommand(userA, device, commandId, "1");
        long previous = commandsConfig.getObservationWindowSeconds();
        commandsConfig.setObservationWindowSeconds(0);
        try {
            var checked = JSON.readTree(check(userA, commandId).body());
            assertEquals("UNKNOWN", checked.path("status").asString(), checked.toString());
            assertEquals("OBSERVATION_WINDOW_EXPIRED", checked.path("reason").asString());

            // Незавершённая команда держит следующую; после отметки — новая допустима.
            assertEquals(
                    200,
                    request(
                                    userA,
                                    "/api/v1/commands/" + commandId + "/resolution",
                                    "POST",
                                    "{\"note\":\"окно истекло, повтор не отправляли\"}",
                                    csrf(userA))
                            .statusCode());
            String second = UUID.randomUUID().toString();
            sendCommand(userA, device, second, "0");
            infoBody = "{\"error\":1,\"error_code\":\"Access denied\"}";
            var blocked = JSON.readTree(check(userA, second).body());
            assertEquals("UNKNOWN", blocked.path("status").asString(), blocked.toString());
            assertEquals("TASK_SOURCE_BLOCKED", blocked.path("reason").asString());
        } finally {
            commandsConfig.setObservationWindowSeconds(previous);
        }
    }

    @Test
    void resolutionAcknowledgesUnknownWithoutConfirming() throws Exception {
        login(userA, "userA11");
        String device = prepareBoundDevice(userA, "Leak L");
        String commandId = UUID.randomUUID().toString();
        sendBody = "not json";
        var unknown = JSON.readTree(sendCommand(userA, device, commandId, "1"));
        assertEquals("UNKNOWN", unknown.path("status").asString());

        var resolved = request(
                userA,
                "/api/v1/commands/" + commandId + "/resolution",
                "POST",
                "{\"note\":\"сеть пропала, повтор не отправляли\"}",
                csrf(userA));
        assertEquals(200, resolved.statusCode(), resolved.body());
        var receipt = JSON.readTree(resolved.body());
        assertEquals("UNKNOWN", receipt.path("status").asString());
        assertEquals("ACKNOWLEDGED_UNKNOWN", receipt.path("resolution").path("kind").asString());
        assertNotEquals("CONFIRMED", receipt.path("status").asString());

        var same = request(
                userA,
                "/api/v1/commands/" + commandId + "/resolution",
                "POST",
                "{\"note\":\"сеть пропала, повтор не отправляли\"}",
                csrf(userA));
        assertEquals(200, same.statusCode());

        var different = request(
                userA,
                "/api/v1/commands/" + commandId + "/resolution",
                "POST",
                "{\"note\":\"другая заметка\"}",
                csrf(userA));
        assertEquals(409, different.statusCode());
    }

    @Test
    void resolutionIsRejectedForNonUnknown() throws Exception {
        login(userA, "userA12");
        String device = prepareBoundDevice(userA, "Leak M");
        String commandId = UUID.randomUUID().toString();
        sendCommand(userA, device, commandId, "1");
        var response = request(
                userA,
                "/api/v1/commands/" + commandId + "/resolution",
                "POST",
                "{\"note\":\"пока не проверили\"}",
                csrf(userA));
        assertEquals(409, response.statusCode());
    }

    @Test
    void deletionHoldIsReleasedByAcknowledgedUnknown() throws Exception {
        login(userA, "userA13");
        String device = prepareBoundDevice(userA, "Leak N");
        String commandId = UUID.randomUUID().toString();
        sendBody = "not json";
        sendCommand(userA, device, commandId, "1");

        var dependencies =
                request(userA, "/api/v1/devices/" + device + "/dependencies", "GET", null, null);
        assertTrue(dependencies.body().contains(commandId), dependencies.body());

        var blocked = request(
                userA,
                "/api/v1/devices/" + device + "?expectedVersion=2",
                "DELETE",
                null,
                csrf(userA));
        assertEquals(409, blocked.statusCode(), blocked.body());

        assertEquals(
                200,
                request(
                                userA,
                                "/api/v1/commands/" + commandId + "/resolution",
                                "POST",
                                "{\"note\":\"неизвестно, повтор не отправляли\"}",
                                csrf(userA))
                        .statusCode());

        var after = request(
                userA,
                "/api/v1/devices/" + device + "?expectedVersion=2",
                "DELETE",
                null,
                csrf(userA));
        assertEquals(204, after.statusCode(), after.body());
    }

    @Test
    void readOnlyOutOfRangeAndUnmappedSignalsAreRejected() throws Exception {
        login(userA, "userA14");
        String device = createDevice(userA, "Leak O");
        // Без привязки — устройства нет у платформы для этой команды.
        var unbound = request(
                userA,
                "/api/v1/devices/" + device + "/commands",
                "POST",
                "{\"commandId\":\"" + UUID.randomUUID()
                        + "\",\"signalKey\":\"lamp\",\"value\":1,\"executionMode\":\"UJIN\"}",
                csrf(userA));
        assertEquals(404, unbound.statusCode());
        assertTrue(unbound.body().contains("PLATFORM_NOT_BOUND"), unbound.body());
        bind(userA, device);
        // Сигнал без mapping в привязке.
        var unmapped = request(
                userA,
                "/api/v1/devices/" + device + "/commands",
                "POST",
                "{\"commandId\":\"" + UUID.randomUUID()
                        + "\",\"signalKey\":\"extra\",\"value\":1,\"executionMode\":\"UJIN\"}",
                csrf(userA));
        assertEquals(422, unmapped.statusCode());
        assertTrue(unmapped.body().contains("SIGNAL_NOT_BOUND"), unmapped.body());
        var readOnly = request(
                userA,
                "/api/v1/devices/" + device + "/commands",
                "POST",
                "{\"commandId\":\"" + UUID.randomUUID()
                        + "\",\"signalKey\":\"mode\",\"value\":1,\"executionMode\":\"UJIN\"}",
                csrf(userA));
        assertEquals(422, readOnly.statusCode());
        assertTrue(readOnly.body().contains("SIGNAL_READ_ONLY"));

        var outOfRange = request(
                userA,
                "/api/v1/devices/" + device + "/commands",
                "POST",
                "{\"commandId\":\"" + UUID.randomUUID()
                        + "\",\"signalKey\":\"lamp\",\"value\":5,\"executionMode\":\"UJIN\"}",
                csrf(userA));
        assertEquals(422, outOfRange.statusCode());
        assertEquals(0, sendCalls.get(), "validation must happen before dispatch");
    }
    @Test
    void taskAlreadyPresentBeforeSendDoesNotConfirmNewCommand() throws Exception {
        login(userA, "review-stale-task");
        String device = prepareBoundDevice(userA, "Review baseline");
        infoBody = """
                {"error":0,"data":{"signals":[{"name":"lamp","task":1,"signal_task_dt":"old-task","state":{"value":0}}]}}
                """;
        String command = UUID.randomUUID().toString();
        sendCommand(userA, device, command, "1");
        var receipt = JSON.readTree(check(userA, command).body());
        assertNotEquals("CONFIRMED", receipt.path("status").asString(), receipt.toString());
    }

}
