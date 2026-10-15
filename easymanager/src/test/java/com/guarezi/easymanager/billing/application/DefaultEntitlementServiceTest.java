package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.Role;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.auth.domain.SellerStatus;
import com.guarezi.easymanager.billing.application.ports.out.PlanRepository;
import com.guarezi.easymanager.billing.application.ports.out.SubscriptionRepository;
import com.guarezi.easymanager.billing.application.ports.out.TokenLedgerRepository;
import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Plan;
import com.guarezi.easymanager.billing.domain.Subscription;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// Fase 1.5, Block C: EntitlementService, scoped per PROGRESS.md (no
// Store-related methods — Fase 2 — and canExecuteAnalysis without the
// Store-active guard — Fase 7). See the interface javadoc and PROGRESS.md
// decisions log for every deviation from software-engineering.md sec. 4.6.
class DefaultEntitlementServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private TokenLedgerRepository tokenLedgerRepository;
    @Mock
    private SellerRepository sellerRepository;

    private DefaultEntitlementService entitlementService;
    private final UUID sellerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        entitlementService = new DefaultEntitlementService(
                subscriptionRepository, planRepository, tokenLedgerRepository, sellerRepository);
    }

    // --- canUseModule / getSubscriptionStatus / isInTrial / isPastDue / isInRetention ---

    @Test
    void canUseModuleIsFalseWhenThereIsNoSubscription() {
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS)).thenReturn(Optional.empty());

        assertThat(entitlementService.canUseModule(sellerId, Module.PRODUCTS)).isFalse();
        assertThat(entitlementService.getSubscriptionStatus(sellerId, Module.PRODUCTS)).isEmpty();
    }

    @Test
    void canUseModuleIsTrueForActivePastDueAndCancellationRequested() {
        for (SubscriptionStatus status : new SubscriptionStatus[]{
                SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE, SubscriptionStatus.CANCELLATION_REQUESTED,
                SubscriptionStatus.TRIAL_ACTIVE}) {
            when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS))
                    .thenReturn(Optional.of(subscription(status, Module.PRODUCTS, null)));

            assertThat(entitlementService.canUseModule(sellerId, Module.PRODUCTS)).isTrue();
        }
    }

    @Test
    void canUseModuleIsFalseForRestrictedExhaustedRetentionDeletedAndWithdrawn() {
        for (SubscriptionStatus status : new SubscriptionStatus[]{
                SubscriptionStatus.TRIAL_RESTRICTED, SubscriptionStatus.TRIAL_EXHAUSTED, SubscriptionStatus.RETENTION,
                SubscriptionStatus.DELETED, SubscriptionStatus.WITHDRAWN}) {
            when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS))
                    .thenReturn(Optional.of(subscription(status, Module.PRODUCTS, null)));

            assertThat(entitlementService.canUseModule(sellerId, Module.PRODUCTS)).isFalse();
        }
    }

    @Test
    void isInTrialIsTrueForAnyTrialSubState() {
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.TRIAL_RESTRICTED, Module.ADS_ANALYZER, null)));

        assertThat(entitlementService.isInTrial(sellerId, Module.ADS_ANALYZER)).isTrue();
    }

    @Test
    void isPastDueAndIsInRetentionReadTheStatusDirectly() {
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.PAST_DUE, Module.PRODUCTS, null)));
        assertThat(entitlementService.isPastDue(sellerId, Module.PRODUCTS)).isTrue();
        assertThat(entitlementService.isInRetention(sellerId, Module.PRODUCTS)).isFalse();
    }

    // --- getStoreLimit ---

    @Test
    void getStoreLimitIsOneDuringTrial() {
        // regras-negocio.md: RN-PROD-002 states it explicitly for
        // PRODUCTS; applied the same way to ADS_ANALYZER trial, which has
        // no documented limit of its own either.
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.TRIAL_ACTIVE, Module.PRODUCTS, null)));

        assertThat(entitlementService.getStoreLimit(sellerId, Module.PRODUCTS)).isEqualTo(1);
    }

    @Test
    void getStoreLimitReadsThePlanWhenOnAPaidSubscription() {
        UUID planId = UUID.randomUUID();
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE, Module.PRODUCTS, planId)));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(Module.PRODUCTS, 5, null)));

        assertThat(entitlementService.getStoreLimit(sellerId, Module.PRODUCTS)).isEqualTo(5);
    }

    @Test
    void getStoreLimitIsZeroWhenThereIsNoSubscription() {
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS)).thenReturn(Optional.empty());

        assertThat(entitlementService.getStoreLimit(sellerId, Module.PRODUCTS)).isZero();
    }

    // --- getTokenBalance ---

    @Test
    void getTokenBalanceDuringTrialUsesTheIsolatedSixtyThousandPool() {
        // RN-TRIAL-004: isolated from any paid-cycle calculation.
        Subscription subscription = subscription(SubscriptionStatus.TRIAL_ACTIVE, Module.ADS_ANALYZER, null);
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER)).thenReturn(Optional.of(subscription));
        when(tokenLedgerRepository.sumAllTokensUsed(subscription.getId())).thenReturn(18500L);

        assertThat(entitlementService.getTokenBalance(sellerId)).isEqualTo(60000 - 18500);
    }

    @Test
    void getTokenBalanceOnAPaidPlanSumsQuotaPlusPackagesMinusUsageForTheCurrentCycle() {
        UUID planId = UUID.randomUUID();
        LocalDate cycle = LocalDate.now();
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, Module.ADS_ANALYZER, planId);
        subscription.setCurrentTokenPeriodStartDate(cycle);
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(Module.ADS_ANALYZER, 1, 500_000L)));
        when(tokenLedgerRepository.sumTokensPurchasedInCycle(subscription.getId(), cycle)).thenReturn(50_000L);
        when(tokenLedgerRepository.sumTokensUsedInCycle(subscription.getId(), cycle)).thenReturn(120_000L);

        long balance = entitlementService.getTokenBalance(sellerId);

        assertThat(balance).isEqualTo(500_000 + 50_000 - 120_000);
    }

    @Test
    void getTokenBalanceIsZeroWhenThereIsNoAdsAnalyzerSubscription() {
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER)).thenReturn(Optional.empty());

        assertThat(entitlementService.getTokenBalance(sellerId)).isZero();
    }

    // --- canExecuteAnalysis ---

    @Test
    void canExecuteAnalysisRequiresModuleTokensAndAConfirmedSeller() {
        UUID planId = UUID.randomUUID();
        LocalDate cycle = LocalDate.now();
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, Module.ADS_ANALYZER, planId);
        subscription.setCurrentTokenPeriodStartDate(cycle);
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(Module.ADS_ANALYZER, 1, 10_000L)));
        when(tokenLedgerRepository.sumTokensPurchasedInCycle(subscription.getId(), cycle)).thenReturn(0L);
        when(tokenLedgerRepository.sumTokensUsedInCycle(subscription.getId(), cycle)).thenReturn(0L);
        when(sellerRepository.findById(sellerId)).thenReturn(Optional.of(seller(SellerStatus.ACTIVE)));

        assertThat(entitlementService.canExecuteAnalysis(sellerId, UUID.randomUUID())).isTrue();
    }

    @Test
    void canExecuteAnalysisIsFalseForAPendingSeller() {
        // RN-ABUSE-005: a PENDING seller (email not confirmed) never
        // executes an analysis, even with module + balance available.
        UUID planId = UUID.randomUUID();
        LocalDate cycle = LocalDate.now();
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, Module.ADS_ANALYZER, planId);
        subscription.setCurrentTokenPeriodStartDate(cycle);
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(Module.ADS_ANALYZER, 1, 10_000L)));
        when(tokenLedgerRepository.sumTokensPurchasedInCycle(subscription.getId(), cycle)).thenReturn(0L);
        when(tokenLedgerRepository.sumTokensUsedInCycle(subscription.getId(), cycle)).thenReturn(0L);
        when(sellerRepository.findById(sellerId)).thenReturn(Optional.of(seller(SellerStatus.PENDING)));

        assertThat(entitlementService.canExecuteAnalysis(sellerId, UUID.randomUUID())).isFalse();
    }

    @Test
    void canExecuteAnalysisIsFalseWithoutTokenBalance() {
        UUID planId = UUID.randomUUID();
        LocalDate cycle = LocalDate.now();
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, Module.ADS_ANALYZER, planId);
        subscription.setCurrentTokenPeriodStartDate(cycle);
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(Module.ADS_ANALYZER, 1, 10_000L)));
        when(tokenLedgerRepository.sumTokensPurchasedInCycle(subscription.getId(), cycle)).thenReturn(0L);
        when(tokenLedgerRepository.sumTokensUsedInCycle(subscription.getId(), cycle)).thenReturn(10_000L);

        assertThat(entitlementService.canExecuteAnalysis(sellerId, UUID.randomUUID())).isFalse();
    }

    // --- canBuyExtraPackage ---

    @Test
    void canBuyExtraPackageIsTrueOnlyWhenActive() {
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE, Module.ADS_ANALYZER, UUID.randomUUID())));
        assertThat(entitlementService.canBuyExtraPackage(sellerId)).isTrue();

        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.TRIAL_ACTIVE, Module.ADS_ANALYZER, null)));
        assertThat(entitlementService.canBuyExtraPackage(sellerId)).isFalse();
    }

    // --- canCreateProduct ---

    @Test
    void canCreateProductEnforcesTheFifteenProductTrialCapOnly() {
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.TRIAL_ACTIVE, Module.PRODUCTS, null)));

        assertThat(entitlementService.canCreateProduct(sellerId, 14)).isTrue();
        assertThat(entitlementService.canCreateProduct(sellerId, 15)).isFalse();
    }

    @Test
    void canCreateProductIsAlwaysTrueOnAPaidPlan() {
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE, Module.PRODUCTS, UUID.randomUUID())));

        assertThat(entitlementService.canCreateProduct(sellerId, 10_000)).isTrue();
    }

    private Subscription subscription(SubscriptionStatus status, Module module, UUID planId) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(sellerId);
        subscription.setModule(module);
        subscription.setStatus(status);
        subscription.setPlanId(planId);
        subscription.setCreatedAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        return subscription;
    }

    private Plan plan(Module module, int storesLimit, Long tokenQuota) {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setModule(module);
        plan.setName("Completo");
        plan.setStoresLimit(storesLimit);
        plan.setCurrentMonthTokenQuota(tokenQuota);
        return plan;
    }

    private Seller seller(SellerStatus status) {
        Seller seller = new Seller();
        seller.setId(sellerId);
        seller.setEmail("seller@example.com");
        seller.setRole(Role.SELLER);
        seller.setStatus(status);
        return seller;
    }
}
