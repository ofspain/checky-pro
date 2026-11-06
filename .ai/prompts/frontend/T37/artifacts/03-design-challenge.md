<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T37 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — The copy is not specified; the brief only gives a shape
· **Severity:** **Major/Blocker**
· **Evidence:** OQ-T37-1 says the copy is "derived from L4 and modelled on `signIn.sessionEnded`" and names three keys (`heading`, `explanation`, `action`), but it does not propose the actual English strings.
· **Assessment:** Without the text, Phase 4 cannot freeze the brief. The copy must be neutral (L4), name the event not a cause, not be a lie if the visitor still holds a token, and not duplicate the sign-in button text. The actual wording determines whether AC3, AC4, AC5, and AC12 are testable.
· **Recommended brief amendment:** Add the proposed catalogue strings. For example:
  - `heading`: "Signed out"
  - `explanation`: "You can sign back in whenever you are ready."
  - `action`: "Sign in"
  Then justify each against L4 and OQ-T37-2.

### F2 — The decision to use `Link` vs. button is delegated to Phase 5, but the brief is frozen
· **Severity:** **Major**
· **Evidence:** The Constraints section says "T37 would be the first use of react-router's `Link`" and "Phase 5 should confirm a real anchor is wanted over a button." The brief header says "Brief frozen."
· **Assessment:** A frozen brief cannot defer a UX/navigation decision to a later phase. The choice affects the component's imports, the test's assertions (anchor vs. button), and whether the screen performs a router navigation or a full-page one. The brief must take a position.
· **Recommended brief amendment:** Freeze the choice. A `Link` to `/app/sign-in` is correct because this is an in-app navigation to a public route, and it lets the visitor use browser back/forward naturally. If a button is chosen instead, document why.

### F3 — The screen is reachable while signed in, and the copy must not be a lie
· **Severity:** **Major**
· **Evidence:** OQ-T37-2 freezes option (a): the screen makes no claim it can verify, and a visitor who types `/app/signed-out` while holding a token sees the screen.
· **Assessment:** This is the right security decision, but it places a heavy burden on the copy. If the heading says "You have been signed out" or "Signed out successfully," it is false for a token-holding visitor. The copy must describe the route's purpose, not assert the visitor's state.
· **Recommended brief amendment:** Add an explicit constraint that the copy must be true for both a signed-out visitor and a token-holding visitor who arrives directly. Provide example wording that satisfies this.

### F4 — Attaching `SignedOutScreen` to `routes.tsx` risks a new import cycle
· **Severity:** **Major**
· **Evidence:** `routes.tsx:2-5` documents an existing cycle: `routes -> SignInScreen -> authorizeRequest -> routes`. `SignedOutScreen` will likely import `SIGN_IN_PATH` from `routes.tsx` for its action, creating `routes -> SignedOutScreen -> routes`.
· **Assessment:** If `SignedOutScreen` reads `SIGN_IN_PATH` at module scope, it could join the existing cycle or create a new one. The existing cycle resolves because `APP_BASENAME` is read inside a function, not at module scope. The same discipline must apply to `SIGN_IN_PATH` in `SignedOutScreen`.
· **Recommended brief amendment:** Require that `SignedOutScreen` read `SIGN_IN_PATH` inside the component render or event handler, never at module scope, and add a source scan to enforce it.

### F5 — Publishing `SIGNED_OUT_PATH` changes `routes.tsx` for the second consecutive task
· **Severity:** **Minor/Major**
· **Evidence:** T36 declared `SIGNED_OUT_PATH` privately in `endSession.ts` because `routes.tsx` was closed. T37 now modifies `routes.tsx` to publish it.
· **Assessment:** This is correct in scope, but it means T36's `endSession.ts` now has a duplicate spelling that should be replaced with the published constant. The brief mentions this retires T36-D2 but does not explicitly say to edit `endSession.ts`.
· **Recommended brief amendment:** Add `auth/endSession.ts` to the Files to Modify list, replacing the private `SIGNED_OUT_PATH` constant with the import from `routes.tsx`. Add a test that asserts the two spellings are equal.

### F6 — `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` is named for a claim T37 does not make
· **Severity:** **Minor/Major**
· **Evidence:** OQ-T37-3 says T37 creates `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` holding only its own route's claims, and task 40 completes it for all nine public paths.
· **Assessment:** The file name promises an exhaustive set claim, but T37's content will be one route. This is confusing and could lead a reader to think T37 is incomplete. The file header can explain, but the name is misleading.
· **Recommended brief amendment:** Either rename the file to `signedOutRouteIsPublicAndRendersItsScreen.test.tsx` (or similar) for T37, and let T40 create the exhaustive file separately, or accept the shared name and make the header very explicit.

