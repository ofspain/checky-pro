# frontend · T02 · Phase 4 — Frozen Task Brief (Human Approval gate)

STATUS: FROZEN (2026-10-22)

Consumes `02-task-implementation-brief.md` and `03-design-challenge.md` (Kimi 2.7: 10 findings, Low/Medium, no blocker).
Each finding was checked against the repository and against `react-router@8.4.0` itself. A scratch probe ran the real
package, with React 19.3.1 and jsdom 30.1.3, before any finding was dispositioned. None is an architectural trade-off, so
they are settled here, as at the T01 gate.

## What the probe showed (react-router 8.4.0)

- **Exports.** `react-router` exports `RouterProvider`, `createBrowserRouter`, `createMemoryRouter` and `matchRoutes`.
- **`react-router-dom@8.4.0` does not exist** in the registry (404). Since v7 the router is a single package.
- **A location outside the basename** (`/home`, `/oauth2/authorize`):
  - `matchRoutes(routes, path, '/app')` returns `null`;
  - the router holds a 404 at the root, renders nothing, and logs a console warning;
  - it neither throws nor redirects.
- **An unmatched `/app/*` path:**
  - with a `*` child, it matches `['/', '*']` and renders nothing;
  - **without** the catch-all, React Router's default error element renders "Unexpected Application Error! 404 Not
    Found". That is the library default copy AC4 forbids, so the "catch-all removed" mutation break is real.
- **Bare `/app`** matches the root route only: no error, nothing rendered.
- **`createBrowserRouter(routes, { basename: '/app' })`** exposes `.basename === '/app'` and `.routes`.

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| 1 | AC2/AC3 assertion unspecified | **ACCEPTED.** "Matches nothing" means `matchRoutes(appRoutes, path, APP_BASENAME) === null`. It is a pure call: nothing is rendered and no warning is logged. This is the probed behaviour. |
| 2 | Catch-all shape ambiguous | **ACCEPTED.** The exact shape is fixed below: a root `{ path: '/' }` route with no element, so it renders an outlet. Its children are one leaf per declared path, plus `{ path: '*' }` last. Leaves have no element. |
| 3 | "Library default 404" undefined | **ACCEPTED (reworded); premise corrected.** React Router does ship a default error element; the probe showed its "404 Not Found" text. AC4 now reads: an unmatched `/app/*` path resolves to the catch-all and the shell renders no text. |
| 4 | Overlapping-prefix ordering | **PARTLY ACCEPTED.** React Router ranks matches by specificity, not declaration order, so an ordering rule would add nothing and is not adopted. Adopted instead: declared paths contain no `*`, and the catch-all is the only wildcard. The test asserts this. |
| 5 | Sample UUID unspecified | **ACCEPTED.** The test uses one literal RFC 4122 v4 UUID constant for every `:id` segment. |
| 6 | `react-router-dom` vs `react-router` | **REJECTED.** `react-router-dom@8.4.0` is not published. The dependency is `react-router@8.4.0`, and every import comes from `'react-router'`. |
| 7 | Bare `/app/` behaviour | **ACCEPTED (note).** `/app/` is not a route. It matches only the root, renders nothing, and is not `home`. Redirects belong to T41. |
| 8 | T01 shape may not exist | **REJECTED (does not reproduce).** T01 landed as frozen and was restored in its Phase 9. `git diff 9f7aca56 -- frontend` shows only the later test-file changes. `mountShell.tsx` and `AppShell.tsx` exist with the frozen signatures. |
| 9 | `AppShell` children type | **ACCEPTED.** `AppShell({ children }: { children?: ReactNode })` renders `<div data-testid="app-shell">{children}</div>`. `children` is optional. |
| 10 | `createAppRouter` not tested | **ACCEPTED.** A test case calls `createAppRouter()`, asserts `.basename === '/app'` and `.routes` deep-equal to `appRoutes`, then calls `dispose()`. |

Open Questions: none. The Phase 2 defaults are confirmed:
- the full route set is declared as paths now;
- the catch-all is silent;
- `react-router@8.4.0`, because 8.5.0 (2026-10-19) is too new under the two-week rule.

---

## Frozen brief

### Task
T02 — **/app prefix.** Every SPA route resolves under `/app`, and a path outside it is not served by the SPA. Verifies
`routesResolveUnderAppPrefixOnly`. Cites L14.

### Purpose
Put the SPA's whole route set under `/app`, and make sure nothing outside it is ever handled by the SPA, especially the
backend prefixes.

### Scope
**In**
- `react-router@8.4.0`.
- A route module that declares the design.md §4c route set as paths only, with no screens.
- The router mounted inside `AppShell`, with basename `/app`.
- A silent catch-all for unmatched paths under `/app`.
- `routesResolveUnderAppPrefixOnly`.

