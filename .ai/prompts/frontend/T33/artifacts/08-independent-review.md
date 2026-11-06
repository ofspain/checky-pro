<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T33 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx` | **29/29 passed** |
| `python3 -I .ai/prompts/frontend/T33/artifacts/07-mutation-harness.py` | **17/17 killed, 0 broken, 0 survivors** |
| `oxlint src` | **exit 0, no output** |
| `tsc --noEmit` | **exit 0** |

No production or test code was changed during this review.

---

## Findings

### I1 — The delivered routing has no production caller and is therefore unreachable
· **Severity:** **Blocker** (scope / process)
· **Evidence:** `frontend/src/auth/cleanReauth.ts:13-19` states explicitly that nothing in production constructs `createSessionBoundary`. The module exports `createRenewalFailureHandler`, but no production file imports or calls it. The self-review's S4 calls this "the weakest thing about T33 and no test can fix it."
· **Assessment:** The task is scoped in `tasks.md:61` as "a failed renewal routes to re-auth with no half-session". The implementation delivers the routing function, but because `createSessionBoundary` is never wired into the SPA, the routing never executes in a running application. This is not a forward guard (like T32's static scans) — it is a runtime behavior whose trigger does not exist. The brief froze resolution (c) after establishing that no task owns the wiring, but a deliverable that cannot run is a partial completion of the mandate.
· **Recommendation:** Either (a) identify and assign the wiring task before calling T33 complete, (b) have T33 include the wiring itself despite the scope interpretation, or (c) record in the artifact that T33's acceptance criteria are satisfied only at the module level and that AC2/AC3/AC7/AC8 cannot be exercised in production until the wiring task exists. The current state is honest about the gap but leaves the task functionally dormant.

### I2 — NR17 is a mutant designed to fail in the future, not to score current code
· **Severity:** **Minor/Major**
· **Evidence:** The mutation harness includes NR17, which edits `routes.tsx` to attach an element to an authenticated route. The self-review's S5 and the harness comments say it is "the day the repository stops making T33's claim true for free".
· **Assessment:** NR17 is a canary, not a regression. It will pass today and fail the moment an authenticated route gains an element. That is valuable information, but a mutation harness is conventionally a score of current mutants, and a row that is expected to change state can confuse a reader. The harness correctly reports it as killed today, so the score is not inflated.
· **Recommendation:** Keep NR17, but move it out of the "mutants killed" summary and report it separately as an "environmental canary" or "future guard" so the mutation score remains a pure measure of current regressions. Alternatively, convert it to a regular test case (it already is one in AC11) and remove it from the harness.

### I3 — The AC8 test manipulates `process` uncaught-exception listeners and can leave the runner unprotected
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx:335-352` removes all `uncaughtException` listeners, installs a recorder, runs the test, then restores the saved listeners in a `finally` block.
· **Assessment:** The test is correct in intent: it proves the boundary's microtask containment by catching the deliberate throw. But if an unexpected error occurs between `process.removeAllListeners('uncaughtException')` and the `finally` restoration, the runner's handlers are gone and the error may crash the process or be swallowed. The self-review's S4 flags this exact risk.
· **Recommendation:** Wrap the body more defensively: catch any error in the try block, restore listeners, then re-throw if it was not the expected one. Or use `process.once('uncaughtException', ...)` around the single `settle()` call instead of replacing all listeners. The current `finally` is good but not enough if the throw happens during listener restoration itself.

### I4 — Source scans in `cleanReauth.ts` forbid `catch`, preventing safe future error handling
· **Severity:** **Minor/Major**
· **Evidence:** `:500-504` forbids `/\breason\b/`, `/\bcatch\b/`, and `/instanceof/` in `cleanReauth.ts`.
· **Assessment:** Forbidding `catch` is overly broad. `rememberReturnTarget` and `setSignInNotice` currently swallow their own errors, so `cleanReauth.ts` needs no `catch`. But a future legitimate change — for example, guarding `navigate` or making the handler robust to a throwing `setSignInNotice` — would require a `try/catch` and would fail this test. The scan catches the right risk (reason-dependent branching) with a pattern that also blocks unrelated safe patterns.
· **Recommendation:** Replace the broad `/\bcatch\b/` ban with a targeted scan: forbid reading a `reason` parameter or branching on `instanceof`, but allow `catch` blocks that do not inspect the caught value. Document that any future `catch` must discard the error.

### I5 — The forbidden-word list for `sessionEnded` copy is author-defined
· **Severity:** **Minor**
· **Evidence:** `:442` defines `forbidden = /expire|invalid|revoke|renew|refresh|token|lock|suspend|delet|unknown|password|mfa|totp|server|network|401/i` and asserts `en.signIn.sessionEnded` does not match it.
· **Assessment:** The list is reasonable and the control at `:445` proves it fires, but it is the author's judgement, not derived from L4's enumerated categories (account states and token conditions). A word such as `session` is conspicuously absent; "Your session has ended" is allowed because `session` is not in the list. That is the intended result, but the list's incompleteness is a latent risk.
· **Recommendation:** Ground the list in L4's text or add a comment citing which categories each group of words covers. Consider adding `session` and proving the test then fails, so future maintainers see the boundary.

### I6 — `inAppPath()` duplicates basename-stripping logic from `authorizeRequest.ts`
· **Severity:** **Minor**
· **Evidence:** `cleanReauth.ts:36-41` mirrors `authorizeRequest.ts:58-61` (trailing-slash strip, basename prefix removal).
· **Assessment:** The duplication is noted and intentional, but it is still a maintenance hazard: a change to basename handling must be made in two places. The function is small, so the cost is low.
· **Recommendation:** Extract a shared `stripBasename(pathname, basename)` utility to `src/app/routes.ts` or a helper module, used by both `authorizeRequest.ts` and `cleanReauth.ts`. This is a small refactor that pays down the duplication.

