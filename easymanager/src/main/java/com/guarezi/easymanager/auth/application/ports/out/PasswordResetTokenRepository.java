package com.guarezi.easymanager.auth.application.ports.out;

import com.guarezi.easymanager.auth.domain.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
