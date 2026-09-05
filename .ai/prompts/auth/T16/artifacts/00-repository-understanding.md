# auth · T16 — Phase 0: Repository Understanding

## 1. Architecture summary

Unchanged platform-wide facts (Spring Boot 3.5.4/Java 21, package-by-feature, Postgres/Flyway,
Kafka outbox, `Clock`-injected time) — see `agents.md`, restated nowhere else. New to this task:
this is the **first task to require an AWS SDK dependency** in `services/auth` — `agents.md`'s
"Security" section already carves out the exception this task implements: "no AWS SDK code in the
service, **except** a single scoped KMS `GenerateDataKey`/`Decrypt` call inside
`mfa.MfaSeedEncryption`... (D-010 exception... L14, D-025)." That exception exists specifically
*for* T16 — this isn't a new decision to make, it's already been made and recorded (ADR-0003,
`auth-decisions.md` D-025, `design.md` L14), narrowly scoped to one class.

## 2. Existing code this task touches

**Schema — already exists, V1, immutable, task builds against it as-is:**
- `mfa_enrollments` table (`V1__auth_baseline_schema.sql:24-33`): `account_id`, `type VARCHAR(16)
  DEFAULT 'TOTP'`, `secret_encrypted BYTEA NOT NULL` (comment: "AES-GCM, KMS-enveloped data key" —
  ADR-0003 confirms this comment already accurately describes the chosen design, no migration
  needed), `confirmed_at`, `last_used_at`, `created_at`, unique `(account_id, type)`.
- `recovery_codes` table (`V1:35-42`): `account_id`, `code_hash CHAR(64)` (SHA-256), `used_at`,
  `created_at`. Belongs to T17 (MfaEnrollment entity/repository), not this task — T16 only writes
  `secret_encrypted` via the encryption primitive it builds; it does not touch `recovery_codes`.

**Package — exists but empty:**
- `com.themistra.auth.mfa` — only `package-info.java` exists. No `TotpGenerator`,
  `MfaSeedEncryption`, entity, repository, service, or controller yet. No test package exists
  either (`src/test/java/com/themistra/auth/mfa` doesn't exist).

**Not yet present — this task must add:**
- **AWS SDK dependency.** `pom.xml` has zero AWS/KMS dependencies today (confirmed via full
  `<artifactId>` grep). ADR-0003 requires `kms:GenerateDataKey`/`kms:Decrypt` calls — the AWS SDK
  v2 KMS client (`software.amazon.awssdk:kms` per current AWS SDK v2 convention, unconfirmed exact
  artifact/version — Phase 1/2 to verify) isn't in the module yet.
- **TOTP library or hand-rolled RFC 6238.** No TOTP/HOTP dependency exists in `pom.xml` either. L6
  is fully specified (RFC 6238, 30s step, 6 digits, HMAC-SHA1) — implementable directly with JDK
  `javax.crypto.Mac`("HmacSHA1") without a third-party library, or via a small dependency. Which
  approach is a Phase 2 design question, not decided here.
- **`themistra.auth.mfa.seed-kek-arn` config property**, referenced by ADR-0003 ("blank ⇒ `local`
  profile, non-blank ⇒ real ARN; non-`local` profiles must refuse a blank value") — doesn't exist
  in `application.properties` yet.

## 3. Established patterns to follow

- **`@ConfigurationProperties` records:** `LockoutProperties.java` is the clearest template —
  `@ConfigurationProperties(prefix = "themistra.auth.X")` + `@Validated` record with Jakarta
  Validation annotations (`@Min`, etc.), startup-fails-on-invalid per `agents.md`'s Configuration
  rule. A new `MfaProperties`-style record (or similar) for `seed-kek-arn` should follow this
  exact shape.
- **ADR-0003's byte-layout table is the encryption format spec, verbatim** — version byte (`0x01`
  KMS / `0x00` local-dev-only), 2-byte big-endian wrapped-key length, wrapped data key (KMS
  `CiphertextBlob`), 12-byte GCM nonce, ciphertext+16-byte tag. This is not something to redesign;
  it's already fully specified and just needs implementing.
- **Local-dev fallback:** version-`0x00` envelopes, no KMS call, a fixed 32-byte constant key,
  gated on `local` profile AND blank `seed-kek-arn` — ADR-0003 is explicit this must never occur in
  `dev`/`staging`/`prod`, enforced by the same config-startup-guard pattern L13/`agents.md` already
  establish elsewhere (e.g. `PasswordPolicyProperties`-style validated binding).
- **Money/`BigDecimal`, outbox, idempotent consumers:** not applicable — no monetary values, no
  event publication named by R22 (enrollment-begin doesn't fire a lifecycle event per
  `requirements.md`'s exact text — confirm at Phase 1, not assumed here).

## 4. Testing conventions

- Unchanged: plain JUnit + Mockito + fixed `Clock`, no Spring context, for unit tests;
  `@SpringBootTest` + Testcontainers for integration. ADR-0003's own "Testing implications" note
  (for task #16, explicit) names three regression properties any test suite here should prove:
  `secret_encrypted` never contains the raw seed in plaintext form, decrypt round-trips correctly
  for a freshly-enrolled seed, and a wrong/rotated key fails as a KMS `Decrypt` error rather than
  silently producing bad plaintext. These aren't optional nice-to-haves — ADR-0003 names them
  directly as this task's testing obligation.
- **Testcontainers/Docker status, current as of T15:** Docker is present in this sandbox but
  Testcontainers' Java client cannot complete its handshake (diagnosed T15, documented in memory as
  `docker-testcontainers-handshake-issue`) — still unresolved. **Additional wrinkle specific to
  T16:** even if Testcontainers worked, KMS itself has no LocalStack/Testcontainers module wired
  into this project today (unconfirmed — not found in `pom.xml`) — so a `GenerateDataKey`/`Decrypt`
  round-trip test would need either a fake/mock KMS client, a LocalStack container (new
  infrastructure), or reliance on the local-dev fallback path (version `0x00`) for anything
  runnable in this sandbox. This is a bigger environment question than T11-T15 faced and should be
  flagged early, not discovered mid-implementation.

## 5. Known gaps / unknowns

- **I do not know** which AWS SDK v2 KMS artifact/version to pin, or whether the parent POM already
  manages an AWS SDK BOM anywhere in the monorepo (unconfirmed — only checked `services/auth/pom.xml`
  directly, not the root parent POM or sibling services). Worth checking before Phase 2 commits to
  a specific dependency coordinate.
- **I do not know** whether this task is expected to also add IRSA/CDK-level KMS permissions
  (ADR-0003 mentions "the `auth-service` IRSA role gains `kms:GenerateDataKey` and `kms:Decrypt`")
  — that's infrastructure-as-code (CDK, TypeScript, per `agents.md`'s Deployment section), likely
  out of scope for `services/auth` Java code, but worth confirming at Phase 1/2 rather than
  assuming.
- **Confirmed, not a gap:** `contracts/api/auth.yaml` still doesn't exist (tracked since T11,
  irrelevant to this task's actual scope so far — R22's provisioning-URI response shape isn't
  contract-governed today).
- **Confirmed, not a gap:** the header's scoped LOCKED decisions list `L6, L13` only — but `L14`
  (TOTP seed encryption) is clearly and directly governing for this task and must be treated as
  in-scope too, per this pipeline's own "widen the starting set only if the task clearly requires
  it" rule. Flagging this now rather than silently under-scoping at Phase 1.
