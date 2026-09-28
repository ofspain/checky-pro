package com.themistra.auth.account.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire body for auth.email.requested. {@code purpose} is a plain string ({@code "verify_email"},
 * later {@code "password_reset"}) rather than the internal {@code VerificationToken.Purpose} enum
 * — the external event contract stays decoupled from the JPA representation.
 *
 * <p>{@code token} is the raw verification token — a deliberate, LOCKED exception to the
 * standing rule that a credential appears exactly once in the creation response (see
 * {@code agents.md}, T06 frozen brief Finding 1): Notification Service has no other channel to
 * obtain it. The overridden {@link #toString()} below is the corresponding mitigation — records
 * otherwise auto-generate a {@code toString()} that would print every component, exactly the leak
 * T05's equivalent {@code VerificationTokenResult} guarded against.</p>
 *
 * <p>{@code email} was added to unblock notification-service's own T05 (contact projection):
 * neither this event nor {@code auth.user.lifecycle} carried a recipient address, leaving
 * notification-service with no way to populate {@code contact_projection} without a synchronous
 * Auth call, which L2 forbids. Backward-compatible, additive field (`contracts/README.md`'s own
 * "backward-compatible evolution only" rule) - the same v1 schema file gained the property rather
 * than a new v2 file, matching the only precedent in this repo (no v2 event schema exists anywhere).
 * Unlike {@code token}, {@code email} is not a secret and is safe to include in {@link #toString()}.</p>
 */
public record EmailRequestedEventPayload(
        UUID accountUuid,
        String purpose,
        String token,
        String email,
        Instant occurredAt
) {

    @Override
    public String toString() {
        return "EmailRequestedEventPayload[accountUuid=" + accountUuid + ", purpose=" + purpose
                + ", email=" + email + ", occurredAt=" + occurredAt + "]";
    }
}
