package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.domain.PaymentAttemptResult;
import com.guarezi.easymanager.billing.domain.PaymentAttemptType;
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

import java.util.UUID;

// Read-model for DelinquencyJob's guard — see TokenUsageEntity javadoc
// for why a partial mapping of payment_attempts is fine here.
@Getter
@Setter
@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentAttemptType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentAttemptResult result;
}
