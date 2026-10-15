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
public class PasswordResetToken {
    private UUID id;
    private UUID sellerId;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
}
