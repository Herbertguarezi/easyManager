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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Fase 1.5, Block D: RN-RET-001/002/003 — only the state transition +
// the (empty) purge port call happen in this phase; real data deletion
// is Fase 3/7.
class RetentionJobTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private DataPurgePort dataPurgePort;

    private SubscriptionStateMachine stateMachine;
    private RetentionJob job;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        stateMachine = new SubscriptionStateMachine(subscriptionRepository);
        job = new RetentionJob(subscriptionRepository, stateMachine, dataPurgePort);
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void movesCancelledSubscriptionsToRetentionOnceTheirCycleHasEnded() {
        // RN-RET-002: counts from the end of the paid cycle, never from
        // the cancellation request itself.
        Subscription subscription = subscription(SubscriptionStatus.CANCELLATION_REQUESTED, Module.ADS_ANALYZER);
        subscription.setCurrentCycleEndDate(LocalDate.now().minusDays(1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.CANCELLATION_REQUESTED)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.RETENTION)).thenReturn(List.of());

        job.run();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.RETENTION);
        assertThat(subscription.getScheduledDeletionDate()).isEqualTo(LocalDate.now().minusDays(1).plusDays(7));
    }

    @Test
    void leavesCancelledSubscriptionsAloneBeforeTheirCycleEnds() {
        Subscription subscription = subscription(SubscriptionStatus.CANCELLATION_REQUESTED, Module.ADS_ANALYZER);
        subscription.setCurrentCycleEndDate(LocalDate.now().plusDays(1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.CANCELLATION_REQUESTED)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.RETENTION)).thenReturn(List.of());

        job.run();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLATION_REQUESTED);
    }

    @Test
    void deletesAndPurgesSubscriptionsPastTheirRetentionDeadline() {
        Subscription subscription = subscription(SubscriptionStatus.RETENTION, Module.PRODUCTS);
        subscription.setScheduledDeletionDate(LocalDate.now().minusDays(1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.CANCELLATION_REQUESTED)).thenReturn(List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.RETENTION)).thenReturn(List.of(subscription));

        job.run();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.DELETED);
        verify(dataPurgePort).purge(subscription.getId(), Module.PRODUCTS);
    }

    @Test
    void leavesRetentionSubscriptionsAloneBeforeTheDeadline() {
        Subscription subscription = subscription(SubscriptionStatus.RETENTION, Module.PRODUCTS);
        subscription.setScheduledDeletionDate(LocalDate.now().plusDays(1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.CANCELLATION_REQUESTED)).thenReturn(List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.RETENTION)).thenReturn(List.of(subscription));

        job.run();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.RETENTION);
        verify(dataPurgePort, never()).purge(any(), any());
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
