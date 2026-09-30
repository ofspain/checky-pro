package com.themistra.notification.channel;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.themistra.notification.common.config.EmailProperties;
import com.themistra.notification.template.TemplateRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

/**
 * Mocked {@link EmailTransport}, no Spring context, no Docker.
 */
class EmailChannelTest {

    private final EmailTransport emailTransport = mock(EmailTransport.class);
    private final EmailProperties emailProperties = new EmailProperties("no-reply@checky.pro", "fake");
    private final EmailChannel channel = new EmailChannel(emailTransport, emailProperties);

    private ch.qos.logback.classic.Logger logbackLogger;
    private ListAppender<ILoggingEvent> logAppender;

    /** Kimi Phase 11 Gap #5: the static source-scan guard below proves the log statement's own
     * *source text* never references {@code recipient}, but not that the *runtime-formatted log
     * event* excludes it - a future refactor could introduce a differently-named variable holding
     * the same value. This attaches a real Logback {@link ListAppender} (already on the classpath
     * via spring-boot-starter, no new test dependency needed) and inspects the actual formatted
     * message text. */
    @BeforeEach
    void attachLogCapture() {
        logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(EmailChannel.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logbackLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogCapture() {
        logbackLogger.detachAppender(logAppender);
    }

    private static TemplateRenderer.RenderedMessage message() {
        return new TemplateRenderer.RenderedMessage("Verify your email", "Visit the link", 1);
    }

    @Test
    void channelReturnsEmail() {
        assertThat(channel.channel()).isEqualTo("EMAIL");
    }

    @Test
    void sendBuildsTheCorrectEmailMessageAndDelegatesToTheTransport() {
        UUID accountUuid = UUID.randomUUID();
        when(emailTransport.send(any())).thenReturn("mid-1");

        channel.send(accountUuid, "recipient@example.com", message());

        var captor = forClass(EmailMessage.class);
        verify(emailTransport).send(captor.capture());
        EmailMessage sent = captor.getValue();
        assertThat(sent.accountUuid()).isEqualTo(accountUuid);
        assertThat(sent.to()).isEqualTo("recipient@example.com");
        assertThat(sent.from()).isEqualTo("no-reply@checky.pro");
        assertThat(sent.subject()).isEqualTo("Verify your email");
        assertThat(sent.body()).isEqualTo("Visit the link");
    }

    // --- AC7: validation, before any transport call -----------------------------------------

    @Test
    void sendThrowsForANullRecipientWithoutCallingTheTransport() {
        assertThatThrownBy(() -> channel.send(UUID.randomUUID(), null, message()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(emailTransport);
    }

    @Test
    void sendThrowsForABlankRecipientWithoutCallingTheTransport() {
        assertThatThrownBy(() -> channel.send(UUID.randomUUID(), "   ", message()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(emailTransport);
    }

    @Test
    void sendThrowsForANullSubjectWithoutCallingTheTransport() {
        TemplateRenderer.RenderedMessage noSubject = new TemplateRenderer.RenderedMessage(null, "body", 1);

        assertThatThrownBy(() -> channel.send(UUID.randomUUID(), "recipient@example.com", noSubject))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(emailTransport);
    }

    @Test
    void sendThrowsForANullBodyWithoutCallingTheTransport() {
        TemplateRenderer.RenderedMessage noBody = new TemplateRenderer.RenderedMessage("subject", null, 1);

        assertThatThrownBy(() -> channel.send(UUID.randomUUID(), "recipient@example.com", noBody))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(emailTransport);
    }

    @Test
    void sendThrowsForABlankBodyWithoutCallingTheTransport() {
        TemplateRenderer.RenderedMessage blankBody = new TemplateRenderer.RenderedMessage("subject", "   ", 1);

        assertThatThrownBy(() -> channel.send(UUID.randomUUID(), "recipient@example.com", blankBody))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(emailTransport);
    }

    // --- AC4: never catches what the transport throws ---------------------------------------

    @Test
    void sendPropagatesTheTransportsOwnExceptionUnmodified() {
        EmailDeliveryException transportFailure = new EmailDeliveryException("SES is down");
        when(emailTransport.send(any())).thenThrow(transportFailure);

        assertThatThrownBy(() -> channel.send(UUID.randomUUID(), "recipient@example.com", message()))
                .isSameAs(transportFailure);
    }

    /** Kimi Phase 8 Finding #2: the success log line must never receive the recipient address as an
     * argument - a source-level static guard, mirroring this codebase's own established convention
     * for locking an exact, security-relevant code shape (e.g.
     * IdempotencyGuardIntegrationTest.insertIfNewUsesOnConflictDoNothing). */
    @Test
    void successLogLineNeverReferencesTheRecipientVariable() throws java.io.IOException {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/themistra/notification/channel/EmailChannel.java"));

        int logIndex = source.indexOf("log.info(");
        int logStatementEnd = source.indexOf(");", logIndex);
        String logStatement = source.substring(logIndex, logStatementEnd);

        assertThat(logStatement).doesNotContain("recipient");
        assertThat(logStatement).contains("accountUuid").contains("messageId");
    }

    /** Kimi Phase 11 Gap #5: the runtime counterpart to the static guard above - proves the actual
     * formatted log event, not just the source text, excludes the recipient's own email address and
     * any rendered content. */
    @Test
    void successLogEventContainsNeitherTheRecipientAddressNorRenderedContentAtRuntime() {
        UUID accountUuid = UUID.randomUUID();
        when(emailTransport.send(any())).thenReturn("mid-1");
        TemplateRenderer.RenderedMessage message = new TemplateRenderer.RenderedMessage(
                "Reset your password", "Visit https://example.com/reset?token=raw-secret-token", 1);

        channel.send(accountUuid, "victim@example.com", message);

        assertThat(logAppender.list).hasSize(1);
        String formatted = logAppender.list.get(0).getFormattedMessage();
        assertThat(formatted).doesNotContain("victim@example.com");
        assertThat(formatted).doesNotContain("Reset your password");
        assertThat(formatted).doesNotContain("raw-secret-token");
        assertThat(formatted).contains(accountUuid.toString()).contains("mid-1");
    }
}
