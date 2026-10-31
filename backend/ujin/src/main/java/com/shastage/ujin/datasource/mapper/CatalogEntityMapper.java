package com.shastage.ujin.datasource.mapper;

import com.shastage.ujin.datasource.entity.*;
import com.shastage.ujin.domain.model.Catalog.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CatalogEntityMapper {
    public Device toDomain(DeviceEntity entity) {
        return new Device(entity.getId(),entity.getVersion()+1,entity.getTitle(),entity.getFamily(),
                entity.getSignals().stream().map(this::toDomain).toList(),
                entity.getEvents().stream().map(e -> new Event(e.getKey(),e.getTitle(),e.getSignalKey(),e.getEmission())).toList(),entity.getPlatformLink());
    }
    private Signal toDomain(SignalEntity s) {
        return new Signal(s.getKey(),s.getTitle(),s.getGroup(),s.getManagement(),s.getValueType(),s.getControl(),s.getUnit(),s.getMin(),s.getMax(),
                s.getEnumValues().isEmpty()?null:List.copyOf(s.getEnumValues()));
    }
    public void update(DeviceEntity entity,Device device) {
        entity.setId(device.id()); entity.setTitle(device.title()); entity.setFamily(device.family()); entity.setPlatformLink(device.platformLink());
        if (entity.getSignals()==null) entity.setSignals(new ArrayList<>());
        entity.getSignals().clear();
        for (var s:device.signals()) {
            var signal=new SignalEntity(); signal.setId(UUID.randomUUID()); signal.setKey(s.key()); signal.setTitle(s.title());
            signal.setGroup(s.group()); signal.setManagement(s.management()); signal.setValueType(s.valueType()); signal.setControl(s.control());
            signal.setUnit(s.unit()); signal.setMin(s.min()); signal.setMax(s.max());
            signal.setEnumValues(new ArrayList<>(s.enumValues()==null?List.of():s.enumValues())); entity.getSignals().add(signal);
        }
        if (entity.getEvents()==null) entity.setEvents(new ArrayList<>());
        entity.getEvents().clear();
        for(var e:device.events()) {
            var event=new EventDefinition(); event.setKey(e.key()); event.setTitle(e.title()); event.setSignalKey(e.signalKey()); event.setEmission(e.emission());
            entity.getEvents().add(event);
        }
    }
    public Scenario toDomain(ScenarioEntity entity) {
        return new Scenario(entity.getId(),entity.getVersion()+1,entity.getTitle(),entity.getEnabled(),entity.getExecutionMode(),
                new Trigger(entity.getTriggerDevice().getId(),entity.getTriggerSignal(),entity.getOperator(),entity.getTriggerValue(),entity.getTriggerEvent()),
                new Action(entity.getActionDevice().getId(),entity.getActionSignal(),entity.getActionValue()));
    }
    public void update(ScenarioEntity entity,Scenario scenario,DeviceEntity source,DeviceEntity target) {
        entity.setId(scenario.id()); entity.setTitle(scenario.title()); entity.setEnabled(scenario.enabled()); entity.setExecutionMode(scenario.executionMode());
        entity.setTriggerDevice(source); entity.setTriggerSignal(scenario.trigger().signalKey()); entity.setTriggerEvent(scenario.trigger().eventKey());
        entity.setOperator(scenario.trigger().operator()); entity.setTriggerValue(scenario.trigger().value());
        entity.setActionDevice(target); entity.setActionSignal(scenario.action().signalKey()); entity.setActionValue(scenario.action().value());
    }
}
