<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review) -->

# frontend · T40 · Phase 7 — Self-Review

| | |
|---|---|
| **Consumes** | phase 6's implementation |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py`, `artifacts/07-mutation-run.txt` |

Findings only. **No fixes** — those are phase 9's, and the test work is phase 10's.

## Measurement

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |
| `vitest run` | 1724 / 1725 — the one failure is the known host-environmental `lintRejectsHandWrittenBackendFetch` case |
| `07-mutation-harness.py` | **16 mutants · 10/15 regressions killed · 1 unfalsifiable scored apart · 0 broken · 5 survivors · 4 expectation disagreements** |

**The harness is run at phase 7, before the named suite exists**, which no earlier task's was. A survivor
here mostly means *the work is not written yet*, and that must not be read as *a gap in finished work*. So
every row carries an **expectation** — `inherited` if an instrument that already exists should kill it,
`phase 10` if nothing can until the named suite is written — and the run reports the rows whose outcome
**disagrees**. The raw survivor count at this phase is not the finding; the disagreements are.

All five survivors were predicted. All four disagreements are of the same kind — **inherited cover is wider
than this task assumed** — and three of them change what phase 10 should write.

---

## Findings

### F1 — The gate's entire decision is unmeasured · **Major** (expected, and the headline)
· **Evidence:** `07-mutation-run.txt` — **RS01** (`hasSession = true`), **RS02** (`= false`) and **RS03**
(`Boolean(...)` → `!== null`) all survive **1725 tests**. `auth/RequireSession.tsx:36-37`.
· **Assessment:** R28's first clause now has a mechanism and **nothing** holds it. The gate could be open,
shut, or wrong about the empty string, and the suite is green either way. This is exactly the state AC3 was
written to forbid, one level up: phase 6 shipped a guard whose contract has no instrument at all.
· **Recommendation:** phase 10's three arms (§4.2 of the plan) are not optional polish — they are the task.
RS01/RS02/RS03 are the three rows that must die by **named** case, and the AC3 control arm is what makes
RS01's death mean something rather than being an artefact of a probe that renders nowhere.

### F2 — Kimi F5's precision issue is real, and currently invisible · **Major** (expected)
· **Evidence:** **RS05** — the blocked arm returns `<div />` instead of `null` — survives 1725 tests.
· **Assessment:** An empty `<div>` renders **no text**, so a case asserting only `textContent === ''` passes
it. Phase 4 accepted F5 on reasoning; this is the measurement that it was worth accepting. AC8's assertion
must be about **elements**, not text, and the plan's `container.innerHTML === ''` (mounting without
`AppShell`, so the container *is* the outlet's subtree) is what kills RS05.
· **Recommendation:** keep the no-shell mount; assert `innerHTML`, not `textContent`.

### F3 — The gate could grow per-path policy and nothing would notice · **Major** (expected)
· **Evidence:** **RS08** — the gate reads `globalThis.location.pathname` and refuses `/app/admin`
specifically — survives 1725 tests.
· **Assessment:** The gate's whole contract is *one question, asked once, for all eleven*. A route read is
how that becomes eleven policies no spec authorizes, and no rendered-output comparison can see it: the
mutant's output is identical for ten of the eleven paths. This is the same argument T37's AC7 made about
reading the session (phase 8 I1) and the reason that scan exists.
· **Recommendation:** AC7's source scan, with F18's route-read patterns, and the paired-probe control so a
typo in any pattern fails the control rather than passing silently.

### F4 — Two mutants die by suites whose claims are about something else · **Major**
· **Evidence:** **RS06** (the gate redirects, taking task 41's clause) and **RS07** (the gate writes to
`console`) both print `KILLED  INHERITED-ONLY` — **no case in any file this task owns or grew fails**.
RS07's named failures are five cases in `auth/tokensAreNeverWrittenToAnyStorage.test.tsx`, including its
package-wide *"contains no a console call, in any production file"*.
· **Assessment:** The mutants die, and the *claim* is still unpinned. T22's scan is about **token leaks**;
it catches a `console` call in `RequireSession.tsx` as a side effect of sweeping all of `src/`. Reading that
as cover for AC7 would be the mistake T39's harness invented `MISATTRIBUTED` to prevent — a kill by an
unrelated instrument is evidence about the *mutant*, not about the task. The `INHERITED-ONLY` flag is the
signal, and it fired correctly both times.
· **Recommendation:** phase 10 writes AC7's own scan and AC10's no-navigation case regardless of these
kills, and the harness keeps both rows: a package-wide scan that someone later narrows must not silently
take T40's only coverage with it.

### F5 — AC13's mutant is killed by the wrong instrument, so the criterion is not yet measuring its own claim · **Major**
· **Evidence:** **RS14** — a second pathless route carrying `<SignInScreen />` — was expected to survive
until AC13 existed. It **dies**, and its only named failure is *"declares exactly the 20 §4c paths, all
relative, with the catch-all as the only wildcard (L14)"* — the `pathTree` assertion phase 6 grew
(`routesResolveUnderAppPrefixOnly.test.tsx:154`). Nothing about elements is involved: `pathTree` sees a new
`[undefined, []]` entry in the tree's **shape**.
· **Assessment:** AC13 says *"exactly one pathless route in the whole tree carries an element, and it is the
guard."* RS14 only exercises the half `pathTree` already owns — **adding** a pathless route. The half AC13
uniquely owns is an element appearing on a pathless route **already in the shape**: give the existing gate
route an `errorElement`, and the tree shape is unchanged, `routesWithElements` cannot see it (no `path`),
T37's AC9 cannot see it (no `path`), and `pathTree` cannot see it (it maps `path` only). *That* is the hole
phase 4 said T40 opens, and RS14 does not measure it. **A mutant that measures something other than its own
description is worse than a missing mutant, because the score counts it** — T36 and T39 both paid for this.
· **Recommendation:** phase 9 or 10 replaces RS14 with the `errorElement`-on-the-existing-gate shape and
keeps the current row only if it is relabelled as a `pathTree` row. AC13 is then measured against the
mutant it was written for — or, if that mutant also dies on an inherited instrument, AC13 is recorded as
**redundant** and dropped rather than written as a case that pins nothing.

