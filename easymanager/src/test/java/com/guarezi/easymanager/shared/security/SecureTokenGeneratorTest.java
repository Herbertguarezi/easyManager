package com.guarezi.easymanager.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenGeneratorTest {

    @Test
    void generatesAReasonablyLongToken() {
        assertThat(SecureTokenGenerator.generate().length()).isGreaterThanOrEqualTo(32);
    }

    @Test
    void generatesADifferentTokenEachTime() {
        assertThat(SecureTokenGenerator.generate()).isNotEqualTo(SecureTokenGenerator.generate());
    }

    @Test
    void generatesAUrlSafeToken() {
        assertThat(SecureTokenGenerator.generate()).matches("[A-Za-z0-9_-]+");
    }
}
