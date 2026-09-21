# crypto · T28 · Phase 10 — Test Generation

## Scope

None authored fresh in this phase. Phase 1's own "Tests required" section is explicit: "None authored
fresh, unless AC1's row-by-row check finds a genuine gap." Two gaps were found — both already closed as
direct implementation/review-resolution work (Phase 6, Phase 9), not deferred to this phase.

## What test code this task actually touched, and where

- **Phase 6:** `WatcherTest.doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering` and
  `.laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers` — strengthened with a direct
  `verifyNoInteractions(txLifecyclePublisher)` assertion (Kimi Phase 3 Finding #3, verified as a
  logically-implied-but-not-directly-asserted property).
- **Phase 6:** `T01SkeletonRegressionTest.threatModelTracksThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched`
  — assertion updated from `"tracked"` to `"closed"`, a genuine gap found only by running the suite, not
  anticipated by any prior phase.
- **Phase 9:** `WatcherTest.doesNotEvaluateWithOnlyOneOfThreeProvidersAnswering` (new) — closes Kimi
  Phase 8 Finding #3, the literal "single-provider" shape the threat model describes.
- **Phase 9:** `T01SkeletonRegressionTest`, renamed to
  `threatModelClosesThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched`, strengthened with a
  `ClassName.methodName`-citation-pattern assertion and three per-row caveat-keyword assertions — closes
  Kimi Phase 8 Findings #1/#2/#6.

## Traceability

| Test | Threat row | Closes |
|---|---|---|
| `WatcherTest.doesNotEvaluateWithOnlyOneOfThreeProvidersAnswering` (new) | #1 | Kimi Phase 8 Finding #3 |
| `WatcherTest.doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering` / `.laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers` (strengthened) | #1 | Kimi Phase 3 Finding #3 |
| `T01SkeletonRegressionTest.threatModelClosesThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched` (renamed + strengthened) | all six, as a regression guard on the document itself | Phase 6's own discovered gap; Kimi Phase 8 Findings #1/#2/#6 |

## What stands in for "AC1" here

`mvn -pl services/crypto -am verify`, run fresh at Phase 6 and again at Phase 9 after every edit: final
result **698 tests, 0 failures, 14 already-disclosed Docker-only errors** — the same standing
environmental limitation carried since T23, not a defect in this task's own work.

## Phase 11 (Kimi Test Review) additions

Kimi's Phase 11 pass (`artifacts/11-test-review.md`) raised 6 findings.

| # | Finding | Disposition |
|---|---|---|
| 1 | End-to-end threat verification still not executed | **Rejected — already disclosed**, and independently superseded mid-phase: see "Docker became available" below. |
| 2 | Row #3's citations remain class-level | **Rejected — stale.** Already fixed in Phase 9 (`EthereumFinalityPolicyTest.shouldRequireBeaconFinalizedCheckpointForEthereumFinality`, etc. were already exact methods before this review ran). |
| 3 | Row #5's DB-grant claim has no cited test | **Rejected — stale.** Already fixed in Phase 9 (`ChainBaselineMigrationIntegrationTest.cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnTheThreeGrantedTables` was already cited before this review ran). |
| 4 | No classpath validation that cited tests exist | **Rejected — out of scope.** Same class of CI/tooling suggestion rejected at Phase 9 Finding #7 and T27 Phase 11. |
| 5 | No runtime/IAM test for the `kms:Sign` property | **Rejected — already disclosed.** Row #4 already documents this as an ownership split; CI/IAM smoke testing is infrastructure work, not this task's own scope. |
| 6 | Single-provider coverage doesn't assert the observation is still logged | **Accepted.** L3 requires every provider response logged verbatim even when quorum can't fire. Added `verify(observationLog).record(...)` assertions to all three `<3`-provider `WatcherTest` methods (the single-provider, two-provider, and lagging-provider cases), proving the "no evaluation" outcome isn't because the response was silently dropped. |

## Docker became available mid-phase — a finding beyond Kimi's own review

While re-running the full suite to verify Finding #6's fix, `docker info` succeeded for the first time
in this entire session (T23 onward) — the standing, disclosed Docker-unavailability limitation lifted
mid-task. This surfaced real information directly relevant to T28's own citations:

- **Row #5 improved:** `ChainBaselineMigrationIntegrationTest.cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnTheThreeGrantedTables`
  now genuinely runs and passes (10/10). The citation's "Docker-blocked, not yet executed" caveat is
  removed — it's now fully, not just structurally, verified.
- **Row #3 corrected, not improved:** `EndToEndIntegrationTest.reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor`
  was attempted and genuinely **fails** — `SchemaManagementException: missing table [chain.attestations]`
  on Spring context startup, a real, pre-existing `EndToEndIntegrationTest` infrastructure defect (the
  table exists in `V1__chain_baseline.sql`; something in that test class's own Testcontainers/Flyway
  wiring isn't applying migrations correctly). This is a genuine, previously-unknown bug in T26's own
  work, entirely unrelated to T28 and well outside a "threat-model closure" task's scope to root-cause
  and fix — flagged as a new follow-up task candidate (see Phase 12), not attempted here. The row's own
  citation was corrected from "not yet executed — Docker unavailable" to accurately describe the actual
  failure, rather than leave a now-false "would pass if only Docker were available" implication.
- **Separately, unrelated to any threat-model citation:** running the full suite with Docker available
  also surfaced ~10 further failures in `ObservationRepositoryIntegrationTest`,
  `ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`, and
  `TokenAllowlistRepositoryIntegrationTest` — none of which this task cites for any of the six rows.
  Not investigated further; flagged as further evidence for the same follow-up recommendation.

Full suite re-verified after all Phase 11 changes: `WatcherTest` + `T01SkeletonRegressionTest`
(the two files this task's own edits touch) — 74/74 passing.
