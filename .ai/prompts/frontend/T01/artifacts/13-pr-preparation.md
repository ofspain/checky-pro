# frontend · T01 · Phase 13 — PR / Commit Preparation

Gate: Phase 12 verdict is **PASS**.

Branch note: per the developer's standing instruction, work lands on `spec/service-specs-and-ai-framework` and is pushed after
each phase. For `main`, merge or squash this branch's final tree; `main` stays deployable because the frontend is additive
and the CI job is path-gated. Don't replay `2ee1c1be`/`cbfad357` on their own (see Phase 12, risks).

## Commit title
`frontend: app shell builds and mounts under /app (T01)`

## Commit message
```
frontend: app shell builds and mounts under /app (T01)

Add the frontend skeleton every later task extends:

- Root package.json as the pnpm workspace root (pnpm 12.14.0,
  Node >= 22.22.2) and the lockfile.
- frontend/: Vite 8 + React 19 + TypeScript 7 (strict), base /app/,
  dev server on 5173 for the infra/local edge with no proxy.
- mountShell renders an empty AppShell into #root under StrictMode and
  throws when #root is missing; main.tsx only calls it.
- shellBuildsAndMounts (Vitest + jsdom): production build with every
  script under /app/ and no service worker, dev-server contract, empty
  mount, no fetch/storage/worker, no env key or absolute URL in source,
  entry boundary, missing-root error.
- CI: frontend job (frozen install, test, build); the path filter also
  covers the root pnpm files.

No router, worker, i18n, lint or env key yet; later tasks own them.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```
(The template's trailer names "Claude Opus 4.8 (1M context)"; the repo's standing attribution rule uses the line above.)

## Files changed
- `package.json` (new)
- `pnpm-lock.yaml` (new)
- `.gitignore` (`.pnpm-store/`)
- `.github/workflows/ci.yml` (`frontend` filter and job)
- `frontend/README.md` (run/test commands)
- `frontend/package.json`, `frontend/index.html`, `frontend/vite.config.ts`, `frontend/tsconfig.json` (new)
- `frontend/src/main.tsx`, `frontend/src/app/mountShell.tsx`, `frontend/src/app/AppShell.tsx` (new)
- `frontend/src/app/shellBuildsAndMounts.test.tsx` (new)
- Separately committed tooling fix: `infra/local/bin/up`.

## Summary
The first frontend task. It sets up the workspace and the Vite + React + TypeScript app that later tasks extend (L12). The
shell renders nothing visible yet: no copy (L11), no routes (T02), no backend calls (L9), no env keys (L10), no worker
(L17, later). It is served at `/app/` through the local edge (L14), with hot reload.

## Testing performed
- In the `infra/local` `frontend` container (Node 22, pnpm 12.14.0):
  - `pnpm install --frozen-lockfile`: OK.
  - `pnpm -C frontend test`: 7/7 passed.
  - `vitest run --root frontend` from the repo root: 7/7 passed.
  - `typecheck`: clean. `build`: clean.
- Mutation breaks, 13 in total, each failing exactly its intended case:
  - the shell not rendered;
  - `base: '/'`;
  - the null guard removed;
  - `fetch` added;
  - an env key read;
  - shell text;
  - a proxy added;
  - the port changed;
  - an absolute URL;
  - an extra import in `main`;
  - an extra statement in `main`;
  - whitespace text;
  - worker registration.
- Live, through the Caddy edge (`infra/local/bin/up --frontend`):
  - `GET /app/` and every module script → 200;
  - the HMR websocket upgrade → 101;
  - an edit hot-reloaded within seconds;
  - re-checked after the Phase 9 restore.
- Reviews: Kimi Phase 3 (10 findings), Phase 8 (13, all resolved by restoring the frozen tree), Phase 11 (8 gaps, 7 added).

## Specification references
- `spec/frontend/tasks.md` task 1 (T01, Shell renders).
- Named test: `shellBuildsAndMounts` (`package.md` §8 → L13).
- LOCKED: L13 (cited); also upheld: L9, L10, L11, L12, L14.
- Requirements: none cited by the task; R58 is not violated (T13 owns its proof).
