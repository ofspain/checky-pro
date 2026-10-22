# frontend · T02 · Phase 2 — Task Implementation Brief

## Task
T02 — **/app prefix.** Every SPA route resolves under `/app`, and a path outside it is not served by the SPA. Verifies
`routesResolveUnderAppPrefixOnly`. Cites L14.

## Purpose
Put the SPA's whole route set under `/app` and make sure nothing outside it, backend prefixes especially, is ever handled by
the SPA.

## Scope
**In**
- React Router.
- A route module declaring the design.md §4c route set as paths (no screens).
- The router mounted inside `AppShell` with basename `/app`.
- A silent catch-all for unmatched paths under `/app`.
- The `routesResolveUnderAppPrefixOnly` test.

**Out**
- Route screens and components (their own tasks).
- The public/authenticated split and guards (T40–T42).
- The sign-in redirect for unknown routes (T41 replaces the catch-all).
- Return-target allowlist (T24/T25).
- Any edge, Vite or production-hosting change.

## Business Rules
- R28 is not implemented here. The route set it relies on is declared verbatim, so T40/T41 can build on it.

## Locked Decisions
L14 (the route set is verbatim, under `/app`, disjoint from backend prefixes), L13 (React Router), L12 (extend T01,
don't rewrite it), L9, L11.

## Dependencies
- `react-router@8.4.0`, exact. Released 2026-09-15; peers React `>=19.2.7`; Node `>=22.22.0`.
- Route paths: design.md §4c plus its 2026-10-19 note (see Phase 1 Dependencies). There are 20 paths, all relative to
  `/app`.

## Inputs
The browser URL.

## Outputs
The matched route (none outside `/app`).

## State Changes
None.

## Files to Create
- `frontend/src/app/routes.tsx`:
  - `APP_BASENAME = '/app'`;
  - `PUBLIC_ROUTE_PATHS` and `AUTHENTICATED_ROUTE_PATHS` (readonly arrays of §4c paths, relative);
  - `appRoutes: RouteObject[]`, a root route whose children are every path plus a catch-all `*`, with no elements (they
    render nothing);
  - `createAppRouter()`, which returns `createBrowserRouter(appRoutes, { basename: APP_BASENAME })`.
- `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx`.

## Files to Modify
- `frontend/src/app/mountShell.tsx`: render `<AppShell><RouterProvider router={createAppRouter()} /></AppShell>`.
- `frontend/src/app/AppShell.tsx`: accept `children` and render them inside the shell element.
- `frontend/package.json`, `pnpm-lock.yaml`: add `react-router`.

## Files NOT to Modify
- `vite.config.ts`, `index.html`, `main.tsx`.
- `shellBuildsAndMounts.test.tsx`.
- `infra/**`, `spec/**`, `contracts/**`, `services/**`, `libs/**`.
- `prompt`.

## Acceptance Criteria
- **AC1:** each of the 20 paths matches its own route when visited at `/app/<path>` (`:id` routes with a sample UUID).
- **AC2:** the same paths without `/app` match nothing.
- **AC3:** every backend edge prefix matches nothing.
- **AC4:** an unmatched `/app/*` path renders no text (no library default 404).
- **AC5 (live):** `/app/home`, `/app/invoices/<uuid>` and `/app/verify?token=x` return the SPA HTML through the edge;
  `/home` and `/foo` → 404 from the edge.
- **AC6:** `shellBuildsAndMounts` passes unchanged.

## Required Tests
`routesResolveUnderAppPrefixOnly`, using `createMemoryRouter(appRoutes, { basename, initialEntries })`:
1. AC1 for every declared path.
2. AC2.
3. AC3.
4. AC4: render `/app/no-such-route` and assert the shell has no text.
5. The declared set has no duplicates, and no path starts with `/` (all relative to the basename).

Mutation breaks:
- basename removed;
- an absolute path added;
- a catch-all removed, so the default 404 text shows.

## Constraints
- No hard-coded copy.
- No absolute URLs or env (T01 scans for them).
- `strict` TypeScript.
- The route module is the single source of the route set.
- Route elements stay empty until their owning tasks.

## Open Questions
No blockers. Defaults: full route set as paths now; a silent catch-all; React Router 8.4.0.
