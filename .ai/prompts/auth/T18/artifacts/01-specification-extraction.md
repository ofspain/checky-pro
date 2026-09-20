# auth · T18 — Phase 1: Specification Extraction

Consumes `artifacts/00-repository-understanding.md`. Extracting only what T18 ("MFA service")
needs — nothing about tasks 16, 17, 19, 20, 21.

## Business Rules

- **R22.** WHEN an authenticated user without a confirmed TOTP enrollment calls
  `POST /accounts/me/mfa/totp`, THEN the system SHALL generate a random TOTP secret, encrypt it,
  persist it as unconfirmed, and return an `otpauth://` provisioning URI. (T18 owns the
  generate+encrypt+persist logic; the endpoint itself is task 19.)
- **R23.** WHEN the user submits the correct first TOTP code to
  `POST /accounts/me/mfa/totp/confirm`, THEN the system SHALL confirm the enrollment, generate 10
  single-use recovery codes, store only hashes, and return the recovery codes exactly once. (T18
  owns verify+confirm+generate-codes; the endpoint is task 19.)
- **R28.** WHEN an authenticated user supplies their current password and a valid TOTP code to
  `DELETE /accounts/me/mfa/totp`, THEN the system SHALL remove the enrollment, invalidate all
  recovery codes, and record an `mfa.disabled` audit event. Note: the wording is "a valid TOTP
  code" only — no recovery-code alternative for disabling, unlike login (R25).
- **R29.** IF a TOTP code or recovery code verification fails, THEN the system SHALL record an
  `mfa.failed` audit event and deny authentication. Applies to every verification T18 performs
  (confirm, disable) — the login-flow case (R25) is task 20's, not this one's, but the underlying
  verification primitives T18 builds are what task 20 will reuse.

## Locked Decisions

- **L6.** RFC 6238 (30s, 6 digits, HMAC-SHA1) — governs the TOTP-code verification math (HMAC-SHA1
  over the decrypted secret, compared against the submitted 6-digit code for the current and
  adjacent time steps, per standard TOTP clock-skew tolerance practice — exact window is a Phase 2
  design detail). Also: "Recovery codes are random single-use values; only SHA-256 hashes are
  stored" — governs how T18 generates and hashes recovery codes.
