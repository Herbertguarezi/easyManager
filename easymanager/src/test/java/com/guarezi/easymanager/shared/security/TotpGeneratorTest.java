package com.guarezi.easymanager.shared.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TotpGeneratorTest {

    // RFC 6238 Appendix B, SHA-1 row: shared secret is the ASCII string
    // "12345678901234567890", time=59 (T=1, 30s step) yields the 8-digit
    // TOTP 94287082 — the last 6 digits (287082) is what a 6-digit
    // authenticator app would show. This is the published reference
    // vector the whole implementation is checked against, not just a
    // round-trip of this class's own encode/decode.
    private static final String RFC_TEST_SECRET_BASE32 =
            TotpGenerator.base32Encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesTheRfc6238PublishedTestVectorAtTimeFiftyNine() {
        assertThat(TotpGenerator.codeAt(RFC_TEST_SECRET_BASE32, 59)).isEqualTo("287082");
    }

    @Test
    void base32RoundTripsArbitraryBytes() {
        byte[] original = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, (byte) 255, 0, 127};
        String encoded = TotpGenerator.base32Encode(original);
        assertThat(TotpGenerator.base32Decode(encoded)).isEqualTo(original);
    }

    @Test
    void generateSecretProducesADifferentValueEachTime() {
        assertThat(TotpGenerator.generateSecret()).isNotEqualTo(TotpGenerator.generateSecret());
    }

    @Test
    void verifyCodeAcceptsTheCurrentCode() {
        String secret = TotpGenerator.generateSecret();
        long now = 1_700_000_000L;
        String code = TotpGenerator.codeAt(secret, now);

        assertThat(TotpGenerator.verifyCode(secret, code, now)).isTrue();
    }

    @Test
    void verifyCodeRejectsAWrongCode() {
        String secret = TotpGenerator.generateSecret();
        long now = 1_700_000_000L;
        String realCode = TotpGenerator.codeAt(secret, now);
        String wrongCode = realCode.equals("000000") ? "111111" : "000000";

        assertThat(TotpGenerator.verifyCode(secret, wrongCode, now)).isFalse();
    }

    @Test
    void verifyCodeToleratesOneStepOfClockDrift() {
        String secret = TotpGenerator.generateSecret();
        long now = 1_700_000_000L;
        String codeThirtySecondsAgo = TotpGenerator.codeAt(secret, now - 30);

        assertThat(TotpGenerator.verifyCode(secret, codeThirtySecondsAgo, now)).isTrue();
    }

    @Test
    void verifyCodeRejectsCodesOutsideTheToleranceWindow() {
        String secret = TotpGenerator.generateSecret();
        long now = 1_700_000_000L;
        String codeTwoMinutesAgo = TotpGenerator.codeAt(secret, now - 120);

        assertThat(TotpGenerator.verifyCode(secret, codeTwoMinutesAgo, now)).isFalse();
    }
}
