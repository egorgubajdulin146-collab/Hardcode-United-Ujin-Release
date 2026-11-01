package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.DeviceEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DeviceJpaRepository extends JpaRepository<DeviceEntity, UUID> {
    Page<DeviceEntity> findByOwner(String owner, Pageable pageable);
    Optional<DeviceEntity> findByIdAndOwner(UUID id, String owner);
    long countByOwner(String owner);
}
