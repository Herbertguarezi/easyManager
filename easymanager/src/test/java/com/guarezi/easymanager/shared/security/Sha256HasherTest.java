package com.guarezi.easymanager.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256HasherTest {

    @Test
    void sameInputProducesSameHash() {
        assertThat(Sha256Hasher.hashHex("a-raw-token")).isEqualTo(Sha256Hasher.hashHex("a-raw-token"));
    }

    @Test
    void differentInputProducesDifferentHash() {
        assertThat(Sha256Hasher.hashHex("token-a")).isNotEqualTo(Sha256Hasher.hashHex("token-b"));
    }

    @Test
    void neverReturnsTheRawInput() {
        assertThat(Sha256Hasher.hashHex("a-raw-token")).isNotEqualTo("a-raw-token");
    }

    @Test
    void producesA64CharacterHexString() {
        assertThat(Sha256Hasher.hashHex("anything")).hasSize(64).matches("[0-9a-f]{64}");
    }
}
