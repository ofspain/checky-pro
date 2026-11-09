<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution) -->

# frontend · T40 · Phase 9 — Review Resolution

| | |
|---|---|
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md`, `artifacts/09-mutation-run.txt` |
| **Approval** | Human Approval gate — standing owner authorization to run phases back-to-back, stopping only at the Kimi gates. No decision here needed owner input: every verdict below rests on a measurement taken in this phase. |

**Every one of Kimi's fifteen citations was checked against source before its verdict.** All fifteen are
correct, including the two line ranges my own self-review had slightly wrong (`RequireSession.tsx:32` is the
function line; I had cited `:34`, the comment above it).

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |
| `vitest run` | 1724 / 1725 — the one known host-environmental failure |
| harness, re-run after the fixes | **17 mutants · 10/16 regressions killed · 1 unfalsifiable · 0 broken · 6 survivors · 3 disagreements** (was 4) |

---

## 1 · The finding that changed the task: I4 / F5, now measured

Kimi's **I4** and my **F5** said the same thing: **RS14 measured something other than its description.** It
added a *second* pathless route carrying a screen, and it died — on `pathTree`, which sees a new entry in
the tree's **shape** and knows nothing about elements. It never reached AC13's claim.

AC13's claim is that **exactly one** pathless route carries an element. The way to break that without
touching the shape is to put a second element on the route that is **already** pathless. So RS14 is now:

```tsx
{ element: <RequireSession />, errorElement: <SignInScreen />, children: … }
```

**Measured this phase: it SURVIVES all 1725 tests.** `pathTree` maps `path` and cannot see it;
`routesWithElements` and T37's AC9 both filter on `route.path !== undefined` and cannot see it either. So
the hole phase 4 said this task **opens** is real, is open right now, and **AC13 is the only thing that will
ever hold it** — not redundant, as the alternative reading in F5 allowed for.

The old row is **kept and relabelled** as **RS17**, a `pathTree` row rather than an AC13 row. It is a real
regression — a screen attached where no count pin looks — and it does die; it just dies on the shape pin.
Keeping it means a later task that loosens `pathTree` loses a *named* kill here instead of losing it
silently.

## 2 · The finding I rejected, with the measurement that refutes it

### I9 — *"add an isolation case rendering `<RequireSession />` with and without a token… stronger than a route-table test for AC3's control"* · **REJECTED**

I built it and ran it:

```
{"without":"","withToken":"","identical":true}
```

A `<RequireSession />` mounted as a route's own element has **no child route**, so its `<Outlet />` renders
nothing — and `null` renders nothing. **The two arms are byte-identical.** Kimi's case would pass with the
guard inverted, deleted, or hard-wired to `false`: it is a test that measures nothing, and it is not
"stronger" than the route-table arms, it is the one shape of this test that cannot work.

This is the same mistake in a new place, and it is worth naming because it has now happened in three
consecutive tasks: **T37's AC7 forbade `accessToken(`, a call shape that cannot exist**; **T37's F3 aimed a
mutant at nothing**; and here a proposed control's two arms cannot differ. A control must measure its own
claim, and the only way to know it does is to run it and look at both arms.

The derived-table method the frozen brief fixed is kept unchanged: the probe is what gives an authenticated
route something to render, and the pass-through guard arm is what makes "nothing rendered" a measurement.

## 3 · Fixes applied

| | | |
|---|---|---|
| **I2 / F8** | `auth/RequireSession.tsx` — `export function RequireSession(): ReactElement | null`, with `import type { ReactElement } from 'react'` | **FIXED** |
| **I12** | `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — **two** statements T40 had just made false: the header's *"it is currently untestable"* and the describe-block's *"needs an auth gate that does not exist yet"*. Both now say what changed and why, rather than being quietly deleted | **FIXED** |
| **I13** | `routes.tsx` — one sentence saying the three imports below the cycle note are not part of the cycle, because none of them reads a route constant at module scope | **FIXED** |
| **I4 / F5** | the harness — RS14 replaced, RS17 added, and the new row **measured** | **FIXED** |

On **I2**, one correction to Kimi's reasoning: *"inference gives a wider type"* is not quite it — the
annotation compiles, so the inferred type was assignable in both directions. The reason to state it is the
one my F8 gave and Kimi's assessment also gave: **a reader of a route guard most needs to know it can render
nothing**, and inference puts that behind a compiler run. Soundness was never at issue.

## 4 · Verdicts on all fifteen findings

| | finding | verdict |
|---|---|---|
| **I1** | the named suite does not exist, so R28's first clause has no direct measurement | **ACCEPT** — the expected phase-7 state, and phase 10's whole mandate. RS01, RS02, RS03, RS05, RS08, RS14 are the six rows it must kill |
| **I2** | no explicit return type | **ACCEPT · FIXED** (§3) |
| **I3** | `pathTree` is doing most of AC12's structural work | **ACCEPT, with its evidence list corrected** (below) |
| **I4** | `routesWithElements` cannot see a pathless route's `errorElement` | **ACCEPT · FIXED · MEASURED** (§1) |
| **I5** | AC7 and AC10 die by inherited scans; the task should still own them | **ACCEPT** — no code change; phase 10 writes both regardless. The harness's `INHERITED-ONLY` flag is what keeps this visible |
| **I6** | the read is not reactive | **ACCEPT, no action** — documented in the guard's header and in the brief's OQ-T40-1, task 42 named |
| **I7** | the empty-string condition is correct | **ACCEPT** — and RS03 is the row that will prove it, by `retainAccessToken('')` |
| **I8** | the `pathTree` pin encodes the order of `AUTHENTICATED_ROUTE_PATHS`, stronger than behaviour requires | **ACCEPT, no action** — with a note: the reason order does not affect matching is React Router's **specificity ranking**, not the paths being disjoint (`invoices` and `invoices/:id` are not disjoint). The conclusion holds either way |
| **I9** | add an isolation case for the guard | **REJECTED · MEASURED** (§2) |
| **I10** | the `routesWithElements` comment explains why screens stay three | **informational** |
| **I11** | nothing asserts the guard does not import from `app/` | **ACCEPT** — phase 10 adds the `../app/` import scan to AC6 |
| **I12** | the named file's header says the claim is "currently untestable" | **ACCEPT · FIXED** (§3) |
| **I13** | the cycle comment does not mention the new import | **ACCEPT · FIXED** (§3) |
| **I14** | nothing asserts the guard is not applied to public routes or the catch-all, in rendered output | **ACCEPT** — already the plan's §4.2 public and catch-all arms; the structural half is AC12 |
| **I15** | the four disagreements are handled correctly | **informational** — now three, since RS14 agrees with its expectation |

### I3's evidence list, corrected

Kimi lists *"RS09, RS10, RS11, RS12, RS13, and RS16 all die on this assertion."* Five of the six do.
**RS16 does not** — it is a mutation of task 25's own control, and its single named failure is that control:
*"reports a real navigation too, so a broken instrument cannot read as a refusal"* (`09-mutation-run.txt`).
The conclusion is unaffected and the recommendation is accepted: **AC12 adds only what `pathTree` cannot
see** — the gate's element identity (`element.type === RequireSession`) and the derivation from the exported
arrays.

Two further measurements worth keeping from the re-run, because they say how much each instrument carries:

- **RS12** — the gate's children built from *both* arrays, so the public nine sit inside the gate too —
  changes **no behaviour at all** (they match at the root first) and dies on `pathTree` **alone**.
- **RS13** — the gate moved onto the root, so it gates the public nine — fails **247 tests**. R28 inverted is
  not a subtle defect, and the repository already screams about it.

## 5 · Phase 10's mandate, as resolved

1. **Kill RS01, RS02, RS03, RS05, RS08 and RS14 by named case.** Those six are the task's contract.
2. Write **AC7**'s source scan and **AC10**'s no-navigation case although RS06/RS07 already die — a kill by
   T22's package-wide `console` sweep is evidence about the mutant, not about this task (**I5**).
3. **AC12 only adds what `pathTree` cannot see**: element identity and the derivation (**I3**).
4. **AC13 is confirmed necessary** and must be written against the `errorElement` shape (**I4**).
5. **No isolation mount** of the guard — both arms render `""` (**I9**).
6. Add the `../app/` import scan to AC6 (**I11**).
7. **RS04** stays scored apart.

**Phase 9 complete.** Fourteen findings accepted (four of them fixed here), one rejected with a measurement,
and the one that changed the task re-measured.