### I7 — The boundary harness is the third copy of the same shape
· **Severity:** **Minor/Major**
· **Evidence:** The test file header at `:74-78` says this is the third copy of the fake transport and fake session from tasks 5 and 6.
· **Assessment:** As with T32's scan helpers, duplication means a bug fix or behavior change in one copy does not propagate. The justification — that extracting it would edit two closed tasks — is the same one recorded as OQ-T32-4.
· **Recommendation:** Create a shared boundary-test harness module (`src/testing/boundaryHarness.ts`) before a fourth copy appears. This is less risky than editing the closed tasks' tests, because the new module is additive.

### I8 — The cross-task key-set pin edits are correct but multiply the same enumeration
· **Severity:** **Minor**
· **Evidence:** `sessionEnded` was added to the `SIGN_IN_KEYS` pin in `identityErrorsIndistinguishableAcrossAccountStates.test.tsx:377-379`, `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx:260-262`, and `callbackValidatesNonce.test.tsx:822-824`.
· **Assessment:** Each edit includes a justification comment, and none weakens the underlying scans. But the same enumeration now exists in three places, which is exactly the helper-duplication debt the package already carries.
· **Recommendation:** No action needed for T33, but this is further evidence that a shared catalogue-key pin should be extracted (see I7 and OQ-T32-4).

### I9 — No source scan asserts `cleanReauth.ts` does not import the router directly
· **Severity:** **Minor**
· **Evidence:** The module takes `navigate` as an injected parameter to avoid importing the router (`cleanReauth.ts:52-54`). The existing scans check route-name reads are inside functions and forbid `sessionStorage`/`localStorage`.
· **Assessment:** A future edit could add `import { useNavigate } from 'react-router'` or `import { useRouter } from ...` and the existing tests would still pass if the function worked. The injected `navigate` is the architectural protection; a source scan would make it visible.
· **Recommendation:** Add a source scan that asserts `cleanReauth.ts` contains no import from `react-router` or any router module. This pins the dependency-inversion decision.

### I10 — The `AC6` sweep removes the return target before checking for leaks
· **Severity:** **Minor**
· **Evidence:** `:567` calls `takeReturnTarget()` before `leaks(SURFACE_NEEDLES, logged())`.
· **Assessment:** Removing the target before sweeping means the test cannot detect if the return target value itself contained failure information. The preceding case (`:534-540`) asserts the stored value is `/account`, which is clean, and the key-set assertion proves only one write occurred. The combination is sufficient.
· **Recommendation:** None. The two cases together cover the concern. Consider adding a comment in the sweep case explaining why `takeReturnTarget` is called first.

### I11 — The `AC5` sentence test does not verify behavior under a live region
· **Severity:** **Minor**
· **Evidence:** The case at `:448-455` renders the sign-in screen and checks `textContent` contains `sessionEnded` and not `discarded`.
· **Assessment:** The test proves the right sentence appears in the DOM. It does not prove the screen reader announces it correctly (the region has `role="status"` and `aria-live="polite"`). That is a rendering/accessibility concern, not unique to this task.
· **Recommendation:** None for T33. The existing screen tests (T15/T18) own the live-region behavior; this case only needs to pin the correct key.

### I12 — `createRenewalFailureHandler` returns a zero-length function, but the type allows a no-arg function
· **Severity:** **Informational**
· **Evidence:** `:479` asserts the returned function has length 0. The TypeScript signature is `() => void`.
· **Assessment:** This is a strong guarantee against reading the failure reason. It is enforced at the type level and verified at runtime. Good.
· **Recommendation:** None.

### I13 — The destination test at `:386-392` asserts `signIn?.element` is defined
· **Severity:** **Informational**
· **Evidence:** The test checks that the sign-in route has an element, making it the start of the flow.
· **Assessment:** This addresses the Phase 3 concern that landing on `/app/sign-in` is a transition to a screen that offers re-auth. The test proves the screen can start the flow. Combined with the routing using `SIGN_IN_PATH`, this is a reasonable interpretation of "transition to clean re-auth".
· **Recommendation:** None. The Phase 3 concern is resolved by this case.

---

## What the implementation gets right

- **S1's gap is closed properly.** The sign-in screen now chooses the sentence by notice, and two cases prove both paths render the right sentence.
- **The token is cleared first**, and NR03 proves the order matters.
- **The failure reason is unreachable by construction**: zero-parameter handler, verified by `fn.length` and source scans.
- **AC8 is tested from the routing side**, proving the boundary's microtask containment.
- **AC11 pins the environmental assumption** that authenticated routes have no elements, with a clear message naming T41.
- **AC6 allows only the return-target write** and sweeps all other surfaces and console channels.
- **The mutation harness is independently reproducible**: 17/17 killed, 0 broken, with the baseline pinned by name.
- **The cross-task edits include justifications** and do not weaken the existing scans.

---

## Summary

T33's implementation is careful and the tests are strong. The single overriding issue is **I1**: the routing module has no production caller, so every behavioral case is a statement about how the hook *will* behave when wired, not how it *does* behave today. The self-review is honest about this, but honesty does not make the task complete. The other findings are smaller: I3 is a runner-risk, I4 is a future-maintenance risk, and I6/I7 are duplication debts. The mutation score and gates are independently reproducible.

**Phase 8 complete.**
