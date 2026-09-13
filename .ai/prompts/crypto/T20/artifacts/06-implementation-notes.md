# crypto · T20 · Phase 6 — Implementation Notes

## What changed

**Created (production code):**
- `attest/SignatureResult.java` — package-private record `(signatureBase64, kmsKeyId, signedAt)` (Finding #15).
- `attest/KmsSigner.java` — `sign(byte[] digestSha256)`, validates non-null and exactly-32-bytes before
  any KMS call; builds a `SignRequest` (`MessageType.DIGEST`, `SigningAlgorithmSpec.ECDSA_SHA_256` — a
  single named constant flagged pending Q7); maps the response with `kmsKeyId` from `SignResponse.keyId()`
  (Finding #4) and `signatureBase64` via standard `Base64.getEncoder()` (Finding #5); `signedAt` from an
  injected `Clock` (Finding #6); declares no `Logger` field (Finding #11); no try/catch around the KMS
  call (Finding — exceptions propagate uncaught).
- `attest/KmsSignerArchitectureTest.java` — the named test `shouldOnlyAllowAttestPathToInvokeKmsSign`:
  two ArchUnit rules (no class outside `attest` may reference `KmsSigner`; no class outside a
  `KmsSigner*`-name-prefix may depend on the KMS SDK) plus a plain-JUnit canary that actually gates
  `mvn test` (Finding #1), scanning both main and test sources (Finding #14), scoped to
  `com.themistra.crypto` only (Finding #3).

**Created (tests, written alongside production code per this task's own established T19 precedent —
there is no existing caller to exercise these files against manually):**
- `attest/KmsSignerTest.java` — interaction tests (request shape, response mapping, exception
  propagation, null/length validation) plus 3 structural source-scan tests (no key-material API
  imported, no host-only literal, no `Logger` field).
- `attest/KmsSignerLocalStackIntegrationTest.java` — a real KMS-API round-trip through `KmsSigner`
  alone: creates a real asymmetric `SIGN_VERIFY`/`ECC_NIST_P256` key against LocalStack, signs a digest,
  and verifies the resulting signature against the key's own public key using `NONEwithECDSA` (since
  `MessageType.DIGEST` means the input was already hashed — re-hashing during verification would never
  match).

## Deviations from the plan, forced by reality

1. **`KmsSignerConfig.java` was deleted; its responsibility folded into `KmsSigner` itself.** The Phase 5
   plan called for a separate `@Configuration` class mirroring `ObservationSnapshotStoreConfig`. Writing
   the ArchUnit rule first revealed this doesn't work: `KmsSignerConfig`'s own `@Bean` method necessarily
   depends on `KmsClient` just to construct it, which the rule (correctly) flags as a violation — a
   config class needing the type to wire it is not the same as a class invoking `kms:Sign`, but the
   dependency-based rule can't tell those apart. The actual, already-reviewed precedent for exactly this
   situation already exists in this monorepo: `services/auth`'s `MfaSeedEncryption` builds its own
   `KmsClient` internally via a private static factory, with a package-private test-seam constructor —
   discovered and verified by reading that class directly, not assumed. `KmsSigner` now does the same:
   a public `(KmsProperties, Clock)` constructor delegates to a package-private
   `(KmsProperties, Clock, KmsClient)` one, with `resolveKmsClient()` as the private static factory. This
   is a better fit for this task's own "one class owns both construction and the sensitive operation"
   goal than the S3Client analogy was, and it eliminates the tension at its root rather than special-casing
   an exception.
2. **The ArchUnit exception was redesigned from an exact-name list to a name-prefix predicate.** After
   fixing (1), a second, identical tension appeared with `KmsSignerTest` (which must mock `KmsClient`)
   and then again with `KmsSignerLocalStackIntegrationTest` (which must use the real SDK to prove
   connectivity) — each legitimate test needing the same kind of exception. Rather than accumulate an
   ever-growing `.and().doNotHaveFullyQualifiedName(...)` chain (one edit per future test file), the rule
   was rewritten as `haveSimpleNameNotStartingWith("KmsSigner")` — verified directly against the ArchUnit
   1.3.0 jar's own `ClassesThat` interface bytecode that this predicate method exists exactly as needed.
   This still catches a rogue, unrelated class anywhere in `com.themistra.crypto` (Finding #14's real
   goal), while never needing another edit for a legitimate `KmsSigner`-family test.
3. **The contingent LocalStack test was written, not skipped or documented-only.** Phase 5's plan
   committed to directly verifying LocalStack's asymmetric-KMS support before deciding. A standalone
   probe (`CreateKey` with `KeyUsage=SIGN_VERIFY`/`KeySpec=ECC_NIST_P256`, then `Sign` with
   `MessageType.DIGEST`/`ECDSA_SHA_256`) succeeded against the same pinned `localstack/localstack:3.8`
   image already used by `ObservationSnapshotStoreLocalStackIntegrationTest`, producing a real 72-byte
   DER-encoded signature — so the real test was written per the frozen brief's Finding #13 disposition,
   not a documented skip.
4. **Region resolution (Finding #8) was verified, not left assumed.** A standalone probe (`KmsClient.
   builder().build()` with a deliberately clean environment — no `AWS_REGION`, no `~/.aws/config`)
   confirmed `SdkClientException` is thrown immediately at `.build()` time, not deferred to first API
   call — since this happens inside `KmsSigner`'s own eagerly-constructed Spring bean at context
   startup, the "fail at startup" rule (`agents.md`) is already satisfied without a `@PostConstruct`
   validator, exactly as the frozen brief anticipated pending this verification.
5. **A test assertion (not production code) was wrong on first write and corrected.** The LocalStack
   test initially asserted `result.kmsKeyId()` equals the bare key id passed as `KmsProperties.keyId()`.
   The real LocalStack response returned the full ARN instead
   (`arn:aws:kms:us-east-1:000000000000:key/<uuid>` vs. the bare `<uuid>`) — empirically confirming
   Finding #4's own reasoning (the configured input and KMS's own canonical response identifier can
   legitimately differ) was correct, not hypothetical. Fixed the assertion to check containment of the
   bare id within the returned ARN, rather than exact equality.

## Mapping to acceptance criteria

AC1 (ArchUnit rule + canary): `KmsSignerArchitectureTest`. AC2 (request shape):
`KmsSignerTest.signCallsKmsWithTheExpectedRequestShape`. AC3 (no key material): structural test. AC4
(exception propagation): `KmsSignerTest.aKmsExceptionPropagatesUnwrapped`. AC5 (null/length): the two
rejection tests. AC6 (no host-only assumption): structural test. AC7 (no Logger field): structural test.

## Verification

`mvn -pl services/crypto compile` and `test-compile` succeed cleanly.
`mvn -pl services/crypto test -Dtest=KmsSignerTest,KmsSignerArchitectureTest,KmsSignerLocalStackIntegrationTest`
— 10/10 pass. Full module regression (`mvn -pl services/crypto -am test`): 650 tests, 6 failures, all
pre-existing and unrelated to this task (the same 6 disclosed since T19). Zero regressions.
