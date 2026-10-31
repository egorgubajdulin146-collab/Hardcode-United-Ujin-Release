package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.ScenarioEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ScenarioJpaRepository extends JpaRepository<ScenarioEntity, UUID> {
    Page<ScenarioEntity> findByOwner(String owner, Pageable pageable);
    Optional<ScenarioEntity> findByIdAndOwner(UUID id, String owner);
    List<ScenarioEntity> findByOwner(String owner);

    @Query("SELECT s.id FROM ScenarioEntity s WHERE (s.triggerDevice.id = :deviceId OR s.actionDevice.id = :deviceId) AND s.owner = :owner")
    List<UUID> findDependenciesByDeviceIdAndOwner(@Param("deviceId") UUID deviceId, @Param("owner") String owner);

    @Query("SELECT s.id FROM ScenarioEntity s WHERE s.triggerDevice.id = :deviceId OR s.actionDevice.id = :deviceId")
    List<UUID> findDependencies(@Param("deviceId") UUID deviceId);
}
