# frontend · T01 · Phase 6 — Implementation Notes

Consumes `05-implementation-plan.md` and the FROZEN brief. Date: 2026-10-22.

## What changed

| File | Plan item | Notes |
|---|---|---|
| `package.json` (root, new) | 1 | `private`, `packageManager: pnpm@12.14.0`, `engines.node >=22.12`. No dependencies. Makes `pnpm install` at the repo root work, which the `infra/local` `frontend` service runs. |
| `pnpm-lock.yaml` (new, generated) | 10 | Produced by `pnpm install` in `node:22-alpine` (pnpm 12.14.0 via corepack). 79 packages. |
| `frontend/package.json` (new) | 2 | Exact versions as planned (no ranges). Scripts: `dev` = `vite`, `build` = `tsc --noEmit && vite build`, `test` = `vitest run`, `typecheck` = `tsc --noEmit`. |
| `frontend/index.html` (new) | 3 | `lang="en"`, `<title>Checky Pro</title>` (permitted static metadata, frozen AC4), `#root`, module script `/src/main.tsx`. |
| `frontend/vite.config.ts` (new) | 4 | `base: '/app/'`; `server { host: true, port: 5173, strictPort: true }`; no proxy; React plugin; Vitest `environment: 'jsdom'`. Uses `defineConfig` from `vitest/config`. |
| `frontend/tsconfig.json` (new) | 5 | `strict`, `noEmit`, `jsx: react-jsx`, `moduleResolution: bundler`, `isolatedModules`, `types: ["vite/client"]`. |
| `frontend/src/app/AppShell.tsx` (new) | 8 | `<div data-testid="app-shell" />`. No text, no effects, no props. |
| `frontend/src/app/mountShell.tsx` (new) | 7 | Throws `Error('mountShell: root element #root not found')` on null. Otherwise `createRoot`, then renders `<StrictMode><AppShell/></StrictMode>` and returns the `Root`. |
| `frontend/src/main.tsx` (new) | 6 | `mountShell(document.getElementById('root'))`, nothing else. |
| `.github/workflows/ci.yml` | modify | The `frontend` filter now also covers `package.json`, `pnpm-lock.yaml` and `pnpm-workspace.yaml` (Phase 4 disposition 4). New `frontend` job: Node 22, `corepack enable`, `pnpm install --frozen-lockfile`, `pnpm -C frontend test`, `pnpm -C frontend build`. YAML validated. |
| `frontend/README.md` | modify | Adds the frozen command list (`bin/up --frontend`, `bin/status --wait`, the app URL, `exec frontend pnpm -C frontend test` / `build`). |

No test file yet. The plan places `shellBuildsAndMounts.test.tsx` in Phase 10, and Phase 6 writes no tests.

## Acceptance criteria evidence

- **AC1 (build):** `pnpm -C frontend build` in `node:22-alpine` passed: `tsc --noEmit` clean, then vite 8.3.5 built
  16 modules. `dist/index.html` contains `<script type="module" crossorigin src="/app/assets/index-DaoQXqhR.js">`. The base
  is honoured.
- **AC2 (mount):** proved by the Phase 10 test. Live, the served page loads `/app/src/main.tsx`, which calls `mountShell`.
- **AC3 (no env, network, storage or worker):** by construction. The three source files import only `react`,
  `react-dom/client` and each other, and contain no `import.meta.env`, `fetch`, storage or `serviceWorker`. Phase 10 asserts
  this.
- **AC4 (no shell text):** `AppShell` renders an empty `div`.
- **AC5 (live, through the edge):** `infra/local/bin/up --frontend` from the repo root, after which:
  - `GET http://localhost:5173/app/` → 200 within about 6 s of the container starting. The Vite-served HTML carries
    `/app/@vite/client`, `/app/@react-refresh` and `/app/src/main.tsx`.
  - `/app/src/main.tsx`, `/app/@vite/client`, `/app/src/app/AppShell.tsx` and `/app/@react-refresh` → 200 each.
  - The HMR websocket upgrade through Caddy (`Sec-WebSocket-Protocol: vite-hmr`) → `101`.
  - Editing `AppShell.tsx` (a temporary attribute) showed in the served module within 4 s, and the container logged
    `[vite] (client) hmr update /src/app/AppShell.tsx`. The edit was reverted.
- **AC6 (null guard):** implemented in `mountShell`; Phase 10 tests it.

## Deviations and notes (flagged, not hidden)

1. **`infra/local/bin/up` bug, fixed outside T01.** Its last line found `bin/status` relative to `$0` *after* `cd`, so
   `infra/local/bin/up` run from the repo root failed at the end ("No such file"), although the stack had started. Changed to
   `bin/status --wait` (cwd is `infra/local` by then). The frozen brief puts `infra/**` off-limits for T01, so this is
   committed separately as a local-tooling fix, not as part of the T01 change set.
2. **Install location.** The throwaway install container mounted the repo, so pnpm wrote `node_modules/` and its store
   (`node_modules/.pnpm-store`) into the host checkout. Both were deleted afterwards, as were `frontend/node_modules` and
   `frontend/dist`; all are git-ignored anyway. In the compose service the same paths are named volumes, so the store lives
   in the volume.
3. **TypeScript 7.** The current stable release is the native compiler (7.0.2, released 2026-07-08). `tsc --noEmit` works
   unchanged. Vite strips types itself (oxc/rolldown) and does not depend on `typescript`.
4. **CI before Phase 10.** `vitest run` with no test files exits non-zero, so the new CI job would fail on a commit made
   between Phases 6 and 10. It does not matter on this branch (CI runs on PR and `main` push), and Phase 10 adds the test
   before any PR.
