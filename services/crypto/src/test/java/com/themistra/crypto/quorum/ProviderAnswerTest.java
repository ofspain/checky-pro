package com.themistra.crypto.quorum;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 11 Gap 2: direct coverage of {@link ProviderAnswer}'s compact-constructor null guards,
 * isolated from {@code QuorumDecisionServiceTest}'s only-indirect exercise of them. */
class ProviderAnswerTest {

    @Test
    void rejectsANullProvider() {
        assertThatThrownBy(() -> new ProviderAnswer<>(null, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void rejectsANullValue() {
        assertThatThrownBy(() -> new ProviderAnswer<String>("alchemy", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("value");
    }
}
