package com.themistra.auth.common;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityBeansConfigTest {

    private final PasswordEncoder encoder = new SecurityBeansConfig().passwordEncoder();

    @Test
    void encodesSelfDescribingBcryptHashes() {
        String hash = encoder.encode("correct horse battery staple");

        assertThat(hash).startsWith("{bcrypt}$2");
        assertThat(encoder.matches("correct horse battery staple", hash)).isTrue();
        assertThat(encoder.matches("wrong password", hash)).isFalse();
    }

    @Test
    void matchesLegacyUnprefixedBcryptHashes() {
        // hashes written before the delegating wrapper (or imported) still verify
        String raw = "correct horse battery staple";
        String unprefixed = encoder.encode(raw).substring("{bcrypt}".length());

        assertThat(encoder.matches(raw, unprefixed)).isTrue();
    }
}
