# auth · T18 — Phase 2: Task Implementation Brief

## Task

Implement `MfaService` (begin-enroll, confirm, disable) and `TotpVerifier` (stateless TOTP-code
verification), covering recovery-code generation, hashing, and storage as part of `MfaService`.

## Purpose

The orchestration layer that turns T16's crypto primitives and T17's persistence into the actual
enroll/confirm/disable business flows R22/R23/R28/R29 require. No controller (task 19), no SAS
login-flow integration (task 20) — but `TotpVerifier` and the recovery-code verification path are
built as reusable, standalone pieces specifically because task 20 will need the identical
verification logic for ongoing logins (R25), not just this task's confirm/disable flows.

## Scope

**In:**
- `TotpVerifier.verify(byte[] secret, String submittedCode, Instant now)` — RFC 6238 HMAC-SHA1,
  6 digits, 30s step (L6). Checks the current time step **and the adjacent ±1 step** (90s total
  tolerance window, standard clock-skew allowance) — this specific window isn't in L6 and is
  flagged for Phase 4 confirmation. Stateless, no dependencies, mirrors `TotpGenerator`'s
  no-AWS-import posture.
- `MfaService.beginEnroll(UUID accountUuid)`:
  - Resolves `accountId` via `MfaEnrollmentRepository.findAccountIdByUuid`.
  - Checks `findByAccountIdAndType(accountId, TOTP)`: if a **confirmed** row exists, throws
    `MfaAlreadyEnrolledException`. If an **unconfirmed** row exists, deletes it first
    (`deleteByAccountIdAndType`) so the caller can retry an abandoned enrollment — no new mutator
    needed on `MfaEnrollment` (it has none for `secretEncrypted`); delete-then-recreate reuses
    T17's existing repository surface. **This retry behavior isn't explicitly required by R22 and
    is flagged for explicit Phase 4 confirmation**, not silently decided — the alternative is
    rejecting any existing row outright, confirmed or not.
  - Generates a secret (`TotpGenerator.generateSecret()`), encrypts it
    (`MfaSeedEncryption.encrypt`), persists via `MfaEnrollment.create(...)` +
    `mfaEnrollmentRepository.save(...)`.
  - Returns the raw secret and the `otpauth://` URI (`TotpGenerator.buildProvisioningUri`) —
    exact return shape (a new small record) is an implementation-time detail, not fixed here.
- `MfaService.confirm(UUID accountUuid, String submittedCode)`:
  - Loads the account's unconfirmed TOTP enrollment (`findByAccountIdAndType`); throws
    `MfaNotEnrolledException` if none.
  - Decrypts the secret (`MfaSeedEncryption.decrypt`), verifies via `TotpVerifier.verify`.
  - On failure: records `mfa.failed` (R29) via `AuditService.record(...)`, throws
    `InvalidTotpCodeException`. **Does not mutate anything.**
  - On success: `enrollment.confirm(clock.instant())`, generates 10 recovery codes (`SecureRandom`
    → `Base64.getUrlEncoder().withoutPadding()`, matching `VerificationTokenService`'s established
    raw-token pattern — 16 random bytes each, flagged for Phase 4 confirmation of the exact byte
    count), hashes each with the existing `common.Hashing.sha256(...)` utility (reused, not
    reimplemented), persists 10 `RecoveryCode` rows, and returns the 10 raw codes — their only
    appearance in plaintext (R23).
