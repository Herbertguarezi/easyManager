package com.guarezi.easymanager.auth.application.ports.out;

import com.guarezi.easymanager.auth.domain.EmailConfirmationToken;

import java.util.Optional;

public interface EmailConfirmationTokenRepository {
    EmailConfirmationToken save(EmailConfirmationToken token);

    Optional<EmailConfirmationToken> findByTokenHash(String tokenHash);
}
