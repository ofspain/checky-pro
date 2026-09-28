package com.themistra.notification.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Temporary, real (not a stub/TODO) implementation of {@link NotificationDispatcher} - replaced
 * (this file deleted, the real implementation added in its place) by whichever task first provides
 * real dispatch (task 11: {@code DeliveryOrchestrator}; task 12: {@code EmailChannel}), not
 * intended to coexist with a real implementation (component-scan ordering between two
 * {@code @Component}-scanned beans of the same interface type isn't guaranteed by Spring Boot, so
 * a {@code @ConditionalOnMissingBean}-style self-effacement was deliberately not used here - see
 * Phase 5's own design note).
 *
 * <p>Logs only {@code accountUuid}/{@code notificationKind}/{@code eventData}'s own key set -
 * never {@code eventData}'s own values (Kimi Phase 3 Finding #4): {@code eventData} may carry a
 * raw verification/reset token (L4: never log tokens/secrets).</p>
 */
@Component
public class NoOpNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NoOpNotificationDispatcher.class);

    @Override
    public void dispatch(UUID accountUuid, String notificationKind, Map<String, String> eventData) {
        log.info("Dispatch (no-op): accountUuid={}, notificationKind={}, eventDataKeys={}",
                accountUuid, notificationKind, eventData.keySet());
    }
}
