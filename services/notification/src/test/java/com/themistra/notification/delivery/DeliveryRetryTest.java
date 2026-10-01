package com.themistra.notification.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain JUnit, no Spring context, no Docker. */
class DeliveryRetryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void rescheduleMutatesAttemptAndNextAttemptAt() {
        DeliveryRetry retry = new DeliveryRetry("key-1", UUID.randomUUID(), "EMAIL", "verify_email",
                "{}", (short) 1, CREATED_AT.plusSeconds(30), CREATED_AT);

        Instant newNextAttemptAt = CREATED_AT.plusSeconds(90);
        retry.reschedule((short) 2, newNextAttemptAt);

        assertThat(retry.getAttempt()).isEqualTo((short) 2);
        assertThat(retry.getNextAttemptAt()).isEqualTo(newNextAttemptAt);
    }

    /** Kimi Phase 3 Finding #2: {@code eventDataJson} carries the same raw token the original event
     * did - {@code toString()} must never print it, so a careless future {@code log.info("{}", retry)}
     * can't leak it. Confirms the default {@code Object.toString()} is still in effect (no override
     * was ever added), rather than asserting a specific string shape. */
    @Test
    void toStringNeverIncludesEventDataJson() {
        DeliveryRetry retry = new DeliveryRetry("key-2", UUID.randomUUID(), "EMAIL", "verify_email",
                "{\"token\":\"super-secret-token\"}", (short) 1, CREATED_AT.plusSeconds(30), CREATED_AT);

        assertThat(retry.toString()).doesNotContain("super-secret-token");
    }
}
