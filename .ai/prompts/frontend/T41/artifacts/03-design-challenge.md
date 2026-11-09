<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T41 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T41 — Unauthenticated redirect |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — The redirect's implementation shape is not specified
· **Severity:** **Major/Blocker**
· **Evidence:** The brief says "whether a production module is created is phase 3's to settle; the redirect may live in modules that already exist." It lists `auth/RequireSession.tsx` and `app/routes.tsx` as files to modify.
· **Assessment:** Without choosing between (a) a new `auth/RedirectToSignIn.tsx` component, (b) putting `<Navigate>` directly in `routes.tsx`, or (c) a `loader`-based redirect, the brief cannot be frozen. The choice determines screen-count pins, testability, StrictMode behavior, and module boundaries.
· **Recommended brief amendment:** Create a new `auth/RedirectToSignIn.tsx` component that:
  - reads `accessToken.current()`;
  - if a session exists, renders `null` (preserving today's behavior for authenticated unmatched paths);
  - if no session, reads `useLocation().pathname`, calls `rememberReturnTarget(pathname)` (the allowlist decides whether to store), and returns `<Navigate to={`/${SIGN_IN_PATH}`} replace />`.
  Use this component inside `RequireSession` when no session, on the catch-all route, and on an index route under the root for bare `/app/`.

### F2 — The catch-all and index redirects will fire the screen-count pins
· **Severity:** **Major/Blocker**
· **Evidence:** `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:22-27` counts every route with `element`, `Component`, or `errorElement` and `path !== undefined`. T37's AC9 does the same. After T41, the route table will have three screens plus the catch-all redirect plus an index redirect (and possibly `RequireSession` pathless, already excluded).
· **Assessment:** The count will go from three to five unless the instruments are updated. The brief explicitly calls this "the same shape of decision phase 3 settled for T40" and says it is T41's real design content.
· **Recommended brief amendment:** Update the screen-count instruments to distinguish screens from redirects. Either:
  - introduce a `SCREEN_PATHS` constant and assert only those three paths carry screen elements, or
  - update `routesWithElements` to exclude routes whose element type is `RedirectToSignIn` (or `Navigate`), and add a separate count pin for redirects.
  Document the convention so task 42 knows how to add authenticated screens without breaking either pin.

### F3 — `RequireSession` will need to use `useLocation` or delegate to a redirect component
· **Severity:** **Major**
· **Evidence:** T40's `GUARD_FORBIDDEN` in `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:311-355` forbids `useLocation`. If `RequireSession` itself uses `useLocation` to remember the return target, T40's source scan will fail.
· **Assessment:** The cleanest solution is to keep `RequireSession` as a pure session check and delegate the redirect (and the path read) to `RedirectToSignIn`. Then only `RedirectToSignIn.tsx` needs `useLocation`, and T40's scan for `RequireSession` remains valid. T41 must still write its own source scan for `RedirectToSignIn`.
· **Recommended brief amendment:** Specify that `RequireSession` renders `<RedirectToSignIn />` when there is no session, and that `RedirectToSignIn.tsx` is the only module that reads the current path for the return target. Update T40's `GUARD_FORBIDDEN` only if necessary — preferably not at all.

### F4 — Return target population during render is a side effect
· **Severity:** **Major**
· **Evidence:** `rememberReturnTarget` writes to `sessionStorage` (`frontend/src/auth/returnTarget.ts:88-96`). If `RedirectToSignIn` calls it during render, StrictMode double invocation will call it twice.
· **Assessment:** `rememberReturnTarget` is idempotent in outcome (same value written), but a test that asserts `setItem` was called exactly once would fail under StrictMode. The test design must assert the stored value, not the call count. Alternatively, use `useLayoutEffect` to call it once, but then the redirect navigation and the storage write are ordered differently.
· **Recommended brief amendment:** State that `rememberReturnTarget` is called during render, that StrictMode may call it twice, and that tests assert the final stored value rather than the number of writes. Add a StrictMode case that verifies the stored value is correct.

### F5 — The three arrivals must be indistinguishable in redirect behavior
· **Severity:** **Major**
· **Evidence:** AC9 says the three arrivals have the same destination, history action, rendered output, and no copy naming a cause. L4 binds harder here than anywhere.
· **Assessment:** Using the same `RedirectToSignIn` component for all three arrivals is the right way to make them identical. The return target differs, but it is stored in the visitor's own `sessionStorage` and reflects only the path they typed. The brief acknowledges this.
· **Recommended brief amendment:** Specify that all three arrivals render `<RedirectToSignIn />`, and that the only difference is the path passed to `rememberReturnTarget`, which the allowlist filters.

### F6 — The bare `/app/` case needs an index route or root-element redirect
· **Severity:** **Major**
· **Evidence:** AC3 says a sessionless arrival at bare `/app/` redirects to sign-in. The current route table has `path: '/'` with children but no `index` route.
· **Assessment:** Add `{ index: true, element: <RedirectToSignIn /> }` under the root route. This matches only the bare `/app/` path. Verify that `/app/sign-in` still matches the `sign-in` child and is not redirected.
· **Recommended brief amendment:** Add the index route to `routes.tsx` and test both `/app/` and `/app` (normalized).

### F7 — The catch-all redirect must not redirect authenticated visitors
· **Severity:** **Major**
· **Evidence:** R28 is conditioned "while unauthenticated." The brief's Open Questions say authenticated unmatched paths are out of scope.
· **Assessment:** If the catch-all uses `<Navigate to={SIGN_IN_PATH} replace />` directly, it will redirect everyone including authenticated visitors. `RedirectToSignIn` must check `accessToken.current()` and render `null` when a session exists.
· **Recommended brief amendment:** Specify that `RedirectToSignIn` returns `null` for authenticated visitors, preserving today's blank-page behavior for unmatched authenticated paths.

### F8 — `rememberReturnTarget` uses the allowlist, so (b) and (c) need no branch
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/returnTarget.ts:88-89` rejects non-allowlisted targets. The allowlist is built from `AUTHENTICATED_ROUTE_PATHS` minus parameterized routes.
· **Assessment:** If `RedirectToSignIn` always calls `rememberReturnTarget(pathname)`, the allowlist naturally refuses `/no-such-route` and `/`. This satisfies "by the allowlist, not by a branch" and unifies the three arrivals.
· **Recommended brief amendment:** State that `RedirectToSignIn` always calls `rememberReturnTarget` with the basename-relative pathname, and the allowlist decides what is stored.

### F9 — Parameterized authenticated routes will not be remembered
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/returnTarget.ts:51-58` excludes paths with segments starting with `:`. So `/invoices/123` and `/receipts/:id` are not allowlisted.
· **Assessment:** This is the existing behavior documented in T24. T41 should not try to fix it. The test should assert that arrival at `/invoices/:id` redirects to sign-in but does NOT populate the return target.
· **Recommended brief amendment:** Add a boundary test for `/invoices/:id` (and `/receipts/:id`): redirect happens, return target is not stored.

### F10 — The redirect must use `replace`, not push
· **Severity:** **Major**
· **Evidence:** AC6. The brief says "a replacing navigation."
· **Assessment:** Use `<Navigate to={`/${SIGN_IN_PATH}`} replace />`. Test `router.state.historyAction` is `'REPLACE'` or similar.
· **Recommended brief amendment:** Specify `replace` on `Navigate` and assert history action.

### F11 — The destination must be built from `SIGN_IN_PATH` read inside a function
· **Severity:** **Minor/Major**
· **Evidence:** AC11 and the package's pattern.
· **Assessment:** `RedirectToSignIn` should read `SIGN_IN_PATH` inside the component function, never at module scope, to avoid the import cycle documented in `routes.tsx:2-5`.
· **Recommended brief amendment:** State this explicitly and add a source scan.

### F12 — The redirect renders no copy
· **Severity:** **Minor**
· **Evidence:** AC10, L11.
· **Assessment:** `Navigate` renders nothing. Tests should assert no text is rendered.
· **Recommended brief amendment:** None required beyond the existing AC10.

### F13 — The new test file must be structured so task 42 can extend it
· **Severity:** **Minor**
· **Evidence:** The brief says task 42 extends the file, and describes should be prefixed from the first case.
· **Assessment:** The new `unauthenticatedRoutesRedirectToSignIn.test.tsx` should use prefixed describe blocks (e.g., `AC1 authenticated paths redirect`) so task 42 can add more cases without name collisions.
· **Recommended brief amendment:** Add a note about test file structure in Required Tests.

### F14 — Public paths with query strings and trailing slashes must not redirect
· **Severity:** **Minor/Major**
· **Evidence:** AC4 and Boundaries.
· **Assessment:** React Router normalizes trailing slashes and preserves query strings. Tests should verify `/app/verify?token=abc` and `/app/verify/` render the public route and stay on the same path with `POP` history action.
· **Recommended brief amendment:** Add boundary tests.

### F15 — The test should verify the return target is consumed by the callback later
· **Severity:** **Minor/Major**
· **Evidence:** AC7 says (a) remembers the arrival. The purpose is for post-login return.
· **Assessment:** A full integration would be task 42's. T41 can assert the stored value matches the expected target. A narrower test could import `takeReturnTarget` and assert it returns the remembered value.
· **Recommended brief amendment:** Add a case that after redirect, `sessionStorage[RETURN_TARGET_KEY]` equals the expected path (e.g., `/account`), and that `takeReturnTarget()` returns it.

### F16 — No new storage key
· **Severity:** **Minor**
· **Evidence:** AC8.
· **Assessment:** Use existing `RETURN_TARGET_KEY`. T22's inventory already knows it.
· **Recommendation:** None required beyond using `rememberReturnTarget`.

### F17 — The `RedirectToSignIn` component must not import from `app/` except route constants
· **Severity:** **Minor**
· **Evidence:** Constraints.
· **Assessment:** It will import `SIGN_IN_PATH` from `../app/routes`. It should not import screens or other app modules.
· **Recommended brief amendment:** Add AC6 source scan for `RedirectToSignIn.tsx`.

### F18 — The redirect must not read or assert account state
· **Severity:** **Minor**
· **Evidence:** Security constraint: held token does not prove account usable.
· **Assessment:** Only `accessToken.current()` is checked. No call to `/accounts/me` or similar.
· **Recommended brief amendment:** Add to source scan: forbid `accounts/me`, `fetch`, `XMLHttpRequest`.

---

## Summary

T41's design challenge is about the redirect mechanism and how it interacts with the route table's screen-count pins. The most important gaps are **F1** (redirect implementation shape not specified), **F2** (screen-count pins will fire), **F3** (`RequireSession` vs `RedirectToSignIn` division of labor), **F4** (return target population during render), **F6** (bare `/app/` redirect), and **F7** (authenticated unmatched paths must not be redirected). A clean design is a single `auth/RedirectToSignIn.tsx` component used for all three sessionless arrivals, with `RequireSession` delegating to it, and the screen-count instruments updated to distinguish screens from redirects.

**Phase 3 complete.** Eighteen findings returned; no code or contract changed.
