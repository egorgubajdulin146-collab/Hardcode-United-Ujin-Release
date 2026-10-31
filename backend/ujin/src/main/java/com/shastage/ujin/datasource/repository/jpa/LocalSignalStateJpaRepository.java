package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.LocalSignalStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LocalSignalStateJpaRepository extends JpaRepository<LocalSignalStateEntity, UUID> {
    List<LocalSignalStateEntity> findByDeviceId(UUID deviceId);
    Optional<LocalSignalStateEntity> findByDeviceIdAndSignalKey(UUID deviceId, String signalKey);
    void deleteByDeviceId(UUID deviceId);
}
