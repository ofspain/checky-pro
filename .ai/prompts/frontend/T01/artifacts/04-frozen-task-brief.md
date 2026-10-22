# frontend · T01 · Phase 4 — Frozen Task Brief (Human Approval gate)

STATUS: FROZEN (2026-10-22)

Consumes `02-task-implementation-brief.md` and `03-design-challenge.md` (Kimi 2.7, 10 findings, all Low/Medium, no blocker).
Every finding was checked against the repository before it was dispositioned. None is an architectural trade-off, so they
are settled directly below, as for earlier gates (e.g. payment T10). The user's standing instruction for frontend work is
"as blitz and smooth as possible".

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| 1 | Routing boundary ambiguous | **ACCEPTED (clarified).** T01 imports no router. `base: '/app/'` plus the edge's `/app*` route is all AC5 needs; no client-side route matching exists until T02. |
| 2 | "No user-facing text" vs `<title>` | **ACCEPTED (amended).** AC4 now covers the React shell only. `index.html` may hold `<title>Checky Pro</title>` (the confirmed app name, design.md §4c) as static document metadata. Moving the title into the catalogue belongs to the first i18n task, not T01. |
| 3 | One named test covers two concerns | **ACCEPTED, modified.** The spec names exactly one test, `shellBuildsAndMounts` (package.md §8), and test names map 1:1 to tasks, so it is not renamed or split into new named tests. It becomes one `describe('shellBuildsAndMounts')` with separate `it` cases (build output, mount, negatives, missing root), so failure messages stay specific. |
| 4 | CI `changes` job may not exist | **REJECTED as stated; related gap ACCEPTED.** `ci.yml` already has the `changes` job (`dorny/paths-filter@v3`) with a `frontend` output. Checking it showed that root `package.json`, `pnpm-lock.yaml` and `pnpm-workspace.yaml` aren't in that filter, so a dependency-only change wouldn't run the frontend job. Those three paths are added to the `frontend` filter. |
| 5 | `typecheck` undefined | **ACCEPTED.** `typecheck` = `tsc --noEmit`; `build` = `tsc --noEmit && vite build`. CI runs `test` and `build`. The test's programmatic build covers Vite output; type correctness is covered by `build`/`typecheck`. |
| 6 | AC3 negatives untested | **ACCEPTED.** The mount case asserts that mounting calls no `fetch`, no `localStorage`/`sessionStorage` `setItem`, and no `navigator.serviceWorker.register` (stubbed, since jsdom has none). A source check asserts that no file under `frontend/src/` references `import.meta.env`. (T13 still owns the bundle-level secret scan.) |
| 7 | Missing-`#root` failure untested | **ACCEPTED.** The mount function throws on a missing container, and a test case covers it. |
| 8 | README commands underspecified | **ACCEPTED.** The commands are listed under Files to Modify. |
| 9 | File naming not stated | **ACCEPTED.** Names below are mandatory. Components are PascalCase, with tests co-located next to the code they test. |
| 10 | Lint out of scope | **ACCEPTED (clarified).** No ESLint, no `lint` script in T01; T04 owns linting. |

Open Questions: none. Every Phase 2 default (minimal L13 subset, no visible shell text, CI job included, versions resolved
and locked at implementation) is confirmed.

---

## Frozen brief

### Task
T01 — **Shell renders.** The Vite, React, and TypeScript app builds and mounts an empty shell. Verifies
`shellBuildsAndMounts`. Cites L13.

### Purpose
Create the frontend skeleton every later task extends (L12). It must build, mount, and be served through the local edge at
`/app/`.

### Scope
**In**
- The workspace root `package.json`.
- The `frontend/` Vite + React + TypeScript app: an entry that mounts a React root and renders an empty shell.
- A Vitest + jsdom setup and `shellBuildsAndMounts`.
- The dev-server config that matches the `infra/local` contract.
- A minimal `frontend` CI job.

**Out**
- Any router (T02); the generated client (T03).
- ESLint and lint scripts (T04); auth (T14+).
- Service worker / `vite-plugin-pwa` (T09–T12).
- i18n catalogue; Tailwind and Radix.
- TanStack Query and Zustand; Playwright.
- Any `VITE_*` key.
- Any change to `infra/local/`, `services/`, `contracts/`, `libs/` or `spec/`.

### Business Rules
- No requirement is cited; T01 implements lock L13.
- R58 is not violated: no env key is read. T13 proves this at bundle level later.

### Locked Decisions
- **L13:** Vite, React, TypeScript, Vitest.
- **L14:** served under `/app` (`base: '/app/'`), no router yet.
- **L9:** no `server.proxy`, no backend URL.
- **L10:** no env key.
- **L11:** the React shell renders no text.
- **L12:** `src/app/` is the extension point; there is one mount function.

