# auth · T16 — Phase 2: Task Implementation Brief

## Task

Implement `TotpGenerator` (random secret, `otpauth://` provisioning URI) and `MfaSeedEncryption`
(the KMS-envelope encryption primitive L14/ADR-0003 already fully specifies).

## Purpose

Lay the cryptographic groundwork R22 depends on. This task does not persist anything or expose an
endpoint — those are tasks 17 and 19. T16's job is two standalone, independently testable
components: generate a valid TOTP secret + provisioning URI, and encrypt/decrypt a seed per
ADR-0003's exact envelope format.

## Scope

**In:**
- `TotpGenerator`: random secret generation (`SecureRandom`, sufficient entropy for HMAC-SHA1 per
  L6), Base32 encoding (hand-rolled RFC 4648, no existing dependency — the Google Authenticator Key
  URI Format convention L6 doesn't restate but every real TOTP client expects), and `otpauth://totp/...`
  URI construction (issuer from `themistra.auth.mfa.issuer-name`, algorithm/digits/period params
  matching L6, which are the RFC 6238 defaults).
- `MfaSeedEncryption`: implements ADR-0003's envelope format exactly — version byte (`0x01` KMS /
  `0x00` local-dev), 2-byte big-endian wrapped-key length, wrapped data key, 12-byte GCM nonce,
  ciphertext+tag. Real `GenerateDataKey`/`Decrypt` calls via a new AWS SDK v2 KMS client dependency
  in non-`local` profiles; the fixed local-dev key (version `0x00`) only when profile is `local`
  AND `seed-kek-arn` is blank.
- New `MfaProperties`-style `@ConfigurationProperties` record (`themistra.auth.mfa.*`:
  `issuer-name`, `seed-kek-arn`), matching `LockoutProperties`'s established shape. Startup fails
  in non-`local` profiles if `seed-kek-arn` is blank (L13, ADR-0003).
- New `application.properties` entries for both keys (currently absent from the real file — only
  shown as an illustrative sample in `design.md`).
- New `pom.xml` dependency: `software.amazon.awssdk:bom` in `<dependencyManagement>` (a recent 2.x
  release — exact version confirmed at Phase 6 implementation time against Maven Central, not
  guessed here; no AWS SDK KMS module is cached in this environment's `~/.m2` to infer a version
  from) plus `software.amazon.awssdk:kms` without an explicit version, letting the BOM manage it —
  the AWS-recommended pattern, and it sidesteps pinning a single fragile version number by hand.
- Class name: **`MfaSeedEncryption`**, per L14 (LOCKED, dated 2026-07-22) and ADR-0003/D-025/`agents.md`,
  all of which agree — not `TotpSeedEncryption` from `design.md`'s file-tree sketch, which predates
  the O1 resolution and is the odd one out among four sources. Flagged for explicit Phase 4
  confirmation since it's a naming call affecting a LOCKED-decision-named class, not decided
  silently.

**Out:**
- No `MfaEnrollment` entity/repository (task 17), no `MfaService` (task 18), no `MfaController` or
  the actual `POST /accounts/me/mfa/totp` endpoint (task 19). No persistence of any kind — T16's
  components are called by *future* tasks, not wired to anything yet.
