package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.billing.application.ports.out.DataPurgePort;
import com.guarezi.easymanager.billing.application.ports.out.SubscriptionRepository;
import com.guarezi.easymanager.billing.domain.Subscription;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

// Fase 1.5, Block D: RN-RET-001/002/003 — SubscriptionStateMachine's
// moveToRetentionAfterCancellation/deleteAfterRetention already guard
// their own date preconditions (defense in depth); this job pre-filters
// the same way so it only calls them on eligible rows.
@Service
public class RetentionJob {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStateMachine stateMachine;
    private final DataPurgePort dataPurgePort;

    public RetentionJob(SubscriptionRepository subscriptionRepository, SubscriptionStateMachine stateMachine,
                         DataPurgePort dataPurgePort) {
        this.subscriptionRepository = subscriptionRepository;
        this.stateMachine = stateMachine;
        this.dataPurgePort = dataPurgePort;
    }

    public void run() {
        LocalDate today = LocalDate.now();

        for (Subscription subscription : subscriptionRepository.findByStatus(SubscriptionStatus.CANCELLATION_REQUESTED)) {
            if (subscription.getCurrentCycleEndDate() != null && !today.isBefore(subscription.getCurrentCycleEndDate())) {
                stateMachine.moveToRetentionAfterCancellation(subscription);
            }
        }

        for (Subscription subscription : subscriptionRepository.findByStatus(SubscriptionStatus.RETENTION)) {
            if (subscription.getScheduledDeletionDate() != null && !today.isBefore(subscription.getScheduledDeletionDate())) {
                Subscription deleted = stateMachine.deleteAfterRetention(subscription);
                dataPurgePort.purge(deleted.getId(), deleted.getModule());
            }
        }
    }
}
