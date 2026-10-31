package com.shastage.ujin.datasource.repository;

import com.shastage.ujin.datasource.entity.*;
import com.shastage.ujin.datasource.mapper.CatalogEntityMapper;
import com.shastage.ujin.datasource.repository.jpa.*;
import com.shastage.ujin.domain.model.Catalog.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository implements com.shastage.ujin.domain.repository.CatalogStore {
    private final DeviceJpaRepository devices;
    private final ScenarioJpaRepository scenarios;
    private final CatalogLockJpaRepository locks;
    private final CatalogEntityMapper mapper;
    public CatalogRepository(DeviceJpaRepository devices,ScenarioJpaRepository scenarios,CatalogLockJpaRepository locks,CatalogEntityMapper mapper) {
        this.devices=devices; this.scenarios=scenarios; this.locks=locks; this.mapper=mapper;
    }
    public void lock() { locks.lockCatalog(); }
    public Optional<Device> device(UUID id) { return devices.findById(id).map(mapper::toDomain); }
    public Optional<Scenario> scenario(UUID id) { return scenarios.findById(id).map(mapper::toDomain); }
    public List<Scenario> scenarios() { return scenarios.findAll(Sort.by("id")).stream().map(mapper::toDomain).toList(); }
    public com.shastage.ujin.domain.model.Catalog.Page<Device> devices(int page,int size) {
        var result=devices.findAll(PageRequest.of(page,size,Sort.by("id")));
        return new com.shastage.ujin.domain.model.Catalog.Page<>(result.getContent().stream().map(mapper::toDomain).toList(),page,size,result.getTotalElements());
    }
    public com.shastage.ujin.domain.model.Catalog.Page<Scenario> scenarios(int page,int size) {
        var result=scenarios.findAll(PageRequest.of(page,size,Sort.by("id")));
        return new com.shastage.ujin.domain.model.Catalog.Page<>(result.getContent().stream().map(mapper::toDomain).toList(),page,size,result.getTotalElements());
    }
    public List<UUID> dependencies(UUID id) { return scenarios.findDependencies(id); }
    public Device insert(Device device) {
        var entity=new DeviceEntity(); mapper.update(entity,device); return mapper.toDomain(devices.saveAndFlush(entity));
    }
    public Device update(Device device) {
        var entity=devices.findById(device.id()).orElseThrow(); mapper.update(entity,device); return mapper.toDomain(devices.saveAndFlush(entity));
    }
    public Scenario insert(Scenario scenario) {
        var entity=new ScenarioEntity(); apply(entity,scenario); return mapper.toDomain(scenarios.saveAndFlush(entity));
    }
    public Scenario update(Scenario scenario) {
        var entity=scenarios.findById(scenario.id()).orElseThrow(); apply(entity,scenario); return mapper.toDomain(scenarios.saveAndFlush(entity));
    }
    private void apply(ScenarioEntity entity,Scenario scenario) {
        mapper.update(entity,scenario,devices.getReferenceById(scenario.trigger().deviceId()),devices.getReferenceById(scenario.action().deviceId()));
    }
    public void deleteDevice(UUID id) { devices.deleteById(id); devices.flush(); }
}
