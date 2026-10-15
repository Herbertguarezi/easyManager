package com.guarezi.easymanager.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// RN-PLAN-002/003: 6 fixed rows, module x tier, seeded in V5 — never
// created/edited by application code in this phase (only the daily
// exchange-rate job, Fase 1.5 Block D, touches currentMonthTokenQuota).
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Plan {
    private UUID id;
    private Module module;
    private String name;
    private int storesLimit;
    private BigDecimal monthlyPrice;
    private BigDecimal targetMargin;
    private Long currentMonthTokenQuota;
    private Instant createdAt;
    private Instant updatedAt;
}
