package com.themistra.notification.channel;

import com.themistra.notification.template.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Temporary, real (not stub/TODO) implementation of {@link NotificationChannel} for {@code EMAIL}
 * - replaced (this file deleted, the real implementation added in its place) by task 12's own
 * {@code EmailChannel}, mirroring {@code NoOpNotificationDispatcher}'s own established precedent
 * (T06).
 *
 * <p>Logs only {@code accountUuid}/{@code recipient}/{@code message} - {@code message}'s own
 * {@code toString()} already excludes {@code subject}/{@code body} content (T09, Kimi Phase 8
 * Finding #2), so this log line never risks leaking a rendered token/PII (L4).</p>
 */
@Component
public class NoOpEmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailChannel.class);

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public void send(UUID accountUuid, String recipient, TemplateRenderer.RenderedMessage message) {
        log.info("Email dispatch (no-op): accountUuid={}, recipient={}, message={}",
                accountUuid, recipient, message);
    }
}
