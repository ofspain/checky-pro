package com.themistra.notification.channel;

import com.themistra.notification.common.config.EmailProperties;
import com.themistra.notification.template.TemplateRenderer;
import org.junit.jupiter.api.Test;

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
}
