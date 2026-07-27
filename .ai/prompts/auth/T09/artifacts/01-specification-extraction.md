# auth · T09 — Phase 1: Specification Extraction

## Business Rules

- **R8.** A password that is shorter than 12 characters or longer than 128 characters must be
  rejected, whenever a password is set or changed — this applies to registration and
  password-reset, not only `changePassword` (already covered, T08).
- **R9.** A password must be checked against the HIBP k-anonymity range API (first 5 chars of its
  uppercase SHA-1 hash); if the trailing suffix appears with a count > 0, reject it — again "set or
  changed," so registration and password-reset are in scope, not only `changePassword`.
- **R10.** If the breach-check range API is unreachable, the password change is allowed (fail
  open) and a `password.breach_check_failed` audit event is recorded — this behavior is entirely
  inside `PasswordPolicy.validate` already; nothing caller-specific is needed to satisfy it beyond
  calling `validate` at all.

## Locked Decisions

- **L2.** NIST 800-63B password policy: min 12 / max 128 chars, no composition rules, no forced
  rotation; HIBP k-anonymity breach screening; fail-open with audit if the range API is down. This
  is exactly what `PasswordPolicy.validate` already implements (T03) and what `changePassword`
  already relies on (T08) — T09 extends the same, unchanged policy logic to two more call sites.
  No change to `PasswordPolicy` itself is implied by L2.

## Files involved

**Existing files to extend (production):**
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — `register(...)`
  and `resetPassword(...)` need a `passwordPolicy.validate(...)` call added. `changePassword(...)`
  needs no change (already wired, T08).

**Existing files to extend (tests) — named explicitly by the task statement:**
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` — the
  `passwordPolicy` field is already a `@Mock` wired into the service constructor (T08); no
  constructor change needed. Existing `register*`/`resetPassword`-family tests need new stubbing
  (`passwordPolicy` is a mock — a void method call with no stub configured is a silent no-op under
  Mockito's default answer, so existing tests won't break, but they also won't prove the new call
  happens). New tests need adding: policy-violation propagation for both `register` and
  `resetPassword`, and (for `register`) proof the policy check runs before the account is persisted
  / duplicate-email check ordering — needs a design decision in Phase 2, not assumed here.
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` — needs new
  tests mirroring T08's existing pattern
  (`changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate`): register and
  password-reset each need a `...PropagatesPolicyViolationForTheExceptionHandlerToTranslate`-shaped
  test proving the controller doesn't swallow `PasswordPolicyViolationException`.

**Files NOT touched by this task (confirmed by Phase 0, re-confirmed here):**
- `PasswordPolicy.java`, `PasswordPolicyProperties.java`, `BreachCheckClient.java` — the policy
  engine itself is unchanged; T09 only adds callers.
- `AccountExceptionHandler.java` — the `PasswordPolicyViolationException` → 400 mapping already
  exists (T08) and is caller-agnostic; nothing new to map.
- `PasswordPolicyTest.java` — already contains both of this task's named tests
  (`shouldRejectPasswordShorterThan12OrLongerThan128`, `shouldRejectBreachedPasswordUsingHibpRange`,
  confirmed present at lines 52 and 84), because they test `PasswordPolicy` itself in isolation,
  not any particular caller. **No new test method is required in this file for T09** — it already
  passes today, before any T09 code changes.
- `AccountController.java` — no new endpoints, no new request/response shapes. Exceptions already
  propagate uncaught from `AccountService` for both `register` and `passwordReset`.
- `RegisterAccountRequest.java`, `PasswordResetConfirmRequest.java` — whether either DTO's bean
  validation should change is a Phase 2 design question (Phase 0 already flagged this), not decided
  here.

## Dependencies

- `PasswordPolicy.validate(String rawPassword, UUID accountUuid, UUID actorUuid)` — existing,
  unchanged signature, both UUIDs `@NonNull`-guarded.
- `PasswordPolicy.PasswordPolicyViolationException` — existing, unchanged, already mapped by
  `AccountExceptionHandler`.
- No new outbox events, no new audit event types beyond what `PasswordPolicy.validate` already
  records internally (`password.breach_check_failed`) — R10 is entirely internal to `validate`.
- No new config keys — `PasswordPolicyProperties` (`themistra.auth.password.*`) is already bound
  and already used by the existing `changePassword` call path; the same bean serves every caller.
