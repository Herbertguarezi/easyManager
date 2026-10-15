package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.billing.application.ports.out.SubscriptionRepository;
import com.guarezi.easymanager.billing.domain.BillingCycle;
import com.guarezi.easymanager.billing.domain.InvalidSubscriptionTransitionException;
import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Subscription;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.guarezi.easymanager.billing.domain.SubscriptionStatus.ACTIVE;
import static com.guarezi.easymanager.billing.domain.SubscriptionStatus.CANCELLATION_REQUESTED;
import static com.guarezi.easymanager.billing.domain.SubscriptionStatus.DELETED;
import static com.guarezi.easymanager.billing.domain.SubscriptionStatus.PAST_DUE;
import static com.guarezi.easymanager.billing.domain.SubscriptionStatus.RETENTION;
import static com.guarezi.easymanager.billing.domain.SubscriptionStatus.TRIAL_ACTIVE;
import static com.guarezi.easymanager.billing.domain.SubscriptionStatus.TRIAL_EXHAUSTED;
import static com.guarezi.easymanager.billing.domain.SubscriptionStatus.TRIAL_RESTRICTED;
import static com.guarezi.easymanager.billing.domain.SubscriptionStatus.WITHDRAWN;

// RF-AU24: this is the only place `subscriptions.status` is ever written.
// ALLOWED_TRANSITIONS is the (from -> {to...}) table derived directly from
// regras-negocio.md sec. 13.3, as confirmed/corrected during the Block B
// review (see PROGRESS.md Fase 1.5 Block B decisions log for the two
// contradictions found and how each was resolved):
//
//  - DELETED has no outgoing entry: confirmed terminal for real, despite
//    arquitetura-banco-de-dados.md's now-corrected note suggesting a
//    DELETED -> ACTIVE reactivation path.
//  - WITHDRAWN (new 9th status, ACTIVE -> WITHDRAWN, right of withdrawal,
//    RN-PAY-002/004) -> ACTIVE only: confirmed business decision
//    (regras-negocio.md sec. 13.3) — same rule as SEM_ASSINATURA ->
//    ATIVO, no penalty for having withdrawn before. Deliberately NOT
//    WITHDRAWN -> TRIAL_ACTIVE: reopening a trial after a refund is an
//    abuse vector current RN-ABUSE rules don't cover.
//  - RETENTION -> ACTIVE is a single table entry covering both
//    RN-RET-003 (simple regularization) and RN-SUB-010 (full new
//    checkout) — the schema/table doesn't distinguish the two paths, only
//    the application code calling this class would (arquitetura-banco-
//    de-dados.md sec. 3); no such caller exists yet in this phase.
//  - SEM_ASSINATURA never appears as a map key or value here: it is not a
//    stored status (see SubscriptionStatus javadoc) — creating a brand
//    new Subscription row (TRIAL_ACTIVE or ACTIVE) is a different
//    operation, not a transitionTo call; see the named methods in this
//    same package (startTrial, startPaidSubscription).
@Service
public class SubscriptionStateMachine {

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> ALLOWED_TRANSITIONS = buildTransitionTable();

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionStateMachine(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public Subscription transitionTo(Subscription subscription, SubscriptionStatus target) {
        SubscriptionStatus current = subscription.getStatus();
        Set<SubscriptionStatus> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());

        if (!allowedTargets.contains(target)) {
            throw new InvalidSubscriptionTransitionException(
                    "transition " + current + " -> " + target + " is not allowed by regras-negocio.md sec. 13.3");
        }

        subscription.setStatus(target);
        subscription.setUpdatedAt(Instant.now());
        return subscriptionRepository.save(subscription);
    }

    // SEM_ASSINATURA -> TRIAL_ATIVO: creates a brand-new row, so there is
    // no existing "from" status to validate against ALLOWED_TRANSITIONS —
    // the guard here is the repository lookup itself (a row must not
    // already exist for this seller x module).
    public Subscription startTrial(UUID sellerId, Module module) {
        if (subscriptionRepository.findBySellerIdAndModule(sellerId, module).isPresent()) {
            throw new InvalidSubscriptionTransitionException(
                    "seller " + sellerId + " already has a subscription for module " + module);
        }

        Instant now = Instant.now();
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(sellerId);
        subscription.setModule(module);
        subscription.setStatus(TRIAL_ACTIVE);
        // RN-TRIAL-006: only ADS_ANALYZER trials expire; RN-PROD-002:
        // PRODUCTS trial has no time limit.
        subscription.setTrialExpiresAt(module.hasTrialExpiry() ? LocalDate.now().plusDays(30) : null);
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);

