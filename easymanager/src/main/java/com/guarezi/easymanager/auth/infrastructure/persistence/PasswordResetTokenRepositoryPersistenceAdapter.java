package com.guarezi.easymanager.auth.infrastructure.persistence;

import com.guarezi.easymanager.auth.application.ports.out.PasswordResetTokenRepository;
import com.guarezi.easymanager.auth.domain.PasswordResetToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PasswordResetTokenRepositoryPersistenceAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository repository;

    public PasswordResetTokenRepositoryPersistenceAdapter(PasswordResetTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return toDomain(repository.save(toEntity(token)));
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    private PasswordResetToken toDomain(PasswordResetTokenEntity entity) {
        if (entity == null) return null;
        return new PasswordResetToken(entity.getId(), entity.getSellerId(), entity.getTokenHash(),
                entity.getExpiresAt(), entity.getUsedAt());
    }

    private PasswordResetTokenEntity toEntity(PasswordResetToken token) {
        if (token == null) return null;
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setId(token.getId());
        entity.setSellerId(token.getSellerId());
        entity.setTokenHash(token.getTokenHash());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setUsedAt(token.getUsedAt());
        return entity;
    }
}
