<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T40 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T40 — Public route set is exact |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — The route guard's implementation shape is not specified
· **Severity:** **Major/Blocker**
· **Evidence:** The brief says "one module under `auth/` — the guard. `design.md:203` places guards in `auth/`; the name and shape are phase 3's to settle."
· **Assessment:** Without choosing between a React component (`<RequireSession><Outlet /></RequireSession>`), a `Component` prop, a higher-order component, or a `loader`, the brief cannot be frozen. The choice determines how tests assert the guard is applied, how StrictMode behaves, and how T37's AC9 count pin must be updated.
· **Recommended brief amendment:** Specify the guard as a React component named `RequireSession` (or similar) that renders `<Outlet />` when `accessToken.current()` is non-null and renders `null` otherwise. This is the idiomatic React Router v6 pattern and is testable both in isolation and through the route table.

### F2 — AC3's test design is the real content and is not specified
· **Severity:** **Major/Blocker**
· **Evidence:** The Open Questions section says "AC3's measurement is the task's real design content, and it is phase 3's to pick: the criterion is fixed here, the method is not."
· **Assessment:** AC3 requires that AC2 (authenticated paths serve no content without a session) turns red when the guard is removed. The natural test is: assert that each authenticated route's element is the guard component, and that the guard renders `null` when `accessToken.current()` is null. But that only proves the guard is present, not that removing it would fail. A stronger test is a control that mounts the route table with the guard replaced by a pass-through and asserts an authenticated path renders content.
· **Recommended brief amendment:** Add AC3 test design: in a control, replace `RequireSession` with a component that always renders `<Outlet />`, mount an authenticated path with no session, and assert content is rendered. The production case asserts no content.

### F3 — T37's AC9 count pin will fire on every authenticated path
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:316-348` walks `appRoutes` and asserts exactly three routes have elements. If T40 adds guard elements to eleven authenticated paths, this will count fourteen.
· **Assessment:** The brief acknowledges this but does not say how to update the instrument. The count pin must distinguish "screens" (rendered elements) from "guards" (session gates), or it must grow to fourteen and lose its meaning as a screen-count pin.
· **Recommended brief amendment:** Update T37's AC9 to count only routes whose element is a screen component (sign-in, callback, signed-out), and add a separate pin that counts routes with guard elements (eleven). Alternatively, introduce a `SCREEN_PATHS` constant in `routes.tsx` and derive both counts from it.

### F4 — The sibling instrument in `routesResolveUnderAppPrefixOnly.test.tsx` needs a guard-aware distinction
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:17` has `SCREENS_ATTACHED_SO_FAR = ['sign-in', 'callback']`, which T37 updated to include `'signed-out'`. The brief says T40 will grow this instrument.
· **Assessment:** If T40 adds guard elements to authenticated routes, `SCREENS_ATTACHED_SO_FAR` should remain `['sign-in', 'callback', 'signed-out']` (screens only), and a new array `GUARDS_ATTACHED_TO_AUTHENTICATED_PATHS` should list the eleven authenticated paths. The existing case that checks "attaches a screen only to the routes whose task has run" must not accidentally count guards as screens.
· **Recommended brief amendment:** Document the exact changes: keep `SCREENS_ATTACHED_SO_FAR` at three, add `GUARDED_PATHS` as the eleven authenticated paths, and update the count assertions accordingly.

### F5 — The guard must render literally nothing, but "nothing" needs a precise assertion
· **Severity:** **Major**
· **Evidence:** AC8 says a blocked route renders no text at all. L11 says user-facing text comes only from the catalogue.
· **Assessment:** A component that returns `null` renders nothing. A component that returns `<></>` renders nothing. A component that returns `<div />` renders an empty div (no text, but extra DOM). The test must assert `container.textContent === ''` and preferably `container.querySelector('*') === null`.
· **Recommended brief amendment:** Specify that the guard returns `null`, and the test asserts both `textContent` is empty and no element is rendered in the route's outlet container.

### F6 — The guard must not store, write, or send anything
· **Severity:** **Major**
· **Evidence:** AC7. The guard reads `accessToken.current()`.
· **Assessment:** A source scan should forbid `sessionStorage`, `localStorage`, `document.cookie`, `indexedDB`, `fetch`, `XMLHttpRequest`, `sendBeacon`, `console.`, and any write to `accessToken`. The guard should be pure.
· **Recommended brief amendment:** Add a source-scan criterion listing forbidden surfaces, mirroring T37's AC7 `FORBIDDEN_REACHES`.

### F7 — The guard must not use React hooks that introduce state or effects
· **Severity:** **Minor/Major**
· **Evidence:** The guard is a synchronous read. Using `useState`, `useEffect`, `useRef`, or `useMemo` would be unnecessary and could introduce subtle bugs under StrictMode.
· **Assessment:** A source scan should forbid these hooks. The guard can be a simple function component.
· **Recommended brief amendment:** Add `useState`, `useEffect`, `useRef`, `useMemo`, `useCallback`, `useReducer` to the forbidden list.

