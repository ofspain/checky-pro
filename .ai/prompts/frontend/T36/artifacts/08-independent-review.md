<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T36 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Model** | Kimi 2.7 |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **76/76 passed** |
| `python3 -I .ai/prompts/frontend/T36/artifacts/07-mutation-harness.py` | **26/26 regressions killed, 0 survivors, 0 broken, 0 canaries** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1654/1655 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |

No production or test code was changed during this review.

---

## Findings

### I1 — `endSession()` is still uncalled in production
· **Severity:** **Blocker** (scope / process)
· **Evidence:** `frontend/src/auth/endSession.ts:31-33` states explicitly that nothing in the SPA calls `endSession`. This is OQ-T36-5, the seventh consecutive dormant task.
· **Assessment:** The implementation is correct and thoroughly tested, but it is dormant. The suite is honest about this limit, but the limit remains. AC10 prevents other modules from building the URL, but it cannot prove the function is wired.
· **Recommendation:** Record this as OQ-T36-5 and ensure the task that owns the sign-out affordance has a handoff note. Do not claim AC1–AC16 are satisfied in production until a caller exists.

### I2 — `CallbackScreen.tsx` was modified, but T19/T20's suite does not assert the new retention
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/CallbackScreen.tsx:9,176` now imports and calls `retainIdToken(response.idToken)` on the far side of the nonce check. `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:1019-1030` has a structural case for `retainAccessToken` after the nonce check, but none for `retainIdToken`.
· **Assessment:** T36's AC14 covers the `retainIdToken` order and exactly-one-place scan, but that lives in T36's suite. A future refactor of `CallbackScreen.tsx` that removes or reorders `retainIdToken` would be caught by T36, but the failure would read as a T36 regression rather than a callback-screen regression. The closed task that owns `CallbackScreen.tsx` should hold its own contract for the new behavior.
· **Recommendation:** Add a case to T19/T20's suite that asserts `retainIdToken(response.idToken)` appears after `idTokenNonceMatchesStoredValue(response.idToken)` and before navigation, mirroring the existing access-token order case. This keeps the contract with the module that owns the screen.

### I3 — AC10's literal scan can miss an import-based reference to the end-session path
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1427-1441` asserts that `END_SESSION_PATH` appears as a literal in exactly two production modules: `auth/endSession.ts` and `pwa/navigationDenylist.ts`.
· **Assessment:** A third module could import `END_SESSION_PATH` from `endSession.ts` and use the imported variable. The literal string would not appear in that module, so the scan would not flag it. This is the same limitation T35's AC10 has for direct vs. indirect primitive calls, but for a path reference it is more consequential because `END_SESSION_PATH` is deliberately exported.
· **Recommended brief amendment:** Add a second scan that asserts `endSession` is imported only by modules that are allowed to trigger sign-out (today, none). Or, do not export `END_SESSION_PATH`; keep it internal and compare the two spellings by literal in the test, which removes the import-based bypass.

### I4 — `idToken.ts` has no dedicated test file
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/idToken.ts` is a new production module with two exports (`retainIdToken`, `takeIdToken`). Its contract is exercised entirely through `signOutClearsMemoryAndEndsSasSession.test.tsx`.
· **Assessment:** The module is small and well-covered, but it is now a permanent part of the auth layer. A future task that moves or renames it has no dedicated suite to fail first. T36's suite tests the integration, not the module in isolation.
· **Recommendation:** Create `frontend/src/auth/idToken.test.ts` with focused unit cases: `retainIdToken` holds the value, `takeIdToken` returns and clears it, repeated `takeIdToken` returns null, and no storage surface is touched. This is additive and does not duplicate T36's integration cases.

### I5 — `postLogoutRedirectUri()` duplicates the de-slash logic from `authorizeRequest.ts`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/endSession.ts:74-77` mirrors `authorizeRequest.ts:59-62`.
· **Assessment:** The duplication is noted in the comment and is small, but it is still a maintenance hazard: a change to basename handling must be made in two places.
· **Recommendation:** Extract a shared `stripTrailingSlash` or `deriveQualifiedPath` utility. This is a minor refactor.

