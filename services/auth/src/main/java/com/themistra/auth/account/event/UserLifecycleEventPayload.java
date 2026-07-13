package com.themistra.auth.account.event;

import com.themistra.auth.account.AccountStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire body for auth.user.lifecycle (target-design §12). Owned by the account module — the
 * events module stays domain-agnostic and only ever handles the serialized JSON.
 */
public record UserLifecycleEventPayload(
        UUID accountUuid,
        AccountStatus status,
        Instant occurredAt
) {
}
