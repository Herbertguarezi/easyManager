package com.guarezi.easymanager.billing.domain;

import com.guarezi.easymanager.shared.exceptions.DomainValidationException;

// RF-AU24: thrown by SubscriptionStateMachine whenever a requested
// (from, to) status pair isn't in the transition table derived from
// regras-negocio.md sec. 13.3 — the only way any subscriptions.status
// UPDATE happens is through that table, this exception is what enforces
// "reject anything not in it".
public class InvalidSubscriptionTransitionException extends DomainValidationException {
    public InvalidSubscriptionTransitionException(String message) {
        super(message);
    }
}
