package com.shastage.ujin.web;

import com.sun.net.httpserver.HttpServer;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UjinSessionIntegrationTest {
    private static final HttpServer upstream;
    private static volatile int upstreamStatus = 200;
    private static volatile String upstreamBody;
    private static volatile String upstreamMode = "normal";
    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstream.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            upstream.createContext("/api/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                if (upstreamMode.equals("disconnect")) { exchange.close(); return; }
                if (upstreamMode.equals("timeout")) {
                    try { Thread.sleep(16_000); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                    exchange.close();
                    return;
                }
                byte[] body = upstreamBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(upstreamStatus, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            upstream.start();
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("ujin.api.base-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort() + "/api");
        registry.add("ujin.api.app", () -> "test-app");
    }

    @AfterAll
    static void stop() { upstream.stop(0); }

    @LocalServerPort
    int port;
    private HttpClient browser;

    @BeforeEach
    void reset() {
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        upstreamStatus = 200;
        upstreamMode = "normal";
        upstreamBody = "{\"error\":0,\"data\":{\"user\":{\"token\":\"private-token\"}}}";
    }

    private HttpResponse<String> request(String path, String method, String body, String csrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf != null) builder.header("X-XSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        return browser.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String csrf() throws Exception {
        var response = request("/api/auth/csrf", "GET", null, null);
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private HttpResponse<String> login(String csrf) throws Exception {
        return request("/api/auth/login", "POST", "{\"login\":\"employee\",\"password\":\"private-password\"}", csrf);
    }

    private void assertSafe(HttpResponse<String> response) {
        String exposed = response.body() + response.headers();
        assertFalse(exposed.contains("private-token"));
        assertFalse(exposed.contains("private-password"));
        assertFalse(exposed.contains("token="));
    }

    @Test
    void sessionRequiresCsrfAndLogoutRevokesAccess() throws Exception {
        String signal = "/api/v1/ujin/devices/device-1/signals?name=lamp";
        assertEquals(401, request(signal, "GET", null, null).statusCode());
        assertEquals(403, login(null).statusCode());
        assertEquals(403, login("invalid").statusCode());
        var login = login(csrf());
        assertEquals(204, login.statusCode());
        assertSafe(login);
        upstreamBody = "{\"error\":0,\"data\":{\"signals\":[{\"name\":\"lamp\",\"state\":{\"value\":1}}]}}";
        var read = request(signal, "GET", null, null);
        assertEquals(200, read.statusCode());
        assertTrue(read.body().contains("\"value\":\"1\""));
        assertSafe(read);
        assertEquals(403, request("/api/auth/logout", "POST", null, null).statusCode());
        assertEquals(403, request("/api/auth/logout", "POST", null, "invalid").statusCode());
        assertEquals(204, request("/api/auth/logout", "POST", null, csrf()).statusCode());
        assertEquals(401, request(signal, "GET", null, null).statusCode());
    }

    @Test
    void authenticationClassifiesUpstreamFailures() throws Exception {
        String csrf = csrf();
        upstreamStatus = 401;
        assertEquals(401, login(csrf).statusCode());
        upstreamStatus = 500;
        var failure = login(csrf);
        assertEquals(502, failure.statusCode());
        assertSafe(failure);
        upstreamStatus = 200;
        upstreamBody = "{\"error\":1,\"data\":{}}";
        assertEquals(401, login(csrf).statusCode());
        for (String body : new String[]{"not json", "{\"error\":0,\"data\":{}}", "{\"error\":99}", "x".repeat(1_048_577)}) {
            upstreamBody = body;
            var response = login(csrf);
            assertEquals(502, response.statusCode());
            assertSafe(response);
        }
    }

    @Test
    void unknownApiErrorIsNotPermissionDenied() throws Exception {
        assertEquals(204, login(csrf()).statusCode());
        upstreamBody = "{\"error\":99,\"message\":\"private-token private-password\"}";
        var response = request("/api/v1/ujin/devices/device-1/signals?name=lamp", "GET", null, null);
        assertEquals(502, response.statusCode());
        assertSafe(response);
        upstreamStatus = 403;
        assertEquals(403, request("/api/v1/ujin/devices/device-1/signals?name=lamp", "GET", null, null).statusCode());
        upstreamStatus = 200;
        upstreamBody = "not json";
        assertEquals(502, request("/api/v1/ujin/devices/device-1/signals?name=lamp", "GET", null, null).statusCode());
    }

    @Test
    void upstreamDisconnectAndTimeoutAreServiceUnavailable() throws Exception {
        String csrf = csrf();
        upstreamMode = "disconnect";
        var disconnected = login(csrf);
        assertEquals(503, disconnected.statusCode());
        assertSafe(disconnected);
        upstreamMode = "timeout";
        var timedOut = login(csrf);
        assertEquals(503, timedOut.statusCode());
        assertSafe(timedOut);
    }
    @Test
    void catalogHttpUsesSessionCsrfAndTypedDtos() throws Exception {
        var path="/api/v1/devices";
        assertEquals(401,request(path,"GET",null,null).statusCode());
        assertEquals(204,login(csrf()).statusCode());
        String declaration="""
                {"title":"HTTP lamp","family":"light","signals":[{"key":"lamp","title":"Lamp","group":"capability","management":"rw","valueType":"NUMBER","control":"switch","min":0,"max":1}],"events":[]}
                """;
        assertEquals(403,request(path,"POST",declaration,null).statusCode());
        var created=request(path,"POST",declaration,csrf());
        assertEquals(201,created.statusCode(),created.body());
        assertTrue(created.headers().firstValue("Location").orElseThrow().startsWith(path+"/"));
        var json=new tools.jackson.databind.ObjectMapper();
        var device=json.readTree(created.body());
        String item=path+"/"+device.get("id").asText();
        assertEquals(1,device.get("version").asLong());
        assertEquals("NOT_CONFIGURED",device.get("platformLink").asText());
        assertFalse(created.body().contains("device_id"));
        assertEquals(200,request(item,"GET",null,null).statusCode());
        assertEquals(400,request(path+"/invalid-uuid","GET",null,null).statusCode());
        assertEquals(400,request(path,"POST","{",csrf()).statusCode());
        var invalid=request(path,"POST",declaration.replace("\"max\":1","\"max\":-1"),csrf());
        assertEquals(422,invalid.statusCode());
        assertTrue(invalid.headers().firstValue("Content-Type").orElseThrow().contains("application/problem+json"));
        assertEquals("VALIDATION_ERROR",json.readTree(invalid.body()).get("code").asText());
        assertEquals(400,request(path,"POST",declaration.replace("\"min\":0","\"min\":\"zero\""),csrf()).statusCode());
        assertEquals(400,request(path,"POST",declaration.replace("\"family\":", "\"unexpected\":1,\"family\":"),csrf()).statusCode());
        String update=declaration.strip().replaceFirst("\\{","{\"expectedVersion\":1,");
        assertEquals(200,request(item,"PUT",update,csrf()).statusCode());
        assertEquals(409,request(item,"PUT",update,csrf()).statusCode());
        assertEquals(204,request(item+"?expectedVersion=2","DELETE",null,csrf()).statusCode());
        assertEquals(404,request(item,"GET",null,null).statusCode());
    }

    @Test
    void scenarioHttpMapsRulesAndProtectsReferencedDevices() throws Exception {
        assertEquals(204,login(csrf()).statusCode());
        var json=new tools.jackson.databind.ObjectMapper();
        String declaration="""
                {"title":"Rule device","family":"light","signals":[{"key":"lamp","title":"Lamp","group":"capability","management":"rw","valueType":"NUMBER","control":"switch","min":0,"max":1}],"events":[{"key":"changed","title":"Changed","signalKey":"lamp","emission":"CHANGE"}]}
                """;
        var first=request("/api/v1/devices","POST",declaration,csrf());
        var second=request("/api/v1/devices","POST",declaration,csrf());
        assertEquals(201,first.statusCode()); assertEquals(201,second.statusCode());
        String source=json.readTree(first.body()).get("id").asText();
        String target=json.readTree(second.body()).get("id").asText();
        String rule="""
                {"title":"HTTP rule","enabled":false,"executionMode":"UJIN","trigger":{"deviceId":"%s","signalKey":"lamp","operator":"EQ","value":1,"eventKey":"changed"},"action":{"deviceId":"%s","signalKey":"lamp","value":1}}
                """.formatted(source,target);
        var created=request("/api/v1/scenarios","POST",rule,csrf());
        assertEquals(201,created.statusCode(),created.body());
        var scenario=json.readTree(created.body());
        String path="/api/v1/scenarios/"+scenario.get("id").asText();
        assertEquals(source,scenario.get("trigger").get("deviceId").asText());
        assertEquals(target,scenario.get("action").get("deviceId").asText());
        assertFalse(scenario.get("enabled").asBoolean());
        assertEquals(created.body(),request(path,"GET",null,null).body());
        assertEquals(200,request("/api/v1/scenarios?page=0&size=1","GET",null,null).statusCode());
        String update=rule.strip().replaceFirst("\\{","{\"expectedVersion\":1,").replace("\"enabled\":false","\"enabled\":true");
        var updated=request(path,"PUT",update,csrf());
        assertEquals(200,updated.statusCode(),updated.body());
        assertEquals(2,json.readTree(updated.body()).get("version").asLong());
        assertEquals(409,request(path,"PUT",update,csrf()).statusCode());
        var dependencies=request("/api/v1/devices/"+target+"/dependencies","GET",null,null);
        assertEquals(200,dependencies.statusCode());
        assertTrue(dependencies.body().contains(scenario.get("id").asText()));
        var deletion=request("/api/v1/devices/"+target+"?expectedVersion=1","DELETE",null,csrf());
        assertEquals(409,deletion.statusCode());
        assertEquals("DEVICE_IN_USE",json.readTree(deletion.body()).get("code").asText());
    }

}
