package com.themistra.notification.delivery;

import com.themistra.notification.channel.NotificationChannel;
import com.themistra.notification.common.SecretSafeLogging;
import com.themistra.notification.preference.ContactProjectionUpdater;
import com.themistra.notification.preference.PreferenceResolver;
import com.themistra.notification.template.TemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
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
    private final NotificationChannel emailChannel = mock(NotificationChannel.class);
    private final NotificationChannel inAppChannel = mock(NotificationChannel.class);

    private DeliveryOrchestrator orchestrator;

    @BeforeEach
    void wireDefaultOrchestrator() {
        when(emailChannel.channel()).thenReturn("EMAIL");
        when(inAppChannel.channel()).thenReturn("IN_APP");
        orchestrator = new DeliveryOrchestrator(preferenceResolver, templateRenderer, contactProjectionUpdater,
                deliveryLogRepository, CLOCK, List.of(emailChannel, inAppChannel));
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
        verify(emailChannel, never()).send(any(), any(), any());
        verify(inAppChannel, never()).send(any(), any(), any());
    }

    @Test
    void nullNotificationKindWritesNoRowsAndTouchesNoCollaborator() {
        UUID accountUuid = UUID.randomUUID();

        assertThatCode(() -> orchestrator.dispatch(accountUuid, null, Map.of("sourceEventKey", "k1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(deliveryLogRepository, contactProjectionUpdater, preferenceResolver, templateRenderer);
        verify(emailChannel, never()).send(any(), any(), any());
        verify(inAppChannel, never()).send(any(), any(), any());
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

        verify(emailChannel).send(eq(accountUuid), eq("a@example.com"), eq(message(3)));
        verify(inAppChannel).send(eq(accountUuid), eq(accountUuid.toString()), eq(message(1)));
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
        verify(emailChannel, never()).send(any(), any(), any());
        verify(inAppChannel).send(any(), any(), any());
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
        verify(emailChannel, never()).send(any(), any(), any());
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

        verify(inAppChannel).send(eq(accountUuid), eq(accountUuid.toString()), any());
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
        verify(emailChannel, never()).send(any(), any(), any());
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
                contactProjectionUpdater, deliveryLogRepository, CLOCK, List.of(emailChannel));
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
                .when(emailChannel).send(any(), any(), any());

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
        org.mockito.Mockito.doThrow(new SimulatedError()).when(emailChannel).send(any(), any(), any());

        assertThrows(SimulatedError.class,
                () -> orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "key-9")));

        verify(inAppChannel, never()).send(any(), any(), any());
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
        verify(emailChannel, never()).send(any(), any(), any());
        verify(inAppChannel, never()).send(any(), any(), any());
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
}
