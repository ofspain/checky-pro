package com.themistra.notification.channel;

import com.themistra.notification.template.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Temporary, real (not stub/TODO) implementation of {@link NotificationChannel} for {@code IN_APP}
 * - replaced (this file deleted, the real implementation added in its place) by task 13's own
 * {@code InAppChannel}, mirroring {@code NoOpNotificationDispatcher}'s own established precedent
 * (T06).
 */
@Component
public class NoOpInAppChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(NoOpInAppChannel.class);

    @Override
    public String channel() {
        return "IN_APP";
    }

    @Override
    public void send(UUID accountUuid, String recipient, TemplateRenderer.RenderedMessage message) {
        // Kimi Phase 8 Finding #5: DEBUG, not INFO - a temporary no-op placeholder should not
        // generate production-volume log lines for every real dispatch.
        if (log.isDebugEnabled()) {
            log.debug("In-app dispatch (no-op): accountUuid={}, recipient={}, message={}",
                    accountUuid, recipient, message);
        }
    }
}
