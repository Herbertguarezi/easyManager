package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.billing.application.ports.out.ApiPricingRepository;
import com.guarezi.easymanager.billing.application.ports.out.ExchangeRateProvider;
import com.guarezi.easymanager.billing.application.ports.out.ExchangeRateSnapshotRepository;
import com.guarezi.easymanager.billing.application.ports.out.PlanRepository;
import com.guarezi.easymanager.billing.domain.ApiPricing;
import com.guarezi.easymanager.billing.domain.ExchangeRateSnapshot;
import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Plan;
import com.guarezi.easymanager.billing.domain.TokenCostCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Fase 1.5, Block D: RN-TOKEN-001/002/006/007 — the only place
// plans.currentMonthTokenQuota is ever written (PlanRepositoryPersistenceAdapter's
// save() exists for exactly this). Runs once a day; idempotent against
// being triggered more than once on the same calendar day.
@Service
public class ExchangeRateJob {

    private final ExchangeRateSnapshotRepository exchangeRateSnapshotRepository;
    private final ExchangeRateProvider exchangeRateProvider;
    private final ApiPricingRepository apiPricingRepository;
    private final PlanRepository planRepository;

    public ExchangeRateJob(ExchangeRateSnapshotRepository exchangeRateSnapshotRepository,
                            ExchangeRateProvider exchangeRateProvider, ApiPricingRepository apiPricingRepository,
                            PlanRepository planRepository) {
        this.exchangeRateSnapshotRepository = exchangeRateSnapshotRepository;
        this.exchangeRateProvider = exchangeRateProvider;
        this.apiPricingRepository = apiPricingRepository;
        this.planRepository = planRepository;
    }

    public void run() {
        LocalDate today = LocalDate.now();
        if (exchangeRateSnapshotRepository.findByReferenceDate(today).isPresent()) {
            return;
        }

        BigDecimal usdBrlRate = exchangeRateProvider.fetchUsdBrlRate();
        recordSnapshot(today, usdBrlRate);

        ApiPricing pricing = apiPricingRepository.findCurrent()
                .orElseThrow(() -> new IllegalStateException("no api_pricing_history entry to compute token quotas from"));
        BigDecimal costPerTokenUsd = TokenCostCalculator.weightedAverageCostPerTokenUsd(pricing);

        // RN-PLAN-001: only ADS_ANALYZER has a variable, token-based cost;
        // PRODUCTS plans have no quota field to recalculate at all.
        List<Plan> adsAnalyzerPlans = planRepository.findByModule(Module.ADS_ANALYZER);
        for (Plan plan : adsAnalyzerPlans) {
            plan.setCurrentMonthTokenQuota(TokenCostCalculator.monthlyTokenQuota(plan, costPerTokenUsd, usdBrlRate));
            plan.setUpdatedAt(Instant.now());
            planRepository.save(plan);
        }
    }

    private void recordSnapshot(LocalDate today, BigDecimal usdBrlRate) {
        ExchangeRateSnapshot snapshot = new ExchangeRateSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setUsdBrlRate(usdBrlRate);
        snapshot.setReferenceDate(today);
        snapshot.setCreatedAt(Instant.now());
        exchangeRateSnapshotRepository.save(snapshot);
    }
}
