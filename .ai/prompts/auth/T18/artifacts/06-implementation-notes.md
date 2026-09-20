# auth · T18 — Phase 6: Implementation Notes

Implements `artifacts/05-implementation-plan.md` against the frozen brief
(`artifacts/04-frozen-task-brief.md`). No tests written here (Phase 10). No files touched beyond
the plan's Files-to-Create/-Modify lists.

## Files Created

- **`TotpVerifier.java`** — stateless RFC 6238 verification. `verify(secret, submittedCode, now)`
  computes the HMAC-SHA1 code for the current 30s step and ±1 adjacent step (90s tolerance, L6),
  comparing each candidate against `submittedCode` with a constant-time string comparison. Private
  `generateCode`/`hmacSha1` match the plan's signatures exactly. No AWS SDK import, matching
  `TotpGenerator`'s posture and `ArchitectureTest.only_MfaSeedEncryption_may_use_the_aws_sdk`.

- **`MfaService.java`** — implements all four methods from the frozen brief:
  - `beginEnroll(accountUuid)`: status-checked via `requireActiveAccount`, resolves `accountId`,
    inspects any existing `(accountId, TOTP)` row — confirmed throws
    `MfaAlreadyEnrolledException`, unconfirmed is deleted in-transaction before the new insert
    (the human-confirmed retry behavior). Generates/encrypts/persists, returns
    `BeginEnrollResult(secret, provisioningUri)` with the account UUID (not email) as the
    provisioning label.
  - `confirm(accountUuid, submittedCode)`: loads the `(accountId, TOTP)` row (any confirmation
    state) via `findByAccountIdAndType`, throwing `MfaNotEnrolledException` if none exists at all.
    Decrypts + verifies; on failure records `mfa.failed` and throws `InvalidTotpCodeException`
    with no mutation. On success calls `enrollment.confirm(...)` — **deliberately not
    pre-filtered to unconfirmed-only**, so a correct-code re-confirm attempt reaches
    `MfaEnrollment.confirm`'s own `IllegalStateException` guard directly, per the plan's
    double-confirm test requirement. Generates 10×32-byte recovery codes, hashes via
    `Hashing.sha256`, persists, returns them raw exactly once.
  - `disable(accountUuid, currentPassword, submittedCode)`: status-checked, then password verified
    via `accountService.findLoginView(account.email())` + `passwordEncoder.matches` — a `null`
    login view (deleted between calls) is folded into the same mismatch path, no distinguishing
    exception. Wrong password records the new `mfa.disable_failed` (FAILURE) event and throws
    `MfaCurrentPasswordMismatchException`. Loads the **confirmed** enrollment only
    (`findByAccountIdAndTypeAndConfirmedAtIsNotNull`); none throws `MfaNotEnrolledException`. Wrong
    code records `mfa.failed` and throws `InvalidTotpCodeException`. Success deletes the enrollment
    and all recovery codes, records `mfa.disabled` (SUCCESS).
  - `verifyRecoveryCode(accountUuid, rawCode)`: no account-status check (frozen brief scopes that
    precondition to the other three flows only — this one is for task 20's login-time use, where
    the caller already established usability). Hashes, looks up, calls `markUsed`; a missing row or
    a `0` `markUsed` result both record `mfa.failed` and throw `InvalidRecoveryCodeException`.
    Returns `void` on success (throw-on-failure style, matching `confirm`/`disable`, not a
    boolean).

  Private helpers: `requireActiveAccount(accountUuid, action)` (status gate, reused by the three
  flows that need it, each with a distinct `action` string for `InvalidAccountStateException`'s
  message), `resolveAccountId(accountUuid)` (uses `MfaEnrollmentRepository.findAccountIdByUuid`,
  throws `AccountNotFoundException` — reused by all four methods, resolving Phase 3 Finding 10
  uniformly), `generateRawRecoveryCode()`, `recordAudit(eventType, outcome, accountUuid)`.

  **Deviation from the plan (consolidation, not scope change):** the plan named a dedicated
  private `recordMfaFailed(accountUuid)` helper. Implemented instead as a single
  `recordAudit(eventType, outcome, accountUuid)`, since `mfa.disabled` and `mfa.disable_failed`
  need the identical call shape and a second near-duplicate helper added nothing. Every call site
  the plan specified is still present with the same audit contract (self-service:
  `accountUuid == actorUuid`, `ip`/`rawUserAgent`/`traceId`/`details` all `null`).

- **Five exception classes** — `MfaAlreadyEnrolledException`, `MfaNotEnrolledException`,
  `InvalidTotpCodeException`, `InvalidRecoveryCodeException`, `MfaCurrentPasswordMismatchException`
  — each a standalone `RuntimeException` subclass with a fixed, secret-free message, matching the
  codebase's one-class-per-exception convention (no shared hierarchy).

## Files Modified

- **`RecoveryCodeRepository.java`** — added `void deleteByAccountId(Long accountId)`, used by
  `disable` to invalidate every recovery code (account-scoped, since `RecoveryCode` has no
  enrollment FK, per T17 Phase 4).

## Mapping to Acceptance Criteria

- **AC1** (begin-enroll persists + rejects duplicate): `beginEnroll` — confirmed-row branch throws
  `MfaAlreadyEnrolledException`; unconfirmed-row branch deletes-and-recreates.
- **AC2** (confirm verifies/confirms/generates codes): `confirm`'s success path.
- **AC3** (confirm-failure audits, no mutation): `confirm`'s `mfa.failed` branch — occurs entirely
  before `enrollment.confirm(...)` is called.
- **AC4** (disable requires password+TOTP, deletes, audits): `disable`'s full sequence.
- **AC5** (disable-failure paths): both the password-mismatch and wrong-code branches in `disable`.
- **AC6** (clock-skew tolerance): `TotpVerifier`'s ±1-step loop.
- **AC7** (recovery-code randomness/hashing/no-plaintext-persistence): `generateRawRecoveryCode`
  (32 `SecureRandom` bytes, URL-safe Base64) + `Hashing.sha256` before every `RecoveryCode.create`
  call — the raw code exists only in the returned `ConfirmResult`, never persisted.

## Verification

- `mvn compile` — clean.
- `mvn test -Dtest=ArchitectureTest` — passes; no `Account` entity import, no AWS SDK import
  outside `MfaSeedEncryption`, `RecoveryCodeRepository`/`MfaEnrollmentRepository` remain
  package-private.

## Deviations Requiring Flagging

None beyond the `recordMfaFailed`→`recordAudit` consolidation noted above, which is an
implementation-detail merge of two identically-shaped planned private methods, not a behavioral or
scope change — every audit event, condition, and exception the frozen brief specifies is present.

## Open Questions

None.
