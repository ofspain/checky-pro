# frontend · T02 · Phase 7 — Self-Review

Date: 2026-10-22. Reviews the Phase 6 change against the frozen brief.

| # | Severity | Observation | Proposed handling |
|---|---|---|---|
| 1 | Low | `shellBuildsAndMounts` now prints a React Router basename warning, because jsdom's URL `/` is outside `/app`. The test is frozen, so it can't set the URL. | Accept as noise. A later task that touches that test may set the jsdom URL to `/app/`. Not T02. |
| 2 | Low | The bundle is now 313 kB (98.7 kB gzipped) with `react-router`; the size before it was not measured. | Accept. L13 mandates the router; code-splitting comes with the screens. |
| 3 | Info | `createAppRouter()` runs inside `mountShell`, so each mount creates a browser router, which is never disposed when the root unmounts. Only `main.tsx` mounts, once per page load. | No change. The test's case 6 disposes its own router. |
| 4 | Info | Bare `/app/` matches only the root and renders nothing (frozen finding 7). | No change. T41 owns the redirect. |
| 5 | Info | The `RouteObject` leaves are typed through `appRoutes: RouteObject[]`; the path arrays stay `as const` for later typed navigation. | No change. |
| 6 | Info | No copy, no absolute URL and no env key in `routes.tsx`. T01's source scan covers the file and passes. | — |

No blocker. The change matches the brief's Files sections exactly. The named test and the mutation breaks follow in
Phase 10.
