package com.themistra.auth.account.event;

import com.themistra.auth.account.AccountStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire body for auth.user.lifecycle (target-design §12). Owned by the account module — the
 * events module stays domain-agnostic and only ever handles the serialized JSON.
 *
 * <p>{@code email} was added to unblock notification-service's own T05 (contact projection) —
 * see {@link com.themistra.auth.account.event.EmailRequestedEventPayload}'s own Javadoc for the
 * full rationale. Backward-compatible, additive field in the same v1 schema file.</p>
 *
 * <p>{@code eventType} was added to unblock notification-service's own T06 (auth event consumer).
 * Before this field, the only Kafka-visible signal was {@code status}, and {@code ACTIVE} results
 * from three distinct transitions - {@code user.registered} (from {@code PENDING_VERIFICATION}),
 * {@code user.reinstated} (from {@code SUSPENDED}), and {@code user.unlocked} (from {@code LOCKED})
 * - genuinely indistinguishable from {@code status} alone, yet R6 requires sending a welcome
 * message only for {@code user.registered}. {@code AccountService} already threads a distinct
 * {@code eventType} string through every call to {@link com.themistra.auth.account.AccountService}'s
 * own {@code publishLifecycleEvent} (see its own {@code outboxPublisher.publish} call) - this field
 * just carries that same, already-real value into the payload too. Open string, not a closed enum
 * (mirrors {@code EmailRequestedEventPayload.purpose}'s own precedent) - the known values today are
 * {@code user.registered}, {@code user.suspended}, {@code user.reinstated}, {@code user.deleted},
 * {@code user.locked}, {@code user.unlocked}.</p>
 */
public record UserLifecycleEventPayload(
        UUID accountUuid,
        AccountStatus status,
        String email,
        String eventType,
        Instant occurredAt
) {
}
