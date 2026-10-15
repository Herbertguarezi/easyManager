package com.guarezi.easymanager.billing.application.ports.out;

import java.time.LocalDate;
import java.util.UUID;

// Read-only aggregates over token_usage/token_packages for
// EntitlementService.getTokenBalance — not a full recording port (that's
// Fase 7's job, once the Ads Analyzer actually calls the Anthropic API).
public interface TokenLedgerRepository {
    // Trial pool (RN-TRIAL-004): isolated from any billing cycle, summed
    // over every token_usage row ever recorded for this subscription.
    long sumAllTokensUsed(UUID subscriptionId);

    long sumTokensUsedInCycle(UUID subscriptionId, LocalDate referenceCycle);

    long sumTokensPurchasedInCycle(UUID subscriptionId, LocalDate referenceCycle);
}
