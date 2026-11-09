<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution) -->

# frontend · T41 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T41 — Unauthenticated redirect |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md`, `artifacts/09-mutation-run.txt` |

## Gates after resolution

| | |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1745 / 1746** — the one failure is the known host-environmental `lintRejectsHandWrittenBackendFetch` case |
| `python3 -I …/07-mutation-harness.py` | **14/20 regressions killed, every kill `KILLED/own`; 6 expected survivors for phase 10; 0 unfalsifiable; 0 broken; 0 disagreements** |

## The finding that changed the code: I6

**Kimi doubted a measurement, and the measurement was wrong.** `RedirectToSignIn.tsx`'s header claimed
*"`useLocation().pathname` carries the `/app` prefix - the router's own state says `/app/sign-in`, measured at
phase 4."* The parenthesis is the error made visible: phase 4 measured **`router.state.location.pathname`** and
the sentence asserted it of **`useLocation()`**. Measured again at phase 9, six arrivals through
`createMemoryRouter` **and** the production `createBrowserRouter`, which agree exactly:

| arrival | `useLocation().pathname` | `router.state.location.pathname` |
|---|---|---|
| `/app/account` | `/account` | `/app/account` |
| `/app/` and `/app` | `/` | `/app/` and `/app` |
| `/app/no-such-route` | `/no-such-route` | `/app/no-such-route` |
| `/app/sign-in` | `/sign-in` | `/app/sign-in` |
| `/app/app/account` | `/app/account` | `/app/app/account` |

Three consequences, and the third is the one I would have missed:

1. **The strip was dead code.** `'/account'.startsWith('/app/')` is false, so the ternary always took its
   right-hand branch. The remembered target was right by accident, not by the code that claimed to make it right.
2. **Two harness rows were mutating a line that cannot run.** RD06 (strip removed) and RD07 (strip off by one)
   both produced behaviour identical to the original, so their survival at phase 7 meant *nothing is there*, not
   *phase 10 has not written the case yet*. The phase-7 artifact called RD06 **"the mutant most likely to ship"**
   and ranked phase 10's priorities around it. That ranking was built on a row that measured nothing.
3. **The dead code was not harmless.** `/app/app/account` matches the catch-all, `useLocation()` gives
   `/app/account`, and that *does* start with `/app/` — so the strip turned a route the visitor never asked for
   into a remembered `/account`. The only arrival where the strip could fire is the one where it was wrong.

**Resolution:** the strip is removed, `arrival` is `pathname`, `APP_BASENAME` is no longer imported, and the
measurement table above is in the file so the next reader does not have to re-derive it. RD06 becomes *the
arrival made absolute* — the real deep-link-loss mutant — and RD07 becomes *the strip put back*, killable only
by a case arriving at a doubled prefix, which phase 10 must now write.

**Why the wrong premise was plausible, which is the part worth keeping:** `cleanReauth.ts:43`'s `inAppPath()`
does exactly this strip and is **right** to, because it reads `globalThis.location.pathname` — a real URL, which
does carry the prefix. My own phase-7 F7 called this "duplicated twice over" and recommended extracting it on a
third instance. That was the wrong lesson from the right observation: the problem was never the duplication, it
was that **copying a shape from a module that reads a different source carried its premise along with it.**

## Kimi's findings

