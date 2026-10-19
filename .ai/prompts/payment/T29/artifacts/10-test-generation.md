# payment · T29 · Phase 10 — Test Manifest

Consumes:
- `artifacts/04-frozen-task-brief.md`
- `artifacts/09-review-resolution.md`

No production code changed. T29's tests were written during Phase 6 and hardened in Phase 9. **No new tests are needed in this phase:** every acceptance criterion and boundary below already maps to a committed, mutation-proven test.

All tests are plain JUnit (no Spring context, no `Clock`) in `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`. They read `../../spec/payment-service/package.md` and `src/test/java`.

## Manifest

| Test | Verifies | Boundaries covered | Mutations that fail it |
|---|---|---|---|
| `paymentSpecIsBumpedToReadyForImplVersionZeroPointTwo` | **AC3, AC4.** Version is `0.2` and Status is `READY FOR IMPL`. | The match is restricted to the header table, before `## 0.` (Phase 8 F6). The Version row is anchored, and the Status row is matched by prefix, so the disclosure sentence is tolerated. A negative assertion checks there is no `` `DRAFT` `` in the Status row. | M1 (Version `0.1`), M2 (Status `DRAFT`), M8 (`0.1` in the header while a stray `0.2` row sits outside it) |
| `everyTestNamedInTheVerificationChecklistExists` | **AC2, AC5.** Every cited test exists (Phase 3 F4, Phase 8 F1/F2/F4). | See "Citation guard boundaries" below. | M3, M9 (method typo), M4 (wrong class), M5 (class renamed) |
| `verificationChecklistKeepsItsPartialAndDeferredItemsUnticked` | **AC2.** Partials stay visible, and R26 stays deferred (Phase 8 F7). | Exactly 14 items. Items 1 and 13 start with `- [ ] ` and contain `**Partial —**`. Item 14 starts with `- [ ] **Not applicable this revision:**` and contains `deferred`. | M6 (item 1 ticked), M7 (item 13 ticked) |

**Citation guard boundaries.** `everyTestNamedInTheVerificationChecklistExists` handles four citation forms:

| Form | Rule |
|---|---|
| `Class.method` | Must be declared in that class's file |
| `.method` | Continues the previous class on the same line |
| bare `method` | Searched across all test sources |
| bare `…Test` class | Its file must exist |

It also asserts there are at least 25 citations, so a parse that finds nothing can't pass. The only exemption is the single deferred R26 name.

## AC coverage

| AC | Covered by |
|---|---|
| AC1 (gates) | The full suite itself: `mvn -o -pl services/payment verify` 535/535, plus `SECURITY-THREAT-MODEL.md` row #5. This is process evidence, as in crypto T29. |
| AC2 (all 14 items addressed, partials visible) | `verificationChecklistKeepsItsPartialAndDeferredItemsUnticked`, `everyTestNamedInTheVerificationChecklistExists` |
| AC3 (header values) | `paymentSpecIsBumpedToReadyForImplVersionZeroPointTwo` |
| AC4 (header guard) | same, mutation-proven by M1, M2, M8 |
| AC5 (citation guard) | `everyTestNamedInTheVerificationChecklistExists`, mutation-proven by M3, M4, M5, M9 |

## Not covered by a test

- **Whether each `[x]` item is actually true.** That is reviewer judgement (Phases 8, 11 and 12). The guards prove only that the cited tests exist and that partials stay marked.
- **Item 2 (LOCKED decisions)** rests on the per-task Phase 12 verifications (Phase 7 S4).

## Run

`mvn -o -pl services/payment verify` from the repo root: **535/535, BUILD SUCCESS**. `T01SkeletonRegressionTest`: 12/12.
