<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T36 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **81/81 passed** |
| `python3 -I .ai/prompts/frontend/T36/artifacts/07-mutation-harness.py` | **30/30 regressions killed, 0 survivors, 0 broken, 0 canaries** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1659/1660 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |

No production or test code was changed during this review.

---

## Findings

### F1 — No test can prove `endSession()` is wired in production
· **Severity:** **Blocker** (testability / process)
· **Evidence:** `frontend/src/auth/endSession.ts:31-33` and the test file header at `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1112-1113` state explicitly that nothing in the SPA calls `endSession`. AC10's import scan asserts `PERMITTED_IMPORTERS` is empty.
· **Assessment:** Every behavioral case is a statement about how the function behaves when invoked. The suite is honest about this limit, and the empty import list is a good handoff instrument, but it does not change the fact that the function never runs in a production browser.
· **Recommendation:** When the sign-out affordance is added, update `PERMITTED_IMPORTERS` and add an integration test that exercises the real wiring. Until then, record in the phase 11 artifact that AC1–AC16 are verified only by direct invocation.

### F2 — AC4's percent-encoding test only checks `post_logout_redirect_uri`, not `id_token_hint`
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1256-1273` asserts the raw query contains the encoded post-logout target and does not contain `=https://`. It does not assert anything about how `id_token_hint` is encoded.
· **Assessment:** A mutant that concatenates the query by hand might also fail to encode `id_token_hint` if the token contained `&`, `=`, or `#`. Real ID tokens are JWTs with base64url characters that usually do not need encoding, but "usually" is not "guaranteed." ES30 only kills the post-logout encoding bug.
· **Recommendation:** Extend the raw-query case to assert that `id_token_hint`'s value is also percent-encoded when it contains characters that require it. Add a control using an ID token containing `&` or `=` and assert the raw query does not contain them unencoded.

### F3 — AC8's surface sweep does not cover `sendBeacon`, `Image`/`Audio` tags, or `performance.mark`
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1424-1438` forbids `sessionStorage`, `localStorage`, `document.cookie`, `indexedDB`, `console.`, `document.title`, `window.name`, and `history.state`. It does not forbid `sendBeacon`, `new Image()`, `new Audio()`, or `performance.mark`.
· **Assessment:** These are all ways a token could leave the tab. AC11 forbids `fetch` and `XMLHttpRequest`, but a module could leak the ID token via `navigator.sendBeacon('/log', hint)` or `new Image().src = '/log?' + hint` without failing any test. The source scan is the only guard.
· **Recommendation:** Add `sendBeacon`, `new Image`, `new Audio`, and `performance.mark` to the AC8 source-scan forbidden list, with a control proving the pattern fires.

### F4 — `idToken.ts` still has no dedicated test file
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/idToken.ts` is a production module, but its contract is tested only through integration cases in `signOutClearsMemoryAndEndsSasSession.test.tsx`.
· **Assessment:** A future refactor that moves or renames `idToken.ts` has no dedicated suite to fail first. The integration coverage is good, but the module deserves its own unit tests.
· **Recommendation:** Create `frontend/src/auth/idToken.test.ts` with focused cases: `retainIdToken` holds the value, `takeIdToken` returns and clears it, repeated `takeIdToken` returns null, and the module touches no storage surface.

### F5 — T19/T20's suite still does not assert `retainIdToken` placement
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/CallbackScreen.tsx:9,176` now calls `retainIdToken(response.idToken)`. `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:1019-1030` checks the access token retention order but not the ID token.
· **Assessment:** T36's AC14 covers this, but it lives in T36's suite. A regression in `CallbackScreen.tsx` would surface as a T36 failure rather than a callback-screen failure, sending the wrong person to debug it.
· **Recommendation:** Add a structural case to T19/T20's suite mirroring the access-token order case, but for `retainIdToken(response.idToken)` after `idTokenNonceMatchesStoredValue(response.idToken)`.

### F6 — AC10's import scan misses dynamic imports and `require()`
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1498-1505` uses `/from '\.{1,2}(?:\/auth)?\/endSession'/`.
· **Assessment:** The scan catches static ES-module imports with two relative shapes. It does not catch `import('./endSession')`, `import('../auth/endSession')`, or `require('./endSession')`. A future module could dynamically import `endSession` and call it without being flagged.
· **Recommendation:** Extend the scan to include `import(` and `require(` references to `./endSession` and `../auth/endSession`, or add a source scan that forbids dynamic imports of the end-session module. Add controls for each shape.

### F7 — No test verifies `endSessionUrl` does not add a fragment
· **Severity:** **Minor**
· **Evidence:** AC3 checks `url.pathname` and AC4 checks `post_logout_redirect_uri`. No case checks `url.hash`.
· **Assessment:** A fragment in the end-session URL would be discarded by the browser before reaching the server, but it could still leak the ID token into browser history if appended to the hint. The current implementation does not do this.
· **Recommendation:** Add an assertion that the hand-over URL has no hash. This is a one-line addition to the AC3 case.

