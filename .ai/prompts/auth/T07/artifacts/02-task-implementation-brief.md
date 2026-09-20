# auth · T07 — Task Implementation Brief (TIB)

## Task

Password reset flow: add `POST /accounts/password-reset-request` and
`POST /accounts/password-reset`. Ensure uniform responses. On valid reset, update password and
revoke all refresh-token families for the account.

## Purpose

Let a user who knows their email but not their password recover access, using T05's
`VerificationTokenService` (`PASSWORD_RESET` purpose, unused until now) and reusing T06's
enumeration-safe endpoint patterns and rejection machinery. Also closes a real security gap: today
nothing revokes refresh-token sessions on password change.

## Scope

**In:**
- `AccountService` gains two new methods: request (issue + emit, gated on `ACTIVE`/`LOCKED`
  status) and confirm (consume + update password + revoke all families + audit).
- `POST /accounts/password-reset-request` — public, email-in-body. Always returns the same
  acknowledgement; issues a token and emits `auth.email.requested` (purpose `password_reset`) only
  for a matching `ACTIVE`/`LOCKED` account.
- `POST /accounts/password-reset` — public, token+new-password-in-body. Success returns `204`;
  every rejection reason reuses T06's existing `AccountService.VerificationTokenRejectedException`
  → `ProblemTypes.INVALID_TOKEN` → `400` mapping (no new exception type, no new
  `AccountExceptionHandler` mapping).
- `PublicEndpoints` gains both new paths.
- `token.RefreshTokenTracker` gains a new method revoking every unrevoked family for a given
  principal (= account UUID string) — the first capability of its kind in this codebase.
- A new acknowledgement message for the request-step response (see Dependencies) — `password-reset
  -request`'s response must not reuse `RegistrationAcknowledgement.standard()`'s literal wording
  ("...check your inbox to **verify your account**"), which is factually wrong for this flow.

**Out:**
- `PasswordPolicy` — explicitly deferred to task 9 (Phase 0 human decision), applies equally here
  as it did to T06's registration path.
- Change-own-password (task 8).
- Rate limiting on either endpoint (task 31).
- Any Notification Service code, actual email sending.
- Fixing the pre-existing, unrelated `token` package compile failure (`SecurityChainsConfig`,
  `ReuseDetectingAuthorizationService`) — this task's changes to `RefreshTokenTracker` must not
  depend on those broken files compiling.

## Business Rules

- **R12.** `password-reset-request` returns the identical acknowledgement regardless of match.
- **R13.** Only a matching `ACTIVE`/`LOCKED` account (not `PENDING_VERIFICATION`,
  `DELETED`, `SUSPENDED`) triggers token issuance + `auth.email.requested` emission.
- **R14.** A valid token + new password updates the password hash, revokes *every* refresh-token
  family for that account, and records `password.reset`. Password-policy compliance is out of this
  task's scope (task 9).
- **R15.** Every rejection reason produces the identical response (reusing T06's existing uniform
  mapping) — read as "uniform among rejection reasons," per Phase 1's resolution of R15's
  imprecise literal wording.

## Locked Decisions

- **L5.** Enumeration-safe responses.
- **L11** (widened). Both new public paths registered in `PublicEndpoints`.

## Dependencies

- `VerificationTokenService.issue(accountUuid, VerificationToken.Purpose.PASSWORD_RESET)` /
  `.consume(rawToken)` (T05, unchanged).
- `AccountRepository.findByEmail`, `.findByAccountUuid`.
- `Account.changePasswordHash(String)`, `Account.getStatus()`.
- `PasswordEncoder.encode(String)` (existing bean, already injected into `AccountService`).
- New `RefreshTokenTracker` method — revokes every family found by the existing, currently-unused
  `RefreshTokenFamilyRepository.findByPrincipalNameAndRevokedAtIsNull(principalName)`, calling the
  existing `RefreshTokenFamily.revoke(reason, now)` on each. Revocation reason string:
  `"PASSWORD_RESET"` (parallel to the existing `"REUSE_DETECTED"` precedent).
- `AccountService.VerificationTokenRejectedException`, `common.ProblemTypes.INVALID_TOKEN` (T06,
  reused as-is).
