package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.domain.BillingCycle;
import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Module module;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "plan_id")
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle")
    private BillingCycle billingCycle;

    @Column(name = "anniversary_date")
    private LocalDate anniversaryDate;

    @Column(name = "current_cycle_start_date")
    private LocalDate currentCycleStartDate;

    @Column(name = "current_cycle_end_date")
    private LocalDate currentCycleEndDate;

    @Column(name = "current_token_period_start_date")
    private LocalDate currentTokenPeriodStartDate;

    @Column(name = "current_token_period_end_date")
    private LocalDate currentTokenPeriodEndDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "combo_group_id")
    private UUID comboGroupId;

    @Column(name = "cancellation_requested_at")
    private Instant cancellationRequestedAt;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "scheduled_deletion_date")
    private LocalDate scheduledDeletionDate;

    @Column(name = "trial_expires_at")
    private LocalDate trialExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
