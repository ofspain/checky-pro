# auth · T08 — Phase 6: Implementation Notes

## What changed

**Created:**
- `account/dto/ChangePasswordRequest.java` — `@NotBlank currentPassword`, `@NotBlank newPassword`;
  `toString()` overridden to omit both (unlike `PasswordResetConfirmRequest`, both fields here are
  standing account credentials, not a one-time token).

**Modified:**
- `common/ProblemTypes.java` — new `CURRENT_PASSWORD_MISMATCH` constant.
- `account/Account.java` — `changePasswordHash`'s guard widened from `status == DELETED` to
  `status != ACTIVE` (Finding 4/decision 2). Confirmed backward-compatible with T07's
  `resetPassword` at Phase 4/5; re-confirmed here by inspection, no behavior change to that path.
- `account/PasswordPolicy.java` — `validate(String)` widened to
  `validate(String rawPassword, UUID accountUuid, UUID actorUuid)`; both new parameters threaded
  through `validateNotBreached` into `recordBreachCheckFailedAudit`'s `RecordAuditEventRequest`,
  replacing the previous `null, null` (Finding 7). Zero other call sites existed before this task,
  so this is a pure signature widening.
- `account/AccountService.java`:
  - New `PasswordPolicy` constructor dependency (inserted before `Clock`, matching the plan).
  - New `changePassword(UUID accountUuid, String currentPassword, String newPassword)` — status
    check → current-password check → policy check → mutation → audit, in that fixed order.
  - New nested `CurrentPasswordMismatchException`.
- `account/AccountExceptionHandler.java` — two new mappings:
  `CurrentPasswordMismatchException` → `400`/`CURRENT_PASSWORD_MISMATCH`;
  `PasswordPolicy.PasswordPolicyViolationException` → `400`/`VALIDATION_ERROR` (with `detail` =
  the policy violation message, matching `InvalidAccountStateException`'s existing precedent).
- `account/AccountController.java` — new `POST /accounts/me/password` endpoint, deriving the
  caller from `Authentication` exactly like `me`; `204` on success, no local catch (mirrors
  `verifyEmail`/`passwordReset`'s existing pattern).

No other files touched.

## Mapping to the plan

Matches `artifacts/05-implementation-plan.md` exactly — every signature, the execution order
(`ProblemTypes` → `Account` → `PasswordPolicy` → `ChangePasswordRequest` → `AccountService` →
`AccountExceptionHandler` → `AccountController`), and the private-method reuse (`getAccount`,
`recordAudit` unchanged; `PasswordPolicy`'s existing private methods modified in place, no new
ones added).

## Mapping to acceptance criteria (frozen brief §Acceptance Criteria)

- **AC1/AC4:** `changePassword` calls `passwordPolicy.validate` before `passwordEncoder.encode`;
  both the match-check and the new hash go through the injected `PasswordEncoder` bean.
- **AC2:** wrong current password throws `CurrentPasswordMismatchException` before
  `passwordPolicy.validate`, `encode`, or `changePasswordHash` are ever reached.
- **AC3:** a policy-violating new password throws `PasswordPolicyViolationException` after the
  current-password check has already passed, before any mutation.
- **AC5:** `UUID.fromString(authentication.getName())` — identical pattern to `me`.
- **AC6:** `recordAudit("password.changed", accountUuid, accountUuid)` is the last statement in
  the success path only; every rejection path returns before reaching it.
- **AC7:** the account-status check is the *first* thing `changePassword` does after resolving the
  account — strictly before `passwordEncoder.matches`, avoiding the `DELETED`-account
  `null`-`passwordHash` risk Kimi flagged.
- **AC8:** no `refreshTokenTracker` call anywhere in `changePassword` — confirmed by inspection,
  will be enforced by a `never()` test at Phase 10.
- **AC9:** no comparison between `currentPassword` and `newPassword` anywhere — a caller
  resubmitting their current password as the new one is treated identically to any other valid
  new password, exactly as decided.
- **AC10:** `validate`'s `accountUuid`/`actorUuid` parameters flow through to
  `recordBreachCheckFailedAudit`'s `RecordAuditEventRequest`, replacing the previous hardcoded
  `null, null`.

## Deviations from the plan

None. Implementation matches the plan's signatures, method bodies, and file list exactly.

## Build verification

Same situation as every prior task: `mvn -pl services/auth compile` still fails on the
pre-existing, unrelated `token` package issue (`SecurityChainsConfig`,
`ReuseDetectingAuthorizationService`, `AuthorizationServiceConfig` — unchanged, untouched by this
task). Verified the seven new/changed production files independently via targeted `javac` against
the module's resolved dependency classpath. **Compiled with zero errors**, including the widened
`Account.changePasswordHash` guard and `PasswordPolicy.validate` signature change.

**Not run in this phase** (Phase 10 scope, per guardrails): unit/controller tests for the new
method/endpoint. Per the frozen brief's explicit expectation, the `AccountService` constructor
change (new `PasswordPolicy` parameter) will break the existing `AccountServiceTest` the same way
every prior constructor addition in this chain has — not fixed here, expected to be caught at
Phase 7 self-review. `PasswordPolicyTest`'s existing `validate(...)` call sites are now
signature-broken too (new required parameters) — same expected, not-yet-fixed class of issue.
