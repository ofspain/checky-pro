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
 */
public record UserLifecycleEventPayload(
        UUID accountUuid,
        AccountStatus status,
        String email,
        Instant occurredAt
) {
}
