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

## Phase 11 (Kimi Test Review) additions

Kimi's Phase 11 pass (`artifacts/11-test-review.md`) raised 6 findings, all about the same three test
methods, as predicted above.

| # | Finding | Disposition |
|---|---|---|
| 1 | Item-13 guard checks the "genuinely fails" prose but not the `[ ]` checkbox itself | **Accepted.** `item13StaysHonestlyDisclosedAsGenuinelyFailingUntilTheFollowUpLands` now asserts the specific item-13 line both starts with `- [ ]` and contains the prose, on the same line — a future edit satisfying only one half now fails. |
| 2 | Q4/Q5/Q6 guard is tied to today's specific resolution date | **Accepted.** Strengthened to the stronger invariant: the line must not contain `"Resolved ("` at all, not just not-today's-date. |
| 3 | Resolution guard only checks the first line of each Q-bullet, not wrapped continuation lines | **Rejected.** Verified directly: every §11 bullet, including Q1's own 872-character line, is a single continuous line — matches this file's own established, deliberate long-single-line convention (same as `SECURITY-THREAT-MODEL.md`'s rows). No wrapped line currently exists to miss. |
| 4 | Header guard is brittle to markdown table formatting | **Rejected — same class of concern already rejected at Phase 9** for the identical reason: exact-substring matching is this file's own established style. |
| 5 | No guard that §9 items 1–12 stay checked and item 13 is the only unchecked one | **Accepted.** Added `exactlyThirteenOfSectionNinesFourteenItemsAreCheckedAndOnlyItemThirteenIsNot`, scoped to lines between the §9 and §10 headers (verified no other checkbox line exists anywhere else in the file), asserting the 13/1 split. |
| 6 | Tests assume a specific working directory | **Rejected — matches established, working convention.** Kimi's own finding marks this "(Optional)" and defers to "keep the existing style if the build already standardizes the working directory" — it does, identically, for every other test in this file. |

Full suite re-verified after the accepted fixes: 763 tests (762 + 1 new), 0 new failures, the same 6
failures / 4 errors already disclosed since T28.
