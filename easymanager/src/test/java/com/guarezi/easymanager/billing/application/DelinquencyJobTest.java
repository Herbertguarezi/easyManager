package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.billing.application.ports.out.PaymentAttemptRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Fase 1.5, Block D: RN-INAD-001/002/004 — the job decides the condition
// (counts payment_attempts), SubscriptionStateMachine (Block B) just
// executes the already-decided transition.
class DelinquencyJobTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    private SubscriptionStateMachine stateMachine;
    private DelinquencyJob job;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        stateMachine = new SubscriptionStateMachine(subscriptionRepository);
        job = new DelinquencyJob(subscriptionRepository, paymentAttemptRepository, stateMachine);
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void marksActiveSubscriptionsPastDueOnceTheirCycleHasEnded() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE);
        subscription.setCurrentCycleEndDate(LocalDate.now().minusDays(1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE)).thenReturn(List.of());

        job.run();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void leavesActiveSubscriptionsAloneBeforeTheirCycleEnds() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE);
        subscription.setCurrentCycleEndDate(LocalDate.now().plusDays(1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE)).thenReturn(List.of());

        job.run();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void blocksPastDueSubscriptionsAfterFiveDeclinedRenewalAttempts() {
        // RN-INAD-002: day 6 = block.
        Subscription subscription = subscription(SubscriptionStatus.PAST_DUE);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE)).thenReturn(List.of(subscription));
        when(paymentAttemptRepository.countDeclinedRenewalAttempts(subscription.getId())).thenReturn(5L);

        job.run();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.RETENTION);
        assertThat(subscription.getBlockedAt()).isNotNull();
    }

    @Test
    void leavesPastDueSubscriptionsAloneBeforeFiveDeclinedAttempts() {
        Subscription subscription = subscription(SubscriptionStatus.PAST_DUE);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE)).thenReturn(List.of(subscription));
        when(paymentAttemptRepository.countDeclinedRenewalAttempts(subscription.getId())).thenReturn(3L);

        job.run();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    private Subscription subscription(SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(UUID.randomUUID());
        subscription.setModule(Module.PRODUCTS);
        subscription.setStatus(status);
        subscription.setPlanId(UUID.randomUUID());
        subscription.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));
        subscription.setUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return subscription;
    }
}
