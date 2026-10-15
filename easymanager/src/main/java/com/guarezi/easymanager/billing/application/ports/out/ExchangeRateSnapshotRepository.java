package com.guarezi.easymanager.billing.application.ports.out;

import com.guarezi.easymanager.billing.domain.ExchangeRateSnapshot;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateSnapshotRepository {
    Optional<ExchangeRateSnapshot> findByReferenceDate(LocalDate referenceDate);

    ExchangeRateSnapshot save(ExchangeRateSnapshot snapshot);
}
