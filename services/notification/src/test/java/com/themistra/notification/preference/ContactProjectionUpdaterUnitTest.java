package com.themistra.notification.preference;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocked repository, no Spring context, no Docker. Real upsert/out-of-order/transaction behavior is
 * {@link ContactProjectionUpdaterIntegrationTest}'s own job; this class only proves
 * {@link ContactProjectionUpdater}'s own logic given a known
 * {@link ContactProjectionRepository#upsertEmail} result.
 */
class ContactProjectionUpdaterUnitTest {

    private final ContactProjectionRepository repository = mock(ContactProjectionRepository.class);
    private final ContactProjectionUpdater updater = new ContactProjectionUpdater(repository);

    @Test
    void upsertEmailDelegatesWithExactArgumentsAndReturnsTrueWhenAccepted() {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");
        when(repository.upsertEmail(accountUuid, "owner@example.com", occurredAt)).thenReturn(1);

        boolean result = updater.upsertEmail(accountUuid, "owner@example.com", occurredAt);

        assertThat(result).isTrue();
        verify(repository).upsertEmail(eq(accountUuid), eq("owner@example.com"), eq(occurredAt));
    }

    @Test
    void upsertEmailReturnsFalseWhenTheGuardRejectsAStaleWrite() {
        when(repository.upsertEmail(any(), any(), any())).thenReturn(0);

        boolean result = updater.upsertEmail(
                UUID.randomUUID(), "owner@example.com", Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result).isFalse();
    }

    /** Kimi Phase 8 Finding #4: proves the *mechanism*, not just behavior an integration test
     * already exercises - a future refactor moving transaction demarcation elsewhere could still
     * pass those behavioral tests while silently changing this method's own propagation. */
    @Test
    void upsertEmailUsesDefaultRequiredPropagation() throws NoSuchMethodException {
        Method method = ContactProjectionUpdater.class.getMethod(
                "upsertEmail", UUID.class, String.class, Instant.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).as("upsertEmail must be @Transactional").isNotNull();
        assertThat(transactional.propagation())
                .as("must never be REQUIRES_NEW/NOT_SUPPORTED - would break the same-transaction "
                        + "requirement with a future IdempotencyGuard.recordIfNew call")
                .isEqualTo(Propagation.REQUIRED);
    }

    /** Kimi Phase 11 Gap #1: a permanent, cheap static guard for the exact SQL shape the entire
     * out-of-order-rejection guarantee rests on - a future edit that weakens or removes the
     * {@code WHERE} clause would otherwise only be caught by re-running the Phase 10 manual
     * mutation test by hand. */
    @Test
    void upsertEmailNativeQueryContainsTheOutOfOrderGuard() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/notification/preference/ContactProjectionRepository.java"));

        assertThat(source).contains("ON CONFLICT");
        assertThat(source).contains("DO UPDATE");
        assertThat(source).contains("WHERE notifications.contact_projection.updated_at <= EXCLUDED.updated_at");
    }
}