### F8 — No test verifies parameter order in the URL
· **Severity:** **Minor**
· **Evidence:** AC6 asserts the set of keys. The implementation inserts `id_token_hint` first (when present), then `post_logout_redirect_uri`.
· **Assessment:** Parameter order does not affect SAS parsing, but a regression that reorders them would not be caught.
· **Recommendation:** Not critical. If desired, assert `raw.indexOf('id_token_hint') < raw.indexOf('post_logout_redirect_uri')` when both are present.

### F9 — The `AC1` source-order case does not assert both calls are in the same function
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1149-1161` finds `takeIdToken()` and `signOut()` by index in the file and asserts the first precedes the second.
· **Assessment:** The scan is correct because `endSession.ts` is small, but it would pass if `takeIdToken()` appeared in a helper function above `signOut()` in the same file. A stronger assertion would parse the function body of `endSession()`.
· **Recommendation:** Extract the body of `export function endSession` and assert the order within that body. This is a small precision improvement.

### F10 — No test verifies the `AC14` `retainIdToken` call uses `response.idToken` specifically
· **Severity:** **Minor**
· **Evidence:** AC14 checks `retainIdToken` appears after the nonce check and that only `CallbackScreen.tsx` calls it. ES16 covers access-token-as-hint.
· **Assessment:** The combination of scans and mutants makes this strong, but there is no direct assertion that the argument is `response.idToken`. A source scan for `retainIdToken(response.idToken)` would pin it.
· **Recommendation:** Add a source scan that asserts `CallbackScreen.tsx` contains `retainIdToken(response.idToken)` exactly once.

### F11 — The `AC8` sweep does not assert the ID token is absent from `document.body.innerHTML`
· **Severity:** **Minor**
· **Evidence:** The source scan forbids `document.title`, `window.name`, and `history.state` but not `document.body` or `innerHTML`.
· **Assessment:** `endSession.ts` and `idToken.ts` do not render anything, so this is not an immediate risk. But a future edit could write the token to the DOM before navigating.
· **Recommendation:** Add `innerHTML` and `document.write` to the AC8 forbidden list, or rely on the fact that the modules contain no rendering code. The former is more robust.

### F12 — The mutation harness's ES27 plants a static import but the test scan's control only covers static shapes
· **Severity:** **Minor**
· **Evidence:** ES27 mutates `AppShell.tsx` to add `import { END_SESSION_PATH } from '../auth/endSession';` and `globalThis.location.assign(END_SESSION_PATH);`. The test control checks three static import shapes.
· **Assessment:** This is consistent. The mutant matches the control. No gap.
· **Recommendation:** None.

### F13 — The whole-suite runtime is growing
· **Severity:** **Informational**
· **Evidence:** `vitest run` now takes ~9 seconds, up from ~3 seconds in earlier tasks.
· **Assessment:** The growth is due to the large test file with many source scans and dynamic imports, plus jsdom creation overhead. It is not a correctness issue.
· **Recommendation:** None for T36. Consider worker pooling optimization separately.

### F14 — The harness arithmetic fix (FU-T36-3) is local to T36
· **Severity:** **Informational**
· **Evidence:** The self-review notes that the broken-counts-as-killed arithmetic exists in four earlier harness copies but was only fixed in T36's.
· **Assessment:** This is a known follow-up. The T36 harness is correct. Earlier tasks are closed.
· **Recommendation:** None for T36.

---

## What the test suite gets right

- **S1 — Phase 10 closed the two material gaps.** AC4 now checks raw query encoding, and AC1 has a source-order case.
- **S2 — Phase 8 findings I3 and I7 were accepted.** The import scan and the expanded surface sweep (`window.name`, `document.title`, `history.state`) are now in place.
- **S3 — Every scan and sweep has a control**, including the new import-scan controls.
- **S4 — The mutation harness correctly reports 30/30 with no broken-counted-as-killed**, and ES29/ES30 demonstrate the phase 10 fixes.
- **S5 — The `SIGN_OUT_SET` derivation and the monopoly case are strong**, with a control that proves a second module assembling the whole set is caught.
- **S6 — The non-idempotence of `endSession()` is pinned rather than hidden**, with a clear message for the future task that might change it.

---

## Summary

Phase 10 produced a strong, corrected test suite. The most important remaining gaps are **F1** (no production wiring test), **F2** (AC4 encoding test misses `id_token_hint`), **F3** (AC8 surface sweep misses `sendBeacon`/`Image`/`performance.mark`), and **F5** (T19/T20 suite not updated for `retainIdToken`). **F6** is a smaller gap in the import scan. The mutation score and gates are independently reproducible, and the harness arithmetic is now correct.

**Phase 11 complete.**
