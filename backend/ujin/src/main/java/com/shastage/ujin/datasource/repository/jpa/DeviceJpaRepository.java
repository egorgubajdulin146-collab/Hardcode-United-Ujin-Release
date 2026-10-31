package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.DeviceEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceJpaRepository extends JpaRepository<DeviceEntity,UUID> {
}
