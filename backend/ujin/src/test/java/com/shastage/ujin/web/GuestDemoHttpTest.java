package com.shastage.ujin.web;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "ujin.demo.enabled=true", "ujin.commands.enabled=true", "ujin.commands.task-fetch-enabled=true", "ujin.publication.enabled=true"})
class GuestDemoHttpTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final AtomicInteger upstreamCalls = new AtomicInteger();
    static final HttpServer upstream;
    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            upstream.createContext("/", exchange -> {
                upstreamCalls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
                byte[] body="{\"error\":0,\"data\":{\"user\":{\"token\":\"synthetic-token\"}}}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length);
                try(var out=exchange.getResponseBody()){out.write(body);}
            }); upstream.start();
        } catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("ujin.api.base-url",()->"http://127.0.0.1:"+upstream.getAddress().getPort()+"/api");
        registry.add("ujin.api.app",()->"");
    }
    @AfterAll static void stop(){upstream.stop(0);}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.shastage.ujin.web.demo.GuestDemoService guests;
    final List<HttpClient> clients=new ArrayList<>();
    HttpClient client(){var c=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();clients.add(c);return c;}
    HttpResponse<String> request(HttpClient client,String path,String method,String body,String csrf)throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(csrf!=null)b.header("X-XSRF-TOKEN",csrf);
        if(body!=null)b.header("Content-Type","application/json");
        return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(HttpClient c,String path)throws Exception{return request(c,path,"GET",null,null);}
    String csrf(HttpClient c)throws Exception{return get(c,"/api/auth/csrf").body();}
    HttpResponse<String> write(HttpClient c,String path,String method,String body)throws Exception{return request(c,path,method,body,csrf(c));}
    HttpClient guest()throws Exception{var c=client();assertEquals(204,write(c,"/api/auth/demo","POST",null).statusCode());return c;}
    String deviceBody(String family){String key=family.equals("leak")?"leak":"lamp";String group=family.equals("leak")?"property":"capability";String mgmt=family.equals("leak")?"r":"rw";
        return "{\"title\":\"Demo device\",\"family\":\""+family+"\",\"signals\":[{\"key\":\""+key+"\",\"title\":\"Signal\",\"group\":\""+group+"\",\"management\":\""+mgmt+"\",\"valueType\":\"NUMBER\",\"control\":\"switch\",\"min\":0,\"max\":1,\"enumValues\":[0,1]}],\"events\":"+(family.equals("leak")?"[{\"key\":\"changed\",\"title\":\"Changed\",\"signalKey\":\"leak\",\"emission\":\"CHANGE\"}]":"[]")+"}";
    }
    String device(HttpClient c,String family)throws Exception {var r=write(c,"/api/v1/devices","POST",deviceBody(family));assertEquals(201,r.statusCode(),r.body());return JSON.readTree(r.body()).path("id").asString();}
    @AfterEach void cleanup()throws Exception{for(var c:clients)write(c,"/api/auth/logout","POST",null);}

    @Test void csrfAndMetadataWithoutUpstream()throws Exception{
        int before=upstreamCalls.get();var c=client();
        var info=JSON.readTree(get(c,"/api/auth/session").body());assertTrue(info.path("demoAvailable").asBoolean());assertFalse(info.path("authenticated").asBoolean());
        assertEquals(403,request(c,"/api/auth/demo","POST",null,null).statusCode());
        assertEquals(204,write(c,"/api/auth/demo","POST",null).statusCode());
        assertEquals(204,write(c,"/api/auth/demo","POST",null).statusCode());
        info=JSON.readTree(get(c,"/api/auth/session").body());assertTrue(info.path("demo").asBoolean());assertTrue(info.path("authenticated").asBoolean());
        assertEquals(200,get(c,"/api/v1/devices").statusCode());assertEquals(before,upstreamCalls.get());
    }
    @Test void twoGuestsAndRealOwnerRemainIsolated()throws Exception{
        var a=guest();var b=guest();String id=device(a,"light");
        assertEquals(404,get(b,"/api/v1/devices/"+id).statusCode());
        var real=client();assertEquals(204,write(real,"/api/auth/login","POST","{\"login\":\"real-demo-test\",\"password\":\"synthetic\"}").statusCode());
        assertEquals(409,write(real,"/api/auth/demo","POST",null).statusCode());
        String realId=device(real,"light");assertEquals(404,get(a,"/api/v1/devices/"+realId).statusCode());assertEquals(404,get(real,"/api/v1/devices/"+id).statusCode());
        assertEquals(204,write(a,"/api/auth/logout","POST",null).statusCode());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM devices WHERE id=?",Long.class,UUID.fromString(id)));
        assertEquals(200,get(real,"/api/v1/devices/"+realId).statusCode());
    }
    @Test void localScenarioRunsWithoutPlatformAndSurvivesReload()throws Exception{
        int before=upstreamCalls.get();var c=guest();String leak=device(c,"leak"),lamp=device(c,"light");
        String rule="{\"title\":\"Demo rule\",\"enabled\":true,\"executionMode\":\"LOCAL\",\"trigger\":{\"deviceId\":\""+leak+"\",\"signalKey\":\"leak\",\"eventKey\":\"changed\",\"operator\":\"EQ\",\"value\":1},\"action\":{\"deviceId\":\""+lamp+"\",\"signalKey\":\"lamp\",\"value\":1}}";
        assertEquals(201,write(c,"/api/v1/scenarios","POST",rule).statusCode());
        for(int value:new int[]{0,1})assertEquals(202,write(c,"/api/v1/devices/"+leak+"/stimuli","POST","{\"eventId\":\""+UUID.randomUUID()+"\",\"kind\":\"WET\",\"value\":"+value+"}").statusCode());
        var state=JSON.readTree(get(c,"/api/v1/devices/"+lamp+"/state?mode=LOCAL").body());assertEquals(1.0,state.path("values").get(0).path("value").asDouble());
        assertTrue(JSON.readTree(get(c,"/api/auth/session").body()).path("demo").asBoolean());
        assertEquals(2,JSON.readTree(get(c,"/api/v1/devices").body()).path("items").size());
        assertTrue(get(c,"/api/v1/runs").body().contains("SUCCEEDED"));assertEquals(before,upstreamCalls.get());
        write(c,"/api/auth/logout","POST",null);
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM devices WHERE id=?",Long.class,UUID.fromString(leak)));
    }
    @Test void platformAndPythonAreDeniedEvenWhenServerFlagsEnabled()throws Exception{
        int before=upstreamCalls.get();var c=guest();String id=device(c,"light");
        for(String path:List.of("/api/v1/ujin/devices/assigned/signals?name=leak","/api/v1/devices/"+id+"/state?mode=UJIN","/api/v1/devices/"+id+"/platform-binding","/api/v1/devices/"+id+"/publications","/api/v1/local-python/status"))
            assertEquals(403,get(c,path).statusCode(),path);
        for(String path:List.of("/api/v1/local-python/poll","/api/v1/scenarios/"+UUID.randomUUID()+"/poll","/api/v1/commands/"+UUID.randomUUID()+"/check"))assertEquals(403,write(c,path,"POST","{}").statusCode(),path);
        String cmd="{\"commandId\":\""+UUID.randomUUID()+"\",\"signalKey\":\"lamp\",\"value\":1,\"executionMode\":\"UJIN\"}";
        assertEquals(403,write(c,"/api/v1/devices/"+id+"/commands","POST",cmd).statusCode());
        assertEquals(403,write(c,"/api/v1/scenarios","POST","{\"executionMode\":\"UJIN\"}").statusCode());
        var status=JSON.readTree(get(c,"/api/v1/integrations/ujin/status").body());assertEquals("BLOCKED",status.path("downlink").path("status").asString());assertEquals(before,upstreamCalls.get());
    }
    @Test void logoutAndReentryCannotRecoverOldSandbox()throws Exception{
        var c=guest();String id=device(c,"light");
        assertEquals(204,write(c,"/api/auth/logout","POST",null).statusCode());assertEquals(401,get(c,"/api/v1/devices").statusCode());
        assertEquals(204,write(c,"/api/auth/demo","POST",null).statusCode());assertEquals(404,get(c,"/api/v1/devices/"+id).statusCode());assertEquals(0,JSON.readTree(get(c,"/api/v1/devices").body()).path("items").size());
    }
    @Test void deviceQuotaIsEnforcedByBackend()throws Exception{
        var c=guest();for(int i=0;i<8;i++)device(c,"light");
        var denied=write(c,"/api/v1/devices","POST",deviceBody("light"));assertEquals(429,denied.statusCode());assertTrue(denied.body().contains("DEMO_QUOTA"));
    }
    @Test void realLoginAfterDemoUsesFreshSessionAndDeletesOnlyGuestData()throws Exception{
        var c=guest();String id=device(c,"light");
        assertEquals(204,write(c,"/api/auth/login","POST","{\"login\":\"real-transition-test\",\"password\":\"synthetic\"}").statusCode());
        assertFalse(JSON.readTree(get(c,"/api/auth/session").body()).path("demo").asBoolean());assertEquals(404,get(c,"/api/v1/devices/"+id).statusCode());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM devices WHERE id=?",Long.class,UUID.fromString(id)));
    }
    @Test void absoluteExpiryClearsAuthenticationAndSandbox()throws Exception{
        var c=guest();String id=device(c,"light");
        var active=(java.util.Map<?,?>)org.springframework.test.util.ReflectionTestUtils.getField(guests,"active");
        assertEquals(1,active.size());
        Object scope=active.values().iterator().next();
        org.springframework.test.util.ReflectionTestUtils.setField(scope,"expiresAt",java.time.Instant.now().minusSeconds(1));
        var info=JSON.readTree(get(c,"/api/auth/session").body());
        assertFalse(info.path("authenticated").asBoolean());assertFalse(info.path("demo").asBoolean());
        assertTrue(info.path("demoAvailable").asBoolean());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM devices WHERE id=?",Long.class,UUID.fromString(id)));
        assertEquals(204,write(c,"/api/auth/demo","POST",null).statusCode());
    }
    @Test void reservedGuestOwnerCannotBeUsedAsRealLogin()throws Exception{
        int before=upstreamCalls.get();var c=client();
        assertEquals(400,write(c,"/api/auth/login","POST","{\"login\":\"demo-session:forged\",\"password\":\"synthetic\"}").statusCode());
        assertEquals(before,upstreamCalls.get());
    }

}
