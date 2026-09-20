# crypto · T20 · Phase 1 — Specification Extraction

## Business Rules

- **R22.** The system SHALL invoke `kms:Sign` on the attestation key only from the attest path; no
  other module or endpoint SHALL be able to reach the signer.

## Locked Decisions

- **L11. KMS-only signing, single path** (`design.md` §4a): attestation keys are generated in AWS KMS
  and never leave it; the receipt digest is sent to KMS for signing. `kms:Sign` on the attestation key
  is reachable **only** from the `attest` module — enforced by ArchUnit (package ban) *and* by IAM
  (infrastructure, out of this codebase's scope). Receipts embed the key id; verification public keys
  are published at a well-known URL (task 22, not this task). The attest logic must be structured to be
  portable into a Nitro Enclave later — no host-only assumptions in the sign path.

## Files involved

**Existing, to read/extend:**
- `design.md` §4c Internal API definition (lines 64-84): `POST /internal/v1/attest` body
  `{ receiptDigestSha256: "<hex>", chain, txHash }`, success response
  `{ signature: "<base64>", kmsKeyId, signedAt, outcome: "SIGNED" }`. This task does not build the
  endpoint (task 21) but its own `KmsSigner`'s API shape should be informed by what that endpoint will
  need to hand it and get back.
- `services/crypto/src/main/java/com/themistra/crypto/common/config/KmsProperties.java` — already
  shipped (T03), validated `@ConfigurationProperties` with exactly one field, `keyId`. Read, not
  modified.
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationSnapshotStoreConfig.java`
  — the only existing AWS-SDK-client-wiring precedent in this codebase (for `S3Client`); its own Javadoc
  names this task's `KmsClient` bean as the reason no other precedent exists yet.
- `services/crypto/pom.xml` — `software.amazon.awssdk:kms` and `com.tngtech.archunit:archunit-junit5`
  are already declared dependencies, unused until this task.
- `spec/crypto-service/agents.md` — authoritative standing rules, in particular: "`kms:Sign` is
  reachable only from the `attest` module — enforced by ArchUnit and by IAM... The sign path stays
  Nitro-Enclave-portable (no host-only assumptions)."

**New, expected by the spec's own package map (`design.md` §6, `attest/`):**
- `attest/KmsSigner.java` — the sole `kms:Sign` caller. This task's only production file.
- A `KmsClient` Spring bean (config class name not dictated by the spec — a Phase 2 design choice,
  mirroring `ObservationSnapshotStoreConfig`'s naming convention, e.g. `KmsSignerConfig`).
- The ArchUnit rule itself — a new test class (name/location not dictated; a Phase 2 design choice,
  likely `attest/KmsSignerArchitectureTest` or similar, following this codebase's per-package test
  convention).

## Dependencies

- `common.config.KmsProperties.keyId()` — the only config this task consumes.
- `software.amazon.awssdk.services.kms.KmsClient` — the KMS SDK client this task's `KmsSigner` calls
  `sign(...)` through; no other class anywhere may import this type or `KmsSigner` itself (R22/L11).
- No database dependency — `KmsSigner` persists nothing (the `Attestation` entity/repository that will
  record the outcome is task 21's own scope, a separate file in the same `attest/` package).
- No outbox/event dependency — no `chain.*` event is emitted by signing itself.
- `com.tngtech.archunit:archunit-junit5:1.3.0` (already a test-scope dependency) for the named ArchUnit
  rule.

## Acceptance Criteria

1. **AC1 (R22/L11).** `KmsSigner` is the only class in the entire `services/crypto` codebase that
   invokes the KMS SDK's signing API (`KmsClient.sign(...)`) — proven by an ArchUnit rule, not merely by
   convention.
2. **AC2 (L11, module boundary).** No package outside `attest` may even *reference* `KmsSigner` or the
   KMS SDK's signing API type(s) — the rule is a reachability ban, not just a "don't call it" convention;
   an unused import from another module should also fail the rule, matching the task statement's literal
   wording ("no package outside `attest` may reference").
3. **AC3 (L11, Nitro-Enclave portability).** `KmsSigner`'s sign path makes no host-only assumption (e.g.
   no local filesystem key material, no host-specific credential file path) — the AWS SDK's own default
   credential chain and network-reachable KMS endpoint are both enclave-compatible by construction, so
   this is primarily a "don't introduce a host-only shortcut" discipline rather than new code to write.
4. **AC4 (L11, KMS-only key material).** `KmsSigner` never handles, logs, or persists raw key material —
   only a request to KMS and the signature KMS returns.
5. **AC5 (R22, no other invoker exists yet).** Since no caller of `KmsSigner` exists yet in this
   codebase (task 21 builds `AttestationService`), this task ships `KmsSigner` as a complete,
   independently-testable unit with no wiring into any endpoint.

## Tests required

- **Named test (`package.md` §8):** `shouldOnlyAllowAttestPathToInvokeKmsSign` → R22. This is the
  ArchUnit rule itself (or the test class containing it) — the task statement and this named test are
  effectively the same deliverable.
- **Unit tests for `KmsSigner`** (exact scenarios are a Phase 2 design decision, pending the algorithm/
  digest-format questions below): a successful sign call returns the expected shape; a KMS client
  exception propagates (or is wrapped — undecided); no defensive re-implementation of KMS's own
  validation (mirrors this codebase's established "trust the SDK/DB for what it already guarantees"
  posture, e.g. `ObservationSnapshotStore`'s treatment of `S3Client`).
- **Integration test** — likely LocalStack-based (mirrors `ObservationSnapshotStoreLocalStackIntegrationTest`'s
  precedent of treating core AWS infrastructure as real, not fake, in tests) — contingent on Phase 0's
  open question of whether LocalStack's KMS emulation actually supports asymmetric `SIGN_VERIFY` keys and
  the `Sign` operation at the pinned image version; to be verified directly in Phase 5/6, not assumed.
- **ArchUnit rule test** — the core deliverable; must fail if any non-`attest` class in `src/main`
  imports `KmsSigner` or the KMS SDK's signing surface, and must pass on the current, clean codebase.

## Open Questions

- **Q7 (`package.md` §11) — KMS signing key type/algorithm is explicitly unanswered.** However,
  `design.md` §4c's own Internal API definition already fixes the digest format on the wire:
  `receiptDigestSha256: "<hex>"` — a SHA-256 digest, hex-encoded. This narrows, but does not fully
  answer, Q7: whichever KMS `SigningAlgorithmSpec` this task uses must accept a 32-byte SHA-256 digest
  via `MessageType.DIGEST` (a strong candidate, given the fixed digest, is an ECDSA P-256 key with
  `ECDSA_SHA_256`, but this is not stated anywhere in the spec and must not be assumed as final without
  human confirmation — Phase 2/4 territory, flagged here as a genuine partial blocker: the digest format
  is fixed, the key spec/algorithm is not).
- **Region resolution for the `KmsClient` bean.** `KmsProperties` has no `region()` field (unlike
  `SnapshotProperties`, which does for S3). Not stated anywhere in the spec. Not a hard blocker — the
  AWS SDK's own default region provider chain is a legitimate, spec-compliant fallback (L13's secrets/
  config discipline governs credentials, not necessarily region) — but the specific choice is left to
  Phase 2 design, not pre-decided by the spec.
- **Whether `KmsSigner`'s own method signature takes a raw digest `byte[]`/hex `String` or something
  richer** (e.g. an already-parsed value object) — not specified anywhere; a Phase 2 design choice, made
  easier by the wire format already being fixed as `receiptDigestSha256: "<hex>"` above.
