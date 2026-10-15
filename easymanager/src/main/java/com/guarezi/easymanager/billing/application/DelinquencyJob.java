package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.billing.application.ports.out.PaymentAttemptRepository;
import com.guarezi.easymanager.billing.application.ports.out.SubscriptionRepository;
import com.guarezi.easymanager.billing.domain.Subscription;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

// Fase 1.5, Block D: RN-INAD-001/002/004. This job decides the two
// conditions SubscriptionStateMachine (Block B) assumed a caller would
// decide:
//  - "vencimento não pago" (RN-INAD-001): the paid cycle ended with no
//    renewal — computed here from currentCycleEndDate, no payment
//    gateway call needed to detect it.
//  - "5ª tentativa falhou" (RN-INAD-002): counted from existing
//    payment_attempts rows. This job never itself attempts a charge —
//    no payment gateway is integrated in any phase so far; it only
//    reacts to attempts some future process writes.
// RN-INAD-004 (combo: the ladder runs per module) falls out naturally —
// each Subscription row is already one seller x module pair.
@Service
public class DelinquencyJob {

    private static final long DECLINED_ATTEMPTS_BEFORE_BLOCKING = 5;

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final SubscriptionStateMachine stateMachine;

    public DelinquencyJob(SubscriptionRepository subscriptionRepository,
                           PaymentAttemptRepository paymentAttemptRepository, SubscriptionStateMachine stateMachine) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.stateMachine = stateMachine;
    }

    public void run() {
        LocalDate today = LocalDate.now();

        for (Subscription subscription : subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)) {
            if (subscription.getCurrentCycleEndDate() != null && !today.isBefore(subscription.getCurrentCycleEndDate())) {
                stateMachine.markPastDue(subscription);
            }
        }

        for (Subscription subscription : subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE)) {
            long declinedAttempts = paymentAttemptRepository.countDeclinedRenewalAttempts(subscription.getId());
            if (declinedAttempts >= DECLINED_ATTEMPTS_BEFORE_BLOCKING) {
                stateMachine.blockForNonPayment(subscription);
            }
        }
    }
}
