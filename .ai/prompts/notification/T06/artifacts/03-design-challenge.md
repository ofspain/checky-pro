<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T06 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T06 — Auth event consumer |
| **Spec section** | Consumers & idempotency |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the T06 Phase 2 brief.

---

## Finding 1 · Idempotency key format is not specified and is left to Phase 5

**Severity:** High

**Evidence:**
- The brief's Constraints section says "Phase 5's own job to pin down the exact string format (e.g. `accountUuid + ':' + eventType/purpose + ':' + occurredAt`)".
- `agents.md` states: "Idempotent by event key is non-negotiable. Every consumer dedupes; replaying any event twice yields exactly one delivery."
- The event key must be stable across redelivery but not rely on Kafka offset/partition. The exact delimiter, field order, and whether `occurredAt` is included as an `Instant` string or ISO timestamp all affect whether two logically identical redeliveries produce the same key.

**Recommended brief amendment:**
Define the exact key format in the brief (not defer it). A stable, payload-only key is the core correctness property of this consumer. Suggested format:
- `auth.email.requested`: `accountUuid + ":" + purpose + ":" + occurredAt`
- `auth.user.lifecycle`: `accountUuid + ":" + eventType + ":" + occurredAt`

Document that `occurredAt` is parsed to `Instant` and serialized to ISO-8601 UTC for the key, so redelivered copies of the same payload produce byte-identical keys.

---

## Finding 2 · No specified behavior for unknown `purpose` values on `auth.email.requested`

**Severity:** Medium

**Evidence:**
- `contracts/events/auth/email-requested.v1.schema.json` explicitly says `purpose` is "Deliberately not a closed enum ... open to more values over time."
- AC4 maps only `verify_email` and `password_reset` to `notificationKind` values. It does not say what happens for `purpose` values other than those two.
- In contrast, AC4 explicitly states that non-`user.registered` `eventType` values on `auth.user.lifecycle` are **not** dispatched.

**Recommended brief amendment:**
Make the handling of unknown `auth.email.requested` purposes explicit and consistent with lifecycle: either dispatch them as-is (pushing the decision to the future `NotificationDispatcher`) or ignore them. Given R1/R2 only cover the two known purposes, recommend ignoring unknown purposes by returning without calling `NotificationDispatcher.dispatch`, so the consumer does not hand unhandled notification kinds to a downstream task that is not yet equipped to reject them.

---

## Finding 3 · Transaction boundary for the Kafka listener methods is not specified

**Severity:** Medium

**Evidence:**
- `IdempotencyGuard.recordIfNew` and `ContactProjectionUpdater.upsertEmail` both use `@Transactional(REQUIRED)`.
- The brief does not say whether the `@KafkaListener` methods themselves are annotated `@Transactional`.
- If the listener method is not transactional, `recordIfNew` creates its own transaction, `upsertEmail` joins it, and `dispatch` runs outside any transaction. If the listener method is transactional, all three steps share one transaction.
- This affects failure behavior: if `dispatch` (today a no-op, tomorrow real) throws, should the idempotency record and projection update roll back with it? The brief is silent.

**Recommended brief amendment:**
Specify the transaction boundary. A sensible default is to annotate each `@KafkaListener` method with `@Transactional` so idempotency, projection refresh, and dispatch are atomic. If a different choice is intended, document the rationale and the expected Kafka retry behavior.

---

## Finding 4 · `AuthEventConsumerNoOpDispatcher` risks logging the raw token

**Severity:** Medium

**Evidence:**
- The brief says the no-op dispatcher "logs the dispatch call at INFO and does nothing else" with `eventData` as a parameter. For `auth.email.requested(verify_email)` and `auth.email.requested(password_reset)`, `eventData` contains the raw `token`.
- `agents.md` Security section: "Never log tokens, secrets, reset-token values, full API keys, or PII."
- `services/auth/.../EmailRequestedEventPayload.java` even overrides `toString()` to avoid logging the token.

**Recommended brief amendment:**
Specify that the no-op dispatcher must log only `accountUuid`, `notificationKind`, and the *keys* of `eventData` (or a redacted map), never the `token` value. Add a test assertion that the token does not appear in logs (e.g., capture `TestLogger` output).

---

## Finding 5 · `spring.kafka.consumer.auto-offset-reset` value is not chosen

**Severity:** Medium

