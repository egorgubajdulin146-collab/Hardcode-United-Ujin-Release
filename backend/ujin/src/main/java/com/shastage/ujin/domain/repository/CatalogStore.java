package com.shastage.ujin.domain.repository;

import com.shastage.ujin.domain.model.Catalog.*;

import java.util.*;

public interface CatalogStore {
    void lock();

    Optional<Device> device(UUID id, String owner);

    Optional<Scenario> scenario(UUID id, String owner);

    List<Scenario> scenarios(String owner);

    Page<Device> devices(int page, int size, String owner);

    Page<Scenario> scenarios(int page, int size, String owner);

    List<UUID> dependencies(UUID id, String owner);

    Device insert(Device device, String owner);

    Device update(Device device, String owner);

    Scenario insert(Scenario scenario, String owner);

    Scenario update(Scenario scenario, String owner);

    void deleteDevice(UUID id, String owner);
}
