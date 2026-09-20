# auth · T06 — Frozen Task Brief

**STATUS: FROZEN**
**Approved by:** femi (human approval gate, Phase 4)
**Date:** 2026-07-25
**Supersedes:** `artifacts/02-task-implementation-brief.md`, amended per `artifacts/03-design-challenge.md`.

Downstream phases (5 onward) implement against this document only.

---

## Phase 3 finding disposition

All 9 Kimi findings accepted. Two involved a real design trade-off, resolved explicitly by the
human approver rather than defaulted.

| # | Finding | Disposition |
|---|---|---|
| 1 | Raw token in `EmailRequestedEventPayload` conflicts with `agents.md`'s credential-handling rule, uncaptured as a LOCKED exception | **ACCEPTED — formalized as an explicit, documented exception** (not redesigned). Mirrors the precedent set by D-010's narrow KMS exception for TOTP seeds (T03 spec context): the raw token stays in the event payload because Notification Service cannot construct a working verification link without it, and no alternative mechanism (signed callback, encrypted envelope) exists without inventing Notification Service coordination this task does not own. Mitigations required: `EmailRequestedEventPayload` must be excluded from any default logging/`toString` exposure; outbox/Kafka retention for this topic must be bounded (existing platform-level retention policy applies — no new retention mechanism invented here). |
| 2 | `verify-email` can leak account state via `InvalidAccountStateException` | **ACCEPTED** — the account's status must be checked as `PENDING_VERIFICATION` *before* calling `activateEmail`; any other status (including `ACTIVE`, `LOCKED`) is treated as the uniform R5 rejection, never allowed to throw `InvalidAccountStateException` out to the controller. |
| 3 | Transaction boundary across consume/activate/emit unspecified | **ACCEPTED** — one new `@Transactional` `AccountService` method (not the controller) wraps `VerificationTokenService.consume`, the status check, `activateEmail`, and the `auth.user.registered` emission as a single unit. |
| 4 | `resend-verification` timing/outbox-write side-channel between match and no-match | **ACCEPTED — documented trade-off, no code change.** True constant-time behavior across DB+outbox operations is impractical to guarantee and the sibling `password-reset-request` flow (R12/R13, elsewhere in this spec) already has the identical shape/limitation. Recorded here as a known, accepted L5 gap — not silently ignored. |
| 5 | Self-service `activateEmail`'s audit `actorUuid` undefined | **ACCEPTED** — pass the activated account's own UUID as `actorUuid` for the self-service path (distinct from the admin path, which passes the acting admin's UUID). |
| 6 | Outbox `aggregateId` for `auth.email.requested` unspecified | **ACCEPTED** — lock to the account UUID, so all email-related events for one user stay ordered on the same Kafka partition. |
| 7 | New `ProblemTypes`/status for R5 rejection unfixed | **ACCEPTED** — `ProblemTypes.INVALID_TOKEN = URI.create(BASE + "invalid-token")`, returned as `400 Bad Request` for every `verify-email` rejection reason. |
| 8 | `resend-verification`'s `@NotBlank` still distinguishes blank input (400) from nonexistent email (202) | **ACCEPTED — keep as-is, documented only.** A malformed/blank request is a different information class than account existence (it reveals nothing about any specific account) and mirrors `RegisterAccountRequest`'s existing bean-validation precedent. No code change beyond what was already planned. |
| 9 | Duplicate-registration's "check your inbox" message is misleading UX for a user who won't receive an email | **ACCEPTED — documented only, no code change.** Forward note: Notification Service or product/UX may want to address this later; out of T06's scope. |

No findings rejected.

---

## Task

Self-service verification endpoints: extend `AccountController` with
`POST /accounts/verify-email` and `POST /accounts/resend-verification`. Emit
`auth.email.requested` via `OutboxPublisher`. Update `EventTopics` with the `verification-token`
aggregate mapping.

## Purpose

Replace the admin-only stand-in for email verification with the real self-service flow:
registration triggers a verification email request, the user redeems the token to activate their
own account, and can request a new token if needed — using T05's `VerificationTokenService`,
wired for the first time.

## Scope

**In:**
- `AccountService` gains a new `@Transactional` self-service activation method wrapping
  `VerificationTokenService.consume`, a `PENDING_VERIFICATION`-only status check (Finding 2),
  `Account.activateEmail()`, `auth.user.registered` emission, and an `account.activated` audit
  record with `actorUuid` = the account's own UUID (Finding 5).
- `AccountService.register(...)` gains an `auth.email.requested` (purpose `verify_email`)
  emission, aggregate ID = account UUID (Finding 6), in the same transaction as account creation.
- `POST /accounts/verify-email` — public, token-in-body. Success per R4; any rejection reason
  (not found/expired/used/wrong-status/unusable-account) returns the single uniform
  `ProblemTypes.INVALID_TOKEN` / `400` response (Findings 2, 7).
- `POST /accounts/resend-verification` — public, email-in-body. Uniform acknowledgement always;
  issues a token and emits an event only for a matching `PENDING_VERIFICATION` account. Timing/
  outbox-write side-channel between match/no-match is an accepted, documented trade-off (Finding
  4) — no constant-time engineering required.
- `EventTopics` gains `"verification-token" -> "auth.email.requested"` (R44).
- `PublicEndpoints` gains both new paths (L11).
- `common.ProblemTypes` gains `INVALID_TOKEN` (Finding 7).
- `EmailRequestedEventPayload` carries the raw verification token — a formalized, documented
  exception to the credential-handling standing rule (Finding 1), mitigated by excluding the
  payload from default logging/`toString` and relying on existing bounded outbox/Kafka retention.

**Out:**
- Password reset (task 7), change-own-password (task 8), registration's password-policy
  enforcement (task 9).
- Any Notification Service code, any actual email sending.
- Formal authoring of `contracts/events/auth/email-requested.v1.schema.json` (task 33).
- Resolving `package.md` §11 Q4 (email link base URL) — Notification Service's problem.
- Engineering constant-time response padding for `resend-verification` (Finding 4, accepted as a
  documented trade-off instead).
- Changing the duplicate-registration acknowledgement text (Finding 9, documented only).

## Business Rules

- **R3.** `register` emits `auth.email.requested` (purpose `verify_email`, aggregate ID = account
  UUID) in the same transaction as account creation.
- **R4.** A valid, unexpired, unused token to `verify-email`, for an account currently
  `PENDING_VERIFICATION`, activates the account and emits `auth.user.registered`.
- **R5** (widened at Phase 1). Any rejection reason — not found, expired, used, unusable account
  (`DELETED`/`SUSPENDED`), or **wrong status at activation time** (Finding 2) — produces the
  identical `400`/`INVALID_TOKEN` response.
- **R6** (modified at Phase 0). `resend-verification` is public, email-identified; uniform
  response regardless of match; token issued and event emitted only for a matching
  `PENDING_VERIFICATION` account.
- **R44.** `EventTopics.forAggregateType("verification-token")` returns `"auth.email.requested"`.

## Locked Decisions

- **L5.** Enumeration-safe responses.
- **L11** (widened). Both new public paths registered in `PublicEndpoints`.
- **T06-specific exception (Finding 1, human-approved).** The raw verification token appearing in
  `EmailRequestedEventPayload` — and therefore transiently at rest in the `outbox_event` table and
  in transit via Kafka — is an authorized, narrow exception to `agents.md`'s "credential in
  transit appears exactly once (the creation response)" rule, justified because Notification
  Service has no other channel to obtain it. Mitigations: the payload type must never be logged by
  default (no `toString` exposure of the token field in application logs); outbox/Kafka retention
  for this topic relies on the platform's existing bounded retention, not a new mechanism.

## Dependencies

- `VerificationTokenService.issue(accountUuid, Purpose.EMAIL_VERIFY)` / `.consume(rawToken)` (T05).
- `AccountRepository.findByEmail(String)`, `AccountRepository.findByAccountUuid(UUID)`.
- `Account.activateEmail()`, `Account.getStatus()` (status pre-check, Finding 2).
- `AuditService` (existing) — for the `account.activated` audit record on self-service activation.
- `OutboxPublisher.publish(...)`, `EventTopics` (one new map entry).
- `common.ProblemTypes.INVALID_TOKEN` (new, Finding 7).
- `Clock` (existing bean) if the event payload needs a timestamp.

## Inputs

- `register` — unchanged (`RegisterAccountRequest`).
- `verify-email` — raw token string (`VerifyEmailRequest`).
- `resend-verification` — email string (`ResendVerificationRequest`).

## Outputs

- `verify-email` success — a success response (exact body/status beyond "success" is a Phase 5
  decision).
- `verify-email` failure — `ProblemTypes.INVALID_TOKEN`, `400 Bad Request`, identical for every
  rejection reason including wrong account status (Finding 2/7).
- `resend-verification` — a single uniform acknowledgement body regardless of outcome (may reuse
  `RegistrationAcknowledgement`'s shape or an equivalently generic type — Phase 5 decision).
- `EmailRequestedEventPayload` — account identifier, purpose, and the raw token (Finding 1,
  formalized exception); must not have a `toString()` that exposes the token.

## State Changes

- `register`: unchanged account-creation behavior, plus one new outbox row
  (`auth.email.requested`, purpose `verify_email`, aggregate ID = account UUID) in the same
  transaction.
- `verify-email` success: single transaction covering token consume, status pre-check,
  `activateEmail`, `auth.user.registered` emission, and the `account.activated` audit record
  (actor = the account's own UUID).
- `verify-email` failure (any reason, including wrong status): no state change — the token is
  never consumed for an account that isn't `PENDING_VERIFICATION`, and the status check happens
  before, not after, any mutation.
- `resend-verification` match: one new `verification_tokens` row (via T05's `issue`, which itself
  invalidates any prior active token) plus one new outbox row (`auth.email.requested`).
- `resend-verification` no-match: no state change (accepted timing/observability trade-off per
  Finding 4 — not fully side-channel-free, documented not engineered around).

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/account/dto/VerifyEmailRequest.java`
- `services/auth/src/main/java/com/themistra/auth/account/dto/ResendVerificationRequest.java`
- `services/auth/src/main/java/com/themistra/auth/account/event/EmailRequestedEventPayload.java`
- Mirrored unit/controller tests under `services/auth/src/test/java/com/themistra/auth/account/`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountController.java`
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java`
- `services/auth/src/main/java/com/themistra/auth/account/AccountExceptionHandler.java`
- `services/auth/src/main/java/com/themistra/auth/events/EventTopics.java`
- `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java`
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java`

## Files NOT to Modify

- `account/VerificationToken.java`, `VerificationTokenRepository.java`,
  `VerificationTokenService.java`, `VerificationTokenProperties.java` (T05).
- `account/PasswordPolicy.java`, `PasswordPolicyProperties.java` (T03).
- `AdminAccountController.java`.
- Any file under `spec/` or `contracts/`.
- Any Flyway migration file.

## Acceptance Criteria

- **R3** — registration still returns its existing uniform `202`; an `auth.email.requested`/
  `verify_email` outbox row (aggregate ID = account UUID) exists in the same transaction.
- **R4** — a valid token for a `PENDING_VERIFICATION` account activates it and emits
  `auth.user.registered`.
- **R5** — every rejection reason (not found, expired, used, `DELETED`/`SUSPENDED` account, or
  wrong status such as already-`ACTIVE`) produces the identical `400`/`INVALID_TOKEN` response.
- **R6 (as modified)** — `resend-verification`'s response body is identical across match/no-match;
  only a matching `PENDING_VERIFICATION` email issues a token and emits an event.
- **R44** — `EventTopics.forAggregateType("verification-token")` equals `"auth.email.requested"`.
- **L11** — both new paths appear in `PublicEndpoints.METHOD_SCOPED`.
- **Audit** — self-service activation records `account.activated` with `actorUuid` equal to the
  activated account's own UUID.
- **No raw-token leakage beyond the event payload** — no log statement, response body, or
  `toString()` anywhere in the new/modified code exposes the raw token except the event payload
  itself (the one formalized exception).

## Required Tests

- `shouldActivateAccountWithValidVerificationToken`.
- `shouldResendVerificationOnlyForPending accounts` *(verbatim spelling from the header)*.
- `shouldEmitVerifyEmailEventOnRegistration`.
- `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic`.
- `verify-email` with an expired/already-used/deleted-account/**already-ACTIVE-account** token —
  all produce the identical `400`/`INVALID_TOKEN` response (Finding 2 is explicitly the
  already-ACTIVE case).
- `verify-email` called twice with the same (now-consumed) token — second call hits the same
  uniform rejection.
- `resend-verification` for an `ACTIVE` account, a nonexistent email, and a matching
  `PENDING_VERIFICATION` email — first two produce no state change and the identical response
  body; only the third issues+emits.
- `register` still returns its unchanged ack (regression check on the existing T02 contract).
- Self-service activation's audit row has `actorUuid` equal to the activated account's UUID.
- `EmailRequestedEventPayload`'s `toString()` (or equivalent) does not expose the raw token.

## Constraints

- **Security:** raw token appears only in the `auth.email.requested` event payload (formalized
  exception, Finding 1) — never logged, never echoed in any response body, no default `toString`
  exposure.
- **Transaction:** `verify-email`'s consume → status-check → activate → emit → audit sequence is
  one transaction (Finding 3) — a failure partway through must not leave a consumed token with a
  non-activated account.
- **Module boundaries:** stays within `account`, `events`, and `common`.
- **Null handling:** both new request DTOs validate their required field (`@NotBlank`) at the bean-
  validation boundary — accepted as an intentionally distinguishable "malformed request" class,
  separate from R5/R6's uniform-response guarantees (Finding 8).
- **Enumeration safety:** response *body* is uniform for both endpoints; `resend-verification`'s
  timing/outbox-write side-channel is an accepted, documented trade-off (Finding 4), not engineered
  away.

## Open Questions

No blockers.
