package com.themistra.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.notification.preference.ContactProjectionUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code package.md} §8's 3 named tests
 * ({@code shouldSendVerificationEmailOnAuthEmailRequestedVerify},
 * {@code shouldSendPasswordResetEmailOnAuthEmailRequestedReset},
 * {@code shouldWelcomeUserOnUserRegistered}), proving routing + dispatch-call correctness only, not
 * an actual send (frozen brief's own Scope note) - plus Kimi Phase 8 Finding #1 (idempotency key
 * format), Finding #2/#3 (non-dispatched-but-projected branches, idempotent short-circuit). Mocked
 * collaborators, no Spring context, no Docker - real end-to-end wiring against a live broker is
 * {@link AuthEventConsumerIntegrationTest}'s own job.
 */
class AuthEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
    private final ContactProjectionUpdater contactProjectionUpdater = mock(ContactProjectionUpdater.class);
    private final NotificationDispatcher notificationDispatcher = mock(NotificationDispatcher.class);
    private final AuthEventConsumer consumer =
            new AuthEventConsumer(objectMapper, idempotencyGuard, contactProjectionUpdater, notificationDispatcher);

    @BeforeEach
    void defaultStubs() {
        when(idempotencyGuard.recordIfNew(anyString(), anyString())).thenReturn(true);
        when(contactProjectionUpdater.upsertEmail(any(), any(), any())).thenReturn(true);
    }

    private static String emailRequestedJson(UUID accountUuid, String purpose, String token,
                                              String email, Instant occurredAt) {
        return "{\"accountUuid\":\"" + accountUuid + "\",\"purpose\":\"" + purpose + "\","
                + "\"token\":\"" + token + "\",\"email\":\"" + email + "\","
                + "\"occurredAt\":\"" + occurredAt + "\"}";
    }

    private static String lifecycleJson(UUID accountUuid, String status, String email,
                                         String eventType, Instant occurredAt) {
        return "{\"accountUuid\":\"" + accountUuid + "\",\"status\":\"" + status + "\","
                + "\"email\":\"" + email + "\",\"eventType\":\"" + eventType + "\","
                + "\"occurredAt\":\"" + occurredAt + "\"}";
    }

    @Test
    void shouldSendVerificationEmailOnAuthEmailRequestedVerify() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        consumer.onEmailRequested(emailRequestedJson(accountUuid, "verify_email", "tok-1",
                "a@example.com", occurredAt));

        verify(notificationDispatcher).dispatch(accountUuid, "verify_email", Map.of("token", "tok-1"));
    }

    @Test
    void shouldSendPasswordResetEmailOnAuthEmailRequestedReset() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        consumer.onEmailRequested(emailRequestedJson(accountUuid, "password_reset", "tok-2",
                "a@example.com", occurredAt));

        verify(notificationDispatcher).dispatch(accountUuid, "password_reset", Map.of("token", "tok-2"));
    }

    @Test
    void shouldWelcomeUserOnUserRegistered() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        consumer.onUserLifecycle(lifecycleJson(accountUuid, "ACTIVE", "a@example.com",
                "user.registered", occurredAt));

        verify(notificationDispatcher).dispatch(accountUuid, "user.registered", Map.of());
    }

    /** Kimi Phase 8 Finding #1: pins the exact idempotency key format for the email-requested
     * topic, not just "some string was passed". */
    @Test
    void idempotencyKeyForEmailRequestedIsAccountUuidPurposeOccurredAt() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-03-04T05:06:07Z");

        consumer.onEmailRequested(emailRequestedJson(accountUuid, "verify_email", "tok",
                "a@example.com", occurredAt));

        verify(idempotencyGuard).recordIfNew(accountUuid + ":verify_email:" + occurredAt, "verify_email");
    }

    /** Kimi Phase 8 Finding #1: pins the exact idempotency key format for the lifecycle topic. */
    @Test
    void idempotencyKeyForUserLifecycleIsAccountUuidEventTypeOccurredAt() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-03-04T05:06:07Z");

        consumer.onUserLifecycle(lifecycleJson(accountUuid, "LOCKED", "a@example.com",
                "user.locked", occurredAt));

        verify(idempotencyGuard).recordIfNew(accountUuid + ":user.locked:" + occurredAt, "user.locked");
    }

    /** AC4 / Kimi Phase 8 Finding #3: an unrecognized purpose is still deduped and projected, never
     * dispatched. */
    @Test
    void unknownPurposeIsProjectedButNeverDispatched() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        consumer.onEmailRequested(emailRequestedJson(accountUuid, "mystery_purpose", "tok",
                "a@example.com", occurredAt));

        verify(contactProjectionUpdater).upsertEmail(accountUuid, "a@example.com", occurredAt);
        verifyNoInteractions(notificationDispatcher);
    }

    /** AC4 / Kimi Phase 8 Finding #3: the 5 non-registration eventType values are still deduped and
     * projected, never dispatched - proven here with one representative value. */
    @Test
    void nonRegisteredEventTypeIsProjectedButNeverDispatched() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        consumer.onUserLifecycle(lifecycleJson(accountUuid, "LOCKED", "a@example.com",
                "user.locked", occurredAt));

        verify(contactProjectionUpdater).upsertEmail(accountUuid, "a@example.com", occurredAt);
        verifyNoInteractions(notificationDispatcher);
    }

    /** AC2 / Kimi Phase 8 Finding #3: a `false` from the guard short-circuits everything - no
     * projection update, no dispatch - for the email-requested topic. */
    @Test
    void duplicateEmailRequestedEventUpdatesNeitherProjectionNorDispatcher() throws Exception {
        when(idempotencyGuard.recordIfNew(anyString(), anyString())).thenReturn(false);
        UUID accountUuid = UUID.randomUUID();

        consumer.onEmailRequested(emailRequestedJson(accountUuid, "verify_email", "tok",
                "a@example.com", Instant.parse("2026-01-01T00:00:00Z")));

        verifyNoInteractions(contactProjectionUpdater);
        verifyNoInteractions(notificationDispatcher);
    }

    /** Same as above, for the lifecycle topic. */
    @Test
    void duplicateLifecycleEventUpdatesNeitherProjectionNorDispatcher() throws Exception {
        when(idempotencyGuard.recordIfNew(anyString(), anyString())).thenReturn(false);
        UUID accountUuid = UUID.randomUUID();

        consumer.onUserLifecycle(lifecycleJson(accountUuid, "ACTIVE", "a@example.com",
                "user.registered", Instant.parse("2026-01-01T00:00:00Z")));

        verifyNoInteractions(contactProjectionUpdater);
        verifyNoInteractions(notificationDispatcher);
    }

    /** AC3: the projection is refreshed for a non-duplicate message even when it also ends up
     * dispatched - the projection update is purpose-agnostic, not conditional on dispatch. */
    @Test
    void projectionIsRefreshedForADispatchedEmailRequestedEventToo() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        consumer.onEmailRequested(emailRequestedJson(accountUuid, "verify_email", "tok",
                "a@example.com", occurredAt));

        verify(contactProjectionUpdater).upsertEmail(accountUuid, "a@example.com", occurredAt);
    }
}
