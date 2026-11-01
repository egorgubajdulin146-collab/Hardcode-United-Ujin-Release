package com.shastage.ujin.web;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UjinScenarioPollHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static volatile String snapshotBody =
            """
            {"error":0,"data":{"devices":[
              {"id":"sn-src","signals":[{"name":"leak","value":0}]},
              {"id":"sn-tgt","signals":[{"name":"lamp","value":0}]}]}}""";
    private static volatile String sendBody = "{\"error\":0,\"data\":{}}";
    private static volatile String infoBody = "{\"error\":0,\"data\":{\"signals\":[]}}";
    private static volatile String snapshotMode = "normal";
    private static final AtomicInteger sendCalls = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicBoolean reviewCaptureNext = new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile java.util.concurrent.CountDownLatch reviewReadEntered;
    private static volatile java.util.concurrent.CountDownLatch reviewReadRelease;

    private static final HttpServer upstream;

    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstream.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            upstream.createContext(
                    "/api/auth/authenticate/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        respond(exchange, "{\"error\":0,\"data\":{\"user\":{\"token\":\"private-token\"}}}");
                    });
            upstream.createContext(
                    "/api/apartment/get-device-signals/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        String capturedBody = snapshotBody;
                        if (reviewCaptureNext.getAndSet(false)) {
                            reviewReadEntered.countDown();
                            try { reviewReadRelease.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                        }
                        if (snapshotMode.equals("slow")) {
                            try {
                                Thread.sleep(600);
                            } catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        if (snapshotMode.equals("disconnect")) {
                            exchange.close();
                            return;
                        }
                        respond(exchange, capturedBody);
                    });
            upstream.createContext(
                    "/api/apartment/send-signal/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        sendCalls.incrementAndGet();
                        respond(exchange, sendBody);
                    });
            upstream.createContext(
                    "/api/bmsdevice/get-device-info/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
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
        registry.add("ujin.scenarios.observation-enabled", () -> "true");
        registry.add("ujin.commands.enabled", () -> "true");
        registry.add("ujin.commands.task-fetch-enabled", () -> "true");
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

    @Autowired
    com.shastage.ujin.domain.service.CommandProperties commandsConfig;

    private HttpClient userA;
    private HttpClient userB;

    @BeforeEach
    void reset() {
        userA = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        userB = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        snapshotBody =
                """
                {"error":0,"data":{"devices":[
                  {"id":"sn-src","signals":[{"name":"leak","value":0}]},
                  {"id":"sn-tgt","signals":[{"name":"lamp","value":0}]}]}}""";
        sendBody = "{\"error\":0,\"data\":{}}";
        infoBody = "{\"error\":0,\"data\":{\"signals\":[]}}";
        snapshotMode = "normal";
        sendCalls.set(0);
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
        assertEquals(
                204,
                request(
                                user,
                                "/api/auth/login",
                                "POST",
                                "{\"login\":\"" + name + "\",\"password\":\"private-password\"}",
                                csrf(user))
                        .statusCode());
    }

    private String createDevice(HttpClient user, String title, String body) throws Exception {
        var response = request(user, "/api/v1/devices", "POST", body, csrf(user));
        assertEquals(201, response.statusCode(), response.body());
        return JSON.readTree(response.body()).path("id").asString();
    }

    private String sourceDevice(HttpClient user, String title) throws Exception {
        return createDevice(
                user,
                title,
                """
                {"title":"%s","family":"leak","signals":[
                  {"key":"leak","title":"Leak","group":"property","management":"r","valueType":"NUMBER",
                   "control":"list","min":0,"max":1}
                ],"events":[{"key":"leak_changed","title":"Leak changed","signalKey":"leak","emission":"CHANGE"}]}"""
                        .formatted(title));
    }

    private String targetDevice(HttpClient user, String title) throws Exception {
        return createDevice(
                user,
                title,
                """
                {"title":"%s","family":"light","signals":[
                  {"key":"lamp","title":"Lamp","group":"capability","management":"rw","valueType":"NUMBER",
                   "control":"switch","min":0,"max":1}
                ],"events":[]}"""
                        .formatted(title));
    }

    private void bind(HttpClient user, String deviceId, String sn, String key, String platformName)
            throws Exception {
        var response =
                request(
                        user,
                        "/api/v1/devices/" + deviceId + "/platform-binding",
                        "PUT",
                        "{\"serialNumber\":\""
                                + sn
                                + "\",\"signalMappings\":[{\"signalKey\":\""
                                + key
                                + "\",\"platformName\":\""
                                + platformName
                                + "\"}]}",
                        csrf(user));
        assertEquals(201, response.statusCode(), response.body());
    }

    private String createScenario(HttpClient user, String title, String source, String target, boolean enabled)
            throws Exception {
        String body =
                """
                {"title":"%s","enabled":%s,"executionMode":"UJIN",
                 "trigger":{"deviceId":"%s","signalKey":"leak","operator":"EQ","value":1,"eventKey":"leak_changed"},
                 "action":{"deviceId":"%s","signalKey":"lamp","value":1}}"""
                        .formatted(title, enabled, source, target);
        var response = request(user, "/api/v1/scenarios", "POST", body, csrf(user));
        assertEquals(201, response.statusCode(), response.body());
        return JSON.readTree(response.body()).path("id").asString();
    }

    private HttpResponse<String> poll(HttpClient user, String scenarioId, String pollId) throws Exception {
        return request(
                user,
                "/api/v1/scenarios/" + scenarioId + "/poll",
                "POST",
                "{\"pollId\":\"" + pollId + "\"}",
                csrf(user));
    }

    private void setValue(String sn, String name, String valueJson) {
        snapshotBody =
                """
                {"error":0,"data":{"devices":[
                  {"id":"sn-src","signals":[{"name":"leak","value":%s}]},
                  {"id":"sn-tgt","signals":[{"name":"lamp","value":0}]}]}}"""
                        .formatted(valueJson);
    }

    private String[] setupScenario(HttpClient user, String tag) throws Exception {
        String source = sourceDevice(user, "Source " + tag);
        String target = targetDevice(user, "Target " + tag);
        bind(user, source, "sn-src", "leak", "leak");
        bind(user, target, "sn-tgt", "lamp", "lamp");
        String scenario = createScenario(user, "Rule " + tag, source, target, true);
        return new String[] {scenario, source, target};
    }

    @Test
    void firstReadBaselineThenChangeRunsOnce() throws Exception {
        login(userA, "userA");
        String[] setup = setupScenario(userA, "A");
        String scenario = setup[0];

        var baseline = JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body());
        assertEquals("BASELINE_STORED", baseline.path("status").asString());
        assertFalse(baseline.has("runId"));
        assertEquals(0, baseline.path("observedValue").asDouble());

        setValue("sn-src", "leak", "1");
        var changed = JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body());
        assertEquals("RUN_CREATED", changed.path("status").asString());
        assertTrue(changed.has("runId"));
        assertEquals(1, changed.path("observedValue").asDouble());
        assertEquals(1, sendCalls.get());

        var run = JSON.readTree(
                request(userA, "/api/v1/runs/" + changed.path("runId").asString(), "GET", null, null).body());
        assertEquals("UJIN", run.path("executionMode").asString());
        assertEquals("PENDING", run.path("status").asString());
        assertTrue(run.has("commandId") && !run.path("commandId").asString().isBlank());
        assertNotEquals("SUCCEEDED", run.path("status").asString());

        // Тот же snapshot не повторяет действие.
        var same = JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body());
        assertEquals("NO_CHANGE", same.path("status").asString());
        assertFalse(same.has("runId"));
        assertEquals(1, sendCalls.get());
    }

    @Test
    void conditionFalseAndDisabledSkipWithoutCommand() throws Exception {
        login(userA, "userA1");
        String[] setup = setupScenario(userA, "B");
        String scenario = setup[0];
        // baseline=1 (условие EQ 1 истинно при чтении, но baseline не запускает правило).
        snapshotBody =
                """
                {"error":0,"data":{"devices":[
                  {"id":"sn-src","signals":[{"name":"leak","value":1}]},
                  {"id":"sn-tgt","signals":[{"name":"lamp","value":0}]}]}}""";
        assertEquals(
                "BASELINE_STORED",
                JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body())
                        .path("status")
                        .asString());
        // Изменение 1→0: условие EQ 1 ложно — SKIPPED без команды.
        setValue("sn-src", "leak", "0");
        var falseCondition = JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body());
        assertEquals("RUN_CREATED", falseCondition.path("status").asString());
        var falseRun = JSON.readTree(
                request(userA, "/api/v1/runs/" + falseCondition.path("runId").asString(), "GET", null, null).body());
        assertEquals("SKIPPED", falseRun.path("status").asString());
        assertEquals("CONDITION_FALSE", falseRun.path("reason").asString());
        assertEquals(0, sendCalls.get(), "false condition must not dispatch");

        String disabled = createScenario(userA, "Disabled B", setup[1], setup[2], false);
        snapshotBody =
                """
                {"error":0,"data":{"devices":[
                  {"id":"sn-src","signals":[{"name":"leak","value":0}]},
                  {"id":"sn-tgt","signals":[{"name":"lamp","value":0}]}]}}""";
        JSON.readTree(poll(userA, disabled, UUID.randomUUID().toString()).body());
        snapshotBody =
                """
                {"error":0,"data":{"devices":[
                  {"id":"sn-src","signals":[{"name":"leak","value":1}]},
                  {"id":"sn-tgt","signals":[{"name":"lamp","value":0}]}]}}""";
        var skipped = JSON.readTree(poll(userA, disabled, UUID.randomUUID().toString()).body());
        assertEquals("RUN_CREATED", skipped.path("status").asString());
        var run = JSON.readTree(
                request(userA, "/api/v1/runs/" + skipped.path("runId").asString(), "GET", null, null).body());
        assertEquals("SKIPPED", run.path("status").asString());
        assertEquals("SCENARIO_DISABLED", run.path("reason").asString());
        assertEquals(0, sendCalls.get(), "disabled scenario must not dispatch");
    }

    @Test
    void repeatedPollIdReturnsSameResultAndConflictsOnOtherScenario() throws Exception {
        login(userA, "userA2");
        String[] setup = setupScenario(userA, "C");
        String scenario = setup[0];
        String pollId = UUID.randomUUID().toString();
        var first = JSON.readTree(poll(userA, scenario, pollId).body());
        assertEquals("BASELINE_STORED", first.path("status").asString());
        var replay = JSON.readTree(poll(userA, scenario, pollId).body());
        assertEquals(first.toString(), replay.toString());

        String other = createScenario(userA, "Other C", setup[1], setup[2], true);
        var conflict = poll(userA, other, pollId);
        assertEquals(409, conflict.statusCode(), conflict.body());
        assertTrue(conflict.body().contains("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void concurrentTabsCreateSingleCommand() throws Exception {
        login(userA, "userA3");
        String[] setup = setupScenario(userA, "D");
        String scenario = setup[0];
        poll(userA, scenario, UUID.randomUUID().toString());
        setValue("sn-src", "leak", "1");
        var tab1 =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return poll(userA, scenario, UUID.randomUUID().toString());
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                });
        var tab2 =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return poll(userA, scenario, UUID.randomUUID().toString());
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                });
        var results = new java.util.ArrayList<String>();
        results.add(JSON.readTree(tab1.get().body()).path("status").asString());
        results.add(JSON.readTree(tab2.get().body()).path("status").asString());
        assertEquals(1, results.stream().filter("RUN_CREATED"::equals).count(), results.toString());
        assertEquals(1, results.stream().filter("NO_CHANGE"::equals).count(), results.toString());
        assertEquals(1, sendCalls.get(), "two tabs must not create two commands");
    }

    @Test
    void scenarioChangedDuringReadIsRejected() throws Exception {
        login(userA, "userA4");
        String[] setup = setupScenario(userA, "E");
        String scenario = setup[0];
        poll(userA, scenario, UUID.randomUUID().toString());
        setValue("sn-src", "leak", "1");
        snapshotMode = "slow";
        var inFlight =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return poll(userA, scenario, UUID.randomUUID().toString());
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                });
        Thread.sleep(150);
        var update =
                request(
                        userA,
                        "/api/v1/scenarios/" + scenario,
                        "PUT",
                        """
                        {"title":"Rule E renamed","enabled":true,"executionMode":"UJIN",
                         "trigger":{"deviceId":"%s","signalKey":"leak","operator":"EQ","value":1,"eventKey":"leak_changed"},
                         "action":{"deviceId":"%s","signalKey":"lamp","value":1},
                         "expectedVersion":1}"""
                                .formatted(setup[1], setup[2]),
                        csrf(userA));
        assertEquals(200, update.statusCode(), update.body());
        snapshotMode = "normal";
        var result = JSON.readTree(inFlight.get().body());
        assertEquals("REJECTED", result.path("status").asString(), result.toString());
        assertEquals("SCENARIO_CHANGED", result.path("reason").asString());
    }

    @Test
    void changedBindingDuringReadIsRejected() throws Exception {
        login(userA, "userA41");
        String[] setup = setupScenario(userA, "E2");
        String scenario = setup[0];
        poll(userA, scenario, UUID.randomUUID().toString());
        setValue("sn-src", "leak", "1");
        snapshotMode = "slow";
        var inFlight =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return poll(userA, scenario, UUID.randomUUID().toString());
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                });
        Thread.sleep(150);
        var rebind =
                request(
                        userA,
                        "/api/v1/devices/" + setup[1] + "/platform-binding",
                        "PUT",
                        "{\"serialNumber\":\"sn-src\",\"signalMappings\":[{\"signalKey\":\"leak\",\"platformName\":\"leak2\"}],\"expectedVersion\":1}",
                        csrf(userA));
        assertEquals(200, rebind.statusCode(), rebind.body());
        snapshotMode = "normal";
        var result = JSON.readTree(inFlight.get().body());
        assertEquals("REJECTED", result.path("status").asString(), result.toString());
        assertEquals("BINDING_CHANGED", result.path("reason").asString());
    }

    @Test
    void missingSignalAndReadErrorsDoNotCreateEvents() throws Exception {
        login(userA, "userA5");
        String[] setup = setupScenario(userA, "F");
        String scenario = setup[0];
        snapshotBody =
                """
                {"error":0,"data":{"devices":[
                  {"id":"sn-src","signals":[{"name":"ver","value":"1"}]},
                  {"id":"sn-tgt","signals":[{"name":"lamp","value":0}]}]}}""";
        var missing = JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body());
        assertEquals("READ_ERROR", missing.path("status").asString());
        assertEquals("MISSING_SIGNAL", missing.path("reason").asString());
        assertFalse(missing.has("runId"));
        assertEquals(0, sendCalls.get());

        snapshotBody = "{\"error\":1,\"error_code\":\"Access denied\"}";
        var denied = JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body());
        assertEquals("READ_ERROR", denied.path("status").asString());
        assertEquals("UJIN_ACCESS_DENIED", denied.path("reason").asString());

        snapshotMode = "disconnect";
        var offline = JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body());
        assertEquals("READ_ERROR", offline.path("status").asString());
        assertEquals("UJIN_UNAVAILABLE", offline.path("reason").asString());
        snapshotMode = "normal";
        setValue("sn-src", "leak", "0");

        // После ошибок чтения baseline не подменён нулём: первое корректное чтение снова baseline.
        var baseline = JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body());
        assertEquals("BASELINE_STORED", baseline.path("status").asString());
    }

    @Test
    void runSucceedsOnlyAfterCommandConfirmed() throws Exception {
        login(userA, "userA6");
        String[] setup = setupScenario(userA, "G");
        String scenario = setup[0];
        poll(userA, scenario, UUID.randomUUID().toString());
        setValue("sn-src", "leak", "1");
        var changed = JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body());
        String runId = changed.path("runId").asString();
        var run = JSON.readTree(request(userA, "/api/v1/runs/" + runId, "GET", null, null).body());
        String commandId = run.path("commandId").asString();
        // Приём платформой (error=0) не делает Run успешным.
        assertEquals("PENDING", run.path("status").asString());

        // Применение задачи подтверждает команду и только тогда SUCCEEDED.
        infoBody =
                """
                {"error":0,"data":{"signals":[
                  {"name":"lamp","task":1,"signal_task_dt":"t-s6","state":{"value":0}}]}}""";
        var check =
                request(userA, "/api/v1/commands/" + commandId + "/check", "POST", null, csrf(userA));
        var receipt = JSON.readTree(check.body());
        assertEquals("CONFIRMED", receipt.path("status").asString(), check.body());

        run = JSON.readTree(request(userA, "/api/v1/runs/" + runId, "GET", null, null).body());
        assertEquals("SUCCEEDED", run.path("status").asString(), run.toString());
    }

    @Test
    void commandPathDisabledBlocksRunWithReason() throws Exception {
        login(userA, "userA7");
        String[] setup = setupScenario(userA, "H");
        String scenario = setup[0];
        poll(userA, scenario, UUID.randomUUID().toString());
        setValue("sn-src", "leak", "1");
        commandsConfig.setEnabled(false);
        try {
            var changed = JSON.readTree(poll(userA, scenario, UUID.randomUUID().toString()).body());
            var run = JSON.readTree(
                    request(userA, "/api/v1/runs/" + changed.path("runId").asString(), "GET", null, null).body());
            assertEquals("BLOCKED", run.path("status").asString(), run.toString());
            assertEquals("COMMAND_DELIVERY_DISABLED", run.path("reason").asString());
        } finally {
            commandsConfig.setEnabled(true);
        }
    }

    @Test
    void foreignOwnerCannotPollScenario() throws Exception {
        login(userA, "userA8");
        login(userB, "userB8");
        String[] setup = setupScenario(userA, "I");
        String scenario = setup[0];
        var foreign = poll(userB, scenario, UUID.randomUUID().toString());
        assertEquals(404, foreign.statusCode(), foreign.body());
        assertFalse(foreign.body().contains("sn-src"));
        assertEquals(0, sendCalls.get());
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate reviewJdbc;

    @Test
    void reviewRebindingBeforeNextPollMustEstablishNewBaseline() throws Exception {
        login(userA, "review-baseline");
        String[] setup = setupScenario(userA, "Review baseline");
        assertEquals("BASELINE_STORED", JSON.readTree(poll(userA, setup[0], UUID.randomUUID().toString()).body()).path("status").asString());
        snapshotBody = """
                {"error":0,"data":{"devices":[
                  {"id":"sn-src","signals":[{"name":"leak","value":0}]},
                  {"id":"sn-alt","signals":[{"name":"leak","value":1}]},
                  {"id":"sn-tgt","signals":[{"name":"lamp","value":0}]}]}}
                """;
        var rebind = request(userA, "/api/v1/devices/" + setup[1] + "/platform-binding", "PUT",
                "{\"serialNumber\":\"sn-alt\",\"signalMappings\":[{\"signalKey\":\"leak\",\"platformName\":\"leak\"}],\"expectedVersion\":1}", csrf(userA));
        assertEquals(200, rebind.statusCode(), rebind.body());
        var firstNewDevice = JSON.readTree(poll(userA, setup[0], UUID.randomUUID().toString()).body());
        assertEquals("BASELINE_STORED", firstNewDevice.path("status").asString(), firstNewDevice.toString());
        assertEquals(0, sendCalls.get(), "first observation of a different device must not send a command");
    }

    @Test
    void reviewInterruptedDispatchMustExpireEvenWhenInputNeverChanges() throws Exception {
        login(userA, "review-interrupted");
        String[] setup = setupScenario(userA, "Review interrupted");
        poll(userA, setup[0], UUID.randomUUID().toString());
        setValue("sn-src", "leak", "1");
        var changed = JSON.readTree(poll(userA, setup[0], UUID.randomUUID().toString()).body());
        UUID runId = UUID.fromString(changed.path("runId").asString());
        // Reproduce the persisted state of a crash after accept() commits but before dispatch() reserves the command.
        reviewJdbc.update("DELETE FROM runtime_commands WHERE command_id=(SELECT command_id FROM runtime_runs WHERE id=?)", runId);
        reviewJdbc.update("UPDATE runtime_runs SET reason='DISPATCHING',created_at=? WHERE id=?", java.time.Instant.now().minusSeconds(120).toString(), runId);
        poll(userA, setup[0], UUID.randomUUID().toString());
        var staleRun = JSON.readTree(request(userA, "/api/v1/runs/" + runId, "GET", null, null).body());
        assertEquals("UNKNOWN", staleRun.path("status").asString(), staleRun.toString());
    }

    @Test
    void reviewOlderReadCannotOverwriteNewerUnchangedObservation() throws Exception {
        login(userA, "review-order");
        String[] setup = setupScenario(userA, "Review order");
        poll(userA, setup[0], UUID.randomUUID().toString());
        setValue("sn-src", "leak", "1");
        reviewReadEntered = new java.util.concurrent.CountDownLatch(1);
        reviewReadRelease = new java.util.concurrent.CountDownLatch(1);
        reviewCaptureNext.set(true);
        var older = CompletableFuture.supplyAsync(() -> {
            try { return poll(userA, setup[0], UUID.randomUUID().toString()); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        });
        assertTrue(reviewReadEntered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        try {
            setValue("sn-src", "leak", "0");
            assertEquals("NO_CHANGE", JSON.readTree(poll(userA, setup[0], UUID.randomUUID().toString()).body()).path("status").asString());
        } finally { reviewReadRelease.countDown(); }
        var stale = JSON.readTree(older.get().body());
        assertEquals("REJECTED", stale.path("status").asString(), stale.toString());
        assertEquals(0, sendCalls.get(), "a late old reading must not create an action after a newer observation");
    }

    @Test
    void reviewUnchangedPollReconcilesExistingCommandWithoutResending() throws Exception {
        login(userA, "review-reconcile");
        String[] setup = setupScenario(userA, "Review reconcile");
        poll(userA, setup[0], UUID.randomUUID().toString());
        setValue("sn-src", "leak", "1");
        var changed = JSON.readTree(poll(userA, setup[0], UUID.randomUUID().toString()).body());
        String runId = changed.path("runId").asString();
        infoBody = """
                {"error":0,"data":{"signals":[{"name":"lamp","task":1,"signal_task_dt":"review-new-task","state":{"value":0}}]}}
                """;
        poll(userA, setup[0], UUID.randomUUID().toString());
        var run = JSON.readTree(request(userA, "/api/v1/runs/" + runId, "GET", null, null).body());
        assertEquals("SUCCEEDED", run.path("status").asString(), run.toString());
        assertEquals(1, sendCalls.get(), "reconciliation must only check the existing command");
    }

}
