package com.themistra.notification.consumer;

import java.util.Map;
import java.util.UUID;

/**
 * The seam {@link AuthEventConsumer} (and, later, {@code PaymentEventConsumer} - task 7) calls
 * once it has resolved which notification a consumed event represents. Implemented for real by
 * whichever task first builds real dispatch (task 11: {@code DeliveryOrchestrator}; task 12:
 * {@code EmailChannel}) - {@link NoOpNotificationDispatcher} is a temporary, real (not a stub)
 * implementation for now.
 *
 * <p>Deliberately does not carry {@code email} (Kimi Phase 3 Finding #8): a future implementation
 * resolves the recipient via {@code contact_projection}
 * ({@code preference.ContactProjectionRepository}), not from the triggering event directly - the
 * caller's own {@code ContactProjectionUpdater.upsertEmail} call (which runs before
 * {@code dispatch}, in the same transaction) ensures that projection is at least as fresh as this
 * event allows.</p>
 *
 * <p>Lives in {@code consumer/} today because the frozen brief names this exact package (Kimi Phase
 * 8 Finding #7) - once a real implementation lands in a future {@code delivery/}-style package
 * (task 11/12), that package will depend back on this one, which is architecturally backward.
 * Relocating this interface to a neutral package (e.g. {@code notification.delivery.api}) is a
 * reasonable follow-up for whichever task first adds a real implementer, not required by T06's own
 * scope.</p>
 */
public interface NotificationDispatcher {

    /**
     * @param accountUuid the recipient's own external identifier.
     * @param notificationKind one of {@code "verify_email"}, {@code "password_reset"}, or
     *                         {@code "user.registered"} - the literal left-hand keys of
     *                         {@code design.md} §4c's own topic-mapping table, not a resolved
     *                         template name (template-name-per-channel resolution needs a
     *                         {@code PreferenceResolver}/{@code Template}, neither of which exist
     *                         yet - deliberately left to whichever task implements this interface).
     * @param eventData raw, event-specific data a future renderer may need (e.g. {@code token} for
     *                  the two email-requested kinds; empty for {@code user.registered}).
     */
    void dispatch(UUID accountUuid, String notificationKind, Map<String, String> eventData);
}
