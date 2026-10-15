package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.billing.application.ports.out.DataPurgePort;
import com.guarezi.easymanager.billing.application.ports.out.SubscriptionRepository;
import com.guarezi.easymanager.billing.domain.Subscription;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

// Fase 1.5, Block D: RN-TRIAL-006 — valid from any trial sub-state,
// ADS_ANALYZER only. SubscriptionStateMachine.expireTrial already guards
// both the module and the date (defense in depth); this job pre-filters
// the same way so it only calls it on eligible rows.
@Service
public class TrialExpirationJob {

    private static final List<SubscriptionStatus> TRIAL_STATUSES = List.of(
            SubscriptionStatus.TRIAL_ACTIVE, SubscriptionStatus.TRIAL_RESTRICTED, SubscriptionStatus.TRIAL_EXHAUSTED);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStateMachine stateMachine;
    private final DataPurgePort dataPurgePort;

    public TrialExpirationJob(SubscriptionRepository subscriptionRepository, SubscriptionStateMachine stateMachine,
                               DataPurgePort dataPurgePort) {
        this.subscriptionRepository = subscriptionRepository;
        this.stateMachine = stateMachine;
        this.dataPurgePort = dataPurgePort;
    }

    public void run() {
        LocalDate today = LocalDate.now();

        for (SubscriptionStatus status : TRIAL_STATUSES) {
            for (Subscription subscription : subscriptionRepository.findByStatus(status)) {
                if (subscription.getModule().hasTrialExpiry()
                        && subscription.getTrialExpiresAt() != null
                        && !today.isBefore(subscription.getTrialExpiresAt())) {
                    Subscription deleted = stateMachine.expireTrial(subscription);
                    dataPurgePort.purge(deleted.getId(), deleted.getModule());
                }
            }
        }
    }
}
