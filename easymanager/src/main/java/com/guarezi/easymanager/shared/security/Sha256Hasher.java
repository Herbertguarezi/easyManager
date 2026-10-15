package com.guarezi.easymanager.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Fast, deterministic hashing for high-entropy opaque values (random
// tokens, webhook payloads) — never for passwords, which need a slow,
// salted hash (see auth.infrastructure.security.PasswordEncoderConfig).
public final class Sha256Hasher {

    private Sha256Hasher() {
    }

    public static String hashHex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
