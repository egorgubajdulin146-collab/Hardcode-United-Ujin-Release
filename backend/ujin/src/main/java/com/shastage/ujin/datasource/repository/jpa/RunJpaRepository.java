package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.RunEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface RunJpaRepository extends JpaRepository<RunEntity, UUID> {
    Page<RunEntity> findByOwnerAndEventId(String owner, UUID eventId, Pageable pageable);
    Page<RunEntity> findByOwnerAndScenarioId(String owner, UUID scenarioId, Pageable pageable);
    Page<RunEntity> findByOwnerAndEventIdAndScenarioId(String owner, UUID eventId, UUID scenarioId, Pageable pageable);
    Page<RunEntity> findByOwner(String owner, Pageable pageable);
    Optional<RunEntity> findByIdAndOwner(UUID id, String owner);
    Page<RunEntity> findByEventId(UUID eventId, Pageable pageable);
    java.util.List<RunEntity> findByActionDeviceIdAndOwnerAndStatusIn(
            UUID actionDeviceId, String owner, java.util.Collection<String> statuses);
    java.util.List<RunEntity> findByCommandId(UUID commandId);
    java.util.List<RunEntity> findByScenarioIdAndOwnerAndStatusAndReason(
            UUID scenarioId, String owner, String status, String reason);
}
