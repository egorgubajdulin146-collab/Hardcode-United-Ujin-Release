package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.CommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CommandJpaRepository extends JpaRepository<CommandEntity, UUID> {
    Optional<CommandEntity> findByCommandIdAndOwner(UUID commandId, String owner);
    boolean existsByCommandId(UUID commandId);
}
