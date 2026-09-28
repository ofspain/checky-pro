package com.themistra.notification.consumer.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialization record for {@code auth.email.requested}
 * ({@code contracts/events/auth/email-requested.v1.schema.json}). Hand-written, not generated
 * (Kimi Phase 3 Finding #6, frozen brief's own disclosed deviation from `agents.md`'s codegen
 * rule): no code-generation tooling exists anywhere in this repo for {@code contracts/events/*} -
 * {@code services/auth}'s own producer-side {@code EmailRequestedEventPayload} is itself
 * hand-written for the same reason. {@code EmailRequestedEventContractTest} is the structural
 * substitute for generation, asserting this record's own serialization matches the real schema
 * file exactly.
 *
 * <p>{@code toString()} is overridden to exclude {@code token} - mirrors
 * {@code services/auth}'s own {@code EmailRequestedEventPayload} (L4: never log tokens/secrets).</p>
 */
public record EmailRequestedEvent(
        UUID accountUuid,
        String purpose,
        String token,
        String email,
        Instant occurredAt
) {

    @Override
    public String toString() {
        return "EmailRequestedEvent[accountUuid=" + accountUuid + ", purpose=" + purpose
                + ", email=" + email + ", occurredAt=" + occurredAt + "]";
    }
}
