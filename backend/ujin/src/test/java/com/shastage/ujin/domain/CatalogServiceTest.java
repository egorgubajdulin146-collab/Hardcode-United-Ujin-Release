package com.shastage.ujin.domain;

import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.service.CatalogService;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.datasource.repository.CatalogRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.shastage.ujin.datasource.repository.jpa.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CatalogServiceTest {
    @Autowired CatalogService service;
    @Autowired DeviceJpaRepository devices;
    @Autowired ScenarioJpaRepository scenarios;
    @Autowired CatalogRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void reset() { scenarios.deleteAll(); devices.deleteAll(); }
    DeviceWrite declaration(String title, Long version) {
        return new DeviceWrite(title,"light",List.of(new Signal("lamp","Lamp","capability","rw","NUMBER","switch",null,0.0,1.0,List.of(0.0,1.0))),
                List.of(new Event("lamp_changed","Changed","lamp","CHANGE")),version);
    }
    ScenarioWrite rule(Device source, Device target, boolean enabled, Long version) {
        return new ScenarioWrite("Rule",enabled,"UJIN",new Trigger(source.id(),"lamp","EQ",1.0,"lamp_changed"),new Action(target.id(),"lamp",1.0),version);
    }
    @Test void storesAndReloadsDeviceAndScenarioFromDatabase() {
        var source = service.create(declaration("Source",null));
        var target = service.create(declaration("Target",null));
        var scenario = service.create(rule(source,target,false,null));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertEquals(source,repository.device(source.id()).orElseThrow());
            assertEquals(scenario,repository.scenario(scenario.id()).orElseThrow());
        });
        assertEquals(2,service.devices(0,1).total());
        assertEquals(1,service.devices(0,1).items().size());
        assertThrows(CatalogException.class,()->service.devices(Integer.MAX_VALUE,100));
        assertEquals(422,assertThrows(CatalogException.class,()->service.devices(-1,20)).status);
        assertEquals(2,service.update(source.id(),declaration("Renamed",1L)).version());
        assertEquals("VERSION_CONFLICT",assertThrows(CatalogException.class,()->service.update(source.id(),declaration("Stale",1L))).code);
        assertEquals(2,service.update(scenario.id(),rule(source,target,true,1L)).version());
        assertEquals("DEVICE_IN_USE",assertThrows(CatalogException.class,()->service.delete(target.id(),1)).code);
        assertEquals(List.of(scenario.id()),service.dependencies(source.id()).scenarioIds());
    }
    @Test void rejectsInvalidDeclarationsAndReferences() {
        var valid = declaration("Test",null);
        assertThrows(CatalogException.class,()->service.create(new DeviceWrite(" ","light",valid.signals(),valid.events(),null)));
        assertThrows(CatalogException.class,()->service.create(new DeviceWrite("Test","light",List.of(valid.signals().getFirst(),valid.signals().getFirst()),valid.events(),null)));
        assertThrows(CatalogException.class,()->service.create(new DeviceWrite("Test","light",valid.signals(),List.of(new Event("change","Change","absent","CHANGE")),null)));
        assertEquals(0,service.devices(0,20).total());
        var source = service.create(valid); var target = service.create(declaration("Target",null));
        assertThrows(CatalogException.class,()->service.create(new ScenarioWrite("Rule",true,"UJIN",new Trigger(source.id(),"lamp","EQ",2.0,"lamp_changed"),new Action(target.id(),"lamp",1.0),null)));
        service.create(rule(source,target,false,null));
        assertEquals("SCENARIO_CYCLE",assertThrows(CatalogException.class,()->service.create(rule(target,source,true,null))).code);
        assertThrows(CatalogException.class,()->service.update(source.id(),new DeviceWrite("Test","changed",valid.signals(),valid.events(),1L)));
    }
    @Test void competingUpdatesHaveOnlyOneWinner() throws Exception {
        var device=service.create(declaration("Test",null));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> update=()->{ start.await(); try { service.update(device.id(),declaration("Changed",1L)); return true; }
                catch(CatalogException error) { assertEquals("VERSION_CONFLICT",error.code); return false; } };
            var first=executor.submit(update); var second=executor.submit(update); start.countDown();
            assertNotEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
        }
        assertEquals(2,service.device(device.id()).version());
        service.delete(device.id(),2);
        assertEquals(404,assertThrows(CatalogException.class,()->service.device(device.id())).status);
    }
    @Test void deletionRacingScenarioCreationCannotLeaveDanglingReferences() throws Exception {
        var source=service.create(declaration("Source",null));
        var target=service.create(declaration("Target",null));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var created=executor.submit(()->{ start.await(); try { service.create(rule(source,target,false,null)); return 201; }
                catch(CatalogException error) { return error.status; } });
            var deleted=executor.submit(()->{ start.await(); try { service.delete(target.id(),1); return 204; }
                catch(CatalogException error) { return error.status; } });
            start.countDown();
            int createStatus=created.get(10,TimeUnit.SECONDS); int deleteStatus=deleted.get(10,TimeUnit.SECONDS);
            assertTrue((createStatus==201 && deleteStatus==409) || (createStatus==404 && deleteStatus==204));
            if(createStatus==201) assertEquals(1,service.dependencies(target.id()).scenarioIds().size());
            else assertTrue(service.scenarios(0,20).items().isEmpty());
        }
    }

}