### Dependencies
- Node 22 (`node:22-alpine`).
- pnpm pinned via root `packageManager` (corepack).
- `react`, `react-dom`; `vite`, `@vitejs/plugin-react`, `typescript`, `@types/react`, `@types/react-dom`; `vitest`, `jsdom`.
- Current stable majors supporting Node 22, exact versions locked in `pnpm-lock.yaml`.

### Inputs
None at runtime. The source tree at build time.

### Outputs
- `frontend/dist/index.html`, plus hashed assets referenced under `/app/`.
- A React root mounted into `#root` that renders the shell element.

### State Changes
None.

### Files to Create
- `package.json` (root): `private: true`, `packageManager: "pnpm@<exact>"`, `engines.node: ">=22"`, no dependencies.
- `pnpm-lock.yaml`: generated.
- `frontend/package.json`: scripts `dev` (`vite`), `build` (`tsc --noEmit && vite build`), `test` (`vitest run`),
  `typecheck` (`tsc --noEmit`).
- `frontend/index.html`: `lang="en"`, `<title>Checky Pro</title>`, `<div id="root">`, module script `/src/main.tsx`.
- `frontend/vite.config.ts`:
  - `base: '/app/'`;
  - `server: { host: true, port: 5173, strictPort: true }`, with no proxy;
  - React plugin;
  - Vitest `environment: 'jsdom'`.
- `frontend/tsconfig.json`: `strict: true`, `noEmit: true`, `jsx: react-jsx`.
- `frontend/src/main.tsx`: calls `mountShell(document.getElementById('root'))` and nothing else.
- `frontend/src/app/mountShell.tsx`: `mountShell(container: HTMLElement | null)`. It throws if the container is null and
  otherwise renders `<AppShell />` into a React root.
- `frontend/src/app/AppShell.tsx`: the empty shell element (identified by `data-testid="app-shell"`; no text).
- `frontend/src/app/shellBuildsAndMounts.test.tsx`: the named test.

### Files to Modify
- `.github/workflows/ci.yml`:
  - add `package.json`, `pnpm-lock.yaml` and `pnpm-workspace.yaml` to the `frontend` filter;
  - add a `frontend` job gated on `needs.changes.outputs.frontend`: Node 22, `corepack enable`,
    `pnpm install --frozen-lockfile`, `pnpm -C frontend test`, `pnpm -C frontend build`.
- `frontend/README.md`: add these commands: `infra/local/bin/up --frontend`, `infra/local/bin/status --wait`, the app at
  `http://localhost:5173/app/`, `docker compose -p themistra-local -f infra/local/compose.yaml exec frontend pnpm -C frontend test`,
  and `... exec frontend pnpm -C frontend build`.

### Files NOT to Modify
- `spec/**`, `contracts/**`, `services/**`, `libs/**`, `infra/**`.
- `pnpm-workspace.yaml`.
- The developer's `prompt` file.

### Acceptance Criteria
- **AC1 (L13, L14):** `pnpm -C frontend build` type-checks and builds with no errors; `dist/index.html` references its
  script under `/app/`.
- **AC2 (L13):** `mountShell` renders the shell element into the given container.
- **AC3 (L9, L10, R58):** mounting makes no `fetch`, writes no storage and registers no service worker; no `src/` file
  references `import.meta.env`.
- **AC4 (L11):** the React shell renders no text content. The static `<title>` in `index.html` is permitted.
- **AC5 (live):** after root `pnpm install`, `pnpm -C frontend dev --host 0.0.0.0 --port 5173` serves the app. Through
  `infra/local/bin/up --frontend`, `GET http://localhost:5173/app/` returns the shell HTML, its module scripts return 200
  through the edge, and an edit hot-reloads.
- **AC6 (null handling):** `mountShell(null)` throws.

### Required Tests
`shellBuildsAndMounts` (Vitest), one `describe` with these cases:
1. A programmatic Vite `build()` into a temp out dir succeeds, and `index.html` references `/app/`.
2. `mountShell` renders `[data-testid="app-shell"]` with empty text content.
3. Mounting calls no `fetch`, no storage `setItem` and no `serviceWorker.register`; no `src/` file contains
   `import.meta.env`.
4. `mountShell(null)` throws.

Mutation breaks, each to be shown failing:
- the shell not rendered;
- `base` not `/app/`;
- the null guard removed;
- a `fetch` added to the shell.

The live check (AC5) is recorded in the implementation notes.

### Constraints
- **Security:** no env access, network, storage or worker; no secrets.
- **Boundaries:** shell code lives in `src/app/`; `main.tsx` only calls `mountShell`.
- **TypeScript:** `strict`, no `any`.
- **Null handling:** a missing container throws a clear error.
- **Performance:** the test's single build keeps the suite to a few seconds.
- **Environment:** installs and runs in `node:22-alpine` with corepack pnpm. No host tooling is assumed: commands run in the
  `frontend` container or a throwaway `node:22-alpine` container.
