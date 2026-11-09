<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation) -->

# frontend · T40 · Phase 13 — PR Preparation

| | |
|---|---|
| **Consumes** | `artifacts/12-specification-verification.md` — **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12's verdict is **PASS**, so this phase proceeds. No code changed here.

> **Note on the trailer.** This prompt asks for `Co-Authored-By: Claude Opus 4.8 (1M context)`. That string
> is stale in the `.ai/` template — it is generated into every task's phase-13 prompt — and the attribution
> in force for this session is `Claude Opus 5`, which is what the eighteen commits carry. The template is an
> owner-level fix (`.ai/generate.py`), not a task-level one, and it is **not** silently obeyed here.

---

## Commit title

```
feat(frontend/T40): gate the eleven authenticated routes behind one session guard (R28, L14)
```

## Commit message

```
Give R28's first clause a mechanism. Until now nothing in src/ guarded any
route, so a visitor with no session was served all twenty declared paths
rather than the nine public ones.

Add auth/RequireSession.tsx: a component that renders <Outlet /> when
Boolean(accessToken.current()) and null otherwise. Apply it ONCE in
app/routes.tsx, as the pathless layout route whose children are exactly
AUTHENTICATED_ROUTE_PATHS. One instance decides for all eleven, so there is
a single place the question is asked, and a screen attached later nests
inside the gate rather than replacing it. The eleven-element alternative
would have taken the element slot task 42 needs and driven both
screen-count pins from three to fourteen on the day it landed.

Render nothing rather than redirect. R28's second clause is task 41's, and
rendering nothing silently is the established behaviour here for a route
that must not render. A case asserts historyAction stays POP on a blocked
path, so this task cannot quietly take task 41's work.

The criterion that made this task hard was not the mechanism. All eleven
authenticated routes are { path } with no element, so "serves no content
without a session" is true today for the wrong reason, and a suite written
against the current table would pass with no production code at all. Every
behavioural claim is therefore measured against a control: one derived
table, probes on all twenty paths and the catch-all, and the gate as the
only value that varies between arms. The control arm replaces the gate with
a pass-through and watches the content arrive.

Close the hole this change opens. Both screen-count pins record a route
only when route.path !== undefined, which is what lets them keep their
three now that an element hangs on a pathless route - and the cost is that
a second element there is invisible to them and to the tree-shape pin,
which maps path. An errorElement on the gate route survived all 1725 tests
before the case that holds it was written.

Grow three inherited instruments rather than bypass them: the tree-shape
pin names the gate's level explicitly, task 33's walker descends the tree
(its own lower bound is what caught the change), and task 25's chain
control names the level a pathless match adds.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| | | |
|---|---|---|
| `frontend/src/auth/RequireSession.tsx` | **new** | +43 | the gate
| `frontend/src/app/routes.tsx` | modified | +15/−2 | the eleven nested under one pathless gate route; the catch-all kept a sibling
| `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | modified | +508/−6 | the named test: the derived-probe instrument, 20 cases, a 50-pattern source sweep and its control
| `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` | modified | +16/−2 | the tree-shape pin grown to name the gate's level; a comment where the count pin's filter became load-bearing
| `frontend/src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx` | modified | +22/−5 | task 33's AC11 walker now descends the tree
| `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | modified | +10/−2 | task 25's chain control names the gate's level

**Six files, +597/−17.** No change to `spec/**`, to the session owner, to the three screens, to
`AppShell`/`mountShell`, to the i18n catalogue, or to the catch-all's own definition.

## Summary

T40 makes the public route set exact. Nine routes are reachable without a session; the eleven authenticated
ones are not, and the split is one pathless layout route carrying `RequireSession` — not eleven elements,
which was the decision the phase-3 challenge forced and phase 4 froze on a measurement of what each shape
does to the existing handoff pins.

Three things are worth a reviewer's attention:

1. **The control is the work.** The criterion was vacuously true before this task, so the suite proves the
   gate's presence rather than a screen's absence: same table, same eleven paths, same probes, one value
   different.
2. **The task closed a hole it opened.** Attaching an element to a pathless route is what lets the two
   screen-count pins keep their three — and makes a *second* element there invisible to every instrument in
   the repository. Measured: it survived 1725 tests. One case now holds it.
3. **Nothing was bypassed.** Three closed tasks' instruments fired and were grown to admit the new state;
   two statements that T40 made false were rewritten to say what changed rather than deleted.

## Testing performed

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **49 / 49** |
| `vitest run` | **1744 / 1745** — the one failure is the known host-environmental `lintRejectsHandWrittenBackendFetch` case (this host's `oxlint@1.87.0` prints no summary line) |
| `python3 -I .ai/prompts/frontend/T40/artifacts/07-mutation-harness.py` | **17 mutants · 16/16 regressions killed · 1 unfalsifiable scored apart · 0 broken · 0 survivors · 0 disagreements** — every kill `KILLED/own`, i.e. with a named failure in a file this task owns or grew |

The harness was run at **four** points — phase 7 (before the suite existed: 10/15, five survivors), phase 9
(after the corrected AC13 mutant: 10/16), phase 10 (16/16) and phase 12 (16/16) — and each run is kept, so
the five rows that died better than predicted and the three disagreements that changed what phase 10 wrote
remain readable.

**Review gates:** 47 findings across three Kimi 2.7 gates (18 · 15 · 14). Four remedies were refused at
phase 4 with the measurement that refuted them; three findings were refuted outright — phase 8's **I9**
(an isolation mount of the guard renders `""` in **both** arms, so the proposed control could not fail) and
phase 11's **F3** and **F14** (both describing the state before phase 9 fixed them).

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` task **40** — *"Public route set is exact."* Verifies `publicRouteSetIsExactlyTheDeclaredSet` |
| **Requirements** | **R28** (first clause; the second is task 41's) · R19 |
| **LOCKED decisions** | **L14** · L1, L2, L3 · L11 · L12, L13 · L15 · L16 |
| **Design** | `design.md` §4c (the nine and the eleven, verbatim) · `design.md:203` (guards live in `auth/`) |
| **Named test** | `publicRouteSetIsExactlyTheDeclaredSet` → R28 (`package.md:152`) |
| **Not this task's** | `unauthenticatedRoutesRedirectToSignIn` → task 41 (`package.md:151`) · `homeRouteRendersLandingScreenAndManagesFocus` → task 174 |

## Branch

Eighteen commits on `spec/service-specs-and-ai-framework`, from `593aa3d6` (phase 0) to `0e9f2b82`
(phase 12), branched off `main`. `main` is untouched and deployable.

## Open, carried forward

| | | owner |
|---|---|---|
| The gate is **dormant**: no authenticated screen exists to be gated, so R28's first clause is verified by probes | tenth consecutive task in this position; the handoff pins make it visible the moment a screen lands | **task 42** (blocked on 173/174) |
| **OQ-T40-1** — the gate reads at render, not reactively | a token arriving with no navigation would leave a mounted blocked route blocked; nothing does that today, and closing it needs a subscription on the session owner this task may not add | **task 42** |
| **RS04** unfalsifiable | `null` and `<></>` both render nothing | — |
| Spec drift at `design.md:137` — *"PROPOSED … pending author confirmation (Q6)"* vs `package.md:368`'s *"Resolved (2026-10-10)"* | `spec/` is not modified by this task | **owner** |
| The `.ai/` phase-13 template's stale `Claude Opus 4.8 (1M context)` trailer | regenerate via `.ai/generate.py` | **owner** |

**Phase 13 complete. T40 CLOSED — PASS.**
