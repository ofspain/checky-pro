# crypto · T29 · Phase 10 — Test Generation

## Scope

None authored fresh in this phase. Like T27/T28, this task's own tests were added directly as
implementation (Phase 6) and review-resolution (Phase 9) work, not deferred to this phase.

## What test code this task actually touched, and where

- **Phase 6:** `T01SkeletonRegressionTest.packageSpecHeaderReflectsReadyForImplAndVersionZeroTwo` (new)
  — guards the header bump itself.
- **Phase 9:** `T01SkeletonRegressionTest.resolvedOpenQuestionsCarryTheirResolutionNoteAndUnresolvedOnesDoNotClaimThisTasksResolution`
  (new) — closes Kimi Phase 8 Finding #6, guarding Q1/Q2/Q3/Q7's own resolution notes against a silent
  revert.
- **Phase 9:** `T01SkeletonRegressionTest.item13StaysHonestlyDisclosedAsGenuinelyFailingUntilTheFollowUpLands`
  (new) — closes Kimi Phase 8 Finding #9, guarding item 13's own honest disclosure against being
  silently marked complete.

## Traceability

| Test | Guards | Closes |
|---|---|---|
| `packageSpecHeaderReflectsReadyForImplAndVersionZeroTwo` | `package.md` header `Version`/`Status` | This task's own AC3 |
| `resolvedOpenQuestionsCarryTheirResolutionNoteAndUnresolvedOnesDoNotClaimThisTasksResolution` | `package.md` §11 Q1/Q2/Q3/Q7's resolution notes, and that Q4/Q5/Q6 don't falsely claim one | Kimi Phase 8 Finding #6 |
| `item13StaysHonestlyDisclosedAsGenuinelyFailingUntilTheFollowUpLands` | `package.md` §9 item 13's own honest "genuinely fails" disclosure | Kimi Phase 8 Finding #9 |

## What stands in for "AC1/AC2" here

`mvn -pl services/crypto -am verify`, run fresh at Phases 6, 7, and 9: final, stable result **762
tests, 6 failures, 4 errors** — the same pre-existing, unrelated set disclosed since T28 Phase 12, no
new regressions from this task's own edits.

## Phase 11 (Kimi Test Review) preview

Every test this task touched or added lives in `T01SkeletonRegressionTest` and is already covered by
Phase 9's own review-resolution pass (itself informed by Kimi's Phase 8 review, which reviewed these
exact three methods). If Kimi's Phase 11 pass finds anything, it will necessarily be about this same
file, not a dedicated new suite.
