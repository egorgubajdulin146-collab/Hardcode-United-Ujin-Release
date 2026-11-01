package com.shastage.ujin.datasource.repository;

import com.shastage.ujin.datasource.entity.*;
import com.shastage.ujin.datasource.mapper.CatalogEntityMapper;
import com.shastage.ujin.datasource.repository.jpa.*;
import com.shastage.ujin.domain.model.Catalog.*;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class CatalogRepository implements com.shastage.ujin.domain.repository.CatalogStore {
    private final DeviceJpaRepository devices;
    private final ScenarioJpaRepository scenarios;
    private final CatalogLockJpaRepository locks;
    private final CatalogEntityMapper mapper;

    public CatalogRepository(
            DeviceJpaRepository devices,
            ScenarioJpaRepository scenarios,
            CatalogLockJpaRepository locks,
            CatalogEntityMapper mapper) {
        this.devices = devices;
        this.scenarios = scenarios;
        this.locks = locks;
        this.mapper = mapper;
    }

    public void lock() {
        locks.lockCatalog();
    }

    public Optional<Device> device(UUID id, String owner) {
        return devices.findByIdAndOwner(id, owner).map(mapper::toDomain);
    }

    public Optional<Scenario> scenario(UUID id, String owner) {
        return scenarios.findByIdAndOwner(id, owner).map(mapper::toDomain);
    }

    public List<Scenario> scenarios(String owner) {
        return scenarios.findByOwner(owner).stream().map(mapper::toDomain).toList();
    }

    public com.shastage.ujin.domain.model.Catalog.Page<Device> devices(int page, int size, String owner) {
        var result = devices.findByOwner(owner, PageRequest.of(page, size, Sort.by("id")));
        return new com.shastage.ujin.domain.model.Catalog.Page<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                page,
                size,
                result.getTotalElements());
    }

    public com.shastage.ujin.domain.model.Catalog.Page<Scenario> scenarios(int page, int size, String owner) {
        var result = scenarios.findByOwner(owner, PageRequest.of(page, size, Sort.by("id")));
        return new com.shastage.ujin.domain.model.Catalog.Page<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                page,
                size,
                result.getTotalElements());
    }

    public List<UUID> dependencies(UUID id, String owner) {
        return scenarios.findDependenciesByDeviceIdAndOwner(id, owner);
    }

    public Device insert(Device device, String owner) {
        var entity = new DeviceEntity();
        mapper.update(entity, device);
        entity.setOwner(owner);
        return mapper.toDomain(devices.saveAndFlush(entity));
    }

    public Device update(Device device, String owner) {
        var entity = devices.findByIdAndOwner(device.id(), owner).orElseThrow();
        mapper.update(entity, device);
        return mapper.toDomain(devices.saveAndFlush(entity));
    }

    public Scenario insert(Scenario scenario, String owner) {
        var entity = new ScenarioEntity();
        apply(entity, scenario);
        entity.setOwner(owner);
        return mapper.toDomain(scenarios.saveAndFlush(entity));
    }

    public Scenario update(Scenario scenario, String owner) {
        var entity = scenarios.findByIdAndOwner(scenario.id(), owner).orElseThrow();
        apply(entity, scenario);
        return mapper.toDomain(scenarios.saveAndFlush(entity));
    }

    private void apply(ScenarioEntity entity, Scenario scenario) {
        mapper.update(
                entity,
                scenario,
                devices.getReferenceById(scenario.trigger().deviceId()),
                devices.getReferenceById(scenario.action().deviceId()));
    }

    public void deleteDevice(UUID id, String owner) {
        var entity = devices.findByIdAndOwner(id, owner).orElseThrow();
        devices.delete(entity);
        devices.flush();
    }

    public void setPlatformLink(UUID id, String platformLink, String owner) {
        var entity = devices.findByIdAndOwner(id, owner).orElseThrow();
        entity.setPlatformLink(platformLink);
        devices.saveAndFlush(entity);
    }
}
