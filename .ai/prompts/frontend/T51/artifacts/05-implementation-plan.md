<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan). No code written. -->

# frontend · T51 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

**No code was written in this phase.**

---

## Order of work, and why this order

| Step | Why here |
|---|---|
| **1 · Write the failing cases FIRST, against the unfixed screen** | The hole is invisible to 2116 tests. **If the new cases pass before the fix, they measure nothing** — that is the one thing this task cannot afford, since a case written for *"the email in no URL"* did exactly that one task ago. The arm loop must **fail on the three non-JSON arms** before `parseAs` lands. |
| **2 · Port `teardownCurrent`** | Needed by the shared-expectation cases; measured viable in Phase 2. |
| **3 · Apply the one-line fix** | Then the loop goes green, and the transition from *fails on three arms* to *passes on thirteen* is the proof the case is aimed at the defect. |
| **4 · Add `setTimeout`/`setInterval` to the source scan (D-T51-3)** | Independent of the rest; verify it passes **and** that removing the patterns is what a mutant would exploit. |
| **5 · Commit the tests** | **Before the harness runs** — it reverts with `git checkout` and has silently destroyed uncommitted test edits twice (19 cases in T14, 5 in T15). |
| **6 · Harness rows** | Phase 7's. |

**Step 1 is the whole discipline of this task.** Writing the fix first would leave no evidence the cases can
fail.

## The shared expectation

One captured rendering, reused by every arm:

```ts
/** The acknowledged markup, captured once from the DECLARED reply and reused as the expectation for every
 *  other arm. Capturing rather than hardcoding is deliberate: a hardcoded string would have to be edited
 *  whenever the design system changes a class, and the edit would silently redefine what "identical" means.
 *  The declared 200 is the right source because it is the only reply the contract actually promises. */
```

**Risk, named now:** a captured expectation is satisfied by a *uniformly broken* screen — if every arm
rendered nothing, every comparison passes. **So the capture is asserted non-empty and asserted to contain the
acknowledgement text before any arm compares against it.** This is the same trap as five empty lists
satisfying a sameness pin in T173; the lesson is already paid for.

## Case plan

| Case | AC |
|---|---|
| Arm loop, thirteen cases named by arm, each comparing `innerHTML` to the shared capture | **AC2, AC3** |
| …each also asserting **no `unsent`**, the **pathname**, and the **absent server `message`** | **AC4, AC9, AC10** |
| The capture's own guard — non-empty, contains the acknowledgement, contains no form | **AC1** + the non-vacuity control |
| Focus identical across arms, driven over the whole set | **AC7** |
| Live region identical across arms | **AC8** |
| A rejecting transport: `unsent`, form mounted, **no** acknowledgement | **AC5** |
| A different email, identical markup | **AC6** |
| The source scan, with the two timer patterns added | **AC11** |
| The catalogue key set is unchanged | **AC12** |

**Focus and the live region get their own cases rather than riding the arm loop**, because T47's lesson stands:
two assertions in one case means the first short-circuits the second, so the second is unmeasured until
measured apart.

## Harness rows planned

| Row | Mutant | Expectation |
|---|---|---|
| **TR24** | `parseAs: 'stream'` removed | **own** — the three non-JSON arms |
| **TR25** | `parseAs: 'json'` | **own** — the same, by a different spelling a refactor would reach for |
| **TR26** | The acknowledgement interpolates the submitted email (**replaces Kimi's unkillable row**, D-T51-1) | **own** — the differing-email case |
| **TR27** | The focus effect fires only for a `200`-shaped settle | **own** — the focus case; **this is the row that proves D-T51-2 was worth adding**, because it is invisible to `innerHTML` |
| **TR28** | The timer patterns removed from the scan, and a `setTimeout` added to the screen | **own** — a **two-part** mutant, because removing the patterns alone changes nothing observable |

**TR28 is the control for D-T51-3.** A scan is only an instrument if something fails when it is removed, and
T36 learned that *the scan added in phase 9 had the same hole it closed.*

## What will NOT be done

| | |
|---|---|
| **No catalogue key** | AC12. |
| **No change to the state machine** | The fix is in the call, not the states. |
| **No audit of the other public screens** | OQ-T51-6, out of scope by `tasks.md`. Recorded, not widened. |
| **No shared test-util module** | FU-T43-2 stays open; `teardownCurrent` becomes the **second** copy, which is the cost the follow-up records. |

## Risks

| Risk | Handling |
|---|---|
| **The new cases pass before the fix** | Step 1 exists to catch it; if they pass, the case is wrong, not the screen. |
| **A captured expectation passes vacuously** | Guarded above. |
| `innerHTML` carries a generated id | Measured in Phase 2: two independent arrivals compared **identical**, and the form (which holds the `useId` pair) is gone in the acknowledged state. |
| **An arm throws instead of rendering** | Phase 2's probe met this and it read as a teardown bug. Each arm is its own case so a throw is attributed to the arm, not to its neighbours. |

---

**Phase 5 complete. Next is Phase 6 — Implementation.**
