STATUS: FROZEN

# crypto · T22 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 6 findings accepted (1 partially — the `Content-Type` half was skipped as redundant with Spring's
own default behavior for this return type).

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | `alg = signingAlgorithms().get(0)` has no empty-list guard — would surface as an opaque `IndexOutOfBoundsException` | **ACCEPTED** | `publicKeyInfo()` checks `hasSigningAlgorithms()`/emptiness explicitly and throws `IllegalStateException` naming the configured key id before indexing. |
| 2 | The PEM helper's `private static` visibility conflicts with the brief's own mandated isolated unit test | **ACCEPTED** | Changed to package-private `static String toPem(byte[] der)`, directly callable from a test in the same `attest` package — no reflection needed. |
| 3 | No AC/test explicitly locks "a `GetPublicKey` failure propagates uncaught" — only stated in Constraints, not enforced | **ACCEPTED** | New **AC6** added; new required test: a thrown `KmsClient.getPublicKey(...)` exception reaches the caller unmodified. |
| 4 | Response `Content-Type`/cache semantics unspecified — a rotated key could be CDN/browser-cached | **ACCEPTED, PARTIAL** | `VerificationKeysController` sets an explicit `Cache-Control: no-store` response header. The `Content-Type` half is skipped — Spring's `@RestController` already defaults to `application/json` for a record return type; asserting it would be redundant, not a real gap. |
| 5 | No verification that `sign(...)`'s `kmsKeyId` and `publicKeyInfo()`'s `kmsKeyId` agree for the same key | **ACCEPTED** | New LocalStack integration test comparing `KmsSigner.sign(...).kmsKeyId()` and `KmsSigner.publicKeyInfo().kmsKeyId()` against the same real key — a verifier-facing correctness concern (a receipt's `kmsKeyId` must be findable in the published key list). |
| 6 | `response.publicKey()` being `null` is not guarded — would surface as a raw `NullPointerException` | **ACCEPTED** | `publicKeyInfo()` checks for `null` explicitly and throws `IllegalStateException` naming the configured key id, mirroring Finding #1's treatment. |

## Task

Implement `attest.VerificationKeysController` for `GET /.well-known/themistra-verification-keys`,
publishing the attestation key's public component (kid, kmsKeyId, alg, publicKeyPem).

## Purpose

Close the loop T20/T21 opened: a receipt is only as verifiable as its public key is discoverable.

## Scope

**In:**
- `attest.KmsSigner.publicKeyInfo()` — new public method, the only new KMS SDK touchpoint in this task,
  living inside the one class the existing ArchUnit rule permits:
  1. Calls `KmsClient.getPublicKey(GetPublicKeyRequest.builder().keyId(properties.keyId()).build())`.
  2. Guards: if `response.publicKey() == null` (Finding #6) or
     `!response.hasSigningAlgorithms() || response.signingAlgorithms().isEmpty()` (Finding #1), throws
     `IllegalStateException` naming `properties.keyId()`, before any further processing.
  3. `kmsKeyId` = `response.keyId()`. `alg` = `response.signingAlgorithmsAsStrings().get(0)` (now
     guaranteed non-empty by the guard above). `kid` = `kmsKeyId` (Phase 2's own disclosed, unchallenged
     proposal). `publicKeyPem` = `toPem(response.publicKey().asByteArray())`.
  4. No try/catch around the `GetPublicKey` call itself — any SDK exception propagates uncaught (AC6,
     Finding #3), mirroring `sign(...)`'s own established posture.
  5. No caching (Phase 2's own disclosed, unchallenged proposal) — every call issues a fresh request.
- `KmsSigner.toPem(byte[] der)` — new package-private static helper (Finding #2): builds
  `-----BEGIN PUBLIC KEY-----`, the base64-encoded DER wrapped at 64 characters per line,
  `-----END PUBLIC KEY-----`.
- `attest.PublicKeyInfo` — public record `(String kid, String kmsKeyId, String alg, String publicKeyPem)`.
- `attest.VerificationKeysResponse` — public record `(List<PublicKeyInfo> keys)`.
- `attest.VerificationKeysController` — `GET /.well-known/themistra-verification-keys`, no security
  annotation (already public), sets `Cache-Control: no-store` on the response (Finding #4), calls
  `KmsSigner.publicKeyInfo()`, wraps in a single-element `VerificationKeysResponse`.
- `KmsSignerArchitectureTest` regression check — no rule change.

**Out:**
- Any caching/refresh mechanism.
- Any change to `KmsSignerArchitectureTest`'s rule shape.
- Any change to `common.PublicEndpoints`.
- Multi-key support beyond a single-element array.
- Resolving Q7 at the specification level.
- An explicit `Content-Type` assertion (Finding #4, partial — redundant with Spring's own default).

## Business Rules

- **R24.** The verification public keys are published (with `kid`/`kmsKeyId`) at a well-known URL.

## Locked Decisions

- **L11.** "Receipts embed the key id; verification public keys are published at a well-known URL." —
  the clause this task enacts.

## Dependencies

`common.config.KmsProperties` (read-only); `attest.KmsSigner` (extended);
`software.amazon.awssdk.services.kms.model.GetPublicKeyRequest`/`GetPublicKeyResponse`.

## Inputs

None.

## Outputs

`200 VerificationKeysResponse{ keys: [ PublicKeyInfo{ kid, kmsKeyId, alg, publicKeyPem } ] }`,
`Cache-Control: no-store`.

## State Changes

None.

## Files to Create

- `attest/PublicKeyInfo.java`
- `attest/VerificationKeysResponse.java`
- `attest/VerificationKeysController.java`

## Files to Modify

- `attest/KmsSigner.java` — `publicKeyInfo()`, `toPem(byte[])`, the two `IllegalStateException` guards.

## Files NOT to Modify

- `attest/SignatureResult.java`, `attest/AttestationService.java`, `attest/AttestController.java`,
  `attest/Attestation.java`/`AttestationRepository.java` (T21, frozen).
- `attest/KmsSignerArchitectureTest.java`.
- `common/PublicEndpoints.java`, `common/ResourceServerConfig.java`, `common/ApiExceptionHandler.java`.
- `common/config/KmsProperties.java`.
- `V1`-`V10` migrations. Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R24, named test).** `GET /.well-known/themistra-verification-keys` returns `200` with
  `{ keys: [ { kid, kmsKeyId, alg, publicKeyPem } ] }`, no `internal.crypto:write` scope required,
  `Cache-Control: no-store` present.
- **AC2 (L11, KMS SDK concentration).** `KmsSignerArchitectureTest` still passes unmodified.
- **AC3 (schema fidelity).** `publicKeyPem` parses back into a valid `PublicKey` via
  `KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(...))`.
- **AC4 (schema fidelity).** `alg` equals the real KMS key's own `signingAlgorithms()` value.
- **AC5 (no secret exposure).** The response contains only `kid`/`kmsKeyId`/`alg`/`publicKeyPem`.
- **AC6 (Finding #3, new).** Any `GetPublicKey` failure propagates uncaught (`500`) — never a partial or
  stale `keys` array, never silently mapped to a different status.

## Required Tests

- `shouldPublishVerificationKeysAtWellKnownUrl` (named test) — real `GetPublicKey` round-trip via
  LocalStack, asserting the PEM parses back to a valid public key and `Cache-Control: no-store` is set.
- A unit test for `KmsSigner.toPem(byte[])` in isolation (Finding #2): fixed DER bytes → expected PEM
  shape.
- A unit test for `publicKeyInfo()`'s field mapping (mocked `KmsClient`): `kmsKeyId` from the response,
  `alg` from `signingAlgorithms()`, `kid` equals `kmsKeyId`.
- A unit test for the empty-`signingAlgorithms()` guard (Finding #1): stub an empty list, assert
  `IllegalStateException` naming the configured key id.
- A unit test for the null-`publicKey()` guard (Finding #6): stub `null`, assert `IllegalStateException`
  naming the configured key id.
- A unit test for AC6 (Finding #3): stub `kmsClient.getPublicKey(...)` to throw, assert the exception
  reaches the caller unmodified.
- A LocalStack integration test (Finding #5): `KmsSigner.sign(...).kmsKeyId()` and
  `KmsSigner.publicKeyInfo().kmsKeyId()` agree for the same real key.
- `KmsSignerArchitectureTest` regression run (AC2).
- A test confirming the endpoint requires no `internal.crypto:write` token.

## Constraints

- **Performance:** one synchronous `GetPublicKey` call per request; no caching (deliberate, disclosed).
- **Security:** intentionally public, no scope check; only non-secret public-key material is ever
  returned; `Cache-Control: no-store` prevents intermediary caching across a key rotation.
- **Thread-safety:** stateless, same `KmsSigner` singleton as `sign(...)`.
- **Transaction:** none.
- **Module boundaries:** all new files live in `attest`; only `KmsSigner` touches the KMS SDK.
- **Null handling:** `response.publicKey() == null` and an empty `signingAlgorithms()` are both explicit,
  named `IllegalStateException`s (Findings #1/#6), not raw NPEs/`IndexOutOfBoundsException`s. A
  `GetPublicKey` call failure itself still propagates uncaught (AC6) — the guards only cover a
  *successful* response with anomalous content, not a failed call.

## Open Questions

No blockers.
