<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T174 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T174 — Home screen |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py`, `artifacts/07-mutation-run.txt` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

All commands run independently of the self-review, on the same tree.

| Command | Result |
|---|---|
| `vitest run src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | **24 / 24 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1857 / 1858 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T174/artifacts/07-mutation-harness.py` | **17/17 regressions killed, 1 canary killed, 1 unfalsifiable survived (RE18), 0 broken, 5 disagreements** |

No production or test code was changed during this review.

---

## Findings

### I1 — The focus target decision is well argued and correctly implemented
· **Severity:** **Minor/Major** (verification of the design decision)
· **Evidence:** `frontend/src/app/HomeScreen.tsx:21-26` explains why the heading was chosen over `<main>` or a labelled wrapper. `frontend/src/app/ui/Heading.tsx:11-27` sets `tabIndex={-1}` inside the primitive and rejects the `tabIndex` prop approach that Phase 3 proposed.
· **Assessment:** The implementation chose to make every `<h1>` focusable rather than add a prop. This preserves T173's rule #2 literally and avoids a prop that future screens could forget. The trade-off is that every heading now carries `tabindex="-1"`, but since `-1` is not in the tab order, there is no user-facing cost. My Phase 3 recommendation would have bent the rule; the freeze's decision is cleaner.
· **Recommendation:** None. This finding records the resolution of F1/F2 from Phase 3.

### I2 — My Phase 3 claim that T173 introduced `SCREENS_ATTACHED_SO_FAR` was wrong
· **Severity:** **Minor** (process correction)
· **Evidence:** `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:18-28` shows the list existed before T173 and was extended for T174. The comment explicitly corrects my Phase 3 assertion.
· **Assessment:** `SCREENS_ATTACHED_SO_FAR` was introduced by task 16; `REDIRECTS_ATTACHED_SO_FAR` was introduced by task 41. T173 touched neither. My Phase 3 finding conflated the two instruments.
· **Recommendation:** None for the code. For the review record: the Phase 3 challenge had an error that Phase 6/7 caught and corrected.

### I3 — The end-to-end arrival proof lives in task 27's suite, as the self-review states
· **Severity:** **Minor/Major** (verification of F5)
· **Evidence:** `frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` contains the T27-D1 case that drives a real callback onto `/app/home` and asserts focus lands on the heading.
· **Assessment:** I verified the case exists and names task 174. It is the strongest proof that the whole sign-in flow lands correctly. T174's named suite cites it by name, so a rename will break a test rather than silently orphan the citation.
· **Recommendation:** None. This confirms self-review F5.

### I4 — AC1 does not assert the order of elements inside `<main>`
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:125-131` checks that `<main>` contains one `<h1>` and that the `<h1>` is inside `<main>`, but not that the heading precedes the paragraph.
· **Assessment:** A mutant that rendered `<Screen><Text>...</Text><Heading>...</Heading></Screen>` would still pass AC1, yet the document would present the explanation before the screen name. For a landing screen this is a real accessibility/UX regression. The R30 skeleton for the signed-out screen asserts order explicitly ("one landmark holding a level-one heading, a paragraph and the way back, in that order").
· **Recommendation:** Add an AC1 assertion that `container.querySelector('h1')` is the first child of `<main>`, or that the heading appears before the paragraph in the DOM.

### I5 — The `import.meta.url` reader in the test file works, contradicting T41's Phase 10 note
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:44` uses `fileURLToPath(import.meta.url)` to derive `srcDir`. The test passes.
· **Assessment:** T41's Phase 10 artifact stated that `import.meta.url` is not a `file:` URL under vitest's jsdom environment and had to switch to `expect.getState().testPath`. Either that issue was specific to T41's setup, or it has been resolved. T174's test uses the simpler pattern successfully.
· **Recommendation:** None required, but note the discrepancy in case a future review wonders which pattern to copy. Prefer `expect.getState().testPath` for consistency with T40/T41 if the file is ever refactored.

### I6 — The mutation harness's disagreements are definition collisions, not defects
· **Severity:** **Minor** (verification of F1)
· **Evidence:** The harness reports five disagreements: RE09, RE15, RE16, RE17, RE19 were expected `inherited` but killed `own`.
· **Assessment:** These rows revert data that belongs to closed tasks (catalogue keys, render-site lists, screen-count lists), but the instruments that detect the reversion were grown by T174. File-level attribution cannot express "task 16's logic reading task 174's data", so the harness reports `own`. The self-review correctly identifies this as a reporting limitation, not a strength gap.
· **Recommendation:** None. The expectations were deliberately left unchanged.

### I7 — RE18 is correctly classified as unfalsifiable
· **Severity:** **Minor** (verification of F2)
· **Evidence:** RE18 mutates the focus effect to run on every render instead of once. It survives because `focus()` on an already-focused element is a no-op.
· **Assessment:** I agree with the self-review: no single-edit mutant can distinguish "idempotent under double invocation" from "works at all". The StrictMode case is corroboration, not an independent instrument.
· **Recommendation:** None. This is a fundamental limit of testing focus with jsdom.

### I8 — AC8's behavioural claim (Tab leaves the screen) remains unmeasured
· **Severity:** **Minor/Major** (verification of F3 and OQ-T174-5)
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:321-345` checks structural properties that prevent trapping (negative tabindex, no key handlers, no modal), but does not press Tab.
· **Assessment:** The suite header honestly states that jsdom does not implement sequential focus navigation, so a Tab test would either fail against correct code or pass against a trap. The structural test is the best available proxy. This is a real but documented gap.
· **Recommendation:** None for the code. Consider adding `@testing-library/user-event` and a real Tab test when the project's dependency policy allows it, or when Playwright e2e coverage arrives.

