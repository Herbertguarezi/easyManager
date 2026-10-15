package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.domain.Module;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "plans")
public class PlanEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Module module;

    @Column(nullable = false)
    private String name;

    @Column(name = "stores_limit", nullable = false)
    private int storesLimit;

    @Column(name = "monthly_price", nullable = false)
    private BigDecimal monthlyPrice;

    @Column(name = "target_margin")
    private BigDecimal targetMargin;

    @Column(name = "current_month_token_quota")
    private Long currentMonthTokenQuota;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
