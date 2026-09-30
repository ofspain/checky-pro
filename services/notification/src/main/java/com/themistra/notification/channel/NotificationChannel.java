package com.themistra.notification.channel;

import com.themistra.notification.template.TemplateRenderer;

import java.util.UUID;

/**
 * The one interface every channel implements (L5) - introduced by this task (T11), since no
 * earlier task owns it, even though task 12's own text ("Implement {@code EmailChannel} behind
 * {@code NotificationChannel}") assumes it already exists by then.
 * {@code com.themistra.notification.delivery.DeliveryOrchestrator} (a sibling package) is this
 * interface's own sole caller today; real
 * implementations ({@code EmailChannel}, task 12; {@code InAppChannel}, task 13) don't exist yet -
 * this task also provides two temporary, real (not stub) implementations,
 * {@link NoOpEmailChannel}/{@link NoOpInAppChannel}, mirroring {@code NoOpNotificationDispatcher}'s
 * own established precedent (T06).
 */
public interface NotificationChannel {

    /**
     * @return {@code "EMAIL"} or {@code "IN_APP"} - matches {@code PreferenceResolver}'s own
     * {@code channel} argument and {@code delivery_log.channel} exactly.
     */
    String channel();

    /**
     * @param accountUuid the recipient's own external identifier.
     * @param recipient the email address for {@code EMAIL}, the account UUID's own string form
     *                  for {@code IN_APP} (Kimi Phase 3 Finding #2/#6) - resolved once by
     *                  {@code DeliveryOrchestrator} before this call, not by the channel itself.
     * @param message the already-rendered subject/body/version (T09).
     */
    void send(UUID accountUuid, String recipient, TemplateRenderer.RenderedMessage message);
}
