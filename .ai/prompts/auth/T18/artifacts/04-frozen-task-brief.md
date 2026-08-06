# auth · T18 — Phase 4: Frozen Task Brief

**STATUS: FROZEN**

Approved by: femi (human approval gate) · 2026-08-06
Base: `artifacts/02-task-implementation-brief.md`, challenged in `artifacts/03-design-challenge.md`.
Downstream phases (5+) may not renegotiate this brief.

---

## Task

Implement `MfaService` (begin-enroll, confirm, disable, recovery-code verification) and
`TotpVerifier` (stateless TOTP-code verification), covering recovery-code generation and hashing.

## Purpose

The orchestration layer turning T16's crypto primitives and T17's persistence into the
enroll/confirm/disable/verify flows R22/R23/R28/R29 require. `TotpVerifier` and
`verifyRecoveryCode` are built reusable now because task 20's login flow needs identical logic
(R25), not invented fresh there.

## Scope

**In:**
- **All three flows (`beginEnroll`, `confirm`, `disable`) require the account to be `ACTIVE`**,
  checked via `accountService.getByUuid(accountUuid)` (throws `AccountNotFoundException` for an
  unknown UUID — resolves Finding #10 for free) then `account.status() != AccountStatus.ACTIVE` →
  throw the existing `InvalidAccountStateException(accountUuid, status, action)`. This mirrors
  `AccountService.changePassword`'s exact established sequence (status check, then
  action-specific verification) — not a new pattern, reused as-is. *(Findings #5, #6, #10 —
  RESOLVED.)*
