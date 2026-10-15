package com.guarezi.easymanager.billing.infrastructure.persistence;

import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, UUID> {
    Optional<SubscriptionEntity> findBySellerIdAndModule(UUID sellerId, Module module);

    List<SubscriptionEntity> findByStatus(SubscriptionStatus status);
}
