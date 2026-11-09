<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T39 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, implementation |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/api/signOutPropagatesToAllTabs.test.ts` | **21/21 passed** |
| `vitest run src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **91/91 passed** |
| `python3 -I .ai/prompts/frontend/T39/artifacts/07-mutation-harness.py` | **28/30 regressions killed, 2 survivors (XT18, XT27), 0 broken, 0 canaries** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1719/1720 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |

No production or test code was changed during this review.

---

## Findings

### I1 — `signOut.ts`'s header still tells the reader the hazard cannot be closed
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/signOut.ts:42-50` states: *"**A HAZARD THIS FUNCTION CANNOT CLOSE (task 39 owns it, OQ-T35-3).** … It is not fixable from here: this function cannot reach the `SharedRenewal` closure, and the alternative - a latch that makes `adopt` refuse - means ADDING a piece of state in the task whose whole statement is removing it."* `signOut.ts:73` calls `markSignedOut()`.
· **Assessment:** This is the same finding as the self-review's F1, independently confirmed. The paragraph is now false in two ways: the latch exists and is set inside `signOut()`, and the `SharedRenewal` listener refuses adoption when the latch is set (`crossTabRenewal.ts:116`). A reader of `signOut.ts` is told the fix is impossible and that the mechanism used was rejected.
· **Recommendation:** Replace the paragraph with a statement that the hazard is closed by `crossTabSignOut.ts`'s latch and the guard in `crossTabRenewal.ts`, with a pointer to those modules.

### I2 — The latch leaks between cases in task 35's suite
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:29` imports `markSignedIn`, but the file's `afterEach` (`:369`) and `renewalRig.close()` (`:793-799`) never call it. The `beforeEach` calls `clearAccessToken()` but does not reset the latch.
· **Assessment:** Independently confirmed the self-review's F2. A probe appended to the end of the file — `expect(hasSignedOut()).toBe(false)` — fails, because AC9 cases leave the latch set and nothing resets it. This is currently harmless because every later case that needs adoption calls `retainAccessToken` (which resets the latch), but it is a latent test pollution hazard.
· **Recommendation:** Call `markSignedIn()` in the suite's `afterEach`, beside the existing teardown.

### I3 — AC11's negative case cannot distinguish "refused" from "never delivered"
· **Severity:** **Major**
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:260-274` posts twelve non-sign-out payloads and asserts `heard` is empty. Nothing in the case establishes that the channel was live.
· **Assessment:** Independently confirmed the self-review's F3. A deaf listener passes this case while failing the rest of the suite. The twelve-payload list is the only case that kills XT13, XT14, and XT15, yet it cannot validate itself.
· **Recommendation:** End the same case with a real sign-out on the same channel and assert it IS heard, so the refusals are measured against a delivery that worked. AC10's store-touch case wants the same treatment.

### I4 — The positional scan over `endSession.ts` reads unstripped comments
· **Severity:** **Minor/Major**
· **Evidence:** `signOutClearsMemoryAndEndsSasSession.test.tsx:2016-2024` (AC9d) reads `endSession.ts` unstripped and orders `signOut()`, `broadcastSignOut()` and `location.assign` by `indexOf`. `signOutPropagatesToAllTabs.test.tsx:132-138` greps `signOut.ts` unstripped for `/broadcastSignOut/`.
· **Assessment:** Independently confirmed the self-review's F4. A comment-only edit in `endSession.ts` that rewrites `` because `assign` may unload the page `` as `` because `location.assign` may unload the page `` would move `atAssign` and fail the order assertion, even though the production code is unchanged. The new suite has a comment-stripper and control; AC9d does not.
· **Recommendation:** Strip comments before the positional scan in AC9d and before AC2's grep in `signOut.ts`.

### I5 — `broadcastSignOut()` is documented fire-and-forget but `postMessage` can throw
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/api/crossTabSignOut.ts:95` — *"Fire-and-forget."* The body wraps `postMessage` in `try`/`finally` but not `try`/`catch`, so a throw propagates. `endSession.ts:121-122` calls `broadcastSignOut()` between `signOut()` and `globalThis.location.assign(...)`.
· **Assessment:** Independently confirmed the self-review's F5. If `postMessage` throws, this tab has cleared its token, siblings have not been told, and the SAS session is never ended because `assign` is not reached. The no-argument path cannot throw today (fresh channel, structured-cloneable payload), but the injected path could if the channel is closed.
· **Recommendation:** Make the contract true: wrap `postMessage` in `try`/`catch` and swallow the exception, so a notification failure cannot strand a sign-out. The harness mutant XT03 demonstrates the failure mode.

