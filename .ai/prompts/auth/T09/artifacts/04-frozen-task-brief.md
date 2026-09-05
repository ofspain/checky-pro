STATUS: FROZEN

# auth · T09 — Phase 4: Frozen Task Brief

Human-approved. Folds every Phase 3 (Kimi) finding into a final decision. Downstream phases may
not renegotiate anything below.

## Phase 3 findings — disposition

| # | Sev | Finding | Disposition |
|---|---|---|---|
| 1 | High | Register brief claimed UUID is "post-persist" while also requiring policy validation to prevent save — self-contradictory | **ACCEPTED, amended.** Reorder `AccountService.register`: construct the `Account` via the existing `Account.register(email, encodedHash)` factory first (it already assigns the UUID internally, at construction — confirmed `Account.java:57-65`), obtain `account.getAccountUuid()`, validate against that real UUID, then check `existsByEmail`, then persist. No change to `Account.java`. Human-approved over the alternative (changing `Account.register`'s signature to take an explicit UUID) — smaller blast radius, no churn across ~30 test call sites, for a 2-method task. |
| 2 | High | Register check ordering determines enumeration safety (L5); brief assumed "no impact" without locking the order | **ACCEPTED, amended.** Same fix as #1 resolves this: `passwordPolicy.validate` runs before `accountRepository.existsByEmail`, so a policy violation returns `400` identically regardless of whether the email is new or already registered — no differential signal. This ordering is now LOCKED for this task per L5, not a performance preference. |
| 3 | High | `resetPassword` validating after token consumption creates a token-validity oracle (bad password on a valid token vs. an invalid token return distinguishable responses) | **Human-approved as accepted residual risk**, not fixed. Policy validation still runs right after `consumeForPurpose`/eligibility, before any mutation (per #4). Rationale: this matches the method's own pre-existing accepted pattern — an ineligible-account status found after consumption already burns the token and returns a *different* uniform rejection today, unchanged by this task. The residual oracle requires already possessing a valid raw reset token; at that point an attacker gains nothing by probing with a bad password first — they could simply submit a compliant one and complete the takeover directly. Closing it fully would require special-casing `PasswordPolicyViolationException` into the same uniform response as an invalid token specifically for this endpoint, hiding the real rejection reason from legitimate users who mistyped their password — rejected as disproportionate to the risk. |
| 4 | Medium | `resetPassword` could mutate lockout state (`unlock()`) before password validation, causing wasted state changes rolled back on violation | **ACCEPTED, amended.** Lock the order: `consumeForPurpose` → eligibility check → `passwordPolicy.validate` → `unlock()` (if `LOCKED`) → `changePasswordHash` → `revokeAllForPrincipal` → audit. An `InOrder` test proves `passwordPolicy.validate` precedes `Account.unlock`, `passwordEncoder.encode`, `refreshTokenTracker.revokeAllForPrincipal`, and `auditService.record`. |
| 5 | Medium | `RegisterAccountRequest`'s `@Size(min=12,max=128)` intercepts length violations via bean validation before `AccountService.register` is reached, contradicting AC1's claim that rejection happens "via `PasswordPolicy.validate`" | **ACCEPTED, amended.** Remove `@Size(min=12,max=128)` from `RegisterAccountRequest.password()`. `RegisterAccountRequest.java` moves from Files NOT to Modify to **Files to Modify**. `@NotBlank` stays. |
| 6 | Medium | DTO validation inconsistent between `RegisterAccountRequest` (has `@Size`) and `PasswordResetConfirmRequest` (doesn't) | **ACCEPTED, resolved by #5.** Removing `@Size` from `RegisterAccountRequest` makes all three password-accepting DTOs consistent: `RegisterAccountRequest`, `PasswordResetConfirmRequest`, and T08's `ChangePasswordRequest` all carry `@NotBlank` only. `PasswordPolicy.validate` is the sole length/breach enforcement point everywhere, matching `RegisterAccountRequest`'s own pre-existing Javadoc ("breach screening is a separate policy check... not bean validation"). |
| 7 | Low | `package.md` §8 maps this task's named tests to `R11`/`R12`/`R13`, which in current `requirements.md` refer to unrelated requirements; content clearly matches current `R8`/`R9`/`R10` | **Confirmed, not fixed** (never modify `spec/`). Same drift already logged as Phase 1 Q1. Non-blocking — this task proceeds against `requirements.md`'s actual `R8`/`R9`/`R10` text, which also matches the generated header's scoped IDs. A documentation fix to `package.md` is a separate, out-of-scope follow-up for the spec author. |

All Phase 1 Open Questions are resolved above (Q1 = Finding 7, confirmed non-blocking; Q2 = Finding
1/2; Q3 = Finding 3). No open questions remain.

---

## Task

Wire the existing `PasswordPolicy.validate` into `AccountService.register` and
`AccountService.resetPassword`, in the specific orders locked above. Update `AccountServiceTest` /
`AccountControllerTest` to prove it.

## Purpose

Close the gap where registration and password-reset bypass `PasswordPolicy` — registration today
enforces length only via a redundant DTO annotation (no breach check at all), and password-reset
enforces nothing. `PasswordPolicy` becomes the single, uniform enforcement point everywhere a
password is set, exactly as it already is for `changePassword` (T08).

## Scope

**In:**
- `AccountService.register`: reorder to construct the `Account` (obtaining its real UUID) before
  the duplicate-email check; call `passwordPolicy.validate(request.password(), accountUuid,
  accountUuid)` immediately after construction, before `existsByEmail`.
- `AccountService.resetPassword`: call `passwordPolicy.validate(newPassword, accountUuid,
  accountUuid)` after `consumeForPurpose`/eligibility, before `unlock()`/`changePasswordHash`.
- Remove `@Size(min=12,max=128)` from `RegisterAccountRequest.password()`.
- New tests in `AccountServiceTest` and `AccountControllerTest` proving both call sites enforce
  the policy, propagate `PasswordPolicyViolationException` correctly, and preserve the locked
  ordering (`InOrder`).

**Out:**
- `changePassword` — already wired (T08), regression-guard only.
- `PasswordPolicy`, `PasswordPolicyProperties`, `BreachCheckClient`, `AccountExceptionHandler`,
  `ProblemTypes` — unchanged.
- `PasswordPolicyTest.java` — both named tests already exist and pass; no new work.
- `PasswordResetConfirmRequest.java`, `ChangePasswordRequest.java` — already `@NotBlank`-only,
  already consistent with the resolved DTO strategy; no change needed.
- Closing the reset-token-validity oracle (Finding 3) — explicitly accepted as residual risk, not
  in scope for this task.
- Enumeration-safety for any endpoint other than `register` (L5's other listed endpoints are
  unaffected).
- Lockout (L4), MFA (L6), API keys (L7) — untouched.

## Business Rules

- R8 — reject a password shorter than 12 or longer than 128 characters, on every path where a
  password is set or changed.
- R9 — reject a password whose HIBP range-API suffix count is > 0, on every path where a password
  is set or changed.
- R10 — if the HIBP range API is unreachable, allow the change and record
  `password.breach_check_failed`; entirely internal to `PasswordPolicy.validate`.

## Locked Decisions

- L2 — NIST 800-63B policy content (12-128 chars, no composition rules, no forced rotation, HIBP
  k-anonymity screening, fail-open with audit). Unchanged; only newly wired into two more callers.
- L5 — enumeration-safe registration. Now explicitly locked at the ordering level: `register`'s
  policy check must run before its duplicate-email branch (Finding 2's resolution).

## Dependencies

- `PasswordPolicy.validate(String rawPassword, UUID accountUuid, UUID actorUuid)` — existing,
  unchanged signature.
- `PasswordPolicy.PasswordPolicyViolationException` — existing, unchanged, already mapped by
  `AccountExceptionHandler` to `400`/`VALIDATION_ERROR`.
- `Account.register(String email, String passwordHash)` — existing, unchanged signature (per
  Finding 1's resolution — no entity change).

## Inputs

- `register`: `RegisterAccountRequest.password()` (plaintext). Account UUID is obtained from the
  constructed-but-not-yet-persisted `Account` returned by `Account.register(...)`.
- `resetPassword`: `newPassword` parameter (plaintext). Account UUID is obtained from
  `consumeForPurpose`'s already-resolved result, as today.

## Outputs

No new outputs. `register` keeps returning `AccountResponse`; `resetPassword` keeps returning
`void`. A policy violation now surfaces as `PasswordPolicyViolationException` instead of the
password being accepted unchecked (register) or only `@NotBlank`-checked (reset).

## State Changes

None beyond what already exists. No new persisted state, no new outbox event, no new audit event
type — `password.breach_check_failed` is already recorded internally by `PasswordPolicy.validate`.

## Files to Create

None.

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — reorder
  `register`; add `passwordPolicy.validate(...)` call, ordered per Finding 1/2's resolution.
  Add `passwordPolicy.validate(...)` call to `resetPassword`, ordered per Finding 4's resolution.
- `services/auth/src/main/java/com/themistra/auth/account/dto/RegisterAccountRequest.java` —
  remove `@Size(min=12,max=128)` from `password()` (Finding 5/6's resolution). Keep `@NotBlank`.
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` — new tests for
  both call sites, `InOrder` proofs; `passwordPolicy` mock already wired into the constructor
  (T08), no signature change. Existing register/reset tests using a real password string may need
  the stub configured (Mockito void-method default is a no-op, but explicit stubbing keeps intent
  clear) or a lenient default if the class doesn't already use one.
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` — new
  propagation tests for both endpoints, mirroring T08's existing
  `changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate` pattern.
- `services/auth/src/test/java/com/themistra/auth/account/dto/RegisterAccountRequestValidationTest.java`
  (if it exists) — remove/update any test asserting `@Size`-driven rejection at the bean-validation
  layer, since that layer no longer enforces length.

## Files NOT to Modify

- `PasswordPolicy.java`, `PasswordPolicyProperties.java`, `BreachCheckClient.java`
- `AccountExceptionHandler.java`, `ProblemTypes.java`
- `PasswordPolicyTest.java`
- `AccountController.java` (no endpoint/shape changes)
- `Account.java` (Finding 1 resolved without an entity change)
- `PasswordResetConfirmRequest.java`, `ChangePasswordRequest.java`
- Anything under `spec/`

## Acceptance Criteria

| ID | Criterion |
|---|---|
| AC1 | `register` rejects a password outside 12-128 chars via `PasswordPolicy.validate` — the sole enforcement point now that `@Size` is removed (R8) |
| AC2 | `register` rejects a password with HIBP count > 0 (R9) |
| AC3 | `register` allows registration and records `password.breach_check_failed` when HIBP is unreachable (R10) |
| AC4 | `register`'s policy check runs before the duplicate-email check — a policy violation returns identically whether the email is new or already registered (L5, Finding 2) |
| AC5 | `resetPassword` rejects a new password outside 12-128 chars (R8) |
| AC6 | `resetPassword` rejects a new password with HIBP count > 0 (R9) |
| AC7 | `resetPassword` allows the reset and records `password.breach_check_failed` when HIBP is unreachable (R10) |
| AC8 | `resetPassword`'s policy check runs before `unlock()`, `changePasswordHash`, `revokeAllForPrincipal`, and the audit record (Finding 4) |
| AC9 | Both new call sites surface `PasswordPolicyViolationException` through the existing `AccountExceptionHandler` mapping — no new problem type |
| AC10 | `changePassword` behavior is unchanged (regression guard) |

## Required Tests

Already satisfied, no new work (confirmed present in `PasswordPolicyTest.java`):
`shouldRejectPasswordShorterThan12OrLongerThan128`, `shouldRejectBreachedPasswordUsingHibpRange`,
`shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure`.

New, in `AccountServiceTest`:
- `register` calls `passwordPolicy.validate` with the real (pre-persist) account UUID and the
  submitted password; a violation prevents `accountRepository.existsByEmail`,
  `accountRepository.save`/`saveAndFlush`, and the verification-email outbox emission.
- `register`'s policy check fires identically for a duplicate email (proves AC4 — no
  `existsByEmail` short-circuit before the policy check).
- `resetPassword` calls `passwordPolicy.validate` with the new password and the token's resolved
  account UUID; a violation prevents `Account.unlock`, the hash update,
  `refreshTokenTracker.revokeAllForPrincipal`, and the `password.reset` audit record — `InOrder`
  proof.

New, in `AccountControllerTest`:
- `register` propagates `PasswordPolicyViolationException` uncaught for the handler to translate.
- `passwordReset` propagates `PasswordPolicyViolationException` uncaught for the handler to
  translate.

Regression: existing `changePassword` tests (T08) continue passing unmodified.

## Constraints

- **Transaction:** both `register` and `resetPassword` are already `@Transactional`; a policy
  violation must throw before any persistence/outbox side effect.
- **Null handling:** `PasswordPolicy.validate` already `Objects.requireNonNull`s both UUID params
  — both call sites pass real, non-null UUIDs (the constructed-but-unpersisted account's UUID for
  `register`; the token-resolved account's UUID for `resetPassword`).
- **Module boundaries:** all changes stay inside `account`; no new cross-module dependency.
- **Security:** `register`'s enumeration-safety (L5) depends on the locked ordering in AC4 — never
  reorder the duplicate-email check ahead of the policy check. The reset-token oracle (Finding 3)
  is an accepted, documented residual risk, not a defect to fix in this task.

## Open Questions

No blockers. All three genuine design tensions Phase 3 surfaced were resolved by explicit human
decision above.
