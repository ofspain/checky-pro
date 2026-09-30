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
 * Kimi Phase 11 Gap #2: mirrors {@link NoOpEmailChannelTest} exactly, for the {@code IN_APP}
 * channel bean.
 */
class NoOpInAppChannelTest {

    private final NoOpInAppChannel channel = new NoOpInAppChannel();

    @Test
    void channelReturnsInApp() {
        assertThat(channel.channel()).isEqualTo("IN_APP");
    }

    @Test
    void sendCompletesNormallyAndNeverThrowsEvenWithTokenBearingContent() {
        TemplateRenderer.RenderedMessage message = new TemplateRenderer.RenderedMessage(
                null, "Verify your email: https://example.com/verify?token=raw-secret-token", 1);

        assertThatCode(() -> channel.send(UUID.randomUUID(), UUID.randomUUID().toString(), message))
                .doesNotThrowAnyException();
    }

    @Test
    void logStatementPassesTheWholeMessageObjectNotItsSubjectOrBodyDirectly() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/notification/channel/NoOpInAppChannel.java"));

        assertThat(source).contains("accountUuid, recipient, message);");
        assertThat(source).doesNotContain("message.subject()").doesNotContain("message.body()");
    }
}
