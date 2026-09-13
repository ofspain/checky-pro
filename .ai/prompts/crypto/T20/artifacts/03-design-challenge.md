# crypto · T20 · Phase 3 — Design Challenge Findings

Adversarial review of `artifacts/02-task-implementation-brief.md`. Only findings are listed below for folding into the brief in Phase 4.

---

## 1. The ArchUnit rule may be silently skipped by Maven Surefire

- **Issue:** The brief relies on `@ArchTest` fields in `KmsSignerArchitectureTest` to enforce R22/L11, but in this repository's Surefire configuration the ArchUnit JUnit 5 engine does not execute `@ArchTest` fields.
- **Severity:** High
- **Evidence:** `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` lines 298–315 documents the exact gap and adds plain `@Test` canaries that call `ArchRule.check(...)`; `services/crypto` inherits the same parent POM/Surefire setup.
- **Recommended brief amendment:** Add a required plain-JUnit canary test (e.g., `shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild`) that imports the same classes as `@AnalyzeClasses` and invokes the rule's `check(...)` directly. Keep the `@ArchTest` field for documentation, but do not rely on it alone to gate `mvn -pl services/crypto verify`.

---

## 2. The ArchUnit rule does not make `KmsSigner` the sole caller of `kms:Sign`

- **Issue:** The proposed rule only forbids classes *outside* `com.themistra.crypto.attest` from depending on `KmsSigner` or KMS SDK types. Another class *inside* `attest` could still call `KmsClient.sign(...)`, contradicting the task statement that `KmsSigner` is the sole `kms:Sign` caller.
- **Severity:** High
- **Evidence:** `02-task-implementation-brief.md` AC1 bans external dependencies on `KmsSigner` and `software.amazon.awssdk.services.kms..`; it says nothing about who inside `attest` may call `KmsClient.sign(...)`.
- **Recommended brief amendment:** Add a second ArchUnit condition/rule (and canary test) asserting that only `com.themistra.crypto.attest.KmsSigner` may call `KmsClient.sign(...)` or build a `SignRequest`. Update AC1, or add AC1b, to cover this internal restriction.

---

## 3. Rule scope must exclude the auth service's existing KMS usage

- **Issue:** `services/auth` already uses `software.amazon.awssdk.services.kms.KmsClient` for MFA seed encryption under ADR-0003. If the new crypto ArchUnit test imports `com.themistra.*`, it will fail on auth classes.
- **Severity:** High
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/mfa/MfaSeedEncryption.java` imports `KmsClient`; `docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md` states the crypto KMS exception is scoped to `services/crypto` only.
- **Recommended brief amendment:** Define the analyzed package as `com.themistra.crypto` (not `com.themistra`), and add a test comment clarifying that auth-service KMS usage is governed by ADR-0003 and is intentionally outside this rule's scope.

---

## 4. `SignatureResult.kmsKeyId` source is ambiguous

- **Issue:** The brief says the `Sign` request uses `KmsProperties.keyId()`, but it never defines what `SignatureResult.kmsKeyId()` should contain. If the configured value is an alias or raw key id while KMS returns a canonical ARN, the verification-keys endpoint (R24) and receipt verifiers may use the wrong identifier.
- **Severity:** Medium
- **Evidence:** `spec/crypto-service/design.md` §4c requires the HTTP response to carry `kmsKeyId`; `02-task-implementation-brief.md` only constrains the request key identifier (AC2).
- **Recommended brief amendment:** Specify that `SignatureResult.kmsKeyId()` equals `KmsProperties.keyId()` unless a deliberate decision is made to use `SignResponse.keyId()`, and state that task 22 must publish keys using the same identifier.

---

## 5. Base64 encoding variant is unspecified

- **Issue:** `SignatureResult.signatureBase64` will be embedded in the JSON response as `signature` (`design.md` §4c). Different Base64 variants (standard, URL-safe, with/without padding, line-wrapped) will break downstream verification.
- **Severity:** Medium
- **Evidence:** `02-task-implementation-brief.md` introduces `signatureBase64` but does not name the encoder; `spec/crypto-service/design.md` §4c only says `<base64>`.
- **Recommended brief amendment:** State that `KmsSigner` must use standard RFC 4648 Base64 without line breaks, and require the success test to assert the encoded value matches that variant.

---

## 6. `signedAt` needs an injectable `Clock`

- **Issue:** `SignatureResult.signedAt` is an `Instant`, but the brief does not say how it is produced. Using `Instant.now()` directly would violate the service's standing time rule.
- **Severity:** Medium
- **Evidence:** `spec/crypto-service/agents.md` platform rules state: No `java.util.Date`; use `java.time` with an injectable `Clock`. `02-task-implementation-brief.md` does not list `Clock` as a dependency of `KmsSigner`.
- **Recommended brief amendment:** Add `java.time.Clock` as a constructor dependency of `KmsSigner`, use `clock.instant()` for `signedAt`, and require the success test to assert `signedAt` equals the fixed test clock's instant.

---

## 7. `ECDSA_SHA_256` choice is premature while Q7 is open

- **Issue:** The brief hard-codes `SigningAlgorithmSpec.ECDSA_SHA_256` even though `package.md` Q7 (KMS key spec/algorithm) is formally unresolved. If Q7 closes to RSA or a different ECDSA curve, the implementation and its tests will be wrong.
- **Severity:** Medium
- **Evidence:** `02-task-implementation-brief.md` lines 23–25 call the algorithm proposed but then use it as the implementation default; `spec/crypto-service/package.md` §11 Q7 remains open and drives R20/R24.
- **Recommended brief amendment:** Either add `signingAlgorithm` to the otherwise frozen `KmsProperties` record with a brief-approved exception, or keep the algorithm as a constructor constant with an explicit pending-Q7 caveat and require the success test to read the value from that constant rather than hardcoding the enum literal.

---

## 8. No-region design defers validation to runtime

- **Issue:** `KmsSignerConfig` will not call `.region(...)`, relying on the SDK default region provider chain. A missing region will surface as a runtime `SdkClientException` when the client is built or used, not as a startup `@ConfigurationProperties` failure.
- **Severity:** Medium
- **Evidence:** `spec/crypto-service/agents.md` platform rules say startup should fail on missing/invalid config in non-local profiles; `02-task-implementation-brief.md` explicitly deviates from the `ObservationSnapshotStoreConfig` precedent by omitting region.
- **Recommended brief amendment:** Document that region is intentionally env/IRSA/instance-metadata driven, and add a test proving the `KmsClient` bean resolves or fails as expected when the region env var is present/absent. Consider a lightweight `@PostConstruct`/validator that fails fast if no region can be resolved in non-local profiles.

---

## 9. The named test describes an invocation check, but the rule is only a dependency check

- **Issue:** `package.md` §8 names the test `shouldOnlyAllowAttestPathToInvokeKmsSign`. Invoke means a method call, but the brief defines the rule as a dependency ban (no class outside `attest` may depend on `KmsSigner` or KMS SDK types). A class could depend on `KmsSigner` without ever invoking `sign(...)`, and a dependency rule alone does not prove invocation is limited to the attest path.
- **Severity:** Medium
- **Evidence:** `spec/crypto-service/package.md` §8 names the test; `02-task-implementation-brief.md` AC1 describes a dependency rule.
- **Recommended brief amendment:** Either rewrite the rule to assert that only classes in `attest` call `KmsClient.sign(...)`, or rename the test/rule to `shouldOnlyAllowAttestPathToDependOnKmsSign` and add a separate invocation assertion for the `sign(...)` method.

---

## 10. AC3 and AC6 lack automated enforcement

- **Issue:** AC3 (no local key material) and AC6 (no host-only assumptions) are acceptance criteria but the brief lists no tests for them, making them unenforceable in CI.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` AC3/AC6 have no corresponding required test; required tests only cover AC1/AC2/AC4/AC5.
- **Recommended brief amendment:** Add a static source-scan test for AC6 (no hardcoded paths, host names, or credential files in `KmsSigner`/`KmsSignerConfig`) and an ArchUnit/source-scan test for AC3 (`KmsSigner` does not import `java.security.KeyPairGenerator`, `KeyStore`, `SecretKey`, etc.).

