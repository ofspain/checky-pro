<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T41 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T41 — Unauthenticated redirect |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | **34 / 34 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1779 / 1780 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T41/artifacts/07-mutation-harness.py` | **20/20 regressions killed, every kill `KILLED/own`, 0 survivors, 0 unfalsifiable, 0 broken, 0 disagreements** |

No production or test code was changed during this review.

---

## Findings

### F1 — The forbidden-surface sweep is missing several auth/session/navigation surfaces
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:141-167` lists 25 forbidden patterns. It includes `retainAccessToken`, `clearAccessToken`, `retainIdToken`, `takeIdToken`, `adopt`, `renew`, `signOut`, `endSession`, `markSignedOut`, `broadcastSignOut`, `redirect`, and `globalThis.location`, but it does NOT include:
  - `listenForSignOut` (subscribes to cross-tab sign-out events)
  - `markSignedIn` (the latch counterpart to `markSignedOut`)
  - `setSignInNotice` (sets the notice shown on the sign-in screen)
  - `clearReturnTarget` or `takeReturnTarget` (other return-target writers/readers)
  - `useNavigate` / `\bnavigate\s*\(` (imperative navigation)
  - `useHref` (route-aware href construction)
  - `switch` (the no-branch scan only forbids `if`, `?`, `&&`, `||`)
· **Assessment:** T40's `GUARD_FORBIDDEN` already includes `useNavigate`, `useHref`, `markSignedIn`, and `listenForSignOut`. T41's sweep should be at least as complete, because `RedirectToSignIn` is the component that actually performs navigation and touches the session. `setSignInNotice` is a natural thing to forbid because the redirect must not name a cause to the visitor. `switch` is a gap in the no-branch rule: a `switch` on `pathname` with cases that skip `rememberReturnTarget` would be behaviorally invisible for disallowed paths (the allowlist refuses them anyway) but would violate the "allowlist is the only filter" design rule. The missing `clearReturnTarget` / `takeReturnTarget` rows matter because the redirect's only storage interaction should be `rememberReturnTarget`.
· **Recommendation:** Add the missing rows to `FORBIDDEN`, with paired probes, and update the length pin from 25 to the new count. Add `switch` to the no-branch regex (or add a separate row forbidding it).

### F2 — No case holds a session and arrives at an authenticated path, then asserts the return target is untouched
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:290-298` checks that an authenticated visitor is not redirected from the eleven authenticated paths, but it only asserts `pathname` and `historyAction`. It never asserts `stored()` is null. The second and third AC5 cases do assert `stored()` is null for unmatched paths and bare `/app/`, but not for authenticated paths.
· **Assessment:** `RedirectToSignIn.tsx:49-54` puts the session check FIRST, and the comment calls the order "load-bearing": a return target must not be written for someone who already has a session. If a future refactor moved `rememberReturnTarget` (or even `useLocation`) above the session check, the AC5 first test would still pass on pathname/action while silently writing `/account` into the visitor's storage. That would be a stale deep link waiting for a sign-in that is not coming — exactly the hazard the component header warns about.
· **Recommendation:** Add `expect(stored(), 'a return target was stored for a visitor who already has a session').toBeNull();` inside the AC5 authenticated-path loop.

### F3 — The indistinguishability test does not assert the shared history action is REPLACE
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:408-430` checks that the three arrivals have the same destination, action, and markup by `Set` size, and asserts the destination is sign-in.
· **Assessment:** The test guarantees sameness but not the correct value of `historyAction`. If a mutant changed all three arrivals to PUSH (or even POP), the test would still see a single action and pass, because it never checks `seen[0].action === 'REPLACE'`. RD04 (`replace` dropped) is killed elsewhere, so this is not a harness gap, but the AC9 case's own claim is weaker than it should be.
· **Recommendation:** Add `expect(seen[0].action).toBe('REPLACE');` after the destination assertion.

