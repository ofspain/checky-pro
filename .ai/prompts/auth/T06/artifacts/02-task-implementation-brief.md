# auth · T06 — Task Implementation Brief (TIB)

## Task

Self-service verification endpoints: extend `AccountController` with
`POST /accounts/verify-email` and `POST /accounts/resend-verification`. Emit
`auth.email.requested` via `OutboxPublisher`. Update `EventTopics` with the `verification-token`
aggregate mapping.

## Purpose

Replace the admin-only stand-in for email verification (`AdminAccountController`'s activate
endpoint) with the real self-service flow: registration triggers a verification email request,
the user redeems the token to activate their own account, and can request a new token if the
original expired or was lost — all using T05's `VerificationTokenService`, now wired for the
first time.

## Scope

**In:**
- `AccountService.register(...)` gains an `auth.email.requested` (purpose `verify_email`) emission
  in the same transaction as account creation (R3).
- `POST /accounts/verify-email` — public, token-in-body. Consumes the token via
  `VerificationTokenService.consume`; on success, activates the account
  (`Account.activateEmail()`) and emits `auth.user.registered`; on any failure reason, a single
  uniform response (R4, R5).
- `POST /accounts/resend-verification` — public, email-in-body (per the Phase 0 human decision,
  not authenticated). Always returns the same acknowledgement; issues a new token and emits
  `auth.email.requested` only when the email resolves to a `PENDING_VERIFICATION` account (R6, as
  modified).
- `EventTopics` gains `"verification-token" -> "auth.email.requested"` (R44).
- `PublicEndpoints` gains both new paths (L11).
- A new `common.ProblemTypes` entry for R5's uniform token-rejection response — none of the five
  existing types fit (`NOT_FOUND`/`CONFLICT`/`INVALID_STATE` are each distinguishing by design).
- `EmailRequestedEventPayload` carries the **raw verification token** — Notification Service
  cannot build a working link without it, and there is no other channel to deliver it. This is a
  deliberate, one-time exception to "credential appears exactly once in the creation response":
  here the event *is* the creation response's async equivalent, consumed by exactly one legitimate
  downstream service. Flagged explicitly, not applied silently.

**Out:**
- Password reset (task 7), change-own-password (task 8), registration's password-policy
  enforcement (task 9) — none of `PasswordPolicy`, `PasswordResetRequest`, `ChangePasswordRequest`
  are touched.
- Any Notification Service code, any actual email sending.
- Formal authoring of `contracts/events/auth/email-requested.v1.schema.json` (task 33) — this task
  emits the event the future contract will describe, but does not author that contract file.
- Resolving `package.md` §11 Q4 (email link base URL) — that's Notification Service's problem.

## Business Rules

- **R3.** `register` emits `auth.email.requested` (purpose `verify_email`) in the same transaction
  as account creation.
- **R4.** A valid, unexpired, unused token to `verify-email` activates the account and emits
  `auth.user.registered`.
- **R5** (widened at Phase 1). Any invalid/expired/used/unusable-account token produces the
  identical response.
- **R6** (modified at Phase 0, human-confirmed). `resend-verification` is public and
  email-identified; response is uniform regardless of match; a token is issued and an event
  emitted only for a matching `PENDING_VERIFICATION` account.
- **R44.** `EventTopics.forAggregateType("verification-token")` returns `"auth.email.requested"`.

## Locked Decisions

- **L5.** Enumeration-safe responses — governs both new endpoints' failure/uniform-response shapes.
- **L11** (widened). Both new public paths must be registered in `PublicEndpoints`.

## Dependencies

- `VerificationTokenService.issue(accountUuid, Purpose.EMAIL_VERIFY)` /
  `.consume(rawToken)` (T05, unchanged).
- `AccountRepository.findByEmail(String)` (existing).
- `Account.activateEmail()` (existing, guarded `PENDING_VERIFICATION → ACTIVE`).
- `OutboxPublisher.publish(...)`, `EventTopics` (one new map entry).
- `common.ProblemTypes` — one new stable URI for R5's uniform rejection.
- `Clock` (existing bean) — if the event payload needs a timestamp field.

## Inputs

- `register` — unchanged (existing `RegisterAccountRequest`).
- `verify-email` — raw token string (`VerifyEmailRequest`, new DTO).
- `resend-verification` — email string (`ResendVerificationRequest`, new DTO).

## Outputs

- `verify-email` success — some success response (exact shape/status is a Phase 5 decision; R4
  says "return success," not a specific body).
