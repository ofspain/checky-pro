# crypto · T22 · Phase 6 — Implementation Notes

## What changed

**Created (production code):**
- `attest/PublicKeyInfo.java` — public record `(kid, kmsKeyId, alg, publicKeyPem)`.
- `attest/VerificationKeysResponse.java` — public record `(List<PublicKeyInfo> keys)`.
- `attest/VerificationKeysController.java` — `GET /.well-known/themistra-verification-keys`, no
  security annotation (already public), sets `Cache-Control: no-store`, delegates to
  `KmsSigner.publicKeyInfo()`.

**Modified:**
- `attest/KmsSigner.java` — new `publicKeyInfo()` (calls `GetPublicKey`, guards against a `null`
  `publicKey()` and an empty `signingAlgorithms()` list with named `IllegalStateException`s, maps
  `kid = kmsKeyId = response.keyId()`, `alg = signingAlgorithmsAsStrings().get(0)`,
  `publicKeyPem = toPem(...)`; a genuine `GetPublicKey` call failure propagates uncaught) and
  `toPem(byte[])` (package-private static — Phase 3 Finding #2's own required visibility for direct
  test access).

**Created (tests, written alongside production code per this task's own established T19-T21
precedent):**
- Extended `attest/KmsSignerTest.java` (+9 tests): request-shape, field-mapping, the two guard cases,
  the uncaught-failure case, `toPem`'s output shape and 64-char line-wrap boundary.
- Extended `attest/KmsSignerLocalStackIntegrationTest.java` (+2 tests): the named test
  `shouldPublishVerificationKeysAtWellKnownUrl` (a real `GetPublicKey` round-trip producing a PEM that
  parses back to the exact same `PublicKey` object created at `@BeforeAll`), and the `kmsKeyId` parity
  test between `sign(...)` and `publicKeyInfo()` for the same real key.
- `attest/VerificationKeysControllerTest.java` (new, `@WebMvcTest` slice): response shape,
  `Cache-Control: no-store` header, and the `AC6` uncaught-failure-→-500 case.

## Deviations from the plan, forced by reality

None. Every detail in Phase 5's plan (the exact guard order, the `toPem` line-wrap algorithm, the
`ResponseEntity.cacheControl(...)` builder shape) matched the actual AWS SDK/Spring APIs on first
attempt — confirmed by the tests passing without needing any mid-implementation correction, unlike
T20's `KmsSignerConfig` redesign or T21's persistence-ordering/JPA-type-mapping bugs. The one thing
worth noting as a deliberate choice, not a forced deviation: no new test was added to
`ResourceServerConfigIntegrationTest` or `PublicEndpointsTest` for public reachability, since
`PublicEndpointsTest.declaredPublicPathsAreNotBlockedBySecurity` (already parameterized over exactly
this path) already fully proves the security layer doesn't block it — adding a duplicate assertion
would have been redundant, not a genuine new test.

## Mapping to acceptance criteria

AC1 (named test `shouldPublishVerificationKeysAtWellKnownUrl`, `VerificationKeysControllerTest`'s own
shape/header assertions, `PublicEndpointsTest`'s pre-existing public-reachability proof), AC2
(`KmsSignerArchitectureTest` re-run, unmodified, still green), AC3 (the LocalStack test's own
`KeyFactory.generatePublic(...)` round-trip, and `KmsSignerTest.toPemProducesTheExpectedShapeForFixedInputBytes`),
AC4 (`KmsSignerLocalStackIntegrationTest`'s real `alg` assertion — confirmed exactly `"ECDSA_SHA_256"`
from the real key's own metadata, not assumed), AC5 (`VerificationKeysControllerTest`'s exact JSON-path
assertions — no extra field leaks through the record's own fixed shape), AC6
(`publicKeyInfoPropagatesAGetPublicKeyFailureUncaught` at the unit level,
`aPublicKeyInfoFailurePropagatesToAGenericFiveHundred` at the controller/HTTP-boundary level).

## Verification

`mvn -pl services/crypto compile` and `test-compile` succeed cleanly.
`mvn -pl services/crypto test -Dtest=KmsSignerTest,KmsSignerLocalStackIntegrationTest,VerificationKeysControllerTest,KmsSignerArchitectureTest,PublicEndpointsTest,ResourceServerConfigIntegrationTest`
— 51/51 pass. Full module regression (`mvn -pl services/crypto -am test`): 715 tests, 6 failures, all
pre-existing and unrelated to this task (disclosed since T18/T19/T20/T21). Zero regressions.
