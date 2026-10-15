package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.billing.application.ports.out.SubscriptionRepository;
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

// Fase 1.5, Block B, checklist item 2: trial transitions (start,
// restriction, exhaustion, contestation, 30-day ADS_ANALYZER expiry).
class SubscriptionStateMachineTrialTest {

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
    void startTrialCreatesARowWithNoPlanAndSetsExpiryOnlyForAdsAnalyzer() {
        UUID sellerId = UUID.randomUUID();
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER)).thenReturn(Optional.empty());

        Subscription subscription = stateMachine.startTrial(sellerId, Module.ADS_ANALYZER);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_ACTIVE);
        assertThat(subscription.getPlanId()).isNull();
        assertThat(subscription.getSellerId()).isEqualTo(sellerId);
        // RN-TRIAL-006: 30 days from the start of the trial.
        assertThat(subscription.getTrialExpiresAt()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void startTrialForProductsNeverSetsAnExpiry() {
        // RN-PROD-002: PRODUCTS trial has no time limit.
        UUID sellerId = UUID.randomUUID();
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS)).thenReturn(Optional.empty());

        Subscription subscription = stateMachine.startTrial(sellerId, Module.PRODUCTS);

        assertThat(subscription.getTrialExpiresAt()).isNull();
    }

    @Test
    void startTrialRejectsWhenASubscriptionAlreadyExistsForThatSellerAndModule() {
        UUID sellerId = UUID.randomUUID();
        when(subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER))
                .thenReturn(Optional.of(existingSubscription(SubscriptionStatus.DELETED)));

        assertThatThrownBy(() -> stateMachine.startTrial(sellerId, Module.ADS_ANALYZER))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void restrictForRiskSignalsMovesFromTrialActiveToTrialRestricted() {
        Subscription subscription = existingSubscription(SubscriptionStatus.TRIAL_ACTIVE);

        Subscription result = stateMachine.restrictForRiskSignals(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_RESTRICTED);
    }

    @Test
    void liftTrialRestrictionMovesBackToTrialActive() {
        Subscription subscription = existingSubscription(SubscriptionStatus.TRIAL_RESTRICTED);

        Subscription result = stateMachine.liftTrialRestriction(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_ACTIVE);
    }

    @Test
    void exhaustTrialMovesFromTrialActiveToTrialExhausted() {
        Subscription subscription = existingSubscription(SubscriptionStatus.TRIAL_ACTIVE);

        Subscription result = stateMachine.exhaustTrial(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_EXHAUSTED);
    }

    @Test
    void expireTrialDeletesAnAdsAnalyzerTrialPastItsExpiryDate() {
        Subscription subscription = existingSubscription(SubscriptionStatus.TRIAL_EXHAUSTED);
        subscription.setModule(Module.ADS_ANALYZER);
        subscription.setTrialExpiresAt(LocalDate.now().minusDays(1));

        Subscription result = stateMachine.expireTrial(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.DELETED);
    }

    @Test
    void expireTrialRejectsBeforeTheExpiryDate() {
        Subscription subscription = existingSubscription(SubscriptionStatus.TRIAL_ACTIVE);
        subscription.setModule(Module.ADS_ANALYZER);
        subscription.setTrialExpiresAt(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> stateMachine.expireTrial(subscription))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void expireTrialRejectsForProductsWhichNeverExpires() {
        // RN-PROD-002/RN-TRIAL-006: only ADS_ANALYZER trials expire.
        Subscription subscription = existingSubscription(SubscriptionStatus.TRIAL_ACTIVE);
        subscription.setModule(Module.PRODUCTS);

        assertThatThrownBy(() -> stateMachine.expireTrial(subscription))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    private Subscription existingSubscription(SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(UUID.randomUUID());
        subscription.setModule(Module.ADS_ANALYZER);
        subscription.setStatus(status);
        subscription.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        subscription.setUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return subscription;
    }
}
