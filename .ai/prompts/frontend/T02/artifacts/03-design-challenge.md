# frontend · T02 · Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T02 — /app prefix**. Findings are against the brief itself; no code was changed.

---

## Findings

### 1. AC3 test behavior for paths outside `/app` is unspecified
**Issue:** AC3 requires that backend edge prefixes (e.g., `/oauth2/authorize`, `/api/v1/invoices`) “match nothing.” With `createMemoryRouter(appRoutes, { basename: '/app', initialEntries: ['/oauth2/authorize'] })`, React Router’s behavior when the location does not start with the basename is version-specific: it may render nothing, throw, or redirect to basename. The brief does not state what the test should assert as proof of “matches nothing.”  
**Severity:** Medium — risk of a test that is either too weak (passes if the router silently renders the catch-all) or too strict (fails on an internal Router error).  
**Evidence:** Brief §Acceptance Criteria AC3; brief §Required Tests item 3.  
**Recommended brief amendment:** Clarify the assertion: e.g., “rendering `/oauth2/authorize` against the router produces no route match and the shell element has empty text; the router does not throw or redirect.”

### 2. Catch-all element behavior is ambiguous
**Issue:** The brief says route elements stay empty, and AC4 says an unmatched `/app/*` path renders no text. But the brief also says `appRoutes` includes a catch-all `*` with no element. In React Router, a route object with `path: '*'` and no `element`/`Component`/`errorElement` may render nothing, may render the outlet, or may fall through depending on the surrounding route structure. The exact shape of `appRoutes` (a root route with `children`, vs. a flat array) determines this.  
**Severity:** Medium — an incorrectly structured catch-all could either fail to suppress the library default 404 or could hide real routes.  
**Evidence:** Brief §Files to Create: `appRoutes: RouteObject[]`, “a root route whose children are every path plus a catch-all `*`, with no elements”; brief §AC4.  
**Recommended brief amendment:** Show the exact route-object shape, e.g.:
```ts
const appRoutes: RouteObject[] = [
  {
    path: '/',
    element: null,
    children: [
      ...PUBLIC_ROUTE_PATHS.map(path => ({ path, element: null })),
      ...AUTHENTICATED_ROUTE_PATHS.map(path => ({ path, element: null })),
      { path: '*', element: null },
    ],
  },
];
```
Or state explicitly that the catch-all is a sibling of the other leaf routes under the basename root.

### 3. Exact meaning of “no library default 404” is not defined
**Issue:** AC4 says an unmatched `/app/*` path renders no text and “no library default 404.” React Router does not ship with a built-in 404 page; the phrase likely means no fallback text from the application code. However, if route elements are all `null`, there is no 404 text regardless. The requirement could be clearer.  
**Severity:** Low — cosmetic, but may confuse implementers.  
**Evidence:** Brief §AC4.  
**Recommended brief amendment:** Rephrase AC4 as: “An unmatched `/app/*` path resolves to the catch-all route and renders no text; there is no visible fallback UI.”

### 4. No ordering rule for overlapping prefixes
**Issue:** The route set includes `register` and `register/sent`, `reset` and `reset/request`, `account`, `account/sessions`, `account/mfa`, `invoices` and `invoices/:id`. React Router matches exact paths, so order does not matter for exact routes, but the brief does not state that routes must be exact or that order is irrelevant. A future edit could introduce a parameterised route that accidentally shadows a static one.  
**Severity:** Low — not a bug today, but an unstated invariant.  
**Evidence:** Brief §Files to Create: `appRoutes` children are “every path plus a catch-all `*`”.  
**Recommended brief amendment:** Add a constraint: “All route paths are exact (no trailing wildcard except the final catch-all), and static paths are listed before parameterised ones.”

### 5. Test for AC1 on `:id` routes uses an unspecified sample UUID
**Issue:** AC1 says “`:id` routes with a sample UUID.” The brief does not specify whether the UUID must be valid, what version, or whether the test should generate it programmatically. A hard-coded placeholder like `00000000-0000-0000-0000-000000000000` might pass route matching but could be confused with a real test value.  
**Severity:** Low — functional, but could lead to inconsistent tests.  
**Evidence:** Brief §AC1.  
**Recommended brief amendment:** Specify: “Use `crypto.randomUUID()` or a literal RFC-4122 v4 UUID (e.g., `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`). The value is not validated by the router; it only exercises the `:id` segment.”