- `verify-email` failure — the single new uniform `ProblemDetail` (R5), identical for every
  rejection reason.
- `resend-verification` — a single uniform acknowledgement body regardless of outcome (may reuse
  `RegistrationAcknowledgement`'s shape — a bare `message` field — or introduce an equivalently
  generic type; Phase 5 decision, not fixed here).
- `EmailRequestedEventPayload` — at minimum: account identifier, purpose, and the raw token (see
  Scope). Exact field set/naming is a Phase 5 decision.

## State Changes

- `register`: unchanged account-creation behavior, plus one new outbox row
  (`auth.email.requested`, purpose `verify_email`) in the same transaction.
- `verify-email` success: `Account.activateEmail()` (`PENDING_VERIFICATION → ACTIVE`) plus one new
  outbox row (`auth.user.registered`), both in the transaction that also consumed the token
  (T05's `consume` is itself transactional — this task's transaction must encompass both the
  consume and the activation so they succeed or fail together).
- `verify-email` failure: no state change (T05's `consume` already guarantees this on any
  rejection path).
- `resend-verification` match: one new `verification_tokens` row (via T05's `issue`, which itself
  invalidates any prior active token) plus one new outbox row (`auth.email.requested`).
- `resend-verification` no-match: no state change.

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
  `VerificationTokenService.java`, `VerificationTokenProperties.java` (T05, called not changed).
- `account/PasswordPolicy.java`, `PasswordPolicyProperties.java` (T03, unrelated to this task).
- `AdminAccountController.java` (its admin-activate stand-in is untouched — deprecating it is not
  this task's job).
- Any file under `spec/` or `contracts/`.
- Any Flyway migration file.

## Acceptance Criteria

- **R3** — registering an account still returns the existing uniform `202` ack; an
  `auth.email.requested`/`verify_email` outbox row exists for the new account in the same
  transaction.
- **R4** — a valid token activates the account and emits `auth.user.registered`.
- **R5** — every invalid-token reason (not found, expired, used, account deleted/suspended)
  produces the identical response from `verify-email`.
- **R6 (as modified)** — `resend-verification`'s response is identical for a matching
  `PENDING_VERIFICATION` email, a non-matching-state email, and a nonexistent email; only the
  first actually issues a token and emits an event.
- **R44** — `EventTopics.forAggregateType("verification-token")` equals `"auth.email.requested"`.
- **L11** — both new paths appear in `PublicEndpoints.METHOD_SCOPED`.
- **No raw-token leakage beyond the event payload** — `verify-email`'s uniform failure response
  never echoes the submitted token back; no log statement in either new endpoint or in
  `AccountService`'s new methods logs a raw token.

## Required Tests

- `shouldActivateAccountWithValidVerificationToken` (full HTTP + activation realization).
- `shouldResendVerificationOnlyForPending accounts` *(verbatim spelling from the header)*.
- `shouldEmitVerifyEmailEventOnRegistration`.
- `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic`.
- `verify-email` with an expired/already-used/deleted-account token — same response as
  not-found.
- `verify-email` called twice with the same (now-consumed) token — second call hits the same
  uniform rejection.
- `resend-verification` for an `ACTIVE` account, a nonexistent email, and a matching
  `PENDING_VERIFICATION` email — first two produce no state change; only the third issues+emits.
- `register` still returns its unchanged ack even though it now also emits an event (regression
  check on the existing T02 contract).

## Constraints

- **Security:** raw token appears in the `auth.email.requested` event payload (the one deliberate
  exception, see Scope) and nowhere else — not logged, not echoed in any response body.
- **Transaction:** `verify-email`'s consume-then-activate-then-emit sequence must be one
  transaction; a failure partway through must not leave a consumed token with a non-activated
  account.
- **Module boundaries:** stays within `account`, `events`, and `common` (`ProblemTypes`,
  `PublicEndpoints`) — no new dependency on `authn`, `audit`, or `token`.
- **Null handling:** both new request DTOs validate their required field (`@NotBlank` token/email)
  at the bean-validation boundary, consistent with `RegisterAccountRequest`'s existing pattern —
  not ad hoc null checks in the controller.
- **Enumeration safety:** neither new endpoint's response timing or body may differ in a way that
  reveals account/token existence — mirrors `register`'s existing local-catch pattern.

## Open Questions

No blockers. (Q4 from `package.md` §11 is cited in Phase 1 as relevant context but does not block
this task — it constrains Notification Service, a downstream consumer this task does not build.)
