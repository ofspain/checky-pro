<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T42 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T42 — Authenticated shell |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py`, `artifacts/07-mutation-run.txt` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

Commands run independently on the tree as delivered by Phase 7. No code or contracts were changed.

| Command | Result |
|---|---|
| `vitest run src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | **51 / 51 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1879 / 1880 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T42/artifacts/07-mutation-harness.py` | **19/19 regressions killed**, 1 canary killed, 1 unfalsifiable survived (TE21), **0 broken**, 3 disagreements |

The whole-suite count matches the self-review (1880 cases, 1 environmental failure). The independently-run harness reproduces the self-review's second-run outcome exactly.

---

## Findings

### I1 — The implementation resolved OQ-T42-2 by adding sign-out chrome, contrary to Phase 3's recommendation
· **Severity:** **Major** (scope)
· **Evidence:** `frontend/src/app/AuthenticatedLayout.tsx:24-36` explains the owner decision to wire `endSession()` and `signOut()`; `frontend/src/app/ui/Banner.tsx` was added as the ninth primitive. Phase 3 recommended carrying OQ-T42-2 to Phase 4 / a later task.
· **Assessment:** The self-review records this as an owner decision closed on 2026-11-14. It is not a defect if approved, but it is a material scope expansion: it turns a layout-only task into one that closes the dormant sign-out path (OQ-T36-5), adds a new primitive to T173's pinned inventory, and imports from `auth/` and `api/` — all of which the brief's constraints originally forbade. The decision should be visible in the Phase 4 approval gate.
· **Recommendation:** None for the code, assuming approval. Ensure the task record and `04-freeze-task.md` (if it exists) reflect the resolved OQ-T42-2 and the T173 primitive inventory change.

### I2 — `AuthenticatedLayout.tsx` violates the brief's module-boundary constraint
· **Severity:** **Major**
· **Evidence:** The brief's constraints state: "the layout may import from `app/ui`, `react-router` (`Outlet`) and `react-i18next`, and from `app/routes` if it needs the path constants. It must not import from `auth/`". `frontend/src/app/AuthenticatedLayout.tsx:4-7` imports from `../api/crossTabSignOut`, `../auth/endSession`, `../auth/RedirectToSignIn`, and `../auth/signOut`.
· **Assessment:** The violation is deliberate and necessary for the sign-out feature, but it is still a violation of the frozen constraint. The AC13 source scan in the named test has been written to permit exactly these imports, which is the right way to ratify a scope change — the test documents the new contract rather than pretending the old one holds.
· **Recommendation:** Update `artifacts/04-freeze-task.md` and the task record to list the actual import surface, or add a deviation entry noting that the constraint was relaxed by owner decision. Do not leave the brief and the source in contradiction.

### I3 — The layout imports from `api/`, which was not in the allowed import set either
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/AuthenticatedLayout.tsx:4` imports `listenForSignOut` from `../api/crossTabSignOut`. The brief's allowed imports are `app/ui`, `react-router`, `react-i18next`, and optionally `app/routes`.
· **Assessment:** Like I2, this is a scope expansion. The cross-tab listener is part of the sign-out feature. The AC13 source scan permits it. But the brief's module boundary needs to be updated to match.
· **Recommendation:** Same as I2: update the frozen constraints or record a deviation.

### I4 — The `onClick={endSession}` reference is a latent parameter-mismatch hazard
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/AuthenticatedLayout.tsx:71` passes `endSession` directly to `ActionButton onClick`. `ActionButton` declares `onClick?: () => void` (`frontend/src/app/ui/ActionButton.tsx:18`). `endSession()` currently declares no parameters (`frontend/src/auth/endSession.ts:108`).
· **Assessment:** This type-checks today because a function with no required parameters is assignable to `() => void`. If a future change adds an optional first parameter to `endSession` (e.g., `endSession(hint?: string)`), React will still call the handler with a `MouseEvent`, and TypeScript will not complain because an optional parameter is assignable to `() => void`. The result would be `endSession` receiving a `MouseEvent` as its `hint`. The self-review F3 identified the same hazard and debated whether to wrap it.
· **Recommendation:** Change to `onClick={() => endSession()}`. The one-line closure removes the entire class of bug. If the AC13 assertion is weakened by the wrapper, split the assertion into "calls `endSession`" (source scan) and "calls nothing else" (mutant).

### I5 — No StrictMode test covers the layout's subscribe/cleanup effect
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/AuthenticatedLayout.tsx:55-64` opens a cross-tab listener in `useEffect`. The self-review F8 / "What the harness does NOT measure" lists StrictMode as a gap.
· **Assessment:** Under StrictMode the effect runs twice: subscribe, cleanup, subscribe. The `close()` is idempotent, so the behavior is correct, but it is not measured. A StrictMode variant of AC14 would verify that double invocation does not create a stale listener or duplicate redirect.
· **Recommendation:** Add a T42 AC14 case that mounts the layout under `StrictMode`, posts a sibling sign-out, and asserts the tab redirects exactly once.

