# frontend · T02 · Phase 6 — Implementation Notes

Date: 2026-10-22. Implements `04-frozen-task-brief.md`, following `05-implementation-plan.md`.

## Changes
- **`frontend/package.json` and `pnpm-lock.yaml`:** `react-router` `8.4.0`, pinned exactly. It was added with
  `pnpm -C frontend add react-router@8.4.0 --save-exact` in the `frontend` container (pnpm 12.14.0). The container was then
  restarted, as the handoff note requires after a dependency change.
- **`frontend/src/app/routes.tsx` (new):**
  - `APP_BASENAME`;
  - `PUBLIC_ROUTE_PATHS` (9) and `AUTHENTICATED_ROUTE_PATHS` (11), `as const`;
  - `appRoutes`, in exactly the frozen shape: a root `/` route, one leaf per path, then `*`, with no elements;
  - `createAppRouter()`.

  Everything is imported from `'react-router'`.
- **`frontend/src/app/AppShell.tsx`:** `children?: ReactNode` is rendered inside `<div data-testid="app-shell">`.
- **`frontend/src/app/mountShell.tsx`:** it renders `<AppShell><RouterProvider router={createAppRouter()} /></AppShell>`
  under `StrictMode`. The signature and the null guard are unchanged.

Not touched: `vite.config.ts`, `index.html`, `main.tsx`, `shellBuildsAndMounts.test.tsx`.

## Verification (in the `infra/local` `frontend` container)
- `pnpm -C frontend typecheck`: clean.
- `pnpm -C frontend test` (`shellBuildsAndMounts`, unchanged): **7/7 passed** (AC6).
  - The Phase 5 risk was confirmed and is harmless. jsdom's URL `/` is outside `/app`, so the browser router renders
    nothing and logs React Router's "does not start with the basename" warning on stderr, twice per mounting case because
    of StrictMode.
  - The `innerHTML === ''` assertion and the no-fetch/storage/worker assertions hold.
- `pnpm -C frontend build`: clean. The single bundle is 312.6 kB, 98.7 kB gzipped; it was 190 kB before the router.

## Live checks through the edge (`http://localhost:5173`), AC5
| Request | Status | SPA HTML |
|---|---|---|
| `/app/` | 200 | yes |
| `/app/home` | 200 | yes |
| `/app/invoices/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11` | 200 | yes |
| `/app/verify?token=x` | 200 | yes |
| `/app/admin` | 200 | yes |
| `/home` | 404 (edge) | no |
| `/foo` | 404 (edge) | no |
| `/admin` | 401 (auth-service) | no |

`/admin` vs `/app/admin` is exactly the disjointness L14 requires: the same name reaches the backend outside the prefix and
the SPA inside it.

## Deviations
None.