- No `TotpVerifier` (code verification) — not named by this task's statement; R22 only covers
  generation, not verification (that's R25, task 18).
- **No LocalStack/KMS-Testcontainers integration test.** The real `GenerateDataKey`/`Decrypt` calls
  are proven via a mocked `KmsClient` (AWS SDK v2 clients are interfaces, directly Mockito-mockable)
  asserting the request/response contract and ADR-0003's byte layout — not a live KMS call. Reasons:
  no LocalStack module exists in this project today (adding one is new infrastructure, disproportionate
  to this task), and Testcontainers itself still can't complete its Docker handshake in this sandbox
  even with Docker present ([[docker-testcontainers-handshake-issue]]). Matches T15's own precedent
  of choosing a fully-executable unit-level proof over an unexecutable integration test. Flagged for
  explicit Phase 4 confirmation, same treatment as the naming call above.
- No IAM/CDK changes (ADR-0003 mentions IRSA role permissions, but that's infrastructure-as-code,
  out of `services/auth`'s Java code and this task's file set).

## Business Rules

- **R22** (partial — generation and encryption components only; persistence and the endpoint are
  tasks 17/19).

## Locked Decisions

- **L6.** RFC 6238, 30s step, 6 digits, HMAC-SHA1 — governs `TotpGenerator`.
- **L13.** Secrets discipline — governs `seed-kek-arn`'s validated binding and non-`local` startup
  guard.
- **L14** (widened at Phase 0/1, not in the task header's scoped list). TOTP seed encryption —
  governs `MfaSeedEncryption` entirely; ADR-0003 is the byte-for-byte implementation spec.

## Dependencies

- JDK `javax.crypto.Mac` (`HmacSHA1`), `java.security.SecureRandom` — no new dependency for the
  TOTP-algorithm side.
- New: `software.amazon.awssdk:kms` (via BOM) — for `MfaSeedEncryption`'s KMS calls.
- `themistra.auth.mfa.issuer-name`, `themistra.auth.mfa.seed-kek-arn` (new config keys).
- ADR-0003's envelope byte-layout table — the exact contract `MfaSeedEncryption` implements.

## Inputs

- `TotpGenerator`: no external input — generates its own secret and reads `issuer-name` from config.
- `MfaSeedEncryption.encrypt`: a raw TOTP secret (byte array), the active Spring profile, and
  `seed-kek-arn` (via config). `MfaSeedEncryption.decrypt`: a previously-produced envelope
  (byte array).

## Outputs

- `TotpGenerator`: the raw secret (for encryption) and a complete `otpauth://` URI string.
- `MfaSeedEncryption.encrypt`: an ADR-0003-format envelope (byte array), ready for
  `mfa_enrollments.secret_encrypted` (task 17 to actually persist it — not this task).
- `MfaSeedEncryption.decrypt`: the original raw secret.

## State Changes

None — both components are pure/stateless aside from the KMS network call; no persistence in this
task.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/mfa/TotpGenerator.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaSeedEncryption.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaProperties.java`

## Files to Modify

- `services/auth/src/main/resources/application.properties` — add the two new keys.
- `services/auth/pom.xml` — add the AWS SDK BOM + KMS dependency.

## Files NOT to Modify

- Anything under `com.themistra.auth.account`, `.authn`, `.authz`, `.apikey`, `.audit`, `.events`,
  `.token`, `.common` — no other module touched.
- `mfa_enrollments`/`recovery_codes` schema (V1, immutable) — read-only context, not altered.
- No entity, repository, service, or controller class in `mfa/` — those are tasks 17-19.

## Acceptance Criteria

- **AC1 (R22, L6).** `TotpGenerator` produces a cryptographically random secret via `SecureRandom`.
- **AC2 (R22, L6).** `TotpGenerator` builds a valid `otpauth://totp/...` URI with a Base32-encoded
  secret, the configured issuer, and L6's algorithm/digits/period parameters.
- **AC3 (R22, L14).** `MfaSeedEncryption.encrypt`/`decrypt` round-trips a seed correctly, producing
  ADR-0003's exact envelope format (version `0x01`, correct field ordering/lengths) via a mocked
  `KmsClient`.
- **AC4 (L14, ADR-0003).** In `local` profile with a blank `seed-kek-arn`, encryption uses the
  fixed version-`0x00` local key with no KMS call.
- **AC5 (L13, ADR-0003).** In any non-`local` profile, a blank `seed-kek-arn` fails application
  startup.
- **AC6 (ADR-0003's named testing obligations).** The produced ciphertext never contains the raw
  seed as a substring; a decrypt with a mocked "wrong key" KMS response fails distinctly (not a
  silently-wrong plaintext).

## Required Tests

- No named test is fully satisfiable by this task alone (`shouldReturnTotpProvisioningUriOnEnrollmentBegin`
  requires persistence + the endpoint — tasks 17/19; see Phase 1's Open Questions). T16's own test
  suite covers AC1-AC6 at the unit level: `TotpGeneratorTest`, `MfaSeedEncryptionTest`.

## Constraints

- **Security:** the raw TOTP secret must never be logged (matches `agents.md`'s "never log tokens,
  secrets, or emails"). `MfaSeedEncryption` is the *only* class in `services/auth` permitted to
  import an AWS SDK class (ADR-0003, D-025) — this must not leak into `TotpGenerator` or
  `MfaProperties`.
- **Thread-safety:** both components must be safe for concurrent use (Spring singleton beans);
  `SecureRandom` and the KMS client are both thread-safe by contract.
- **Transaction:** not applicable — no persistence in this task.
- **Module boundaries (L12):** not exercised — no cross-module dependency, `mfa`-package-internal.
- **Null handling:** `seed-kek-arn` blank-vs-non-blank is the one null/blank-sensitive path,
  governed by AC4/AC5 above — no other null-argument case is in scope.

## Open Questions

No blockers. Two scope decisions (the `MfaSeedEncryption` naming, and mocked-KMS-only test
coverage in place of a LocalStack integration test) are deliberate, reasoned calls made above —
flagged for explicit Phase 4 confirmation rather than treated as silently settled, matching how
T15 handled its own unit-vs-integration scope decision.
