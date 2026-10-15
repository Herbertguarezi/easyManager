package com.guarezi.easymanager.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiPricingJpaRepository extends JpaRepository<ApiPricingEntity, UUID> {
    Optional<ApiPricingEntity> findFirstByOrderByEffectiveFromDesc();
}