### I6 — No test asserts the sign-out button has a visible focus ring
· **Severity:** **Minor**
· **Evidence:** AC9 says "every interactive element in it is keyboard-reachable with a visible focus ring". `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:721-735` checks tag, type, text, and `tabindex`, but not focus-ring styles.
· **Assessment:** T173's primitive tests likely cover `ActionButton`'s focus styles, so the layout gets this by composition. But a layout-specific regression (e.g., someone wrapping the button in a `div` with `overflow-hidden` that clips the ring) would not be caught.
· **Recommendation:** Optional: add a computed-style assertion that the focused button's `outline` is non-zero, or rely on T173's primitive coverage and document the choice.

### I7 — The `Banner` primitive has no accessible name
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/ui/Banner.tsx:26-32` renders `<header className="...">{children}</header>` with no heading, `aria-label`, or `aria-labelledby`.
· **Assessment:** A `<header>` maps to `banner` landmark role natively, which is good. But a screen reader user navigating by landmark will hear "banner" with no name. WCAG does not strictly require named landmarks, but best practice is to label the banner with the application name. The layout currently renders only a sign-out button inside it.
· **Recommendation:** Optional: add a `Text` or `Heading` (not h1) inside `Banner` with the app name, or add `aria-label` to `Banner`. If the brief wants to keep the chrome minimal, document that the banner is intentionally unnamed.

### I8 — AC7 case mixes two unrelated concerns
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:913-925` is titled "AC7 names the layout and the ninth primitive in the lists that pin them", but its last two assertions check that `auth/signOutClearsMemoryAndEndsSasSession.test.tsx` contains `'app/AuthenticatedLayout.tsx',` — which is about closing OQ-T36-5, not AC7.
· **Assessment:** The assertion is valuable (it proves the end-session suite acknowledges the first caller), but it belongs in the AC13 case or a separate "OQ-T36-5 closed" case. Mixing it into AC7 makes the test harder to read and the failure message confusing if the pin list is correct but the end-session reference is missing.
· **Recommendation:** Move the end-session importer assertion to the AC13 case or a dedicated case named after the open question it closes.

### I9 — The `Banner`/`Screen` content column alignment is coincidental and unasserted
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/ui/Banner.tsx:29` and `frontend/src/app/ui/Screen.tsx:17` both use `mx-auto max-w-prose px-6`. The self-review F4 identified this.
· **Assessment:** The two primitives independently happen to share the same column classes. If one changes, the chrome and screen will visually misalign, and no test will fail.
· **Recommendation:** Add a case that reads both primitive sources and asserts the shared column utilities, OR introduce a shared layout constant (e.g., `CONTENT_COLUMN = 'mx-auto max-w-prose px-6'`) in a neutral location. The second option is a design-system change and should be approved; the first is a test-only guard.

### I10 — The one-way `siblingSignedOut` latch is safe today but fragile
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/AuthenticatedLayout.tsx:53,66` sets the latch to true and never clears it. The self-review F7 identified this.
· **Assessment:** The latch is safe because `RedirectToSignIn` navigates away and unmounts the layout, so the component is destroyed before a second sign-out event could arrive. But if a future flow re-mounts the layout without a full tree remount (e.g., a sign-in-in-place feature), the latch would still be set and the layout would immediately redirect.
· **Recommendation:** Document the assumption in the component comment, or reset the latch if the component is ever reused across sessions. No code change needed now.

