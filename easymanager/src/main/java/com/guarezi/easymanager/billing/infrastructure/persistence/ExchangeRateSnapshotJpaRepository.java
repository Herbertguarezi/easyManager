package com.guarezi.easymanager.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExchangeRateSnapshotJpaRepository extends JpaRepository<ExchangeRateSnapshotEntity, UUID> {
    Optional<ExchangeRateSnapshotEntity> findByReferenceDate(LocalDate referenceDate);
}
