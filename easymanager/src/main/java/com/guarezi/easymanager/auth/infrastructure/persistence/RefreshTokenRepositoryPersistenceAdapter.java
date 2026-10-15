package com.guarezi.easymanager.auth.infrastructure.persistence;

import com.guarezi.easymanager.auth.application.ports.out.RefreshTokenRepository;
import com.guarezi.easymanager.auth.domain.RefreshToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RefreshTokenRepositoryPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;

    public RefreshTokenRepositoryPersistenceAdapter(RefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return toDomain(repository.save(toEntity(token)));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public List<RefreshToken> findActiveBySellerId(UUID sellerId) {
        return repository.findBySellerIdAndRevokedAtIsNull(sellerId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private RefreshToken toDomain(RefreshTokenEntity entity) {
        if (entity == null) return null;
        return new RefreshToken(entity.getId(), entity.getSellerId(), entity.getTokenHash(), entity.getExpiresAt(),
                entity.getCreatedAt(), entity.getRevokedAt(), entity.getReplacedBy());
    }

    private RefreshTokenEntity toEntity(RefreshToken token) {
        if (token == null) return null;
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(token.getId());
        entity.setSellerId(token.getSellerId());
        entity.setTokenHash(token.getTokenHash());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setCreatedAt(token.getCreatedAt());
        entity.setRevokedAt(token.getRevokedAt());
        entity.setReplacedBy(token.getReplacedBy());
        return entity;
    }
}
