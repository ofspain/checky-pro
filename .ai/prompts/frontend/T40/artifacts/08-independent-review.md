<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T40 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T40 — Public route set is exact |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, implementation |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run` | **1724/1725 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T40/artifacts/07-mutation-harness.py` | **10/15 regressions killed, 5 expected survivors for phase 10, 1 unfalsifiable scored apart (RS04), 0 broken, 4 inherited-cover disagreements** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |

No production or test code was changed during this review.

---

## Findings

### I1 — The named test suite does not exist yet, so R28's first clause has no direct measurement
· **Severity:** **Blocker** (expected, to be closed in phase 10)
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` ends with T37's cases and a header saying T40 will extend it. The mutation harness shows RS01, RS02, RS03, RS05, and RS08 surviving all 1725 tests.
· **Assessment:** This is the expected state at phase 7 and is acknowledged by the self-review. The implementation is in place (`auth/RequireSession.tsx`, `app/routes.tsx`) but the suite that owns the gate's behavior is not yet written. All five survivors are scheduled for phase 10.
· **Recommendation:** Phase 10 must write the three arms called out in the self-review: (1) each public path resolves and renders without a session, (2) each authenticated path renders nothing without a session but renders with a session, and (3) the AC3 control that proves removing the guard makes AC2 fail.

### I2 — `RequireSession` has no explicit return-type annotation
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/RequireSession.tsx:32` — `export function RequireSession() {`. Phase 5's plan published `RequireSession(): ReactElement | null`.
· **Assessment:** Independently confirms the self-review's F8. Inference gives a wider type, and while `tsc` is clean, a reader cannot see the intended return shape without running the compiler. This matters because the component's whole contract is that it may return nothing.
· **Recommendation:** Add `import type { ReactElement } from 'react';` and annotate the return type as `ReactElement | null`.

### I3 — The structural `pathTree` pin in task 16's suite is doing most of T40's structural work
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:159-163` asserts the route tree shape, including the pathless guard route. RS09, RS10, RS11, RS12, RS13, and RS16 all die on this assertion.
· **Assessment:** Independently confirms the self-review's F6. The `pathTree` instrument is strong and now captures the guard placement, catch-all position, and public/authenticated split. Phase 10 must not waste cases re-proving these.
· **Recommendation:** Phase 10's AC12 should add only what `pathTree` cannot see: the guard's element identity (`element.type === RequireSession`) and the derivation of the guarded children from `AUTHENTICATED_ROUTE_PATHS`.

### I4 — The `routesWithElements` predicate cannot see a pathless route's `errorElement`
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:22-25` returns paths only when `route.path !== undefined`. The self-review's F5 notes that an `errorElement` on the existing pathless guard route would be invisible to this predicate and to T37's AC9.
· **Assessment:** Independently confirms the self-review's F5. RS14 as currently written adds a second pathless route with an element, but it is killed by `pathTree` (new route shape), not by the claim it was meant to test.
· **Recommendation:** Replace RS14 with a mutant that adds an `errorElement` to the existing pathless guard route, leaving the tree shape unchanged, before writing AC13. If that mutant also dies on an inherited instrument, record AC13 as redundant rather than as a false pin.

### I5 — AC7 and AC10 are killed by inherited scans, but the task should still own them
· **Severity:** **Major**
· **Evidence:** RS06 (gate redirects) and RS07 (gate writes to console) are killed by inherited suites, with no case in T40's files failing. RS06 dies on `tokensAreNeverWrittenToAnyStorage.test.tsx` and related scans.
· **Assessment:** Independently confirms the self-review's F4. A kill by an unrelated instrument is evidence about the mutant, not about the task. If the inherited scan is narrowed later, T40's coverage disappears silently.
· **Recommendation:** Phase 10 writes AC7's own source scan (forbidden surfaces, no storage, no network, no console, no navigation) and AC10's own no-navigation case, even though the mutants already die.

### I6 — The `RequireSession` component reads `accessToken.current()` at render, which is correct but not reactive
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/RequireSession.tsx:38` and the header's OQ-T40-1.
· **Assessment:** The header documents the reactivity gap honestly. No test currently asserts this limit. This is fine because the task explicitly does not close it.
· **Recommendation:** None for T40. Task 42 should read the header before assuming reactivity.

### I7 — The guard's condition correctly treats empty string as no session
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/RequireSession.tsx:33-38` uses `Boolean(accessToken.current())`. RS03 expects to survive until phase 10.
· **Assessment:** The implementation is correct per the constraint. Phase 10 should include a case that sets `accessToken.current()` to `''` (via `retainAccessToken('')`) and asserts the route is blocked.
· **Recommendation:** Add the empty-string case to phase 10's AC1/AC2 coverage.

