package com.guarezi.easymanager.auth.infrastructure.persistence;

import com.guarezi.easymanager.auth.application.ports.out.EmailConfirmationTokenRepository;
import com.guarezi.easymanager.auth.domain.EmailConfirmationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EmailConfirmationTokenRepositoryPersistenceAdapter implements EmailConfirmationTokenRepository {

    private final EmailConfirmationTokenJpaRepository repository;

    public EmailConfirmationTokenRepositoryPersistenceAdapter(EmailConfirmationTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public EmailConfirmationToken save(EmailConfirmationToken token) {
        return toDomain(repository.save(toEntity(token)));
    }

    @Override
    public Optional<EmailConfirmationToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    private EmailConfirmationToken toDomain(EmailConfirmationTokenEntity entity) {
        if (entity == null) return null;
        return new EmailConfirmationToken(
                entity.getId(), entity.getSellerId(), entity.getTokenHash(), entity.getExpiresAt(), entity.getUsedAt());
    }

    private EmailConfirmationTokenEntity toEntity(EmailConfirmationToken token) {
        if (token == null) return null;
        EmailConfirmationTokenEntity entity = new EmailConfirmationTokenEntity();
        entity.setId(token.getId());
        entity.setSellerId(token.getSellerId());
        entity.setTokenHash(token.getTokenHash());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setUsedAt(token.getUsedAt());
        return entity;
    }
}