### I9 — No test asserts that other screens do not steal focus on mount
· **Severity:** **Minor**
· **Evidence:** `Heading` now renders every `<h1>` with `tabIndex={-1}`. Only `HomeScreen` calls `.focus()`.
· **Assessment:** A regression in `SignInScreen`, `CallbackScreen`, or `SignedOutScreen` that added a focus effect would now succeed in moving focus, potentially surprising a screen-reader user. The existing suites for those screens do not assert where focus lands on mount.
· **Recommendation:** Optional: add a one-line assertion to each existing screen's mount test that `document.activeElement` is `<body>` (or the first focusable control, for sign-in). This is cheap and prevents accidental focus grabs.

### I10 — The route table conditional for HOME_PATH is the right shape
· **Severity:** **Minor** (positive finding)
· **Evidence:** `frontend/src/app/routes.tsx:85-94` attaches `<HomeScreen />` only for `HOME_PATH` and leaves the other ten authenticated paths without an element.
· **Assessment:** This mirrors the public branch pattern and avoids placeholders. The screen-count pins correctly see four attached elements (three screens + one redirect) for the routes they inspect.
· **Recommendation:** None.

### I11 — `HomeScreen.tsx` correctly imports only allowed modules
· **Severity:** **Minor** (positive finding)
· **Evidence:** `frontend/src/app/HomeScreen.tsx` imports `react`, `react-i18next`, and `./ui`.
· **Assessment:** This satisfies the module-boundary constraint. The AC6 source scan pins it.
· **Recommendation:** None.

### I12 — The catalogue entries are minimal and identity-free
· **Severity:** **Minor** (positive finding)
· **Evidence:** `frontend/src/i18n/catalogue.ts:61-70` defines `home.heading: 'Home'` and `home.explanation: 'You are signed in. There is nothing here yet.'`.
· **Assessment:** The copy is generic and contains no name, email, account state, or other identity-shaped text. AC6's forbidden-word scan passes.
· **Recommendation:** None.

### I13 — The `Heading` type remains `{ children: ReactNode }`
· **Severity:** **Minor** (positive finding)
· **Evidence:** `frontend/src/app/ui/Heading.tsx:29` and `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:355-360`.
· **Assessment:** The props type was not widened. The build gate (`lintRejectsHandWrittenBackendFetch.test.ts`) enforces this at `tsc` level, and the source scan corroborates it.
· **Recommendation:** None.

### I14 — The harness machinery fixes are working
· **Severity:** **Minor** (positive finding)
· **Evidence:** The eleventh harness copy reports `in:` files holding new failures only, and case names come from `×` lines rather than `FAIL` lines (no double-counting).
· **Assessment:** The preflight also caught a prose match for `RE04`'s anchor in `Heading.tsx`, which would have edited the comment instead of the code. These improvements are visible in the run output.
· **Recommendation:** None.

### I15 — The whole-suite count matches the self-review
· **Severity:** **Informational**
· **Evidence:** My run: 1857 passed / 1858 total, 1 environmental failure. Self-review: 1858 cases, 1 failing.
· **Assessment:** The numbers match. The environmental failure is the same `oxlint@1.87.0` host case seen in every prior task.
· **Recommendation:** None.

---

## Summary

T174's implementation is clean and the suite is strong. The focus-target decision was implemented by setting `tabIndex={-1}` inside `Heading` rather than exposing it as a prop, which is a defensible resolution of Phase 3's central question. My independent verification reproduced the self-review's numbers: 17/17 mutants killed, 1 canary, 1 unfalsifiable survivor, 5 disagreements that are definition collisions rather than defects. The most important remaining gaps are **I4** (AC1 does not assert the heading precedes the paragraph), **I8** (no real Tab keypress test), and **I9** (other screens are not protected against accidental focus grabs now that every heading is focusable). I also acknowledge **I2**: my Phase 3 challenge incorrectly attributed `SCREENS_ATTACHED_SO_FAR` to T173; task 16 introduced it.

**Phase 8 complete.** Fifteen findings returned; no code or contract changed.