### I8 — The `pathTree` assertion encodes the exact order of `AUTHENTICATED_ROUTE_PATHS`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:161` maps `AUTHENTICATED_ROUTE_PATHS` into the expected tree.
· **Assessment:** This pins the order of authenticated routes, which is good. A regression that reorders the array would fail. However, route order among authenticated paths does not affect matching (they are disjoint paths), so the pin is stronger than behavior requires.
· **Recommendation:** Acceptable. It makes the route table's shape a deliberate contract.

### I9 — No isolation test exists for `RequireSession`
· **Severity:** **Minor**
· **Evidence:** `auth/RequireSession.tsx` is a simple component. No test file mounts it directly.
· **Assessment:** The behavior will be tested through the route table, which is the more important claim. An isolation test is optional but would make the guard's contract explicit.
· **Recommendation:** Consider adding a focused case in `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` that renders `<RequireSession />` inside a memory router with and without a retained token, asserting `<Outlet />` vs `null`. This is stronger than a route-table test for AC3's control.

### I10 — The `routesWithElements` comment now explains why screens remain three after T40
· **Severity:** **Informational**
· **Evidence:** `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:169-174`.
· **Assessment:** The sibling instrument was grown correctly: the screen-count pin stays at three because it filters out pathless routes (the guard). The handoff to T40's named test for the pathless route's element identity is documented.
· **Recommendation:** None.

### I11 — The `RequireSession` component does not import from `app/`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/RequireSession.tsx:1-2` imports only from `react-router` and `./accessToken`.
· **Assessment:** This satisfies AC6. No source scan asserts it yet, but phase 10 should add one.
· **Recommendation:** Phase 10 adds a source scan forbidding imports from `../app/` in `RequireSession.tsx`.

### I12 — The `RequireSession` header documents that the guard is a layout route, but the named test file header still says T40's claim is "currently untestable"
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:10-12` says the T40 claim "needs the auth gate, and no route requires a session yet, so it is not merely unwritten - it is currently untestable."
· **Assessment:** That was true before T40 implemented the gate. Now the gate exists, and the claim is testable. Phase 10 should update the header.
· **Recommendation:** Update the file header to state that the gate is implemented and the remaining work is to extend the suite with T40's claims.

### I13 — The `routes.tsx` import-cycle comment does not mention the new `RequireSession` import
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/routes.tsx:2-5` explains the cycle involving `SignInScreen`. It does not mention `CallbackScreen`, `SignedOutScreen`, or `RequireSession`.
· **Assessment:** The three new imports do not create cycles because they don't read route constants at module scope. The comment is still accurate but incomplete.
· **Recommendation:** Optional: add a sentence that `CallbackScreen`, `SignedOutScreen`, and `RequireSession` are also imported here but do not participate in the cycle.

### I14 — No test asserts that the guard does not apply to public routes or the catch-all
· **Severity:** **Major**
· **Evidence:** The `pathTree` assertion shows public routes and the catch-all as direct children of `/`, while authenticated routes are nested under the pathless guard. This is structural coverage.
· **Assessment:** The structural assertion is strong. Phase 10 should also have a rendered-output case that visits a public route without a session and asserts no content is blocked (AC1/AC10).
· **Recommendation:** Phase 10 adds rendered-output cases for public routes and the catch-all without a session.

### I15 — The mutation harness reports 4 inherited-cover disagreements, all handled correctly
· **Severity:** **Informational**
· **Evidence:** RS07, RS11, RS12, RS14.
· **Assessment:** The harness correctly flags these. The self-review explains each. No action needed beyond phase 10 writing its own cases.
· **Recommendation:** None.

---

## Summary

T40's implementation is clean and the self-review is thorough. The dominant finding is the expected one: **I1**, the named test suite does not exist yet, so the gate's actual behavior is unmeasured. The independent review confirms the self-review's other major findings: **I2** (missing return type), **I3** (`pathTree` doing structural work), **I4** (`routesWithElements`/`errorElement` hole), and **I5** (inherited scans killing AC7/AC10 mutants). Phase 10 has a clear mandate: write the named suite's three arms, add the guard-identity and no-navigation/source-scan cases, replace RS14 with the correct mutant shape, and update file headers.

**Phase 8 complete.** Fifteen findings returned; no code or contract changed.
