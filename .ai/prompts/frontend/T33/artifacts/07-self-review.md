<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review) -->

# frontend · T33 · Phase 7 — Self-Review and Mutation Score

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |

## What this phase changed, and why each change was needed

### S1 — Nothing asserted the sentence reached the screen. Found by designing a mutant for it.

The handler sets a notice; the notice's whole purpose is a sentence on the sign-in screen. Every AC5 case
I had written stopped at "the notice was set" and "the map points at the right key" — so a screen that
displayed the **wrong** sentence would have passed all of them. That is not hypothetical: it is precisely
what the screen did before this task changed it, because `SignInScreen` rendered `t('signIn.discarded')`
for *any* notice.

The gap surfaced while writing mutant NR09 ("the screen renders the discarded sentence for every notice").
I expected it to be killed and realised nothing could kill it. The file was renamed `.ts` → `.tsx` and two
cases added: the new sentence appears on the real rendered screen and the discarded one does not, plus a
**control** that T18's discarded path still renders its own sentence — so choosing by notice did not
quietly take that sentence over.

**The lesson is about the order of work.** Designing the mutant found the hole, not re-reading the cases.
Three tasks running, the same thing each time.

### S2 — A mutant that measured something other than its own description.

NR03 is labelled "the token is cleared LAST, after the navigation that can throw". Its first version
**deleted** the clear line, which is NR01 — a duplicate wearing another name, and the score counted it.
A single replacement can delete or rewrite in place; it cannot reorder.

The harness now allows a mutant to carry several edits, and NR03 performs the actual move. The evidence
that it is now a different regression: with two edits the token still *does* get cleared, so every ordinary
case passes and the mutant dies by **2** failures, where NR01 dies by **6**. Only the throwing-navigate
case can tell it from the original — which is the entire reason the order exists.

This is the only change to machinery I inherited from T32, and it is recorded in the file.

### S3 — The phase 5 survey was truncated and I used it as a completeness claim.

Written up fully in `06-implementation-notes.md`, repeated here because it is the worst mistake in this
task. The plan said "nothing else in `src/` pins the catalogue's shape" on the strength of a grep piped
through `head -12`. There were three pins, in three closed tasks' suites. **A survey used as a completeness
claim cannot be truncated**, and the fix is not to grep more carefully but to stop writing conclusions from
a list I can see has been cut.

## Mutation score

**17 mutants, NR01–NR17, all KILLED, all by this task's own cases.** No survivors, no BROKEN,
`HARNESS_EXIT=0`. Baseline: whole suite 1 failed of 1567 — the known environmental case, pinned by name —
and T33's own 29 cases green.

| Group | Mutants | What they protect |
|---|---|---|
| The token (AC1) | NR01–NR03 | never dropped; dropped asynchronously, leaving a window; dropped LAST, after a throw |
| The copy (AC5) | NR04–NR08 | no notice; the `discarded` notice; the map mis-pointed; a duplicate sentence; **a sentence that names the cause** (L4 in one line) |
| The screen (AC5) | NR09 | the regression S1 found: one sentence for every notice |
| The destination (AC4, AC10) | NR10–NR12 | `/signed-out`; a re-spelled literal; a route constant read at module scope |
| The reason (AC9) | NR13–NR14 | the parameter taken back; copy chosen by the reason |
| The return target (AC6) | NR15–NR16 | forgotten; written past the allowlist into storage |
| The environment (AC11) | NR17 | **an authenticated route gains a screen** |

**NR17 is not a defect in this task's code.** It is the day the repository stops making T33's
"no half-authenticated render" claim true for free, and the case it kills says so in its message and names
T41. It is the only row here whose purpose is to fail in the future.

**What a mutant here cannot measure**, carried forward from T32 phase 11: a mutant can only record a fix
that turns a survivor into a kill. T33's documented limit — a tab outside the SPA remembers no return
target — is of the false-**positive** kind, so it is held by a control, not by a row. The harness measures
an instrument's strength; only a control measures its precision.

## Weaknesses I know about

- **S4 — The whole task is unreachable in production.** `createSessionBoundary` has no caller, so the only
  thing that ever runs this handler is this suite. Every behavioural case is therefore a statement about
  how the hook *will* be called. The cases drive the **real** boundary rather than a stub to make that
  statement as strong as it can be, but it is still a statement about an unwired component (OQ-T33-2).
  **This is the weakest thing about T33 and no test can fix it.**
- **S5 — AC11 is an assumption, not a property.** "No half-authenticated render" is true today only
  because no authenticated route has an element. The case pins the assumption and fails when it changes,
  which is the most an unwired task can do; the guard itself is T41's.
- **S6 — The source cases on `cleanReauth.ts` are shape pins.** Forbidding `reason`, `catch`, `instanceof`,
  `sessionStorage` and a module-scope route read catches the regressions I could name. A module that
  reached the failure reason by some route I did not name would pass them. The zero-parameter signature is
  the real guarantee there, and it is type-level; the scans are the belt.
- **S7 — The boundary harness is the third copy** of the fake transport and fake session from tasks 5 and
  6. Said out loud in the file. With the three catalogue key-set pins found this task, the duplication
  OQ-T32-4 records has now been measured in two different helper families, by four independent reviews.
- **S8 — `en.signIn.sessionEnded` is checked against a forbidden-word pattern I wrote.** It has a control
  that proves the pattern fires, but the word list is mine, not the spec's. L4 names account states and
  token conditions; the rest of the list is judgement.

## Gates

| Gate | Result |
|---|---|
| T33 cases | **29 passed** |
| Whole suite | **1566 passed / 1 failed of 1567** — the known `oxlint` environmental case |
| Mutation | **17/17 killed, 0 survivors, 0 broken**, `HARNESS_EXIT=0` |
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |
| Closed suites touched | three key-set pins updated with justification; `src/auth` + `src/i18n` 1012 passed |

## For the phase 8 reviewer

In the order I would attack this:

1. **S4.** Is delivering an unwired behavioural task defensible at all, or should T33 have included the
   wiring despite `tasks.md:61` scoping it to the routing? Phase 4 froze resolution (c) after establishing
   that no unit of `tasks.md` owns the wiring. That freeze is the single most consequential decision here.
2. **The three cross-task edits.** T28's, T31's and the callback suite's key-set pins. Each instrument
   invited the edit in its own comment, and I strengthened T28's and T31's no-cause checks to hold the new
   sentence — but a reviewer should check I did not weaken anything while in there.
3. **S8**, the forbidden-word list, and whether `en.signIn.sessionEnded` is genuinely neutral under L4 or
   merely neutral under my own pattern.
4. **AC8's handler-stealing.** The case removes the runner's `uncaughtException` listeners and restores
   them in a `finally`. If it ever throws between those points, the runner is left without its handlers.
5. Whether **NR17** belongs in a mutation harness at all, or whether a row designed to fail in the future
   teaches a reader to expect noise from the score.

**Phase 7 complete.** Phase 8 is Kimi's.
