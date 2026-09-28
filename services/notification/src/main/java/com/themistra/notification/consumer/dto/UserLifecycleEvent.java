package com.themistra.notification.consumer.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialization record for {@code auth.user.lifecycle}
 * ({@code contracts/events/auth/user-lifecycle.v1.schema.json}). Hand-written, not generated
 * (Kimi Phase 3 Finding #6, see {@code EmailRequestedEvent}'s own Javadoc for the full rationale).
 * {@code UserLifecycleEventContractTest} is the structural substitute for generation.
 *
 * <p>{@code eventType} (not {@code status}) is the field this consumer relies on to distinguish
 * {@code user.registered} from {@code user.reinstated}/{@code user.unlocked} - all three produce
 * {@code status=ACTIVE} alike (see {@code services/auth}'s own commit adding this field).</p>
 */
public record UserLifecycleEvent(
        UUID accountUuid,
        String status,
        String email,
        String eventType,
        Instant occurredAt
) {
}
