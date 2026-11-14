<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T43 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T43 — Registration accepts 12 characters |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md`, `artifacts/10-mutation-run.txt` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | **39 / 39 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1937 / 1938 passed** — 1 known environmental `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T43/artifacts/07-mutation-harness.py` | **26 / 26 regressions killed**, 1 canary killed, **1 survivor** (TR24, expected/unfalsifiable), **0 broken**, **0 disagreements** |

The named suite grew from 34 to 39 cases and the whole suite from 1931 to 1938, matching the Phase 10 manifest. The harness now kills every regression it was expected to kill.

---

## Findings

### F1 — TR24 (`Form` drops `noValidate`) is the only survivor, and it is expected but not testable
· **Severity:** **Minor**
· **Evidence:** Harness reports `SURVIVED` for TR24; the manifest classifies it as *unfalsifiable* because jsdom does not implement native form validation.
· **Assessment:** This is correctly classified. The `Form` primitive sets `noValidate` to prevent browser constraint-validation bubbles, but there is no runtime observable in jsdom that distinguishes a form with `noValidate` from one without. The case that would catch it requires a real browser. This is not a defect in the test suite; it is a boundary of the environment.
· **Recommendation:** Verify that `Form.tsx`'s comment about jsdom logging "Not implemented" is accurate, or rephrase it to say the attribute is untestable in jsdom. If the project later adds Playwright/Cypress coverage, TR24 should be the first mutant re-run there.

### F2 — Double submit / request racing is still unmeasured
· **Severity:** **Minor/Major**
· **Evidence:** No case clicks `SubmitButton` twice in quick succession while a request is held in flight. The rejection case retries after failure, but that is a serial retry, not a race.
· **Assessment:** Phase 10 explicitly left this as the owner's decision (OQ-T43-11). The screen currently creates no guard: `SubmitButton` has no `aria-busy`, no `disabled`, and `submit()` does not short-circuit a second call while `status === 'submitting'`. A user with a slow connection or a screen-reader user activating the control twice could issue two registration requests. This is a real product gap, even if the task brief does not require solving it.
· **Recommendation:** Decide in task closure whether T43 intentionally leaves double-submit to task 45, or add an `aria-busy`/`disabled` guard and a case that fires two clicks during a held request and asserts only one transport call.

### F3 — The rejection case does not assert absence of an unhandled promise rejection
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:721-750` asserts UI state after a rejected fetch, but installs no `unhandledrejection` listener.
· **Assessment:** The case is strong enough to catch the original bug (live region stuck on "Creating your account…"), so it is not vacuous. However, it does not distinguish "rejection handled and UI reset" from "rejection handled but also logged as unhandled". Vitest may or may not surface such a rejection as a failure depending on timing.
· **Recommendation:** Optional: add a listener inside the case (or a wrapper in `test/setup.ts`) that fails the test if any unhandled rejection occurs. This is a low-cost hygiene improvement.

### F4 — `createPublicAuthApi()` is still instantiated on every submit
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:62` calls `createPublicAuthApi()` inside `submit()`.
· **Assessment:** Phase 8 I4 flagged this; Phase 10 did not address it. It is functionally harmless for a one-shot mutation, but it is inconsistent with the rest of the codebase where API clients are created once per tab. It also means any future middleware passed to the factory would be re-registered each time.
· **Recommendation:** Move the client to module scope. Not a test issue; a small production cleanup.

### F5 — The real route-table case does not prove the layout/AppShell is rendered
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:495-523` mounts `appRoutes` and asserts the form and heading render, but does not assert the `AuthenticatedLayout`/banner/chrome is absent or present.
· **Assessment:** The case's stated claim is that the path is outside the session gate, which it proves by checking the form renders. A regression that accidentally wrapped the registration route inside `RequireSession` would be caught by this case failing. A regression that removed the layout from public routes would be caught by T41/T42 suite cases, not this one.
· **Recommendation:** Optional: assert that the banner/chrome is NOT rendered (public routes have no shell) or that the route resolves outside `RequireSession`. This would make the case a stronger composition test.