---

## 11. No required test that digest/signature are not logged

- **Issue:** The brief's Security constraint forbids logging the signature or digest at INFO/WARN, but no test is required.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` Security paragraph mentions logging but required tests do not include a logging assertion; `ObservationSnapshotStoreTest` already uses this pattern (lines 158–179).
- **Recommended brief amendment:** Add a required unit test that captures `KmsSigner` log events and asserts neither the input digest bytes nor the output signature bytes appear at INFO or WARN.

---

## 12. Non-32-byte digest behavior is undefined

- **Issue:** `sign(byte[] digestSha256)` is documented to accept a 32-byte SHA-256 digest, but only `null` is rejected in AC5. A wrong-length array will be sent to KMS with `MessageType.DIGEST` and fail with a less useful error.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` AC5 only covers `null`; no length validation or test is specified.
- **Recommended brief amendment:** Either add an immediate `IllegalArgumentException` for `digestSha256.length != 32`, or explicitly document that length validation is KMS's responsibility and add a test proving the KMS `ValidationException` propagates for a wrong-length digest.

---

## 13. Contingent LocalStack test needs an endpoint-override story

- **Issue:** The brief mentions a possible LocalStack integration test but `KmsSignerConfig` has no endpoint/credential override hook. Without a test-only override, the integration test cannot point the production `KmsClient` bean at LocalStack.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` lines 99–103; `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationSnapshotStoreLocalStackIntegrationTest.java` shows the override pattern needed.
- **Recommended brief amendment:** If the contingent test is written, require a `@TestConfiguration` that overrides the `KmsClient` bean with `endpointOverride`, `region`, and `AwsBasicCredentials` from the LocalStack container, and use `Assumptions.assumeTrue` (not a fake pass) when LocalStack's asymmetric KMS support is absent.

---

## 14. Test sources may be excluded from the ArchUnit scan

- **Issue:** If the ArchUnit rule uses `ImportOption.DoNotIncludeTests`, a test class outside `attest` could import `KmsClient` without failing the rule, weakening R22.
- **Severity:** Low
- **Evidence:** `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` uses `DoNotIncludeTests`; ADR-0004 says no package in `services/crypto` may reference the KMS SDK outside `attest`.
- **Recommended brief amendment:** Either analyze test classes too, or add a separate static source-scan test over `src/test/java` that forbids `software.amazon.awssdk.services.kms` imports outside `com.themistra.crypto.attest`.

---

## 15. `SignatureResult` visibility could leak the signing abstraction

- **Issue:** The rule bans depending on `KmsSigner` and KMS types, but not on `SignatureResult`. If `SignatureResult` is public, a class outside `attest` could depend on it without tripping the rule.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` says `SignatureResult` is local to `attest` but does not specify visibility.
- **Recommended brief amendment:** Make `SignatureResult` package-private (it is only consumed by `AttestationService` in the same package), and add an ArchUnit assertion that no class outside `attest` depends on any public type returned by `KmsSigner.sign(...)`.
