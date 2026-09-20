# auth · T16 — Phase 4: Frozen Task Brief

**STATUS: FROZEN**

Approved by: femi (human approval gate) · 2026-08-03
Base: `artifacts/02-task-implementation-brief.md`, challenged in `artifacts/03-design-challenge.md`.
Downstream phases (5+) may not renegotiate this brief.

---

## Task

Implement `TotpGenerator` (random secret, `otpauth://` provisioning URI) and `MfaSeedEncryption`
(the KMS-envelope encryption primitive ADR-0003 fully specifies).

## Purpose

Lay the cryptographic groundwork R22 depends on. This task does not persist anything or expose an
endpoint — those are tasks 17 and 19. Two standalone, independently testable components: generate
a valid TOTP secret + provisioning URI, and encrypt/decrypt a seed per ADR-0003's exact envelope
format.

## Scope

**In:**
- `TotpGenerator`:
  - Random secret generation via `SecureRandom`, **fixed at 20 bytes / 160 bits** (RFC 6238 §5.1
    minimum; matches the Google Authenticator convention of a 32-character Base32 secret).
    *(Amendment, Finding #2 — ACCEPTED.)*
  - Base32 encoding: hand-rolled RFC 4648, **uppercase `A–Z2–7` alphabet, no padding** — the de
    facto authenticator standard, not just any RFC-4648-conformant variant.
    *(Amendment, Finding #3 — ACCEPTED.)*
  - `otpauth://totp/...` URI construction takes an **account label as an explicit input**
    (e.g. account UUID or email — Phase 6 picks the concrete value from what's available in
    context; not persistence, just a passed-in string). URI label is `<issuer>:<accountLabel>`,
    both segments URL-encoded per RFC 3986; `issuer` query param repeats the issuer. Issuer from
    `themistra.auth.mfa.issuer-name`; algorithm/digits/period params match L6 (RFC 6238 defaults).
    *(Amendment, Finding #1 — ACCEPTED. This changes `TotpGenerator`'s signature from the Phase 2
    "no external input" framing but adds no persistence or entity access — a plain string
    parameter only.)*
- `MfaSeedEncryption`: implements ADR-0003's envelope format exactly — version byte (`0x01` KMS /
  `0x00` local-dev), 2-byte big-endian wrapped-key length, wrapped data key, 12-byte GCM nonce,
  ciphertext+tag.
  - Real `GenerateDataKey`/`Decrypt` calls via a new AWS SDK v2 KMS client dependency in
    non-`local` profiles. **Uses the SDK's default AWS credential and region provider chains**
    (IRSA in EKS; no explicit keys/region in config); SDK default timeouts/retries are acceptable
    for this task. *(Amendment, Finding #8 — ACCEPTED.)*
  - The fixed local-dev key (version `0x00`) only when profile is `local` AND `seed-kek-arn` is
    blank. Per ADR-0003 (already normative, not a new decision): the key is a `private static
    final` 32-byte AES compile-time constant in `MfaSeedEncryption`, with a doc comment explicitly
    marking it local-only/unsafe for any deployed profile. *(Amendment, Finding #4 — ACCEPTED as
    restatement.)*
  - **Local-vs-non-local detection** is resolved via `Environment.acceptsProfiles("local")` (or
    equivalent membership check against the active profile set), never raw string equality —
    Spring supports multiple simultaneously active profiles. *(Amendment, Finding #6 — ACCEPTED.)*
  - **Startup guard** (AC5): a profile-aware validator (e.g. a `@PostConstruct` guard on
    `MfaProperties` or a small dedicated validator bean) that fails startup when not in `local`
    and `seed-kek-arn` is blank. Exact shape left to Phase 6; must be unit-testable without a full
    Spring context. *(Amendment, Finding #5 — ACCEPTED, light touch.)*
  - A new `Cipher.getInstance("AES/GCM/NoPadding")` instance is created **per encrypt/decrypt
    call** — `Cipher` is not thread-safe and this is a Spring singleton bean.
    *(Amendment, Finding #9 — ACCEPTED.)*
  - `decrypt` on an envelope with an unsupported version byte (anything other than `0x00`/`0x01`)
    throws a dedicated exception (e.g. `MfaEncryptionException`) — never a silent fallthrough or
    generic unchecked exception. *(Amendment, Finding #10 — ACCEPTED.)*
- New `MfaProperties`-style `@ConfigurationProperties` record (`themistra.auth.mfa.*`:
  `issuer-name`, `seed-kek-arn`), matching `LockoutProperties`'s established shape.
- New `application.properties` entries for both keys.
- New `pom.xml` dependency: `software.amazon.awssdk:bom` in `<dependencyManagement>` plus
  `software.amazon.awssdk:kms` without an explicit version (BOM-managed). **Exact BOM version
  confirmed at Phase 6 against Maven Central, not pinned here** — deliberate, not a gap.
  *(Finding #7 — REJECTED: conflates task T16 with pipeline Phase 6/Implementation; deferring the
  concrete version to the implementation phase is correct by design, since no AWS SDK version is
  cached locally to check against and guessing one here would be worse than confirming it live.)*
- Class name: **`MfaSeedEncryption`** — confirmed. ADR-0003, `auth-decisions.md` D-025, and
  `agents.md` all agree; `design.md`'s file-tree sketch (`TotpSeedEncryption`) predates the O1
  resolution and is treated as stale. *(Finding #12 / Phase 2's flagged naming question —
  CONFIRMED via `MfaSeedEncryption`.)*

**Out:**
- No `MfaEnrollment` entity/repository (task 17), no `MfaService` (task 18), no `MfaController` or
  the `POST /accounts/me/mfa/totp` endpoint (task 19). No persistence of any kind.
- No `TotpVerifier` (code verification) — R25/task 18.
- **No LocalStack/KMS-Testcontainers integration test — confirmed.** `GenerateDataKey`/`Decrypt`
  are proven via a mocked `KmsClient`. The mock contract is pinned: `GenerateDataKey` returns a
  fixed/deterministic 32-byte plaintext data key and a non-empty `CiphertextBlob`; `Decrypt` of
  that same blob returns the identical 32-byte plaintext key. The test asserts ADR-0003's exact
  byte layout independently of the mock's internals (not just "no exception thrown").
  *(Finding #13 — ACCEPTED, and Phase 2's flagged test-scope question — CONFIRMED mocked-only,
  consistent with T15's precedent and the still-open
  [[docker-testcontainers-handshake-issue]] blocking Testcontainers in this sandbox.)*
- No IAM/CDK changes — infrastructure-as-code, out of `services/auth`.

## Business Rules

- **R22** (partial — generation and encryption components only; persistence and the endpoint are
  tasks 17/19).

## Locked Decisions

- **L6.** RFC 6238, 30s step, 6 digits, HMAC-SHA1 — governs `TotpGenerator`.
- **L13.** Secrets discipline — governs `seed-kek-arn`'s validated binding and non-`local` startup
  guard.
- **L14** (ADR-0003). TOTP seed encryption — governs `MfaSeedEncryption` entirely.

## Dependencies

- JDK `javax.crypto.Mac` (`HmacSHA1`), `javax.crypto.Cipher` (`AES/GCM/NoPadding`),
  `java.security.SecureRandom` — no new dependency for the TOTP-algorithm side.
- New: `software.amazon.awssdk:kms` (via BOM) — for `MfaSeedEncryption`'s KMS calls.
- `themistra.auth.mfa.issuer-name`, `themistra.auth.mfa.seed-kek-arn` (new config keys).
- ADR-0003's envelope byte-layout table.

## Inputs

- `TotpGenerator`: `issuer-name` from config, plus an **account label** (string) passed in by the
  caller for URI construction.
- `MfaSeedEncryption.encrypt`: a raw TOTP secret (byte array). Local/non-local mode resolved
  internally via `Environment`, not passed as a raw string.
- `MfaSeedEncryption.decrypt`: a previously-produced envelope (byte array).

## Outputs

- `TotpGenerator`: the raw secret (20 bytes, for encryption) and a complete `otpauth://` URI string
  including the account-labeled, URL-encoded label.
- `MfaSeedEncryption.encrypt`: an ADR-0003-format envelope (byte array).
- `MfaSeedEncryption.decrypt`: the original raw secret, or a thrown exception for wrong-key /
  unsupported-version cases.

## State Changes

None — both components are pure/stateless aside from the KMS network call.

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
- `mfa_enrollments`/`recovery_codes` schema (V1, immutable) — read-only context.
- No entity, repository, service, or controller class in `mfa/` — those are tasks 17-19.

## Acceptance Criteria

- **AC1 (R22, L6).** `TotpGenerator` produces a cryptographically random 20-byte (160-bit) secret
  via `SecureRandom`.
- **AC2 (R22, L6).** `TotpGenerator` builds a valid `otpauth://totp/...` URI: Base32-encoded secret
  (uppercase `[A-Z2-7]{32}`, no padding), `<issuer>:<accountLabel>` label with both segments
  URL-encoded per RFC 3986, configured issuer, and L6's algorithm/digits/period parameters.
- **AC3 (R22, L14).** `MfaSeedEncryption.encrypt`/`decrypt` round-trips a seed correctly, producing
  ADR-0003's exact envelope format (version `0x01`, correct field ordering/lengths) via a mocked
  `KmsClient` whose `GenerateDataKey`/`Decrypt` contract is pinned (32-byte plaintext data key,
  non-empty `CiphertextBlob`, decrypt of that blob returns the same plaintext key).
- **AC4 (L14, ADR-0003).** In `local` profile (resolved via `Environment.acceptsProfiles`) with a
  blank `seed-kek-arn`, encryption uses the fixed version-`0x00` local key (a documented, local-only
  `private static final` constant) with no KMS call.
- **AC5 (L13, ADR-0003).** In any non-`local` profile, a blank `seed-kek-arn` fails application
  startup via a profile-aware guard.
- **AC6 (ADR-0003's named testing obligations).** The produced ciphertext never contains the raw
  seed as a substring; a decrypt with a mocked "wrong key" KMS response fails distinctly.
- **AC7 (Finding #9).** `MfaSeedEncryption` creates a new `Cipher` instance per encrypt/decrypt
  call; safe under concurrent use.
- **AC8 (Finding #10).** `decrypt` on an envelope with an unsupported version byte throws a
  dedicated exception, not a silent fallthrough.

## Required Tests

- No named test is fully satisfiable by this task alone (`shouldReturnTotpProvisioningUriOnEnrollmentBegin`
  requires persistence + the endpoint — tasks 17/19). T16's own suite covers AC1-AC8 at the unit
  level: `TotpGeneratorTest`, `MfaSeedEncryptionTest`.

## Constraints

- **Security:** the raw TOTP secret **and the full provisioning URI** (which carries the same
  secret, Base32-encoded) must never be logged. *(Amendment, Finding #11 — ACCEPTED, extends the
  existing no-log constraint.)* `MfaSeedEncryption` is the *only* class in `services/auth`
  permitted to import an AWS SDK class (ADR-0003, D-025) — must not leak into `TotpGenerator` or
  `MfaProperties`.
- **Thread-safety:** both components must be safe for concurrent use (Spring singleton beans);
  `SecureRandom` and the KMS client are thread-safe by contract; `Cipher` is not — see AC7.
- **Transaction:** not applicable — no persistence in this task.
- **Module boundaries (L12):** not exercised — no cross-module dependency, `mfa`-package-internal.
- **Null handling:** `seed-kek-arn` blank-vs-non-blank is the one null/blank-sensitive path,
  governed by AC4/AC5.

## Open Questions

None outstanding. Both Phase 2-flagged items are resolved above: `MfaSeedEncryption` naming
confirmed; mocked-KMS-only test scope confirmed (no LocalStack integration test).

## Phase 3 Findings — Disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | Missing account identifier for URI label | High | ACCEPTED |
| 2 | Secret length/entropy unpinned | Medium | ACCEPTED |
| 3 | Base32 variant unspecified | Medium | ACCEPTED |
| 4 | Local-dev key detail underspecified | Medium | ACCEPTED (restatement of existing ADR-0003 text) |
| 5 | Startup guard mechanism undescribed | Medium | ACCEPTED (light touch, mechanism left to Phase 6) |
| 6 | Active-profile detection ambiguous | Medium | ACCEPTED |
| 7 | AWS SDK BOM version deferred to Phase 6 | Medium | **REJECTED** — conflates task T16 with pipeline Phase 6 (Implementation); deferring the exact version to that phase is correct by design |
| 8 | KMS client region/credentials unspecified | Medium | ACCEPTED (documented as SDK default chain) |
| 9 | `Cipher` reuse not ruled out | Medium | ACCEPTED |
| 10 | Unknown envelope version handling undefined | Low-Medium | ACCEPTED |
| 11 | Provisioning URI is secret material | Low | ACCEPTED |
| 12 | `MfaSeedEncryption` naming | Low | CONFIRMED (`MfaSeedEncryption`) |
| 13 | Mocked-KMS test scope underspecified | Low | ACCEPTED |

**Note (non-blocking, out of scope for T16):** `spec/auth-service/package.md` §8's traceability
table maps `shouldReturnTotpProvisioningUriOnEnrollmentBegin` → R19, but `requirements.md` R19 is
the lockout-counter-decay requirement — R22 (TOTP provisioning URI) is the semantically correct
match, and is what this brief and the T16 prompt header use. Appears to be a stale numbering error
in `package.md` itself, unrelated to T16's design. Not corrected here (spec files are out of scope
for this task); flagged for separate follow-up.
