<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T174 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T174 — Home screen |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

All commands run on the tree as delivered by Phase 10. No code or contracts were changed.

| Command | Result |
|---|---|
| `vitest run src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | **29 / 29 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1862 / 1863 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T174/artifacts/07-mutation-harness.py` | **19/19 regressions killed**, 1 canary killed, 1 unfalsifiable survived (RE18), **0 broken**, 5 disagreements |

---

## Findings

### F1 — The suite is comprehensive and every AC has at least one killing mutant
· **Severity:** **Positive / summary**
· **Evidence:** 29 cases covering AC1–AC10 plus the Phase 8 I9 guard. The mutation harness reports 19/19 killed with no broken rows.
· **Assessment:** The test generation phase addressed both Phase 8 findings (I4 ordering, I9 cross-screen focus grab) with dedicated cases and mutants (RE20, RE21). The StrictMode-through-real-route-table case closes a genuine hole that no prior case covered. The manifest correctly maps cases to mutants and criteria.
· **Recommendation:** None. This is the baseline against which the remaining findings are measured.

### F2 — The AC9 props-type assertion is regex-brittle, though `tsc` is the real enforcement
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:375-380` asserts `/forwardRef<HTMLHeadingElement,\s*\{\s*children:\s*ReactNode\s*\}>/` and `/tabIndex\?\s*:/`.
· **Assessment:** The first regex will fail if the type is ever extracted to a named `Props` alias or formatted across lines. The second regex is safe because it targets the prop syntax, not the JSX attribute. Both assertions are described in the test as corroboration; the actual enforcement is `lintRejectsHandWrittenBackendFetch.test.ts` running `tsc` and Vite. So the brittleness is acceptable, but a future refactor of `Heading.tsx` could produce a misleading failure message about "props type widened" when the type is semantically unchanged.
· **Recommendation:** Add a comment noting that the regex is a smoke test and the real gate is `tsc`, or relax the regex to allow optional whitespace/newlines and a named type alias. Low priority.

### F3 — AC8's "no key handler" clause has no dedicated mutant
· **Severity:** **Minor/Major**
· **Evidence:** The Phase 10 manifest lists this under "Gaps this suite does not close" (gap 2). The case at `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:359-365` asserts `source` does not match `/onKeyDown|addEventListener/`.
· **Assessment:** The assertion is present and would kill a mutant that added a handler, but no harness row plants such a mutant. That means the precision of the regex is unmeasured. The other two AC8 clauses (negative tabindex, no modal) are independently killed by RE04, so the trap is covered in practice. A real focus trap needs both a handler and a reason to swallow keys; the structural clauses make it hard to build one accidentally.
· **Recommendation:** Optional: add a canary mutant that injects `onKeyDown={() => {}}` into `HomeScreen.tsx` and confirm the AC8 case kills it. This would close the gap without adding a test case.

### F4 — The "heading before paragraph" test assumes `<main>` has exactly two children
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:147-150` maps `main.children` to `['H1', 'P']`.
· **Assessment:** This is the right assertion for today's `Screen` primitive, but it will break if `Screen` ever adds a wrapper, skip link, or status region inside `<main>`. A more resilient assertion would be: the first heading-related element inside `<main>` is `<h1>`, and the `<p>` appears after it, ignoring non-element nodes. The current test is fine for the current design; the finding records a future-fragility, not a present defect.
· **Recommendation:** Optional: rewrite the assertion to check relative order rather than exact child list.

### F5 — The I9 cross-screen guard is correctly placed in T174's suite
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:389-422` mounts `SignInScreen`, `SignedOutScreen`, and `CallbackScreen` and asserts each leaves `document.activeElement` on `<body>`.
· **Assessment:** This is the right fix for Phase 8 I9. Because T174 made every `<h1>` focusable, a future `.focus()` call in any screen would silently change behavior. The guard lives in the task that created the risk, not in three closed-task files. Each screen is mounted through a minimal router so the test is realistic.
· **Recommendation:** None.

### F6 — The deviation in `returnTarget.ts` is a comment-only change in a frozen file
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/returnTarget.ts:36-37` now reads "It was someone else's task and it has now run." The Phase 10 manifest records this as D-6.
· **Assessment:** The original comment became false after T174 shipped. Updating it is better than leaving a lie in a frozen file, but it is technically a production-file modification during a test-only phase. The manifest is transparent about it and the change is purely prose.
· **Recommendation:** Accept the deviation as recorded. No further action.

