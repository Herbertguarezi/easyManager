package com.guarezi.easymanager.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "api_pricing_history")
public class ApiPricingEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "input_price_usd_per_million", nullable = false)
    private BigDecimal inputPriceUsdPerMillion;

    @Column(name = "output_price_usd_per_million", nullable = false)
    private BigDecimal outputPriceUsdPerMillion;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
