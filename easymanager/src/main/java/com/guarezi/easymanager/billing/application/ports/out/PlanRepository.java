package com.guarezi.easymanager.billing.application.ports.out;

import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository {
    Optional<Plan> findById(UUID id);

    // Fase 1.5, Block D: the exchange-rate job recalculates
    // currentMonthTokenQuota for every ADS_ANALYZER plan (the 3 fixed
    // rows, RN-PLAN-003) — PRODUCTS plans have no token quota at all.
    List<Plan> findByModule(Module module);

    // Only the exchange-rate job writes here (RN-TOKEN-002) — see the
    // class-level note on PlanRepositoryPersistenceAdapter.
    Plan save(Plan plan);
}