- `account/event/EmailRequestedEventPayload` (T06, reused as-is — `purpose = "password_reset"`).
- `events/EventTopics` (T06's existing mapping, no change).
- `account/dto/RegistrationAcknowledgement` — gains a new static factory for password-reset
  wording (see Files to Modify); the type itself isn't renamed despite now serving three flows.

## Inputs

- `password-reset-request` — email string (`PasswordResetRequest`).
- `password-reset` — raw token + new (plaintext) password (`PasswordResetConfirmRequest`).

## Outputs

- `password-reset-request` — a `RegistrationAcknowledgement` with password-reset-appropriate
  wording, always.
- `password-reset` success — `204 No Content` (mirrors `verify-email`).
- `password-reset` failure — the existing `400`/`INVALID_TOKEN` `ProblemDetail`, unchanged, no new
  mapping.

## State Changes

- `password-reset-request` match (`ACTIVE`/`LOCKED`): one new `verification_tokens` row (via T05's
  `issue`, itself invalidating any prior active `PASSWORD_RESET` token for that account) plus one
  new outbox row (`auth.email.requested`, purpose `password_reset`, aggregate ID = account UUID).
- `password-reset-request` no-match (wrong status or no account): no state change.
- `password-reset` success: one transaction covering — token consume (T05), password hash update,
  every unrevoked refresh-token family for that account marked revoked, and a `password.reset`
  audit record.
- `password-reset` failure (any reason): no state change — the token is never consumed for a
  rejected attempt (T05's existing guarantee), and nothing about the account/families is touched.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/account/dto/PasswordResetRequest.java`
- `services/auth/src/main/java/com/themistra/auth/account/dto/PasswordResetConfirmRequest.java`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountController.java`
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java`
- `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java`
- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenTracker.java`
- `services/auth/src/main/java/com/themistra/auth/account/dto/RegistrationAcknowledgement.java` —
  new static factory only, existing `standard()` untouched.

## Files NOT to Modify

- `account/PasswordPolicy.java`, `PasswordPolicyProperties.java` (task 9's scope).
- `account/AccountExceptionHandler.java` — no new mapping needed, T06's is reused as-is.
- `events/EventTopics.java`, `account/event/EmailRequestedEventPayload.java` — both already
  purpose-generic from T06, reused unchanged.
- `token/SecurityChainsConfig.java`, `token/ReuseDetectingAuthorizationService.java` — the
  pre-existing broken files; this task's `RefreshTokenTracker` change must not require touching
  either.
- `token/RefreshTokenFamily.java`, `RefreshTokenFamilyRepository.java` — both already have
  everything this task needs (`revoke(...)`, `findByPrincipalNameAndRevokedAtIsNull(...)`).
- Any file under `spec/` or `contracts/`.
- Any Flyway migration file.

## Acceptance Criteria

- **R12** — identical acknowledgement for a matching email, a non-matching email, and an email
  belonging to a `PENDING_VERIFICATION`/`DELETED`/`SUSPENDED` account.
- **R13** — token issuance + event emission only for `ACTIVE`/`LOCKED` accounts; explicitly *not*
  for `PENDING_VERIFICATION` (a deliberate contrast with T06's `resend-verification`, which is the
  opposite filter).
- **R14** — password hash updated; *every* unrevoked family for that account revoked; a
  *different* account's family untouched; `password.reset` audit recorded with `actorUuid` = the
  account's own UUID (mirroring T06 Finding 5's self-service pattern).
- **R15** — every rejection reason produces the identical `400`/`INVALID_TOKEN` response.
- **L11** — both new paths in `PublicEndpoints.METHOD_SCOPED`.
- **No secret leakage** — the new plaintext password never appears in a log statement, response
  body, or exception message.

## Required Tests

- `shouldEmitPasswordResetEventOnlyWhenEmailExists` (existence *and* correct status — nonexistent
  email and wrong-status-but-existing email both produce no event).
- `shouldResetPasswordAndRevokeAllFamiliesWithValidToken`.
- `password-reset-request` for a `LOCKED` account issues+emits (contrast with T06).
- `password-reset-request` for a `PENDING_VERIFICATION` account does not.
- `password-reset` with expired/used/deleted-account token — uniform rejection.
- Revoke-all isolation: a second account's family is untouched by the first account's reset.
- New password never logged/echoed.
- `RegistrationAcknowledgement`'s new factory produces password-reset-appropriate wording, not the
  registration/verification message.

## Constraints

- **Security:** new password never logged; hashed via the existing `PasswordEncoder` bean before
  `changePasswordHash`; raw password never persisted anywhere except as its hash.
- **Transaction:** `password-reset`'s consume → password-update → revoke-all-families → audit
  sequence is one transaction (same reasoning as T06 Finding 3 for `verify-email`).
- **Module boundaries:** this task introduces `account`'s first dependency on `token`
  (`RefreshTokenTracker`) — confirmed at Phase 0 as unblocked by any existing `ArchitectureTest`
  rule, but a genuinely new architectural edge, not a routine same-module change.
- **Constructor changes:** both `AccountService` (new `RefreshTokenTracker` dependency) and
  `RefreshTokenTracker` itself (new method, no constructor change) are touched — expect the same
  "existing test needs updating" situation T06 hit with `AccountServiceTest`; per established
  practice this gets caught at Phase 7 self-review and fixed at Phase 9, not silently ignored.
- **Enumeration safety:** `password-reset-request`'s status filter (`ACTIVE`/`LOCKED` only) must
  not leak via timing/response differences beyond what's already an accepted, documented trade-off
  from T06 Finding 4 (same class of gap, not re-litigated here).

## Open Questions

No blockers.