- `MfaService.disable(UUID accountUuid, String currentPassword, String submittedCode)`:
  - Verifies the password via `accountService.getByUuid(accountUuid)` →
    `accountService.findLoginView(email)` → `passwordEncoder.matches(...)` — reuses only
    **already-public** `AccountService` methods; no change to `AccountService` at all. On
    mismatch: throws `MfaCurrentPasswordMismatchException` (no `mfa.failed` audit — a wrong
    *password* isn't a TOTP/recovery-code verification failure per R29's literal wording).
  - Loads the account's **confirmed** TOTP enrollment
    (`findByAccountIdAndTypeAndConfirmedAtIsNotNull`); throws `MfaNotEnrolledException` if none.
  - Decrypts + verifies the submitted code via `TotpVerifier`. On failure: records `mfa.failed`
    (R29), throws `InvalidTotpCodeException`.
  - On success: `deleteByAccountIdAndType` (removes the enrollment), then deletes every
    `RecoveryCode` for the account (account-level, per T17 Phase 4's own note — no
    enrollment-scoping FK exists) — needs a new `RecoveryCodeRepository.deleteByAccountId(Long)`
    method (T17 didn't add one; not needed until now). Records `mfa.disabled` (R28) via
    `AuditService.record(...)`.
- New exceptions (each a standalone `RuntimeException` subclass, matching the codebase's
  one-class-per-exception convention — no shared hierarchy): `MfaAlreadyEnrolledException`,
  `MfaNotEnrolledException`, `InvalidTotpCodeException`, `MfaCurrentPasswordMismatchException`.

**Out:**
- No controller, no DTOs beyond the minimal ones `beginEnroll`/`confirm` need to return (task 19).
- No SAS/login-flow integration, no recovery-code verification call site for login (task 20) —
  though `MfaService` will expose the primitives task 20 needs (a public recovery-code-verify
  method is natural to add here too, but the task statement's "recovery-code
  generation/**verification**" clause is ambiguous about whether "verification" means the
  confirm-flow's TOTP check or a recovery-code redemption method for login; **this is a genuine
  scope question flagged for explicit Phase 4 confirmation** — this brief includes a
  `MfaService.verifyRecoveryCode(UUID accountUuid, String rawCode)` method using
  `RecoveryCodeRepository.findByAccountIdAndCodeHash` + `markUsed`, callable by task 20 later, on
  the reading that "verification" is this task's to build even though nothing calls it yet).
- No `TokenClaimsCustomizer` changes (task 21). No new Flyway migration (no schema change).
- No changes to `MfaEnrollment`/`RecoveryCode`/their repositories beyond the one new
  `deleteByAccountId` method noted above.

## Business Rules

- **R22** (begin-enroll: generate, encrypt, persist unconfirmed).
- **R23** (confirm: verify, confirm, generate+hash+return 10 codes exactly once).
- **R28** (disable: password + TOTP required, remove enrollment, invalidate all codes, audit).
- **R29** (any TOTP/recovery-code verification failure → `mfa.failed` audit, deny).

## Locked Decisions

- **L6.** RFC 6238 params govern `TotpVerifier`; "only SHA-256 hashes are stored" governs
  recovery-code hashing (via the existing `Hashing.sha256` utility, not a new implementation).
- **L12** (widened, as for T16/T17). No `Account` import — password verification composes
  `AccountService`'s existing public `getByUuid`/`findLoginView` methods.

## Dependencies

- `TotpGenerator`, `MfaSeedEncryption`, `MfaEnrollment`(`Repository`), `RecoveryCode`(`Repository`)
  — all existing (T16/T17), unmodified except the one new `deleteByAccountId` method.
- `AccountService` (existing public methods only), `PasswordEncoder`, `Clock`, `AuditService`,
  `common.Hashing`.
- No new config keys, no new dependencies, no new contracts (the four referenced in this task's
  header don't exist in the repo — confirmed at Phase 1, same as every prior MFA task).

## Inputs / Outputs

- `beginEnroll(accountUuid)` → raw secret + provisioning URI.
- `confirm(accountUuid, code)` → 10 raw recovery codes.
- `disable(accountUuid, password, code)` → void.
- `verifyRecoveryCode(accountUuid, rawCode)` → boolean (consumed, for task 20's future use).

## State Changes

`mfa_enrollments`: insert (begin-enroll), update `confirmed_at` (confirm), delete (disable, or
begin-enroll's retry-delete path). `recovery_codes`: 10 inserts (confirm), mark-used update
(`verifyRecoveryCode`), bulk delete (disable). `auth_audit` + Kafka mirror: `mfa.failed`,
`mfa.disabled`.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/TotpVerifier.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaAlreadyEnrolledException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaNotEnrolledException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/InvalidTotpCodeException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaCurrentPasswordMismatchException.java`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCodeRepository.java` — add
  `deleteByAccountId(Long)`.

## Files NOT to Modify

- `AccountService.java` (only its existing public methods are called), `Account.java`,
  `MfaEnrollment.java`, `MfaEnrollmentRepository.java`, `RecoveryCode.java` — no changes beyond
  the one repository method above. No controller, no `TokenClaimsCustomizer`, no migration.

## Acceptance Criteria

(As listed in Phase 1: AC1-AC7, unchanged — begin-enroll persistence + duplicate rejection,
confirm verify/confirm/generate-codes, confirm-failure audit + no mutation, disable
password+TOTP+delete+audit, disable-failure paths, clock-skew tolerance, recovery-code
randomness/hashing/no-plaintext-persistence.)

## Required Tests

Both named tests (`shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes`,
`shouldRequirePasswordAndTotpToDisableMfa` — both mismapped in `package.md` §8, R23/R28 are the
real matches) are **satisfiable at this task's service layer**, unlike T16/T17 — `MfaService`
owns the full confirm and disable flows end to end. Plus: wrong-code/wrong-password boundary
tests, double-confirm (hits `MfaEnrollment.confirm`'s existing guard), begin-enroll retry-on-
abandoned-enrollment, disable-with-no-enrollment, recovery-code single-use (`markUsed` semantics,
already proven at T17).

## Constraints

- **Security:** raw TOTP secret, raw recovery codes, and raw passwords never logged. Recovery
  codes returned exactly once (R23) — never re-derivable from the stored hash.
- **Thread-safety:** `MfaService` is a stateless Spring singleton; no mutable instance state.
- **Transaction:** each public `MfaService` method is one `@Transactional` boundary (matches
  `AccountService`'s convention) — `confirm`'s enrollment-update + 10 recovery-code inserts are
  one atomic unit; `disable`'s enrollment-delete + recovery-codes-delete are one atomic unit.
- **Module boundaries (L12):** no `Account` import; password verification composes
  `AccountService`'s public API only.
- **Null handling:** no null-argument case beyond what the entities/repositories already
  enforce — mirrors T17's precedent scope.

## Open Questions

No blockers. Four scope/design calls are flagged above for explicit Phase 4 confirmation rather
than silently decided: (1) `TotpVerifier` as a separate class vs. a private method, (2)
begin-enroll's delete-and-retry behavior for an abandoned unconfirmed enrollment, (3) the exact
recovery-code byte length (16) and encoding (URL-safe Base64, matching `VerificationTokenService`),
(4) whether `verifyRecoveryCode` belongs in this task at all given nothing calls it until task 20.
