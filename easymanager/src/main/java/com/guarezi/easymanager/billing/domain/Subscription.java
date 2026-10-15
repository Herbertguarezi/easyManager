package com.guarezi.easymanager.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// One row per seller x module (arquitetura-banco-de-dados.md sec. 3) —
// this object always represents an existing row; a seller/module pair
// with no Subscription yet (SEM_ASSINATURA) is Optional.empty() at the
// repository, never an instance of this class with a null-ish state.
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Subscription {
    private UUID id;
    private UUID sellerId;
    private Module module;
    private UUID planId;
    private SubscriptionStatus status;
    private BillingCycle billingCycle;
    private LocalDate anniversaryDate;
    private LocalDate currentCycleStartDate;
    private LocalDate currentCycleEndDate;
    private LocalDate currentTokenPeriodStartDate;
    private LocalDate currentTokenPeriodEndDate;
    private UUID comboGroupId;
    private Instant cancellationRequestedAt;
    private Instant blockedAt;
    private LocalDate scheduledDeletionDate;
    private LocalDate trialExpiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
