# auth · T06 — Design Challenge Findings

Adversarial review of `artifacts/02-task-implementation-brief.md` against the spec package,
existing code, and `agents.md`. Findings are numbered for reference.

## Finding 1 — Raw verification token in the `auth.email.requested` event conflicts with the credentials-at-rest/in-transit standing rule (HIGH)

**Issue:** The brief proposes putting the raw token in `EmailRequestedEventPayload`. That payload is
serialized into the `outbox_event` table and then mirrored to Kafka. `agents.md` states that
"credentials at rest are hashed or envelope-encrypted" and "any credential in transit appears
exactly once (the creation response)." The event is neither the creation response nor a hashed
value, and the outbox row keeps the raw token at rest until the relay processes and archives it.

**Severity:** HIGH — a direct conflict with an authoritative standing rule, even though the brief
explicitly flags it as a deliberate exception. The exception is not captured in a LOCKED decision,
which is the mechanism `agents.md` says must be used to override its standing rules.

**Evidence:**
- TIB Scope lines 35–39: "`EmailRequestedEventPayload` carries the **raw verification token** ... a
deliberate, one-time exception."
- `agents.md` (Credentials at rest / in transit): "Credentials at rest are hashed or
envelope-encrypted; any credential in transit appears exactly once (the creation response)."
- `OutboxPublisher.java` lines 25–35: the payload is serialized to JSON and inserted into the
outbox table inside the caller's transaction.

**Recommended brief amendment:** Either (a) add a T06-specific LOCKED decision that explicitly
authorizes this exception and documents mitigations (TLS in transit, no downstream logging,
outbox cleanup/retention), or (b) redesign so the raw token never leaves the service boundary
(e.g., Notification Service calls back with a short-lived signed exchange token, or the event
carries an encrypted envelope using a key shared only with Notification Service). If (a) is chosen,
also require that `EmailRequestedEventPayload` is excluded from any default `toString`/logging and
that outbox retention is bounded.

---

## Finding 2 — `verify-email` may leak account state if `Account.activateEmail()` throws `InvalidAccountStateException` (HIGH)

**Issue:** `Account.activateEmail()` only permits `PENDING_VERIFICATION → ACTIVE`. If T05's
purpose-generic `consume` returns a UUID for an account that is `ACTIVE`, `LOCKED`, etc., calling
`activateEmail` will throw `InvalidAccountStateException`. The existing
`AccountExceptionHandler` maps that to a `409 CONFLICT` with `ProblemTypes.INVALID_STATE`, which is
a different response from the intended uniform R5 rejection and reveals that the account exists in
a non-pending state.

**Severity:** HIGH — violates L5/R5 enumeration safety.

**Evidence:**
- TIB Scope lines 23–26: "Consumes the token ... on success, activates the account ... on any
failure reason, a single uniform response (R4, R5)."
- `AccountService.java` lines 84–91: `activateEmail(UUID, UUID)` throws
`InvalidAccountStateException` if status is not `PENDING_VERIFICATION`.
- `AccountExceptionHandler.java` lines 25–32: maps `InvalidAccountStateException` to `409`
`INVALID_STATE`.
- Frozen T05 Finding 4 disposition: T05 rejects only `DELETED`/`SUSPENDED`; purpose-specific state
filtering belongs to T06 callers.

**Recommended brief amendment:** State explicitly that `verify-email` must check the account is in
`PENDING_VERIFICATION` *before* calling `activateEmail`, and any mismatch (including `ACTIVE`,
`LOCKED`, etc.) must be handled as a uniform R5 token-rejection response — never allowed to
propagate as `InvalidAccountStateException`.

---

## Finding 3 — The `verify-email` transaction boundary across two services is unspecified (MEDIUM)

**Issue:** T05's `VerificationTokenService.consume` is `@Transactional` and `AccountService.activateEmail`
is `@Transactional`. If the controller calls one then the other without an enclosing transaction,
the consume and activation happen in separate transactions. A failure after consume (e.g., activation
throws, event serialization fails) would leave a consumed token with an unactivated account.

**Severity:** MEDIUM — the brief says "this task's transaction must encompass both the consume and
the activation," but it does not say how that is achieved.

**Evidence:**
- TIB State Changes lines 100–103: "`verify-email` success: ... both in the transaction that also
consumed the token."
- `VerificationTokenService.java` line 106: `consume` is `@Transactional` (REQUIRED).
- `AccountService.java` line 84: `activateEmail` is `@Transactional` (REQUIRED).

**Recommended brief amendment:** Specify that `AccountService` (or a new T06 service method)
provides a single `@Transactional` entry point wrapping `consume`, `activateEmail`, and event
emission, so the controller does not orchestrate across transaction boundaries.

---

## Finding 4 — `resend-verification` has a timing side-channel between match and no-match paths (MEDIUM)

**Issue:** For a matching `PENDING_VERIFICATION` email, `resend-verification` performs a lookup,
calls T05 `issue` (which invalidates a prior token and inserts a new row), and writes an outbox
row. For a non-matching email or wrong-state account, it does none of that. Even though the HTTP
response body is uniform, the latency difference and the outbox write are observable signals that
could reveal account existence/state.

**Severity:** MEDIUM — enumeration safety requires both body *and timing* to be indistinguishable
(L5); the brief mentions timing only generically in Constraints.

**Evidence:**
- TIB Scope lines 27–29: resend is public/email-identified; response uniform; token issued and
event emitted only for matching `PENDING_VERIFICATION` account.
- TIB Constraints lines 180–181: "Enumeration safety: neither new endpoint's response timing or
body may differ."
- `OutboxPublisher.java` lines 25–35: the matched path does a synchronous DB insert.

