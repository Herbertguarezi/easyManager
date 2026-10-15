package com.guarezi.easymanager.billing.domain;

// regras-negocio.md sec. 13.1 — 8 documented states, plus WITHDRAWN (9th,
// added during the Fase 1.5 Block B review; see PROGRESS.md decisions
// log). SEM_ASSINATURA is deliberately absent here: it is not a stored
// value, it is the absence of a Subscription row for that seller x module
// (arquitetura-banco-de-dados.md sec. 3) — modeled as Optional.empty() at
// the repository/service layer, never as an enum constant.
public enum SubscriptionStatus {
    TRIAL_ACTIVE,
    TRIAL_RESTRICTED,
    TRIAL_EXHAUSTED,
    ACTIVE,
    PAST_DUE,
    CANCELLATION_REQUESTED,
    RETENTION,
    DELETED,
    WITHDRAWN
}
