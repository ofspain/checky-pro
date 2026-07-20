# 3. Requirements — acceptance criteria (EARS)

Each requirement is independently testable and maps to a named test in [`package.md`](package.md) §8. No real email is sent in tests; the outbound transport is a capturing fake.

## Event → notification fan-out

- R1. WHEN an `auth.email.requested` event with purpose `verify_email` is consumed, THEN the system SHALL send the recipient an email-verification message containing the verification link.
- R2. WHEN an `auth.email.requested` event with purpose `password_reset` is consumed, THEN the system SHALL send the recipient a password-reset message containing the reset link.
- R3. WHEN a `payments.invoice.created` event is consumed, THEN the system SHALL notify the invoice's recipients (per the §4c matrix / Q7) that an invoice was created.
- R4. WHEN a `payments.payment.seen` event is consumed, THEN the system SHALL notify the configured recipients that a payment has been seen on-chain.
- R5. WHEN a `payments.receipt.issued` event is consumed, THEN the system SHALL notify both parties and include the receipt link.
- R6. WHEN an `auth.user.lifecycle` event with type `user.registered` is consumed, THEN the system SHALL send the user a welcome/onboarding message.

## Idempotency

- R7. WHEN a consumed event carrying a stable event key is processed, THEN the system SHALL record that key so the same event is processed at most once.
- R8. IF the same event is redelivered (at-least-once Kafka semantics or a consumer-group rebalance), THEN the system SHALL NOT produce a second delivery for it.

## Preferences & channels

- R9. WHEN determining how to reach a recipient for a given event type, THEN the system SHALL resolve that recipient's channel preferences and deliver only on the enabled channels.
- R10. IF a recipient has opted out of a channel for a given notification category, THEN the system SHALL suppress delivery on that channel and record the suppression in the delivery log.
- R18. IF a recipient has no stored preference for an event category, THEN the system SHALL apply the documented default preference (per §4c) rather than failing or sending on all channels.
- R16. WHEN an authenticated recipient connects to the in-app notification stream, THEN the system SHALL stream only that recipient's notifications and SHALL reject unauthenticated connections.
- R17. WHEN an authenticated recipient calls the in-app read API, THEN the system SHALL return their unread notifications and SHALL NOT return another account's notifications.

## Delivery log & retry

- R11. WHEN a delivery is attempted on any channel, THEN the system SHALL append a delivery-log record capturing recipient, channel, source event key, template version, outcome, and timestamp.
- R12. IF a channel delivery fails with a transient error, THEN the system SHALL mark the attempt `FAILED`, retain it in the log, and schedule a bounded retry per the backoff policy.
- R13. IF a delivery has failed the maximum number of attempts, THEN the system SHALL stop retrying, record a terminal `DEAD_LETTERED` outcome, and SHALL NOT retry indefinitely.

## Rendering & safety

- R14. WHEN rendering a message, THEN the system SHALL populate the versioned template for the event type and selected channel from the event data.
- R15. WHEN rendering a message or writing a log line, THEN the system SHALL NOT include secrets, access/refresh tokens, raw password-reset token values beyond the intended one-time link, or full API keys.

## Contracts & boundaries

- R19. WHERE the consumed event schemas under `contracts/events/{auth,payments}/` are authored, THEN the consumers SHALL deserialize against them and a schema mismatch SHALL fail a contract test rather than a production delivery.
