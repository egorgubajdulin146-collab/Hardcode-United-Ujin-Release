package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.ScenarioObservationEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScenarioObservationJpaRepository
        extends JpaRepository<ScenarioObservationEntity, UUID> {
    Optional<ScenarioObservationEntity> findByScenarioIdAndOwner(UUID scenarioId, String owner);
}
