package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.ScenarioPollEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScenarioPollJpaRepository extends JpaRepository<ScenarioPollEntity, UUID> {
    java.util.List<ScenarioPollEntity> findByRunId(UUID runId);
    Optional<ScenarioPollEntity> findByPollIdAndOwner(UUID pollId, String owner);
}
