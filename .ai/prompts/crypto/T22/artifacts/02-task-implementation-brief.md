# crypto · T22 · Phase 2 — Task Implementation Brief

## Task

Implement `attest.VerificationKeysController` for `GET /.well-known/themistra-verification-keys`,
publishing the attestation key's public component (kid, kmsKeyId, alg, publicKeyPem) so any third party
can independently verify a Themistra attestation signature.

## Purpose

Close the loop T20/T21 opened: a receipt is only as verifiable as its public key is discoverable.
Completes the `attest` package per `design.md`'s own package map.

## Scope

**In:**
- `attest.KmsSigner` gains one new public method, `PublicKeyInfo publicKeyInfo()` — **all KMS SDK usage
  for this task stays inside `KmsSigner`**, the only class the existing, negative-proof-tested
  `KmsSignerArchitectureTest` rule permits to touch the KMS SDK at all (Phase 0/1 finding). Calls
  `KmsClient.getPublicKey(GetPublicKeyRequest.builder().keyId(properties.keyId()).build())`, then:
  - `kmsKeyId` = `response.keyId()` (the canonical identifier KMS itself returns — same principle as
    `SignatureResult.kmsKeyId()`, T20/T21 precedent: never blindly echo the configured input value).
  - `alg` = the first entry of `response.signingAlgorithmsAsStrings()` — the real key's own authoritative
    supported-algorithm list (Phase 0 finding), not a hardcoded literal disconnected from the actual key.
    **Proposed for Phase 3/4 review:** if the list ever has more than one entry, the first is used and
    the rest silently ignored — a real KMS asymmetric signing key exposes exactly one signing algorithm
    in practice (verified against the LocalStack-backed key T20 already created: a single-entry list),
    so this is a documented, disclosed simplification, not an unverified guess.
  - `kid` = `kmsKeyId` itself — **proposed for Phase 3/4 review** (Phase 1's own disclosed open
    question): no other document defines `kid` distinctly, and reusing `kmsKeyId` is the simplest,
    single-source-of-truth answer that introduces no new config surface.
  - `publicKeyPem` = a PEM encoding of `response.publicKey().asByteArray()` (X.509 SubjectPublicKeyInfo
    DER, confirmed via the SDK jar and T20's own successful `KeyFactory` parse of this exact format) —
    `-----BEGIN PUBLIC KEY-----`, base64 body wrapped at 64 characters per line, `-----END PUBLIC
    KEY-----`, built via a new private static helper in `KmsSigner`.
  - No caching — every call to `publicKeyInfo()` issues a fresh `GetPublicKey` request. **Proposed for
    Phase 3/4 review**: simplest correct behavior, always reflects the real key (transparently survives
    a key rotation), and `GetPublicKey` is a low-volume, low-cost, non-mutating KMS read — no requirement
    in this spec package calls for caching, and adding it now would be unrequested complexity for an
    endpoint with no stated traffic-volume concern.
- `attest.PublicKeyInfo` — public record `(String kid, String kmsKeyId, String alg, String publicKeyPem)`,
  the JSON shape `design.md` names for one array element. Public (not package-private like
  `SignatureResult`) since this one *is* the serialized wire payload, mirroring `AttestResponse`'s own
  convention rather than `SignatureResult`'s internal-transfer-object one.
- `attest.VerificationKeysResponse` — public record `(List<PublicKeyInfo> keys)`, matching
  `{ keys: [...] }` exactly. Always a single-element list (one configured `keyId`) — the wire shape's
  own plural naming is honored structurally without inventing a multi-key config surface nothing else
  in this spec calls for.
- `attest.VerificationKeysController` — `GET /.well-known/themistra-verification-keys`, no security
  annotation (already public via the pre-existing `PublicEndpoints` entry, verified in Phase 0), calls
  `KmsSigner.publicKeyInfo()` and wraps the result in a single-element `VerificationKeysResponse`.
- `KmsSignerArchitectureTest` regression check: `VerificationKeysController`/`PublicKeyInfo`/
  `VerificationKeysResponse` all live in `attest` but are not named `KmsSigner*` — they must depend on
  `KmsSigner` (already permitted, same package) and never on the KMS SDK directly. No rule change
  needed; this is exactly the shape the existing rule already supports.

**Out:**
- Any caching/refresh mechanism for the fetched public key.
- Any change to `KmsSignerArchitectureTest`'s rule shape — the existing name-plus-package exception
  already covers this task's design without modification.
- Any change to `common.PublicEndpoints` — the path is already listed.
- Multi-key support beyond a single-element array.
- Resolving Q7 (KMS key type/algorithm) at the specification level — this task's `alg` field sources
  from the real key's own metadata, sidestepping the need for Q7 to be answered in spec text, but does
  not close Q7 as a standing open question in `package.md` §11.

## Business Rules

- **R24.** The verification public keys are published (with `kid`/`kmsKeyId`) at a well-known URL.

## Locked Decisions

- **L11.** The clause this task enacts: "verification public keys are published at a well-known URL."
  `kms:Sign` itself stays untouched by this task, unmodified from T20.

## Dependencies

`common.config.KmsProperties` (read-only, `keyId()`); `attest.KmsSigner` (extended);
`software.amazon.awssdk.services.kms.model.GetPublicKeyRequest`/`GetPublicKeyResponse`.

## Inputs

None (no request body, no path variable, no query parameter).

## Outputs

`VerificationKeysResponse{ keys: [ PublicKeyInfo{ kid, kmsKeyId, alg, publicKeyPem } ] }`, `200`.

## State Changes

None. No persistence, no event, no mutation.

## Files to Create

- `attest/PublicKeyInfo.java`
- `attest/VerificationKeysResponse.java`
- `attest/VerificationKeysController.java`

## Files to Modify

- `attest/KmsSigner.java` — new `publicKeyInfo()` method and its private PEM-encoding helper.

## Files NOT to Modify

- `attest/SignatureResult.java`, `attest/AttestationService.java`, `attest/AttestController.java`,
  `attest/Attestation.java`/`AttestationRepository.java` (T21, frozen).
- `attest/KmsSignerArchitectureTest.java` — no rule change expected.
- `common/PublicEndpoints.java`, `common/ResourceServerConfig.java`, `common/ApiExceptionHandler.java`.
- `common/config/KmsProperties.java`.
- `V1`-`V10` migrations. Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R24, named test).** `GET /.well-known/themistra-verification-keys` returns `200` with
  `{ keys: [ { kid, kmsKeyId, alg, publicKeyPem } ] }`, reachable with no `internal.crypto:write` scope.
