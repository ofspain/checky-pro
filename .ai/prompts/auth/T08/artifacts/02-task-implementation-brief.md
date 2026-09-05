# auth · T08 — Phase 2: Task Implementation Brief

## Task

T08 — Change own password. Add `POST /accounts/me/password`, protected by current password.

## Purpose

Let an authenticated account holder rotate their own password by proving possession of the
current one. Closes the last self-service credential-management gap in this spec's account
module extension — registration, email-verify, resend-verify, and password-reset request/confirm
already ship; this covers the "already logged in, want to change my password anyway" case.

## Scope

**In:**
- New endpoint `POST /accounts/me/password`, authenticated (not public — no `PublicEndpoints`
  change).
- `AccountService` method: verify current password via `PasswordEncoder.matches`, then call
  `Account.changePasswordHash` with the newly-encoded password.
- New `ChangePasswordRequest` DTO.
- A distinct rejection for a wrong current password, mapped by `AccountExceptionHandler` to a
  `4xx` problem-detail response — not enumeration-sensitive (L5 doesn't scope this endpoint,
  confirmed at Phase 0/1).
- A `password.changed` audit event (by convention, matching `password.reset`/`account.activated`'s
  existing naming and self-service actor pattern: `actorUuid` = the account's own UUID).

**Out:**
- `PasswordPolicy` enforcement on the new password — see **Open Questions (blocker)**.
- Refresh-token family revocation — R11's text does not name this (unlike R14); out of scope per
  the literal requirement and the "no unrelated scope" guardrail.
- Contract file authoring (`tasks.md` items 33/34) and rate limiting (item 31) — unrelated tasks.

## Business Rules

- **R11.** WHEN an authenticated caller submits their current password and a new password to
  `POST /accounts/me/password`, THEN the system SHALL verify the current password and, if it
  matches, update the password hash.

## Locked Decisions

- **L2.** Governs the *content* of "policy" only if/when enforcement is wired in (task 9); this
  task's own code path does not evaluate it (see Open Questions).
- **L3.** The new password is encoded, and the current password is verified, through the same
  existing delegating `PasswordEncoder` (`{bcrypt}`, strength 12).

## Dependencies

- `PasswordEncoder` (`encode`, `matches`)
- `AccountRepository.findByAccountUuid(UUID)`
- `Account.changePasswordHash(String)`
- `AuditService.record(RecordAuditEventRequest)`
- `Clock` (existing `AccountService` field)
- `Authentication` (Spring Security) for caller identity

## Inputs

- `Authentication` — JWT `sub` → account UUID (never a path/body-supplied identifier).
- `ChangePasswordRequest { currentPassword: String, newPassword: String }`, both `@NotBlank`.

## Outputs

- `204 No Content` on success (no body) — matches `verify-email`/`password-reset`'s shape for a
  state-changing self-service action with nothing to return.
- A `4xx` problem-detail body on a wrong current password.

## State Changes

- `Account.passwordHash` updated when the current password matches.
- One `password.changed` audit row on success. None on rejection.
- No outbox event — R11 names none, unlike R3/R13/R14, none of which apply here since this isn't
  tied to an email/notification flow.

## Files to Create

- `account/dto/ChangePasswordRequest.java`

## Files to Modify

- `account/AccountController.java` — new endpoint
- `account/AccountService.java` — new method
- `account/AccountExceptionHandler.java` — new exception mapping

## Files NOT to Modify

- `account/Account.java` — reuse `changePasswordHash` as-is
- `common/PublicEndpoints.java` — endpoint is authenticated, not public
- `account/PasswordPolicy.java` / `PasswordPolicyProperties.java` — task 9's job (pending Open
  Questions)
- `events/EventTopics.java` — no new event
- `token/RefreshTokenTracker.java` — no revocation, per Scope/Out

## Acceptance Criteria

- **AC1 (R11).** Correct current password + a new password → hash updated;
  `passwordEncoder.encode` called with the raw new password.
- **AC2 (R11).** Wrong current password → rejected; `changePasswordHash` never called; hash
  provably unchanged.
- **AC3 (L3).** Both the match-check and the new hash go through the same `PasswordEncoder` bean.
- **AC4.** Caller identity comes only from `Authentication.getName()`, never a request field.
- **AC5.** Exactly one `password.changed` audit row on success; none on rejection.

## Required Tests

- `AccountServiceTest`: success path (`encoder.matches` called with the raw current password and
  the stored hash; `encoder.encode` called with the raw new password; `changePasswordHash`
  invoked; audit recorded with `actorUuid` = `accountUuid` = the caller's own UUID); wrong-current-
  password rejection (`changePasswordHash` never called, no audit); defensive missing-account case
  (mirrors the `AccountNotFoundException`-non-leak precedent from T06/T07, even though this
  endpoint is authenticated and the case should be normally unreachable).
- `AccountControllerTest`: success → `204`, delegates to the right `AccountService` method with the
  right arguments; wrong-current-password propagates uncaught for the exception handler to
  translate (mirrors `verifyEmail`/`passwordReset`'s existing pattern — no local catch).
- `AccountExceptionHandlerTest`: the new mapping's status/problem-detail shape.
- **Named test** `shouldRejectPasswordShorterThan12OrLongerThan128`: **not included in this
  brief's Required Tests**, pending Open Questions (blocker) below.

## Constraints

- **Transactional:** single `@Transactional` `AccountService` method, matching every existing
  mutation in this module.
- **Module boundaries:** no new cross-module dependency — everything here stays within
  `account`/`common`, unlike T07's `account → token` addition.
- **Security:** current-password verification must complete, and match, strictly before any
  mutation; a failed match must never call `encode(newPassword)` or `changePasswordHash`.
- **Null handling:** `@NotBlank` on both DTO fields; bean validation rejects blanks before the
  service layer is reached.

## Open Questions

**Blocker — does T08 wire `PasswordPolicy` into change-password, or is that task 9's job?**
`tasks.md` task 9 explicitly separates "apply `PasswordPolicy` to registration, change-password,
and password-reset" as later, dedicated work — the same split T07 got an explicit human decision
to honor for password-reset. This brief scopes `PasswordPolicy` **out**, following that precedent,
but this is a working assumption, not a resolved decision: T08's own named test
(`shouldRejectPasswordShorterThan12OrLongerThan128`) is mapped in `package.md` §8 to **R11**
specifically, the only named test anywhere in the spec tying a policy-length assertion to this
requirement. If the answer flips, it changes State Changes, Files to Modify (`PasswordPolicy`
becomes a new `AccountService` dependency), Required Tests, and Acceptance Criteria. Needs
confirmation before Phase 3 design review treats this brief as settled.
