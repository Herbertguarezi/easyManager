package com.guarezi.easymanager.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// RN-TOKEN-008: versioned history of the Anthropic API's own price —
// append-only, never UPDATEd (a new price change is a new row). The
// "current" price is always the one with the latest effectiveFrom.
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ApiPricing {
    private UUID id;
    private BigDecimal inputPriceUsdPerMillion;
    private BigDecimal outputPriceUsdPerMillion;
    private Instant effectiveFrom;
    private UUID updatedBy;
    private Instant createdAt;
}
