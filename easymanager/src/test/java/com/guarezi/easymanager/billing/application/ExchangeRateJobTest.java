package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.billing.application.ports.out.ApiPricingRepository;
import com.guarezi.easymanager.billing.application.ports.out.ExchangeRateProvider;
import com.guarezi.easymanager.billing.application.ports.out.ExchangeRateSnapshotRepository;
import com.guarezi.easymanager.billing.application.ports.out.PlanRepository;
import com.guarezi.easymanager.billing.domain.ApiPricing;
import com.guarezi.easymanager.billing.domain.ExchangeRateSnapshot;
import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Fase 1.5, Block D: RN-TOKEN-001/002/006/007.
class ExchangeRateJobTest {

    @Mock
    private ExchangeRateSnapshotRepository exchangeRateSnapshotRepository;
    @Mock
    private ExchangeRateProvider exchangeRateProvider;
    @Mock
    private ApiPricingRepository apiPricingRepository;
    @Mock
    private PlanRepository planRepository;

    private ExchangeRateJob job;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        job = new ExchangeRateJob(exchangeRateSnapshotRepository, exchangeRateProvider, apiPricingRepository, planRepository);
        when(exchangeRateSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void doesNothingWhenTodaysSnapshotAlreadyExists() {
        when(exchangeRateSnapshotRepository.findByReferenceDate(LocalDate.now()))
                .thenReturn(Optional.of(new ExchangeRateSnapshot(UUID.randomUUID(), new BigDecimal("5.00"), LocalDate.now(), Instant.now())));

        job.run();

        verify(exchangeRateProvider, never()).fetchUsdBrlRate();
        verify(planRepository, never()).save(any());
    }

    @Test
    void savesTodaysSnapshotAndRecalculatesEveryAdsAnalyzerPlanQuota() {
        when(exchangeRateSnapshotRepository.findByReferenceDate(LocalDate.now())).thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchUsdBrlRate()).thenReturn(new BigDecimal("5.0000"));
        when(apiPricingRepository.findCurrent()).thenReturn(Optional.of(apiPricing("3.0000", "15.0000")));

        Plan basico = plan("39.00", "0.7300");
        Plan completo = plan("149.00", "0.5400");
        when(planRepository.findByModule(Module.ADS_ANALYZER)).thenReturn(List.of(basico, completo));

        job.run();

        ArgumentCaptor<ExchangeRateSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ExchangeRateSnapshot.class);
        verify(exchangeRateSnapshotRepository).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().getUsdBrlRate()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(snapshotCaptor.getValue().getReferenceDate()).isEqualTo(LocalDate.now());

        verify(planRepository, times(2)).save(any());
        assertThat(basico.getCurrentMonthTokenQuota()).isNotNull().isPositive();
        assertThat(completo.getCurrentMonthTokenQuota()).isNotNull().isPositive();
        // Completo has a lower target margin (54% vs 73%) but a much
        // higher monthly price (149 vs 39) — its max cost ceiling, and
        // therefore quota, is still higher.
        assertThat(completo.getCurrentMonthTokenQuota()).isGreaterThan(basico.getCurrentMonthTokenQuota());
    }

    private ApiPricing apiPricing(String inputPrice, String outputPrice) {
        ApiPricing pricing = new ApiPricing();
        pricing.setId(UUID.randomUUID());
        pricing.setInputPriceUsdPerMillion(new BigDecimal(inputPrice));
        pricing.setOutputPriceUsdPerMillion(new BigDecimal(outputPrice));
        pricing.setEffectiveFrom(Instant.now());
        pricing.setCreatedAt(Instant.now());
        return pricing;
    }

    private Plan plan(String monthlyPrice, String targetMargin) {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setModule(Module.ADS_ANALYZER);
        plan.setName("plan");
        plan.setStoresLimit(1);
        plan.setMonthlyPrice(new BigDecimal(monthlyPrice));
        plan.setTargetMargin(new BigDecimal(targetMargin));
        plan.setUpdatedAt(Instant.now());
        return plan;
    }
}