- **AC2 (L11, KMS SDK concentration).** `KmsSignerArchitectureTest`'s existing rules still pass
  unmodified with this task's three new files present.
- **AC3 (schema fidelity).** `publicKeyPem` parses back into a valid `java.security.PublicKey` via
  `KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(...))` after stripping the
  PEM header/footer and base64-decoding — proving correct DER-to-PEM conversion.
- **AC4 (schema fidelity).** `alg` equals the real KMS key's own `signingAlgorithms()` value, not a
  disconnected hardcoded literal.
- **AC5 (no secret exposure).** The response contains only `kid`/`kmsKeyId`/`alg`/`publicKeyPem` — no
  other `GetPublicKeyResponse` field (e.g. `keyUsage`, `customerMasterKeySpec`) leaks through.

## Required Tests

- `shouldPublishVerificationKeysAtWellKnownUrl` (named test) — real `GetPublicKey` round-trip via
  LocalStack (mirrors `KmsSignerLocalStackIntegrationTest`'s established pattern), asserting the returned
  PEM parses back to a valid public key.
- A unit test for the PEM-encoding helper in isolation: fixed DER input bytes → expected PEM string
  shape (header, base64 body wrapped at 64 chars, footer).
- A unit test for `KmsSigner.publicKeyInfo()`'s field mapping (mocked `KmsClient`): `kmsKeyId` from the
  response, `alg` from `signingAlgorithms()`, `kid` equals `kmsKeyId`.
- `KmsSignerArchitectureTest` regression run (AC2).
- A test confirming the endpoint requires no `internal.crypto:write` token — mirrors
  `ResourceServerConfigIntegrationTest`'s own established parameterization style, if a public-endpoint
  case exists there already; otherwise a focused new assertion.

## Constraints

- **Performance:** one synchronous `GetPublicKey` call per request; no caching (deliberate, disclosed).
- **Security:** the response is intentionally public — no scope check, no secret ever included (only a
  public key, by definition non-secret).
- **Thread-safety:** `KmsSigner`'s new method is stateless, same singleton as `sign(...)`.
- **Transaction:** none — no database interaction.
- **Module boundaries:** `VerificationKeysController`/`PublicKeyInfo`/`VerificationKeysResponse` all
  live in `attest`, satisfying `KmsSignerArchitectureTest`'s first rule (no cross-package reference to
  `KmsSigner` needed — they're already in the same package) and its second rule (they never touch the
  KMS SDK directly, only `KmsSigner`).
- **Null handling:** `KmsSigner.publicKeyInfo()` propagates any `GetPublicKey` failure uncaught (mirrors
  `sign(...)`'s own established posture — an infrastructure fault surfaces as `500`, not a swallowed or
  converted error, since there is no gate-failure semantic that applies to a public-key read).

## Open Questions

No blockers. `kid`'s derivation, the no-caching decision, and the single-element-array scoping are all
resolved above as explicit, disclosed proposals for Phase 3/4 review, not silently assumed.
