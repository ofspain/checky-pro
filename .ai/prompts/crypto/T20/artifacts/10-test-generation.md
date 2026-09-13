# crypto · T20 · Phase 10 — Test Generation

**Process note.** Per this task's own Phase 6 implementation notes (and the established T19 precedent),
tests were written alongside production code — there was no existing caller to manually exercise
`KmsSigner`/`KmsSignerArchitectureTest` against, and the ArchUnit rule's own correctness could only be
proven by attempting to run it. Additional tests were added at Phase 7 (the Spring-wiring regression
test, discovered necessary by a critical self-review finding) and Phase 9 (none — Phase 9's fixes were
production-code/rule changes, already covered by the existing suite). No production code changes in
this phase — this artifact is the traceability manifest.

## Test files

| File | Tests | Purpose |
|---|---|---|
| `attest/KmsSignerTest.java` | 8 | Request shape, response mapping, exception propagation, null/length validation, and 3 structural source-scan tests (no key-material API, no host-only literal, no `Logger` field). |
| `attest/KmsSignerArchitectureTest.java` | 1 | The named test `shouldOnlyAllowAttestPathToInvokeKmsSign` — two ArchUnit rules plus the plain-JUnit canary that actually gates `mvn test`. |
| `attest/KmsSignerSpringWiringTest.java` | 1 | Proves `KmsSigner` is actually wireable as a Spring `@Component` (added at Phase 7 after a critical `@Autowired` omission was discovered and fixed). |
| `attest/KmsSignerLocalStackIntegrationTest.java` | 1 | Real KMS-API round-trip: creates a real asymmetric key against LocalStack, signs a digest through `KmsSigner`, verifies the signature against the key's own public key. |

**Total: 11 tests**, all passing.

## Traceability matrix

