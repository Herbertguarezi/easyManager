package com.guarezi.easymanager.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerJpaRepository extends JpaRepository<SellerEntity, UUID> {
    Optional<SellerEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