        return subscriptionRepository.save(subscription);
    }

    public Subscription restrictForRiskSignals(Subscription subscription) {
        return transitionTo(subscription, TRIAL_RESTRICTED);
    }

    public Subscription liftTrialRestriction(Subscription subscription) {
        return transitionTo(subscription, TRIAL_ACTIVE);
    }

    public Subscription exhaustTrial(Subscription subscription) {
        return transitionTo(subscription, TRIAL_EXHAUSTED);
    }

    // RN-TRIAL-006: only ADS_ANALYZER trials expire, and only once
    // trialExpiresAt has actually passed — both guards are pure functions
    // of data already on the Subscription itself, not a cross-table
    // query, so they belong here rather than in whatever future job
    // calls this (Fase 1.5 Block D).
    public Subscription expireTrial(Subscription subscription) {
        if (!subscription.getModule().hasTrialExpiry()) {
            throw new InvalidSubscriptionTransitionException(
                    "module " + subscription.getModule() + " trial never expires (RN-PROD-002)");
        }
        if (subscription.getTrialExpiresAt() == null || LocalDate.now().isBefore(subscription.getTrialExpiresAt())) {
            throw new InvalidSubscriptionTransitionException(
                    "trial for subscription " + subscription.getId() + " has not reached its expiry date yet");
        }
        return transitionTo(subscription, DELETED);
    }

    // SEM_ASSINATURA -> ATIVO (RN-PAY-001, 1st charge approved, no trial
    // involved): brand-new row, same reasoning as startTrial.
    public Subscription startPaidSubscriptionDirectly(UUID sellerId, Module module, UUID planId, BillingCycle cycle) {
        if (subscriptionRepository.findBySellerIdAndModule(sellerId, module).isPresent()) {
            throw new InvalidSubscriptionTransitionException(
                    "seller " + sellerId + " already has a subscription for module " + module);
        }

        Instant now = Instant.now();
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(sellerId);
        subscription.setModule(module);
        subscription.setStatus(ACTIVE);
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);
        applyNewPlanAndCycle(subscription, planId, cycle);

        return subscriptionRepository.save(subscription);
    }

    // TRIAL_* -> ATIVO (RN-PAY-001, payment approved while in any trial
    // sub-state, including TRIAL_RESTRITO — RF-AU25/RN-ABUSE-003 note:
    // paying never requires contesting a restriction first).
    public Subscription convertTrialToPaidSubscription(Subscription subscription, UUID planId, BillingCycle cycle) {
        applyNewPlanAndCycle(subscription, planId, cycle);
        return transitionTo(subscription, ACTIVE);
    }

    // WITHDRAWN -> ATIVO (confirmed business decision, regras-negocio.md
    // sec. 13.3): same rule as SEM_ASSINATURA -> ATIVO, no penalty for
    // having withdrawn before. Deliberately has no TRIAL_ACTIVE
    // counterpart — see the ALLOWED_TRANSITIONS comment above.
    public Subscription resubscribeAfterWithdrawal(Subscription subscription, UUID planId, BillingCycle cycle) {
        applyNewPlanAndCycle(subscription, planId, cycle);
        return transitionTo(subscription, ACTIVE);
    }

    // RN-SUB-004/005/006: upgrade/downgrade has immediate effect, but only
    // on the monthly cycle — a longer, already-paid cycle locks plan
    // changes until renewal. Does not change `status` (ATIVO stays ATIVO,
    // self-loop in sec. 13.3), so this does not go through transitionTo —
    // RF-AU24 only governs the `status` column. Token-balance transport
    // between plans (RN-SUB-005/006) is EntitlementService/Fase 7
    // territory, out of scope here.
    public Subscription changePlan(Subscription subscription, UUID newPlanId) {
        if (subscription.getStatus() != ACTIVE) {
            throw new InvalidSubscriptionTransitionException(
                    "cannot change plan for subscription " + subscription.getId() + " in status " + subscription.getStatus());
        }
        if (subscription.getBillingCycle() != BillingCycle.MONTHLY) {
            throw new InvalidSubscriptionTransitionException(
                    "RN-SUB-004: plan changes are only allowed on the monthly cycle, subscription "
                            + subscription.getId() + " is on " + subscription.getBillingCycle());
        }

        subscription.setPlanId(newPlanId);
        subscription.setUpdatedAt(Instant.now());
        return subscriptionRepository.save(subscription);
    }

    // ATIVO -> CANCELAMENTO_SOLICITADO (RN-SUB-007/009): irreversible, the
    // transition table itself already enforces "no way back to ATIVO"
    // since CANCELLATION_REQUESTED has no such entry.
    public Subscription requestCancellation(Subscription subscription) {
        subscription.setCancellationRequestedAt(Instant.now());
        return transitionTo(subscription, CANCELLATION_REQUESTED);
    }

    // ATIVO -> WITHDRAWN (RN-PAY-002/004): right of withdrawal, 7 dias
    // corridos from signup (createdAt used as the anchor — Subscription
    // has no separate "first charge date" field). plan_id is cleared:
    // WITHDRAWN is plan-exempt (chk_subscriptions_plan_required, V11).
    public Subscription withdraw(Subscription subscription) {
        Instant deadline = subscription.getCreatedAt().plus(7, java.time.temporal.ChronoUnit.DAYS);
        if (Instant.now().isAfter(deadline)) {
            throw new InvalidSubscriptionTransitionException(
                    "RN-PAY-002: the 7-day right of withdrawal has expired for subscription " + subscription.getId());
        }

        subscription.setPlanId(null);
        return transitionTo(subscription, WITHDRAWN);
    }

    // ATIVO -> INADIMPLENTE (RN-INAD-001): vencimento não pago, início da
    // régua de 5 tentativas — access stays open during the ladder, that
    // is an EntitlementService.canUseModule concern (Block C), not this
    // class.
    public Subscription markPastDue(Subscription subscription) {
        return transitionTo(subscription, PAST_DUE);
    }

    // INADIMPLENTE -> ATIVO / RETENCAO -> ATIVO: both RN-RET-003
    // (regularization) and RN-SUB-010 (full new checkout from RETENCAO)
    // land here — see the table's comment on why they're not
    // distinguished at this layer.
    public Subscription regularizePayment(Subscription subscription) {
        return transitionTo(subscription, ACTIVE);
    }

    // INADIMPLENTE -> RETENCAO (RN-INAD-002/003): day 6 = block; the
    // retention window (RN-RET-001, module-specific) starts counting from
    // the block itself, not from the original due date.
    public Subscription blockForNonPayment(Subscription subscription) {
        Instant now = Instant.now();
        subscription.setBlockedAt(now);
        subscription.setScheduledDeletionDate(LocalDate.now().plusDays(subscription.getModule().retentionDays()));
        return transitionTo(subscription, RETENTION);
    }

    // CANCELAMENTO_SOLICITADO -> RETENCAO (RN-RET-002): the retention
    // clock only starts once the already-paid cycle actually ends, never
    // at the moment cancellation was requested — guarded here since it's
    // a pure function of currentCycleEndDate, already on the entity.
    public Subscription moveToRetentionAfterCancellation(Subscription subscription) {
        LocalDate today = LocalDate.now();
        if (subscription.getCurrentCycleEndDate() == null || today.isBefore(subscription.getCurrentCycleEndDate())) {
            throw new InvalidSubscriptionTransitionException(
                    "RN-RET-002: subscription " + subscription.getId() + " has not reached the end of its paid cycle yet");
        }
        subscription.setScheduledDeletionDate(subscription.getCurrentCycleEndDate().plusDays(subscription.getModule().retentionDays()));
        return transitionTo(subscription, RETENTION);
    }

    // RETENCAO -> EXCLUIDO (RN-RET-001/003): retention window exhausted,
    // no regularization/reactivation happened in time. plan_id is
    // cleared — DELETED is plan-exempt (chk_subscriptions_plan_required).
    public Subscription deleteAfterRetention(Subscription subscription) {
        LocalDate today = LocalDate.now();
        if (subscription.getScheduledDeletionDate() == null || today.isBefore(subscription.getScheduledDeletionDate())) {
            throw new InvalidSubscriptionTransitionException(
                    "RN-RET-001: retention period for subscription " + subscription.getId() + " has not elapsed yet");
        }
        subscription.setPlanId(null);
        return transitionTo(subscription, DELETED);
    }

    private static void applyNewPlanAndCycle(Subscription subscription, UUID planId, BillingCycle cycle) {
        LocalDate today = LocalDate.now();
        subscription.setPlanId(planId);
        subscription.setBillingCycle(cycle);
        subscription.setAnniversaryDate(today);
        subscription.setCurrentCycleStartDate(today);
        subscription.setCurrentCycleEndDate(today.plusMonths(cycle.months()));

        // RN-TOKEN-005: the monthly token period resets independently of
        // the billing cycle length (which can span up to 12 months on an
        // annual plan) — only ADS_ANALYZER has a token concept at all.
        // Found while building EntitlementService.getTokenBalance (Fase
        // 1.5 Block C) — see PROGRESS.md decisions log.
        if (subscription.getModule().hasTokenConcept()) {
            subscription.setCurrentTokenPeriodStartDate(today);
            subscription.setCurrentTokenPeriodEndDate(today.plusMonths(1));
        }
    }

    private static Map<SubscriptionStatus, Set<SubscriptionStatus>> buildTransitionTable() {
        Map<SubscriptionStatus, Set<SubscriptionStatus>> table = new EnumMap<>(SubscriptionStatus.class);

        table.put(TRIAL_ACTIVE, EnumSet.of(TRIAL_RESTRICTED, TRIAL_EXHAUSTED, ACTIVE, DELETED));
        table.put(TRIAL_RESTRICTED, EnumSet.of(TRIAL_ACTIVE, ACTIVE, DELETED));
        table.put(TRIAL_EXHAUSTED, EnumSet.of(ACTIVE, DELETED));
        table.put(ACTIVE, EnumSet.of(WITHDRAWN, CANCELLATION_REQUESTED, PAST_DUE));
        table.put(CANCELLATION_REQUESTED, EnumSet.of(RETENTION));
        table.put(PAST_DUE, EnumSet.of(ACTIVE, RETENTION));
        table.put(RETENTION, EnumSet.of(ACTIVE, DELETED));
        table.put(WITHDRAWN, EnumSet.of(ACTIVE));
        // DELETED: no entry = no outgoing transition allowed (confirmed terminal).

        return Map.copyOf(table);
    }
}
