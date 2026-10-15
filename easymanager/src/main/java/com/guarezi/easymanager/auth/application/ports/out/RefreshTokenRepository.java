package com.guarezi.easymanager.auth.application.ports.out;

import com.guarezi.easymanager.auth.domain.RefreshToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findActiveBySellerId(UUID sellerId);
}