- No contract changes — `contracts/api/auth.yaml` already documents `400` responses for `register`
  and `password-reset` (validation errors already occur there today via `@Valid` bean validation);
  this task adds a new *reason* a `400` can occur, not a new response shape.

## Acceptance Criteria

| # | Criterion | Requirement |
|---|---|---|
| AC1 | `POST /accounts` rejects a password shorter than 12 or longer than 128 characters via `PasswordPolicy.validate` (not only DTO `@Size`) | R8 |
| AC2 | `POST /accounts` rejects a password whose HIBP suffix count > 0 | R9 |
| AC3 | `POST /accounts` allows registration and records `password.breach_check_failed` if the HIBP range API is unreachable | R10 |
| AC4 | `POST /accounts/password-reset` rejects a new password shorter than 12 or longer than 128 characters | R8 |
| AC5 | `POST /accounts/password-reset` rejects a new password whose HIBP suffix count > 0 | R9 |
| AC6 | `POST /accounts/password-reset` allows the reset and records `password.breach_check_failed` if the HIBP range API is unreachable | R10 |
| AC7 | Both new call sites use the existing `PasswordPolicyViolationException` → `400`/`VALIDATION_ERROR` mapping — no new problem type | R8/R9 (error path) |
| AC8 | `changePassword` behavior is unchanged (regression guard only, not new work) | R8/R9/R10 (already satisfied, T08) |

## Tests required

**Already satisfied, no new work (confirmed present in `PasswordPolicyTest.java`):**
- `shouldRejectPasswordShorterThan12OrLongerThan128` (line 52) → R8
- `shouldRejectBreachedPasswordUsingHibpRange` (line 84) → R9
- `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` (line 94) → R10

**New tests needed** (the task statement's "Update `AccountControllerTest` / `AccountServiceTest`
accordingly" — caller-level proof, not policy-content re-testing):
- `AccountServiceTest`: `register` calls `passwordPolicy.validate` with the new password and a
  policy violation prevents account creation (no save, no outbox event) — exact ordering (before
  vs. after the duplicate-email check, before vs. after `passwordEncoder.encode`) is a Phase 2
  design decision.
- `AccountServiceTest`: `resetPassword` calls `passwordPolicy.validate` with the new password and a
  policy violation prevents the hash update, refresh-token revocation, and audit record (mirroring
  the boundary-test style T08 used for `changePassword`'s non-ACTIVE-status gate).
- `AccountControllerTest`: `register` and `passwordReset` each propagate
  `PasswordPolicyViolationException` for `AccountExceptionHandler` to translate (mirrors T08's
  `changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate`).
- Regression guard: existing `changePassword` tests continue to pass unmodified — confirms this
  task didn't touch that call path.

## Open Questions

- **Q1 (documentation drift, not a blocker).** `package.md` §8's own test→requirement table maps
  `shouldRejectPasswordShorterThan12OrLongerThan128` → `R11` and
  `shouldRejectBreachedPasswordUsingHibpRange` → `R12`. Neither matches current `requirements.md`
  content at those numbers (`R11` is change-own-password, `R12` is reset-request acknowledgement).
  By content, both tests clearly belong to current `R8`/`R9`, which is also what this task's
  generated header already cites as the scoped requirement IDs. Proceeding with `R8`/`R9`/`R10` as
  authoritative (matches both the header and `requirements.md`'s actual text); `package.md`'s table
  appears stale and out of sync with `requirements.md` — flagged, not fixed (never modify `spec/`).
- **Q2 (genuine, deferred to Phase 2 design).** Should `PasswordPolicy.validate` run before or
  after the duplicate-email check in `register`? Before: a policy-violating password on an
  already-registered email leaks nothing extra (both fail before any DB write) but costs a wasted
  HIBP network call on every duplicate-email attempt. After: matches `register`'s existing check
  order (duplicate check, then persist) and avoids the network call when the request was always
  going to be rejected anyway (the same reasoning T08 used to order `changePassword`'s
  current-password check before the policy check). No requirement mandates either order. This is a
  design decision, not extracted here.
- **Q3 (genuine, deferred to Phase 2 design).** Should `resetPassword`'s policy check run before or
  after the token-purpose/eligibility checks? Token validity is proof of ownership and should
  likely gate first (consistent with `changePassword`'s status-check-first pattern, and avoids
  spending a network call on a request whose token was never going to be honored) — but this is a
  design call, not something R8/R9/R10 dictate.