**Recommended brief amendment:** Either (a) make the no-match path perform a lightweight,
deterministic amount of work (e.g., a constant-time hash or a no-op outbox write) so latency is
statistically indistinguishable, or (b) accept the side-channel as out of scope and document the
trade-off explicitly. Also add a test that asserts response-time parity or, at minimum, that both
paths complete without touching the same observable resources.

---

## Finding 5 — Self-service `activateEmail` audit `actorUuid` is undefined (MEDIUM)

**Issue:** `AccountService.activateEmail(UUID accountUuid, UUID actorUuid)` records an
`account.activated` audit event with the supplied `actorUuid`. For the admin flow, `actorUuid` is
the admin. For self-service `verify-email`, the actor is the account itself, but the brief does not
say what value to pass. Passing `null` produces an audit row with a null actor; passing the
account's own UUID is more consistent with "self" action.

**Severity:** MEDIUM — not a correctness bug, but an unstated assumption that affects audit-trail
quality and possibly future compliance queries.

**Evidence:**
- `AccountService.java` lines 84–91: `activateEmail` records audit with `actorUuid`.
- TIB Scope lines 23–26: "activates the account (`Account.activateEmail()`)" but never mentions the
audit/actor parameter.

**Recommended brief amendment:** Specify the `actorUuid` for self-service activation (recommend the
account's own UUID) and add an acceptance test asserting the audit row's `actor_uuid` equals the
activated account.

---

## Finding 6 — Outbox aggregate ID for `auth.email.requested` is unspecified (LOW/MEDIUM)

**Issue:** `OutboxPublisher.publish` requires an `aggregateId`. For `verification-token` events, the
aggregate ID could be the account UUID, the token hash, or something else. The choice determines
Kafka partitioning and ordering guarantees for downstream consumers. The brief says the payload
contains the account identifier but does not say what `aggregateId` to publish.

**Severity:** LOW/MEDIUM — wrong choice could cause event reordering or complicate deduplication for
Notification Service.

**Evidence:**
- TIB Dependencies line 74: `OutboxPublisher.publish(...)`.
- `OutboxPublisher.java` line 25: signature requires `aggregateId`.
- `EventTopics.java`: maps aggregate type to topic.

**Recommended brief amendment:** Lock the aggregate ID to the account UUID (so all email events for
one user stay ordered on the same Kafka partition) and add a test asserting the outbox row's
`aggregate_id` field equals the account UUID.

---

## Finding 7 — The new R5 problem type and HTTP status are not fixed (LOW)

**Issue:** The brief says "a new `common.ProblemTypes` entry for R5's uniform token-rejection
response" but does not name the URI or pick the HTTP status. That makes contract tests and
endpoint behavior ambiguous for reviewers and for task 33's contract authoring.

**Severity:** LOW — implementation can choose, but the brief should at least give a default to keep
Phase 5 deterministic.

**Evidence:**
- TIB Scope lines 33–34: "A new `common.ProblemTypes` entry ... none of the five existing types fit."
- `ProblemTypes.java` lines 13–18: existing types.

**Recommended brief amendment:** Propose a concrete value, e.g.,
`ProblemTypes.INVALID_TOKEN = URI.create(BASE + "invalid-token")` returned as `400 Bad Request`,
and require `verify-email` failures to use it for every rejection reason.

---

## Finding 8 — `resend-verification` bean-validation `@NotBlank` still distinguishes a missing email from a nonexistent email (LOW)

**Issue:** The brief says the DTO should use `@NotBlank` on the email field. A request with a
missing or blank email will receive a `400` validation-error body from `ApiExceptionHandler`, while
a nonexistent email receives the uniform `202` acknowledgement. That is a distinguishable response,
although it does not reveal account existence.

**Severity:** LOW — not account-enumeration, but an inconsistency with the ideal that every
resend response is identical.

**Evidence:**
- TIB Constraints lines 177–179: "both new request DTOs validate their required field
(`@NotBlank` token/email)."
- `ApiExceptionHandler.java` lines 32–38: maps `MethodArgumentNotValidException` to a validation
problem.

**Recommended brief amendment:** Decide whether missing/blank email should also return the uniform
acknowledgement. If yes, drop `@NotBlank` and treat blank the same as "no matching account" in the
service (return uniform ack). If the `400` distinction is acceptable, explicitly document it as a
malformed-request exception that is intentionally distinguishable from the R5/ack path.

---

## Finding 9 — Register's duplicate path does not emit an event but still tells the user to "check your inbox" (LOW)

**Issue:** The existing `register` endpoint catches `DuplicateEmailException` and returns the same
`202` acknowledgement. With T06, the acknowledgement text still says "check your inbox." For a
duplicate email, no account is created and no `auth.email.requested` event is emitted, so the user
will not receive an email. This is acceptable enumeration safety but creates a misleading UX that
may surface as a support issue.

**Severity:** LOW — not a security or correctness defect in this task, but a product-side
consequence of the uniform-response requirement that should be visible to the author.

**Evidence:**
- `AccountController.java` lines 37–45: catches `DuplicateEmailException` and returns standard ack.
- `RegistrationAcknowledgement.java` lines 11–14: message text tells user to check inbox.
- TIB Scope lines 21–22: `register` emits event only when account creation succeeds.

**Recommended brief amendment:** No code change required, but add an Open Question or forward note
to product/UX that the uniform response may confuse users who attempt duplicate registration. The
Notification Service could handle resend logic separately, but that is outside T06.

---

## Summary

The brief correctly scopes the self-service verification flow, but Finding 1 and Finding 2 are
blocker-level: the raw-token-in-event design violates a standing security rule unless explicitly
overridden as a LOCKED decision, and the `verify-email` success path can leak account state if
activation is attempted on the wrong status. Findings 3–8 are design-level clarifications needed
before Phase 5, and Finding 9 is a UX consequence worth recording.