**Evidence:**
- The brief says to add `spring.kafka.consumer.auto-offset-reset` but does not choose `earliest` or `latest`.
- This choice determines whether a newly deployed consumer group replays all historical `auth.email.requested`/`auth.user.lifecycle` events or starts from the tail of the topic.
- For verification/reset emails, replaying old events could re-send expired links; for welcome emails, it could re-welcome inactive users.

**Recommended brief amendment:**
Specify the value and rationale. For a notification consumer, `latest` is usually the safer default: the service processes events from deployment onward and does not replay history. If `earliest` is required for recovery or backfill scenarios, document that explicitly and add a compensating design note (e.g., idempotency makes replay safe, but business impact must be considered).

---

## Finding 6 · Hand-written DTOs conflict with `agents.md`'s "generated from contracts" rule

**Severity:** Medium

**Evidence:**
- `agents.md` Events & messaging section: "Deserialization models are generated from `contracts/` — never hand-written. A schema mismatch fails a contract test, not a production delivery."
- The brief instructs creating hand-written records `EmailRequestedEvent.java` and `UserLifecycleEvent.java` "mirroring the real, current contract shape."
- The auth service itself uses hand-written payload records (`EmailRequestedEventPayload`, `UserLifecycleEventPayload`), so there is precedent, but `agents.md` is still authoritative for this service.

**Recommended brief amendment:**
Either:
- add a contract-test requirement that validates the hand-written records against `contracts/events/auth/*.schema.json` (acknowledging generation is not yet set up but preserving the spirit of the rule), or
- set up code generation from the schemas now and remove the hand-written DTO requirement.

If the former, explicitly state in the brief that the hand-written records are a temporary deviation pending code-generation setup, and that a contract test is required.

---

## Finding 7 · No error-handling strategy for deserialization or processing failures

**Severity:** Medium

**Evidence:**
- The brief says "deserialize via Jackson into the matching DTO" and "no defensive null-handling beyond what a failed deserialization already surfaces as a real exception."
- It does not say whether a deserialization failure should:
  - throw (causing Kafka retry, potentially poisoning the topic if the message is genuinely malformed), or
  - be logged and skipped (dead-letter behavior), or
  - go to a dedicated dead-letter topic.
- `agents.md` says "Retries are bounded and terminate in a dead-letter outcome, never an infinite loop."

**Recommended brief amendment:**
Specify the consumer's error-handling strategy. For T06, the simplest safe choice is to let exceptions propagate (Spring Kafka retries with default backoff), because malformed messages should fail a contract test before reaching production. Document this choice and note that a dedicated dead-letter handler is a future enhancement.

---

## Finding 8 · Future dispatcher will read email from projection while event carries a fresher email

**Severity:** Low

**Evidence:**
- The `NotificationDispatcher` seam receives `accountUuid` but not `email`. Future tasks will presumably look up the recipient in `contact_projection`.
- The consumer calls `ContactProjectionUpdater.upsertEmail` before dispatch, but the upsert may be rejected if the event's `occurredAt` is older than the projection's current `updated_at`.
- In that case, the projection email may be newer than the event email, and the dispatcher would use the newer email. This could be correct (projection is more recent) or surprising (the event was specifically about this email address).

**Recommended brief amendment:**
Add a note under `NotificationDispatcher` that the seam intentionally does not carry `email`; downstream dispatchers must resolve the recipient from `contact_projection`, and the consumer's `upsertEmail` call ensures the projection is at least as fresh as the event allows.

---

## Finding 9 · `AuthEventConsumerNoOpDispatcher` name implies auth-specific scope

**Severity:** Low

**Evidence:**
- The class implements the generic `NotificationDispatcher` interface and is a fallback for any caller, not just auth events.
- The name `AuthEventConsumerNoOpDispatcher` suggests it belongs only to the auth-event consumer path.

**Recommended brief amendment:**
Rename to `NoOpNotificationDispatcher` to reflect that it is a generic, temporary implementation of the `NotificationDispatcher` seam. This avoids implying a package/class coupling that does not exist.

---

## Summary

The T06 brief correctly identifies the seam (`NotificationDispatcher`) needed to keep task 6 from bleeding into tasks 9/11/12, respects L1/L2/L5, and correctly scopes the 3 dispatchable notification kinds. The most consequential gap is **Finding 1**: the idempotency key format is the non-negotiable correctness property of the consumer, yet it is deferred to Phase 5. **Findings 2–5** are medium-severity ambiguities (unknown purpose handling, transaction boundary, token logging, and offset reset) that should be resolved in the brief before implementation. **Finding 6** flags a real tension with `agents.md`'s generated-model rule. Findings 7–9 are smaller precision and naming items.
