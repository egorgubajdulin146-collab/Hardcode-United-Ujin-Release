package com.shastage.ujin.web;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"ujin.demo.enabled=false","ujin.api.base-url=http://127.0.0.1:1/api"})
class GuestDemoDisabledTest {
    @LocalServerPort int port;
    @Test void demoIsUnavailableWithoutFlag()throws Exception{
        var c=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();String base="http://127.0.0.1:"+port;
        var info=c.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/session")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,info.statusCode());assertTrue(info.body().contains("\"demoAvailable\":false"));
        var csrf=c.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body();
        var result=c.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/demo")).header("X-XSRF-TOKEN",csrf).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());assertEquals(404,result.statusCode());
    }
}
