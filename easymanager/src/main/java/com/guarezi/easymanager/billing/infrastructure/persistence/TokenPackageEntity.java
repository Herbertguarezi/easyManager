package com.guarezi.easymanager.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

// Read-model for EntitlementService.getTokenBalance — see TokenUsageEntity
// javadoc for why a partial mapping of token_packages is fine here.
@Getter
@Setter
@Entity
@Table(name = "token_packages")
public class TokenPackageEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "reference_cycle", nullable = false)
    private LocalDate referenceCycle;

    @Column(name = "tokens_purchased", nullable = false)
    private int tokensPurchased;
}
