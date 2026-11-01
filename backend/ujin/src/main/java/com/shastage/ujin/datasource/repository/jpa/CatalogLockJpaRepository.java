package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.CatalogLockEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

public interface CatalogLockJpaRepository extends JpaRepository<CatalogLockEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CatalogLockEntity c where c.id=1")
    CatalogLockEntity lockCatalog();
}
