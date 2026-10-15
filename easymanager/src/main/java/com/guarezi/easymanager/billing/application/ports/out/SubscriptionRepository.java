package com.guarezi.easymanager.billing.application.ports.out;

import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Subscription;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {
    // Empty = SEM_ASSINATURA (no row for this seller x module yet, or the
    // row was never created) — never modeled as a Subscription instance.
    Optional<Subscription> findBySellerIdAndModule(UUID sellerId, Module module);

    // Fase 1.5, Block D: the daily jobs scan across every seller by
    // status, not per-seller like the method above.
    List<Subscription> findByStatus(SubscriptionStatus status);

    Subscription save(Subscription subscription);
}
