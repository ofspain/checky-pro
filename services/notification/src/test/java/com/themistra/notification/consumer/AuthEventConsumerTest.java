package com.themistra.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.notification.preference.ContactProjectionUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    /** Kimi Phase 11 Gap #1: a permanent, cheap static guard for the exact idempotency
     * short-circuit shape both behavioral tests above depend on - a future edit that reordered the
     * guard after the projection/dispatch calls, or removed it entirely, would otherwise only be
     * caught by re-running Phase 10's own manual mutation test by hand. */
    @Test
    void bothListenersShortCircuitOnTheIdempotencyGuardBeforeAnyOtherCall() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/notification/consumer/AuthEventConsumer.java"));

        assertThat(source).contains("if (!idempotencyGuard.recordIfNew(eventKey, event.purpose())) {");
        assertThat(source).contains("if (!idempotencyGuard.recordIfNew(eventKey, event.eventType())) {");
    }

    /** Kimi Phase 11 Gap #4: locks the topic strings and the transaction boundary at the
     * source-code level, not just via the integration tests' own observed behavior - a typo'd
     * topic string would otherwise only surface as a silently-missing message against a real
     * broker, not a test failure. */
    @Test
    void listenerMethodsAreAnnotatedWithTheCorrectTopicsAndAreTransactional() throws NoSuchMethodException {
        Method onEmailRequested = AuthEventConsumer.class.getMethod("onEmailRequested", String.class);
        Method onUserLifecycle = AuthEventConsumer.class.getMethod("onUserLifecycle", String.class);

        assertThat(onEmailRequested.getAnnotation(KafkaListener.class).topics())
                .containsExactly("auth.email.requested");
        assertThat(onUserLifecycle.getAnnotation(KafkaListener.class).topics())
                .containsExactly("auth.user.lifecycle");
        assertThat(onEmailRequested.getAnnotation(Transactional.class)).isNotNull();
        assertThat(onUserLifecycle.getAnnotation(Transactional.class)).isNotNull();
    }

    /** Kimi Phase 11 Gap #6: AC4's routing decision is based on {@code eventType}, not
     * {@code status} - proven here by deliberately mismatching them, since every other test
     * happens to pair them the way a real caller would. */
    @Test
    void dispatchDependsOnEventTypeNotStatusForARegisteredEventWithAnUnusualStatus() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        consumer.onUserLifecycle(lifecycleJson(accountUuid, "SUSPENDED", "a@example.com",
                "user.registered", occurredAt));

        verify(notificationDispatcher).dispatch(accountUuid, "user.registered", Map.of());
    }

    /** The converse of the above: {@code status=ACTIVE} alone must never trigger dispatch when
     * {@code eventType} says otherwise - the exact ambiguity this task's own eventType field was
     * added to resolve (Phase 1's own blocker). */
    @Test
    void noDispatchForAnActiveStatusEventWithANonRegisteredEventType() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        consumer.onUserLifecycle(lifecycleJson(accountUuid, "ACTIVE", "a@example.com",
                "user.reinstated", occurredAt));

        verifyNoInteractions(notificationDispatcher);
    }

    /** Kimi Phase 11 Gap #7: {@code upsertEmail}'s boolean return (accepted vs. rejected as stale)
     * is currently discarded, not branched on - locks that dispatch still happens even when the
     * projection write was rejected, so a future change that accidentally started skipping dispatch
     * on a stale projection would fail this test, not slip through silently. */
    @Test
    void dispatchStillHappensWhenTheProjectionUpsertIsRejectedAsStale() throws Exception {
        when(contactProjectionUpdater.upsertEmail(any(), any(), any())).thenReturn(false);
        UUID accountUuid = UUID.randomUUID();

        consumer.onEmailRequested(emailRequestedJson(accountUuid, "verify_email", "tok",
                "a@example.com", Instant.parse("2026-01-01T00:00:00Z")));

        verify(notificationDispatcher).dispatch(accountUuid, "verify_email", Map.of("token", "tok"));
    }
}