| | finding | verdict | action |
|---|---|---|---|
| **I6** | the basename strip rests on a measurement | **ACCEPTED — the finding of the phase** | strip removed; table in the file; two harness rows rewritten |
| **I3** | RD09 is behaviourally unfalsifiable but a **source** scan can kill it | **ACCEPTED** | `UNFALSIFIABLE` is now empty; RD09 is a `phase 10` row and phase 10 writes a scan forbidding a branch on `arrival` before the call. "Unfalsifiable" goes back to meaning *no observable effect of any kind, in the DOM or in the file* |
| **I4** | the catch-all case proves only "not a 404" | **ACCEPTED** (= my F6) | phase 10 asserts the sign-in heading for all three arrivals, which AC9's indistinguishability claim needs anyway |
| **I1** | the callback fixture change is real and right | **ACCEPTED**, independently verified | none; phase 10 does not touch those files |
| **I2** | the render-time `sessionStorage` write is a deliberate deviation | **ACCEPTED** | none. The constraint it implies is binding: **no test may assert a write count** |
| **I7** | no source scan for `RedirectToSignIn.tsx` yet | **ACCEPTED** (= my F1) | phase 10, in the order the survivors give |
| **I5** | nothing pins that `/app/sign-in` does not match the index route | **PARTLY ACCEPTED — and the premise is wrong** | `routesResolveUnderAppPrefixOnly.test.tsx:111` already asserts the matched leaf for **every** declared path, `sign-in` among them, so the narrowness is pinned at the MATCH level today, not merely "indirectly". What is missing is a RENDER-level case: `/app/sign-in` renders the sign-in screen and stays put with `POP`. Phase 10 adds it |
| **I8**, **I9**, **I11**, **I12**, **I13**, **I14** | T40's scan is complete; return types explicit; the expectation field works; `withProbes` handles the index route; module boundaries respected; the suite failure is environmental | **ACCEPTED as confirmations** | none |
| **I10** | `routes.tsx:6` cites "phase 8, I13" before phase 8 existed | **REJECTED as stated, and a different defect fixed** | The citation is **T40's** phase 8 I13 — a past review, not this task's — so it is not a forward reference. But the same comment says *"the three imports below it"* and *"the two screens and the T40 guard"*, and T41 made that **four** imports. Reworded, with the citation kept and attributed |

## My own findings

| | verdict | action |
|---|---|---|
| **F1** (named test missing) | expected | phase 10 |
| **F2** (nine false failures from one baseline) | recorded | no code change; the lesson is in phase 6's D1 |
| **F3** (`gatesFound`'s message names only the zero case) | **FIXED** | now *"did not find exactly one pathless gate WITH children"*, with a comment saying why both directions matter |
| **F4** (AC13 pins order inside a count case) | **FIXED** | order kept — it is intent — with a comment naming `pathTree` as the instrument that owns order, so the next reader does not read the failure as an element problem |
| **F5** (the AC2 arms' message describes one of two failures) | **FIXED** | the message now names the destination it expected |
| **F6** (the catch-all case is loose) | phase 10 | = I4 |
| **F7** (the strip is duplicated twice over) | **SUPERSEDED by I6** | the recommendation was to extract on a third instance. The strip is gone, so there is no second instance — and the real lesson replaces it, above |
| **F8** (write during render) | kept | = I2 |
| **F9** (the callback seams) | confirmed by I1 | none |
| **F10** (not reactive) | carried | OQ-T40-1 |

## D3 — I ran the harness on uncommitted edits and it destroyed them

Phase 9's fixes were written, verified green, and then **lost**, because I ran the mutation harness before
committing them: `revert()` is `git checkout -- frontend/src/` followed by `git clean -fq`, and it wipes the
whole tree rather than the mutated file. The harness's own docstring says so in capitals, and a standing note
in my memory says it cost 19 cases in T14, 5 in T15 and a phase-9 edit in T36. It has now cost a phase-9 edit
in T41.

Nothing was lost permanently — every edit was reproducible from the patch script — but the run that followed was
worse than useless: it ran the **old** rows against the **old** code and reported `14/19, disagreements: none`,
a clean result for a tree I had already changed. A harness that reverts to `HEAD` reports on `HEAD`, and a green
result from it says nothing about the work in front of you. The run file was deleted and the sequence redone in
the right order: fix, verify, **commit**, then mutate.

## Open Questions

- **OQ-T41-1** (carried) — the basename-spelled destination cannot be a harness row: it exhausts the V8 heap
  rather than failing a test, so the most expensive mistake available at this seam is the one mutant that cannot
  be scored. Now partly mitigated in a different way: `APP_BASENAME` is no longer imported by this module at all,
  so spelling the destination with it requires adding an import first.
- **OQ-T41-2** (carried) — AC14's end-to-end value is provable only by composing two suites until task 42 exists.
- **OQ-T41-3** (new) — `useLocation()` being basename-relative is now load-bearing in the opposite direction: the
  code depends on the router **not** prefixing. Nothing but phase 10's stored-value case will notice if a future
  react-router changes it, which is exactly what I6 recommended and what phase 10 must write.

**Phase 9 complete.** Four fixes in test instruments, one in production (a line removed, not added), one comment
corrected, two harness rows rewritten, one scoring category emptied, one Kimi finding rejected with evidence and
a real defect found in its place, and one process deviation recorded as D3.