**Out**
- Route screens (each has its own task).
- The public/authenticated guards (T40–T42).
- The sign-in redirect for unknown routes, including bare `/app/` (T41).
- The return-target allowlist (T24/T25).
- Any change to the edge, Vite config or production hosting.

### Locked decisions
- **L14:** the route set is verbatim, under `/app`, and disjoint from the backend prefixes.
- **L13:** React Router.
- **L12:** extend T01; don't rewrite it.
- **L9.**
- **L11:** no copy.

### Dependency
`react-router@8.4.0`, pinned exactly:
- released 2026-09-15;
- peers: `react` and `react-dom` `>=19.2.7` (19.3.1 is installed);
- engines: Node `>=22.22.0` (the root floor is `>=22.22.2`).

### Route set (relative to `/app`; 20 paths)
- **Public (9):** `sign-in`, `callback`, `register`, `register/sent`, `verify`, `resend-verification`, `reset/request`,
  `reset`, `signed-out`.
- **Authenticated (11):** `home`, `account`, `account/sessions`, `account/mfa`, `api-keys`, `admin`, `invoices`,
  `invoices/:id`, `payments`, `notifications`, `receipts/:id`.

Not routes (deferred names): `evidence`, `disputes`, `reputation`, `fraud`.

### Files to create
**`frontend/src/app/routes.tsx`**
- `export const APP_BASENAME = '/app'`.
- `export const PUBLIC_ROUTE_PATHS` and `export const AUTHENTICATED_ROUTE_PATHS`: readonly (`as const`) arrays of the
  paths above, in that order.
- `export const appRoutes: RouteObject[]`, with exactly this shape:
  ```ts
  [{ path: '/', children: [
      ...PUBLIC_ROUTE_PATHS.map((path) => ({ path })),
      ...AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })),
      { path: '*' },
  ] }]
  ```
- `export function createAppRouter()`, which returns `createBrowserRouter(appRoutes, { basename: APP_BASENAME })`.
- All imports come from `'react-router'`.

**`frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx`**

### Files to modify
- **`frontend/src/app/mountShell.tsx`:** render
  `<StrictMode><AppShell><RouterProvider router={createAppRouter()} /></AppShell></StrictMode>`.
- **`frontend/src/app/AppShell.tsx`:** take optional `children` and render them inside the shell `div` (finding 9).
- **`frontend/package.json`, `pnpm-lock.yaml`:** add `react-router` `8.4.0` (exact) to `dependencies`.

### Files not to modify
- `vite.config.ts`, `index.html`, `main.tsx`, `shellBuildsAndMounts.test.tsx`.
- `infra/**`, `spec/**`, `contracts/**`, `services/**`, `libs/**`.
- `prompt`.

### Acceptance criteria
- **AC1:** each of the 20 paths matches its own leaf when visited at `/app/<path>`, with the sample UUID in each `:id`
  segment. The last match's `route.path` equals the declared path.
- **AC2:** the same 20 paths without `/app` (`/<path>`) give `matchRoutes(...) === null`.
- **AC3:** every backend edge prefix gives `matchRoutes(...) === null`. The prefixes are `/oauth2`, `/login`, `/logout`,
  `/connect`, `/.well-known`, `/userinfo`, `/error`, `/accounts`, `/admin`, `/api-keys`, `/api/v1`, `/notifications`,
  each with a representative sub-path (for example `/oauth2/authorize`, `/api/v1/invoices`).
- **AC4:** `/app/no-such-route`, rendered through a memory router inside `AppShell`, matches the `*` route, and the shell
  renders no text (`textContent === ''`).
- **AC5 (live, recorded):**
  - `/app/home`, `/app/invoices/<uuid>` and `/app/verify?token=x` through the edge return the SPA HTML;
  - `/home` and `/foo` return 404 from the edge.
- **AC6:** `shellBuildsAndMounts` passes unchanged.

### Required tests: `routesResolveUnderAppPrefixOnly`
One `describe` with these cases:
1. AC1 for every declared path.
2. AC2.
3. AC3.
4. AC4.
5. The declared set has 20 unique paths, none starts with `/`, and none contains `*`.
6. `createAppRouter()` has `.basename === '/app'` and `.routes` deep-equal to `appRoutes`, and is disposed afterwards
   (finding 10).

### Mutation breaks (each must fail its case)
- The basename removed from `createAppRouter`.
- An absolute path declared.
- The catch-all removed, so the default 404 text shows.
- A backend prefix (for example `admin` at the root) made resolvable outside `/app`.

### Constraints
- No copy.
- No absolute URLs and no env keys (T01's scan covers `routes.tsx`).
- TypeScript `strict`.
- `routes.tsx` is the single source of the route set.
- Route elements stay empty until their owning tasks.
