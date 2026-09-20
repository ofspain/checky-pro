# crypto · T22 · Phase 1 — Specification Extraction

## Business Rules

- **R24.** When a third party requests the verification public keys, the system publishes them (with
  `kid`/`kmsKeyId`) at a well-known URL so any Themistra receipt can be independently verified.

## Locked Decisions

- **L11.** KMS-only signing, single path — the specific clause this task enacts: "Receipts embed the key
  id; verification public keys are published at a well-known URL." `kms:Sign` itself remains reachable
  only from `attest` (T20's own already-implemented, negative-proof-tested guarantee, unmodified by this
  task). This task's own new KMS call (`GetPublicKey`, a read of public, non-secret key material) must
  stay consistent with the existing ArchUnit rule's structural intent — concentration of all KMS SDK
  usage inside `attest`, effectively inside `KmsSigner` itself given the rule's current name-prefix
  shape (confirmed in Phase 0 by reading `KmsSignerArchitectureTest` directly).
- **L13 (service-wide, unmodified).** No KMS key ARN is committed; `KmsProperties.keyId()` (already
  validated, already fails startup on a missing value in non-local profiles) is this task's only
  config dependency — no new secret, no new `@ConfigurationProperties` record needed.

## Files involved

**Existing, to read/extend:**
- `common/PublicEndpoints.java` — already lists `/.well-known/themistra-verification-keys`; confirmed
  in Phase 0, no change expected.
- `common/config/KmsProperties.java` — `keyId()`, read-only, unmodified.
- `attest/KmsSigner.java` — the sole class permitted to touch the KMS SDK; this task's own `GetPublicKey`
  call must be added here (or an equally-named, equally-scoped seam) to stay compliant with the existing
  ArchUnit rule, per Phase 0's own finding.
- `attest/KmsSignerArchitectureTest.java` — regression check only; no rule change expected unless Phase
  2's design requires widening the name-prefix exception (to be proposed explicitly if so, not assumed).
- `design.md` §4c's wire shape (`GET /.well-known/themistra-verification-keys` →
  `{ keys: [ { kid, kmsKeyId, alg, publicKeyPem } ] }`).

**New, expected by `design.md`'s own package map (`attest/`):**
- `attest/VerificationKeysController.java` — the only file the task statement names explicitly.
- Whatever response record(s) the controller returns (hand-written, no `contracts/api/crypto-internal.yaml`
  exists — confirmed absent by every prior task in this package).

## Dependencies

`common.config.KmsProperties` (read-only, `keyId()`); `attest.KmsSigner` (extended with a new public-key
read method, or an equivalent seam); `software.amazon.awssdk.services.kms.model.GetPublicKeyRequest`/
`GetPublicKeyResponse` (confirmed via direct SDK-jar inspection in Phase 0: `GetPublicKeyResponse`
exposes `keyId()`, `publicKey()` — X.509 SubjectPublicKeyInfo DER bytes — and `signingAlgorithms()`, the
key's own authoritative supported-algorithm list); no database, no outbox, no event.

## Acceptance Criteria

1. **AC1 (R24).** `GET /.well-known/themistra-verification-keys` returns `200` with
   `{ keys: [ { kid, kmsKeyId, alg, publicKeyPem } ] }`, publicly, with no `internal.crypto:write` scope
   required (already covered by the pre-existing `PublicEndpoints` entry — verified, not newly built).
2. **AC2 (L11, KMS SDK concentration).** No class outside `attest.KmsSigner` (or an equally-scoped seam)
   touches the KMS SDK for this task's own new `GetPublicKey` call — `KmsSignerArchitectureTest`'s
   existing rules must still pass with this task's new files present, exactly as T21 handled it for its
   own, unrelated KMS-adjacent files (T21 added none directly touching the SDK, so this is this task's
   own first real test of that constraint since T20).
3. **AC3 (schema fidelity).** `publicKeyPem` is a valid PEM-encoded public key, parseable back into a
   `java.security.PublicKey` via the standard JCA `KeyFactory` path — proving the DER-to-PEM conversion
   is correct, not just "a base64 string with headers."
4. **AC4 (schema fidelity).** `alg` reflects the actual KMS key's own supported signing algorithm
   (`GetPublicKeyResponse.signingAlgorithms()`), not a hardcoded literal disconnected from the real key —
   given Q7 remains formally unanswered, this is the most defensible source of truth available.
5. **AC5 (no secret exposure).** The response never includes anything beyond `kid`/`kmsKeyId`/`alg`/
   `publicKeyPem` — no private key material, no raw KMS API response fields beyond what the wire shape
   names.

## Tests required

- **Named test (`package.md` §8):** `shouldPublishVerificationKeysAtWellKnownUrl` → R24.
- **Boundary/unit tests implied by scope:**
  - The endpoint is reachable without an `internal.crypto:write` token (public) — likely folded into the
    existing `ResourceServerConfigIntegrationTest`'s own parameterized internal-vs-public test shape, if
    that file already distinguishes the two, or a new small assertion there.
  - `KmsSignerArchitectureTest`'s existing rules regression-pass with this task's new files present.
  - A real, LocalStack-backed integration test proving an actual `GetPublicKey` round-trip produces a
    valid, parseable PEM — mirrors `KmsSignerLocalStackIntegrationTest`'s own established pattern and
    proven API call shape (already exercised successfully in that file, though not through production
    code).
  - A unit test for the DER-to-PEM conversion logic in isolation (fixed input bytes → expected PEM
    string shape: header, base64 body, footer).

## Open Questions

Both genuine, disclosed in Phase 0, requiring explicit Phase 2 design proposals rather than blocking
this task outright:

- **How `kid` is derived is not specified anywhere in the spec.** No document defines what `kid` is or
  how it differs from `kmsKeyId`. Not a hard blocker — a Phase 2 proposal (e.g., `kid` = `kmsKeyId`
  itself, or a short derived value) will be submitted for Phase 3/4 review rather than assumed silently.
- **Whether the response caches the fetched public key or calls `GetPublicKey` on every request** is
  unspecified. Not a hard blocker — a Phase 2 proposal, informed by the fact that a public key never
  changes for a given `kmsKeyId` outside of a key-rotation event.
- **Whether the response's `keys` array should be designed to hold more than the one configured key** is
  unspecified beyond the wire shape's own plural naming. Not a hard blocker — `KmsProperties` only ever
  configures one `keyId` today, so a single-element array is the only shape this task can build against
  without inventing a multi-key config surface no other part of the spec calls for.
- **Q7 (KMS key type/algorithm) remains formally unanswered at the specification level** — this task's
  own `alg` field sources from the real key's own KMS metadata (Phase 0 finding), which sidesteps needing
  Q7 answered in the spec text itself, but does not resolve Q7 as a standing open question in
  `package.md` §11.