### F7 — `routesResolveUnderAppPrefixOnly.test.tsx` is a closed task's suite that T37 edits
· **Severity:** **Minor**
· **Evidence:** The brief modifies `SCREENS_ATTACHED_SO_FAR` in `routesResolveUnderAppPrefixOnly.test.tsx`, which is T16's suite.
· **Assessment:** The edit is small and justified, but it is still a later task editing a closed task's test. The brief should note this and ensure the change is additive (adding one string to the array).
· **Recommended brief amendment:** Document that T16's suite is edited additively and that the original T16 cases are unchanged.

### F8 — No test is proposed for the screen's behavior when the visitor clicks the action
· **Severity:** **Minor/Major**
· **Evidence:** Required tests say "The way back to sign-in is present, is the only action, and resolves to `/app/sign-in`" but do not specify how to test the click.
· **Assessment:** If the action is a `Link`, the test should assert `href` is `/app/sign-in`. If it is a button that calls `navigate`, the test should assert `navigate` is called with `/app/sign-in`. The brief's ambiguity here makes AC5 under-specified.
· **Recommended brief amendment:** State the action type (Link or button) and the exact test assertion. For a Link: assert `screen.getByRole('link').href` ends with `/app/sign-in`. For a button: assert `navigate` is called with `/${SIGN_IN_PATH}`.

### F9 — The screen's indifference to query string may be confusing for the `state` SAS sends
· **Severity:** **Minor**
· **Evidence:** OQ-T37-4 freezes indifference: T36 sends no `state`; the backend test supplies one. AC6 requires identical rendering with and without query parameters.
· **Assessment:** This is correct, but the brief should ensure the screen does not accidentally read or display `state` (which could be a long opaque string and a poor UX). A source scan forbidding `useSearchParams` or `location.search` would pin this.
· **Recommended brief amendment:** Add a source scan that asserts `SignedOutScreen.tsx` does not import `useSearchParams`, `useLocation`, or read `globalThis.location.search`.

### F10 — The screen has no loading or error state, but the pattern exists in `SignInScreen`
· **Severity:** **Minor**
· **Evidence:** `SignInScreen.tsx:21` has `status` state for `idle`/`starting`/`failed`. `SignedOutScreen` is a static screen.
· **Assessment:** A static screen does not need status state. The brief correctly scopes T37 as just words and one action. But the test should assert the screen renders synchronously with no pending state.
· **Recommended brief amendment:** None required; the static nature is clear.

### F11 — AC9 `routesWithElements(appRoutes) equals exactly three` is brittle
· **Severity:** **Minor**
· **Evidence:** AC9 says exactly three routes have elements after T37.
· **Assessment:** This is correct today (sign-in, callback, signed-out). But if a future task attaches a screen to another public route before T40, this test would fail. That is the point of the pin, but it should be documented.
· **Recommended brief amendment:** Add a comment that AC9 is a count pin and will fail deliberately when the next screen is attached.

### F12 — The brief does not say whether `SignedOutScreen` uses `useTranslation` from the screen or the catalogue
· **Severity:** **Minor**
· **Evidence:** The pattern is `useTranslation()` + `t('signedOut.heading')` per `SignInScreen.tsx`.
· **Assessment:** This is implied by the dependency table, but the brief should be explicit.
· **Recommended brief amendment:** State that `SignedOutScreen` follows the `SignInScreen` pattern: `useTranslation()`, no props, keys under `en.signedOut`.

### F13 — The screen does not clear tokens, but a visitor might expect it to
· **Severity:** **Minor**
· **Evidence:** OQ-T37-2 rejects clearing on arrival.
· **Assessment:** This is the right security decision. The brief explains it well.
· **Recommended brief amendment:** None.

### F14 — `tasks.md` named test vs. file name
· **Severity:** **Minor**
· **Evidence:** `tasks.md` likely names a test for T37. The brief creates `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`.
· **Assessment:** If `tasks.md` names a different test, the file name should match. The brief says the file name follows the house convention (named after the task's named test in `tasks.md`).
· **Recommended brief amendment:** Verify the `tasks.md` test name and align the file name. If the named test is about the public route set, the current name is fine.

---

## Summary

T37 is a small task, but the brief leaves several decisions unresolved that block freezing. The most important are **F1** (copy not specified), **F2** (Link vs. button deferred), **F3** (copy must not be a lie for token-holding visitors), and **F4** (import-cycle risk). **F5** is a practical edit that should be explicit. The other findings are smaller scoping and naming issues.

**Phase 3 complete.** Fourteen findings returned; no code or contract changed.
