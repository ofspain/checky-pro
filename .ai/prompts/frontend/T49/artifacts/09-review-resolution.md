<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# frontend · T49 · Phase 9 — Review Resolution

| | |
|---|---|
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |
| **Status** | **COMPLETE** — owner-approved 2026-11-28: **ratify both deviations as built.** |

**No code changed in this phase**, so the Phase 7 harness result stands as the record rather than being
re-run for appearance:

```
regressions: 17/17 killed · canary dead · survivors ['TR17','TR20'] (both declared)
broken: none · disagreements: none · baseline 0 failed of 2083
```

Kimi re-ran the suite, `tsc`, `oxlint` **and the harness** on its own machine and reproduced every number,
including `0 disagreements` and both declared survivors. **No independent run was needed to clear a flake
this time** — unlike T48, where Kimi's first attempt was refused by the baseline guard.

---

## Owner decision

**D-T49-3 and D-T49-4 are both RATIFIED as built.**

| ID | |
|---|---|
| **D-T49-3** | `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` modified — **list entries only, no logic.** This was effectively forced: the catalogue key-set pin is set equality against the catalogue itself, so any new key fires it and the suite cannot be green otherwise. What is worth keeping is *why the freeze missed it*: the render-site measurement was correct and **blind to a second pin in the same file.** |
| **D-T49-4** | The two keys ship **flat**. Phase 3 designed them nested; Phase 5's probe measured that shape **crashing** the copy scan with `sentence.replace is not a function`, because it flattens the catalogue exactly one level and assumes strings. The alternative — teaching a closed task's flattener **and** its scanner to recurse — was declined: it rewrites **logic** in T173's suite for a cosmetic gain, in a task whose brief said not to touch that file at all. The nesting's stated purpose, grouped copy task 54 can reuse, survives intact. |

## Kimi's Phase 8 — disposition

**Thirteen findings: ten positive or observational, two process, one correction.** Every file:line citation
was checked against source before being accepted; all were accurate.

| Finding | Severity | Disposition |
|---|---|---|
| **I1** D-T49-2 implemented as ratified | Positive | Noted — confirms the owner's reversal landed, in both component and test comments |
| **I2** no flash before success | Positive | Noted — `idle`-means-in-flight plus the reset line |
| **I3** the response-body scan is comprehensive | Positive | Noted |
| **I4** F5 is real: nothing fails when task 50 attaches the element | **Medium (process)** | **ACCEPTED, and Kimi agrees with the plan** — *"Address in Phase 10 as planned."* |
| **I5** D-T49-1 implemented correctly | Positive | Noted; ratified at Phase 4 |
| **I6** D-T49-3 / D-T49-4 need ratification | **High/Medium (process)** | **RESOLVED above — both ratified.** |
| **I7** "the console spy is wider than T48's" | Positive | **ACCEPTED, with a correction — see below** |
| **I8** nine statuses, more than the brief required | Positive | Noted; `401/403/409` are the ones that would tempt reason-specific handling |
| **I9** the rewritten handoff case avoids the "both empty" trap | Positive | Noted |
| **I10** TR16's survival was a harness defect, not a product gap | Observation | Noted |
| **I11** TR17 correctly declared unfalsifiable | Observation | Noted |
| **I12** the link is outside the live region | Positive | Noted |
| **I13** focus follows OQ-T49-4 | Positive | Noted |

### The one correction — I7 under-credits the fix it is praising

Kimi describes the spy as *"covers `log`, `warn`, `error`, `debug`, `info`, `table`, `dir`, `trace`"*, as
though those eight were enumerated. They are not. The list is **derived from `console` at runtime**:

```ts
const methods = (Object.keys(console) as (keyof Console)[])
  .filter((name) => typeof console[name] === 'function');
expect(methods, 'the console reflection found nothing, so the assertion below is vacuous')
  .toEqual(expect.arrayContaining(['log', 'warn', 'error', 'debug', 'info', 'table', 'dir', 'trace']));
```

Those eight are the **non-vacuity guard**, not the coverage. The spy covers **every function on `console`**,
including any a future runtime adds.

**The distinction is the whole point of T48's F4 fix**: Kimi's own T48 Phase 11 recommended *adding* four
names, and the reason it was implemented by derivation instead was that a hand-written list is a list that
goes stale. Recorded because the artifact as written would let the next reader re-introduce an enumeration
believing that is what is there.

## No fixes were required by this review

Kimi found no behavioural gap, no inaccurate claim, and no surviving mutant that should die. Its only
actionable item, **I4**, is the one Phase 7 had already recorded for Phase 10 — and that is where it goes.

## Final state

| | |
|---|---|
| Suite | **2083 / 2083**, 40 files |
| Named suite | **35 cases** |
| `lint` / `typecheck` | 0 / 0 |
| Harness | **17/17**, canary dead, 0 broken, **0 disagreements** |
| Deviations | **D-T49-1, D-T49-2 ratified at Phase 4 · D-T49-3, D-T49-4 ratified here** |

## Carried forward

| ID | |
|---|---|
| **OQ-T49-6** | The handoff instrument for task 50 — **Phase 10's job** (F5 / I4). Phase 5's measurement sets the shape: a positive claim that **inverts** when the element lands, not a sameness, because T48's labelled handoff case did not fire while two unlabelled equality assertions did. |
| **OQ-T49-3a** | If the catalogue grows a shared auth group, `verify.failureMessage` moves; task 54 is the first consumer that would feel it. |
| **OQ-T48-4** | `auth.yaml` declares `204` and nothing else for `verifyEmail` — a contract-accuracy defect, not a blocker. |
| **R-T48-1** | The gateway hazard. **T49 now owns its visible consequence**: that visitor sees this task's failure message, and R4 forbids explaining it. |
| **FU-T48-1**, **FU-T48-2** | Unchanged; neither is this task's. |

**Phase 9 complete. Next is Phase 10 — Test Generation.**
