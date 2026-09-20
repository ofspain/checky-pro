# crypto · T20 · Phase 5 — Implementation Plan

Every file traces to the frozen brief's (`artifacts/04-frozen-task-brief.md`) Files to Create section.
API surface below (`KmsClient`, `SignRequest`, `SignResponse`, `SigningAlgorithmSpec`, `MessageType`,
`SdkBytes`) was verified directly against the actual `kms-2.50.2.jar`/`sdk-core-2.50.2.jar` bytecode via
`javap` — not assumed from memory. `KmsClient.sign(SignRequest)` throws only unchecked exceptions
(`KmsException` extends `AwsServiceException` extends `SdkException`, all `RuntimeException`), confirming
the frozen brief's "propagate uncaught" decision requires no special unwrapping.

## Files to create

1. `services/crypto/src/main/java/com/themistra/crypto/attest/SignatureResult.java`
2. `services/crypto/src/main/java/com/themistra/crypto/attest/KmsSigner.java`
3. `services/crypto/src/main/java/com/themistra/crypto/attest/KmsSignerConfig.java`
4. `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerTest.java`
5. `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java`
6. (Contingent on direct LocalStack verification during this phase's own execution — see below)
   `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerLocalStackIntegrationTest.java`

## Files to modify

None.

## Public methods (signatures)

- `SignatureResult` — package-private record:
  `record SignatureResult(String signatureBase64, String kmsKeyId, Instant signedAt) {}`
- `KmsSigner`:
  - `public KmsSigner(KmsClient kmsClient, KmsProperties properties, Clock clock)` (constructor
    injection, matching this codebase's universal convention).
  - `public SignatureResult sign(byte[] digestSha256)` — the sole public method, and (per the frozen
    brief) the only method anywhere in `com.themistra.crypto` permitted to depend on
    `software.amazon.awssdk.services.kms..`.
- `KmsSignerConfig`:
  - `@Bean public KmsClient kmsClient()` — mirrors `ObservationSnapshotStoreConfig.s3Client(...)`'s
    shape: `KmsClient.builder().overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(API_CALL_TIMEOUT).build()).build()`.
    No `.region(...)` call (frozen brief Finding #8 — SDK default region provider chain).
  - `@Bean public KmsSigner kmsSigner(KmsClient kmsClient, KmsProperties properties, Clock clock)`.

## Private methods

- `KmsSigner` — no private helper methods planned; `sign(...)` is small enough (validate → build
  request → call → map response) to stay one method, matching `FailClosedScreeningClient.screen`'s own
  precedent for a method of comparable size and branching complexity.

## Constants

- `KmsSigner.SIGNING_ALGORITHM = SigningAlgorithmSpec.ECDSA_SHA_256` (`private static final`,
  Javadoc-flagged pending Q7).
- `KmsSignerConfig.API_CALL_TIMEOUT = Duration.ofSeconds(5)` (`private static final`, mirrors
  `ObservationSnapshotStoreConfig`'s identical fixed-timeout precedent and value).

## `sign(byte[] digestSha256)` — verified call shape

```
Objects.requireNonNull(digestSha256, "digestSha256");
if (digestSha256.length != 32) throw new IllegalArgumentException("digestSha256 must be exactly 32 bytes (SHA-256), was " + digestSha256.length);

SignRequest request = SignRequest.builder()
        .keyId(properties.keyId())
        .message(SdkBytes.fromByteArray(digestSha256))
        .messageType(MessageType.DIGEST)
        .signingAlgorithm(SIGNING_ALGORITHM)
        .build();

SignResponse response = kmsClient.sign(request);   // unchecked KmsException propagates uncaught

return new SignatureResult(
        Base64.getEncoder().encodeToString(response.signature().asByteArray()),
        response.keyId(),
        clock.instant());
```

No `Logger` field anywhere in this class (frozen brief Finding #11) — no import of `org.slf4j.*`.

## Entities used

None.

## Repositories used

None.

## Services used

`KmsProperties` (existing, T03), `Clock` (existing bean, `common.ClockConfig`).

## `KmsSignerArchitectureTest` — verified rule shape

Two rules, both scoped to `com.themistra.crypto` (never `com.themistra` — Finding #3), both kept as
`@ArchTest` fields for documentation (per `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`'s
own established, already-in-this-parent-POM precedent) plus a plain `@Test` canary that actually gates
`mvn test` (Finding #1):

```java
@AnalyzeClasses(packages = "com.themistra.crypto")
class KmsSignerArchitectureTest {

    @ArchTest
    static final ArchRule noClassOutsideAttestMayReferenceKmsSigner = noClasses()
            .that().resideOutsideOfPackage("com.themistra.crypto.attest..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.themistra.crypto.attest.KmsSigner")
            .because("R22/L11: kms:Sign is reachable only from the attest module");

    @ArchTest
    static final ArchRule onlyKmsSignerMayUseTheKmsSigningSdk = noClasses()
            .that().doNotHaveFullyQualifiedName("com.themistra.crypto.attest.KmsSigner")
            .should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk.services.kms..")
            .because("ADR-0004/L11: kms:Sign is invoked only from KmsSigner itself - no other class "
                    + "in attest (or elsewhere) may depend on the KMS SDK at all, which necessarily "
                    + "covers building a SignRequest or calling KmsClient.sign(...) directly");

    // Kimi Phase 3 Finding #1: @ArchTest fields are not executed under this repo's Surefire setup
    // (confirmed via auth's own ArchitectureTest.java negative-proof comment) - this canary imports
    // the identical class set (main AND test sources - Finding #14, no DoNotIncludeTests) and invokes
    // check(...) directly so mvn test actually gates this rule.
    private static final JavaClasses analyzedClasses = new ClassFileImporter()
            .importPackages("com.themistra.crypto");

    @Test
    void shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild() {
        noClassOutsideAttestMayReferenceKmsSigner.check(analyzedClasses);
        onlyKmsSignerMayUseTheKmsSigningSdk.check(analyzedClasses);
    }
}
```

Reasoning for collapsing the frozen brief's two conceptual conditions into this exact two-rule shape:
`onlyKmsSignerMayUseTheKmsSigningSdk` bans *any* dependency on the whole `kms` SDK package from every
class except `KmsSigner` — this single, package-wide ban already subsumes Finding #2's narrower "only
KmsSigner may call `KmsClient.sign(...)`/build a `SignRequest`" concern (any such call necessarily
requires depending on `KmsClient`/`SignRequest`, both under `software.amazon.awssdk.services.kms..`),
and is simpler and more robust than a method-call-specific `ArchCondition`. This exact shape
(`noClasses().that().doNotHaveFullyQualifiedName(X).should().dependOnClassesThat().resideInAPackage(Y)`)
is `services/auth/.../ArchitectureTest.java`'s own `only_MfaSeedEncryption_may_use_the_aws_sdk` rule,
verified to already compile and run under this exact parent POM/ArchUnit version.

## Structural source-scan tests (in `KmsSignerTest.java`, mirroring `ScreeningResultTest`'s reflection style)

- AC3 (no key-material handling): assert no import line in `KmsSigner.java`/`KmsSignerConfig.java`
  contains `java.security.KeyPairGenerator`, `java.security.KeyStore`, `javax.crypto.SecretKey`, or
  `java.security.PrivateKey`.
- AC6 (no host-only assumption): assert no import/line contains a literal `file:`, `/etc/`, `localhost`,
  or a hardcoded IP-looking pattern in either file.
- AC7 (no `Logger` field, Finding #11): `Arrays.stream(KmsSigner.class.getDeclaredFields()).noneMatch(f -> f.getType().getName().contains("Logger"))`.

## Verification run for the contingent LocalStack test

Before writing `KmsSignerLocalStackIntegrationTest.java`, this phase directly attempts, against the
same pinned `localstack/localstack:3.8` image already used by
`ObservationSnapshotStoreLocalStackIntegrationTest`: `CreateKey` with `KeyUsage=SIGN_VERIFY`,
`KeySpec=ECC_NIST_P256`, then `Sign` with `MessageType.DIGEST`/`SigningAlgorithmSpec.ECDSA_SHA_256`. If
this succeeds, the test is written per the frozen brief's Finding #13 disposition (a `@TestConfiguration`-
style override, `endpointOverride`/region/`AwsBasicCredentials` from the container). If it does not
(LocalStack Community's KMS emulation is known in some versions to support only symmetric keys), the
test is either omitted with a documented reason, or written with `Assumptions.assumeTrue(...)` gating a
skip — never a fake pass. This is a genuine unknown per Phase 0 — resolved by direct execution now, not
assumption.

## Execution order

1. `SignatureResult` (no dependencies).
2. `KmsSigner` (depends on `SignatureResult`, `KmsProperties`, `Clock`, the KMS SDK).
3. `KmsSignerConfig` (depends on `KmsSigner`, `KmsProperties`).
4. `KmsSignerTest` (unit tests + the 3 structural source-scan tests).
5. Direct LocalStack verification (see above), then conditionally `KmsSignerLocalStackIntegrationTest`.
6. `KmsSignerArchitectureTest` last, since it scans the finished package's full file set (mirrors
   `ScreeningModuleBoundaryTest`'s own established "boundary test last" ordering).

## Open Questions

No blockers (unchanged from the frozen brief).
