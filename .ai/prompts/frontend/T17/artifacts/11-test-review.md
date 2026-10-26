# frontend · T17 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Method

Reviewed the Phase 10 test manifest for `callbackWithMismatchedStateIsDiscarded` (62 cases, M8–M10 killed, M1–M7 killed earlier) against the frozen T17 acceptance criteria and the Phase 8 findings. No code was rewritten.

The Phase 9/10 resolution is outstanding: every Phase 8 finding is closed (F1 `.catch`, F2 empty deps, F3 trim/whitespace, F4 StrictMode seam-zero, F5 `CALLBACK_PATH`, F6 keyed violations), plus three new groups: the second circular-import strand (AC12), the notice drain as a unit (AC16), and real authorization-server shapes (OAuth error response, duplicated parameters).

---

## Gaps

### 1. No assertion that the post-discard sign-in URL carries no callback query parameters
**Gap ·** The navigation cases assert the pathname is `/app/sign-in` and the action is `REPLACE`, but they do not assert that `router.state.location.search` is empty. A buggy implementation that navigated to `/${SIGN_IN_PATH}?code=abc` would still pass the pathname tests and would leave the authorization code in browser history.  
**Why it matters ·** Privacy: the callback's `code` and `state` should not survive the discard navigation.  
**Suggested test ·** Add `expect(router.state.location.search).toBe('')` to the discard navigation cases.

### 2. Trim is only tested for all-whitespace values, not for leading/trailing whitespace
**Gap ·** `CallbackScreen.tsx` trims `state` and `code` before use. The test suite only checks that all-whitespace values are discarded. It does not check that a value with leading/trailing whitespace is trimmed before reaching the seam.  
**Why it matters ·** The contract says "the trimmed values travel onward, so the exchange never receives padding the authorization server did not send." A regression that moved `trim()` to only the discard branch would not be caught.  
**Suggested test ·** Add a success-path case: `/callback?code=abc&state=%20xyz%20` and assert the seam receives `{ state: 'xyz' }` (and `code: 'abc'`).

### 3. The `error` + `code` combination is not covered
**Gap ·** The OAuth error-response case asserts the screen discards when `error` is present and `code` is absent. It does not cover a malicious or malformed URL that contains both `error` and `code`.  
**Why it matters ·** The current code proceeds to exchange whenever `code` is non-empty. If a URL carries both, the screen would exchange the code rather than treat it as an authorization error. The correct behavior is debatable, but the suite currently says nothing.  
**Suggested test ·** Add a case for `/callback?error=access_denied&code=abc&state=xyz` and assert either (a) it is discarded as an error response, or (b) it is treated as a success and the seam is called. Lock the chosen behavior with a test.

### 4. Catalogue keys used by the callback screen are not asserted to exist
**Gap ·** The callback screen renders `t('callback.heading')` and `t('callback.working')`. The suite checks that these catalogue values do **not** appear as literals in the source, but it never asserts that the keys exist and are non-empty strings. If the keys were missing, `t` would fall back to the key names and most assertions would still pass.  
**Why it matters ·** A missing translation would be a UI defect that the current suite does not catch.  
**Suggested test ·** Add an assertion: `expect(en.callback.heading).toBeTruthy()` and `expect(en.callback.working).toBeTruthy()`.

### 5. The StrictMode accessibility case is missing
**Gap ·** The suite asserts one `h1`, one `main`, and one live region on the callback screen in a normal render. It does not assert the same under `StrictMode`, where React double-invokes effects and could in theory mount duplicate live regions if the effect were structured incorrectly.  
**Why it matters ·** StrictMode is a first-class concern for this screen (the latch exists because of it). The DOM output under StrictMode should be identical.  
**Suggested test ·** Optional: add an accessibility case under `{ strict: true }` and assert the same element counts.

### 6. No test that the callback screen does not read or write `sessionStorage` at all
**Gap ·** The suite spies on `Storage.prototype` methods and asserts none are called during a discard. It does not explicitly assert that the success path (which reaches the seam) also does not touch storage. The implementation does not, but the success path is where T18/T20 will add reads; the boundary should be pinned.  
**Why it matters ·** T17 must not read stored `state`/`nonce`/`code_verifier` — that is T18/T20's job. A regression that added a read in the success branch would pass the current suite.  
**Suggested test ·** Add the Storage spy case to the success path as well, asserting `getItem`/`setItem`/`removeItem`/`clear` are not called.

### 7. No negative control for the `SIGN_IN_PATH` indentation scan
**Gap ·** The circular-import guard asserts every non-comment, non-import use of `SIGN_IN_PATH` is indented. It does not feed a synthetic source containing a module-scope use and assert the scan detects it.  
**Why it matters ·** The scan is the only thing that catches the TDZ defect, per the manifest. A regression in the regex (e.g., matching `SIGN_IN_PATH` inside a string) could break it.  
**Suggested test ·** Add a unit test for the scan logic: pass it a snippet with a module-scope `SIGN_IN_PATH` use and assert it fails; pass it the real source and assert it passes.

### 8. No mutation that removes the `.catch(discard)` line
**Gap ·** The F1 test asserts a rejecting seam returns the visitor to sign-in. There is no mutant listed that removes `.catch(discard)` and verifies the test fails.  
**Why it matters ·** The test is only load-bearing if the code path is shown to fail when the `.catch` is absent.  
**Suggested test/mutant ·** Add a mutation pass that comments out `.catch(discard)`; the F1 case should fail (visitor stays on callback). This is the standard T09/T13/T14 pattern.

---

## Non-gaps (acknowledged, no change needed)

- **62/62 cases pass** and all listed mutants are caught.
- **F1 `.catch` rejection handling** is implemented and tested.
- **F2 empty dependency array** is implemented and tested with a re-render case.
- **F3 trim/whitespace** is implemented and tested with `%20%20` and `%09` cases.
- **F4 StrictMode seam-zero on discard** is added.
- **F5 `CALLBACK_PATH` constant** is used symmetrically in `routes.tsx`.
- **F6 keyed violation records** prevent misalignment.
- **AC12 second circular-import strand** is pinned by indentation scan and dynamic import.
- **AC16 notice drain** is tested as a unit.
- **R8 boundary cases** for OAuth error response and duplicated `state` are added.
- **AC1–AC10, AC14–AC17** are covered; AC11 and AC13 are regression obligations discharged by suite runs.
- **Every matcher has a non-vacuity control** (seam spy positive, AC15 violations keyed, copy word list with reach, L4 identical-copy comparison).
- **OQ-5** is correctly noted: `stateSurvivesRedirectAndIsConsumedOnce` is task 23's and was deliberately not implemented here.
- **Suite state 608/609** with one known environmental T07 failure is consistent.

---

## Summary

T17's test suite is excellent: 62 cases, strong mutation coverage, and honest handling of every Phase 8 finding plus the circular-import strand. The remaining gaps are small and mostly about privacy/hygiene: ensuring callback parameters do not leak into the sign-in URL, testing trim on partial whitespace, deciding the `error`+`code` case, asserting catalogue keys exist, covering StrictMode accessibility, pinning the storage boundary on the success path, and adding a negative control for the TDZ scan. Adding these would close the last holes without changing the suite's core design.
