package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.RunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RunJpaRepository extends JpaRepository<RunEntity, UUID> {
    Page<RunEntity> findByEventId(UUID eventId, Pageable pageable);
    Page<RunEntity> findByScenarioId(UUID scenarioId, Pageable pageable);
    Page<RunEntity> findByEventIdAndScenarioId(UUID eventId, UUID scenarioId, Pageable pageable);
}
