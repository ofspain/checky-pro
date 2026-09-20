# auth · T16 — Phase 1: Specification Extraction

## Business Rules

- **R22.** WHEN an authenticated user without a confirmed TOTP enrollment calls `POST
  /accounts/me/mfa/totp`, THEN the system SHALL generate a random TOTP secret, encrypt it, persist
  it as unconfirmed, and return an `otpauth://` provisioning URI. **T16 builds only two of R22's
  four components** — secret generation and encryption — per the task statement's own text
  ("Implement `TotpGenerator`... and the selected encryption primitive"). Persistence
  (`MfaEnrollment` entity/repository) is task 17; the endpoint itself is task 19. See Open
  Questions.

## Locked Decisions

- **L6.** TOTP algorithm: RFC 6238, 30-second time step, 6 digits, HMAC-SHA1. Recovery codes are a
  separate concern (random, single-use, SHA-256 hash only) — not built by this task (task 18).
- **L13.** Secrets discipline: no secret/credential/key material committed; External Secrets
  Operator injects values; hardcoded defaults exist only for `local` and are refused elsewhere by a
  validated `@ConfigurationProperties` binding or startup guard. Directly governs
  `themistra.auth.mfa.seed-kek-arn`.
- **L14 (widened at Phase 0 — not in the task header's scoped list, but directly governing).**
  TOTP seed encryption: AES-256-GCM, KMS-enveloped data key, `GenerateDataKey` at enrollment /
  `Decrypt` at read time, confined to `com.themistra.auth.mfa.MfaSeedEncryption` — the narrow,
  named D-010 exception (`auth-decisions.md` D-025, ADR-0003). ADR-0003 fully specifies the
  ciphertext envelope byte layout — not re-derived here, just implemented as written.

## Files involved

**New (this task builds these):**
- `com.themistra.auth.mfa.TotpGenerator` — random secret + `otpauth://` URI construction. Named
  explicitly by the task statement.
- `com.themistra.auth.mfa.MfaSeedEncryption` — encrypt/decrypt per ADR-0003's envelope format.
  **Naming discrepancy found:** `design.md`'s file-tree sketch (line 256) calls this
  `TotpSeedEncryption.java`, but L14 (the LOCKED decision, dated 2026-07-22, same day as ADR-0003)
  explicitly names the class `MfaSeedEncryption`. L14 is authoritative — it's a LOCKED decision,
  post-dates the file-tree sketch, and is corroborated by ADR-0003's own text ("confined to
  `com.themistra.auth.mfa.MfaSeedEncryption`") and by `agents.md`'s Security section, which also
  says `MfaSeedEncryption`. Flagged as an Open Question for explicit confirmation, not silently
  resolved.
- A new `@ConfigurationProperties` record for `themistra.auth.mfa.*` (pattern: `LockoutProperties.java`)
  — at minimum `seed-kek-arn`; `issuer-name` is also referenced by `design.md`'s illustrative config
  block (used in the `otpauth://` URI's `issuer` parameter) and belongs with `TotpGenerator`, so
  likely the same properties record.
- `application.properties` additions: `themistra.auth.mfa.issuer-name`,
  `themistra.auth.mfa.seed-kek-arn` — confirmed absent from the real file today (only shown in
  `design.md` as an illustrative sample, not yet applied).
- `pom.xml` addition: an AWS SDK v2 KMS client dependency — confirmed absent today. Exact
  coordinate/version unconfirmed (Phase 0 open item, carried forward).

**Existing, read-only:**
- `mfa_enrollments` table (V1 migration, immutable) — `secret_encrypted BYTEA` is where
  `MfaSeedEncryption`'s output eventually lands (task 17's job to wire up, not this task's).
- `LockoutProperties.java` — pattern template for the new properties record.
- `agents.md` Security section — states the D-010 exception this task implements.

**Not touched by this task:**
- `MfaEnrollment`/`MfaEnrollmentRepository` (task 17), `MfaService` (task 18), `MfaController`
  (task 19), `TotpVerifier` (code verification — task statement doesn't name it for T16; belongs
  wherever verification is first needed, likely task 18).

## Dependencies

- JDK `javax.crypto.Mac` (`HmacSHA1`) and `SecureRandom` — sufficient to implement L6's RFC 6238
  spec directly; no third-party TOTP library exists in `pom.xml` today, and none is required.
- AWS SDK v2 KMS client (`GenerateDataKey`, `Decrypt`) — new dependency, per L14/ADR-0003.
- `java.util.Base32` equivalent — JDK has no built-in Base32 encoder (only Base64); the `otpauth://`
  URI's `secret` parameter is conventionally Base32-encoded (Google Authenticator Key URI Format,
  the de facto standard L6 doesn't itself restate). Needs either a small hand-rolled encoder or a
  dependency — Phase 2 design decision, not resolved here.
- `themistra.auth.mfa.seed-kek-arn` (new config key, L13-governed) and
  `themistra.auth.mfa.issuer-name` (new config key).
- ADR-0003's envelope format (version byte, wrapped-key length + bytes, 12-byte nonce, ciphertext+tag)
  is the exact byte-for-byte contract `MfaSeedEncryption` must produce/consume.

## Acceptance Criteria

- **AC1 (R22, L6).** `TotpGenerator` produces a cryptographically random secret suitable for
  HMAC-SHA1-based RFC 6238 TOTP (JDK `SecureRandom`).
- **AC2 (R22, L6).** `TotpGenerator` builds a valid `otpauth://totp/...` provisioning URI
  containing the secret, an issuer (`themistra.auth.mfa.issuer-name`), and the algorithm/digits/
  period parameters L6 specifies (or their RFC 6238 defaults, since L6's values *are* the RFC
  defaults).
- **AC3 (R22, L14).** `MfaSeedEncryption` encrypts a seed into ADR-0003's exact envelope format
  (version `0x01` in non-`local` profiles) using a real KMS `GenerateDataKey` call, and decrypts it
  back to the original plaintext via `Decrypt`.
- **AC4 (L14, ADR-0003's local-dev fallback).** In the `local` profile with a blank
  `seed-kek-arn`, `MfaSeedEncryption` uses the version-`0x00` fixed local key instead of calling
  KMS.
- **AC5 (L13, ADR-0003).** In any non-`local` profile, a blank `seed-kek-arn` fails startup rather
  than silently falling back to the local-dev key — ADR-0003 is explicit this must never produce a
  version-`0x00` envelope outside `local`.
- **AC6 (ADR-0003's named testing obligations, verbatim).** `secret_encrypted`'s ciphertext never
  contains the raw seed; decrypt round-trips correctly for a freshly-encrypted seed; a wrong/rotated
  key fails as a KMS `Decrypt` error, not a silent bad-plaintext result.

## Tests required

- **Named test (`package.md` §8):** `shouldReturnTotpProvisioningUriOnEnrollmentBegin` — maps to
  R19 in `package.md`; confirmed drift (same pattern as every prior task: T09, T11-T15): the real
  match is R22, per this task's own header. **More significantly:** this test's name describes the
  full `POST /accounts/me/mfa/totp` enrollment-begin flow (generate, encrypt, *persist*, return
  URI) — T16 builds only generation and encryption, not persistence (task 17) or the endpoint
  (task 19). This test cannot be meaningfully satisfied end-to-end until at least task 19 exists.
  Flagged as an Open Question — T16's own test suite should cover `TotpGenerator`/
  `MfaSeedEncryption` at the unit level; the named test itself is deferred, not skipped.
- **Boundary tests implied by AC1-AC6, not separately named:** the local-dev-fallback path (AC4),
  the non-`local`-blank-ARN startup failure (AC5), and ADR-0003's three explicit testing
  obligations (AC6) — none of these have their own `package.md` names, but all are directly named
  by LOCKED text (L14 for AC4, L13/ADR-0003 for AC5, ADR-0003 for AC6), so they're required
  regardless of naming.

## Open Questions

- **Q1 (blocker for Phase 2 file-naming, not a design blocker).** `MfaSeedEncryption` (L14,
  ADR-0003, `agents.md`) vs. `TotpSeedEncryption` (`design.md`'s file-tree sketch) — recommend
  treating L14 as authoritative since it's the LOCKED decision and postdates the sketch, but this
  should get explicit confirmation before Phase 2 commits to a class name, not be silently decided.
- **Q2 (carried from Phase 0, still open).** Exact AWS SDK v2 KMS artifact/version to pin in
  `pom.xml` — needs resolving before Phase 2's Files-to-Create can be concrete about the dependency
  addition.
- **Q3 (carried from Phase 0, still open).** How to exercise the real KMS path (`GenerateDataKey`/
  `Decrypt`) in this sandbox — no LocalStack/KMS-Testcontainers module found in `pom.xml`, and
  Testcontainers itself still can't complete its Docker handshake
  ([[docker-testcontainers-handshake-issue]]). AC3/AC6 may be verifiable only via mocked KMS client
  calls at the unit level here, with the real integration path deferred to an environment where it
  can actually run. Not a blocker for design, but should be decided explicitly at Phase 2/4 rather
  than discovered mid-implementation.
- **Q4 (genuine blocker, not yet resolved by the spec author).** `package.md` §11 doesn't carry an
  open question about Base32 secret encoding for the `otpauth://` URI, and L6 doesn't specify it
  either — the Google Authenticator Key URI Format convention (Base32) is the de facto standard,
  but "de facto standard" isn't the same as "LOCKED decision." Not severe enough to block Phase 2
  (Base32 is effectively the only real option any TOTP client will accept), but worth a one-line
  confirmation rather than silently assumed as if it were spec text.
