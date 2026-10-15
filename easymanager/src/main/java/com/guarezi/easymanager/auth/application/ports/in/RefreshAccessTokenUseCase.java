package com.guarezi.easymanager.auth.application.ports.in;

public interface RefreshAccessTokenUseCase {
    AuthTokens refresh(String rawRefreshToken);
}
