package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.application.ports.out.SubscriptionRepository;
import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Subscription;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SubscriptionRepositoryPersistenceAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository repository;

    public SubscriptionRepositoryPersistenceAdapter(SubscriptionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Subscription> findBySellerIdAndModule(UUID sellerId, Module module) {
        return repository.findBySellerIdAndModule(sellerId, module).map(this::toDomain);
    }

    @Override
    public List<Subscription> findByStatus(SubscriptionStatus status) {
        return repository.findByStatus(status).stream().map(this::toDomain).toList();
    }

    @Override
    public Subscription save(Subscription subscription) {
        return toDomain(repository.save(toEntity(subscription)));
    }

    private Subscription toDomain(SubscriptionEntity entity) {
        if (entity == null) return null;

        Subscription subscription = new Subscription();
        subscription.setId(entity.getId());
        subscription.setSellerId(entity.getSellerId());
        subscription.setModule(entity.getModule());
        subscription.setPlanId(entity.getPlanId());
        subscription.setStatus(entity.getStatus());
        subscription.setBillingCycle(entity.getBillingCycle());
        subscription.setAnniversaryDate(entity.getAnniversaryDate());
        subscription.setCurrentCycleStartDate(entity.getCurrentCycleStartDate());
        subscription.setCurrentCycleEndDate(entity.getCurrentCycleEndDate());
        subscription.setCurrentTokenPeriodStartDate(entity.getCurrentTokenPeriodStartDate());
        subscription.setCurrentTokenPeriodEndDate(entity.getCurrentTokenPeriodEndDate());
        subscription.setComboGroupId(entity.getComboGroupId());
        subscription.setCancellationRequestedAt(entity.getCancellationRequestedAt());
        subscription.setBlockedAt(entity.getBlockedAt());
        subscription.setScheduledDeletionDate(entity.getScheduledDeletionDate());
        subscription.setTrialExpiresAt(entity.getTrialExpiresAt());
        subscription.setCreatedAt(entity.getCreatedAt());
        subscription.setUpdatedAt(entity.getUpdatedAt());
        return subscription;
    }

    private SubscriptionEntity toEntity(Subscription subscription) {
        if (subscription == null) return null;

        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setId(subscription.getId());
        entity.setSellerId(subscription.getSellerId());
        entity.setModule(subscription.getModule());
        entity.setPlanId(subscription.getPlanId());
        entity.setStatus(subscription.getStatus());
        entity.setBillingCycle(subscription.getBillingCycle());
        entity.setAnniversaryDate(subscription.getAnniversaryDate());
        entity.setCurrentCycleStartDate(subscription.getCurrentCycleStartDate());
        entity.setCurrentCycleEndDate(subscription.getCurrentCycleEndDate());
        entity.setCurrentTokenPeriodStartDate(subscription.getCurrentTokenPeriodStartDate());
        entity.setCurrentTokenPeriodEndDate(subscription.getCurrentTokenPeriodEndDate());
        entity.setComboGroupId(subscription.getComboGroupId());
        entity.setCancellationRequestedAt(subscription.getCancellationRequestedAt());
        entity.setBlockedAt(subscription.getBlockedAt());
        entity.setScheduledDeletionDate(subscription.getScheduledDeletionDate());
        entity.setTrialExpiresAt(subscription.getTrialExpiresAt());
        entity.setCreatedAt(subscription.getCreatedAt());
        entity.setUpdatedAt(subscription.getUpdatedAt());
        return entity;
    }
}