### 6. Dependency name vs import source is ambiguous
**Issue:** The brief lists the dependency as `react-router@8.4.0`, but the React Router data APIs (`createBrowserRouter`, `RouterProvider`) are typically imported from `react-router-dom` in browser apps. The brief’s code examples use `createBrowserRouter` and `RouterProvider` without stating the import package.  
**Severity:** Low — likely `react-router-dom`, but a mismatch could cause install/import confusion.  
**Evidence:** Brief §Dependencies: `react-router@8.4.0`; brief §Files to Create: `createAppRouter()` returns `createBrowserRouter(...)`.  
**Recommended brief amendment:** State the import package explicitly, e.g., `import { createBrowserRouter, RouterProvider, createMemoryRouter } from 'react-router-dom';` and confirm the dependency is `react-router-dom@8.4.0` (which re-exports `react-router`).

### 7. `/app` (basename root with no trailing path) behavior is unspecified
**Issue:** The route set contains `/app/home` but no route at `/app`. When a user visits `http://localhost:5173/app/`, the router will match the root route under the basename and then the catch-all `*`. AC4 covers unmatched paths, but it is worth stating explicitly that `/app/` itself is not the home route.  
**Severity:** Low — avoids later tasks assuming `/app` maps to home.  
**Evidence:** Brief §AC1 lists paths relative to `/app`; no path is `''` or `'/'`.  
**Recommended brief amendment:** Add a note: “The basename root `/app/` is intentionally not a route; it falls through to the catch-all and renders no text. T41 will own redirects.”

### 8. T02 assumes T01 implementation shape that may not exist
**Issue:** The brief modifies `frontend/src/app/mountShell.tsx` and `frontend/src/app/AppShell.tsx`, assuming they follow the T01 frozen brief names and signatures. If T01 was implemented differently (e.g., no `mountShell` abstraction, `App.tsx` wrapper, different file names), T02 cannot be applied cleanly without first reconciling T01.  
**Severity:** Medium — ordering/dependency hazard.  
**Evidence:** Brief §Files to Modify: `mountShell.tsx`, `AppShell.tsx`; current repo state (from T01 review) does not match those files.  
**Recommended brief amendment:** Add a dependency note: “T02 assumes T01 landed exactly as specified in `T01/artifacts/04-frozen-task-brief.md`. Any T01 deviation must be resolved before T02 begins.”

### 9. No guidance on how `AppShell` accepts children
**Issue:** The brief says `AppShell` should accept `children` and render them inside the shell element, but it does not specify the prop type or whether `children` is optional. The T01 test `shellBuildsAndMounts` renders `AppShell` without children (via `mountShell`), so `children` must be optional to keep AC6 passing.  
**Severity:** Low — type-level detail.  \**Evidence:** Brief §Files to Modify: `AppShell.tsx`: “accept `children` and render them inside the shell element.”  
**Recommended brief amendment:** Specify the signature: `export function AppShell({ children }: { children?: React.ReactNode }): JSX.Element` and render `<div data-testid="app-shell">{children}</div>`.

### 10. `routesResolveUnderAppPrefixOnly` test uses memory router but AC5 is live
**Issue:** The automated test uses `createMemoryRouter`, which does not exercise the browser history integration that `createBrowserRouter` uses. AC5 covers the live edge behavior, but there is no automated test that `createAppRouter()` actually returns a browser router configured with the correct basename.  
**Severity:** Low — the live check catches it, but a regression in `createAppRouter` (e.g., switching to `createMemoryRouter` accidentally) would not be caught by the unit test.  
**Evidence:** Brief §Files to Create: `createAppRouter()` returns `createBrowserRouter(appRoutes, { basename: APP_BASENAME })`; Required Tests use `createMemoryRouter`.  
**Recommended brief amendment:** Add a minimal test case: `createAppRouter()` returns an object whose `basename` option is `/app` and whose history type is browser (if introspectable), or assert that `window.location.pathname` is respected when rendering with `RouterProvider` in jsdom.

---

## Open Questions

- None that block freezing, provided the ambiguity in findings 1, 2, and 6 is resolved.
