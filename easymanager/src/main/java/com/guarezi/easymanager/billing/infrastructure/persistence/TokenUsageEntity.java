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

// Read-model for EntitlementService.getTokenBalance (Fase 1.5 Block C) —
// maps only the columns that balance math needs. The full-fidelity
// entity (every column of token_usage, used to actually record a run)
// is Fase 7's job, once the Ads Analyzer calls the Anthropic API for
// real; Hibernate's ddl-auto=validate only checks the columns an entity
// maps, not that it maps every column of the table, so this partial
// mapping coexisting with that later, fuller one is fine.
@Getter
@Setter
@Entity
@Table(name = "token_usage")
public class TokenUsageEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "reference_cycle", nullable = false)
    private LocalDate referenceCycle;

    @Column(name = "total_tokens", insertable = false, updatable = false)
    private Integer totalTokens;
}