- `TotpVerifier.verify(byte[] secret, String submittedCode, Instant now)` — RFC 6238 HMAC-SHA1,
  6 digits, 30s step (L6), checking the current time step and one adjacent step in each direction
  (90s total tolerance). **Locked, not revisited at a later gate** — Phase 1 and Phase 2 had
  already independently converged on this exact window; continuing to flag it as open served no
  purpose. *(Finding #2 — RESOLVED/LOCKED.)*
- `MfaService.beginEnroll(UUID accountUuid)`:
  - Status check (above).
  - Resolves `accountId` via `MfaEnrollmentRepository.findAccountIdByUuid`.
  - `findByAccountIdAndType(accountId, TOTP)`: a **confirmed** row → `MfaAlreadyEnrolledException`.
    An **unconfirmed** row → deleted (`deleteByAccountIdAndType`) then replaced with a fresh
    secret, in the same `@Transactional` method as the new insert. **Deliberate, human-confirmed
    behavior**: R22 only requires "without a confirmed enrollment," but an abandoned/expired
    unconfirmed enrollment must not permanently block re-enrollment — no other task builds a way
    to explicitly cancel one. *(Finding #1 — RESOLVED, confirmed by the human, not silently
    assumed.)*
  - Generates a secret (`TotpGenerator.generateSecret()`), encrypts it
    (`MfaSeedEncryption.encrypt`), persists via `MfaEnrollment.create(...)`.
  - Builds the provisioning URI via `TotpGenerator.buildProvisioningUri(secret, accountUuid.toString())`
    — **the label is the account UUID, never the email**, to avoid embedding PII in a URI that
    ends up rendered as a QR code and potentially logged by the authenticator app or any
    intermediate system. *(Finding #11 — RESOLVED.)*
  - Returns a new `BeginEnrollResult(byte[] secret, String provisioningUri)` record with a
    **`toString()` override that redacts both fields** (e.g. `"BeginEnrollResult[REDACTED]"`) —
    no logging framework or AOP should ever be able to print secret material through a default
    record `toString()`. *(Finding #9 — RESOLVED.)*
- `MfaService.confirm(UUID accountUuid, String submittedCode)`:
  - Status check. Loads the unconfirmed enrollment (`findByAccountIdAndType`); no match →
    `MfaNotEnrolledException`.
  - Decrypts (`MfaSeedEncryption.decrypt`), verifies (`TotpVerifier.verify`).
  - Failure: records `mfa.failed` (`accountUuid`=target=`actorUuid`, `outcome=FAILURE`, `ip`/
    `rawUserAgent`/`traceId`/`details` all `null` — matches `AccountService`'s existing
    `recordAudit` helper convention exactly), throws `InvalidTotpCodeException`. No mutation.
    *(Finding #8 — RESOLVED.)*
  - Success: `enrollment.confirm(clock.instant())`; generates 10 recovery codes — **32 random
    bytes each** (not 16 — matches `VerificationTokenService.RAW_TOKEN_BYTES` exactly, the
    established precedent for a comparable single-use secret token), `Base64.getUrlEncoder()
    .withoutPadding()` encoded, hashed via the existing `common.Hashing.sha256(...)`. *(Finding
    #3 — RESOLVED: 32 bytes, not the TIB's original 16.)* Persists 10 `RecoveryCode` rows, returns
    a `ConfirmResult(List<String> recoveryCodes)` record with a **redacting `toString()`** (same
    requirement as `BeginEnrollResult`).
- `MfaService.disable(UUID accountUuid, String currentPassword, String submittedCode)`:
  - Status check.
  - Password: `accountService.findLoginView(account.email())` (the `AccountResponse` from the
    status-check call above supplies `email()`) → `passwordEncoder.matches(...)`. If
    `findLoginView` returns empty despite the account existing and being `ACTIVE` moments earlier
    (defensive; not expected to be reachable), throw `AccountNotFoundException` — reusing the same
    exception, not inventing a new one. On mismatch: **records a new `mfa.disable_failed` audit
    event** (`outcome=FAILURE`, same shape as `mfa.failed` otherwise) and throws
    `MfaCurrentPasswordMismatchException`. This event type is **not named by any requirement** —
    it exists solely to satisfy `agents.md`'s standing "every security-relevant action is
    recorded" rule for a destructive action's failed attempt, since R29's `mfa.failed` is
    precisely scoped to TOTP/recovery-code failures only, and misusing it for a password failure
    would misrepresent what actually failed. *(Finding #7 — RESOLVED, human-confirmed scope
    addition beyond R29's literal text.)*
  - Loads the **confirmed** enrollment (`findByAccountIdAndTypeAndConfirmedAtIsNotNull`); no
    match → `MfaNotEnrolledException`.
  - Decrypts + verifies via `TotpVerifier`. Failure: records `mfa.failed` (R29), throws
    `InvalidTotpCodeException`.
  - Success: `deleteByAccountIdAndType` (removes enrollment), `recoveryCodeRepository
    .deleteByAccountId(accountId)` (new repository method — account-level, no enrollment FK
    exists per T17's own Phase 4 note), records `mfa.disabled` (`outcome=SUCCESS`) (R28).
- `MfaService.verifyRecoveryCode(UUID accountUuid, String rawCode)` — **kept in T18** (the task
  statement names "recovery-code generation/**verification**" explicitly; nothing calling it yet
  doesn't make it out of scope, matching T17's own precedent of building forward-looking,
  complete repository methods for tasks that hadn't started). Fully specified, not left vague:
  hashes `rawCode` via `Hashing.sha256`, looks up via `findByAccountIdAndCodeHash`; if absent, or
  if found but `markUsed` returns `0` (already used): records `mfa.failed` (R29) and throws a new
  `InvalidRecoveryCodeException` — **returns `void` on success, never a `boolean`**, for
  consistency with `confirm`/`disable`'s throw-on-failure style. *(Finding #4 — RESOLVED: kept in
  scope, fully specified, exception-based not boolean-based.)*
- New exceptions: `MfaAlreadyEnrolledException`, `MfaNotEnrolledException`,
  `InvalidTotpCodeException`, `InvalidRecoveryCodeException`, `MfaCurrentPasswordMismatchException`
  — each standalone, no shared hierarchy, matching the codebase's one-class-per-exception
  convention. (`AccountNotFoundException`/`InvalidAccountStateException` are reused from
  `account`, not reimplemented — both are public classes in a package `mfa` may reference without
  violating L12, which forbids only the `Account` entity import specifically.)

**Out:**
- No controller, no HTTP DTOs (task 19). No SAS/login-flow call site for `verifyRecoveryCode`
  (task 20) — the method exists and is complete, just uncalled until then.
- No `TokenClaimsCustomizer` changes (task 21). No new Flyway migration.
- No changes to `MfaEnrollment`/`Account`/`AccountService` — password/status logic composes only
  already-public `AccountService` methods.

## Business Rules

R22, R23, R28, R29 — as extracted at Phase 1, with the account-status precondition (`ACTIVE`)
added to all three flows per Finding #5's resolution (not stated by R22/R23/R28 themselves, but
required by the standing convention `changePassword` already establishes for self-service
security actions).

## Locked Decisions

- **L6.** RFC 6238 params (`TotpVerifier`, 90s tolerance window) and "only SHA-256 hashes stored"
  (recovery-code hashing via `common.Hashing.sha256`).
- **L12** (widened). No `Account` entity import; `AccountResponse`, `AccountStatus`,
  `AccountNotFoundException`, `InvalidAccountStateException` are all fair game as public
  `account`-package classes.

## Dependencies

`TotpGenerator`, `MfaSeedEncryption`, `MfaEnrollment`(`Repository`), `RecoveryCode`(`Repository`)
— all existing, unmodified except the one new `RecoveryCodeRepository.deleteByAccountId(Long)`.
`AccountService` (public methods only: `getByUuid`, `findLoginView`), `PasswordEncoder`, `Clock`,
`AuditService`, `common.Hashing`. No new config, no new contracts (confirmed non-existent at
Phase 1).

## Inputs / Outputs

- `beginEnroll(accountUuid)` → `BeginEnrollResult(secret, provisioningUri)`.
- `confirm(accountUuid, code)` → `ConfirmResult(List<String> recoveryCodes)` (10 raw codes).
- `disable(accountUuid, password, code)` → `void`.
- `verifyRecoveryCode(accountUuid, rawCode)` → `void` (throws on failure).

## State Changes

`mfa_enrollments`: insert/delete-then-insert (begin-enroll), update `confirmed_at` (confirm),
delete (disable). `recovery_codes`: 10 inserts (confirm), atomic mark-used update
(`verifyRecoveryCode`), bulk delete by account (disable, new repository method). `auth_audit` +
Kafka mirror: `mfa.failed`, `mfa.disabled`, `mfa.disable_failed` (new event type).

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/TotpVerifier.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaAlreadyEnrolledException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaNotEnrolledException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/InvalidTotpCodeException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/InvalidRecoveryCodeException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaCurrentPasswordMismatchException.java`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCodeRepository.java` — add
  `deleteByAccountId(Long)`.

## Files NOT to Modify

`AccountService.java`, `Account.java`, `MfaEnrollment.java`, `MfaEnrollmentRepository.java`,
`RecoveryCode.java` — no changes beyond the one repository method above. No controller, no
`TokenClaimsCustomizer`, no migration.

## Acceptance Criteria

AC1-AC7 as extracted at Phase 1, plus:
- **AC8.** All three flows reject a non-`ACTIVE` account with `InvalidAccountStateException`.
- **AC9.** A wrong password on `disable` records `mfa.disable_failed` (not `mfa.failed`) and
  changes nothing.
- **AC10.** `verifyRecoveryCode` throws `InvalidRecoveryCodeException` and records `mfa.failed`
  for an unknown or already-used code; a valid code is consumed exactly once (`markUsed`
  semantics, already proven at T17).
- **AC11.** `BeginEnrollResult`/`ConfirmResult`'s `toString()` never exposes the secret/URI/codes.
- **AC12.** The provisioning URI's label is the account UUID, never the email.

## Required Tests

Both named tests remain satisfiable at this service layer (R23/R28 are the real matches, per the
recurring `package.md` §8 numbering bug already flagged at T16/T17). Plus: status-check rejection
for each flow, begin-enroll retry-on-abandoned-enrollment (asserting the old secret is genuinely
invalidated, not just replaced in appearance), wrong-password vs. wrong-TOTP-code on disable
producing distinct audit event types, `verifyRecoveryCode` single-use enforcement, redacted
`toString()` on both result records.

## Constraints

- **Security:** no raw secret/code/password ever logged; both result records redact `toString()`.
- **Thread-safety:** `MfaService` stateless singleton.
- **Transaction:** each public method is one `@Transactional` boundary; begin-enroll's
  delete-then-insert and confirm's confirm-then-generate-10-codes are each atomic.
- **Module boundaries (L12):** no `Account` entity import; only public `account`-package classes.
- **Null handling:** unchanged from Phase 2 — no null-argument case beyond what T17's entities
  already enforce.

## Open Questions

None outstanding. All 11 Phase 3 findings have an explicit disposition above.

## Phase 3 Findings — Disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | Begin-enroll retry-delete conflicts with literal R22 | High | RESOLVED — delete-and-retry kept, human-confirmed, in the same transaction |
| 2 | Clock-skew window redundantly flagged as open | Medium | LOCKED — ±1 step (90s), no further re-confirmation |
| 3 | Recovery-code entropy/format not locked, inconsistent with precedent | Medium | RESOLVED — 32 bytes (was 16), matches `VerificationTokenService` exactly |
| 4 | `verifyRecoveryCode` has no caller, unclear contract | Medium | RESOLVED — kept in T18, fully specified, exception-based |
| 5 | Account-status preconditions unstated | High | RESOLVED — `ACTIVE` required for all three flows, via `changePassword`'s established pattern |
| 6 | Disable password-verification path underspecified | Medium | RESOLVED — sequence documented, empty-`findLoginView` defensive case specified |
| 7 | Wrong-password-on-disable has no audit trail | Medium | RESOLVED — new `mfa.disable_failed` event, human-confirmed scope addition |
| 8 | Audit-event shape unspecified | Low | RESOLVED — matches `AccountService`'s existing `recordAudit` shape exactly |
| 9 | No logging guard on secret-carrying return records | Medium | RESOLVED — explicit redacting `toString()` requirement |
| 10 | Unknown account UUID handling unspecified | Low | RESOLVED — free consequence of #5's fix (`getByUuid` throws `AccountNotFoundException`) |
| 11 | Provisioning URI label is PII (email) | Low | RESOLVED — account UUID used instead |
