# auth · T09 — Phase 2: Task Implementation Brief

## Task

Wire the existing `PasswordPolicy.validate` into `AccountService.register` and
`AccountService.resetPassword`. Update `AccountServiceTest` / `AccountControllerTest` to prove it.

## Purpose

`PasswordPolicy` (length + HIBP breach screening, L2/R8-R10) already exists and is already the
sole enforcement point for `changePassword` (T08). Registration and password-reset currently
bypass it entirely — registration gets only partial coverage via a DTO `@Size` annotation (no
breach check at all), and password-reset gets none (not even length). This task closes that gap so
password-content policy is enforced uniformly everywhere a password is set.

## Scope

**In:**
- Add a `passwordPolicy.validate(...)` call inside `AccountService.register`.
- Add a `passwordPolicy.validate(...)` call inside `AccountService.resetPassword`.
- New tests in `AccountServiceTest` and `AccountControllerTest` proving both call sites enforce
  the policy and correctly propagate `PasswordPolicyViolationException`.

**Out:**
- `changePassword` — already wired (T08), out of scope, regression-guard only.
- `PasswordPolicy`, `PasswordPolicyProperties`, `BreachCheckClient` — policy engine itself is
  unchanged.
- `AccountExceptionHandler` — the `PasswordPolicyViolationException` → 400 mapping already exists
  and is caller-agnostic.
- `PasswordPolicyTest.java` — both of this task's named tests already exist there and already
  pass; no new work in that file.
- Any change to `RegisterAccountRequest`/`PasswordResetConfirmRequest` DTO-level bean validation
  (`@Size`, etc.) — not required by R8-R10, since `PasswordPolicy.validate` is the real
  enforcement point regardless of DTO annotations.
- Enumeration-safety (L5), lockout (L4), MFA (L6), API keys (L7) — untouched.

## Business Rules

- R8 — reject a password shorter than 12 or longer than 128 characters, on every path where a
  password is set or changed.
- R9 — reject a password whose HIBP range-API suffix count is > 0, on every path where a password
  is set or changed.
- R10 — if the HIBP range API is unreachable, allow the change and record
  `password.breach_check_failed`; entirely internal to `PasswordPolicy.validate`, no caller-side
  work needed to satisfy it.

## Locked Decisions

- L2 — NIST 800-63B policy content (12-128 chars, no composition rules, no forced rotation, HIBP
  k-anonymity screening, fail-open with audit). Unchanged; only newly wired into two more callers.

## Dependencies

- `PasswordPolicy.validate(String rawPassword, UUID accountUuid, UUID actorUuid)` — existing,
  unchanged signature, both UUID params `@NonNull`-guarded.
- `PasswordPolicy.PasswordPolicyViolationException` — existing, unchanged.
- `AccountExceptionHandler` — existing mapping, no change needed.

## Inputs

- `register`: `RegisterAccountRequest.password()` (plaintext, already present).
- `resetPassword`: `newPassword` parameter (plaintext, already present).
- Both: the account's own UUID, available at the point validation would run (post-persist for
  register; post-token-resolution for reset) — self-service callers pass the same UUID for both
  `accountUuid`/`actorUuid`, matching `changePassword`'s existing pattern.

## Outputs

No new outputs. `register` keeps returning `AccountResponse`; `resetPassword` keeps returning
`void`. A policy violation now surfaces as `PasswordPolicyViolationException` instead of the
password being accepted unchecked.

## State Changes

None beyond what already exists. No new persisted state, no new outbox event, no new audit event
type — `password.breach_check_failed` is already recorded internally by `PasswordPolicy.validate`
regardless of caller.

## Files to Create

None.

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — add
  `passwordPolicy.validate(...)` calls in `register` and `resetPassword`.
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` — new tests for
  both call sites; `passwordPolicy` mock already wired into the constructor (T08), no signature
  change.
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` — new
  propagation tests for both endpoints, mirroring T08's existing
  `changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate` pattern.

## Files NOT to Modify

- `PasswordPolicy.java`, `PasswordPolicyProperties.java`, `BreachCheckClient.java`
- `AccountExceptionHandler.java`, `ProblemTypes.java`
- `PasswordPolicyTest.java` (already covers both named tests)
- `AccountController.java` (no endpoint/shape changes)
- `RegisterAccountRequest.java`, `PasswordResetConfirmRequest.java`
- Anything under `spec/`

## Acceptance Criteria

| ID | Criterion |
|---|---|
| AC1 | `register` rejects a password outside 12-128 chars via `PasswordPolicy.validate` (R8) |
| AC2 | `register` rejects a password with HIBP count > 0 (R9) |
| AC3 | `register` allows registration and records `password.breach_check_failed` when HIBP is unreachable (R10, already guaranteed internally by `validate`) |
| AC4 | `resetPassword` rejects a new password outside 12-128 chars (R8) |
| AC5 | `resetPassword` rejects a new password with HIBP count > 0 (R9) |
| AC6 | `resetPassword` allows the reset and records `password.breach_check_failed` when HIBP is unreachable (R10) |
| AC7 | Both new call sites surface `PasswordPolicyViolationException` through the existing `AccountExceptionHandler` mapping — no new problem type |
| AC8 | `changePassword` behavior is unchanged (regression guard) |

## Required Tests

Already satisfied, no new work (confirmed present in `PasswordPolicyTest.java`):
`shouldRejectPasswordShorterThan12OrLongerThan128`, `shouldRejectBreachedPasswordUsingHibpRange`,
`shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure`.

New, in `AccountServiceTest`:
- `register` calls `passwordPolicy.validate` with the submitted password; a violation prevents
  `accountRepository.save`/`saveAndFlush` and the verification-email outbox emission.
- `resetPassword` calls `passwordPolicy.validate` with the new password; a violation prevents the
  hash update, `refreshTokenTracker.revokeAllForPrincipal`, and the `password.reset` audit record.

New, in `AccountControllerTest`:
- `register` propagates `PasswordPolicyViolationException` uncaught for the handler to translate.
- `passwordReset` propagates `PasswordPolicyViolationException` uncaught for the handler to
  translate.

Regression: existing `changePassword` tests (T08) continue passing unmodified.

## Constraints

- **Transaction:** both `register` and `resetPassword` are already `@Transactional`; a policy
  violation must throw before any persistence/outbox side effect, matching `changePassword`'s
  existing all-or-nothing shape.
- **Null handling:** `PasswordPolicy.validate` already `Objects.requireNonNull`s both UUID params
  — both call sites must pass real, non-null UUIDs (post-persist account UUID for `register`,
  post-token-resolution account UUID for `resetPassword`), never `null`.
- **Module boundaries:** all changes stay inside `account`; no new cross-module dependency
  (`PasswordPolicy` is already an `account`-package collaborator).
- **Check ordering (unspecified by R8-R10, not decided here):** neither the existing
  duplicate-email check in `register` nor the existing token/eligibility check in `resetPassword`
  has a requirement-mandated order relative to the new policy check. `changePassword`'s existing
  precedent (T08) runs cheaper/local checks — status, current-password match — before the
  network-calling policy check, to avoid a wasted HIBP call on a request that was always going to
  be rejected. Phase 5 (Implementation Plan) must pick an ordering consistent with that precedent
  for both new call sites; Phase 3 (Design Challenge) should confirm it's defensible.
- **Security:** no enumeration-safety impact — `register`'s duplicate-email uniform-202 response
  (L5) is unaffected; a policy violation is a distinct, non-existence-revealing rejection the
  caller already has enough information to have avoided (they submitted the bad password).

## Open Questions

No blockers.
