package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.domain.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanJpaRepository extends JpaRepository<PlanEntity, UUID> {
    List<PlanEntity> findByModule(Module module);
}
