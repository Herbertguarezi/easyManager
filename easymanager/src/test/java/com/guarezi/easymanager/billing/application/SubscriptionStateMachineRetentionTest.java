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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Fase 1.5, Block B, checklist item 4: past-due/retention transitions
// (5-attempt charging ladder, day-6 block, reactivation).
class SubscriptionStateMachineRetentionTest {

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
    void markPastDueMovesFromActiveToPastDue() {
        Subscription result = stateMachine.markPastDue(activeSubscription());

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void regularizePaymentMovesFromPastDueToActive() {
        Subscription subscription = subscription(SubscriptionStatus.PAST_DUE, Module.PRODUCTS);

        Subscription result = stateMachine.regularizePayment(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void regularizePaymentMovesFromRetentionToActive() {
        // RN-RET-003 (regularization) and RN-SUB-010 (full new checkout)
        // both land here — the table doesn't distinguish them, see
        // SubscriptionStateMachineTest.
        Subscription subscription = subscription(SubscriptionStatus.RETENTION, Module.PRODUCTS);

        Subscription result = stateMachine.regularizePayment(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void blockForNonPaymentMovesToRetentionAndSchedulesDeletionFromTheBlockDate() {
        // RN-INAD-002/003: day 6 = block; retention window starts counting
        // from the block, not the original due date.
        Subscription subscription = subscription(SubscriptionStatus.PAST_DUE, Module.ADS_ANALYZER);

        Subscription result = stateMachine.blockForNonPayment(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.RETENTION);
        assertThat(result.getBlockedAt()).isNotNull();
        assertThat(result.getScheduledDeletionDate()).isEqualTo(LocalDate.now().plusDays(7));
    }

    @Test
    void blockForNonPaymentUsesThirtyDaysForProducts() {
        Subscription subscription = subscription(SubscriptionStatus.PAST_DUE, Module.PRODUCTS);

        Subscription result = stateMachine.blockForNonPayment(subscription);

        assertThat(result.getScheduledDeletionDate()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void moveToRetentionAfterCancellationRequiresTheCurrentCycleToHaveEnded() {
        // RN-RET-002: the retention clock only starts once the paid
        // cycle actually ends, never at the moment cancellation was
        // requested.
        Subscription subscription = subscription(SubscriptionStatus.CANCELLATION_REQUESTED, Module.ADS_ANALYZER);
        subscription.setCurrentCycleEndDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> stateMachine.moveToRetentionAfterCancellation(subscription))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void moveToRetentionAfterCancellationSchedulesDeletionFromTheCycleEndDate() {
        Subscription subscription = subscription(SubscriptionStatus.CANCELLATION_REQUESTED, Module.ADS_ANALYZER);
        subscription.setCurrentCycleEndDate(LocalDate.now().minusDays(1));

        Subscription result = stateMachine.moveToRetentionAfterCancellation(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.RETENTION);
        assertThat(result.getScheduledDeletionDate()).isEqualTo(LocalDate.now().minusDays(1).plusDays(7));
    }

    @Test
    void deleteAfterRetentionRequiresTheScheduledDateToHavePassed() {
        Subscription subscription = subscription(SubscriptionStatus.RETENTION, Module.PRODUCTS);
        subscription.setScheduledDeletionDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> stateMachine.deleteAfterRetention(subscription))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void deleteAfterRetentionMovesToDeletedAndClearsThePlan() {
        // RN-RET-001/003: retention exhausted, no regularization/reactivation.
        Subscription subscription = subscription(SubscriptionStatus.RETENTION, Module.PRODUCTS);
        subscription.setScheduledDeletionDate(LocalDate.now().minusDays(1));

        Subscription result = stateMachine.deleteAfterRetention(subscription);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.DELETED);
        assertThat(result.getPlanId()).isNull();
    }

    private Subscription activeSubscription() {
        return subscription(SubscriptionStatus.ACTIVE, Module.PRODUCTS);
    }

    private Subscription subscription(SubscriptionStatus status, Module module) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(UUID.randomUUID());
        subscription.setModule(module);
        subscription.setStatus(status);
        subscription.setPlanId(UUID.randomUUID());
        subscription.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));
        subscription.setUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return subscription;
    }
}
