package com.guarezi.easymanager.auth.application.ports.in;

public interface LoginUseCase {
    // totpCode is ignored for non-ADMIN sellers; required and verified
    // for ADMIN (RF-AU17) — null/blank is fine when not applicable.
    AuthTokens login(String email, String rawPassword, String totpCode);
}
