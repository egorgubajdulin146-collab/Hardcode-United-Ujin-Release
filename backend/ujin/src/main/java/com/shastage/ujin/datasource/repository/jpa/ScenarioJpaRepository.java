package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.ScenarioEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScenarioJpaRepository extends JpaRepository<ScenarioEntity, UUID> {
    @org.springframework.data.jpa.repository.Query(
            "select s.id from ScenarioEntity s where s.triggerDevice.id=:deviceId or"
                + " s.actionDevice.id=:deviceId order by s.id")
    java.util.List<UUID> findDependencies(
            @org.springframework.data.repository.query.Param("deviceId") UUID deviceId);
}
