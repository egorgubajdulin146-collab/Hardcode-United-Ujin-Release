package com.shastage.ujin.domain.repository;

import com.shastage.ujin.domain.model.Catalog.*;

import java.util.*;

public interface CatalogStore {
    void lock();

    Optional<Device> device(UUID id);

    Optional<Scenario> scenario(UUID id);

    List<Scenario> scenarios();

    Page<Device> devices(int page, int size);

    Page<Scenario> scenarios(int page, int size);

    List<UUID> dependencies(UUID id);

    Device insert(Device device);

    Device update(Device device);

    Scenario insert(Scenario scenario);

    Scenario update(Scenario scenario);

    void deleteDevice(UUID id);
}
