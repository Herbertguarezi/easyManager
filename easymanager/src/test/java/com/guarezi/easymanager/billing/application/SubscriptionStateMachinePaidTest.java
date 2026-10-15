package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.billing.application.ports.out.SubscriptionRepository;
import com.guarezi.easymanager.billing.domain.BillingCycle;
import com.guarezi.easymanager.billing.domain.InvalidSubscriptionTransitionException;
import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Subscription;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Fase 1.5, Block B, checklist item 3: paid-subscription transitions
// (1st charge, upgrade/downgrade only on the monthly cycle RN-SUB-004,
// irreversible cancellation, right of withdrawal with immediate cutoff).
class SubscriptionStateMachinePaidTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    private SubscriptionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        stateMachine = new SubscriptionStateMachine(subscriptionRepository);
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startPaidSubscriptionDirectlyCreatesAnActiveRowWithThePlanAndCycle() {
        UUID sellerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS)).thenReturn(Optional.empty());

        Subscription subscription = stateMachine.startPaidSubscriptionDirectly(sellerId, Module.PRODUCTS, planId, BillingCycle.MONTHLY);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getPlanId()).isEqualTo(planId);
        assertThat(subscription.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(subscription.getAnniversaryDate()).isEqualTo(LocalDate.now());
        assertThat(subscription.getCurrentCycleStartDate()).isEqualTo(LocalDate.now());
        assertThat(subscription.getCurrentCycleEndDate()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    void startPaidSubscriptionDirectlySetsTheTokenPeriodOnlyForAdsAnalyzer() {
        // RN-TOKEN-005: the monthly token period is independent of the
        // billing cycle length — needed by EntitlementService.getTokenBalance
        // (Fase 1.5 Block C) to filter token_usage/token_packages by the
        // right reference_cycle. PRODUCTS has no token concept at all.
        UUID sellerId = UUID.randomUUID();
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER)).thenReturn(Optional.empty());

        Subscription subscription = stateMachine.startPaidSubscriptionDirectly(
                sellerId, Module.ADS_ANALYZER, UUID.randomUUID(), BillingCycle.ANNUAL);

        assertThat(subscription.getCurrentTokenPeriodStartDate()).isEqualTo(LocalDate.now());
        assertThat(subscription.getCurrentTokenPeriodEndDate()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    void startPaidSubscriptionDirectlyLeavesTokenPeriodNullForProducts() {
        UUID sellerId = UUID.randomUUID();
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS)).thenReturn(Optional.empty());

        Subscription subscription = stateMachine.startPaidSubscriptionDirectly(
                sellerId, Module.PRODUCTS, UUID.randomUUID(), BillingCycle.MONTHLY);

        assertThat(subscription.getCurrentTokenPeriodStartDate()).isNull();
        assertThat(subscription.getCurrentTokenPeriodEndDate()).isNull();
    }

    @Test
    void startPaidSubscriptionDirectlyRejectsWhenASubscriptionAlreadyExists() {
        UUID sellerId = UUID.randomUUID();
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS))
                .thenReturn(Optional.of(activeSubscription(BillingCycle.MONTHLY)));

        assertThatThrownBy(() -> stateMachine.startPaidSubscriptionDirectly(
                sellerId, Module.PRODUCTS, UUID.randomUUID(), BillingCycle.MONTHLY))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void convertTrialToPaidSubscriptionMovesToActiveWithThePlanAndCycle() {
        Subscription subscription = trialSubscription();
        UUID planId = UUID.randomUUID();

        Subscription result = stateMachine.convertTrialToPaidSubscription(subscription, planId, BillingCycle.MONTHLY);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getPlanId()).isEqualTo(planId);
        assertThat(result.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
    }

    @Test
    void changePlanUpdatesThePlanOnTheMonthlyCycle() {
        Subscription subscription = activeSubscription(BillingCycle.MONTHLY);
        UUID newPlanId = UUID.randomUUID();

        Subscription result = stateMachine.changePlan(subscription, newPlanId);

        assertThat(result.getPlanId()).isEqualTo(newPlanId);
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void changePlanRejectsOutsideTheMonthlyCycle() {
        // RN-SUB-004: plan/cycle changes are locked once a longer cycle
        // (quarterly/semiannual/annual) has been chosen, until renewal.
        Subscription subscription = activeSubscription(BillingCycle.ANNUAL);

        assertThatThrownBy(() -> stateMachine.changePlan(subscription, UUID.randomUUID()))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void requestCancellationMovesFromActiveToCancellationRequestedAndStampsTheRequestTime() {
        Subscription subscription = activeSubscription(BillingCycle.MONTHLY);

        Subscription result = stateMachine.requestCancellation(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.CANCELLATION_REQUESTED);
        assertThat(result.getCancellationRequestedAt()).isNotNull();
    }

    @Test
    void withdrawWithinSevenDaysClearsThePlanAndCutsAccessImmediately() {
        Subscription subscription = activeSubscription(BillingCycle.MONTHLY);
        subscription.setCreatedAt(Instant.now().minus(3, ChronoUnit.DAYS));

        Subscription result = stateMachine.withdraw(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.WITHDRAWN);
        assertThat(result.getPlanId()).isNull();
    }

    @Test
    void withdrawRejectsAfterSevenDays() {
        // RN-PAY-002: 7 dias corridos.
        Subscription subscription = activeSubscription(BillingCycle.MONTHLY);
        subscription.setCreatedAt(Instant.now().minus(8, ChronoUnit.DAYS));

        assertThatThrownBy(() -> stateMachine.withdraw(subscription))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void resubscribeAfterWithdrawalMovesToActiveWithANewPlanAndCycle() {
        // Business decision confirmed after the Block B review
        // (regras-negocio.md sec. 13.3): same rule as SEM_ASSINATURA ->
        // ATIVO, no penalty for having withdrawn before.
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(UUID.randomUUID());
        subscription.setModule(Module.ADS_ANALYZER);
        subscription.setStatus(SubscriptionStatus.WITHDRAWN);
        subscription.setCreatedAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        UUID planId = UUID.randomUUID();

        Subscription result = stateMachine.resubscribeAfterWithdrawal(subscription, planId, BillingCycle.MONTHLY);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getPlanId()).isEqualTo(planId);
    }

    private Subscription trialSubscription() {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(UUID.randomUUID());
        subscription.setModule(Module.ADS_ANALYZER);
        subscription.setStatus(SubscriptionStatus.TRIAL_ACTIVE);
        subscription.setCreatedAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        return subscription;
    }

    private Subscription activeSubscription(BillingCycle cycle) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(UUID.randomUUID());
        subscription.setModule(Module.PRODUCTS);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPlanId(UUID.randomUUID());
        subscription.setBillingCycle(cycle);
        subscription.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        subscription.setUpdatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        return subscription;
    }
}
