# crypto · T28 · Phase 6 — Implementation Notes

## Files modified

- `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java` — two additive
  `verifyNoInteractions(txLifecyclePublisher)` lines, exactly as pinned in Phase 5.
- `SECURITY-THREAT-MODEL.md` — header wording, `Status` column (rows #1–#6), and the trailing
  explanatory paragraph (see "Beyond Phase 5's plan" below).
- `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java` — one assertion
  updated (see below); not anticipated by any prior phase, found only by running the suite.

## What ran, in order

1. Applied the two `WatcherTest.java` edits exactly as pinned. `mvn -pl services/crypto -am test
   -Dtest=WatcherTest` — 67/67 passing.
2. Applied the `SECURITY-THREAT-MODEL.md` header + `Status` column edits exactly as pinned.
3. Ran the full `mvn -pl services/crypto -am verify`. Result: **697 tests, 1 new failure** (not the
   expected 0) — `T01SkeletonRegressionTest.threatModelTracksThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched`,
   asserting each of rows #1–#6 `.contains("tracked")`. This is a real, in-scope finding, not a defect
   introduced by this task's own edits: the test's own Javadoc labels it "AC1: SECURITY-THREAT-MODEL.md
   threats #1-6 are tracked with an owning task" — a T01-era regression guard written when `tracked` was
   the only correct state, now correctly superseded by T28's own purpose (closing those rows). Updated
   the assertion to `.contains("closed")` and its Javadoc to explain why, rather than leave a
   self-contradicting regression guard in the suite.
4. Re-ran the full `mvn -pl services/crypto -am verify`. Result: **697 tests, 0 failures, 14 errors** —
   the same 14 already-disclosed Docker-only errors as T27 (Testcontainers/LocalStack unable to find a
   Docker daemon). AC1a satisfied.

## Beyond Phase 5's plan

Phase 5 scoped the `SECURITY-THREAT-MODEL.md` edit to the header (line 3) and the `Status` column. While
applying it, the document's own trailing paragraph (originally: "`tracked` means each is mapped to the
task that closes it, not yet implemented (`services/crypto` has no application code as of crypto-service
T01)") was left directly self-contradicting the edits just made — the same internal-consistency concern
Kimi's Phase 3 Finding #8 raised about the header applies equally here. Updated it to describe what
`closed` now means and to correctly attribute infrastructure/cross-service-scoped rows (#3–#5) to their
own Status cells rather than claim blanket closure. Scope: wording only, no threat/mitigation text
touched, no change to rows #7–#8.

## Row-by-row citation (final, matches `SECURITY-THREAT-MODEL.md`)

| Row | Test(s) cited | Scope note |
|---|---|---|
| 1 | `QuorumEvaluatorTest.shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree`; `WatcherTest.doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering` / `.laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers` (now with `verifyNoInteractions(txLifecyclePublisher)`) | Fully closed |
| 2 | `TokenValidatorTest.shouldIdentifyTokenByContractAddressNotSymbol` / `.shouldSurfaceUnknownTokenForNonAllowlistedContract` | Fully closed |
| 3 | `EthereumFinalityPolicyTest`, `TronFinalityPolicyTest`, `ReorgDetectorTest` | Unit-level only; integration-level proof (`EndToEndIntegrationTest.reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor`) not yet executed — Docker unavailable |
| 4 | `KmsSignerArchitectureTest` | Code-path only; IAM/runtime half is infrastructure-owned |
| 5 | `ObservationLogTest.recordAttemptsTheS3WriteBeforeThePostgresInsert` | crypto-service's own portion only; S3 Object Lock/WORM is infrastructure; hash-chain ledger/on-chain anchor are Payment-Service-owned |
| 6 | `AddressPoisoningDetectorTest.shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` | Fully closed |

## Final verification

`mvn -pl services/crypto -am verify`: **697 tests, 0 failures, 14 errors** (all 14 confirmed
Docker-daemon-unavailability, matching every prior task's own disclosed pattern since T23). AC1b (full
`verify` including Testcontainers) remains deferred to a Docker-available CI environment, disclosed
explicitly in the threat-model's own row #3 Status cell and in this artifact — not silently claimed
passing.