### F8 — The guard must not import `useNavigate` or perform any navigation
· **Severity:** **Major**
· **Evidence:** AC10 says a sessionless visit to a public path performs no navigation. R28's second clause (redirect) is task 41's.
· **Assessment:** The guard must not navigate. A blocked route renders nothing; task 41 will later wrap or replace the catch-all to redirect.
· **Recommended brief amendment:** Add a source scan forbidding `useNavigate`, `useHref`, `Navigate`, `redirect`, and `location.assign`/`replace`/`href`.

### F9 — The guard must be tested under `StrictMode`
· **Severity:** **Minor**
· **Evidence:** The Boundaries section lists "the guard under `StrictMode`, as every suite here mounts."
· **Assessment:** StrictMode double-renders components. The guard must be idempotent and have no side effects. A test under StrictMode verifies this.
· **Recommended brief amendment:** Add an acceptance criterion or boundary test: render an authenticated route under StrictMode with no session and assert it still renders nothing.

### F10 — The two route sets are already disjoint and exact in `routes.tsx`
· **Severity:** **Minor**
· **Evidence:** AC5. `PUBLIC_ROUTE_PATHS` and `AUTHENTICATED_ROUTE_PATHS` are separate arrays.
· **Assessment:** This is already true. A test can derive the counts from `routes.tsx` and assert they sum to 20 with no overlap. The brief says counts must be derived, not written down.
· **Recommended brief amendment:** Keep AC5 as is; the test should derive counts from the exported arrays.

### F11 — The guard introduces a runtime dependency from `app/routes.tsx` to `auth/`
· **Severity:** **Minor**
· **Evidence:** AC6 says the guard lives in `auth/`; `app/` imports it.
· **Assessment:** This is the correct direction. `app/routes.tsx` already imports `SignInScreen`, `CallbackScreen`, `SignedOutScreen` from `auth/`. Adding `RequireSession` is consistent.
· **Recommendation:** None.

### F12 — The guard must handle `accessToken.current()` returning empty string
· **Severity:** **Minor**
· **Evidence:** Constraints: "`accessToken.current()` returns `string | null`; empty string is not a session."
· **Assessment:** The guard should check `accessToken.current() !== null` or `!!accessToken.current()`. The constraint says empty string is not a session, so a truthiness check is correct.
· **Recommended brief amendment:** Specify the guard condition as `accessToken.current() != null` (allowing empty string to be treated as no session) or `Boolean(accessToken.current())`. Given the constraint, the latter is safer.

### F13 — Authenticated routes currently have no elements, so AC2 is vacuously true today
· **Severity:** **Major**
· **Evidence:** AC3 explicitly notes this.
· **Assessment:** This is why AC3 is the crux. Without a test that fails when the guard is removed, T40 could ship no guard and pass.
· **Recommended brief amendment:** Ensure AC3 is implemented as described in F2.

### F14 — The catch-all route must remain unchanged
· **Severity:** **Minor**
· **Evidence:** Scope says changing the catch-all is out.
· **Assessment:** The catch-all `{ path: '*' }` remains with no element. Task 41 will handle redirect. Tests should assert the catch-all is unchanged.
· **Recommended brief amendment:** Add a test that asserts the catch-all has no element and no guard.

### F15 — Query strings and trailing slashes on public paths
· **Severity:** **Minor**
· **Evidence:** Boundaries section.
· **Assessment:** React Router matches `/app/verify?token=...` to `verify` route. A trailing slash `/app/verify/` may or may not match depending on router config. The test should verify the public path still resolves with query string, and document trailing slash behavior.
· **Recommended brief amendment:** Add boundary tests for `/app/verify?token=x` and `/app/verify/` (if supported).

### F16 — The guard should not be applied to public routes or the catch-all
· **Severity:** **Major**
· **Evidence:** AC1 and AC10.
· **Assessment:** Applying the guard to public routes would block them without a session, violating R28. Applying it to the catch-all would also block it (and task 41 needs the catch-all to redirect).
· **Recommended brief amendment:** Add a source scan or test that asserts `PUBLIC_ROUTE_PATHS` and the catch-all do not have `RequireSession` as their element.

### F17 — No new catalogue entry is needed
· **Severity:** **Minor**
· **Evidence:** Constraints say "No copy."
· **Assessment:** The guard renders nothing, so no strings. Tests should verify `i18n` catalogue is untouched.
· **Recommendation:** None required beyond AC8.

### F18 — The guard must not read the route path or query string
· **Severity:** **Minor**
· **Evidence:** The guard only asks the session owner.
· **Assessment:** A source scan should forbid `useLocation`, `useParams`, `useSearchParams`, `URLSearchParams`, or `globalThis.location` reads.
· **Recommended brief amendment:** Add these to the forbidden list.

---

## Summary

T40 is conceptually simple — add a session guard to authenticated routes — but the design challenge is in the measurement, not the mechanism. The most important gaps are **F1** (the guard shape is unspecified), **F2** (AC3's test design is the real content and is not specified), **F3** (T37's AC9 screen-count pin will fire when guards are added), and **F4** (the sibling instrument must distinguish screens from guards). **F5**–**F8** are the precision issues that make the guard testable. The brief is otherwise clear about scope and boundaries.

**Phase 3 complete.** Eighteen findings returned; no code or contract changed.
