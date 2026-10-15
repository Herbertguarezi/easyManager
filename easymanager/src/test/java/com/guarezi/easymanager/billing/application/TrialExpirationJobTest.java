package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.billing.application.ports.out.DataPurgePort;
import com.guarezi.easymanager.billing.application.ports.out.SubscriptionRepository;
import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Subscription;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Fase 1.5, Block D: RN-TRIAL-006 — 30-day ADS_ANALYZER trial expiry,
// counted from trial start, valid from any trial sub-state; PRODUCTS is
// never touched (RN-PROD-002, no time limit).
class TrialExpirationJobTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private DataPurgePort dataPurgePort;

    private SubscriptionStateMachine stateMachine;
    private TrialExpirationJob job;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        stateMachine = new SubscriptionStateMachine(subscriptionRepository);
        job = new TrialExpirationJob(subscriptionRepository, stateMachine, dataPurgePort);
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void expiresAndPurgesAnAdsAnalyzerTrialPastItsExpiryDateFromAnySubState() {
        Subscription restricted = trialSubscription(SubscriptionStatus.TRIAL_RESTRICTED, Module.ADS_ANALYZER,
                LocalDate.now().minusDays(1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL_ACTIVE)).thenReturn(List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL_RESTRICTED)).thenReturn(List.of(restricted));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL_EXHAUSTED)).thenReturn(List.of());

        job.run();

        assertThat(restricted.getStatus()).isEqualTo(SubscriptionStatus.DELETED);
        verify(dataPurgePort).purge(restricted.getId(), Module.ADS_ANALYZER);
    }

    @Test
    void leavesAnAdsAnalyzerTrialAloneBeforeItsExpiryDate() {
        Subscription active = trialSubscription(SubscriptionStatus.TRIAL_ACTIVE, Module.ADS_ANALYZER,
                LocalDate.now().plusDays(1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL_ACTIVE)).thenReturn(List.of(active));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL_RESTRICTED)).thenReturn(List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL_EXHAUSTED)).thenReturn(List.of());

        job.run();

        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_ACTIVE);
        verify(dataPurgePort, never()).purge(any(), any());
    }

    @Test
    void neverTouchesProductsTrialsEvenWithAnExpiryDateSomehowSet() {
        // RN-PROD-002: PRODUCTS has no time limit at all, regardless of
        // what any stray data says.
        Subscription products = trialSubscription(SubscriptionStatus.TRIAL_ACTIVE, Module.PRODUCTS,
                LocalDate.now().minusDays(1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL_ACTIVE)).thenReturn(List.of(products));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL_RESTRICTED)).thenReturn(List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL_EXHAUSTED)).thenReturn(List.of());

        job.run();

        assertThat(products.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_ACTIVE);
        verify(dataPurgePort, never()).purge(any(), any());
    }

    private Subscription trialSubscription(SubscriptionStatus status, Module module, LocalDate trialExpiresAt) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(UUID.randomUUID());
        subscription.setModule(module);
        subscription.setStatus(status);
        subscription.setTrialExpiresAt(trialExpiresAt);
        subscription.setCreatedAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        return subscription;
    }
}
