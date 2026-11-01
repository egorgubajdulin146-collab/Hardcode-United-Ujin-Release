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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlatformBindingHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MULTI_SNAPSHOT =
            """
            {"error":0,"data":{"devices":[
              {"id":"sn-alpha","signals":[{"name":"ver","value":"1.0"},{"name":"leak","value":1}]},
              {"id":"sn-target","signals":[{"name":"leak","value":0},{"name":"lamp","value":1},
                {"name":"ver","value":"2.0"},{"name":"dt-create","value":0,"dt":"2026-11-01T10:00:00Z"}]},
              {"id":"sn-omega","signals":[{"name":"leak","value":1},{"name":"lamp","value":0}]}
            ]}}""";

    private static final HttpServer upstream;
    private static volatile String snapshotBody = MULTI_SNAPSHOT;
    private static volatile String authBody =
            "{\"error\":0,\"data\":{\"user\":{\"token\":\"private-token\"}}}";
    private static volatile String upstreamMode = "normal";

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
                        if (upstreamMode.equals("disconnect")) {
                            exchange.close();
                            return;
                        }
                        respond(exchange, snapshotBody);
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

    private HttpClient userA;
    private HttpClient userB;

    @BeforeEach
    void reset() {
        userA = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        userB = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        snapshotBody = MULTI_SNAPSHOT;
        authBody = "{\"error\":0,\"data\":{\"user\":{\"token\":\"private-token\"}}}";
        upstreamMode = "normal";
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

    private String createDevice(HttpClient user, String title, String body) throws Exception {
        var response = request(user, "/api/v1/devices", "POST", body, csrf(user));
        assertEquals(201, response.statusCode(), response.body());
        return JSON.readTree(response.body()).path("id").asString();
    }

    private static String deviceJson(String title) {
        return """
            {"title":"%s","family":"leak","signals":[
              {"key":"leak","title":"Leak","group":"property","management":"r","valueType":"NUMBER",
               "control":"list","min":0,"max":1},
              {"key":"lamp","title":"Lamp","group":"capability","management":"rw","valueType":"NUMBER",
               "control":"switch","min":0,"max":1}
            ],"events":[{"key":"leak_changed","title":"Leak changed","signalKey":"leak","emission":"CHANGE"}]}"""
                .formatted(title);
    }

    private HttpResponse<String> putBinding(
            HttpClient user, String deviceId, String body) throws Exception {
        return request(
                user, "/api/v1/devices/" + deviceId + "/platform-binding", "PUT", body, csrf(user));
    }

    private static String bindingJson(String sn, String mappings, String expectedVersion) {
        String version =
                expectedVersion == null ? "" : ",\"expectedVersion\":" + expectedVersion;
        return "{\"serialNumber\":\"" + sn + "\",\"signalMappings\":" + mappings + version + "}";
    }

    private static final String MAPPINGS =
            "[{\"signalKey\":\"leak\",\"platformName\":\"leak\"},"
                    + "{\"signalKey\":\"lamp\",\"platformName\":\"lamp\"}]";

    private void assertSafe(HttpResponse<String> response) {
        String exposed = response.body() + response.headers();
        assertFalse(exposed.contains("private-token"), exposed);
        assertFalse(exposed.contains("private-password"), exposed);
        assertFalse(exposed.contains("token="), exposed);
    }

    private void assertProblem(
            HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(
                response.headers().firstValue("Content-Type").orElse("").contains("application/problem+json"),
                response.body());
        var root = JSON.readTree(response.body());
        assertEquals(code, root.path("code").asString());
        assertSafe(response);
    }

    @Test
    void reviewDuplicateSerialMustNotBeBound() throws Exception {
        login(userA, "review-duplicate");
        String id=createDevice(userA,"Duplicate",deviceJson("Duplicate"));
        snapshotBody="{\"error\":0,\"data\":{\"devices\":[{\"id\":\"same\",\"signals\":[]},{\"id\":\"same\",\"signals\":[]}]}}";
        var result=putBinding(userA,id,bindingJson("same",MAPPINGS,null));
        assertTrue(result.statusCode()>=400,"Ambiguous SN must not become BOUND: "+result.body());
    }
    @Test
    void reviewBinaryEnumMustRejectFractionalPlatformValue() throws Exception {
        login(userA, "review-enum");
        String declaration="{\"title\":\"Binary\",\"family\":\"leak\",\"signals\":[{\"key\":\"leak\",\"title\":\"Leak\",\"group\":\"property\",\"management\":\"r\",\"valueType\":\"NUMBER\",\"control\":\"switch\",\"min\":0,\"max\":1,\"enumValues\":[0,1]}],\"events\":[]}";
        String id=createDevice(userA,"Binary",declaration);
        assertEquals(201,putBinding(userA,id,bindingJson("sn-target","[{\"signalKey\":\"leak\",\"platformName\":\"leak\"}]",null)).statusCode());
        snapshotBody="{\"error\":0,\"data\":{\"devices\":[{\"id\":\"sn-target\",\"signals\":[{\"name\":\"leak\",\"value\":0.5}]}]}}";
        var state=request(userA,"/api/v1/devices/"+id+"/state?mode=UJIN","GET",null,null);
        assertEquals(200,state.statusCode());
        assertTrue(JSON.readTree(state.body()).path("values").isEmpty(),"Out-of-enum value must not be returned as valid: "+state.body());
    }

    @Test
    void twoAccountsCannotSeeEachOtherBindingOrState() throws Exception {
        login(userA, "userA");
        login(userB, "userB");
        String deviceA = createDevice(userA, "Leak A", deviceJson("Leak A"));
        var saved =
                putBinding(userA, deviceA, bindingJson("sn-target", MAPPINGS, null));
        assertEquals(201, saved.statusCode(), saved.body());
        assertSafe(saved);

        for (String path :
                java.util.List.of(
                        "/api/v1/devices/" + deviceA + "/platform-binding",
                        "/api/v1/devices/" + deviceA + "/state?mode=UJIN")) {
            var forbidden = request(userB, path, "GET", null, null);
            assertProblem(forbidden, 404, "DEVICE_NOT_FOUND");
            assertFalse(forbidden.body().contains("sn-target"), forbidden.body());
        }
        var foreignPut =
                putBinding(userB, deviceA, bindingJson("sn-omega", MAPPINGS, null));
        assertProblem(foreignPut, 404, "DEVICE_NOT_FOUND");
        assertFalse(foreignPut.body().contains("sn-omega"));

        var own = request(userA, "/api/v1/devices/" + deviceA + "/platform-binding", "GET", null, null);
        assertEquals(200, own.statusCode(), own.body());
        assertTrue(own.body().contains("sn-target"));
        assertSafe(own);
    }

    @Test
    void invisibleSerialIsNotSavedAsBound() throws Exception {
        login(userA, "userA1");
        String device = createDevice(userA, "Leak B", deviceJson("Leak B"));
        var rejected =
                putBinding(userA, device, bindingJson("sn-invisible", MAPPINGS, null));
        assertProblem(rejected, 422, "UJIN_SN_NOT_VISIBLE");

        var missing =
                request(userA, "/api/v1/devices/" + device + "/platform-binding", "GET", null, null);
        assertProblem(missing, 404, "PLATFORM_NOT_BOUND");

        var state = request(userA, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, null);
        assertProblem(state, 409, "PLATFORM_NOT_BOUND");

        var declaration = request(userA, "/api/v1/devices/" + device, "GET", null, null);
        assertEquals(200, declaration.statusCode());
        assertEquals("NOT_CONFIGURED", JSON.readTree(declaration.body()).path("platformLink").asString());
    }

    @Test
    void bindingMatchesExactSerialAmongManyDevices() throws Exception {
        login(userA, "userA2");
        String device = createDevice(userA, "Leak C", deviceJson("Leak C"));
        var saved = putBinding(userA, device, bindingJson("sn-target", MAPPINGS, null));
        assertEquals(201, saved.statusCode(), saved.body());
        assertEquals("sn-target", JSON.readTree(saved.body()).path("serialNumber").asString());
        assertEquals(1, JSON.readTree(saved.body()).path("version").asInt());

        var state =
                request(userA, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, null);
        assertEquals(200, state.statusCode(), state.body());
        var root = JSON.readTree(state.body());
        assertEquals("UJIN", root.path("executionMode").asString());
        assertEquals("UNKNOWN", root.path("availability").asString());
        var values = root.path("values");
        assertEquals(2, values.size(), state.body());
        assertEquals(0, values.get(0).path("value").asDouble(), state.body());
        assertEquals("leak", values.get(0).path("signalKey").asString());
        assertEquals(1, values.get(1).path("value").asDouble(), state.body());
        assertEquals("lamp", values.get(1).path("signalKey").asString());
        assertSafe(state);

        var declaration = request(userA, "/api/v1/devices/" + device, "GET", null, null);
        assertEquals("BOUND", JSON.readTree(declaration.body()).path("platformLink").asString());
    }

    @Test
    void ambiguousOrUnknownMappingIsRejected() throws Exception {
        login(userA, "userA3");
        String device = createDevice(userA, "Leak D", deviceJson("Leak D"));
        var duplicateKey =
                putBinding(
                        userA,
                        device,
                        bindingJson(
                                "sn-target",
                                "[{\"signalKey\":\"leak\",\"platformName\":\"leak\"},"
                                        + "{\"signalKey\":\"leak\",\"platformName\":\"lamp\"}]",
                                null));
        assertProblem(duplicateKey, 422, "VALIDATION_ERROR");

        var duplicateName =
                putBinding(
                        userA,
                        device,
                        bindingJson(
                                "sn-target",
                                "[{\"signalKey\":\"leak\",\"platformName\":\"leak\"},"
                                        + "{\"signalKey\":\"lamp\",\"platformName\":\"leak\"}]",
                                null));
        assertProblem(duplicateName, 422, "VALIDATION_ERROR");

        var unknownKey =
                putBinding(
                        userA,
                        device,
                        bindingJson(
                                "sn-target",
                                "[{\"signalKey\":\"ghost\",\"platformName\":\"leak\"}]",
                                null));
        assertProblem(unknownKey, 422, "VALIDATION_ERROR");

        var missing =
                request(userA, "/api/v1/devices/" + device + "/platform-binding", "GET", null, null);
        assertProblem(missing, 404, "PLATFORM_NOT_BOUND");
    }

    @Test
    void zeroMissingAndWrongValuesAreReportedSeparately() throws Exception {
        snapshotBody =
                """
                {"error":0,"data":{"devices":[{"id":"sn-values","signals":[
                  {"name":"leak","value":0},
                  {"name":"lamp"}
                ]}]}}""";
        login(userA, "userA4");
        String device = createDevice(userA, "Leak E", deviceJson("Leak E"));
        assertEquals(201, putBinding(userA, device, bindingJson("sn-values", MAPPINGS, null)).statusCode());

        var state =
                request(userA, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, null);
        assertEquals(200, state.statusCode(), state.body());
        var root = JSON.readTree(state.body());
        var values = root.path("values");
        assertEquals(1, values.size(), state.body());
        assertEquals("leak", values.get(0).path("signalKey").asString());
        assertEquals(0, values.get(0).path("value").asDouble(), state.body());
        var issues = root.path("signalIssues");
        assertEquals(1, issues.size(), state.body());
        assertEquals("lamp", issues.get(0).path("signalKey").asString());
        assertEquals("MISSING_VALUE", issues.get(0).path("status").asString());
    }

    @Test
    void missingSignalIsNotSynthesizedAsZero() throws Exception {
        snapshotBody =
                """
                {"error":0,"data":{"devices":[{"id":"sn-empty","signals":[
                  {"name":"ver","value":"1"}
                ]}]}}""";
        login(userA, "userA5");
        String device = createDevice(userA, "Leak F", deviceJson("Leak F"));
        assertEquals(201, putBinding(userA, device, bindingJson("sn-empty", MAPPINGS, null)).statusCode());

        var state =
                request(userA, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, null);
        assertEquals(200, state.statusCode(), state.body());
        var root = JSON.readTree(state.body());
        assertEquals(0, root.path("values").size(), state.body());
        var issues = root.path("signalIssues");
        assertEquals(2, issues.size(), state.body());
        for (int i = 0; i < 2; i++) {
            assertEquals("MISSING_SIGNAL", issues.get(i).path("status").asString());
            String key = issues.get(i).path("signalKey").asString();
            assertEquals(key, issues.get(i).path("expectedPlatformName").asString());
            assertEquals("ver", issues.get(i).path("availablePlatformNames").get(0).asString());
        }
    }

    @Test
    void wrongTypeRangeAndAmbiguousPlatformSignalAreIssues() throws Exception {
        snapshotBody =
                """
                {"error":0,"data":{"devices":[{"id":"sn-bad","signals":[
                  {"name":"leak","value":true},
                  {"name":"lamp","value":5},
                  {"name":"dup","value":1},{"name":"dup","value":2}
                ]}]}}""";
        login(userA, "userA6");
        String device =
                createDevice(
                        userA,
                        "Leak G",
                        """
                        {"title":"Leak G","family":"leak","signals":[
                          {"key":"leak","title":"Leak","group":"property","management":"r","valueType":"NUMBER",
                           "control":"list","min":0,"max":1},
                          {"key":"lamp","title":"Lamp","group":"capability","management":"rw","valueType":"NUMBER",
                           "control":"switch","min":0,"max":1},
                          {"key":"extra","title":"Extra","group":"property","management":"r","valueType":"NUMBER",
                           "control":"input","min":0,"max":10}
                        ],"events":[]}""");
        String mappings =
                "[{\"signalKey\":\"leak\",\"platformName\":\"leak\"},"
                        + "{\"signalKey\":\"lamp\",\"platformName\":\"lamp\"},"
                        + "{\"signalKey\":\"extra\",\"platformName\":\"dup\"}]";
        assertEquals(201, putBinding(userA, device, bindingJson("sn-bad", mappings, null)).statusCode());

        var state =
                request(userA, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, null);
        assertEquals(200, state.statusCode(), state.body());
        var root = JSON.readTree(state.body());
        assertEquals(0, root.path("values").size(), state.body());
        var issues = root.path("signalIssues");
        assertEquals(3, issues.size(), state.body());
        assertEquals("BAD_SCHEMA", issues.get(0).path("status").asString());
        assertEquals("OUT_OF_RANGE", issues.get(1).path("status").asString());
        assertEquals("BAD_SCHEMA", issues.get(2).path("status").asString());
        assertTrue(issues.get(2).path("detail").asString().contains("ambiguous"), state.body());
    }

    @Test
    void stringNumberIsAcceptedAndTextIsRejected() throws Exception {
        snapshotBody =
                """
                {"error":0,"data":{"devices":[{"id":"sn-text","signals":[
                  {"name":"leak","value":"0"},
                  {"name":"lamp","value":"abc"}
                ]}]}}""";
        login(userA, "userA7");
        String device = createDevice(userA, "Leak H", deviceJson("Leak H"));
        assertEquals(201, putBinding(userA, device, bindingJson("sn-text", MAPPINGS, null)).statusCode());
        var state =
                request(userA, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, null);
        var root = JSON.readTree(state.body());
        var values = root.path("values");
        assertEquals(1, values.size(), state.body());
        assertEquals(0, values.get(0).path("value").asDouble());
        assertEquals("BAD_SCHEMA", root.path("signalIssues").get(0).path("status").asString());
    }

    @Test
    void upstreamFailuresAreClassifiedOnBindingAndState() throws Exception {
        login(userA, "userA8");
        String device = createDevice(userA, "Leak I", deviceJson("Leak I"));
        String[][] cases = {
            {"{\"error\":1,\"error_code\":\"Access denied\"}", "403", "UJIN_ACCESS_DENIED"},
            {"{\"error\":2,\"error_code\":\"unknown\"}", "502", "UJIN_PLATFORM_ERROR"},
            {"not json", "502", "UJIN_BAD_RESPONSE"},
            {"{\"error\":0,\"data\":{\"devices\":{}}}", "502", "UJIN_BAD_RESPONSE"}
        };
        for (String[] entry : cases) {
            snapshotBody = entry[0];
            var binding =
                    putBinding(userA, device, bindingJson("sn-target", MAPPINGS, null));
            assertProblem(binding, Integer.parseInt(entry[1]), entry[2]);
            var state =
                    request(userA, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, null);
            assertProblem(state, 409, "PLATFORM_NOT_BOUND");
        }
        snapshotBody = MULTI_SNAPSHOT;
        assertEquals(201, putBinding(userA, device, bindingJson("sn-target", MAPPINGS, null)).statusCode());
        upstreamMode = "disconnect";
        var state =
                request(userA, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, null);
        assertProblem(state, 503, "UJIN_UNAVAILABLE");
        upstreamMode = "normal";
    }

    @Test
    void staleExpectedVersionDoesNotOverwriteBinding() throws Exception {
        login(userA, "userA9");
        String device = createDevice(userA, "Leak J", deviceJson("Leak J"));
        var createWithVersion =
                putBinding(userA, device, bindingJson("sn-target", MAPPINGS, "1"));
        assertProblem(createWithVersion, 409, "VERSION_CONFLICT");

        assertEquals(201, putBinding(userA, device, bindingJson("sn-target", MAPPINGS, null)).statusCode());
        var withoutVersion =
                putBinding(userA, device, bindingJson("sn-omega", MAPPINGS, null));
        assertProblem(withoutVersion, 409, "VERSION_CONFLICT");
        var stale = putBinding(userA, device, bindingJson("sn-omega", MAPPINGS, "7"));
        assertProblem(stale, 409, "VERSION_CONFLICT");

        var current = request(userA, "/api/v1/devices/" + device + "/platform-binding", "GET", null, null);
        assertEquals("sn-target", JSON.readTree(current.body()).path("serialNumber").asString());

        var replace = putBinding(userA, device, bindingJson("sn-omega", MAPPINGS, "1"));
        assertEquals(200, replace.statusCode(), replace.body());
        var updated = JSON.readTree(replace.body());
        assertEquals("sn-omega", updated.path("serialNumber").asString());
        assertEquals(2, updated.path("version").asInt());
    }

    @Test
    void localStateIsNotOverwrittenByUjinRead() throws Exception {
        login(userA, "userA10");
        String device = createDevice(userA, "Leak K", deviceJson("Leak K"));
        assertEquals(201, putBinding(userA, device, bindingJson("sn-target", MAPPINGS, null)).statusCode());

        var stimulus =
                request(
                        userA,
                        "/api/v1/devices/" + device + "/stimuli",
                        "POST",
                        "{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"WET\",\"value\":1}",
                        csrf(userA));
        assertEquals(202, stimulus.statusCode(), stimulus.body());

        var ujin =
                request(userA, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, null);
        var ujinValues = JSON.readTree(ujin.body()).path("values");
        assertEquals(0, ujinValues.get(0).path("value").asDouble(), ujin.body());

        var local = request(userA, "/api/v1/devices/" + device + "/state?mode=LOCAL", "GET", null, null);
        var localValues = JSON.readTree(local.body()).path("values");
        assertEquals(1, localValues.get(0).path("value").asDouble(), local.body());
    }

    @Test
    void serialNumberStaysAStringWithoutPrecisionLoss() throws Exception {
        snapshotBody =
                "{\"error\":0,\"data\":{\"devices\":[{\"id\":\"9007199254740993123\",\"signals\":[{\"name\":\"leak\",\"value\":1}]}]}}";
        login(userA, "userA11");
        String device = createDevice(userA, "Leak L", deviceJson("Leak L"));
        String sn = "9007199254740993123";
        var saved =
                putBinding(
                        userA,
                        device,
                        bindingJson(sn, "[{\"signalKey\":\"leak\",\"platformName\":\"leak\"}]", null));
        assertEquals(201, saved.statusCode(), saved.body());
        var savedRoot = JSON.readTree(saved.body());
        assertTrue(savedRoot.path("serialNumber").isString(), saved.body());
        assertEquals(sn, savedRoot.path("serialNumber").asString());

        var read = request(userA, "/api/v1/devices/" + device + "/platform-binding", "GET", null, null);
        assertEquals(sn, JSON.readTree(read.body()).path("serialNumber").asString());
    }

    @Test
    void measuredAtIsKeptSeparateFromObservationTime() throws Exception {
        login(userA, "userA12");
        String device = createDevice(userA, "Leak M", deviceJson("Leak M"));
        assertEquals(
                201,
                putBinding(
                                userA,
                                device,
                                bindingJson(
                                        "sn-target",
                                        "[{\"signalKey\":\"leak\",\"platformName\":\"dt-create\"}]",
                                        null))
                        .statusCode());
        var state =
                request(userA, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, null);
        var value = JSON.readTree(state.body()).path("values").get(0);
        assertEquals("2026-11-01T10:00:00Z", value.path("measuredAt").asString());
        assertNotEquals("", value.path("updatedAt").asString());
        assertNotEquals("2026-11-01T10:00:00Z", value.path("updatedAt").asString());
    }
}
