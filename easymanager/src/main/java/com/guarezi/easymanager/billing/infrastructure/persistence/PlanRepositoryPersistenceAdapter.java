package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.application.ports.out.PlanRepository;
import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Plan;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Fase 1.5, Block D: save()/findByModule() exist only for the daily
// exchange-rate job (RN-TOKEN-002) — nothing else writes to `plans`.
@Component
public class PlanRepositoryPersistenceAdapter implements PlanRepository {

    private final PlanJpaRepository repository;

    public PlanRepositoryPersistenceAdapter(PlanJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Plan> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Plan> findByModule(Module module) {
        return repository.findByModule(module).stream().map(this::toDomain).toList();
    }

    @Override
    public Plan save(Plan plan) {
        return toDomain(repository.save(toEntity(plan)));
    }

    private PlanEntity toEntity(Plan plan) {
        if (plan == null) return null;

        PlanEntity entity = new PlanEntity();
        entity.setId(plan.getId());
        entity.setModule(plan.getModule());
        entity.setName(plan.getName());
        entity.setStoresLimit(plan.getStoresLimit());
        entity.setMonthlyPrice(plan.getMonthlyPrice());
        entity.setTargetMargin(plan.getTargetMargin());
        entity.setCurrentMonthTokenQuota(plan.getCurrentMonthTokenQuota());
        entity.setCreatedAt(plan.getCreatedAt());
        entity.setUpdatedAt(plan.getUpdatedAt());
        return entity;
    }

    private Plan toDomain(PlanEntity entity) {
        if (entity == null) return null;

        Plan plan = new Plan();
        plan.setId(entity.getId());
        plan.setModule(entity.getModule());
        plan.setName(entity.getName());
        plan.setStoresLimit(entity.getStoresLimit());
        plan.setMonthlyPrice(entity.getMonthlyPrice());
        plan.setTargetMargin(entity.getTargetMargin());
        plan.setCurrentMonthTokenQuota(entity.getCurrentMonthTokenQuota());
        plan.setCreatedAt(entity.getCreatedAt());
        plan.setUpdatedAt(entity.getUpdatedAt());
        return plan;
    }
}
