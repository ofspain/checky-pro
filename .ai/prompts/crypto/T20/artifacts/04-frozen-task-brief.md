STATUS: FROZEN

# crypto · T20 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 15 findings accepted. The 3 High-severity findings were independently verified against actual
source before acceptance (`services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`,
`services/auth/src/main/java/com/themistra/auth/mfa/MfaSeedEncryption.java`, and
`docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md`) — not accepted on Kimi's assertion alone.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | `@ArchTest` fields are not executed under this repo's Surefire setup | **ACCEPTED** | Verified via `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`'s own documented Phase 8 finding (a deliberately-introduced violation did not fail `mvn test`). Add a plain JUnit `@Test` canary (e.g. `shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild`) that imports the same classes and calls the rule's `check(...)` directly, mirroring auth's exact established fix. Keep the `@ArchTest` field for documentation only. |
| 2 | The rule doesn't stop another class *inside* `attest` from calling `kms:Sign` | **ACCEPTED** | Add a second condition: only `attest.KmsSigner` itself (not "any class in `attest`") may call `KmsClient.sign(...)` or construct a `SignRequest`. |
| 3 | Scanning `com.themistra.*` would break on auth-service's own, separately-governed KMS usage | **ACCEPTED** | Verified: `MfaSeedEncryption.java` legitimately imports `KmsClient` directly under ADR-0003, and ADR-0004 explicitly scopes this task's rule to `services/crypto` only. The analyzed package is `com.themistra.crypto`, never `com.themistra`. |
| 4 | `SignatureResult.kmsKeyId()`'s source is unspecified | **ACCEPTED** | `kmsKeyId()` = `SignResponse.keyId()` (the canonical identifier KMS itself asserts it signed with), not blindly `KmsProperties.keyId()` echoed back. Task 22 (verification-keys endpoint) must publish keys under this same identifier. |
| 5 | Base64 variant unspecified | **ACCEPTED** | Standard RFC 4648 Base64, `java.util.Base64.getEncoder()` — no URL-safe variant, no line-wrapping. |
| 6 | No injectable `Clock` for `signedAt` | **ACCEPTED** | `KmsSigner` takes `Clock` as a constructor dependency; `signedAt = clock.instant()`. |
| 7 | `ECDSA_SHA_256` is asserted while Q7 is still open | **ACCEPTED** | Kept as a single named constant (`SIGNING_ALGORITHM`) in `KmsSigner`, Javadoc-flagged as pending Q7's final confirmation — not added to the frozen `KmsProperties` record. Tests reference the constant, never a duplicated literal. |
| 8 | No-region `KmsClient` config defers a startup-fail-fast guarantee to runtime | **ACCEPTED, CONTINGENT ON VERIFICATION** | AWS SDK v2 resolves region eagerly inside `.build()`, throwing `SdkClientException` immediately if none can be determined — this is called during eager `@Bean` construction at Spring startup, so it plausibly already satisfies `agents.md`'s "fail at startup" rule without a new validator. **To be confirmed by direct execution in Phase 6, not assumed** — if verification shows otherwise, a `@PostConstruct` check will be added. |
| 9 | Named test says "invoke," the rule only checked "depend" | **ACCEPTED** | Subsumed by Finding #2's fix — the combined external-dependency ban plus the new internal-sole-caller condition together do assert that only the attest path may *invoke* `kms:Sign`, matching the named test's literal wording. |
| 10 | AC3 (no key material) / AC6 (no host-only assumptions) have no test | **ACCEPTED** | Two new structural source-scan tests: one asserting `KmsSigner`/`KmsSignerConfig` import none of `java.security.KeyPairGenerator`/`KeyStore`/`SecretKey`-family types (AC3); one asserting no hardcoded file path, hostname, or credential literal appears in either file (AC6). |
| 11 | No test that the digest/signature are never logged | **ACCEPTED, STRENGTHENED** | Rather than a log-capture test proving a negative, `KmsSigner` declares **no `Logger` field at all** — the strongest available guarantee, since a class with no logger cannot log sensitive material by construction. A structural test asserts this directly (mirrors `ScreeningResultTest`'s own "no public setters" reflection-test style). |
| 12 | Non-32-byte digest behavior undefined | **ACCEPTED** | `sign(...)` throws `IllegalArgumentException` immediately if `digestSha256.length != 32`, before any KMS call. |
| 13 | Contingent LocalStack test needs an endpoint-override story | **ACCEPTED (documentation only)** | If Phase 5/6 verification confirms LocalStack's asymmetric KMS support, the test must use a `@TestConfiguration`-style override (`endpointOverride`, region, `AwsBasicCredentials` from the container) mirroring `ObservationSnapshotStoreLocalStackIntegrationTest`'s exact pattern, and `Assumptions.assumeTrue(...)` (never a fake pass) if support is absent. |
| 14 | ArchUnit's class import may exclude test sources | **ACCEPTED** | The Phase 6 canary's class import must include `src/test/java`, not only `src/main/java` (i.e., no `ImportOption.DoNotIncludeTests`), so a test class outside `attest` importing the KMS SDK also fails the rule. |
| 15 | `SignatureResult`'s visibility could itself leak the boundary | **ACCEPTED** | `SignatureResult` is a package-private record — its only legitimate consumer, the future `AttestationService` (task 21), lives in the same `attest` package per `design.md`'s own package map. |

## Task

Implement `attest.KmsSigner`, the sole class in `services/crypto` permitted to call AWS KMS's `Sign`
operation on the attestation key, and an ArchUnit-plus-canary rule proving no other package — and no
other class within `attest` — can reach it.

## Purpose

Concentrate the platform's single highest-consequence capability — producing a Themistra attestation
signature — behind one auditable, structurally-enforced choke point (L11), so "an attacker makes
Themistra attest to something false" cannot be achieved by finding a second code path to the signing key.

## Scope

**In:**
- `attest.KmsSigner` — one public method, `SignatureResult sign(byte[] digestSha256)`.
  - Rejects `null` (`NullPointerException`) and any length other than 32 bytes
    (`IllegalArgumentException`) before any KMS call.
  - Calls `KmsClient.sign(...)` with `MessageType.DIGEST`, `KeyId = KmsProperties.keyId()`, and
    `SigningAlgorithmSpec` from a single named constant `SIGNING_ALGORITHM = ECDSA_SHA_256`
    (Javadoc-flagged as pending Q7's final confirmation).
  - Maps the response into `SignatureResult(signatureBase64, kmsKeyId, signedAt)`:
    `signatureBase64` via `Base64.getEncoder()` over `SignResponse.signature()`'s bytes; `kmsKeyId` from
    `SignResponse.keyId()` (not the input config value); `signedAt` from an injected `Clock`.
  - Declares **no `Logger` field** — the guarantee against ever logging the digest or signature.
  - Any `KmsClient.sign(...)` exception propagates uncaught (signing is the load-bearing deliverable of
    the future `/attest` endpoint, unlike `ObservationSnapshotStore`'s supplementary-S3-durability
    swallow-to-`Optional.empty()` posture, which does not apply here).
- `attest.SignatureResult` — package-private record, `(String signatureBase64, String kmsKeyId, Instant signedAt)`.
- `attest.KmsSignerConfig` — builds the `KmsClient` bean (fixed `ClientOverrideConfiguration` API-call
  timeout, no explicit credentials, no explicit `.region(...)` — see Finding #8's contingent
  verification) and the `KmsSigner` bean.
- The named rule, implemented as two ArchUnit conditions plus a plain-JUnit canary
  (`attest.KmsSignerArchitectureTest`):
  1. No class outside `com.themistra.crypto.attest` may depend on `attest.KmsSigner` or any type under
     `software.amazon.awssdk.services.kms..`.
  2. Within `com.themistra.crypto.attest`, only `KmsSigner` itself may call `KmsClient.sign(...)` or
     construct a `SignRequest`.
  3. A plain `@Test` canary invokes both rules' `check(...)` directly against classes imported from
     **both** `src/main/java` and `src/test/java` (no `DoNotIncludeTests`), scoped to
     `com.themistra.crypto` only (never `com.themistra`, which would break on auth-service's own,
     separately-ADR-0003-governed KMS usage).
- Two structural source-scan tests: no key-material-handling API imported (AC3), no hardcoded
  host-only path/hostname/credential literal (AC6).

**Out:**
- Wiring `KmsSigner` into any endpoint or service (task 21).
- The `Attestation` entity/repository and any persistence of the signing outcome (task 21).
- The verification-keys well-known endpoint (task 22).
- Resolving Q7 definitively — the real key's provisioning is AWS CDK infrastructure, out of this
  codebase; this task's algorithm constant is a documented, reviewable placeholder, not a final answer.
- A full outbound module-boundary test for everything `attest` itself may import — explicitly deferred
  to task 25 by ADR-0004 itself ("enforced first narrowly by task T20's own `KmsSigner`-reference
  ArchUnit rule, then consolidated into the module's full boundary suite in task T25").
- Adding a `region`/`signingAlgorithm` field to the frozen `KmsProperties` record (T03) — both stay out
  of that file per Findings #7/#8's dispositions.

## Business Rules

- **R22.** `kms:Sign` on the attestation key is invoked only from the attest path; no other module,
  endpoint, or class within `attest` other than `KmsSigner` itself can reach the signer.

## Locked Decisions

- **L11.** KMS-only signing, single path: the key never leaves KMS; `kms:Sign` is reachable only from
  `attest` (now: only from `KmsSigner` specifically within `attest`), enforced by ArchUnit+canary (this
  task) and IAM (infrastructure, out of scope); the sign path makes no host-only assumption
  (Nitro-Enclave-portable).

## Dependencies

`common.config.KmsProperties` (read-only — `keyId()`); `java.time.Clock` (new — Finding #6);
`software.amazon.awssdk.services.kms.KmsClient`; `com.tngtech.archunit:archunit-junit5:1.3.0`.

## Inputs

A 32-byte SHA-256 digest (`byte[]`), matching the future `/attest` request's `receiptDigestSha256` field
once hex-decoded by its (not-yet-built) controller.

## Outputs

`SignatureResult(signatureBase64, kmsKeyId, signedAt)` — no persistence, no event.

## State Changes

None.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/attest/KmsSigner.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/SignatureResult.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/KmsSignerConfig.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java`
- (Contingent on Phase 5/6's direct verification that LocalStack's KMS emulation supports asymmetric
  `SIGN_VERIFY` keys and `Sign` at the pinned image version)
  `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerLocalStackIntegrationTest.java`

## Files to Modify

None.

## Files NOT to Modify

- `common/config/KmsProperties.java` — frozen (T03); no `region`/`signingAlgorithm` field added.
- `observation/ObservationSnapshotStoreConfig.java` / `ObservationSnapshotStore.java` — pattern
  precedent only.
- `services/auth/**` — out of this service's scope entirely; auth's own `MfaSeedEncryption`/ADR-0003 KMS
  usage is untouched and unaffected by this task's rule (Finding #3).
- Any file under `spec/` or `docs/adr/`.
- `contracts/`.

## Acceptance Criteria

- **AC1 (R22/L11).** The ArchUnit rule (both conditions) plus its canary fail if any class outside
  `com.themistra.crypto.attest` depends on `attest.KmsSigner`/any `kms..` SDK type, or if any class
  inside `attest` other than `KmsSigner` calls `KmsClient.sign(...)`; both pass on the current, clean
  codebase, scanning both main and test sources, scoped to `com.themistra.crypto` only.
- **AC2 (L11).** `sign(...)` calls `KmsClient.sign(...)` with `MessageType.DIGEST`, the `SIGNING_ALGORITHM`
  constant, and `KmsProperties.keyId()`; maps the response per Findings #4/#5/#6's resolutions.
- **AC3 (L11, no key-material handling), automated.** A structural source-scan test proves `KmsSigner`/
  `KmsSignerConfig` import no key-material-handling API.
- **AC4 (scope).** A `KmsClient.sign(...)` failure propagates out of `KmsSigner.sign(...)` uncaught.
- **AC5 (null/length handling).** `sign(null)` throws `NullPointerException`; `sign(new byte[16])` (or
  any non-32 length) throws `IllegalArgumentException` — both before any KMS call.
- **AC6 (Nitro-Enclave portability, L11), automated.** A structural source-scan test proves no host-only
  literal (path, hostname, credential) appears in either file.
- **AC7 (Finding #11).** A structural test proves `KmsSigner` declares no `Logger` field.

## Required Tests

- `shouldOnlyAllowAttestPathToInvokeKmsSign` (named test) + its plain-`@Test` canary —
  `KmsSignerArchitectureTest` (AC1).
- A test that `sign(...)` calls `KmsClient.sign(...)` with the expected `MessageType`, `SIGNING_ALGORITHM`
  constant, and `keyId`, and correctly maps a successful `SignResponse` into `SignatureResult`'s three
  fields — including that `kmsKeyId` comes from the response, not the input config, and `signatureBase64`
  is standard-Base64 (AC2).
- A test that a `KmsClient.sign(...)` exception propagates unwrapped (AC4).
- A test that `sign(null)` throws `NullPointerException` and `sign(<wrong-length>)` throws
  `IllegalArgumentException`, neither calling `KmsClient` at all (AC5).
- Two structural source-scan tests for AC3/AC6.
- A structural test asserting `KmsSigner` has no `Logger`/`log` field (AC7).
- (Contingent) a LocalStack-backed real-API test, or a documented, `Assumptions`-gated skip if LocalStack
  cannot support it at the pinned version.

## Constraints

- **Performance:** a single synchronous KMS call per invocation; no polling, no added retry logic beyond
  the KMS SDK's own default.
- **Security:** no `Logger` field in `KmsSigner` at all (Finding #11); `KmsProperties.keyId()` identifies
  a key, it is not secret material.
- **Thread-safety:** `KmsSigner`/`KmsSignerConfig`'s beans are stateless singletons; `KmsClient` is
  documented thread-safe by AWS.
- **Transaction:** none.
- **Module boundaries:** enforced by the two-condition ArchUnit rule plus canary (AC1); a full outbound
  boundary test for `attest` is explicitly deferred to task 25 (ADR-0004).
- **Null/length handling:** `sign(byte[] digestSha256)` rejects `null` and any length other than 32
  before any KMS call (AC5).

## Open Questions

No blockers. Q7 (KMS key type/algorithm) remains formally open at the specification level; this task's
`SIGNING_ALGORITHM` constant is a documented, reviewable placeholder pending its final resolution, not a
claim that Q7 is closed.
