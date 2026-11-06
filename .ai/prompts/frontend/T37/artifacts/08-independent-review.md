<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T37 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, implementation |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **26/26 passed** |
| `python3 -I .ai/prompts/frontend/T37/artifacts/07-mutation-harness.py` | **23/23 regressions killed, 0 survivors, 0 broken, 0 canaries** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1688/1689 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |

No production or test code was changed during this review.

---

## Findings

### I1 — The screen's "do nothing" scan is narrower than the token-privacy surface requires
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:285-291` forbids `fetch`, `XMLHttpRequest`, `sessionStorage`, `localStorage`, `document.cookie`, `indexedDB`, `setTimeout`, `setInterval`, `useEffect`, `useState`, `useRef`, `console.`, and `sendBeacon`. It does NOT forbid reading `accessToken()`, `takeIdToken()`, or any import from `auth/idToken.ts`.
· **Assessment:** The screen's central design constraint is that it must not read or assert session state. A mutant that imported `accessToken` and called it would still render the same output, and no test would fail. The output-equality cases (AC6, AC13) only notice if the render *changes*; they do not notice a read that branches to the same result. T36's AC8 covers `document.title`, `window.name`, and `history.state`; T37's scan covers none of these.
· **Recommendation:** Add `accessToken`, `takeIdToken`, `clearAccessToken`, `clearIdToken`, `retainIdToken`, `document.title`, `window.name`, and `history.state` to the AC7 source-scan forbidden list. Add controls proving each pattern fires. Mirror T36's surface sweep, because the privacy rationale is the same.

### I2 — `AC8` only checks the access token; the ID token is ignored
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:302-313` retains an access token, arrives at the screen, and asserts it survives. No case retains an ID token.
· **Assessment:** Task 36 introduced `idToken.ts` specifically so `endSession()` can send an `id_token_hint`. The signed-out screen must not touch that token either. A mutant that called `clearIdToken()` or `takeIdToken()` would not be caught by AC8.
· **Recommendation:** Extend AC8 to retain both an access token and an ID token before arrival, and assert both survive unchanged. Add the ID-token primitives to the source scan in I1.

### I3 — `AC10` does not prove `SIGNED_OUT_PATH` is used in `endSession.ts`
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:360-368` asserts `endSession.ts` does not declare its own copy and imports the published constant. It does not assert the constant appears in `postLogoutRedirectUri()`.
· **Assessment:** A mutant could import `SIGNED_OUT_PATH` and then hard-code `'signed-out'` in the URL function. The test would pass, but the very drift it is meant to prevent would exist. SL17 kills the private-copy revert, but not the import-without-use case.
· **Recommendation:** Add a source scan that asserts `SIGNED_OUT_PATH` appears inside `postLogoutRedirectUri` or `endSessionUrl` in `endSession.ts`, and that no literal `'signed-out'` remains in either function.

### I4 — The file name still promises an exhaustive claim T37 does not make
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:1-12` carries a long header explaining that the file name refers to a claim task 40 will complete. The file itself tests only one route.
· **Assessment:** The header is honest, but the name is still misleading. A reader grepping for the test file named in `tasks.md:71` will find T37's partial content first and may mistake it for the finished claim. The self-review defends this as a shared file T40 extends, which is reasonable, but the cost in readability is real.
· **Recommendation:** Consider renaming to `signedOutRouteIsPublicAndRenders.test.tsx` for T37, and let T40 create or rename the exhaustive file. If the shared name must stay, add a prominent comment at the top of every test case reminding readers that only the signed-out route is asserted here.

### I5 — The source scan forbids `<button` but not `<a role="button">` or `<input type="button">`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:218-219` checks for `button`, `input`, `select`, `textarea`, and `form` elements. The source scan forbids `<button`.
· **Assessment:** An `<a role="button">` would be counted as a link, not a button, so AC5's "only one way out" could be satisfied by a styled anchor that behaves like a button. This is unlikely but possible.
· **Recommendation:** Add `role="button"` to the source-scan forbidden list, or assert that the single anchor has `href` and no `role` attribute. Low priority.

### I6 — `AC7` forbids `sendBeacon` but the control only tests `sessionStorage`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:294-299` adds a control that plants `sessionStorage.getItem('x')`. It does not plant `sendBeacon`, `fetch`, `XMLHttpRequest`, etc.
· **Assessment:** The control proves the scan can see *one* forbidden surface, but not all of them. A typo in the regex (e.g., `/sendbeacon/` instead of `/sendBeacon/`) would not be caught.
· **Recommendation:** Add a planted-reach control for each class of forbidden surface, or at least for `sendBeacon` since it was added in this task.

