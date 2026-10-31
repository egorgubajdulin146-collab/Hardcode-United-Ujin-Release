package com.shastage.ujin.domain;

import com.shastage.ujin.UjinApplication;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.service.CatalogService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class CatalogRestartTest {
    @TempDir Path directory;
    private ConfigurableApplicationContext start() {
        var application=new SpringApplication(UjinApplication.class);
        application.setRegisterShutdownHook(false);
        return application.run("--server.port=0", "--spring.datasource.url=jdbc:h2:file:"+directory.resolve("catalog").toAbsolutePath().toString().replace('\\','/'),
                "--spring.datasource.username=sa","--spring.datasource.password=");
    }
    @Test void declarationsAndRulesSurviveApplicationRestart() {
        Device source; Device target; Scenario rule;
        var signals=List.of(new Signal("lamp","Lamp","capability","rw","NUMBER","switch",null,0.0,1.0,List.of(0.0,1.0)));
        var events=List.of(new Event("changed","Changed","lamp","CHANGE"));
        try(var context=start()) {
            var service=context.getBean(CatalogService.class);
            source=service.create(new DeviceWrite("Source","light",signals,events,null));
            target=service.create(new DeviceWrite("Target","light",signals,events,null));
            rule=service.create(new ScenarioWrite("Persistent rule",false,"UJIN",new Trigger(source.id(),"lamp","EQ",1.0,"changed"),new Action(target.id(),"lamp",1.0),null));
        }
        try(var context=start()) {
            var service=context.getBean(CatalogService.class);
            assertEquals(source,service.device(source.id()));
            assertEquals(target,service.device(target.id()));
            assertEquals(rule,service.scenario(rule.id()));
            assertEquals(List.of(rule.id()),service.dependencies(target.id()).scenarioIds());
            assertEquals(2,service.update(rule.id(),new ScenarioWrite("Updated rule",true,"LOCAL",rule.trigger(),rule.action(),1L)).version());
        }
    }
}
