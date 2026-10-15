package com.guarezi.easymanager.billing.domain;

// RN-SUB-003.
public enum BillingCycle {
    MONTHLY(1),
    QUARTERLY(3),
    SEMIANNUAL(6),
    ANNUAL(12);

    private final int months;

    BillingCycle(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
