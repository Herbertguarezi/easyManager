package com.guarezi.easymanager.billing.domain;

// software-engineering.md sec. 4.6: a fixed code enum, not a table — maps
// to the two existing bounded contexts (products, ads).
public enum Module {
    PRODUCTS,
    ADS_ANALYZER;

    // RN-RET-001: retention window after cancellation/non-payment, before
    // permanent deletion.
    public int retentionDays() {
        return this == ADS_ANALYZER ? 7 : 30;
    }

    // RN-TRIAL-006 vs RN-PROD-002: only the ADS_ANALYZER trial expires
    // after a fixed number of days; the PRODUCTS trial has no time limit
    // (only a product/Store count cap, RN-PROD-003).
    public boolean hasTrialExpiry() {
        return this == ADS_ANALYZER;
    }

    // RN-TOKEN-001/005: only ADS_ANALYZER has a token quota/balance
    // concept at all — PRODUCTS plans have no variable cost.
    public boolean hasTokenConcept() {
        return this == ADS_ANALYZER;
    }
}
