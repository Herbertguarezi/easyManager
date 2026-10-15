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
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "exchange_rate_snapshots")
public class ExchangeRateSnapshotEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "usd_brl_rate", nullable = false)
    private BigDecimal usdBrlRate;

    @Column(name = "reference_date", nullable = false, unique = true)
    private LocalDate referenceDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
