package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.PlatformBindingEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformBindingJpaRepository extends JpaRepository<PlatformBindingEntity, UUID> {
    Optional<PlatformBindingEntity> findByDeviceIdAndOwner(UUID deviceId, String owner);

    void deleteByDeviceIdAndOwner(UUID deviceId, String owner);
}
