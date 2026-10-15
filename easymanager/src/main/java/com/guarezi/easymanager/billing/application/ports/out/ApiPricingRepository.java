package com.guarezi.easymanager.billing.application.ports.out;

import com.guarezi.easymanager.billing.domain.ApiPricing;

import java.util.Optional;

public interface ApiPricingRepository {
    // RN-TOKEN-008: the row with the latest effectiveFrom — the daily
    // calculation always uses the current price, never the history.
    Optional<ApiPricing> findCurrent();
}
