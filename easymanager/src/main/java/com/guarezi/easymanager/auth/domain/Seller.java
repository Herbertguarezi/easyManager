package com.guarezi.easymanager.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Seller {
    private UUID id;
    private String email;
    private String passwordHash;
    private Role role;
    private SellerStatus status;
    private Instant emailConfirmedAt;
    private String cnpj;
    private Instant cnpjVerifiedAt;
    private Instant createdAt;
    private Instant updatedAt;
    // RF-AU15: progressive per-account login throttling (see PROGRESS.md
    // Fase 1 decisions log for the exact backoff chosen).
    private int failedLoginAttempts;
    private Instant lockedUntil;
    // RF-AU17: TOTP secret for MFA, required for ADMIN accounts (base32,
    // see shared.security.TotpGenerator). Null = MFA not provisioned yet.
    private String mfaSecret;
}