| Test | AC / Requirement | What it proves |
|---|---|---|
| `KmsSignerArchitectureTest.shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild` | AC1 (R22/L11, named test) | No class outside `attest` references `KmsSigner`; no class outside a `KmsSigner*`-in-`attest` exception depends on the KMS SDK at all — scoped to `com.themistra.crypto`, scanning both main and test sources. |
| `KmsSignerTest.signCallsKmsWithTheExpectedRequestShape` | AC2 | `sign(...)` builds the request with `MessageType.DIGEST`, `SigningAlgorithmSpec.ECDSA_SHA_256`, and the configured `keyId`. |
| `KmsSignerTest.signMapsAResponseIntoTheExpectedSignatureResultFields` | AC2 (Findings #4/#5/#6) | `kmsKeyId` comes from the KMS response (not the input config); `signatureBase64` is standard Base64; `signedAt` comes from the injected `Clock`. |
| `KmsSignerTest.aKmsExceptionPropagatesUnwrapped` | AC4 | A `KmsException` from `KmsClient.sign(...)` propagates uncaught. |
| `KmsSignerTest.rejectsNullDigestWithoutCallingKms` | AC5 | `sign(null)` throws `NullPointerException` before any KMS call. |
| `KmsSignerTest.rejectsAWrongLengthDigestWithoutCallingKms` | AC5 | `sign(<non-32-byte>)` throws `IllegalArgumentException` before any KMS call. |
| `KmsSignerTest.kmsSignerImportsNoKeyMaterialHandlingApi` | AC3 | No `KeyPairGenerator`/`KeyStore`/`SecretKey`/`PrivateKey` import in `KmsSigner.java`. |
| `KmsSignerTest.kmsSignerContainsNoHostOnlyLiteral` | AC6 | No `file:`/`/etc/`/`localhost`/`127.0.0.1` literal in `KmsSigner.java`. |
| `KmsSignerTest.kmsSignerDeclaresNoLoggerField` | AC7 (Finding #11) | `KmsSigner` declares no field of a `Logger` type. |
| `KmsSignerSpringWiringTest.kmsSignerWiresUpAsASpringComponentWithOnlyPropertiesAndClockBeansPresent` | Self-Review Finding #1 (critical, fixed) | `KmsSigner` resolves to its `@Autowired` public constructor and wires correctly in a real `ApplicationContext` with only `KmsProperties`/`Clock` beans present — the regression test for the critical bug this phase's own self-review discovered and fixed. |
| `KmsSignerLocalStackIntegrationTest.producesARealSignatureVerifiableAgainstTheKeysOwnPublicKey` | AC2, end-to-end | A real LocalStack-emulated `Sign` call through `KmsSigner`'s actual production code path (test-seam constructor) produces a signature that verifies against the key's own public key; empirically confirms `kmsKeyId()` differs from the configured bare key id (the response carries the full ARN). |

## Verification run

`mvn -pl services/crypto test -Dtest=KmsSignerTest,KmsSignerArchitectureTest,KmsSignerLocalStackIntegrationTest,KmsSignerSpringWiringTest`
— 11/11 pass.

Full module regression (`mvn -pl services/crypto -am test`): 651 tests, 6 failures, all pre-existing and
unrelated to this task (disclosed since T18/T19: `ObservationRepositoryIntegrationTest`,
`ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`,
`TokenAllowlistRepositoryIntegrationTest`). Zero regressions.

## Gaps

None identified beyond what Phase 7/8/9 already surfaced and resolved (at the time this section was
first written — see the Phase 11 additions below).

## Phase 11 (Kimi Test Review) additions

Per this pipeline's own Phase 11 convention, no separate resolution artifact is written — accepted
findings are folded directly into the test suite. Kimi raised 6 gaps; 5 were accepted and closed with
new tests (plus one new fixture file), 1 was rejected.

| Gap | Disposition | Test/fixture added |
|---|---|---|
| 1. No negative-proof that the ArchUnit rules actually fail on a real violation | **ACCEPTED** | New fixture `archtestfixtures.RogueAttestReferencer` (a standalone top-level package outside `com.themistra.crypto` entirely, so the real canary's package-wide scan can never sweep it up) plus `KmsSignerArchitectureTest.bothRulesActuallyFailAgainstAGenuineViolation`, which imports only that one fixture class (and `KmsSigner`) into an isolated `JavaClasses` set and asserts both rules throw `AssertionError` — verified directly via a standalone diagnostic that `ArchRule.check(...)` throws plain `java.lang.AssertionError`, not an ArchUnit-specific subtype. `services/auth`'s real `MfaSeedEncryption` was considered as a ready-made violation instead of a new fixture, but rejected: `services/crypto` has no dependency on `services/auth` at all, and adding one would itself violate `agents.md`. |
| 2. `KmsSigner.destroy()` is untested | **ACCEPTED** | `KmsSignerTest.destroyClosesTheKmsClient`. |
| 3. `SignatureResult`'s package-private visibility is not locked by a test | **ACCEPTED** | `KmsSignerTest.signatureResultIsPackagePrivate`. |
| 4. No regression test for startup fail-fast when the AWS region is missing | **ACCEPTED** | `KmsSignerSpringWiringTest.kmsSignerBeanFailsFastAtStartupWhenNoAwsRegionIsResolvable` — gated by `Assumptions.assumeTrue(...)` on the *ambient* default region chain (independent of the system property this test itself manages) already resolving nothing, so it never false-passes or flakes on a machine with real AWS credentials/config/IMDS reachable, mirroring the same "skip, never fake-pass" principle already used for the contingent LocalStack test. |
| 5. Production `resolveKmsClient()` path never exercised against a real endpoint (only the test-seam constructor is) | **REJECTED** | Would require either a test-only endpoint-override seam added to production code (no precedent anywhere in this codebase) or mutating real OS environment variables from a running JVM (no clean portable Java API, no precedent here either). The existing `KmsSignerLocalStackIntegrationTest` (proves `sign()`'s real logic against real infrastructure) and `KmsSignerSpringWiringTest` (proves the public constructor/`resolveKmsClient()` path doesn't throw and produces a working bean when Spring-wired) already jointly cover the meaningful risk. |
| 6. Exception-propagation test covers only `KmsException` | **ACCEPTED** | `KmsSignerTest.anyKmsClientExceptionPropagatesUnwrapped`, parameterized over `KmsException`, `SdkClientException`, and `IllegalArgumentException` (`junit-jupiter-params` is already used elsewhere in this codebase). |

**Verification run (Phase 11):**
`mvn -pl services/crypto test -Dtest=KmsSignerTest,KmsSignerArchitectureTest,KmsSignerLocalStackIntegrationTest,KmsSignerSpringWiringTest`
— 18/18 pass (was 11/11 before this phase's 7 new test executions). Full module regression: 658 tests,
same 6 pre-existing unrelated failing tests, zero regressions (one transient Testcontainers/Docker
connection error was observed on a single run and did not reproduce on immediate re-run — unrelated to
any code in this task).
