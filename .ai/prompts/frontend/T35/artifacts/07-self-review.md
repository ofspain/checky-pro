<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review) -->

# frontend · T35 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | `artifacts/06-implementation.md` |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |

## Mutation score

**14/14 regressions killed · 1/1 canary killed · 0 survivors · 0 broken.** Each mutant's failure count is
recorded in the run, because a kill count alone does not show that a mutant died through the instrument meant
for it.

| Mutant | Criterion | Failures |
|---|---|---|
| SO01 the held token is never dropped | AC1 | 11 |
| SO02 cleared asynchronously, leaving a window | AC1, AC4 | 14 |
| SO03 cleared LAST, after three steps that can throw | AC1 order | 3 |
| SO04 a stale notice survives | AC3 | 5 |
| SO05 the three authorize items survive | AC3 | 6 |
| SO06 the return target survives | AC3 | 6 |
| SO07 the consuming reader is used instead | AC6 | 6 |
| SO08 the store is cleared wholesale | AC6 | 8 |
| SO09 the handler takes the reason back | AC5 | 2 |
| SO10 sign-out becomes asynchronous | AC4 | 6 |
| SO11 the new primitive loses its guard | AC12 | 2 |
| SO12 clearing twice becomes an error | AC2 | **303** |
| SO13 an unaccounted client-side key in production | AC8 | 4 |
| SO14 a module-scope mutable appears | AC7 | 2 |
| **SO15** the cross-tab adoption is removed *(canary)* | AC9 | 11 |

**Two coarse kills, verified narrowly** rather than trusted. SO12 breaks `clearAccessToken` for the whole
package, so 303 failures say nothing about T35; filtered to AC2 it fails **exactly its two cases**. SO15
changes an adoption path task 6 tests in depth; filtered to AC9 it fails **exactly its one case**. That is
T33's lesson applied: a coarse kill needs narrow verification, because the `own` column cannot say *which*
case saw it.

## S1 — the survivor, and what it found

**SO03 survived the suite as first written**, and it is the finding of this phase. The mutant moves
`clearAccessToken()` from first to last. `signOut.ts` states in so many words that the position is
load-bearing; AC1 asserted the *outcome*; **nothing asserted the sequence.**

Why it was invisible: none of the three later steps can throw in real life — each is guarded inside its own
module — so there is no natural execution in which the order is observable. That is exactly what made the
claim feel safe and exactly what made it unmeasured.

Closed with two cases, deliberately not one:

- The **behavioural** one spies `consumeAuthorizeState` to throw and asserts the token is already `null` when
  it does. This is the real measurement. It works because Vitest keeps a named import as a live binding —
  the technique task 33 landed on after an own-property spy on jsdom's proxied `Storage` silently intercepted
  nothing.
- The **source** one pins the statement order directly. Kept *beside* the behavioural one and not instead of
  it, because it would still pass if the clear were moved into a branch that never runs.

Third task running for the same lesson: **designing the mutant finds the hole; re-reading the cases does
not.**

## S2 — what the suite gets right

- **The hazard is pinned by measurement, not by prose.** AC9 drives a real `BroadcastChannel` and shows a
  signed-out tab holding a sibling's token on the next tick. Phase 5 promised to withdraw OQ-T35-3 if it did
  not reproduce; it reproduces.
- **SO15 is the inverse mutant and it is the point of the canary.** Because AC9 asserts the behaviour *as it
  is*, the mutant that kills it is the one that **fixes** the hazard. The kill is the proof that the day task
  39 closes OQ-T35-3, its author is told by a failing case carrying the instruction rather than finding out
  later.
- **SO07 is the mutant that justifies a whole design decision.** It swaps `clearReturnTarget` back to
  `takeReturnTarget` — Kimi's F3 — and the key still gets removed, so every behavioural case passes. Only the
  storage-operation count catches it. Without AC6 counting reads, F3's fix would have been unfalsifiable
  taste.
- **Every scan has a control**, and SO13 and SO14 prove the two production scans catch a planted change
  rather than merely reporting clean.
- **AC8 asserts its inventory in both directions**, so a stale row is a failure rather than a pass.
- **The zero-parameter claim is stronger than a test**: `signOut(undefined as never)` is a compile error, and
  the case records that instead of a runtime assertion.

## S3 — the weakest things, stated plainly

1. **Nothing calls `signOut`, and no test can fix that.** Every behavioural case is a statement about how the
   function behaves when called. T35 is the sixth built-and-dormant task. **OQ-T35-2.**
2. **AC9 asserts a defect.** It is the right instrument available and it is still unusual: a green suite here
   means a known hazard is still present. The failure message is written so that the *good* outcome reads as
   an instruction rather than a mystery, but a reader skimming case names will see a passing test beside a
   real problem. **OQ-T35-3.**
3. **AC7 cannot see closure state**, and says so. `crossTabRenewal.ts` and `sessionBoundary.ts` hold theirs in
   factory closures, unreachable from a zero-argument function. That limitation is not theoretical — it *is*
   AC9's hazard.
4. **I widened a closed task's security pin.** T23's single-caller pin on `consumeAuthorizeState` now reads
   three. The argument is in phase 6 and in the test, and it turns on T23-D2's threat being a drive-by
   navigation that sign-out cannot be. A reviewer should challenge it: it is the most consequential thing
   this task did outside its own two files.
5. **The `strip` helper is the fourth copy** in this package, and the boundary-harness shape is a fifth family
   of the same debt. **OQ-T32-4**, now raised by seven reviews across three tasks.
6. **SO12's 303 failures** show how much of the package leans on `clearAccessToken` being idempotent. That is
   not a T35 defect, but it is a fact worth someone knowing: a change to that three-line function moves 300
   tests.

## Gates

| Gate | Result |
|---|---|
| T35 cases | **29 passed**, exit 0 |
| whole suite | **1603 passed / 1 failed of 1604** — the known `oxlint` host case, the recorded baseline |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| mutation | **14/14 regressions · 1/1 canary · 0 survivors · 0 broken** |

## Open questions carried to phase 8

**OQ-T35-3** (confirmed by measurement, Blocker, task 39's) · **OQ-T35-2** (dormancy, sixth task) ·
**OQ-T35-1** (frozen: task 36 retains the `id_token`) · **OQ-T32-4** (helper duplication, seventh raising) ·
`package.md:123`'s stale test name, recorded not fixed.
