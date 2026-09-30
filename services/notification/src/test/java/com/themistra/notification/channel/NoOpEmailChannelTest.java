package com.themistra.notification.channel;

import com.themistra.notification.template.TemplateRenderer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Kimi Phase 11 Gap #2: no dedicated tests existed for this temporary, real (not stub) channel
 * bean, even though it is one of only two dispatch targets today. Plain JUnit, no Spring context,
 * no Docker.
 */
class NoOpEmailChannelTest {

    private final NoOpEmailChannel channel = new NoOpEmailChannel();

    @Test
    void channelReturnsEmail() {
        assertThat(channel.channel()).isEqualTo("EMAIL");
    }

    /** The message's own {@code toString()} (T09, Kimi Phase 8 Finding #2) already excludes
     * subject/body content - this proves {@code send} completes normally even when the underlying
     * rendered content is token/PII-bearing, since the safety property lives in the message type
     * itself, not in how the channel logs it. */
    @Test
    void sendCompletesNormallyAndNeverThrowsEvenWithTokenBearingContent() {
        TemplateRenderer.RenderedMessage message = new TemplateRenderer.RenderedMessage(
                "Reset your password", "Visit https://example.com/reset?token=raw-secret-token", 1);

        assertThatCode(() -> channel.send(UUID.randomUUID(), "owner@example.com", message))
                .doesNotThrowAnyException();
    }

    /** Kimi Phase 11 Gap #2: locks that the log statement passes the whole {@code message} object
     * (relying on its own safe {@code toString()}), never {@code message.subject()}/
     * {@code message.body()} directly - a future edit that "helpfully" logged the raw subject/body
     * would defeat T09's own safety property without this static guard catching it. */
    @Test
    void logStatementPassesTheWholeMessageObjectNotItsSubjectOrBodyDirectly() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/notification/channel/NoOpEmailChannel.java"));

        assertThat(source).contains("accountUuid, recipient, message);");
        assertThat(source).doesNotContain("message.subject()").doesNotContain("message.body()");
    }
}
