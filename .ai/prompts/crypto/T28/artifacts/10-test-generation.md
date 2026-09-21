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

## Phase 11 (Kimi Test Review) preview

Every test this task touched or added is now already covered by Phase 9's own review-resolution pass
(itself informed by Kimi's Phase 8 review). If Kimi's Phase 11 pass finds anything, it will necessarily
be about these same three test methods, not a dedicated new suite.
