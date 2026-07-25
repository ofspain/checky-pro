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
 */
public record EmailRequestedEventPayload(
        UUID accountUuid,
        String purpose,
        String token,
        Instant occurredAt
) {

    @Override
    public String toString() {
        return "EmailRequestedEventPayload[accountUuid=" + accountUuid + ", purpose=" + purpose
                + ", occurredAt=" + occurredAt + "]";
    }
}