### I7 — No test asserts the link receives the router's basename correctly in production
· **Severity:** **Minor**
· **Evidence:** AC5 asserts `href` is `${APP_BASENAME}/${SIGN_IN_PATH}`. The test uses a memory router with basename `APP_BASENAME`.
· **Assessment:** This is correct for jsdom. In a production browser, `Link` from `react-router` would also prepend the basename. The test covers the behavior.
· **Recommendation:** None.

### I8 — `AC11` whole-text equality is sensitive to whitespace, which is good but brittle
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:385-387` asserts `main.textContent` and `container.textContent` equal the concatenation of the three copy strings with no separators.
· **Assessment:** This pins the exact rendering. Any added newline or spacing will fail. That is the intended strength, but it means future styling changes (e.g., adding a `<br>`) will require updating the test.
· **Recommendation:** Document this in the test file so future maintainers understand why a whitespace change fails the suite.

### I9 — The screen has no error boundary or fallback
· **Severity:** **Minor**
· **Evidence:** `SignedOutScreen.tsx` is a single synchronous component. If `useTranslation()` throws, the route table has no error element for it.
· **Assessment:** This is consistent with `SignInScreen` and `CallbackScreen`. The current route table does not attach error elements.
· **Recommendation:** None for T37. Task 41 (catch-all) may consider this.

### I10 — `SL19` dies on `TypeError`, confirming FU-T37-1
· **Severity:** **Minor**
· **Evidence:** The self-review already records this: replacing `<main>` with `<div>` makes every `main!` dereference throw.
· **Assessment:** The screen's landmark is pinned only as a side effect. An independent re-measurement confirms the self-review's finding.
· **Recommendation:** Add an explicit assertion that `container.querySelector('main')` is not null, with a descriptive message about the landmark. This turns the `TypeError` into a named criterion.

### I11 — The route table comment at `routes.tsx:5` is still incorrect
· **Severity:** **Minor**
· **Evidence:** The self-review measured that a module-scope read does not make the application fail to start in this test environment.
· **Assessment:** Independent verification confirms the self-review's measurement. The comment in T16's closed file should be fixed by T16's owner.
· **Recommendation:** Leave as FU-T37-2; do not edit the closed file.

### I12 — No test verifies `SignedOutScreen` doesn't import `redirect` or `NavLink` from react-router
· **Severity:** **Minor**
· **Evidence:** AC5's source scan checks for `<Link `, `useNavigate`, `navigate(`, and `location.assign`. It does not check for `redirect(`, `NavLink`, or `<Navigate`.
· **Assessment:** A mutant that used `NavLink` would still render an anchor and pass most tests. A mutant that used `redirect('/home')` would fail AC12, so it is covered. `NavLink` is harmless but worth forbidding to keep the implementation uniform.
· **Recommendation:** Add `NavLink` to the source-scan forbidden list, or rely on the existing link-count assertion. Low priority.

### I13 — The mutation harness is the sixth copy of T35's harness
· **Severity:** **Informational**
· **Evidence:** The self-review notes this as inherited machinery.
· **Assessment:** The copy is functional but the duplicated harnesses across tasks are a maintenance burden. The self-review already opened OQ-T32-4.
· **Recommendation:** None for T37.

### I14 — `AC12` does not test arrival with a query string
· **Severity:** **Minor**
· **Evidence:** AC12 tests `router.state.location.search` is empty after arrival with no query string. AC6 tests identical rendering with query strings.
· **Assessment:** No gap — AC6 covers query-string behavior, and AC12 covers no-redirect behavior. The combination is complete.
· **Recommendation:** None.

---

## Summary

T37's implementation is clean and the self-review is thorough. The most important independent findings are **I1** (the source sweep misses token-reading and document/window surfaces) and **I2** (AC8 only checks the access token, not the ID token). These are privacy-scoped gaps directly related to the screen's central constraint. **I3** is a precision improvement for AC10. **I4** is a naming/readability concern already raised in Phase 3. The other findings are minor or confirm the self-review's follow-ups.

The suite is strong: 23/23 mutants killed, all gates green, and the one whole-suite failure is the known environmental `oxlint` host case.

**Phase 8 complete.** Fourteen findings returned; no code or contract changed.
