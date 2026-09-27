package com.themistra.notification.consumer;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The task statement's own literal "unit-test the dedupe" instruction, plus {@code package.md} §8's
 * two named tests. Mocked repository, fixed {@code Clock} - no Spring context, no Docker. Real
 * concurrency/transaction-join/round-trip behavior is {@link IdempotencyGuardIntegrationTest}'s own
 * job; this class only proves {@link IdempotencyGuard}'s own logic given a known
 * {@link ProcessedEventRepository#insertIfNew} result.
 */
class IdempotencyGuardUnitTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private final ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    private final IdempotencyGuard guard = new IdempotencyGuard(repository, clock);

    @Test
    void shouldDedupeDuplicateEventDeliveryByEventKey() {
        when(repository.insertIfNew("key-1", "type-1", FIXED_INSTANT)).thenReturn(1);

        assertThat(guard.recordIfNew("key-1", "type-1")).isTrue();
    }

    @Test
    void shouldNotDoubleSendWhenSameEventRedelivered() {
        when(repository.insertIfNew("key-1", "type-1", FIXED_INSTANT)).thenReturn(0);

        assertThat(guard.recordIfNew("key-1", "type-1")).isFalse();
    }

    @Test
    void shouldUseTheInjectedClockNotWallClockTime() {
        when(repository.insertIfNew(any(), any(), any())).thenReturn(1);

        guard.recordIfNew("key-1", "type-1");

        verify(repository).insertIfNew(eq("key-1"), eq("type-1"), eq(FIXED_INSTANT));
    }
}
