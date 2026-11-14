<!-- MODEL: Human Approval — Phase 9 (Review Resolution). Every finding dispositioned; accepted fixes applied. -->

# frontend · T44 · Phase 9 — Review Resolution

| | |
|---|---|
| **Consumes** | `artifacts/07-self-review.md` (F1–F6), `artifacts/08-independent-review.md` (I1–I12) |
| **Harness** | 35 rows · `artifacts/09-mutation-run-1.txt`, `artifacts/09-mutation-run-2.txt` |
| **Result, both runs** | **32/32 regressions killed · 0 broken · 2 canaries killed · 1 survivor (TR24) · 0 disagreements** |

## I1 — the flake has a cause, and it is not the harness

Phase 8 asked for the harness to be re-run and for BROKEN rows to be treated as hypotheses. Both runs were
done, and the first pair **refused to start**:

```
run 1  baseline whole suite: 2 failed of 1945
       ['adds no second hand-over and no fifth stored key when the button is clicked twice', …lint…]
run 2  baseline whole suite: 4 failed of 1945
       ['fires exactly one hand-over, to the authorize URL, counted across assign/replace/href/open',
        'reports const f = fetch …', 'reports new XMLHttpRequest() …', …lint…]
REFUSING TO RUN: a file outside the known environmental set is failing.
  unexpectedly failing: ['src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx']
```

**That is FU-T42-1 caught at BASELINE for the first time** — in three previous sightings it had only ever
appeared as an extra kill credited to a mutant. The refusal guard did its job: it declined to score 35 rows
against a corrupt floor rather than reporting a number that was not true.

Then the cause was measured rather than guessed. The only variable is the local Docker stack, which was
brought up on this machine for a demo rehearsal between phase 7 and phase 9:

| condition | clean | flaky |
|---|---|---|
| stack **stopped** (3 runs before it was started, 4 after it was stopped, 2 harness runs) | **9** | 0 |
| stack **running** (4 suite runs + 2 harness baselines) | 4 | **3** |

Every flaking case is in one family: the sign-in/PKCE cases that count hand-overs and storage writes
(`fires exactly one hand-over…`, `adds no second hand-over…`, `are in session storage after the action…`,
`hold the verifier belonging to the challenge that was sent`) plus the two lint cases that **spawn** a
process. They are timing-sensitive, and eleven containers are enough to tip them.

**Resolution.** FU-T42-1 is **load-sensitive, not random**, and it is not a defect in the harness — the
harness's two guards (baseline refusal, BROKEN-by-dropped-total) are what made it visible. Kimi's Run 1,
which reported TR18 and TR20 as BROKEN with *"a file did not LOAD"*, overlapped with this machine building
that stack; a vitest worker dropping a file under load is the same phenomenon at a different layer.

The standing rule this produces: **the harness runs with the stack stopped.** Re-running until a run passes
is the wrong remedy, because a run costs twenty minutes and a flaky kill is indistinguishable from a real
one in the output. What is NOT yet known is which of the two is at fault — a real race in the production
code those cases cover, or an over-tight assertion in the cases themselves. FU-T42-1 stays open with that
question attached, and it is now a question somebody can answer, which it was not this morning.

## Disposition

| finding | severity | disposition |
|---|---|---|
| **F1 / I1** harness flake | Major | **RESOLVED by diagnosis** (above). Both required runs recorded; outcomes **identical row for row**. |
| **F2** TR21 predicted `own`, killed `inherited` | Minor | **FIXED.** Re-aimed to `inherited` with the reason in its comment. T43 predicted `own` and was right for itself; T44 grew two files and that focus guard is not one of them. An outcome is never relabelled — the prediction moved. |
| **F3 / I3** the scan hardcoded two filenames while its comment claimed more | Major | **FIXED.** It now loops `PRIMITIVE_FILES`, the list this file already pins by size, so a new field primitive joins the scan the day it joins the layer and there is no second list to maintain. |
| **F4 / I2** four of AC4's six assertions had no mutant | Major | **FIXED.** TR35 adds `minLength={12}` to `EmailField` — one row crossing **both** unmeasured axes, and the realistic mistake: R1's *lower* bound written as an attribute, on the field that has no bound. It is killed by exactly the two AC4 pins, the same two TR32 kills. |
| **F5 / I4** `/maxLength/i` is a substring match | Minor/Major | **DECLINED, with the reason written into the source.** A forbidden-token scan is the one place breadth is the safe direction: `\bmaxLength\s*=` would miss `{...{maxLength: 128}}`, a `const cap = { maxLength }` and a prop forwarded under another name, while breadth costs a loud failure on an identifier nobody has written. The package's substring lesson was about a scan that **passed** when it should have failed; this one errs the other way. |
| **F6** `T44 AC4` inside a `describe` numbered `AC2/AC3/AC4` | Minor | **ACCEPTED AS IS.** The prefix is the plan's deliberate answer to two tasks sharing one file. Renaming T43's describes is out of scope and would touch a frozen file for cosmetics. |
| **I5** AC2 duplicates the 128 count with the totality case | Minor | **No action** — the two are at different layers and I5 itself says so. T43's comment at `:310-324` already draws the line. |
| **I6** no 129-**astral** row | Minor | **DECLINED with a reason.** It cannot fail where the 129 ASCII row passes: 129 code points is out of range under a code-point check, and 258 UTF-16 units is out of range under a `.length` one. A row that cannot discriminate adds rows, not measurement. |
| **I7** the table case does not assert `value.length` for ASCII rows | Minor | **No action** — I7 agrees. The astral rows and the AC2 case carry that claim. |
| **I8–I12** | Positive | Noted. I12 confirms independently that **no production file changed**. |

## Verification after the fixes

| | |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| whole suite | **1944 / 1945**, the known environmental lint-script failure |
| design-system suite | **64 / 64** with the widened scan |
| harness, run 1 | 32/32 killed · 0 broken · survivors `['TR24']` · **disagreements: none** |
| harness, run 2 | 32/32 killed · 0 broken · survivors `['TR24']` · **disagreements: none** |
| run 1 vs run 2 | **identical outcomes, row for row** |

## What phase 10 inherits

Nothing is left open against the frozen brief. The one survivor is the row the freeze predicted would
survive, for the reason it predicted. The open items that leave this task are **FU-T42-1** (now diagnosed as
load-sensitive, cause-in-code still unknown) and the standing rule that the harness runs on a quiet machine.
