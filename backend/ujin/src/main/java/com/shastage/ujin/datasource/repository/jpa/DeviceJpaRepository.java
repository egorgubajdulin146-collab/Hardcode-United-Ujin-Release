package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.DeviceEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceJpaRepository extends JpaRepository<DeviceEntity, UUID> {}
