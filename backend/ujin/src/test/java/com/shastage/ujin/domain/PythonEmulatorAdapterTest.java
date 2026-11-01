package com.shastage.ujin.domain;
import static org.junit.jupiter.api.Assertions.*;
import com.shastage.ujin.domain.service.*;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.datasource.repository.jpa.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:python-adapter;DB_CLOSE_DELAY=-1", "ujin.api.base-url=http://127.0.0.1:1/api"})
class PythonEmulatorAdapterTest {
    static HttpServer leak,light;
    static AtomicInteger wet=new AtomicInteger(),lamp=new AtomicInteger(),posts=new AtomicInteger(),reads=new AtomicInteger();
    static volatile String mode="normal";
    static volatile boolean missing=false;
    @BeforeAll static void servers() throws Exception {
        leak=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        light=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        light.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        leak.createContext("/state",x->{reads.incrementAndGet();reply(x,200,"{\"family\":\"leak\",\"executionMode\":\"LOCAL\",\"values\":{"+(missing?"":"\"leak\":"+wet.get())+"}}");});
        light.createContext("/state",x->reply(x,200,"{\"family\":\"light\",\"executionMode\":\"LOCAL\",\"values\":{\"lamp\":"+lamp.get()+"}}"));
        light.createContext("/command",x->{
            String body=new String(x.getRequestBody().readAllBytes()); posts.incrementAndGet();
            if(mode.equals("reject")){reply(x,422,"{}");return;}
            if(!mode.equals("mismatch"))lamp.set(body.contains("1.0")?1:0);
            if(mode.equals("disconnect")){x.close();return;}
            reply(x,200,"{}");
        });leak.start();light.start();
    }
    static void reply(com.sun.net.httpserver.HttpExchange x,int code,String body)throws java.io.IOException{
        byte[] bytes=body.getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(code,bytes.length);try(var out=x.getResponseBody()){out.write(bytes);}
    }
    @AfterAll static void stop(){leak.stop(0);light.stop(0);}
    @Autowired CatalogService catalog;
    @Autowired LocalRuntimeService runtime;
    @Autowired PythonEmulatorService adapter;
    @Autowired PythonEmulatorProperties config;
    @Autowired RunJpaRepository runs;
    @Autowired CommandJpaRepository commands;
    @Autowired LocalSignalStateJpaRepository states;
    UUID source,target,scenario;
    static final String OWNER="python-owner";
    @BeforeEach void setup(){
        wet.set(0);lamp.set(0);posts.set(0);reads.set(0);mode="normal";missing=false;
        source=catalog.create(new DeviceWrite("Python leak","leak",List.of(new Signal("leak","Протечка","property","r","NUMBER","switch",null,0.0,1.0,List.of(0.0,1.0))),List.of(new Event("changed","Изменение","leak","CHANGE")),null),OWNER).id();
        target=catalog.create(new DeviceWrite("Python light","light",List.of(new Signal("lamp","Свет","capability","rw","NUMBER","switch",null,0.0,1.0,List.of(0.0,1.0))),List.of(),null),OWNER).id();
        scenario=catalog.create(new ScenarioWrite("Python rule",true,"LOCAL",new Trigger(source,"leak","EQ",1.0,"changed"),new Action(target,"lamp",1.0),null),OWNER).id();
        config.setEnabled(true);config.setOwner(OWNER);config.setSourceDeviceId(source);config.setTargetDeviceId(target);
        config.setLeakUrl(URI.create("http://127.0.0.1:"+leak.getAddress().getPort()));config.setLightUrl(URI.create("http://127.0.0.1:"+light.getAddress().getPort()));
    }
    @AfterEach void reset(){config.setEnabled(false);}
    UUID transition(){adapter.poll(UUID.randomUUID(),OWNER);wet.set(1);UUID e=UUID.randomUUID();adapter.poll(e,OWNER);return e;}
    @Test void firstObservationIsBaselineEvenWhenWet(){wet.set(1);adapter.poll(UUID.randomUUID(),OWNER);assertEquals(0,posts.get());assertEquals(1,states.findByDeviceIdAndSignalKeyAndOwner(source,"leak",OWNER).orElseThrow().getValue());}
    @Test void ruleSendsToPythonAndRequiresReadback(){UUID e=transition();assertEquals(1,posts.get());assertEquals(1,lamp.get());var r=runs.findByOwnerAndEventId(OWNER,e,org.springframework.data.domain.PageRequest.of(0,10)).getContent().get(0);assertEquals("SUCCEEDED",r.getStatus());assertEquals("PYTHON_READBACK_CONFIRMED",r.getReason());assertEquals("CONFIRMED",commands.findByCommandIdAndOwner(r.getId(),OWNER).orElseThrow().getStatus());}
    @Test void duplicatePollAndUnchangedReadingDoNotResend(){UUID e=transition();int n=reads.get();adapter.poll(e,OWNER);assertEquals(n,reads.get());adapter.poll(UUID.randomUUID(),OWNER);assertEquals(1,posts.get());}
    @Test void missingSignalNeverBecomesZero(){missing=true;assertEquals("PYTHON_BAD_RESPONSE",assertThrows(CatalogException.class,()->adapter.poll(UUID.randomUUID(),OWNER)).code);assertTrue(states.findByDeviceIdAndSignalKeyAndOwner(source,"leak",OWNER).isEmpty());assertEquals(0,posts.get());}
    @Test void foreignOwnerCannotReadOrPoll(){assertEquals(404,assertThrows(CatalogException.class,()->adapter.poll(UUID.randomUUID(),"other")).status);assertEquals(0,reads.get());assertEquals(404,assertThrows(CatalogException.class,()->adapter.state(target,"other")).status);}
    @Test void disabledNeverCallsNetwork(){config.setEnabled(false);assertEquals(503,assertThrows(CatalogException.class,()->adapter.poll(UUID.randomUUID(),OWNER)).status);assertEquals(0,reads.get());}
    @Test void lostPostResponseStaysUnknownAndIsNotResentOrConfirmedByOldValue(){adapter.poll(UUID.randomUUID(),OWNER);wet.set(1);mode="disconnect";UUID e=UUID.randomUUID();adapter.poll(e,OWNER);var run=runs.findByOwnerAndEventId(OWNER,e,org.springframework.data.domain.PageRequest.of(0,10)).getContent().get(0);assertEquals("UNKNOWN",run.getStatus());assertEquals(1,lamp.get());mode="normal";assertEquals("UNKNOWN",adapter.check(run.getId(),OWNER).get("status"));adapter.poll(e,OWNER);adapter.checkEvent(e,OWNER);assertEquals(1,posts.get());}
    @Test void acceptedCommandWithWrongReadbackIsNotSuccess(){mode="mismatch";UUID e=transition();var run=runs.findByOwnerAndEventId(OWNER,e,org.springframework.data.domain.PageRequest.of(0,10)).getContent().get(0);assertEquals("UNKNOWN",run.getStatus());assertEquals(0,lamp.get());lamp.set(1);assertEquals("CONFIRMED",adapter.check(run.getId(),OWNER).get("status"));assertEquals(1,posts.get());}
    @Test void explicitRejectionIsFailure(){mode="reject";UUID e=transition();var run=runs.findByOwnerAndEventId(OWNER,e,org.springframework.data.domain.PageRequest.of(0,10)).getContent().get(0);assertEquals("FAILED",run.getStatus());assertEquals(0,lamp.get());}
    @Test void offlineTargetBlocksExternalCommand(){runtime.setAvailability(target,"OFFLINE",OWNER);transition();assertEquals(0,posts.get());}
    @Test void disabledRuleDoesNotSend(){var rule=catalog.scenario(scenario,OWNER);catalog.update(scenario,new ScenarioWrite(rule.title(),false,"LOCAL",rule.trigger(),rule.action(),rule.version()),OWNER);transition();assertEquals(0,posts.get());}
    @Test void directCommandIsPendingBeforeDispatchAndIsNeverExecutedByJava(){UUID id=UUID.randomUUID();assertEquals("PENDING",runtime.processCommand(target,id,"lamp",1.0,"LOCAL",OWNER).get("status"));assertEquals(0,posts.get());assertTrue(states.findByDeviceIdAndSignalKeyAndOwner(target,"lamp",OWNER).isEmpty());assertEquals("CONFIRMED",adapter.dispatch(id,OWNER).get("status"));adapter.dispatch(id,OWNER);assertEquals(1,posts.get());}
    @Test void concurrentDuplicatePollHasOneAction()throws Exception{adapter.poll(UUID.randomUUID(),OWNER);wet.set(1);UUID id=UUID.randomUUID();try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var a=pool.submit(()->adapter.poll(id,OWNER));var b=pool.submit(()->adapter.poll(id,OWNER));a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}assertEquals(1,posts.get());}
    @Test void operatorUrlCannotPointAtAnArbitraryHost(){config.setLeakUrl(URI.create("http://example.com"));assertEquals("PYTHON_NOT_CONFIGURED",assertThrows(CatalogException.class,()->adapter.poll(UUID.randomUUID(),OWNER)).code);assertEquals(0,reads.get());}

}
