# frontend · T02 · Phase 0 — Repository Understanding

Task: **T02 — /app prefix.** "Every SPA route resolves under `/app`, and a path outside it is not served by the SPA.
Verifies `routesResolveUnderAppPrefixOnly`. Cites L14." Date: 2026-10-22.

## 1. Architecture summary (after T01, `0d2b6e2e`)

- **Workspace and versions.**
  - Root `package.json` is the pnpm 12.14.0 workspace root (Node `>=22.22.2`).
  - `frontend/` is Vite 8.3.5 + React 19.3.1 + TypeScript 7.0.2 (strict), with Vitest 5.0.5 + jsdom 30.1.3 and
    `@types/node` 22.20.5 (test only).
- **Entry and shell.**
  - `src/main.tsx` only calls `mountShell(document.getElementById('root'))`.
  - `src/app/mountShell.tsx` creates a React root and renders `<StrictMode><AppShell/></StrictMode>`, throwing if the
    container is null.
  - `src/app/AppShell.tsx` renders `<div data-testid="app-shell" />`.
- **No router exists yet.** T01 kept it out of scope on purpose; L13 names React Router as the confirmed router.
- **Serving.**
  - `vite.config.ts` has `base: '/app/'` and `server { host: true, port: 5173, strictPort: true }`, with no proxy.
  - Vite's default `appType: 'spa'` already answers unknown `/app/*` paths with `index.html`. Checked live through the edge:
    `/app/home` and `/app/invoices/abc` → 200.
  - The edge (Caddy, `infra/local/edge/Caddyfile`) sends only `/app*` to the dev server. Backend prefixes go to their
    services, `/` redirects to `/app/`, and everything else is 404 (`/notaroute` → 404, checked).
- **Tests.**
  - `src/app/shellBuildsAndMounts.test.tsx` (7 cases) covers the build under `/app/`, the dev-server contract, an empty
    mount (`innerHTML === ''` inside `app-shell`), no fetch/storage/worker, no env key or absolute URL in the source, the
    entry boundary, and the null root.
  - Tests run in the `infra/local` `frontend` container. CI job: `frontend`.

## 2. Existing code this task touches

| Item | State | T02 relevance |
|---|---|---|
| `src/app/mountShell.tsx` | exists | must start rendering the router |
| `src/app/AppShell.tsx` | exists | the router must render inside it, keeping T01's empty-shell assertion true when nothing matches (L12) |
| `src/app/shellBuildsAndMounts.test.tsx` | exists (T01) | must keep passing unchanged (L12: later tasks don't rewrite earlier ones) |
| `frontend/package.json` | exists | gains `react-router` |
| Route table / router module | none | new |

## 3. Established patterns (from T01)

- Exact dependency versions, resolved in `node:22-alpine`, and a version at least ~2 weeks old.
- Code lives in `src/app/`; components are PascalCase, with tests co-located next to them; there is one named test per task
  (`describe('<name>')` with several cases).
- No visible copy outside the catalogue (L11). No env keys (L10). No absolute URLs (L9; T01 scans for them).
- Each production change gets a mutation break; live checks go through the edge.
- After any dependency change, restart the `frontend` container (pnpm re-links paths; `KIMI-LOCAL-STACK.md` §7).

## 4. Testing conventions

Vitest + jsdom, `act()` from React (`IS_REACT_ACT_ENVIRONMENT`). jsdom's default URL is `http://localhost:3000/`, which is
outside `/app`. Router tests therefore need a memory router or an explicit URL.

## 5. Known gaps / unknowns

1. **Router version.** React Router 8.5.0 is 3 days old (2026-10-19); 8.4.0 is from 2026-09-15. Both need React `>=19.2.7`
   and Node `>=22.22.0`, and both are satisfied.
2. **Unknown path under `/app` (e.g. `/app/nope`).** T41 owns it (redirect to sign-in, R28). Until then, React Router's
   default 404 error element would render hard-coded English, which conflicts with L11. I do not know if T02 should neutralise
   this; it's a Phase 1/2 call.
3. **Route elements.** Screens for each route belong to their own tasks. Whether T02 registers the whole §4c route set or
   only the prefix mechanism is not stated by the task.
4. **Production hosting** (CloudFront rewrite of `/app/*` to `index.html`) is not in the repo. `infra/stacks` is empty.
   I do not know who owns it. It is outside this task.
