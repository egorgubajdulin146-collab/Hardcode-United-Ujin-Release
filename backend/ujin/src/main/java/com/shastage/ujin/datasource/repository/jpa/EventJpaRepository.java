package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EventJpaRepository extends JpaRepository<EventEntity, UUID> {
}
