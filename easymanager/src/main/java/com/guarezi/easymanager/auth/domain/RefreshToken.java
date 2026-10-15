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
public class RefreshToken {
    private UUID id;
    private UUID sellerId;
    private String tokenHash;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant revokedAt;
    private UUID replacedBy;
}
