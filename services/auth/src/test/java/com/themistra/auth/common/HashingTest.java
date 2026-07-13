package com.themistra.auth.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingTest {

    @Test
    void sameInputProducesSameHash() {
        assertThat(Hashing.sha256("value")).isEqualTo(Hashing.sha256("value"));
    }

    @Test
    void differentInputProducesDifferentHash() {
        assertThat(Hashing.sha256("value-a")).isNotEqualTo(Hashing.sha256("value-b"));
    }

    @Test
    void producesA64CharacterHexDigest() {
        String hash = Hashing.sha256("anything");
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }
}
