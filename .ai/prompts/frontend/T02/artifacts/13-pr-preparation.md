# frontend · T02 · Phase 13 — PR / Commit Preparation

Gate: Phase 12 verdict is **PASS**.

Branch note: per the developer's standing instruction, work lands on `spec/service-specs-and-ai-framework` and is pushed
after each phase. For `main`, merge or squash this branch's final tree. The change is additive and the CI `frontend` job is
path-gated.

## Commit title
`frontend: SPA routes resolve under /app only (T02)`

## Commit message
```
frontend: SPA routes resolve under /app only (T02)

Put the design.md 4c route set under /app with React Router:

- react-router 8.4.0, exact (react-router-dom 8.x isn't published; the
  router is a single package since v7).
- src/app/routes.tsx: APP_BASENAME, the 9 public and 11 authenticated
  paths (relative, as const), appRoutes (root, one leaf per path, then a
  silent catch-all so React Router's default 404 copy never renders) and
  createAppRouter() (browser router, basename /app).
- AppShell takes optional children; mountShell renders the router inside it.
- routesResolveUnderAppPrefixOnly (9 cases): every route resolves under
  /app and none without it, no backend edge prefix resolves, emailed
  ?token= links keep their query, unmatched and bare /app render nothing,
  the route set is exact with no screens, and createAppRouter is a browser
  router on /app.

No screens, guards or redirects yet; T40-T42 and the screen tasks own them.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed
- `frontend/package.json`, `pnpm-lock.yaml` (`react-router` `8.4.0`)
- `frontend/src/app/routes.tsx` (new)
- `frontend/src/app/AppShell.tsx` (optional `children`)
- `frontend/src/app/mountShell.tsx` (`RouterProvider` inside `AppShell`; router-lifetime comment)
- `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` (new)

## Summary
The second frontend task gives the SPA its route set: 20 paths from design.md §4c, all under `/app` (L14). The prefix is
what keeps SPA routes like `/app/admin` and `/app/notifications` apart from the backend's `/admin` and `/notifications`.
Unknown `/app` paths, and bare `/app/`, render nothing for now. T41 replaces that with the sign-in redirect. Nothing is
visible yet: each route gets its screen in its own task.

## Testing performed
In the `infra/local` `frontend` container (Node 22, pnpm 12.14.0):
- `pnpm install --frozen-lockfile`: OK.
- `typecheck`: clean. `build`: clean.
- `pnpm -C frontend test`: **16/16 passed** (`routesResolveUnderAppPrefixOnly` 9, `shellBuildsAndMounts` 7, unchanged).

Mutation breaks: 10 in total, each caught.
- basename removed;
- absolute path;
- catch-all removed;
- prefix widened to `/`;
- deferred name added;
- memory router swapped in;
- screen attached;
- split changed;
- `/admin` declared absolute;
- `index` route under the root.

Live, through the Caddy edge:
- `/app/home`, `/app/invoices/<uuid>`, `/app/verify?token=x` → 200 (SPA);
- `/home`, `/foo` → 404;
- `/admin` → auth-service (401).

Reviews:
- Kimi Phase 3: 10 findings (7 accepted, 1 partly accepted, 2 rejected after checking against the real package).
- Kimi Phase 8: 10 items (no defects; 1 comment added).
- Kimi Phase 11: 7 gaps (6 added, one with a corrected expectation; 1 covered by a recorded break).

## Specification references
- `spec/frontend/tasks.md` task 2 (T02, /app prefix).
- Named test: `routesResolveUnderAppPrefixOnly` (`package.md` §8 → L14).
- LOCKED: L14 (cited); also upheld: L9, L11, L12, L13.
- Related, not implemented here: R28 (T40/T41 build on this route set).