### F6 — The grown `pathTree` pin is doing most of AC12's work · **Minor/Major**
· **Evidence:** **RS11** (the catch-all moved inside the gate) and **RS12** (the gate's children built from
both arrays, so the public nine are inside it too) were both expected to survive until AC12 existed. Both
die, and `pathTree`'s assertion is the named failure in both — the only one, for RS12.
· **Assessment:** RS12 is the strongest evidence here: moving the public nine inside the gate changes **no
behaviour at all**, because they match at the root first, so no rendered-output case could ever see it. The
instrument that catches it is the structural one phase 6 grew — which is a vindication of growing it
explicitly rather than loosening it, and a warning about phase 10's plan. Several AC12 cases as planned
(the children list, the catch-all's position) would be **second copies** of a claim already pinned one file
away.
· **Recommendation:** phase 10 states plainly what AC12 adds beyond `pathTree` — the gate's **element
identity** (`element.type === RequireSession`, which `pathTree` cannot see, since it maps paths) and the
derivation from the exported arrays — and does not spend cases re-pinning the children list. Where it keeps
a redundant case, it says why, as T37's AC9 did.

### F7 — RS04 is correctly unfalsifiable · **Minor**
· **Evidence:** **RS04** — the blocked arm returns `<></>` instead of `null` — survives, and is scored
apart rather than as a kill or a loss.
· **Assessment:** React produces no node for either, so no DOM assertion can separate them, and AC8's claim
is about **output**. A row that could only be killed by making an internal choice observable is a limit of
the code's observability, not a gap in the suite.
· **Recommendation:** none. Keep it scored apart, and keep the reason in the harness beside it.

### F8 — The guard's return type is inferred, where the plan published a signature · **Minor**
· **Evidence:** `auth/RequireSession.tsx:34` — `export function RequireSession() {`. Phase 5 §1 published
`RequireSession(): ReactElement | null`.
· **Assessment:** Inference gives the same type, and `tsc` is clean, so nothing is unsound. But the plan
named a signature and the code does not state it, which is a deviation a reader cannot see without running
the compiler — and the one thing a reader of a route guard most wants to know is that it can return nothing.
· **Recommendation:** annotate it, or record the deviation at phase 12. Cost is one import.

### F9 — Boundary conditions, null-safety, module boundaries: nothing to report
· `accessToken.current()` is `string | null`, and `Boolean(...)` is total over both plus the empty string
(F12's measurement: `accessToken.ts:90` is `held = token;` with no guard) · no `undefined` path: the gate
takes no props and reads no optional field · module direction is `app/` → `auth/`, already established at
`routes.tsx:6-8`, and the guard imports nothing from `app/` · **no** concurrency, transactions, money types
or enumerable secrets are in reach — the gate is a synchronous read of an in-memory string in a browser · no
new storage key, endpoint, config key or catalogue entry · complexity: one expression, no branches beyond
the ternary, no helpers.

### F10 — The reactivity gap is documented, not closed, and that is still right · **Minor** (OQ-T40-1)
· **Evidence:** `auth/RequireSession.tsx` header; `accessToken.ts` publishes no subscribe API; the only
`useSyncExternalStore` in `src/` is `app/RateLimitNotice.tsx:14`.
· **Assessment:** A token arriving with **no navigation** leaves an already-mounted blocked route blocked.
Nothing in `src/` does that today, and a subscription would be a new surface on the session owner that this
task's Files-NOT-to-modify list forbids.
· **Recommendation:** none for T40. It is in the guard's header where a reader of the guard will find it,
and in the frozen brief's Open Questions with task 42 named.

---

## Deviations from the plan, for phase 12

| | |
|---|---|
| **D1** | Phase 6 modified **three** files, not two. `failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx` and `returnTargetOutsideAllowlistFallsBackToHome.test.tsx` both fired; phase 5's risk table predicted neither. Both grown under the closed-task rule, both recorded in `06-implementation.md`. |
| **D2** | The condition is named (`const hasSession = ...`) rather than inlined, because `Boolean(...)` in a condition position is a cast oxlint strips as redundant. The frozen expression is unchanged. |
| **D3** | No explicit return-type annotation — **F8**. |

## What phase 9 and phase 10 inherit

1. **RS01, RS02, RS03, RS05, RS08** must die by named case. They are the task's actual contract.
2. **RS06, RS07** die today by suites that are about something else (**F4**) — write AC7 and AC10 anyway.
3. **RS14 must be replaced** before AC13 is written against it (**F5**).
4. **AC12 must be scoped down** to what `pathTree` cannot see (**F6**).
5. **RS04** stays scored apart.

**Phase 7 complete.** Ten findings. No code changed; the harness reverts with
`git checkout -- frontend/src/` and the tree was committed before it ran.