### F6 — No case asserts the label/input DOM order inside the field primitives
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:408-412` asserts `EmailField` and `PasswordField` render `<input>` and `<label>`, but not that the label precedes the input.
· **Assessment:** For most screen readers and browser/AT combinations, the association via `htmlFor`/`id` is sufficient regardless of DOM order, but some setups announce more reliably when the label precedes the control. The brief does not require this, and the primitives render label-first today.
· **Recommendation:** Optional: add a DOM-order assertion to the element-count exception test, or decide label order is a primitive implementation detail not pinned by the suite.

### F7 — The empty-string row still fills the email field
· **Severity:** **Minor / observation**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:246` adds `{ name: 'the empty string, which must not crash the count', password: '', codePoints: 0, inRange: false }` while the helper fills `email` with `visitor@example.test`.
· **Assessment:** The row correctly tests D16's clause for the password count. Both fields empty is a plausible user accident, but it is covered implicitly: an empty email would still submit through the generated client, and the server is the authority.
· **Recommendation:** Optional: add a one-off case that submits with both fields empty and asserts the request still goes. This is a small addition with high user-surface relevance.

### F8 — `FU-T43-1` (inert storage spy) is closed
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:594-633` now spies on `Storage.prototype.setItem`, asserts no writes, then writes control keys to both storages and asserts the spy attributed them correctly.
· **Assessment:** This is a well-constructed control. The negative assertion is now backed by positive evidence that the spy is alive and can distinguish the two storage objects.
· **Recommendation:** None.

### F9 — `FU-T43-2` (substring trap in catalogue scan) is closed
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:412-416` uses word-boundary regexes to detect spelled sentences, avoiding the false negative where "Password" is a substring of `PasswordField`.
· **Assessment:** The fix is correct and the comment documents the trap. The source-scan case now genuinely distinguishes lookup from literal.
· **Recommendation:** None.

### F10 — Phase 8 I1 (focus guard missing RegisterScreen) is closed
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:445` adds `RegisterScreen` to the four-screen loop that asserts focus stays on `<body>` on mount. Harness TR21, which previously survived, is now killed.
· **Assessment:** Strong closure of an independent finding. The comment explains why the guard matters more for a screen with interactive controls.
· **Recommendation:** None.

### F11 — Phase 8 I2 (no real route-table arrival) is closed
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:495-523` mounts the real `appRoutes` and arrives at `/app/register` with no session, asserting the form renders and no redirect occurs.
· **Assessment:** This brings T43 in line with T41/T42's composition coverage.
· **Recommendation:** None.

### F12 — Phase 8 I7/F2 (missing rejection handler) is closed
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:62-66` now passes a rejection callback to `.then(...)`, and `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:721-750` holds the fix. Harness TR26 kills the regression.
· **Assessment:** The case is honest about the limitation: it clears the status region and leaves the form usable for retry, claiming no outcome.
· **Recommendation:** None.

### F13 — Phase 8 I8/F3 (false pair-pin claim) is closed
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:587-600` now asserts `REQUIRED_UTILITIES.SubmitButton` equals `REQUIRED_UTILITIES.ActionButton` and compares the literal class strings in both files. Harness TR27 and TR28 specifically attack this pin.
· **Assessment:** The pair pin is now a real contract, not two rows that happen to match.
· **Recommendation:** None.

### F14 — The harness outcome is clean
· **Severity:** **Positive**
· **Evidence:** 26/26 regressions killed, 0 broken, 0 disagreements. The only survivor is TR24, which was classified unfalsifiable and remains so.
· **Assessment:** Phase 10 added exactly the cases needed to kill the mutants that Phase 8 identified as holes: TR09 (hardcoded copy), TR21 (focus on mount), and TR26 (rejection handler). The two new pair-pin mutants (TR27, TR28) are also killed.
· **Recommendation:** None.

---

## Summary

Phase 10 closes every major hole identified in Phases 7 and 8: the inert storage spy (**F8**), the catalogue substring trap (**F9**), the missing focus guard (**F10**), the missing real-route arrival (**F11**), the missing rejection handler (**F12**), and the false pair-pin claim (**F13**). The harness is now clean: **26/26 killed, 0 broken, 0 disagreements** (**F14**).

Remaining adversarial concerns are small and mostly deliberate boundaries:
- **F1** — TR24 (`noValidate`) remains a known jsdom limitation.
- **F2** — double submit / request racing is intentionally left as OQ-T43-11.
- **F3** — the rejection case could assert absence of unhandled rejections.
- **F4** — `createPublicAuthApi()` is still created per submit (cleanup, not correctness).
- **F5** — the real route-table case could assert shell absence/presence.
- **F6** — label/input DOM order inside field primitives is not pinned.
- **F7** — both-fields-empty submit is not explicitly covered.

None of these block task closure, but F2 and F4 are the two worth resolving before signing off if the owner wants T43 to be complete rather than deliberately partial.

**Phase 11 complete.** Seven findings returned; no code changed.
