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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Fase 1.5, Block B, checklist item 1: the core, generic mechanism behind
// RF-AU24 — every (from, to) pair either is or isn't in the table derived
// from regras-negocio.md sec. 13.3, nothing else decides it. The trial /
// paid-subscription / past-due-retention methods (items 2-4) are thin
// named wrappers around this, tested in their own files.
class SubscriptionStateMachineTest {

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
    void allowsADocumentedTransitionAndPersistsTheNewStatus() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE);

        Subscription result = stateMachine.transitionTo(subscription, SubscriptionStatus.PAST_DUE);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void rejectsAPairNotInTheTransitionTable() {
        Subscription subscription = subscription(SubscriptionStatus.TRIAL_ACTIVE);

        assertThatThrownBy(() -> stateMachine.transitionTo(subscription, SubscriptionStatus.RETENTION))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void deletedIsTerminalNoOutgoingTransitionIsAllowed() {
        // Confirmed with the user during the Block B review: EXCLUIDO is
        // terminal for real, despite arquitetura-banco-de-dados.md's
        // now-corrected note suggesting otherwise — see PROGRESS.md Fase
        // 1.5 Block B decisions log.
        Subscription subscription = subscription(SubscriptionStatus.DELETED);

        assertThatThrownBy(() -> stateMachine.transitionTo(subscription, SubscriptionStatus.ACTIVE))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void withdrawnCanResubscribeDirectlyToActive() {
        // Business decision confirmed after the Block B review
        // (regras-negocio.md sec. 13.3, "mesma regra de SEM_ASSINATURA ->
        // ATIVO, sem penalidade"): no longer terminal.
        Subscription subscription = subscription(SubscriptionStatus.WITHDRAWN);

        Subscription result = stateMachine.transitionTo(subscription, SubscriptionStatus.ACTIVE);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void withdrawnCannotRestartATrial() {
        // Deliberate exclusion (regras-negocio.md sec. 13.3): reopening a
        // trial after a refund is an abuse vector RN-ABUSE doesn't cover,
        // so only paid resubscription is allowed from WITHDRAWN.
        Subscription subscription = subscription(SubscriptionStatus.WITHDRAWN);

        assertThatThrownBy(() -> stateMachine.transitionTo(subscription, SubscriptionStatus.TRIAL_ACTIVE))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void retentionToActiveIsAllowedRegardlessOfHowRetentionWasReached() {
        // arquitetura-banco-de-dados.md sec. 3: the schema (and therefore
        // this table) doesn't distinguish RN-RET-003 (regularization)
        // from RN-SUB-010 (full new checkout) — both are the same
        // (RETENTION, ACTIVE) pair; the precondition difference is an
        // application-layer concern for whichever future phase builds
        // the billing/checkout use cases, not this table.
        Subscription subscription = subscription(SubscriptionStatus.RETENTION);

        Subscription result = stateMachine.transitionTo(subscription, SubscriptionStatus.ACTIVE);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    private Subscription subscription(SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setSellerId(UUID.randomUUID());
        subscription.setModule(Module.ADS_ANALYZER);
        subscription.setStatus(status);
        subscription.setCreatedAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        return subscription;
    }
}