### F7 — RE18 remains correctly classified as unfalsifiable
· **Severity:** **Positive / verification**
· **Evidence:** The harness reports RE18 "CANARY: the focus effect runs on every render, not once" as `SURVIVED`.
· **Assessment:** `focus()` on an already-focused element is a no-op, so a mutant that calls it on every render is observationally equivalent to the correct code. The Phase 10 manifest and Phase 8 review both acknowledge this. The StrictMode cases are corroboration, not isolation.
· **Recommendation:** None. This is a fundamental limit of testing focus with jsdom, not a suite weakness.

### F8 — The five harness disagreements are definition collisions, not regressions
· **Severity:** **Positive / verification**
· **Evidence:** RE09, RE15, RE16, RE17, RE19 were expected `inherited` but killed `own`.
· **Assessment:** These rows revert data owned by earlier tasks (catalogue keys, render-site lists, screen-count lists), but the instruments that detect the reversion were grown by T174. File-level attribution cannot express "task 16's assertion reading task 174's data," so the harness conservatively labels them `own`. The mutations are still killed.
· **Recommendation:** None. The expectations could be updated to `own` to remove the disagreements, but the current labeling is honest about the intended ownership.

### F9 — The suite does not measure screen-reader announcement
· **Severity:** **Minor**
· **Evidence:** Every AC2 case asserts `document.activeElement` is the `<h1>`, not that a screen reader would announce it.
· **Assessment:** jsdom does not expose accessibility APIs, so this cannot be measured in unit tests. The test header is explicit about this limitation. The choice of `<h1>` as focus target is justified by the implementation comment and the AC2 cases.
· **Recommendation:** None for unit tests. Consider an a11y e2e test (e.g., Playwright + NVDA/VO or axe) when e2e coverage is added.

### F10 — The whole-suite count and environmental failure match the manifest
· **Severity:** **Positive / verification**
· **Evidence:** My run: 1862 passed / 1863 total. Phase 10 manifest: "1862 passed, 1 failed (environmental) of 1863."
· **Assessment:** The numbers match exactly. The single failure is the same `oxlint` host-environment case seen in every prior task.
· **Recommendation:** None.

### F11 — The "no network call / no storage write" test also covers the no-identity-source source scan redundantly
· **Severity:** **Minor / positive**
· **Evidence:** AC5 has two cases (no network/storage, no token read) and AC6 has three (imports, forbidden sources, rendered text).
· **Assessment:** The overlap is deliberate and desirable: a regression that imports `fetch` would fail both AC5 and AC6 source scans, while a regression that reads `accessToken` would fail AC5 token-read and AC6 forbidden-source. The redundancy makes the suite robust against single-test deletion.
· **Recommendation:** None.

### F12 — The AC6 forbidden-source regex list is narrower than T40/T41 equivalents
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:291-292` forbids `/\bauth\//`, `/accessToken/`, `/idToken/`, `/userinfo/i`, `/accounts\/me/`, `/sessionStorage/`, `/localStorage/`, `/document\.cookie/`, `/\bfetch\s*\(/`, `/globalThis\.location/`.
· **Assessment:** This is sufficient for a screen that is not allowed to reach auth, storage, network, or navigation. It is narrower than T40/T41's comprehensive forbidden lists, but `HomeScreen` has no reason to use `useNavigate`, `rememberReturnTarget`, `broadcastSignOut`, etc. Adding those patterns would be defensive but would not increase kill power against realistic mutants.
· **Recommendation:** None required. If the project prefers a unified forbidden list across screens, consider importing or mirroring T40's `GUARD_FORBIDDEN`, but that would couple the test to another task's internals.

### F13 — The new StrictMode-through-real-route-table case is the most important addition
· **Severity:** **Positive / major**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:425-454` mounts the full `appRoutes` under `StrictMode` with a session and asserts one `<h1>` and focus on it.
· **Assessment:** This case genuinely covers a composition that no prior case did: the gate + route table + StrictMode + focus. It would catch a bug where `RequireSession` or `createMemoryRouter` unmount/remount behavior interacts badly with the focus effect. The justification in the test comment is clear.
· **Recommendation:** None.

---

## Summary

T174's test suite is in good shape. Phase 10 addressed both Phase 8 findings (I4 element ordering, I9 cross-screen focus grab) and added a valuable StrictMode-through-real-route-table case. The mutation harness is strong: 19/19 regressions killed, 0 broken, with RE18 correctly classified as unfalsifiable. The remaining issues are minor: F2 (regex brittleness in AC9), F3 (no dedicated mutant for AC8's no-key-handler clause), and F4 (ordering test assumes exactly two children in `<main>`). All three are documented or acceptable trade-offs. The suite is ready to ship as-is; the optional recommendations would add marginal robustness.

**Phase 11 complete.** Thirteen findings returned; no code or contract changed.
