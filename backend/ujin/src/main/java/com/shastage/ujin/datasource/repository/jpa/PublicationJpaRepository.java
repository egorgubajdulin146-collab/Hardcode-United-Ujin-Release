package com.shastage.ujin.datasource.repository.jpa;
import com.shastage.ujin.datasource.entity.PublicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PublicationJpaRepository extends JpaRepository<PublicationEntity, UUID> {
    List<PublicationEntity> findTop20ByDeviceIdAndOwnerOrderByCreatedAtDesc(UUID deviceId, String owner);
    List<PublicationEntity> findByDeviceIdAndOwnerAndStatusIn(UUID deviceId, String owner, Collection<String> statuses);
    boolean existsByDeviceIdAndOwnerAndSignalKeyAndStatusIn(UUID deviceId, String owner, String signalKey, Collection<String> statuses);
}