### I6 — The OQ-T39-7 residual is worse than its comment admits, and no test asserts it
· **Severity:** **Major**
· **Evidence:** `crossTabRenewal.ts:111-115` notes that `use()` below still returns a sibling's token to a local `renew()` caller in a signed-out tab, and claims *"Nothing holds it - it is never adopted."*
· **Assessment:** Independently confirmed and extended the self-review's F6. "Nothing holds it" is false if the caller then hands the token to `retainAccessToken`, which both resets the latch and stores the token. The residual is not "one token escapes" but "the guarded path can clear its own guard." No test asserts this explicitly. The AC9c case exercises the stale-success path but asserts the wrong outcome because it is pinned as a hazard.
· **Recommendation:** Update the comment to state the full residual. Add a test (or extend AC9c's documentation) that asserts `renew()` in a signed-out tab can return a token that, if adopted, would re-authenticate the tab.

### I7 — `quiet()`'s doc comment describes a different helper
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:43-46` says *"Waits for `received` to stop growing, with delivery observed rather than guessed at."* There is no `received`, and the body is a fixed 25 × 2 ms spin.
· **Assessment:** Independently confirmed the self-review's F7. The comment is stale and misleading. The fixed bound is a guess, not an observation.
· **Recommendation:** Either rewrite the comment to match the implementation or rewrite `quiet()` to observe delivery before settling. If the latter, use the technique from T35's `settle()`.

### I8 — No test asserts the sign-out channel name
· **Severity:** **Minor/Major**
· **Evidence:** `crossTabSignOut.ts:37` exports `SIGN_OUT_CHANNEL = 'themistra.session.signout'`. `signOutPropagatesToAllTabs.test.ts` imports it and uses it in `uniqueName()`, but no case asserts the constant's value.
· **Assessment:** A regression that changed the channel name would break inter-tab communication silently. The constant is used in tests only to derive unique names, not to assert the production channel name. The parser and broadcaster must agree on the name for the feature to work.
· **Recommendation:** Add an assertion that `SIGN_OUT_CHANNEL` equals `'themistra.session.signout'` and that `SIGN_OUT_TYPE` equals `'session:signed-out'`. This is a one-line addition to AC4.

### I9 — No source scan asserts `accessToken.ts` imports and calls `markSignedIn`
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/accessToken.ts:83-91` calls `markSignedIn()` inside `retainAccessToken()`. T35's suite scans `signOut.ts` for unauthorized additions but does not scan `accessToken.ts` for the new runtime dependency on `api/crossTabSignOut.ts`.
· **Assessment:** The behavioral test in T35's AC9 reset case will catch a missing reset indirectly, but there is no source-level assertion that the reset happens at the single assignment point. A future refactor that moved the reset to `CallbackScreen.tsx` would still pass the behavioral test if all current callers were updated, but would violate the design rationale (new sign-in paths could skip it).
· **Recommendation:** Add a source scan in `accessToken.ts` that asserts `markSignedIn()` is called inside `retainAccessToken()` and that no other module calls it.

### I10 — The `SIGN_OUT_TYPE` string workaround is not directly tested
· **Severity:** **Minor**
· **Evidence:** `crossTabSignOut.ts:39-49` explains that `SIGN_OUT_TYPE` is a string rather than an object constant because T35's module-scope mutable scan would count a `const` object literal as a second declaration.
· **Assessment:** The workaround is clever and justified, but if a future edit changed `SIGN_OUT_TYPE` to an object literal, T35's scan would catch it only if that scan is still run. No test in T39's suite asserts the shape of the constant.
· **Recommendation:** Add a source scan that asserts `SIGN_OUT_TYPE` is declared as a string literal and not as an object, array, or other mutable-looking structure. This documents the constraint.

### I11 — The `endSession.ts` header about module-level mutable state is now slightly misleading
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/endSession.ts:103-106` — *"A latch would be module-level mutable state - the one thing this module must not hold."*
· **Assessment:** `endSession.ts` itself does not hold the latch, but it now depends on `broadcastSignOut()` from `crossTabSignOut.ts`, which does. The comment is technically true for `endSession.ts` but could mislead a reader into thinking no latch exists anywhere in the sign-out flow.
· **Recommendation:** Add a phrase noting that the latch lives in `crossTabSignOut.ts` and is set by `signOut()`, not by `endSession.ts`.

### I12 — `listenForSignOut` does not defend against a throwing callback
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/crossTabSignOut.ts:135-137` calls `onSignOut()` directly inside `onMessage`. If `onSignOut` throws, the exception propagates out of the listener.
· **Assessment:** In production, `onSignOut` would be `signOut()`, which should never throw (each step is guarded). If it did throw, the listener would crash and future sign-out broadcasts would be ignored for that tab. This is consistent with `crossTabRenewal.ts`'s behavior (it rethrows via `queueMicrotask`), but it is not documented.
· **Recommendation:** Document the behavior: the callback's exceptions are the caller's responsibility. Optionally wrap the call and rethrow via `queueMicrotask` to avoid breaking the channel.

### I13 — The `AC6` degraded-path test does not assert `listenForSignOut` returns a no-op close that can be called multiple times safely
· **Severity:** **Minor**
· **Evidence:** `signOutPropagatesToAllTabs.test.ts:210-226` asserts `listener.close()` does not throw twice.
· **Assessment:** The test does assert this. No gap.
· **Recommendation:** None.

### I14 — The harness survivors XT18 and XT27 are real, unfalsifiable claims
· **Severity:** **Minor**
· **Evidence:** XT18 removes `close()`'s idempotency guard and survives because double `removeEventListener` and double `BroadcastChannel.close()` are no-ops by spec. XT27 moves the renewal guard above the count/wake and survives because the only suite that counts outcomes (`siblingTabsShareOneRenewal`) never signs out.
· **Assessment:** Independently confirmed the self-review's F12 and F13. These are not defects but measurement gaps. The code is right; the tests cannot prove it.
· **Recommendation:** For XT27, add a case in T35's suite that signs out, posts `renewal:succeeded`, and asserts `outcomeCount` still increments and a pending waiter settles while the token stays null. For XT18, either accept the unfalsifiability or give `close()` an observable side effect.

### I15 — The `renewalRig` helper does not reset the latch, and its `close()` does not call `markSignedIn()`
· **Severity:** **Minor**
· **Evidence:** `signOutClearsMemoryAndEndsSasSession.test.tsx:793-799`.
· **Assessment:** Same as I2, scoped to the helper. Combined with the suite-level `afterEach` leak, it means the latch state is managed by luck.
· **Recommendation:** Add `markSignedIn()` to `renewalRig.close()` and to the suite `afterEach`.

### I16 — No production caller installs the listener
· **Severity:** **Blocker** (process)
· **Evidence:** `signOutPropagatesToAllTabs.test.ts:9-12`, `endSession.ts` has no caller.
· **Assessment:** Independently confirmed the self-review's F11. The mechanism is built and correct, but the full cross-tab journey is unreachable.
· **Recommendation:** None for T39.

---

## Summary

T39's implementation is sound and the self-review is thorough. The independent review confirms all major self-review findings and adds a few new angles. The most important remaining gaps are **I1** (the false comment in `signOut.ts`), **I2** (the latch leak in T35's suite), **I3** (AC11's negative case cannot validate itself), **I5** (`broadcastSignOut` can throw despite being documented fire-and-forget), **I6** (the OQ-T39-7 residual is worse than its comment claims), and **I9** (no source scan asserts the latch reset lives in `accessToken.ts`). The two harness survivors (XT18, XT27) are correctly identified as unfalsifiable claims rather than defects.

The suite and gates are independently reproducible: 28/30 mutants killed, all tests green, and the one whole-suite failure is the known environmental `oxlint` host case.

**Phase 8 complete.** Sixteen findings returned; no code or contract changed.
