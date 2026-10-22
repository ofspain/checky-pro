# frontend · T02 · Phase 10 — Test Generation (test manifest)

Consumes the FROZEN brief and `09-review-resolution.md`. Date: 2026-10-22. No production code was changed in this phase.
The L13 test runner is Vitest with jsdom.

## Test file
`frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` contains one `describe('routesResolveUnderAppPrefixOnly')`.
It is the named test (`package.md` §8 → L14), with six cases.

| # | Case | Verifies |
|---|---|---|
| 1 | Every declared path, at `/app/<path>` with `:id` → a literal v4 UUID, resolves through `matchRoutes(appRoutes, …, APP_BASENAME)`; the last match's `route.path` equals the declared path. | AC1 |
| 2 | The same 20 paths at `/<path>` give `matchRoutes(...) === null`. | AC2 |
| 3 | 20 backend locations give `null`: each edge prefix bare and, where useful, with a sub-path (`/oauth2/authorize`, `/connect/logout`, `/.well-known/openid-configuration`, `/accounts/me`, `/admin/accounts`, `/api/v1/invoices`, `/notifications/preferences`, …). | AC3, L9 |
| 4 | A memory router at `/app/no-such-route`, rendered inside `AppShell` under `act()`: the matches are `['/', '*']`, `state.errors` is `null`, and the shell's `textContent === ''`. | AC4, L11 |
| 5 | 9 public plus 11 authenticated paths, 20 unique; none starts with `/` and none contains `*`; the route tree is exactly `/` → the 20 leaves in order → `*`. | L14, Kimi phase 3 findings 2 and 4 |
| 6 | `createAppRouter()` has `.basename === '/app'`, and its route tree has the same paths as `appRoutes`. It is disposed in `afterEach`. | Kimi phase 3 finding 10 |

Every mounted root is unmounted and every router disposed in `afterEach`.

**Recorded deviation (case 6).** The frozen brief says `.routes` "deep-equal to `appRoutes`". In fact a router's `.routes`
are React Router's processed data routes: copies with added `id` and `hasErrorBoundary` fields (see the Phase 9
`convertRoutesToDataRoutes` note). So they can never deep-equal the input. The case compares the route trees by path,
which is what the finding asked for: the router is built from the declared routes. It is not a weaker check, because
case 5 pins those paths exactly.

## Results (in the `infra/local` `frontend` container: Node 22, pnpm 12.14.0)
- `pnpm -C frontend typecheck`: clean.
- `pnpm -C frontend test`: **13 passed (13)**. That is `routesResolveUnderAppPrefixOnly` 6/6 and `shellBuildsAndMounts`
  7/7 (AC6, unchanged).
- Expected stderr noise:
  - case 4 logs React Router's "leaf route … does not have an element" warning, because elements stay empty until their
    owning tasks;
  - `shellBuildsAndMounts` logs the basename warning from Phase 6.

## Mutation breaks (each applied alone to `routes.tsx`, then restored from a backup; `diff` confirmed the restore)
| Break | Failed cases | Result |
|---|---|---|
| a) basename removed from `createAppRouter` | 6 | 1 failed, 5 passed |
| b) absolute path declared (`'home'` → `'/home'`) | 1, 5 | 2 failed, 4 passed |
| c) catch-all removed | 4 (default 404), 5 | 2 failed, 4 passed |
| d) prefix widened to the root (`APP_BASENAME = '/'`): every route and backend prefix resolves outside `/app` | 1, 2, 3, 6 | 4 failed, 2 passed |
| e) deferred name added (`'disputes'`) | 5 | 1 failed, 5 passed |

All four breaks in the frozen brief, plus extra break e, are caught. Break d is how the brief's "a backend prefix becomes
resolvable outside `/app`" is realised: route paths are relative to the basename, so the only way to make `/admin`
resolve is to widen the prefix.
