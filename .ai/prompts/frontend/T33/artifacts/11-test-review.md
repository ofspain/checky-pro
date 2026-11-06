<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T33 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx` | **32/32 passed** |
| `python3 -I .ai/prompts/frontend/T33/artifacts/07-mutation-harness.py` | **17/17 regressions killed, 1 canary killed, 0 survivors, 0 broken** |
| `vitest run` | **1569/1570 passed**; 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |

No production or test code was changed during this review.

---

## Findings

### F1 — The tests prove the hook's behavior, but no test can prove it is wired in production
· **Severity:** **Blocker** (testability / scope)
· **Evidence:** The test file header at `frontend/src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx:30-35` states explicitly that `createSessionBoundary` has no production caller. The suite drives the real boundary in test code and asserts the handler outcome.
· **Assessment:** Every behavioral case is valid only for the test harness. In production the handler never runs because the boundary is never constructed. This is not a failing test — it is a limit on what tests can claim. The file is honest about it, but an acceptance suite that cannot be exercised against a running SPA cannot fully satisfy R22.
· **Recommendation:** Add an integration or contract test that asserts `mountShell.tsx` (or the future wiring module) constructs `createSessionBoundary` with `createRenewalFailureHandler`. Until the wiring exists, record this as an uncloseable gap in the phase 11 artifact.

### F2 — No test covers a throwing `rememberReturnTarget` or `setSignInNotice`
· **Severity:** **Major**
· **Evidence:** `cleanReauth.ts:67-76` clears the token, then calls `rememberReturnTarget`, then `setSignInNotice`, then `navigate`. The only throwing-step test is AC8's throwing `navigate`. `returnTarget.ts` and `signInNotice.ts` currently swallow their own errors, so the handler completes.
· **Assessment:** If either helper later throws — for example, because `sessionStorage` is full or a future edit removes the internal guard — the visitor ends up with a cleared token, no notice, and no navigation. That is the half-cleared state R22 is meant to prevent. The current tests would all pass because the helpers do not throw in the harness.
· **Recommendation:** Add two cases that stub `rememberReturnTarget` and `setSignInNotice` to throw, and assert that (a) the token is still cleared, and (b) the error still escapes as an uncaught exception in the boundary's microtask without reaching the waiting caller. This pins the failure mode.

### F3 — The AC6 surface sweep does not cover all cause words that could leak
· **Severity:** **Major**
· **Evidence:** `SURFACE_NEEDLES = ['held-token', 'renewal', 'Renewal', '401', 'sibling', 'Sibling']`. The forbidden-word test at `:460-479` covers `expire|invalid|revoke|...` but only for the catalogue string.
· **Assessment:** A regression that logs `"Your token expired"` to the console would not be caught by the surface sweep because `expired` is not in `SURFACE_NEEDLES`. The catalogue test would not catch it either because the sentence itself is unchanged. The sweep is therefore only as strong as its needle list.
· **Recommendation:** Derive `SURFACE_NEEDLES` from the same L4-based forbidden list used for the catalogue, so any cause word leaked to any surface fails. Add a control case that proves the sweep fires on each category of cause word.

### F4 — No test verifies the token is cleared before `rememberReturnTarget`
· **Severity:** **Major**
· **Evidence:** AC1's "clears FIRST" case (`:224-232`) only tests clear-before-`navigate` by supplying a throwing `navigate`. It does not test clear-before-`rememberReturnTarget`.
· **Assessment:** A mutant that reorders clear after `rememberReturnTarget` would pass every current test, because the harness's `rememberReturnTarget` never throws. Yet the module's comment says the order is load-bearing: "a notice set or a navigation started while a token is still held is exactly the half-authenticated tab R22 forbids."
· **Recommendation:** Add a case that stubs `rememberReturnTarget` to throw and asserts the token is already null when it throws. This is the same shape as the existing throwing-navigate case and closes the order gap.

### F5 — The source scan does not prevent bypassing the injected navigate via `location.assign`
· **Severity:** **Minor/Major**
· **Evidence:** `AC9` at `:557-566` forbids `react-router`, `useNavigate`, `createBrowserRouter`, and `globalThis.history`. It does not forbid `globalThis.location` or `location.assign`.
· **Assessment:** A future edit could bypass the injected `navigate` by assigning `globalThis.location.href` or calling `globalThis.location.assign()`. That would be a full-page navigation, which would discard the in-memory notice and break the design. The behavioral tests might still pass if the recorder also listened to location changes, but the source scan should pin the architectural decision.
· **Recommendation:** Add `globalThis\.location` and `location\.` to the forbidden patterns in the source scan, with a control that proves the pattern fires.

### F6 — The AC10 case does not prove the screen "builds an authorize request on the visitor's action"
· **Severity:** **Minor/Major**
· **Evidence:** `failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx:397-410` checks that the route's element is `SignInScreen`. The test comment at `:399-406` acknowledges this only fixes which screen is at the other end of the navigation.
· **Assessment:** AC10's clause says the destination screen "builds an authorize request on the visitor's action". The test proves the route points at `SignInScreen`, but it does not prove `SignInScreen` builds an authorize request, nor that it does so on user action. Those are T15's obligations. This is a scope boundary, but adversarially the test overclaims if it is read as proving the full clause.
· **Recommendation:** Either tighten the test name to say "the destination route carries `SignInScreen`", or add a shallow assertion that the rendered screen contains a button whose `onClick` calls `buildAuthorizeRequest` (which T15 already owns). Do not duplicate T15, but make the AC10 claim precise.

### F7 — `inAppPath()` has no direct unit test
· **Severity:** **Minor**
· **Evidence:** `cleanReauth.ts:36-41` contains logic for stripping a trailing slash from `APP_BASENAME` and converting a pathname to a router-relative path. It is exercised indirectly by the return-target cases (`/app/account` remembers `/account`; `/elsewhere` remembers nothing).
· **Assessment:** The indirect cases cover the happy paths but do not explicitly test the trailing-slash normalization (`/app/` → `/`) or the exact basename boundary. A regression that broke `/app/` handling would only surface when a route at the root is added.
· **Recommendation:** Add a focused case for `inAppPath` outcomes: `/app` → `/`, `/app/` → `/`, `/app/account` → `/account`, `/elsewhere` → `null`. If `inAppPath` is kept internal, test it through a public helper; if it is extracted, test the extracted function.

### F8 — NR18's anchor is tightly coupled to the JSX shape of `routes.tsx`
· **Severity:** **Minor**
· **Evidence:** The mutant at `07-mutation-harness.py:129-131` replaces `if (path === SIGN_IN_PATH) return { path, element: <SignInScreen /> };` with `{ path, element: <CallbackScreen /> };`.
· **Assessment:** The anchor works today because the route table is a single map expression. If `routes.tsx` is refactored to use a helper function, an object map, or moves the element assignment elsewhere, NR18 will break. That is acceptable for a mutation harness, but it makes the harness fragile to routine refactoring.
· **Recommendation:** Consider adding a static assertion test (not a mutant) that verifies the sign-in route's element type, so the AC10 clause has a non-fragile pin in addition to the mutant.

### F9 — No test checks that a pre-existing notice is overwritten, not merged
· **Severity:** **Minor**
· **Evidence:** `signInNotice.ts` stores a single value. `beforeEach` calls `takeSignInNotice()` to clear it. There is no case that sets a `discarded` notice first, runs the handler, and asserts the result is `session-ended`.
· **Assessment:** The current implementation overwrites, and the single-read contract makes merging impossible. But a future change to `setSignInNotice` could append or queue, and no test would fail.
· **Recommendation:** Add a case that calls `setSignInNotice('discarded')` before the handler and asserts `takeSignInNotice()` returns `session-ended`.

### F10 — The AC8 uncaught-exception test still manipulates global process state
· **Severity:** **Minor**
· **Evidence:** `failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx:335-356` removes all listeners, installs a recorder, and restores them in `finally`. Phase 10 moved the removal inside the `try` block (`:338-341`), reducing the window.
· **Assessment:** The window is smaller but not eliminated. If the test body throws between `removeAllListeners` and `on('uncaughtException', ...)`, the runner is left without handlers. The `finally` would then restore the saved listeners, but the original error is lost.
· **Recommendation:** Use `process.once('uncaughtException', ...)` without removing existing listeners, and assert that the expected exception is the one captured. If the runner also catches it, accept the dual capture rather than risk mutating global state.

### F11 — The boundary harness remains the third copy
· **Severity:** **Minor/Major**
· **Evidence:** The test file header at `:74-78` acknowledges this is the third copy of the fake transport / fake session shape.
· **Assessment:** This is the same duplication debt noted in Phase 8 I7. It does not affect correctness today, but it increases the cost of fixing a boundary behavior bug and the risk of the copies diverging.
· **Recommendation:** Extract a shared harness before a fourth copy appears. This is additive and does not edit closed tasks.

### F12 — The whole-suite baseline has the known environmental failure
· **Severity:** **Informational**
· **Evidence:** `vitest run` reports one failure in `src/api/lintRejectsHandWrittenBackendFetch.test.ts` due to the host's `oxlint@1.87.0` summary line format.
· **Assessment:** This is recorded as a known environmental failure in the mutation harness and is unrelated to T33. The T33 suite passes cleanly.
· **Recommendation:** None for T33.

---

## What the test suite gets right

- **S1 — The AC10 gap from Phase 10 is closed.** The new case ties the destination route to `SignInScreen`, and NR18 proves the case actually fires.
- **S2 — Phase 8 findings I2–I5 and I9 are addressed.** NR17 is now scored as a canary, the forbidden-word list is grounded in L4, the `catch` scan is narrowed, and router imports are forbidden by source scan.
- **S3 — The mutation score is independently reproducible:** 17/17 regressions killed, 0 broken.
- **S4 — Every AC from the frozen brief has at least one instrument**, and the clause-by-clause audit in Phase 10 found and fixed the only missing one.
- **S5 — The tests drive the real boundary**, not a stub, so they measure the integration point that future wiring will use.

---

## Summary

The T33 test suite is strong and the Phase 10 additions close the AC10 hole. The most important remaining gaps are **F1** (the behavior is not wired in production, so the tests prove a harness-only contract), **F2** (no coverage for throwing `rememberReturnTarget` / `setSignInNotice`), **F3** (surface sweep needles are narrower than the cause-word list), and **F4** (no test pins clear-before-remember order). The mutation harness is sound, but the third-copy harness debt and NR18's tight anchor remain.

**Phase 11 complete.**
