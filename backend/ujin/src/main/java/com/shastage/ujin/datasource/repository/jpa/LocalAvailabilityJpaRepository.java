package com.shastage.ujin.datasource.repository.jpa;
import com.shastage.ujin.datasource.entity.LocalAvailabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface LocalAvailabilityJpaRepository extends JpaRepository<LocalAvailabilityEntity, UUID> {}
