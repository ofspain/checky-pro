# crypto · T20 · Phase 2 — Task Implementation Brief

## Task

Implement `attest.KmsSigner`, the sole class in `services/crypto` permitted to call AWS KMS's `Sign`
operation on the attestation key, and an ArchUnit rule proving no other package can reach it (directly,
or via the KMS SDK's signing surface).

## Purpose

Concentrate the platform's single highest-consequence capability — producing a Themistra attestation
signature — behind one auditable, structurally-enforced choke point (L11), so "an attacker makes
Themistra attest to something false" (`package.md`'s own core threat framing) cannot be achieved by
finding a second code path to the signing key.

## Scope

**In:**
- `attest.KmsSigner` — one public method, `SignatureResult sign(byte[] digestSha256)`. Accepts an
  already-computed 32-byte SHA-256 digest (matching `design.md` §4c's wire field
  `receiptDigestSha256: "<hex>"` — hex-decoding is the future HTTP layer's job, task 21's, not this
  class's). Calls `KmsClient.sign(...)` with `MessageType.DIGEST` and
  `SigningAlgorithmSpec.ECDSA_SHA_256` (**proposed**, informed by the digest already being fixed as
  SHA-256 in the wire contract; Q7, the KMS key type itself, remains formally unanswered — this proposal
  is submitted for Phase 3/4 challenge and human confirmation, not asserted as settled). Returns
  `SignatureResult(String signatureBase64, String kmsKeyId, Instant signedAt)` — a new record local to
  `attest`, deliberately not the same shape as the eventual `/attest` HTTP response (task 21 adds
  `outcome`, decides `BLOCKED`/`REFUSED`/`409` — none of that is this class's concern; `KmsSigner` only
  ever succeeds or throws).
- `attest.KmsSignerConfig` — builds the `KmsClient` bean, mirroring
  `ObservationSnapshotStoreConfig`'s established shape (a fixed `ClientOverrideConfiguration` API-call
  timeout, no explicit credentials). **Deviation from that precedent, proposed and flagged:** no explicit
  `.region(...)` call — `KmsProperties` (T03, frozen) has no `region()` field, and adding one would mean
  reopening an already-shipped config record for a single field this task alone would use; the AWS SDK's
  own default region provider chain (env var / instance metadata / IRSA) is a legitimate, spec-compliant
  fallback and keeps `KmsProperties` untouched.
- The named ArchUnit rule (`shouldOnlyAllowAttestPathToInvokeKmsSign`) — no class outside
  `com.themistra.crypto.attest` may depend on `attest.KmsSigner` or any type under
  `software.amazon.awssdk.services.kms..` A new test class, `attest.KmsSignerArchitectureTest` (first
  real use of the already-declared, never-yet-used `com.tngtech.archunit:archunit-junit5` dependency).
- Exceptions from `KmsClient.sign(...)` (e.g. `KmsException`, network/SDK failures) **propagate
  uncaught** — deliberately the opposite posture from `ObservationSnapshotStore`'s own S3-failure
  handling (which swallows into `Optional.empty()`, because S3 there is "a supplementary durability
  layer, not a blocking dependency"). Signing is the load-bearing deliverable of the future `/attest`
  endpoint, not supplementary — a failure must be visible to whatever calls `KmsSigner`, not silently
  downgraded.

**Out:**
- Wiring `KmsSigner` into any endpoint or service — no `AttestController`/`AttestationService` exists
  yet (task 21).
- The `Attestation` entity/repository and any persistence of the signing outcome — task 21's own
  `attest/` package-map entry, listed after `KmsSigner.java`; this task persists nothing.
- The verification-keys well-known endpoint and its `alg`/`publicKeyPem` publication — task 22.
- Resolving Q7 definitively (the actual KMS key's type/algorithm) — this task proposes a
  digest-format-compatible algorithm for the *call shape*, but the real key's creation/provisioning is
  infrastructure (AWS CDK, out of this codebase) and Q7's final vendor/spec confirmation is an author
  decision, not this task's to force closed.
- An outbound module-boundary test for what `attest` itself may import (mirroring
  `ScreeningModuleBoundaryTest`'s style) — `attest/` contains exactly one trivial file
  (`KmsSigner` + its config), needing only `common.config.KmsProperties` and the KMS SDK; a full
  "what may `attest` import" test now would be premature and largely redundant with the ArchUnit rule
  already required, which is the task statement's own explicit, singular ask.

## Business Rules

- **R22.** `kms:Sign` on the attestation key is invoked only from the attest path; no other module or
  endpoint can reach the signer.

## Locked Decisions

- **L11.** KMS-only signing, single path: the key never leaves KMS; `kms:Sign` is reachable only from
  `attest`, enforced by ArchUnit (this task) and IAM (infrastructure, out of scope); the sign path makes
  no host-only assumption (Nitro-Enclave-portable).

## Dependencies

`common.config.KmsProperties` (read-only — `keyId()`); `software.amazon.awssdk.services.kms.KmsClient`
(new usage); `com.tngtech.archunit:archunit-junit5` (new usage, test-scope, already declared).

## Inputs

A 32-byte SHA-256 digest (`byte[]`), matching the future `/attest` request's `receiptDigestSha256` field
once hex-decoded by its (not-yet-built) controller.

## Outputs

`SignatureResult(signatureBase64, kmsKeyId, signedAt)` — no persistence, no event.

## State Changes

None. `KmsSigner` reads no table, writes no table, publishes no event.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/attest/KmsSigner.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/KmsSignerConfig.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java`
- (Contingent on Phase 5's direct verification that LocalStack's KMS emulation actually supports
  asymmetric `SIGN_VERIFY` keys and `Sign` at the pinned image version already used elsewhere in this
  service — not assumed here.)
  `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerLocalStackIntegrationTest.java`

## Files to Modify

None.

## Files NOT to Modify

- `common/config/KmsProperties.java` — frozen (T03); no region field added (see Scope).
- `observation/ObservationSnapshotStoreConfig.java` / `ObservationSnapshotStore.java` — pattern
  precedent only, not touched.
- Any file under `spec/`.
- `contracts/`.

## Acceptance Criteria

- **AC1 (R22/L11).** `KmsSignerArchitectureTest` fails if any class outside `com.themistra.crypto.attest`
  depends on `attest.KmsSigner` or any `software.amazon.awssdk.services.kms..` type, and passes on the
  current, clean codebase.
- **AC2 (L11).** `KmsSigner.sign(...)` calls `KmsClient.sign(...)` with `MessageType.DIGEST` and the
  chosen `SigningAlgorithmSpec`, using `KmsProperties.keyId()` as the key identifier — never a
  hardcoded/alternate key id.
- **AC3 (L11, no key-material handling).** `KmsSigner` never reads, logs, or returns anything but the
  signature bytes/metadata KMS itself returns — no local key generation, storage, or derivation anywhere
  in this class.
- **AC4 (scope).** A `KmsClient.sign(...)` failure propagates out of `KmsSigner.sign(...)` uncaught —
  not swallowed, not converted to a sentinel/empty value.
- **AC5 (null handling).** `sign(null)` throws `NullPointerException` immediately, before any KMS call.
- **AC6 (Nitro-Enclave portability, L11).** No host-only assumption anywhere in `KmsSigner`/
  `KmsSignerConfig` — no local file path, no hardcoded host-specific credential/region value.

## Required Tests

- `shouldOnlyAllowAttestPathToInvokeKmsSign` (named test) — `KmsSignerArchitectureTest` (AC1).
- A test that `sign(...)` calls `KmsClient.sign(...)` with the expected `MessageType`, algorithm, and
  `keyId`, and maps a successful `SignResponse` into the expected `SignatureResult` fields (AC2).
- A test that a `KmsClient.sign(...)` exception propagates unwrapped from `KmsSigner.sign(...)` (AC4).
- A test that `sign(null)` throws `NullPointerException` without calling `KmsClient` at all (AC5).
- (Contingent, per Files to Create) a LocalStack-backed real-API test proving an actual signature is
  produced and is verifiable against the key's own public key, if LocalStack's KMS emulation supports
  this at the pinned version — to be confirmed in Phase 5, with a documented fallback (a clearly-labeled
  skip/deferral, not a fake pass) if it does not.

## Constraints

- **Performance:** none beyond a single synchronous KMS call per invocation; no polling, no retries added
  by this task (KMS SDK's own default retry policy applies, unmodified).
- **Security:** no secret is logged; `KmsProperties.keyId()` identifies a key, it is not itself secret
  material, but is still never logged at a level that would leak infrastructure topology beyond what
  operators already need (a Phase 6 implementation detail, not a hard rule beyond "don't log the
  signature or digest at INFO/WARN").
- **Thread-safety:** `KmsSigner` and `KmsSignerConfig`'s bean are stateless singletons; `KmsClient`
  itself is documented by AWS as thread-safe.
- **Transaction:** none — no database interaction.
- **Module boundaries:** enforced by the new ArchUnit rule itself (AC1); `attest`'s own outbound
  dependencies are limited to `common.config.KmsProperties` and the KMS SDK (see Scope/Out for why a
  companion outbound-boundary test is not built in this task).
- **Null handling:** `sign(byte[] digestSha256)` rejects `null` immediately (AC5); no other parameter
  exists.

## Open Questions

No blockers. Q7 (KMS key type/algorithm) remains formally open at the specification level, but this
task's own scope is unblocked by proposing a digest-format-compatible algorithm choice for review at
Phase 3/4 — not by requiring Q7's final resolution first (no real KMS key is provisioned by this
codebase; provisioning is AWS CDK infrastructure, out of scope here).
