package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.TaskAckEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaskAckJpaRepository extends JpaRepository<TaskAckEntity, TaskAckEntity.TaskAckId> {
    Optional<TaskAckEntity> findByIdDeviceIdAndIdSignalKey(UUID deviceId, String signalKey);
}
