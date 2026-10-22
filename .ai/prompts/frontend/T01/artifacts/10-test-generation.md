# frontend · T01 · Phase 10 — Test Generation (test manifest)

Consumes the FROZEN brief and `09-review-resolution.md`. Date: 2026-10-22.

The backend conventions in the phase prompt (JUnit, fixed `Clock`, Testcontainers) don't apply to a browser SPA. The L13 test
runner is Vitest. No production code was changed in this phase.

## Test file

`frontend/src/app/shellBuildsAndMounts.test.tsx`: one `describe('shellBuildsAndMounts')` (the named test, `package.md`
§8 → L13) with five cases.

| # | Case | Verifies |
|---|---|---|
| 1 | `builds for production with every asset under /app/ (L13, L14)`: checks the working directory is `@themistra/frontend`, runs Vite `build()` with the project config into a temp dir, and asserts `index.html` has a module script at `/app/assets/*.js` and none at `/assets/` | AC1, L13, L14 |
| 2 | `mounts the empty shell into the container (L13, L11)`: `mountShell` into a fresh `div` under `act()`; `[data-testid="app-shell"]` exists, with `textContent === ''` | AC2, AC4, L11 |
| 3 | `makes no network call, writes no storage and registers no service worker (L9, L10, R58)`: spies on `fetch` and `Storage.prototype.setItem` and stubs `navigator.serviceWorker.register`, mounts, and expects none called | AC3 |
| 4 | `reads no environment key anywhere in the app source (L10, R58)`: every non-test `.ts`/`.tsx` under `src/` (at least one) lacks `import.meta.env` | AC3, R58 (source level; T13 owns the bundle scan) |
| 5 | `throws when the root element is missing instead of rendering nothing`: `mountShell(null)` throws `mountShell: root element #root not found` | AC6 |

Each mounting case unmounts its root and removes its container in `afterEach`, and restores all mocks (self-review 3).

AC5 (live, through the edge) is a recorded check, not an automated test. The evidence is in `06-implementation-notes.md`,
and it was re-confirmed after the Phase 9 restore: the `frontend` container started on pnpm 12.14.0 / Vite 8.3.5 and served
`/app/src/main.tsx` → 200 through the edge.

## Results (in the `infra/local` `frontend` container, Node 22, pnpm 12.14.0)

- `pnpm install --frozen-lockfile`: OK.
- `pnpm -C frontend test`: **5 passed (5)**, about 0.9 s.
- `pnpm -C frontend typecheck`: clean.
- `pnpm -C frontend build`: clean.
- Self-review 1 is resolved: the production build runs fine inside the jsdom environment, so no per-file environment split
  was needed.

## Mutation breaks (each applied alone, run, then restored from a backup)

| Break | Caught by | Result |
|---|---|---|
| `AppShell` returns `null` | case 2 | 1 failed, 4 passed |
| `base: '/'` instead of `'/app/'` | case 1 | 1 failed, 4 passed |
| null guard replaced by a silent mount elsewhere | case 5 | 1 failed, 4 passed |
| `fetch(...)` added to `AppShell` | case 3 | 1 failed, 4 passed |
| `import.meta.env.VITE_SECRET` read in `main.tsx` | case 4 | 1 failed, 4 passed |
| `AppShell` renders "Checky Pro" | case 2 | 1 failed, 4 passed |

Each break fails exactly the case meant to catch it.

## Deviations (flagged)

1. **`@types/node@22.20.5` added as an exact devDependency.** The test imports `node:fs/promises`, `node:os` and
   `node:path`, and type-checks under `tsc --noEmit`, so it needs Node's types; the frozen dependency list didn't include
   them. The types are scoped to the test file with `/// <reference types="node" />`, so app code still type-checks with
   `vite/client` types only and can't use Node globals unnoticed. The 22.x line matches the Node 22 runtime.
2. **Frontend directory from `process.cwd()`.** Under jsdom, `import.meta.url` is not a `file:` URL, so the directory comes
   from the working directory that `pnpm -C frontend test` sets (locally and in CI). Case 1 fails fast if it isn't the
   frontend package.
