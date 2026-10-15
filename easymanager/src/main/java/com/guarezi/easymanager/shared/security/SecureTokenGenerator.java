package com.guarezi.easymanager.shared.security;

import java.security.SecureRandom;
import java.util.Base64;

// Opaque, high-entropy, URL-safe tokens for one-time links (email
// confirmation, password reset) — 256 bits, never sequential/guessable.
public final class SecureTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private SecureTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
