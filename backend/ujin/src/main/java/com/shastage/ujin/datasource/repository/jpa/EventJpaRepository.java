package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface EventJpaRepository extends JpaRepository<EventEntity, UUID> {
    Optional<EventEntity> findByEventIdAndOwner(UUID eventId, String owner);
}