### I6 — The `SIGN_OUT_SET` strengthening edits T35's closed test logic
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:928-954` derives the set of sign-out primitives from `signOut.ts`'s source and asserts only `signOut.ts` may call the whole set. It is marked "TASK 36 STRENGTHENS THIS."
· **Assessment:** The strengthening is correct and closes a real gap, but it is a later task editing a closed task's instrument. The self-review carries this as an open question to phase 8. It is the right fix, but it should be explicitly accepted or rejected.
· **Recommendation:** Accept the strengthening, and document in the phase 8 artifact that the edit to T35's test is justified because the alternative — a hardcoded set — had become false and would have allowed a second sign-out to assemble itself one allowlist entry at a time.

### I7 — AC8's surface sweep does not cover all surfaces
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1361-1396` checks the ID token does not reach storage or console, and appears exactly once in the URL.
· **Assessment:** The test does not sweep `document.title`, `window.name`, `document.body.innerHTML`, `history.state`, or other surfaces that T35's AC6 sweeps. The source scan forbids `sessionStorage`, `localStorage`, `document.cookie`, `indexedDB`, and `console`, but it does not forbid `document.title` or `window.name`. A future edit could leak the token to one of these surfaces without failing a test.
· **Recommendation:** Extend AC8's sweep to the same seven surfaces T35's AC6 covers, or add a source scan forbidding `document.title`, `window.name`, and `history.state` in `idToken.ts` and `endSession.ts`.

### I8 — The absolute-host scan regex is broad but may miss unusual schemes
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1214-1224` uses `/https?:\/\/(?!localhost|127\.0\.0\.1)[\w.-]+\.\w/`.
· **Assessment:** The regex catches `http://` and `https://` with hostnames. It would miss a scheme-less `//example.com` or a non-http scheme like `ftp://`. Neither is realistic for this codebase, but the scan's claim is "spells no absolute host anywhere."
· **Recommendation:** Either add `//` and other schemes to the scan, or narrow the criterion to "spells no `http://` or `https://` host."

### I9 — No test verifies `endSession()` clears the ID token even if URL building throws
· **Severity:** **Minor**
· **Evidence:** AC1 has a case where `location.assign` throws, proving the clear happens first. There is no case where `endSessionUrl` or `URLSearchParams` throws.
· **Assessment:** `endSession()` takes the token, runs `signOut()`, then builds the URL. If URL building throws, the token is already cleared. This is correct by construction, but a test would pin it.
· **Recommendation:** Add a case that stubs `URLSearchParams` or `endSessionUrl` to throw and asserts the token is null after the throw. This is a low-priority addition.

### I10 — The `AC4` basename-move test cannot check clearing because of module-instance isolation
· **Severity:** **Minor**
· **Evidence:** The comment at `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1266-1267` explains that the dynamic import brings its own `idToken` instance, so no token is retained.
· **Assessment:** This is honest and correct. The case checks URL derivation only. A reader might expect it to also assert clearing, but the comment explains why it cannot.
· **Recommendation:** None required, but consider making the comment even more prominent or splitting the basename-move cases into a separate describe block labelled "URL derivation only."

### I11 — `AC13` pins non-idempotence by relying on a polling-loop-free second call
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1503-1523` calls `endSession()` twice and asserts two hand-overs.
· **Assessment:** This is the right instrument given the deliberate decision not to latch. The case is clear that the second navigation is accepted, not a defect.
· **Recommendation:** None.

### I12 — The whole-suite baseline grew as expected
· **Severity:** **Informational**
· **Evidence:** Self-review reports 1654 passed / 1 failed of 1655. T35 baseline was 1616/1617.
· **Assessment:** Growth is consistent with 38 new T36 cases and no regressions.
· **Recommendation:** None.

### I13 — The mutation harness correctly identifies and rejects bad mutants
· **Severity:** **Informational**
· **Evidence:** Self-review S1 describes ES16's dirty kill being caught by the harness's labels.
· **Assessment:** The harness machinery is working as intended. This is a strength, not a finding.
· **Recommendation:** None.

---

## What the implementation gets right

- **S1 — Phase 3 F1 and F2 were accepted.** The `id_token` lives in its own module with an atomic `takeIdToken()`, eliminating the read-before-clear hazard.
- **S2 — AC8's wording conflict is resolved.** The ID token is asserted to appear exactly once and only in the hint.
- **S3 — The four-mechanism hand-over counter is justified and working.** ES05 and ES06 are killed by it.
- **S4 — The `SIGN_OUT_SET` derivation closes T35's hardcoded-set gap.** A real regression, fixed with a control.
- **S5 — The T35 inventory and scans were correctly extended** to include the ID token and its module.
- **S6 — Every scan and sweep has a control**, and the mutation score is independently reproducible.
- **S7 — The `retainIdToken` placement after the nonce check is pinned structurally** in T36's AC14.

---

## Summary

T36 is a strong implementation that addresses the main Phase 3 concerns. The overriding issue is **I1** (`endSession()` is still uncalled in production). The most important new findings are **I2** (T19/T20's suite should assert the new `retainIdToken` behavior in the callback screen's own tests) and **I3** (AC10's literal scan can miss import-based references to `END_SESSION_PATH`). Smaller findings cover surface-sweep coverage (**I7**), duplication (**I5**), and the cross-task edit to T35's test (**I6**).

**Phase 8 complete.**