### F4 — The no-branch source scan's regex is brittle and incomplete
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:470-472` asserts the slice between `const { pathname }` and `rememberReturnTarget(` contains no `\bif\b`, `?`, `&&`, or `||`.
· **Assessment:** The slice is sensitive to the exact names of the local variables. If the component were refactored to rename `pathname` to `locationPathname`, the scan would measure from the wrong place and pass trivially. It also does not forbid `switch`, `while`, `for`, `try`, or `do` in that region. A `switch` with a default case that calls `rememberReturnTarget` would be behaviorally invisible for disallowed paths but would violate the design rule.
· **Recommendation:** Strengthen the scan by:
  - asserting the slice actually contains both `const { pathname }` (or equivalent) and `rememberReturnTarget(`;
  - adding `\bswitch\b`, `\bwhile\b`, `\bfor\b`, `\btry\b`, and `\bdo\b` to the forbidden list, or by switching to a structural assertion (e.g., counting statements between the two anchors).

### F5 — The bare `/app/` with-session case does not assert the return target is untouched
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:313-318` checks the bare `/app/` renders nothing for an authenticated visitor but does not assert `stored()` is null.
· **Assessment:** The unmatched-path case already asserts this. For symmetry and because the same order hazard applies, the bare `/app/` case should too. This is a minor tightening, not a new regression class.
· **Recommendation:** Add `expect(stored(), 'a return target was stored for the bare /app/ visitor who already has a session').toBeNull();`.

### F6 — The source scan does not enforce that the session check comes before any route reading or storage writing
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/RedirectToSignIn.tsx:49-80` reads `accessToken.current()` first, then `useLocation()`, then `rememberReturnTarget`. The test scans the source but does not assert this order.
· **Assessment:** The order is load-bearing (see F2). A source scan could assert that `accessToken.current()` appears before `useLocation()` and `rememberReturnTarget` in the function body. The behavior tests catch the worst case (unmatched path with session), but not the authenticated-path-with-session case.
· **Recommendation:** Add a source-scan case that asserts `accessToken.current()` is the first external read in the function body, preceding both `useLocation` and `rememberReturnTarget`.

### F7 — The forbidden-surface sweep forbids `document` broadly but not `window`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:147` forbids `/\bdocument\b/`. There is no row for `window`.
· **Assessment:** `window.location`, `window.history`, `window.name`, and `window.sessionStorage` are all ways a component could reach outside React. `window.sessionStorage` is caught by the `sessionStorage` row (word boundary), and `window.location.assign` is caught by `location.assign`, but `window.name` and `window.history.pushState` are not.
· **Recommendation:** Add `/\bwindow\.name\b/` and `/\bhistory\.(pushState|replaceState)\b/`, or a single `/\bwindow\b/` row if the intent is to forbid all direct window access.

### F8 — The byte-identical output test is strong but compares HTML after the redirect has settled
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:397-405` compares `viaRedirect.innerHTML` to `direct.innerHTML`.
· **Assessment:** This is a good test. It would catch a wrapper div, a notice, or extra markup from the redirect. Because `act()` flushes renders synchronously, the comparison happens after navigation. The test is correct.
· **Recommendation:** None.

### F9 — The `REMEMBERABLE` set is computed from `AUTHENTICATED_ROUTE_PATHS` and is correct
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:46-47` filters out paths with segments starting with `:`.
· **Assessment:** This matches `returnTarget.ts`'s allowlist logic. The AC13 case asserts `REMEMBERABLE.length > 0` and `< 11`, so the loop cannot silently be empty.
· **Recommendation:** None.

### F10 — The test file correctly excludes `callback` from rendered public routes
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:247-257` and the file header.
· **Assessment:** `callback` acts on arrival, and its behavior belongs to task 6. The test matches it render-free and asserts the route resolves. This is correct.
· **Recommendation:** None.

### F11 — The `takeReturnTarget()` call in AC7 is a strong end-to-end assertion
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:333-334` asserts `takeReturnTarget()` returns `/account` and deletes it.
· **Assessment:** This pins both the write and the read side of the return target, which is the closest T41 can get to an end-to-end deep-link flow without a screen behind the gate.
· **Recommendation:** None.

### F12 — Phase 9 removed the basename strip, and the suite now pins the correct behavior
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/RedirectToSignIn.tsx:56-80` and the AC7 cases.
· **Assessment:** The Phase 8 review (I6) identified the basename strip as potentially dead/defective. Phase 9 removed it, and the tests now assert that `/app/account` stores `/account` while `/app/app/account` stores nothing. The no-branch scan passes because the strip's ternary operators are gone.
· **Recommendation:** None. This is confirmation that the review resolution was correct.

### F13 — The mutation harness now scores RD09 as killed, not unfalsifiable
· **Severity:** **Minor**
· **Evidence:** Harness output shows RD09 killed by `puts no branch on the arrival before remembering it (AC7, phase 8 I3)`.
· **Assessment:** Phase 8's I3 finding (RD09 is behaviorally unfalsifiable but source-enforceable) was correct and is now closed by the source scan.
· **Recommendation:** None.

### F14 — No test verifies the redirect does not read or write cookies
· **Severity:** **Minor**
· **Evidence:** `FORBIDDEN` forbids `document.cookie` indirectly via `/\bdocument\b/`, but there is no explicit `/document\.cookie/` row and no behavior test for cookies.
· **Assessment:** The broad `document` row is sufficient for a source scan, but an explicit row would make the intent clearer and match T40's scan.
· **Recommendation:** Optional: add an explicit `/document\.cookie/` row to `FORBIDDEN`.

---

## Summary

T41's Phase 10 is a strong suite: 34 cases, 20/20 mutants killed with every kill in this task's own files, and the survivors from Phase 7 are all gone. The most important remaining gaps are **F1** (the forbidden-surface sweep misses `listenForSignOut`, `markSignedIn`, `setSignInNotice`, `useNavigate`, `useHref`, and `switch`), **F2** (no test asserts an authenticated visitor arriving at an authenticated path leaves the return target untouched), and **F4** (the no-branch scan is brittle and incomplete). **F3** and **F5** are smaller tightenings. The suite and gates are independently reproducible, and the one whole-suite failure is the known environmental `oxlint` host case.

**Phase 11 complete.** Fourteen findings returned; no code or contract changed.
