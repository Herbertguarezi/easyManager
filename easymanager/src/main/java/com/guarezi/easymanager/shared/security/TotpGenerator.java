package com.guarezi.easymanager.shared.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

// RFC 6238 TOTP (HMAC-SHA1, 30s step, 6 digits) — implemented directly
// (RFC 4226/6238 + RFC 4648 Base32) rather than adding a dependency for
// an algorithm this small and this precisely specified; correctness is
// checked against the RFC's own published test vector, not just a
// round-trip of this class's own encode/decode (see TotpGeneratorTest).
public final class TotpGenerator {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int CLOCK_DRIFT_STEPS_TOLERATED = 1;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;

    private TotpGenerator() {
    }

    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static String currentCode(String base32Secret) {
        return codeAt(base32Secret, Instant.now().getEpochSecond());
    }

    public static boolean verifyCode(String base32Secret, String code) {
        return verifyCode(base32Secret, code, Instant.now().getEpochSecond());
    }

    static boolean verifyCode(String base32Secret, String code, long epochSeconds) {
        for (int drift = -CLOCK_DRIFT_STEPS_TOLERATED; drift <= CLOCK_DRIFT_STEPS_TOLERATED; drift++) {
            if (codeAt(base32Secret, epochSeconds + (long) drift * TIME_STEP_SECONDS).equals(code)) {
                return true;
            }
        }
        return false;
    }

    static String codeAt(String base32Secret, long epochSeconds) {
        long counter = epochSeconds / TIME_STEP_SECONDS;
        byte[] key = base32Decode(base32Secret);
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash = hmacSha1(key, counterBytes);

        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int code = binary % (int) Math.pow(10, DIGITS);
        return String.format("%0" + DIGITS + "d", code);
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not compute HMAC-SHA1", e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int bits = 0;
        int value = 0;
        for (byte b : data) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                result.append(BASE32_ALPHABET.charAt((value >>> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            result.append(BASE32_ALPHABET.charAt((value << (5 - bits)) & 0x1F));
        }
        return result.toString();
    }

    static byte[] base32Decode(String encoded) {
        String cleaned = encoded.trim().toUpperCase().replace("=", "");
        int bits = 0;
        int value = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (char c : cleaned.toCharArray()) {
            int index = BASE32_ALPHABET.indexOf(c);
            if (index < 0) {
                continue;
            }
            value = (value << 5) | index;
            bits += 5;
            if (bits >= 8) {
                out.write((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
