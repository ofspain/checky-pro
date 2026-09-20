package com.themistra.auth.mfa;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain-JUnit unit tests for {@link RecoveryCode} — entity-level behavior only. Persistence
 * behavior (mapping, {@code markUsed}'s atomic update, real queries) is covered by
 * {@link MfaPersistenceIntegrationTest}. There is deliberately no mutator test for {@code usedAt}
 * — {@link RecoveryCode} has none, by design (see its class Javadoc).
 */
class RecoveryCodeTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test // AC3
    void createStartsUnused() {
        RecoveryCode code = RecoveryCode.create(1L, "a".repeat(64), CREATED_AT);

        assertThat(code.getAccountId()).isEqualTo(1L);
        assertThat(code.getCodeHash()).isEqualTo("a".repeat(64));
        assertThat(code.getUsedAt()).isNull();
        assertThat(code.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test // Phase 8/9 fix: create() rejects null required arguments
    void createRejectsNullArguments() {
        assertThatThrownBy(() -> RecoveryCode.create(null, "a".repeat(64), CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RecoveryCode.create(1L, null, CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RecoveryCode.create(1L, "a".repeat(64), null))
                .isInstanceOf(NullPointerException.class);
    }
}
