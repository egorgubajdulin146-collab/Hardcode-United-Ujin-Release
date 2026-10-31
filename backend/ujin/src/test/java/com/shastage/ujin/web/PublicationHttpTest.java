package com.shastage.ujin.web;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import com.shastage.ujin.datasource.repository.jpa.PublicationJpaRepository;
import com.shastage.ujin.domain.service.PublicationProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicationHttpTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final HttpServer upstream;
    static final List<String> writes = new CopyOnWriteArrayList<>();
    static volatile String signals = "";
    static volatile String failure = "";
    static volatile boolean visible = true;
    static volatile String malformedQuery = null;
    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstream.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            upstream.createContext("/api/auth/authenticate/", e -> {
                e.getRequestBody().readAllBytes();
                respond(e, "{\"error\":0,\"data\":{\"token\":\"resident-secret\"}}");
            });
            upstream.createContext("/api/apartment/get-device-signals/", e -> respond(e,
                    "{\"error\":0,\"data\":{\"devices\":[" + (visible
                      ? "{\"id\":\"9007199254740993123\",\"signals\":[" + signals + "]}" : "") + "]}}"));
            upstream.createContext("/api/import_device/group-update-device-signal/", e -> {
                String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                writes.add(body);
                if (!e.getRequestMethod().equals("POST") || !"application/json".equals(e.getRequestHeaders().getFirst("Content-Type"))
                        || !"token=ust-secret&apartment_id=apt-test&serialnumber=ext-test".equals(e.getRequestURI().getRawQuery()))
                    malformedQuery = "Wire contract mismatch";
                var row = JSON.readTree(body).path("data").get(0);
                if (!row.path("sn").asString().equals("ext-test") || !row.path("c").asString().equals("appliances"))
                    malformedQuery = "Destination mismatch";
                if (failure.equals("reject")) { respond(e, "{\"error\":1,\"error_code\":\"Access denied\"}"); return; }
                if (failure.equals("disconnect")) { e.close(); return; }
                if (failure.equals("bad-json")) { respond(e, "not-json ust-secret"); return; }
                if (!failure.equals("silent") && row.path("s").asString().equals("appliances_leak"))
                    signals = "{\"name\":\"appliances_leak\",\"value\":" + row.path("v").asDouble() + "}";
                respond(e, "{\"error\":0}");
            });
            upstream.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    static void respond(com.sun.net.httpserver.HttpExchange e, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json");
        e.sendResponseHeaders(200, bytes.length);
        try (var out = e.getResponseBody()) { out.write(bytes); }
    }
    @AfterAll static void stop() { upstream.stop(0); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("ujin.api.base-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort() + "/api");
        r.add("ujin.publication.enabled", () -> "true");
        r.add("ujin.publication.ust", () -> "ust-secret");
        r.add("ujin.publication.dictionary-delay-ms", () -> "0");
        String p = "ujin.publication.targets[0].";
        r.add(p + "serial-number", () -> "9007199254740993123");
        r.add(p + "ext-guid", () -> "ext-test");
        r.add(p + "apartment-id", () -> "apt-test");
        r.add(p + "model", () -> "other");
        r.add(p + "publish-class", () -> "appliances");
        r.add(p + "signals[0].signal-key", () -> "leak");
        r.add(p + "signals[0].platform-name", () -> "appliances_leak");
        r.add(p + "signals[0].short-name", () -> "leak");
    }
    @LocalServerPort int port;
    @Autowired PublicationJpaRepository repository;
    @Autowired PublicationProperties config;
    HttpClient user;
    String device;
    @BeforeEach void setup() throws Exception {
        writes.clear(); signals = ""; failure = ""; visible = true; malformedQuery = null;
        config.setEnabled(true); config.getTargets().getFirst().getSignals().getFirst().setZeroWireValue(null);
        user = login("owner-" + UUID.randomUUID());
        device = createDevice(user);
        var bind = request(user, "/api/v1/devices/" + device + "/platform-binding", "PUT", """
                {"serialNumber":"9007199254740993123","signalMappings":[{"signalKey":"leak","platformName":"appliances_leak"}]}
                """, true);
        assertEquals(201, bind.statusCode(), bind.body());
        wet(1);
    }
    HttpClient login(String login) throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        assertEquals(204, request(client, "/api/auth/login", "POST",
                "{\"login\":\"" + login + "\",\"password\":\"fake-password\"}", true).statusCode());
        return client;
    }
    String createDevice(HttpClient client) throws Exception {
        var created = request(client, "/api/v1/devices", "POST", """
                {"title":"Publication leak","family":"leak","signals":[
                {"key":"leak","title":"Leak","group":"property","management":"r","valueType":"NUMBER","control":"list","min":0,"max":1},
                {"key":"reset","title":"Reset","group":"capability","management":"w","valueType":"NUMBER","control":"button","min":0,"max":1}],
                "events":[{"key":"wet","title":"Wet","signalKey":"leak","emission":"CHANGE"}]}
                """, true);
        assertEquals(201, created.statusCode(), created.body());
        return JSON.readTree(created.body()).path("id").asString();
    }
    HttpResponse<String> request(HttpClient client, String path, String method, String body, boolean csrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf) builder.header("X-XSRF-TOKEN", request(client, "/api/auth/csrf", "GET", null, false).body());
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    void wet(int value) throws Exception {
        var response = request(user, "/api/v1/devices/" + device + "/stimuli", "POST",
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"WET\",\"value\":" + value + "}", true);
        assertEquals(202, response.statusCode(), response.body());
    }
    String body(UUID id, String key, double value) {
        return "{\"publicationId\":\"" + id + "\",\"signalKey\":\"" + key + "\",\"value\":" + value + "}";
    }
    HttpResponse<String> publish(UUID id, double value) throws Exception {
        return request(user, "/api/v1/devices/" + device + "/publications", "POST", body(id, "leak", value), true);
    }
    void status(HttpResponse<String> response, String expected) {
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(expected, JSON.readTree(response.body()).path("status").asString());
        assertFalse(response.body().contains("secret")); assertFalse(response.body().contains("token="));
        assertNull(malformedQuery);
    }
    @Test void twoPhasesReadbackAndZeroPreserveLocalState() throws Exception {
        var id = UUID.randomUUID(); status(publish(id, 1), "CONFIRMED");
        assertEquals(2, writes.size());
        assertEquals("leak", JSON.readTree(writes.getFirst()).path("data").get(0).path("s").asString());
        status(publish(id, 1), "CONFIRMED"); assertEquals(2, writes.size());
        wet(0); status(publish(UUID.randomUUID(), 0), "CONFIRMED"); assertEquals(3, writes.size());
        assertEquals(0, JSON.readTree(writes.getLast()).path("data").get(0).path("v").asDouble());
        var local = request(user, "/api/v1/devices/" + device + "/state", "GET", null, false);
        assertEquals(0, JSON.readTree(local.body()).path("values").get(0).path("value").asDouble());
        assertFalse(repository.findById(id).orElseThrow().plan.contains("secret"));
    }
    @Test void explicitZeroMappingAffectsOnlyWireValue() throws Exception {
        config.getTargets().getFirst().getSignals().getFirst().setZeroWireValue(0.001);
        wet(0); var response = publish(UUID.randomUUID(), 0); status(response, "CONFIRMED");
        assertEquals(0, JSON.readTree(response.body()).path("value").asDouble());
        assertEquals(0.001, JSON.readTree(writes.getLast()).path("data").get(0).path("v").asDouble());
        var state = request(user, "/api/v1/devices/" + device + "/state?mode=UJIN", "GET", null, false);
        assertEquals(0, JSON.readTree(state.body()).path("values").get(0).path("value").asDouble());
    }
    @Test void dictionaryFailureStopsBeforeSecondPhase() throws Exception {
        failure = "reject"; var id = UUID.randomUUID(); status(publish(id, 1), "FAILED");
        assertEquals(1, writes.size()); status(publish(id, 1), "FAILED"); assertEquals(1, writes.size());
    }
    @Test void acknowledgementWithoutValueStaysUnknownAndReadbackDoesNotResend() throws Exception {
        failure = "silent"; var id = UUID.randomUUID(); status(publish(id, 1), "UNKNOWN");
        status(publish(id, 1), "UNKNOWN"); assertEquals(2, writes.size());
        assertEquals(409, publish(UUID.randomUUID(), 1).statusCode());
        signals = "{\"name\":\"appliances_leak\",\"value\":1}";
        status(request(user, "/api/v1/devices/" + device + "/publications/" + id + "/readback", "POST", "{}", true), "CONFIRMED");
        assertEquals(2, writes.size());
    }
    @Test void uncertainTransportNeverRetriesAndRejectsChangedPayload() throws Exception {
        failure = "bad-json"; var id = UUID.randomUUID(); status(publish(id, 1), "UNKNOWN");
        status(publish(id, 1), "UNKNOWN"); assertEquals(1, writes.size());
        assertEquals(409, publish(id, 0).statusCode());
    }
    @Test void disconnectedPostIsNotAutomaticallyReplayed() throws Exception {
        failure = "disconnect"; var id = UUID.randomUUID(); status(publish(id, 1), "UNKNOWN");
        assertEquals(1, writes.size()); status(publish(id, 1), "UNKNOWN"); assertEquals(1, writes.size());
    }
    @Test void persistedPendingCanBeReadAfterLeaseWithoutResending() throws Exception {
        failure = "silent"; var id = UUID.randomUUID(); status(publish(id, 1), "UNKNOWN");
        var row = repository.findById(id).orElseThrow(); row.status = "PENDING"; row.reason = "DISPATCHING";
        row.createdAt = Instant.now().minusSeconds(100); repository.saveAndFlush(row);
        signals = "{\"name\":\"appliances_leak\",\"value\":1}";
        status(request(user, "/api/v1/devices/" + device + "/publications/" + id + "/readback", "POST", "{}", true), "CONFIRMED");
        assertEquals(2, writes.size());
    }
    @Test void permissionsCurrentStateAndConfigurationFailClosed() throws Exception {
        var id = UUID.randomUUID();
        assertEquals(409, publish(id, 0).statusCode());
        assertEquals(422, request(user, "/api/v1/devices/" + device + "/publications", "POST", body(id, "reset", 1), true).statusCode());
        config.setEnabled(false); assertEquals(503, publish(id, 1).statusCode()); config.setEnabled(true);
        visible = false; assertEquals(403, publish(id, 1).statusCode()); visible = true;
        assertEquals(403, request(user, "/api/v1/devices/" + device + "/publications", "POST", body(id, "leak", 1), false).statusCode());
        assertTrue(writes.isEmpty()); assertTrue(repository.findById(id).isEmpty());
    }
    @Test void otherAccountCannotPublishReadOrCollideWithReceipt() throws Exception {
        var id = UUID.randomUUID(); status(publish(id, 1), "CONFIRMED");
        var other = login("other-" + UUID.randomUUID());
        assertEquals(404, request(other, "/api/v1/devices/" + device + "/publications", "POST", body(id, "leak", 1), true).statusCode());
        assertEquals(404, request(other, "/api/v1/devices/" + device + "/publications/" + id, "GET", null, false).statusCode());
        String otherDevice = createDevice(other);
        assertEquals(409, request(other, "/api/v1/devices/" + otherDevice + "/publications", "POST", body(id, "leak", 1), true).statusCode());
        assertEquals(2, writes.size());
    }
    @Test void concurrentReplayReservesExactlyOneExternalPublication() throws Exception {
        var id = UUID.randomUUID();
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> publish(id, 1));
            var second = pool.submit(() -> publish(id, 1));
            assertEquals(200, first.get().statusCode()); assertEquals(200, second.get().statusCode());
        }
        assertEquals(2, writes.size());
        status(publish(id, 1), "CONFIRMED"); assertEquals(2, writes.size());
    }
    @Test void booleanAndMissingReadbackCannotConfirmNumericZero() throws Exception {
        failure = "silent"; wet(0); var id = UUID.randomUUID(); status(publish(id, 0), "UNKNOWN");
        for (String value : List.of("false", "null", "\"not-a-number\"")) {
            signals = "{\"name\":\"appliances_leak\",\"value\":" + value + "}";
            status(request(user, "/api/v1/devices/" + device + "/publications/" + id + "/readback", "POST", "{}", true), "UNKNOWN");
        }
        assertEquals(2, writes.size());
    }
}
