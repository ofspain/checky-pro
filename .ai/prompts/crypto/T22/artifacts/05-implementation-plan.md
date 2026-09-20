# crypto · T22 · Phase 5 — Implementation Plan

Every file traces to the frozen brief's (`artifacts/04-frozen-task-brief.md`) Files to Create/Modify
sections. `GetPublicKeyRequest.Builder.keyId(String)` verified directly against the actual
`kms-2.50.2.jar` bytecode via `javap` before use.

## Files to create

1. `attest/PublicKeyInfo.java`
2. `attest/VerificationKeysResponse.java`
3. `attest/VerificationKeysController.java`

## Files to modify

1. `attest/KmsSigner.java` — add `publicKeyInfo()` and `toPem(byte[])`.

## Public methods (signatures)

- `PublicKeyInfo` — `public record PublicKeyInfo(String kid, String kmsKeyId, String alg, String publicKeyPem) {}`.
- `VerificationKeysResponse` — `public record VerificationKeysResponse(List<PublicKeyInfo> keys) {}`.
- `VerificationKeysController`:
  - `VerificationKeysController(KmsSigner kmsSigner)`.
  - `@GetMapping ResponseEntity<VerificationKeysResponse> verificationKeys()` — builds the response,
    sets `Cache-Control: no-store` via `ResponseEntity.ok().cacheControl(CacheControl.noStore())...`.
- `KmsSigner` (modified) — add:
  - `public PublicKeyInfo publicKeyInfo()`.
  - `static String toPem(byte[] der)` (package-private, no modifier).

## Private methods

- `KmsSigner.publicKeyInfo()` has no further private helpers beyond `toPem` itself — small enough
  (build request, call, guard, map) to stay one method, matching `sign(...)`'s own size/shape.

## `publicKeyInfo()` — verified call shape

```
GetPublicKeyRequest request = GetPublicKeyRequest.builder()
        .keyId(properties.keyId())
        .build();

GetPublicKeyResponse response = kmsClient.getPublicKey(request);   // uncaught on failure - AC6

if (response.publicKey() == null) {
    throw new IllegalStateException(
            "KMS returned no public key for keyId=" + properties.keyId());
}
if (!response.hasSigningAlgorithms() || response.signingAlgorithms().isEmpty()) {
    throw new IllegalStateException(
            "KMS returned no signing algorithms for keyId=" + properties.keyId());
}

String kmsKeyId = response.keyId();
String alg = response.signingAlgorithmsAsStrings().get(0);
String publicKeyPem = toPem(response.publicKey().asByteArray());

return new PublicKeyInfo(kmsKeyId, kmsKeyId, alg, publicKeyPem);
```

## `toPem(byte[] der)` — verified shape

```
static String toPem(byte[] der) {
    String base64 = Base64.getEncoder().encodeToString(der);
    StringBuilder pem = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
    for (int i = 0; i < base64.length(); i += 64) {
        pem.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
    }
    pem.append("-----END PUBLIC KEY-----\n");
    return pem.toString();
}
```
No new dependency — `java.util.Base64`, already used elsewhere in this service (`KmsSigner.sign`,
`ScreeningResult`).

## `VerificationKeysController` — cache-control header shape

Spring's `ResponseEntity.BodyBuilder.cacheControl(CacheControl)` is the established, type-safe way to
set `Cache-Control` (verified against Spring's own public API, already transitively available via
`spring-boot-starter-web`, no new dependency):
```
return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new VerificationKeysResponse(List.of(kmsSigner.publicKeyInfo())));
```

## Entities used

None.

## Repositories used

None.

## Services used

`attest.KmsSigner` (extended).

## Unit/integration tests required (per frozen brief's Required Tests — file placement)

- `attest/KmsSignerTest.java` (extended): `toPem` unit test (fixed DER bytes → expected PEM string,
  including the 64-char line-wrap boundary case); `publicKeyInfo()` field-mapping test (mocked
  `KmsClient`); the empty-`signingAlgorithms()` guard test; the null-`publicKey()` guard test; the
  uncaught-`GetPublicKey`-failure test (AC6).
- `attest/VerificationKeysControllerTest.java` (new, `@WebMvcTest` slice, mirrors `AttestControllerTest`'s
  established pattern): response shape, `Cache-Control: no-store` header assertion, no
  `internal.crypto:write` requirement (via `@AutoConfigureMockMvc(addFilters = false)`, matching
  `AttestControllerTest`'s own precedent for a narrow slice that doesn't exercise real security filters).
- `attest/KmsSignerLocalStackIntegrationTest.java` (extended, not a new file — the existing real-KMS
  round-trip test already creates a real asymmetric key): named test
  `shouldPublishVerificationKeysAtWellKnownUrl`'s real proof — calls `publicKeyInfo()` against the same
  real key, asserts the PEM parses back to a valid `PublicKey`; a second test asserting
  `sign(...).kmsKeyId()` and `publicKeyInfo().kmsKeyId()` agree (Finding #5).
- `attest/KmsSignerArchitectureTest` — re-run only, no new test (AC2 regression).
- `common/ResourceServerConfigIntegrationTest` (extended, or a focused new assertion) — confirms
  `/.well-known/themistra-verification-keys` requires no scope, mirroring that file's own existing
  parameterization style for public vs. internal paths.

## Execution order

1. **`PublicKeyInfo`** (no dependencies).
2. **`VerificationKeysResponse`** (depends on `PublicKeyInfo`).
3. **`KmsSigner` modifications** — `toPem(byte[])` first (no dependency on anything else new), then
   `publicKeyInfo()` (depends on `toPem` and `PublicKeyInfo`).
4. **`VerificationKeysController`** (depends on `KmsSigner.publicKeyInfo()` and
   `VerificationKeysResponse`).
5. **Tests**, in the same dependency order: `KmsSignerTest` extensions first (unit-level, cheapest to
   iterate on), then `KmsSignerLocalStackIntegrationTest` extensions (real infrastructure, slower), then
   `VerificationKeysControllerTest` (depends on the finished controller), then the
   `ResourceServerConfigIntegrationTest` extension, then `KmsSignerArchitectureTest` re-run last (mirrors
   this codebase's own established "boundary/architecture test last" convention).

## Open Questions

No blockers (unchanged from the frozen brief).
