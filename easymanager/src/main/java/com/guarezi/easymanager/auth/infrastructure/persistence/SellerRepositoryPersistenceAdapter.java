package com.guarezi.easymanager.auth.infrastructure.persistence;

import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.Seller;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SellerRepositoryPersistenceAdapter implements SellerRepository {

    private final SellerJpaRepository repository;

    public SellerRepositoryPersistenceAdapter(SellerJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Seller save(Seller seller) {
        return toDomain(repository.save(toEntity(seller)));
    }

    @Override
    public Optional<Seller> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Seller> findByEmail(String email) {
        return repository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    private Seller toDomain(SellerEntity entity) {
        if (entity == null) return null;

        Seller seller = new Seller();
        seller.setId(entity.getId());
        seller.setEmail(entity.getEmail());
        seller.setPasswordHash(entity.getPasswordHash());
        seller.setRole(entity.getRole());
        seller.setStatus(entity.getStatus());
        seller.setEmailConfirmedAt(entity.getEmailConfirmedAt());
        seller.setCnpj(entity.getCnpj());
        seller.setCnpjVerifiedAt(entity.getCnpjVerifiedAt());
        seller.setCreatedAt(entity.getCreatedAt());
        seller.setUpdatedAt(entity.getUpdatedAt());
        seller.setFailedLoginAttempts(entity.getFailedLoginAttempts());
        seller.setLockedUntil(entity.getLockedUntil());
        seller.setMfaSecret(entity.getMfaSecret());
        return seller;
    }

    private SellerEntity toEntity(Seller seller) {
        if (seller == null) return null;

        SellerEntity entity = new SellerEntity();
        entity.setId(seller.getId());
        entity.setEmail(seller.getEmail());
        entity.setPasswordHash(seller.getPasswordHash());
        entity.setRole(seller.getRole());
        entity.setStatus(seller.getStatus());
        entity.setEmailConfirmedAt(seller.getEmailConfirmedAt());
        entity.setCnpj(seller.getCnpj());
        entity.setCnpjVerifiedAt(seller.getCnpjVerifiedAt());
        entity.setCreatedAt(seller.getCreatedAt());
        entity.setUpdatedAt(seller.getUpdatedAt());
        entity.setFailedLoginAttempts(seller.getFailedLoginAttempts());
        entity.setLockedUntil(seller.getLockedUntil());
        entity.setMfaSecret(seller.getMfaSecret());
        return entity;
    }
}
