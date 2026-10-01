package com.themistra.notification.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.notification.channel.NotificationChannel;
import com.themistra.notification.common.SecretSafeLogging;
import com.themistra.notification.common.config.RetryProperties;
import com.themistra.notification.preference.ContactProjectionUpdater;
import com.themistra.notification.preference.PreferenceResolver;
import com.themistra.notification.template.TemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

/**
 * Mocked collaborators, no Spring context, no Docker. Real rendering/persistence/preference
 * round-trips are {@link DeliveryOrchestratorIntegrationTest}'s own job; this class proves
 * {@link DeliveryOrchestrator}'s own orchestration logic given known collaborator responses -
 * every row of the frozen brief's pinned outcome-decision-table (Phase 3 Finding #4), AC9's "never
 * throws" guarantee across every failure shape Kimi's Phase 8 review raised (Findings #4/#6/#7),
 * and the Phase 9 fixes (displayName wiring, Finding #2; fallback FAILED row, Finding #4).
 */
class DeliveryOrchestratorTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private final PreferenceResolver preferenceResolver = mock(PreferenceResolver.class);
    private final TemplateRenderer templateRenderer = mock(TemplateRenderer.class);
    private final ContactProjectionUpdater contactProjectionUpdater = mock(ContactProjectionUpdater.class);
    private final DeliveryLogRepository deliveryLogRepository = mock(DeliveryLogRepository.class);
    private final DeliveryRetryRepository deliveryRetryRepository = mock(DeliveryRetryRepository.class);
    // Real, concrete values (not mocked) - maxAttempts=3 gives transient-failure tests real
    // exhaustion-boundary room; a real ObjectMapper is a stateless utility, not a collaborator.
    private final RetryProperties retryProperties = new RetryProperties(3, 30, 3600, 30);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationChannel emailChannel = mock(NotificationChannel.class);
    private final NotificationChannel inAppChannel = mock(NotificationChannel.class);

    private DeliveryOrchestrator orchestrator;

    @BeforeEach
    void wireDefaultOrchestrator() {
        when(emailChannel.channel()).thenReturn("EMAIL");
        when(inAppChannel.channel()).thenReturn("IN_APP");
        orchestrator = new DeliveryOrchestrator(preferenceResolver, templateRenderer, contactProjectionUpdater,
                deliveryLogRepository, deliveryRetryRepository, retryProperties, objectMapper, CLOCK,
                List.of(emailChannel, inAppChannel));
    }

    private static TemplateRenderer.RenderedMessage message(int version) {
        return new TemplateRenderer.RenderedMessage("subject", "body", version);
    }

    // --- AC10 / Finding #1: transaction boundary -------------------------------------------

    @Test
    void dispatchIsAnnotatedTransactionalWithDefaultRequiredPropagation() throws NoSuchMethodException {
        Method dispatch = DeliveryOrchestrator.class.getMethod(
                "dispatch", UUID.class, String.class, Map.class);
        Transactional transactional = dispatch.getAnnotation(Transactional.class);

        assertThat(transactional).as("dispatch must be @Transactional (AC10)").isNotNull();
        assertThat(transactional.propagation())
                .as("must join the caller's already-open transaction, not start a new one")
                .isEqualTo(Propagation.REQUIRED);
    }

    // --- AC2: unknown notificationKind is a true no-op -------------------------------------

    @Test
    void unknownNotificationKindWritesNoRowsAndTouchesNoCollaborator() {
        UUID accountUuid = UUID.randomUUID();

        assertThatCode(() -> orchestrator.dispatch(accountUuid, "does.not.exist",
                Map.of("sourceEventKey", "k1"))).doesNotThrowAnyException();

        verifyNoInteractions(deliveryLogRepository, contactProjectionUpdater, preferenceResolver, templateRenderer);
        verify(emailChannel, never()).send(any(), any(), any(), any());
        verify(inAppChannel, never()).send(any(), any(), any(), any());
    }

    @Test
    void nullNotificationKindWritesNoRowsAndTouchesNoCollaborator() {
        UUID accountUuid = UUID.randomUUID();

        assertThatCode(() -> orchestrator.dispatch(accountUuid, null, Map.of("sourceEventKey", "k1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(deliveryLogRepository, contactProjectionUpdater, preferenceResolver, templateRenderer);
        verify(emailChannel, never()).send(any(), any(), any(), any());
        verify(inAppChannel, never()).send(any(), any(), any(), any());
    }

    // --- R11 named test: every attempt and outcome is recorded with the required fields ---

    @Test
    void shouldRecordEveryDeliveryAttemptAndOutcomeInLog() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(3));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));

        orchestrator.dispatch(accountUuid, "verify_email",
                Map.of("token", "tok-1", "sourceEventKey", "key-1"));

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        List<DeliveryLog> rows = captor.getAllValues();

        DeliveryLog emailRow = rows.stream().filter(r -> "EMAIL".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(emailRow.getAccountUuid()).isEqualTo(accountUuid);
        assertThat(emailRow.getRecipient()).isEqualTo("a@example.com");
        assertThat(emailRow.getSourceEventKey()).isEqualTo("key-1");
        assertThat(emailRow.getTemplateName()).isEqualTo("email.verify");
        assertThat(emailRow.getTemplateVersion()).isEqualTo(3);
        assertThat(emailRow.getOutcome()).isEqualTo("SENT");
        assertThat(emailRow.getErrorDetail()).isNull();
        assertThat(emailRow.getAttempt()).isEqualTo((short) 1);
        assertThat(emailRow.getCreatedAt()).isEqualTo(FIXED_INSTANT);

        DeliveryLog inAppRow = rows.stream().filter(r -> "IN_APP".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(inAppRow.getRecipient()).isEqualTo(accountUuid.toString());
        assertThat(inAppRow.getTemplateName()).isEqualTo("user.verify");
        assertThat(inAppRow.getTemplateVersion()).isEqualTo(1);
        assertThat(inAppRow.getOutcome()).isEqualTo("SENT");

        verify(emailChannel).send(eq(accountUuid), eq("a@example.com"), eq("SECURITY"), eq(message(3)));
        verify(inAppChannel).send(eq(accountUuid), eq(accountUuid.toString()), eq("SECURITY"), eq(message(1)));
    }

    // --- R10 named test: suppression reaches the delivery log, not just the resolver ------

    @Test
    void shouldSuppressChannelWhenRecipientOptedOut() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")).thenReturn(false);
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "IN_APP")).thenReturn(true);
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-2"));

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        List<DeliveryLog> rows = captor.getAllValues();

        DeliveryLog emailRow = rows.stream().filter(r -> "EMAIL".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(emailRow.getOutcome()).isEqualTo("SUPPRESSED");
        assertThat(emailRow.getRecipient()).isEqualTo("a@example.com");
        assertThat(emailRow.getTemplateName()).isNull();
        assertThat(emailRow.getTemplateVersion()).isNull();
        assertThat(emailRow.getErrorDetail()).isNull();

        DeliveryLog inAppRow = rows.stream().filter(r -> "IN_APP".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(inAppRow.getOutcome()).isEqualTo("SENT");

        verify(templateRenderer, never()).render(eq("email.verify"), any(), any());
        verify(emailChannel, never()).send(any(), any(), any(), any());
        verify(inAppChannel).send(any(), any(), any(), any());
    }

    // --- Finding #3 / AC5: missing contact projection is FAILED for EMAIL only -------------

    @Test
    void missingEmailFailsEmailChannelWithoutRenderOrSendButInAppStillProceeds() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.empty());
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-3"));

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        List<DeliveryLog> rows = captor.getAllValues();

        DeliveryLog emailRow = rows.stream().filter(r -> "EMAIL".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(emailRow.getOutcome()).isEqualTo("FAILED");
        assertThat(emailRow.getRecipient()).isNull();
        assertThat(emailRow.getTemplateName()).as("templateName was already resolved before the "
                + "missing-recipient check runs, so it is still recorded even though render never happened")
                .isEqualTo("email.verify");
        assertThat(emailRow.getTemplateVersion()).isNull();
        assertThat(emailRow.getErrorDetail()).isEqualTo("no recipient email on file");

        DeliveryLog inAppRow = rows.stream().filter(r -> "IN_APP".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(inAppRow.getOutcome()).isEqualTo("SENT");
        assertThat(inAppRow.getRecipient()).isEqualTo(accountUuid.toString());

        verify(templateRenderer, never()).render(eq("email.verify"), any(), any());
        verify(emailChannel, never()).send(any(), any(), any(), any());
    }

    /** Finding #2/#6: IN_APP's own recipient is always the account UUID's own string form - even
     * when an email IS present, it must not leak into the IN_APP row's recipient. */
    @Test
    void inAppRecipientIsAlwaysTheAccountUuidStringEvenWhenEmailIsPresent() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(any(), any(), any())).thenReturn(message(1));

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-4"));

        verify(inAppChannel).send(eq(accountUuid), eq(accountUuid.toString()), any(), any());
    }

    // --- AC4 / Finding #4: render failure is FAILED, not propagated, redacted --------------

    @Test
    void renderFailureRecordsFailedRowSkipsSendAndDoesNotPropagate() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any()))
                .thenThrow(new IllegalArgumentException("no template for name=email.verify, channel=EMAIL"));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));

        assertThatCode(() -> orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-5")))
                .doesNotThrowAnyException();

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        DeliveryLog emailRow = captor.getAllValues().stream()
                .filter(r -> "EMAIL".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(emailRow.getOutcome()).isEqualTo("FAILED");
        assertThat(emailRow.getErrorDetail()).isEqualTo("no template for name=email.verify, channel=EMAIL");
        verify(emailChannel, never()).send(any(), any(), any(), any());
    }

    /** Finding #5: every errorDetail is redacted before persistence - a render failure whose
     * exception message happens to contain a secret-shaped substring must not leak it. */
    @Test
    void renderFailureErrorDetailIsRedactedBeforePersistence() {
        UUID accountUuid = UUID.randomUUID();
        String leaky = "upstream failed: token=abc123 while rendering";
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenThrow(new RuntimeException(leaky));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-6"));

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        DeliveryLog emailRow = captor.getAllValues().stream()
                .filter(r -> "EMAIL".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(emailRow.getErrorDetail()).isEqualTo(SecretSafeLogging.redact(leaky));
        assertThat(emailRow.getErrorDetail()).doesNotContain("abc123");
    }

    // --- Finding #7 (frozen brief): missing channel bean ------------------------------------

    @Test
    void missingChannelBeanRecordsFailedRowWithTheAlreadyRenderedTemplateVersionAndSkipsSend() {
        DeliveryOrchestrator emailOnlyOrchestrator = new DeliveryOrchestrator(preferenceResolver, templateRenderer,
                contactProjectionUpdater, deliveryLogRepository, deliveryRetryRepository, retryProperties,
                objectMapper, CLOCK, List.of(emailChannel));
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(5));

        emailOnlyOrchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-7"));

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        DeliveryLog inAppRow = captor.getAllValues().stream()
                .filter(r -> "IN_APP".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(inAppRow.getOutcome()).isEqualTo("FAILED");
        assertThat(inAppRow.getErrorDetail()).isEqualTo("no channel bean registered for IN_APP");
        assertThat(inAppRow.getTemplateName()).isEqualTo("user.verify");
        assertThat(inAppRow.getTemplateVersion()).isEqualTo(5);
    }

    // --- AC6 / Finding #4: channel send failure is FAILED, redacted, non-propagating ------

    @Test
    void channelSendFailureRecordsFailedRedactedRowAndOtherChannelStillProceeds() {
        UUID accountUuid = UUID.randomUUID();
        String leaky = "smtp rejected, secret=xyz789 attached";
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(2));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new RuntimeException(leaky))
                .when(emailChannel).send(any(), any(), any(), any());

        assertThatCode(() -> orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-8")))
                .doesNotThrowAnyException();

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        List<DeliveryLog> rows = captor.getAllValues();

        DeliveryLog emailRow = rows.stream().filter(r -> "EMAIL".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(emailRow.getOutcome()).isEqualTo("FAILED");
        assertThat(emailRow.getErrorDetail()).isEqualTo(SecretSafeLogging.redact(leaky));
        assertThat(emailRow.getTemplateVersion()).isEqualTo(2);

        DeliveryLog inAppRow = rows.stream().filter(r -> "IN_APP".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(inAppRow.getOutcome()).isEqualTo("SENT");
    }

    /** Finding #7: a genuine {@code Error} (not {@code Exception}) is deliberately allowed to
     * propagate out of {@code dispatch} - this is the converse of every other test in this class,
     * which only ever throws {@code Exception} subclasses. A dedicated local {@code Error} type
     * avoids any accidental interaction with a real JVM-level error (e.g. actually exhausting
     * memory). */
    @Test
    void aGenuineErrorFromAChannelPropagatesOutOfDispatchInsteadOfBeingSwallowed() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new SimulatedError()).when(emailChannel).send(any(), any(), any(), any());

        assertThrows(SimulatedError.class,
                () -> orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-9")));

        verify(inAppChannel, never()).send(any(), any(), any(), any());
    }

    private static final class SimulatedError extends Error {
    }

    // --- Finding #4/#6: pre-loop failure still records a best-effort row per launch channel ---

    @Test
    void findEmailThrowingRecordsFallbackFailedRowPerLaunchChannelWithoutPropagating() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-10")))
                .doesNotThrowAnyException();

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        for (DeliveryLog row : captor.getAllValues()) {
            assertThat(row.getOutcome()).isEqualTo("FAILED");
            assertThat(row.getSourceEventKey()).isEqualTo("key-10");
            assertThat(row.getErrorDetail()).isEqualTo("db down");
            assertThat(row.getRecipient()).isNull();
            assertThat(row.getTemplateName()).isNull();
        }
        assertThat(captor.getAllValues().stream().map(DeliveryLog::getChannel))
                .containsExactlyInAnyOrder("EMAIL", "IN_APP");
        verifyNoInteractions(preferenceResolver, templateRenderer);
        verify(emailChannel, never()).send(any(), any(), any(), any());
        verify(inAppChannel, never()).send(any(), any(), any(), any());
    }

    @Test
    void findEmailThrowingWithANullSourceEventKeyFallsBackToASyntheticKey() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenThrow(new RuntimeException("db down"));

        orchestrator.dispatch(accountUuid, "verify_email", null);

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        for (DeliveryLog row : captor.getAllValues()) {
            assertThat(row.getSourceEventKey()).isEqualTo("unknown:" + accountUuid);
        }
    }

    /** Finding #4's own inner safety net: even if the fallback {@code save} itself throws (e.g. a
     * genuinely broken DB connection), {@code dispatch} must still never throw. */
    @Test
    void dispatchNeverThrowsEvenWhenTheFallbackSaveItselfThrows() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenThrow(new RuntimeException("db down"));
        when(deliveryLogRepository.save(any())).thenThrow(new RuntimeException("save also broken"));

        assertThatCode(() -> orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-11")))
                .doesNotThrowAnyException();
    }

    // --- Kimi Phase 8 Finding #2: displayName wiring ---------------------------------------

    @Test
    void displayNameIsMergedIntoRenderDataWhenPresentWithoutDroppingOriginalKeys() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.of("Ada Lovelace"));
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(any(), any(), any())).thenReturn(message(1));

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("token", "tok-1", "sourceEventKey", "key-12"));

        var captor = forClass(Map.class);
        verify(templateRenderer).render(eq("email.verify"), eq("EMAIL"), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, String> renderData = captor.getValue();
        assertThat(renderData)
                .containsEntry("displayName", "Ada Lovelace")
                .containsEntry("token", "tok-1")
                .containsEntry("sourceEventKey", "key-12");
    }

    @Test
    void renderDataEqualsTheOriginalEventDataWhenDisplayNameIsAbsent() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(any(), any(), any())).thenReturn(message(1));

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("token", "tok-1", "sourceEventKey", "key-13"));

        var captor = forClass(Map.class);
        verify(templateRenderer).render(eq("email.verify"), eq("EMAIL"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(Map.of("token", "tok-1", "sourceEventKey", "key-13"));
    }

    @Test
    void nullEventDataProducesAnEmptyRenderDataMapWithoutThrowing() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(any(), any(), any())).thenReturn(message(1));

        assertThatCode(() -> orchestrator.dispatch(accountUuid, "verify_email", null)).doesNotThrowAnyException();

        var captor = forClass(Map.class);
        verify(templateRenderer).render(eq("email.verify"), eq("EMAIL"), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    // --- Kimi Phase 11 Gap #1/#5: a failure inside dispatchOneChannel's own body (not already ---
    // --- converted to a row) must still leave a best-effort FAILED row, not just a log line. ---

    @Test
    void preferenceResolverThrowingRecordsAFailedRowForThatChannelAndDoesNotPropagate() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL"))
                .thenThrow(new RuntimeException("resolver down"));
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "IN_APP")).thenReturn(true);
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));

        assertThatCode(() -> orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-14")))
                .doesNotThrowAnyException();

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        DeliveryLog emailRow = captor.getAllValues().stream()
                .filter(r -> "EMAIL".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(emailRow.getOutcome()).isEqualTo("FAILED");
        assertThat(emailRow.getErrorDetail()).isEqualTo("resolver down");
        assertThat(emailRow.getSourceEventKey()).isEqualTo("key-14");

        DeliveryLog inAppRow = captor.getAllValues().stream()
                .filter(r -> "IN_APP".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(inAppRow.getOutcome()).isEqualTo("SENT");
        verify(emailChannel, never()).send(any(), any(), any(), any());
    }

    // --- Kimi Phase 11 Gap #3: both channels suppressed simultaneously ---------------------

    @Test
    void bothChannelsSuppressedRecordsTwoSuppressedRowsAndNeitherRendersNorSends() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(false);

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-15"));

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(row -> assertThat(row.getOutcome()).isEqualTo("SUPPRESSED"));
        verifyNoInteractions(templateRenderer);
        verify(emailChannel, never()).send(any(), any(), any(), any());
        verify(inAppChannel, never()).send(any(), any(), any(), any());
    }

    // --- Kimi Phase 11 Gap #4: documents (locks) the current precedence, doesn't change it ---

    /** Current behavior: the projection's own {@code displayName} unconditionally overrides a
     * caller-supplied value already present in {@code eventData}. Unreachable in production today
     * ({@code AuthEventConsumer} never supplies a {@code displayName} key), but locked here so a
     * future caller that does supply one gets a deliberate, tested precedence rule rather than an
     * accidental one. */
    @Test
    void callerSuppliedDisplayNameInEventDataIsOverriddenByTheProjectionValueWhenBothArePresent() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.of("Projection Name"));
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(any(), any(), any())).thenReturn(message(1));

        orchestrator.dispatch(accountUuid, "verify_email",
                Map.of("displayName", "Caller Supplied Name", "sourceEventKey", "key-16"));

        var captor = forClass(Map.class);
        verify(templateRenderer).render(eq("email.verify"), eq("EMAIL"), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, String> renderData = captor.getValue();
        assertThat(renderData).containsEntry("displayName", "Projection Name");
    }

    // --- Kimi Phase 11 Gap #7: payment-derived mappings forward eventData keys unchanged ---

    @Test
    void paymentDerivedMappingForwardsAllEventDataKeysUnchangedToBothChannels() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(any(), any(), any())).thenReturn(message(1));
        Map<String, String> eventData = Map.of(
                "amount", "125.00", "currency", "USD", "invoiceId", "inv-1",
                "invoiceUuid", UUID.randomUUID().toString(), "sourceEventKey", "key-17");

        orchestrator.dispatch(accountUuid, "invoice.created", eventData);

        var emailCaptor = forClass(Map.class);
        verify(templateRenderer).render(eq("invoice.created"), eq("EMAIL"), emailCaptor.capture());
        assertThat(emailCaptor.getValue()).containsAllEntriesOf(eventData);

        var inAppCaptor = forClass(Map.class);
        verify(templateRenderer).render(eq("invoice.created"), eq("IN_APP"), inAppCaptor.capture());
        assertThat(inAppCaptor.getValue()).containsAllEntriesOf(eventData);
    }

    // --- Kimi Phase 11 Gap #8: the mapping table is VERBATIM - lock its exact 7 entries -----

    @Test
    void notificationMappingsTableContainsExactlyTheSevenVerbatimEntriesAndExcludesAccountSuspended()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/notification/delivery/DeliveryOrchestrator.java"));

        assertThat(source)
                .contains("\"verify_email\", new NotificationMapping(\"email.verify\", \"user.verify\", \"SECURITY\")")
                .contains("\"password_reset\", new NotificationMapping(\"email.password_reset\", "
                        + "\"user.password_reset\", \"SECURITY\")")
                .contains("\"user.registered\", new NotificationMapping(\"user.welcome\", \"user.welcome\", \"SECURITY\")")
                .contains("\"invoice.created\", new NotificationMapping(\"invoice.created\", \"invoice.created\", \"PAYMENT\")")
                .contains("\"payment.seen\", new NotificationMapping(\"payment.seen\", \"payment.seen\", \"PAYMENT\")")
                .contains("\"payment.finalized\", new NotificationMapping(\"payment.finalized\", "
                        + "\"payment.finalized\", \"PAYMENT\")")
                .contains("\"receipt.issued\", new NotificationMapping(\"receipt.issued\", \"receipt.issued\", \"PAYMENT\")")
                .doesNotContain("\"account.suspended\", new NotificationMapping(");

        long mappingEntryCount = source.lines().filter(line -> line.contains("new NotificationMapping(")).count();
        assertThat(mappingEntryCount).as("exactly 7 entries, no more, no fewer").isEqualTo(7);
    }

    // --- T14 (R12/R13, L7): transient channel-send failure schedules a bounded retry ----------

    @Test
    void transientChannelSendFailureInsertsAFirstRetryRowAtAttemptOneWithTheCorrectBackoff() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(2));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new RuntimeException("ses throttled")).when(emailChannel).send(any(), any(), any(), any());

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("token", "tok-1", "sourceEventKey", "key-18"));

        var captor = forClass(DeliveryRetry.class);
        verify(deliveryRetryRepository).save(captor.capture());
        DeliveryRetry retry = captor.getValue();
        assertThat(retry.getAccountUuid()).isEqualTo(accountUuid);
        assertThat(retry.getChannel()).isEqualTo("EMAIL");
        assertThat(retry.getSourceEventKey()).isEqualTo("key-18");
        assertThat(retry.getNotificationKind()).isEqualTo("verify_email");
        assertThat(retry.getAttempt()).isEqualTo((short) 1);
        assertThat(retry.getNextAttemptAt()).isEqualTo(FIXED_INSTANT.plusSeconds(30));
        assertThat(retry.getCreatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(retry.getEventDataJson()).contains("tok-1").contains("key-18");
    }

    /** AC5 (L5): the transient-failure classification and retry-scheduling path must be
     * channel-agnostic - proven here with {@code inAppChannel} throwing, mirroring the EMAIL-only
     * coverage above exactly, to close the gap that every other retry-scheduling test in this class
     * only ever exercised the EMAIL channel. */
    @Test
    void transientInAppChannelSendFailureAlsoSchedulesARetry() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(inAppChannel).send(any(), any(), any(), any());

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-18b"));

        var captor = forClass(DeliveryRetry.class);
        verify(deliveryRetryRepository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo("IN_APP");
        assertThat(captor.getValue().getAttempt()).isEqualTo((short) 1);
    }

    /** Boundary: both channels failing transiently on the same dispatch schedules two independent
     * retry rows, each with its own channel/backoff - neither interferes with the other. */
    @Test
    void bothChannelsFailingTransientlyScheduleTwoIndependentRetryRows() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(emailChannel).send(any(), any(), any(), any());
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(inAppChannel).send(any(), any(), any(), any());

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-18c"));

        var captor = forClass(DeliveryRetry.class);
        verify(deliveryRetryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().stream().map(DeliveryRetry::getChannel))
                .containsExactlyInAnyOrder("EMAIL", "IN_APP");
    }

    @Test
    void permanentChannelSendFailureNeverInsertsARetryRow() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("blank recipient"))
                .when(emailChannel).send(any(), any(), any(), any());

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-19"));

        verifyNoInteractions(deliveryRetryRepository);
    }

    /** AC14 (pinned semantics): a maxAttempts=1 configuration must dead-letter a transient failure
     * on the very first attempt, never scheduling a retry it would instantly exhaust. */
    @Test
    void transientFailureWithMaxAttemptsOfOneDeadLettersImmediatelyWithNoRetryRow() {
        DeliveryOrchestrator singleAttemptOrchestrator = new DeliveryOrchestrator(preferenceResolver, templateRenderer,
                contactProjectionUpdater, deliveryLogRepository, deliveryRetryRepository,
                new RetryProperties(1, 30, 3600, 30), objectMapper, CLOCK, List.of(emailChannel, inAppChannel));
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(emailChannel).send(any(), any(), any(), any());

        singleAttemptOrchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-20"));

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(captor.capture());
        DeliveryLog emailRow = captor.getAllValues().stream()
                .filter(r -> "EMAIL".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(emailRow.getOutcome()).isEqualTo("DEAD_LETTERED");
        assertThat(emailRow.getAttempt()).isEqualTo((short) 1);
        verifyNoInteractions(deliveryRetryRepository);
    }

    // --- T14: DeliveryOrchestrator.replay's own 5-outcome classification -----------------------

    @Test
    void replaySuccessWritesASentRowAtTheIncrementedAttemptAndReturnsSent() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(4));

        DeliveryOrchestrator.DeliveryOutcome outcome = orchestrator.replay(accountUuid, "EMAIL", "verify_email",
                "key-21", Map.of("sourceEventKey", "key-21"), (short) 1);

        assertThat(outcome).isEqualTo(DeliveryOrchestrator.DeliveryOutcome.SENT);
        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempt()).isEqualTo((short) 2);
        assertThat(captor.getValue().getOutcome()).isEqualTo("SENT");
        verify(emailChannel).send(eq(accountUuid), eq("a@example.com"), eq("SECURITY"), eq(message(4)));
    }

    /** Phase 8 Finding #3: replay re-resolves displayName fresh (not only email), mirroring the
     * original dispatch path's own merge - previously only email was re-resolved. */
    @Test
    void replayResolvesDisplayNameFreshAndMergesItIntoRenderData() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.of("Ada Lovelace"));
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));

        orchestrator.replay(accountUuid, "EMAIL", "verify_email", "key-28",
                Map.of("token", "tok-1", "sourceEventKey", "key-28"), (short) 1);

        var captor = forClass(Map.class);
        verify(templateRenderer).render(eq("email.verify"), eq("EMAIL"), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, String> renderData = captor.getValue();
        assertThat(renderData)
                .containsEntry("displayName", "Ada Lovelace")
                .containsEntry("token", "tok-1")
                .containsEntry("sourceEventKey", "key-28");
    }

    /** Phase 8 Finding #4: structural, not behavioral - no real `NOTIFICATION_MAPPINGS` entry has a
     * null template for either channel (confirmed: all 7 entries supply both), so this guard cannot
     * be exercised through any real `notificationKind` today. Mirrors
     * `notificationMappingsTableContainsExactlyTheSevenVerbatimEntries`'s own established precedent
     * for asserting a real, source-level invariant that has no reachable behavioral test. */
    @Test
    void replayGuardsAgainstANullTemplateNameMirroringDispatchOneChannel() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/notification/delivery/DeliveryOrchestrator.java"));
        String replayBody = source.substring(source.indexOf("DeliveryOutcome replay("));

        assertThat(replayBody)
                .as("replay must guard a null templateName before resolving a recipient or rendering, "
                        + "mirroring dispatchOneChannel's own identical guard")
                .contains("if (templateName == null) {");
    }

    /** AC11 (Kimi Phase 3 Finding #3): a channel disabled since the original attempt is honored on
     * replay, not overridden - the retry stops, it does not force a send against current preference. */
    @Test
    void replayHonorsAPreferenceDisabledSinceTheOriginalAttemptAndReturnsSuppressed() {
        UUID accountUuid = UUID.randomUUID();
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")).thenReturn(false);

        DeliveryOrchestrator.DeliveryOutcome outcome = orchestrator.replay(accountUuid, "EMAIL", "verify_email",
                "key-22", Map.of("sourceEventKey", "key-22"), (short) 1);

        assertThat(outcome).isEqualTo(DeliveryOrchestrator.DeliveryOutcome.SUPPRESSED);
        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("SUPPRESSED");
        verifyNoInteractions(templateRenderer);
        verify(emailChannel, never()).send(any(), any(), any(), any());
    }

    @Test
    void replayOfATransientFailureBelowMaxAttemptsReturnsTransientFailure() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new RuntimeException("still down")).when(emailChannel).send(any(), any(), any(), any());

        DeliveryOrchestrator.DeliveryOutcome outcome = orchestrator.replay(accountUuid, "EMAIL", "verify_email",
                "key-23", Map.of("sourceEventKey", "key-23"), (short) 1);

        assertThat(outcome).isEqualTo(DeliveryOrchestrator.DeliveryOutcome.TRANSIENT_FAILURE);
        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempt()).isEqualTo((short) 2);
        assertThat(captor.getValue().getOutcome()).isEqualTo("FAILED");
    }

    @Test
    void replayAtTheFinalAllowedAttemptDeadLettersAndReturnsTransientExhausted() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new RuntimeException("still down")).when(emailChannel).send(any(), any(), any(), any());

        // retryProperties.maxAttempts() == 3; attemptsAlreadyMade=2 -> this replay is attempt 3, the last one.
        DeliveryOrchestrator.DeliveryOutcome outcome = orchestrator.replay(accountUuid, "EMAIL", "verify_email",
                "key-24", Map.of("sourceEventKey", "key-24"), (short) 2);

        assertThat(outcome).isEqualTo(DeliveryOrchestrator.DeliveryOutcome.TRANSIENT_EXHAUSTED);
        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempt()).isEqualTo((short) 3);
        assertThat(captor.getValue().getOutcome()).isEqualTo("DEAD_LETTERED");
    }

    @Test
    void replayOfAPermanentFailureReturnsPermanentFailureAndStopsRetrying() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("now invalid")).when(emailChannel).send(any(), any(), any(), any());

        DeliveryOrchestrator.DeliveryOutcome outcome = orchestrator.replay(accountUuid, "EMAIL", "verify_email",
                "key-25", Map.of("sourceEventKey", "key-25"), (short) 1);

        assertThat(outcome).isEqualTo(DeliveryOrchestrator.DeliveryOutcome.PERMANENT_FAILURE);
    }

    @Test
    void replayWithAnUnknownNotificationKindWritesFailedAndReturnsPermanentFailure() {
        UUID accountUuid = UUID.randomUUID();

        DeliveryOrchestrator.DeliveryOutcome outcome = orchestrator.replay(accountUuid, "EMAIL", "does.not.exist",
                "key-26", Map.of("sourceEventKey", "key-26"), (short) 1);

        assertThat(outcome).isEqualTo(DeliveryOrchestrator.DeliveryOutcome.PERMANENT_FAILURE);
        verifyNoInteractions(preferenceResolver, templateRenderer);
        verify(emailChannel, never()).send(any(), any(), any(), any());
        verify(inAppChannel, never()).send(any(), any(), any(), any());
    }

    // --- Phase 11 Gap #6: replay's other guards (only unknown-kind/preference were covered) -----

    @Test
    void replayWithMissingEmailWritesFailedAndReturnsPermanentFailure() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")).thenReturn(true);

        DeliveryOrchestrator.DeliveryOutcome outcome = orchestrator.replay(accountUuid, "EMAIL", "verify_email",
                "key-29", Map.of("sourceEventKey", "key-29"), (short) 1);

        assertThat(outcome).isEqualTo(DeliveryOrchestrator.DeliveryOutcome.PERMANENT_FAILURE);
        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorDetail()).isEqualTo("no recipient email on file");
        verifyNoInteractions(templateRenderer);
        verify(emailChannel, never()).send(any(), any(), any(), any());
    }

    @Test
    void replayWithRenderFailureWritesFailedAndReturnsPermanentFailure() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any()))
                .thenThrow(new IllegalArgumentException("no template for name=email.verify, channel=EMAIL"));

        DeliveryOrchestrator.DeliveryOutcome outcome = orchestrator.replay(accountUuid, "EMAIL", "verify_email",
                "key-30", Map.of("sourceEventKey", "key-30"), (short) 1);

        assertThat(outcome).isEqualTo(DeliveryOrchestrator.DeliveryOutcome.PERMANENT_FAILURE);
        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("FAILED");
        verify(emailChannel, never()).send(any(), any(), any(), any());
    }

    @Test
    void replayWithMissingChannelBeanWritesFailedAndReturnsPermanentFailure() {
        DeliveryOrchestrator emailOnlyOrchestrator = new DeliveryOrchestrator(preferenceResolver, templateRenderer,
                contactProjectionUpdater, deliveryLogRepository, deliveryRetryRepository, retryProperties,
                objectMapper, CLOCK, List.of(emailChannel));
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "IN_APP")).thenReturn(true);
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));

        DeliveryOrchestrator.DeliveryOutcome outcome = emailOnlyOrchestrator.replay(accountUuid, "IN_APP",
                "verify_email", "key-31", Map.of("sourceEventKey", "key-31"), (short) 1);

        assertThat(outcome).isEqualTo(DeliveryOrchestrator.DeliveryOutcome.PERMANENT_FAILURE);
        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorDetail()).isEqualTo("no channel bean registered for IN_APP");
    }

    // --- Phase 11 Gap #7: one channel succeeding must not interfere with the other's own retry ---

    @Test
    void oneChannelSucceedingWhileTheOtherFailsTransientlyStillSchedulesOnlyTheFailingChannelsRetry() {
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(inAppChannel).send(any(), any(), any(), any());

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-32"));

        var logCaptor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository, times(2)).save(logCaptor.capture());
        DeliveryLog emailRow = logCaptor.getAllValues().stream()
                .filter(r -> "EMAIL".equals(r.getChannel())).findFirst().orElseThrow();
        assertThat(emailRow.getOutcome()).isEqualTo("SENT");

        var retryCaptor = forClass(DeliveryRetry.class);
        verify(deliveryRetryRepository).save(retryCaptor.capture());
        assertThat(retryCaptor.getValue().getChannel()).isEqualTo("IN_APP");
    }

    // --- Phase 11 Gap #10: replay for IN_APP specifically, not only EMAIL -----------------------

    @Test
    void replaySuccessForInAppChannelWritesSentRowWithAccountUuidAsRecipient() {
        UUID accountUuid = UUID.randomUUID();
        when(preferenceResolver.resolve(accountUuid, "SECURITY", "IN_APP")).thenReturn(true);
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));

        DeliveryOrchestrator.DeliveryOutcome outcome = orchestrator.replay(accountUuid, "IN_APP", "verify_email",
                "key-33", Map.of("sourceEventKey", "key-33"), (short) 1);

        assertThat(outcome).isEqualTo(DeliveryOrchestrator.DeliveryOutcome.SENT);
        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipient()).isEqualTo(accountUuid.toString());
        verify(inAppChannel).send(eq(accountUuid), eq(accountUuid.toString()), eq("SECURITY"), eq(message(1)));
        // IN_APP's own recipient never goes through findEmail - unlike findDisplayName (resolved
        // unconditionally for every channel, mirroring dispatch's own identical behavior), email
        // resolution is EMAIL-specific.
        verify(contactProjectionUpdater, never()).findEmail(any());
    }

    @Test
    void recordUnrecoverableFailureWritesADeadLetteredRowAtTheGivenAttempt() {
        UUID accountUuid = UUID.randomUUID();

        orchestrator.recordUnrecoverableFailure(accountUuid, "EMAIL", "key-27", (short) 3, "unreadable json");

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        DeliveryLog row = captor.getValue();
        assertThat(row.getOutcome()).isEqualTo("DEAD_LETTERED");
        assertThat(row.getAttempt()).isEqualTo((short) 3);
        assertThat(row.getErrorDetail()).isEqualTo("unreadable json");
    }

    /** Phase 11 Gap #8: the pre-existing test above used a non-secret-shaped detail, giving zero
     * evidence redaction (AC8) actually happens - a removed `SecretSafeLogging.redact()` call would
     * still pass it. */
    @Test
    void recordUnrecoverableFailureRedactsASecretShapedDetailBeforePersistence() {
        UUID accountUuid = UUID.randomUUID();
        String leaky = "parse failed near token=abc123";

        orchestrator.recordUnrecoverableFailure(accountUuid, "EMAIL", "key-34", (short) 3, leaky);

        var captor = forClass(DeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorDetail()).isEqualTo(SecretSafeLogging.redact(leaky));
        assertThat(captor.getValue().getErrorDetail()).doesNotContain("abc123");
    }

    /** Phase 11 Gap #9: scheduleFirstRetry's own best-effort contract - a serialization failure
     * must never propagate out of dispatch, and must never leave a retry row behind either. */
    @Test
    void aSerializationFailureWhileSchedulingTheFirstRetryNeverPropagatesAndInsertsNoRow() throws Exception {
        ObjectMapper throwingObjectMapper = mock(ObjectMapper.class);
        when(throwingObjectMapper.writeValueAsString(any()))
                .thenThrow(mock(com.fasterxml.jackson.core.JsonProcessingException.class));
        DeliveryOrchestrator throwingMapperOrchestrator = new DeliveryOrchestrator(preferenceResolver,
                templateRenderer, contactProjectionUpdater, deliveryLogRepository, deliveryRetryRepository,
                retryProperties, throwingObjectMapper, CLOCK, List.of(emailChannel, inAppChannel));
        UUID accountUuid = UUID.randomUUID();
        when(contactProjectionUpdater.findEmail(accountUuid)).thenReturn(Optional.of("a@example.com"));
        when(contactProjectionUpdater.findDisplayName(accountUuid)).thenReturn(Optional.empty());
        when(preferenceResolver.resolve(eq(accountUuid), anyString(), anyString())).thenReturn(true);
        when(templateRenderer.render(eq("email.verify"), eq("EMAIL"), any())).thenReturn(message(1));
        when(templateRenderer.render(eq("user.verify"), eq("IN_APP"), any())).thenReturn(message(1));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(emailChannel).send(any(), any(), any(), any());

        assertThatCode(() -> throwingMapperOrchestrator.dispatch(accountUuid, "verify_email",
                Map.of("sourceEventKey", "key-35"))).doesNotThrowAnyException();

        verifyNoInteractions(deliveryRetryRepository);
    }
}
