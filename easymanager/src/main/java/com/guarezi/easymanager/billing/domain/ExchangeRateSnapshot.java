package com.guarezi.easymanager.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// RN-TOKEN-002: one row per day, written by the exchange-rate job before
// it recalculates plans.currentMonthTokenQuota — gives traceability of
// which rate produced which quota on a given day.
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ExchangeRateSnapshot {
    private UUID id;
    private BigDecimal usdBrlRate;
    private LocalDate referenceDate;
    private Instant createdAt;
}
