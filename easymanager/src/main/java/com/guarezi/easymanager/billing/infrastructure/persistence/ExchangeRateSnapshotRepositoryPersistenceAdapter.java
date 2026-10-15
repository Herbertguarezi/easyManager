package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.application.ports.out.ExchangeRateSnapshotRepository;
import com.guarezi.easymanager.billing.domain.ExchangeRateSnapshot;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class ExchangeRateSnapshotRepositoryPersistenceAdapter implements ExchangeRateSnapshotRepository {

    private final ExchangeRateSnapshotJpaRepository repository;

    public ExchangeRateSnapshotRepositoryPersistenceAdapter(ExchangeRateSnapshotJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ExchangeRateSnapshot> findByReferenceDate(LocalDate referenceDate) {
        return repository.findByReferenceDate(referenceDate).map(this::toDomain);
    }

    @Override
    public ExchangeRateSnapshot save(ExchangeRateSnapshot snapshot) {
        return toDomain(repository.save(toEntity(snapshot)));
    }

    private ExchangeRateSnapshot toDomain(ExchangeRateSnapshotEntity entity) {
        if (entity == null) return null;

        ExchangeRateSnapshot snapshot = new ExchangeRateSnapshot();
        snapshot.setId(entity.getId());
        snapshot.setUsdBrlRate(entity.getUsdBrlRate());
        snapshot.setReferenceDate(entity.getReferenceDate());
        snapshot.setCreatedAt(entity.getCreatedAt());
        return snapshot;
    }

    private ExchangeRateSnapshotEntity toEntity(ExchangeRateSnapshot snapshot) {
        if (snapshot == null) return null;

        ExchangeRateSnapshotEntity entity = new ExchangeRateSnapshotEntity();
        entity.setId(snapshot.getId());
        entity.setUsdBrlRate(snapshot.getUsdBrlRate());
        entity.setReferenceDate(snapshot.getReferenceDate());
        entity.setCreatedAt(snapshot.getCreatedAt());
        return entity;
    }
}
