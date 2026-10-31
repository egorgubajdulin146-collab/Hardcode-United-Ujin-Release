package com.shastage.ujin.datasource.repository.jpa;

import com.shastage.ujin.datasource.entity.CommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CommandJpaRepository extends JpaRepository<CommandEntity, UUID> {
}
