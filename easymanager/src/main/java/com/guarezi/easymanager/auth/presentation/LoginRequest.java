package com.guarezi.easymanager.auth.presentation;

// totpCode is required only for ADMIN accounts (RF-AU17); null/absent
// otherwise.
public record LoginRequest(String email, String password, String totpCode) {
}
