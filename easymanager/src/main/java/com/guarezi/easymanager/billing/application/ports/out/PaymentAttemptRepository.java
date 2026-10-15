package com.guarezi.easymanager.billing.application.ports.out;

import java.util.UUID;

// Read-only, for the delinquency job's guard (RN-INAD-001/002) — nothing
// writes payment_attempts yet (no payment gateway is integrated in any
// phase so far); this job only counts rows some future process will
// write.
public interface PaymentAttemptRepository {
    long countDeclinedRenewalAttempts(UUID subscriptionId);
}
