# crypto · T20 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R22 — `kms:Sign` invoked only from the attest path; no other module/endpoint can reach the signer | Yes | `KmsSigner.sign` `attest/KmsSigner.java:105-125`; `KmsSignerArchitectureTest`'s two rules `attest/KmsSignerArchitectureTest.java:61-79` | `KmsSignerArchitectureTest.shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild` (named test) + `.bothRulesActuallyFailAgainstAGenuineViolation` (negative-proof, Phase 11) | No | None. |
| L11 — KMS-only signing, single path; ArchUnit + IAM enforced; no host-only assumption; Nitro-Enclave-portable | Yes | `attest/KmsSigner.java:69-97` (no local key material, no host-only literal, `resolveKmsClient` uses only the SDK's own default credential/region chain) | `KmsSignerTest.kmsSignerImportsNoKeyMaterialHandlingApi`, `.kmsSignerContainsNoHostOnlyLiteral` | No | IAM enforcement is infrastructure (AWS IAM policy), explicitly out of this codebase's scope per the frozen brief. |
| Named test `shouldOnlyAllowAttestPathToInvokeKmsSign` (`package.md` §8) | Yes | `attest/KmsSignerArchitectureTest.java` | Both tests in that file | No | None. |
| Schema fidelity / config — `KmsProperties.keyId()` used as the sole key identifier, never a hardcoded value | Yes | `attest/KmsSigner.java:113` | `KmsSignerTest.signCallsKmsWithTheExpectedRequestShape` | No | None. |
| Frozen brief AC1 (ArchUnit rule + canary, scoped to `com.themistra.crypto` only) | Yes | `attest/KmsSignerArchitectureTest.java:52-84` (verified against `services/auth`'s own `MfaSeedEncryption` and ADR-0004 before scoping) | Both architecture tests | No | None. |
| Frozen brief AC2 (request shape: `MessageType.DIGEST`, `SIGNING_ALGORITHM`, response mapping) | Yes | `attest/KmsSigner.java:112-124` | `KmsSignerTest.signCallsKmsWithTheExpectedRequestShape`, `.signMapsAResponseIntoTheExpectedSignatureResultFields`, `KmsSignerLocalStackIntegrationTest` (real end-to-end) | No | None. |
| Frozen brief AC3 (no key-material handling, automated) | Yes | `attest/KmsSigner.java` (no such import) | `KmsSignerTest.kmsSignerImportsNoKeyMaterialHandlingApi` | No | None. |
| Frozen brief AC4 (exceptions propagate uncaught) | Yes | `attest/KmsSigner.java:119` (no try/catch) | `KmsSignerTest.aKmsExceptionPropagatesUnwrapped` + parameterized `.anyKmsClientExceptionPropagatesUnwrapped` (Phase 11) | No | None. |
| Frozen brief AC5 (null / non-32-byte rejection) | Yes | `attest/KmsSigner.java:106-110` | `KmsSignerTest.rejectsNullDigestWithoutCallingKms`, `.rejectsAWrongLengthDigestWithoutCallingKms` | No | None. |
| Frozen brief AC6 (no host-only assumption, automated) | Yes | `attest/KmsSigner.java` (no such literal) | `KmsSignerTest.kmsSignerContainsNoHostOnlyLiteral` | No | None. |
| Frozen brief AC7 (no `Logger` field) | Yes | `attest/KmsSigner.java:69-134` (no such field) | `KmsSignerTest.kmsSignerDeclaresNoLoggerField` | No | None. |
| Self-Review Finding #1 (critical) — `KmsSigner` must be wireable as a Spring bean | Yes, fixed in-phase | `@Autowired` `attest/KmsSigner.java:79` | `KmsSignerSpringWiringTest.kmsSignerWiresUpAsASpringComponentWithOnlyPropertiesAndClockBeansPresent` | No | Discovered and fixed within Phase 7 rather than deferred to Phase 9 — disclosed in full in the self-review artifact, mirroring this pipeline's own T17 precedent for a load-bearing defect. |
| Phase 9 Finding #4 — `KmsSigner` closes its `KmsClient` on shutdown | Yes | `DisposableBean`/`destroy()` `attest/KmsSigner.java:70,130-133` | `KmsSignerTest.destroyClosesTheKmsClient` (Phase 11) | No | None. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every file the frozen brief (Phase 4) authorized exists:
`attest/SignatureResult.java`, `attest/KmsSigner.java`, `attest/KmsSignerArchitectureTest.java`, plus the
Phase 6-discovered `KmsSignerLocalStackIntegrationTest.java` and Phase 7-discovered
`KmsSignerSpringWiringTest.java` (both anticipated as contingent/necessary in the frozen brief itself).
`KmsSignerConfig.java`, originally planned, was deliberately not created — its responsibility was folded
into `KmsSigner` itself after implementation revealed a separate config class would itself trip the
ArchUnit rule it exists to enforce, a disclosed, well-justified deviation. Every review phase's findings
are fully resolved: Phase 3 (15 findings, all accepted), Phase 8 (4 findings, 3 accepted/1 rejected),
Phase 11 (6 gaps, 5 accepted/1 rejected) — every rejection grounded in verified, cited codebase precedent
or a genuine, explained implementation constraint, never an unexplained refusal.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC7 (frozen brief) all have
implementation evidence and test coverage per the matrix above, including the two criteria (AC3, AC6)
that are inherently negative/structural rather than interaction-based, and now including a genuine
negative-proof (Phase 11) that the ArchUnit rules can actually fail, not merely pass on already-clean
code.

**(3) Does it violate any LOCKED decision?** No. L11's "KMS-only signing, single path" is enforced
structurally (ArchUnit, with a verified negative-proof) rather than by convention alone; the Nitro-
Enclave-portability requirement is honored by construction (no host-only assumption anywhere, verified
by a structural test). No frozen file from a prior task (`V1`-`V9` migrations, `common/config/KmsProperties.java`,
any other feature module) was modified. The one file this task's own Phase 6 plan expected to create but
didn't (`KmsSignerConfig.java`) is a disclosed, justified scope adjustment, not a silent deviation.

**(4) Remaining risks?**
- **Q7 (KMS key type/algorithm) remains formally open** at the specification level. `SIGNING_ALGORITHM`
  is a single, clearly-flagged constant (`attest/KmsSigner.java:72`) — changing it once Q7 is answered
  requires editing exactly one line, by design.
- **The cross-thread/cross-request race on `KmsClient`'s own internal connection pool** is not this
  task's concern — the AWS SDK documents its clients as thread-safe, and this task introduces no
  additional concurrency of its own.
- **No compliance-queue, `AttestationService`, or HTTP endpoint exists yet** — by design; task 21 builds
  the caller that will gate signing on quorum + finality + screening before ever invoking `KmsSigner`.
- **The region-missing regression test (Phase 11) is environment-gated** (`Assumptions.assumeTrue`) —
  it only meaningfully asserts in an environment with no ambient AWS region configured anywhere; on a
  machine with real AWS credentials/config, it skips rather than asserting anything, a disclosed,
  deliberate trade-off to avoid ever false-failing on a legitimately-configured environment.

## Verdict

**PASS** — every in-scope requirement, acceptance criterion, and LOCKED decision is implemented, tested
(including a genuine negative-proof for the task's own central ArchUnit guarantee), and traced to
evidence; one critical defect (a Spring-wiring failure that would have broken any real deployment) was
caught by this task's own self-review before ever reaching independent review, fixed immediately, and
permanently regression-tested; the full module regression (658 tests) shows zero regressions and only
the same 6 pre-existing, disclosed, unrelated failing tests.