- **L12** (not in this task's scoped list, but governs, same treatment as T16/T17): no import of
  `com.themistra.auth.account.Account` — password re-verification for `disable` (R28) must go
  through `AccountService`'s existing public API (a cross-module service call, which L12 permits),
  never the `Account` entity directly.

## Files Involved

**New** (none exist yet in `mfa/` beyond T16/T17's files):
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java` — begin-enroll, confirm,
  disable.
- A TOTP-code-verification component (`design.md` §6 names it `TotpVerifier.java`) — not its own
  numbered task anywhere in `tasks.md`; T18 is the first task that needs it. Whether it's a
  separate class or a private method on `MfaService` is a Phase 2 call.
- A recovery-code component (`design.md` §6 names it `RecoveryCodeService.java`) — same
  separate-class-or-not question as above.

**Existing, read-only dependencies (not modified):**
- `mfa.TotpGenerator` (T16) — `generateSecret()`, `buildProvisioningUri(byte[], String)`.
- `mfa.MfaSeedEncryption` (T16) — `encrypt(byte[])`, `decrypt(byte[])`.
- `mfa.MfaEnrollment`/`MfaEnrollmentRepository` (T17) — full CRUD + confirmed-only finder + delete
  surface already exists.
- `mfa.RecoveryCode`/`RecoveryCodeRepository` (T17) — full CRUD + hash lookup + atomic `markUsed`
  already exists.
- `account.AccountService` — password re-verification pattern (`changePassword`'s
  `passwordEncoder.matches(...)` + `CurrentPasswordMismatchException`); `AccountService` itself or
  `PasswordEncoder` may need to be called into directly.
- `audit.AuditService.record(RecordAuditEventRequest)` — for `mfa.disabled` (R28) and `mfa.failed`
  (R29) events.

## Dependencies

- `PasswordEncoder` (Spring Security, already a project dependency) — for password
  re-verification, matching `AccountService`'s own usage.
- `Clock` (existing bean, `SecurityBeansConfig`) — for every timestamp T18 produces.
- `SecureRandom` — for generating 10 raw recovery codes (T18's own responsibility; T17 only stores
  hashes, never generates the plaintext).
- `MessageDigest`/SHA-256 — for hashing recovery codes (matches `common.Hashing.sha256(...)`,
  already used by `AuditService` for `userAgentHash` — worth checking if this exact utility should
  be reused rather than a new SHA-256 call site).
- No new config keys. No new contracts — `contracts/api/auth.yaml`,
  `contracts/api/token-claims.md`, `contracts/events/auth/email-requested.v1.schema.json`, and
  `contracts/events/auth/security-audit.v1.schema.json` (all four referenced in this task's own
  header) **do not exist in the repository** — confirmed via direct filesystem check
  (`contracts/api/` contains only a `.gitkeep`; `contracts/events/auth/` contains only an
  unrelated `user-lifecycle.v1.schema.json`). Same non-existence as every prior MFA task's header
  referenced them without needing them; T18 doesn't touch the API or event-schema layer directly
  either (only the existing Java-level `RecordAuditEventRequest` for audit, which has its own
  already-working contract independent of these files). Not a blocker, just noted.

## Acceptance Criteria

- **AC1 (R22).** Begin-enroll generates a secret via `TotpGenerator`, encrypts it via
  `MfaSeedEncryption`, and persists a new unconfirmed `MfaEnrollment` — but only if no enrollment
  (confirmed or not) already exists for that account+type (schema's `UNIQUE(account_id, type)`,
  per T17's Finding #1 resolution).
- **AC2 (R23).** Confirm verifies the submitted TOTP code against the decrypted secret of the
  caller's unconfirmed enrollment; on success, calls `MfaEnrollment.confirm(...)`, generates 10
  random recovery codes, hashes and persists each as a `RecoveryCode`, and returns the 10 raw
  codes (this exact call is their only appearance in plaintext, per R23's "exactly once").
- **AC3 (R23, R29).** Confirm with an incorrect TOTP code does not confirm the enrollment, does
  not generate codes, and records an `mfa.failed` audit event.
- **AC4 (R28).** Disable verifies the current password (`AccountService`'s pattern) and a valid
  TOTP code against the confirmed enrollment; on success, deletes the `MfaEnrollment`
  (`deleteByAccountIdAndType`), invalidates every recovery code for the account (no
  enrollment-scoping FK exists — T17's Phase 4 already flagged this as necessarily
  account-level), and records an `mfa.disabled` audit event.
- **AC5 (R28, R29).** Disable with a wrong password or wrong TOTP code does not remove anything
  and records `mfa.failed` (or reuses `AccountService`'s existing password-mismatch handling —
  Phase 2 to decide exact shape).
- **AC6 (L6).** TOTP verification correctly accounts for standard clock-skew tolerance (the
  current 30s step plus adjacent steps) — exact window a Phase 2 design decision, not invented
  here.
- **AC7 (L6, R23).** Recovery codes are cryptographically random, hashed with SHA-256 before
  storage, and the raw values are never persisted or logged anywhere.

## Tests Required

- **Named test** `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` (`package.md` §8,
  mismapped there to R20 — a stale mismatch already flagged three times now at T16/T17/T18;
  R23 is the semantically correct match). Unlike T16/T17, **T18 may be able to satisfy this named
  test's substance directly** (begin-enroll + confirm + recovery-code generation is exactly T18's
  service-layer scope) even though the literal HTTP endpoint is task 19 — a Phase 2 question:
  whether to write it at the service layer now or defer to task 19's controller-level test.
- **Named test** `shouldRequirePasswordAndTotpToDisableMfa` (`package.md` §8, mismapped there to
  R25 — R28 is the actual match). Same consideration as above: T18's `disable` method is where
  this requirement actually lives.
- **Boundary tests implied**: wrong TOTP code, expired/skewed TOTP code at the edge of the
  tolerance window, confirm called twice (should hit `MfaEnrollment.confirm`'s existing
  `IllegalStateException` guard), disable with wrong password, disable with wrong TOTP code,
  recovery codes are exactly 10 and each is single-use (`markUsed` semantics already proven at
  T17), disable when no enrollment exists.

## Open Questions

No blockers from `package.md` §11 (none of Q1-Q6 concern the MFA service layer; Q1 is resolved and
belongs to T16). Two genuine design questions carried from Phase 0, not decided here:
1. Whether `TotpVerifier`/`RecoveryCodeService` are separate classes (per `design.md`'s file
   map) or methods on `MfaService` itself.
2. O5 (recovery-code hashing) is formally still "open" in `design.md`, though SHA-256 is the only
   workable choice given T17's `CHAR(64)` column — flagged for explicit Phase 2/4 confirmation,
   not silently assumed.
Neither blocks proceeding to Phase 2; both are brief-level decisions to make there.
