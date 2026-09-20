# auth · T07 — Frozen Task Brief

**STATUS: FROZEN**
**Approved by:** femi (human approval gate, Phase 4)
**Date:** 2026-07-26
**Supersedes:** `artifacts/02-task-implementation-brief.md`, amended per `artifacts/03-design-challenge.md`.

Downstream phases (5 onward) implement against this document only.

---

## Phase 3 finding disposition

All 13 Kimi findings accepted, plus one finding discovered while designing Finding 1's fix (a
mirror-image bug already live in T06's shipped code). Three items involved real product/security
trade-offs, resolved explicitly by the human approver rather than defaulted.

| # | Finding | Disposition |
|---|---|---|
| 1 | `VerificationTokenService.consume` is purpose-blind — an `EMAIL_VERIFY` token could reset a password | **ACCEPTED — CRITICAL.** New method `VerificationTokenService.consumeForPurpose(rawToken, Purpose)` added, checking the token's stored purpose *before* any mutation (no state change on a purpose mismatch, preserving R15's "failure = no state change" guarantee). |
| — (discovered while fixing #1) | T06's `activateFromVerificationToken` has the *mirror-image* bug — a `PASSWORD_RESET` token could currently verify an email | **ACCEPTED — fixed now, human-confirmed.** `AccountService.activateFromVerificationToken`'s existing `consume(rawToken)` call is swapped for `consumeForPurpose(rawToken, Purpose.EMAIL_VERIFY)` — same file, same phase, closes a live vulnerability in already-merged code rather than leaving it open. |
| 2 | `changePasswordHash`'s `InvalidAccountStateException` can leak account state | **ACCEPTED — folded into a single pre-check with Findings 3/4** (see below), not a separate catch-and-convert layer. |
| 3 | `PENDING_VERIFICATION` isn't rejected before password reset | **ACCEPTED — same unified pre-check.** |
| 4 | Stale account read / race between consume and password update | **ACCEPTED.** Account is re-read fresh via `accountRepository.findByAccountUuid(...)` *after* `consumeForPurpose` succeeds, mirroring T06 Finding 2's exact pattern: status is checked *before* calling any guarded entity method, so `InvalidAccountStateException` is never reached by construction — not caught defensively after the fact. |
| 5 | `password-reset-request`'s HTTP status unspecified | **ACCEPTED — `200`, human-confirmed**, matching `resend-verification` (the architecturally closer sibling — both "email in, deferred side-effect, uniform ack"), not `202`/registration as Kimi's own rationale suggested. |
| 6 | DTO validation annotations unspecified | **ACCEPTED** — `@NotBlank @Email` on `PasswordResetRequest.email`; `@NotBlank` on `PasswordResetConfirmRequest.token` and `.newPassword`. |
| 7 | `PasswordResetConfirmRequest`'s auto-generated `toString()` could leak the new password | **ACCEPTED** — required override omitting `newPassword`, same pattern as `EmailRequestedEventPayload`/`VerificationTokenResult`. |
| 8 | `LOCKED` account + reset: unlock or leave locked? | **ACCEPTED — unlock, human-confirmed.** Proving email ownership is at least as strong an identity proof as a successful login (which already unlocks per R18); a locked-out legitimate user needs a working self-service recovery path, which is exactly what R13 including `LOCKED` as a valid reset target already implies. `Account.unlock()` is called when the pre-reset status is `LOCKED`, before `changePasswordHash`. |
| 9 | Live SAS authorization purge not addressed | **ACCEPTED — deferred, documented, not implemented.** Refresh-token families are revoked (R14); live access-token-granting SAS authorizations remain valid until their own TTL expires. This residual risk is explicitly out of scope (tasks 28/29's territory) and recorded here, not silently left unmentioned. |
| 10 | Email normalization unconfirmed | **ACCEPTED** — `normalize(email)` (existing private helper), same as registration/resend-verification. |
| 11 | `package.md` maps the named test to the wrong requirement (R8 instead of R12/R13) | **ACCEPTED — noted, spec not modified.** Confirmed a real spec-level typo; T07 proceeds against `requirements.md`'s R12/R13, not `package.md`'s stale label. |
| 12 | No ArchUnit verification step for the new `account → token` dependency | **ACCEPTED** — added as an explicit acceptance criterion. |
| 13 | Audit payload/reason-string details unconfirmed | **ACCEPTED** — `password.reset` recorded with `actorUuid = accountUuid` (self-service pattern, matching T06 Finding 5), `outcome = SUCCESS`; revocation reason string `"PASSWORD_RESET"`. |

No findings rejected.

---

## Task

Password reset flow: add `POST /accounts/password-reset-request` and
`POST /accounts/password-reset`. Ensure uniform responses. On valid reset, update password and
revoke all refresh-token families for the account.

## Purpose

Let a user recover access via email possession, reusing T05's `VerificationTokenService`
(`PASSWORD_RESET` purpose) and T06's enumeration-safe patterns — while closing two real,
purpose-confusion vulnerabilities (one new, one already-live) discovered during design review.

## Scope

**In:**
- `VerificationTokenService.consumeForPurpose(rawToken, Purpose)` — new method; purpose-checked
  before any mutation.
- `AccountService.activateFromVerificationToken` (T06, existing) — its `consume(rawToken)` call
  swapped for `consumeForPurpose(rawToken, Purpose.EMAIL_VERIFY)`. **The only change to
  already-shipped T06 code in this task.**
- `AccountService` gains two new methods:
  - Request: issues a token + emits `auth.email.requested` (purpose `password_reset`), gated on
    `ACTIVE`/`LOCKED` status only.
  - Confirm: `consumeForPurpose(..., PASSWORD_RESET)` → fresh account re-read → status check
    (`ACTIVE`/`LOCKED` only, else uniform rejection) → unlock if `LOCKED` → update password hash →
    revoke all refresh-token families → audit `password.reset`.
- `POST /accounts/password-reset-request` — public, email-in-body, always `200` with a
  password-reset-appropriate acknowledgement (not `RegistrationAcknowledgement.standard()`'s
  verification-specific wording).
- `POST /accounts/password-reset` — public, token+new-password-in-body. Success `204`; every
  rejection reason (not found, expired, used, wrong purpose, wrong account status) reuses T06's
  existing `VerificationTokenRejectedException` → `ProblemTypes.INVALID_TOKEN` → `400` mapping.
- `PublicEndpoints` gains both new paths.
- `token.RefreshTokenTracker` gains a method revoking every unrevoked family for a principal
  (reason `"PASSWORD_RESET"`), using the existing, previously-unused
  `RefreshTokenFamilyRepository.findByPrincipalNameAndRevokedAtIsNull`.
- `RegistrationAcknowledgement` gains a new static factory for password-reset wording.

**Out:**
- `PasswordPolicy` (task 9's scope, per Phase 0's human decision).
- Change-own-password (task 8), rate limiting (task 31).
- Live SAS authorization purge (Finding 9 — deferred to tasks 28/29, residual risk documented).
- Fixing the pre-existing, unrelated `token` package compile failure.

## Business Rules

- **R12.** `password-reset-request` returns the identical acknowledgement regardless of match.
- **R13.** Only a matching `ACTIVE`/`LOCKED` account triggers issuance + emission.
- **R14.** A valid, correctly-purposed token + new password updates the password hash, unlocks the
  account if it was `LOCKED` (Finding 8), revokes every refresh-token family, and records
  `password.reset`.
- **R15.** Every rejection reason — including wrong purpose (Finding 1) and wrong account status
  (Findings 2/3/4) — produces the identical `400`/`INVALID_TOKEN` response.

## Locked Decisions

- **L5.** Enumeration-safe responses.
- **L11** (widened). Both new public paths registered in `PublicEndpoints`.
- **This task's frozen implementation decisions** (Phase 3/4, not spec-level LOCKED IDs but not
  renegotiable by Phase 5+): purpose-aware token consumption (Finding 1 + its T06 counterpart),
  the unified pre-check-before-mutation pattern (Findings 2/3/4), `200` for
  `password-reset-request` (Finding 5), `toString()` guard on the confirm DTO (Finding 7), unlock
  on successful reset of a `LOCKED` account (Finding 8), and deferred/documented SAS-purge residual
  risk (Finding 9).

## Dependencies

- `VerificationTokenService.consumeForPurpose(rawToken, Purpose)` (new); `.issue(accountUuid,
  Purpose.PASSWORD_RESET)` (existing, T05).
- `AccountRepository.findByEmail`, `.findByAccountUuid`.
- `Account.changePasswordHash(String)`, `.getStatus()`, `.unlock()` (all pre-existing entity
  methods).
- `PasswordEncoder.encode(String)` (existing bean).
- New `RefreshTokenTracker` method (exact name is a Phase 5 decision) — reason string
  `"PASSWORD_RESET"`.
- `AccountService.VerificationTokenRejectedException`, `common.ProblemTypes.INVALID_TOKEN` (T06,
  reused, no new type).
- `account/event/EmailRequestedEventPayload` (T06, reused — `purpose = "password_reset"`).
- `RegistrationAcknowledgement` — new static factory, existing `standard()` untouched.

## Inputs

- `password-reset-request` — email (`PasswordResetRequest`, `@NotBlank @Email`).
- `password-reset` — raw token + new plaintext password (`PasswordResetConfirmRequest`,
  `@NotBlank` on both fields).

## Outputs

- `password-reset-request` — `200`, `RegistrationAcknowledgement` with password-reset wording,
  always.
- `password-reset` success — `204 No Content`.
- `password-reset` failure — existing `400`/`INVALID_TOKEN` `ProblemDetail`, unchanged.

## State Changes

- `password-reset-request` match (`ACTIVE`/`LOCKED`): one new `verification_tokens` row (purpose
  `PASSWORD_RESET`, invalidating any prior active one for that account+purpose per T05's existing
  `issue` behavior) + one outbox row (`auth.email.requested`, purpose `password_reset`, aggregate
  ID = account UUID).
- `password-reset-request` no-match: no state change.
- `password-reset` success: one transaction — token consumed (purpose-checked), account re-read,
  status validated, account unlocked if it was `LOCKED`, password hash updated, every unrevoked
  family for that account revoked, `password.reset` audit recorded (`actorUuid` = account's own
  UUID, `outcome = SUCCESS`).
- `password-reset` failure (any reason): no state change whatsoever — token not consumed, account
  not touched, no families revoked.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/account/dto/PasswordResetRequest.java`
- `services/auth/src/main/java/com/themistra/auth/account/dto/PasswordResetConfirmRequest.java`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountController.java`
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` (two new methods;
  **plus** the one-line `activateFromVerificationToken` fix for the T06 regression)
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java` (T05) —
  new `consumeForPurpose` method only; `consume`/`verify`/`issue` unchanged
- `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java`
- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenTracker.java`
- `services/auth/src/main/java/com/themistra/auth/account/dto/RegistrationAcknowledgement.java`

## Files NOT to Modify

- `account/PasswordPolicy.java`, `PasswordPolicyProperties.java`.
- `account/AccountExceptionHandler.java` — no new mapping needed.
- `events/EventTopics.java`, `account/event/EmailRequestedEventPayload.java`.
- `token/SecurityChainsConfig.java`, `token/ReuseDetectingAuthorizationService.java` (the
  pre-existing broken files).
- `token/RefreshTokenFamily.java`, `RefreshTokenFamilyRepository.java` — both already have
  everything needed.
- `account/VerificationToken.java`, `VerificationTokenRepository.java`,
  `VerificationTokenProperties.java` — `consumeForPurpose` uses only existing repository methods.
- Any file under `spec/` or `contracts/`.
- Any Flyway migration file.

## Acceptance Criteria

- **R12** — identical `200` acknowledgement for matching, non-matching, and wrong-status emails.
- **R13** — issuance + emission only for `ACTIVE`/`LOCKED`; explicitly not for
  `PENDING_VERIFICATION`.
- **R14** — password hash updated; account unlocked if it was `LOCKED`; every unrevoked family for
  that account revoked (a different account's family untouched); `password.reset` audited.
- **R15** — every rejection reason (not found, expired, used, wrong purpose, `DELETED`/`SUSPENDED`,
  `PENDING_VERIFICATION`) produces the identical `400`/`INVALID_TOKEN` response.
- **T06 regression closed** — an `EMAIL_VERIFY`-purpose token can no longer be consumed by
  `password-reset`, and a `PASSWORD_RESET`-purpose token can no longer be consumed by
  `verify-email`.
- **L11** — both new paths in `PublicEndpoints.METHOD_SCOPED`.
- **Module boundary** — `ArchitectureTest` (and any other module-boundary ArchUnit test) still
  passes after `AccountService` gains a dependency on `RefreshTokenTracker` (Finding 12).
- **No secret leakage** — the new plaintext password never appears in a log statement, response
  body, exception message, or `PasswordResetConfirmRequest.toString()`.

## Required Tests

- `shouldEmitPasswordResetEventOnlyWhenEmailExists` (existence *and* `ACTIVE`/`LOCKED` status).
- `shouldResetPasswordAndRevokeAllFamiliesWithValidToken`.
- `shouldRejectEmailVerifyTokenUsedForPasswordReset` (Finding 1).
- A T06-side regression test that a `PASSWORD_RESET` token is rejected by `verify-email`.
- `password-reset-request` for `LOCKED` → issues+emits; for `PENDING_VERIFICATION` → does not.
- `password-reset` with expired/used/wrong-purpose/deleted-account/`PENDING_VERIFICATION` token —
  uniform rejection in every case, no state change.
- `password-reset` on a `LOCKED` account — succeeds, account ends `ACTIVE`, not `LOCKED`.
- Revoke-all isolation: a second account's family untouched.
- New password never logged/echoed; `PasswordResetConfirmRequest.toString()` omits it.
- `RegistrationAcknowledgement`'s new factory produces password-reset wording, not the
  verification message.
- `ArchitectureTest` still passes with the new `account → token` dependency.

## Constraints

- **Security:** new password hashed before persistence, never logged; purpose-check happens before
  any token mutation (no state change on a purpose mismatch).
- **Transaction:** the full confirm sequence (consume → re-read → status-check → unlock-if-needed →
  password-update → revoke-all → audit) is one transaction.
- **Module boundaries:** `account`'s first dependency on `token` — `ArchitectureTest` must still
  pass (Finding 12).
- **Constructor changes:** `AccountService` gains a `RefreshTokenTracker` dependency — expect the
  same "existing test needs updating" situation as T06; caught at Phase 7, fixed at Phase 9 per
  established practice.
- **Enumeration safety:** `password-reset-request`'s `ACTIVE`/`LOCKED`-only filter has the same
  class of timing/observability trade-off as T06 Finding 4 (accepted, documented, not
  re-engineered here).

## Open Questions

No blockers.
