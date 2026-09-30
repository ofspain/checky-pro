package com.themistra.notification.channel;

import com.themistra.notification.inapp.InappNotificationAppender;
import com.themistra.notification.template.TemplateRenderer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Mocked {@link InappNotificationAppender}, no Spring context, no Docker.
 */
class InAppChannelTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private final InappNotificationAppender appender = mock(InappNotificationAppender.class);
    private final InAppChannel channel = new InAppChannel(appender, CLOCK);

    private static TemplateRenderer.RenderedMessage message(String body) {
        return new TemplateRenderer.RenderedMessage(null, body, 1);
    }

    @Test
    void channelReturnsInApp() {
        assertThat(channel.channel()).isEqualTo("IN_APP");
    }

    @Test
    void sendDelegatesToTheAppenderWithTheDerivedTitleAndFixedClockInstant() {
        UUID accountUuid = UUID.randomUUID();

        channel.send(accountUuid, accountUuid.toString(), "SECURITY", message("short body"));

        verify(appender).appendAndPush(eq(accountUuid), eq("SECURITY"), eq("short body"), eq("short body"), eq(FIXED_INSTANT));
    }

    @Test
    void titleEqualsBodyWhenAtOrBelowTheHundredCharacterLimit() {
        UUID accountUuid = UUID.randomUUID();
        String body100 = "x".repeat(100);

        channel.send(accountUuid, accountUuid.toString(), "SECURITY", message(body100));

        verify(appender).appendAndPush(eq(accountUuid), eq("SECURITY"), eq(body100), eq(body100), eq(FIXED_INSTANT));
    }

    @Test
    void titleIsTruncatedWithAnEllipsisWhenBodyExceedsTheHundredCharacterLimit() {
        UUID accountUuid = UUID.randomUUID();
        String body101 = "x".repeat(101);
        String expectedTitle = "x".repeat(100) + "…";

        channel.send(accountUuid, accountUuid.toString(), "SECURITY", message(body101));

        verify(appender).appendAndPush(eq(accountUuid), eq("SECURITY"), eq(expectedTitle), eq(body101), eq(FIXED_INSTANT));
    }

    // --- AC7/Finding #8: category validation, before any appender call --------------------

    @Test
    void sendThrowsForANullCategoryWithoutCallingTheAppender() {
        UUID accountUuid = UUID.randomUUID();

        assertThatThrownBy(() -> channel.send(accountUuid, accountUuid.toString(), null, message("body")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(appender);
    }

    @Test
    void sendThrowsForABlankCategoryWithoutCallingTheAppender() {
        UUID accountUuid = UUID.randomUUID();

        assertThatThrownBy(() -> channel.send(accountUuid, accountUuid.toString(), "   ", message("body")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(appender);
    }

    // --- AC7: body validation, before any appender call -------------------------------------

    @Test
    void sendThrowsForANullBodyWithoutCallingTheAppender() {
        UUID accountUuid = UUID.randomUUID();

        assertThatThrownBy(() -> channel.send(accountUuid, accountUuid.toString(), "SECURITY", message(null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(appender);
    }

    @Test
    void sendThrowsForABlankBodyWithoutCallingTheAppender() {
        UUID accountUuid = UUID.randomUUID();

        assertThatThrownBy(() -> channel.send(accountUuid, accountUuid.toString(), "SECURITY", message("   ")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(appender);
    }
}