### I11 — Ten authenticated paths now render only a sign-out button
· **Severity:** **Informational**
· **Evidence:** `frontend/src/app/routes.tsx:100-103` maps ten paths to `{ path }` with no element, so they render only the layout's banner.
· **Assessment:** This is what the task asked for — a layout behind the gate — but it is worth noting that the user experience on those ten paths is a blank page with a "Sign out" button until their owning tasks run. The self-review F8 noted this as intended.
· **Recommendation:** None. Record as a UX state for the owner.

### I12 — The harness reporting bug in the first run is fixed in the second run
· **Severity:** **Positive / verification**
· **Evidence:** The self-review F1 describes a bug where BROKEN rows were misreported as KILLED/inherited. The second run reportedly prints `BROKEN` and flags "DISAGREES - nothing ran".
· **Assessment:** The fix is important for harness integrity. Without it, a BROKEN mutant could be counted as a kill, inflating the score. The second run's reporting is correct.
· **Recommendation:** None. This is a good process improvement.

### I13 — The second-run disagreements are definition collisions, not defects
· **Severity:** **Positive / verification**
· **Evidence:** The self-review F5 says TE11, TE12, TE20 revert/extend lists inside `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`, which T42 grew, so kills report `own` against predicted `inherited`.
· **Assessment:** This is the same attribution collision T174 recorded. The expectations are deliberately left conservative.
· **Recommendation:** None. The reporting is honest.

### I14 — The BROKEN heuristic cannot distinguish "file did not load" from "a parameterized case was deleted"
· **Severity:** **Minor**
· **Evidence:** The self-review F2 describes the heuristic `total < base_total` and the TE12 case where a renamed primitive caused one test not to run for a different reason.
· **Assessment:** The cheap mitigation (rename instead of delete for list-feeding mutants) is applied. A proper fix would compare case-name sets, which is machinery work for a future harness copy.
· **Recommendation:** None for this task. Record for the thirteenth copy.

### I15 — The T174 I9 focus guard does not cover `AuthenticatedLayout`, but T42 AC8 does
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:422-454` guards SignInScreen, SignedOutScreen, CallbackScreen. `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:692-701` guards the layout itself.
· **Assessment:** The guard is correctly placed in the task that could introduce the regression. Direct-mount cases for screens stay in T174; layout focus behavior stays in T42.
· **Recommendation:** None.

### I16 — The route table nesting keeps screen-count pins honest
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/routes.tsx:90-105` inserts `AuthenticatedLayout` as a pathless child of `RequireSession`; the eleven paths are unchanged as its children.
· **Assessment:** `SCREENS_ATTACHED_SO_FAR` correctly stays at 4 (sign-in, callback, signed-out, home) because the layout is pathless. `AUTHENTICATED_ROUTE_PATHS` and `PUBLIC_ROUTE_PATHS` are untouched (AC5).
· **Recommendation:** None.

### I17 — The `Banner` primitive's contract is well documented
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/ui/Banner.tsx:6-21` explains why it is the ninth primitive, the native `<header>` mapping, and the demoting-ancestry condition.
· **Assessment:** The comment correctly addresses Phase 3's F2 contradiction and justifies the scope change. The AC9 case tests the ancestry, which is the right level of enforcement.
· **Recommendation:** None.

### I18 — The whole-suite count matches the self-review
· **Severity:** **Positive / verification**
· **Evidence:** My run: 1879 passed / 1880 total. Self-review: 1880 cases, 1 failing.
· **Assessment:** The numbers match. The single failure is the known environmental `oxlint` case.
· **Recommendation:** None.

---

## Summary

T42's implementation is substantially more featureful than Phase 3 recommended: it added sign-out chrome and a `Banner` primitive, resolving OQ-T42-2 and OQ-T36-5 in this task. The code is clean and the named suite is strong (51 cases), but the scope expansion creates two major record-keeping issues: **I1** (OQ-T42-2 was decided here, not carried) and **I2/I3** (the layout imports from `auth/` and `api/`, violating the brief's module boundaries). The most important code issue is **I4** (`onClick={endSession}` is a latent parameter-mismatch hazard). Other gaps are **I5** (no StrictMode test for the layout effect), **I6** (no focus-ring assertion), **I7** (banner has no accessible name), **I8** (AC7 case mixes concerns), and **I9** (Banner/Screen column alignment is coincidental). The suite is ready to ship once the brief/task record is reconciled with the actual import surface.

**Phase 8 complete.** Eighteen findings returned; no code or contract changed.
