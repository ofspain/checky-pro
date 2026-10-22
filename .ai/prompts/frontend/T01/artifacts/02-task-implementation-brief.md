# frontend · T01 · Phase 2 — Task Implementation Brief

## Task
T01 — **Shell renders.** The Vite, React, and TypeScript app builds and mounts an empty shell. Verifies
`shellBuildsAndMounts`. Cites L13.

## Purpose
Create the frontend application skeleton every later task builds on (L12). It must build, mount, and be served through the
local edge at `/app/`.

## Scope
**In**
- A pnpm workspace root `package.json`.
- The `frontend/` Vite + React + TypeScript app: an entry that mounts a React root and renders an empty shell component.
- A Vitest setup and the `shellBuildsAndMounts` test.
- The dev-server config matching the `infra/local` contract.
- A minimal `frontend` CI job (install, test, build).

**Out**
- Routing (T02).
- Generated API client (T03).
- Lint rule (T04).
- Auth and session (T14+).
- Service worker / `vite-plugin-pwa` (T09–T12).
- i18n catalogue (first text-bearing task).
- Tailwind / Radix (first styled task).
- TanStack Query and Zustand (first task that uses them).
- Playwright (first task that names an E2E test).
- Any `VITE_*` env key.
- Any change to `infra/local/`, backend services, contracts or `spec/`.

## Business Rules
- No requirement is cited by T01; it implements lock L13.
- R58: the bundle contains no secret and no non-public env key. It is satisfied trivially (no env keys are read) and proved
  later by T13.

## Locked Decisions
- **L13:** Vite, React, TypeScript; Vitest as the test runner. Nothing outside the L13 stack.
- **L14:** the app is served under `/app` (`base: '/app/'`).
- **L9:** single origin; no `server.proxy`, no backend URL.
- **L10:** no env key baked in.
- **L11 / agents.md:** no hard-coded user-facing copy. The shell renders no visible text.
- **L12:** the shell is the extension point later phases reuse (`src/app/` root component, one mount point).

## Dependencies
- Node 22 (`node:22-alpine`, as in `infra/local`); pnpm pinned via `packageManager` (corepack).
- Runtime: `react`, `react-dom`.
- Build: `vite`, `@vitejs/plugin-react`, `typescript`, `@types/react`, `@types/react-dom`.
- Test: `vitest`, `jsdom`.
- Versions: current stable majors that support Node 22, resolved at implementation and locked by `pnpm-lock.yaml`.

## Inputs
None at runtime (no config, no network). At build time: the source tree only.

## Outputs
- `pnpm -C frontend build` → `frontend/dist/` with `index.html` and hashed assets referenced under `/app/`.
- At runtime: a React root mounted into `#root` that renders the shell element.

## State Changes
None (no storage, no network, no service worker).

## Files to Create
- `package.json` (repo root: `private: true`, `packageManager: pnpm@<x>`, no dependencies).
- `pnpm-lock.yaml` (generated).
- `frontend/package.json` (scripts `dev`, `build`, `test`, `typecheck`).
- `frontend/index.html` (`lang="en"`, `#root` mount element).
- `frontend/vite.config.ts` (`base: '/app/'`; `server: { host: true, port: 5173, strictPort: true }`; no proxy; Vitest
  `environment: 'jsdom'`).
- `frontend/tsconfig.json` (strict).
- `frontend/src/main.tsx` (mounts the shell).
- `frontend/src/app/AppShell.tsx` (the empty shell).
- `frontend/src/app/shell.test.ts(x)` (`shellBuildsAndMounts`).
- `frontend/.gitignore` only if anything beyond the root ignores is needed.

## Files to Modify
- `.github/workflows/ci.yml`: add a `frontend` job gated on `needs.changes.outputs.frontend` (Node 22, corepack pnpm,
  `pnpm install --frozen-lockfile`, `pnpm -C frontend test`, `pnpm -C frontend build`).
- `frontend/README.md`: add run/test commands (through `infra/local`).

## Files NOT to Modify
- `spec/**`, `contracts/**`, `services/**`, `libs/**`.
- `infra/local/**`: its `frontend` service and Caddy route are the fixed contract.
- `pnpm-workspace.yaml`.
- The developer's `prompt` file.

## Acceptance Criteria
- **AC1 (L13, L14):** `pnpm -C frontend build` type-checks and builds with no errors; `dist/index.html` references its
  script under `/app/`.
- **AC2 (L13):** mounting the entry renders the shell element into `#root`.
- **AC3 (L9, L10, R58):** the shell reads no env key, makes no network request, writes no storage and registers no service
  worker.
- **AC4 (L11):** the shell renders no user-facing text.
- **AC5 (live, §4c note 2026-10-22):**
  - `pnpm install` at the repo root and `pnpm -C frontend dev --host 0.0.0.0 --port 5173` serve the app;
  - through `infra/local/bin/up --frontend`, `GET http://localhost:5173/app/` returns the shell HTML and its module
    scripts load through the edge (HTTP 200).

## Required Tests
- **`shellBuildsAndMounts`** (Vitest): (a) runs a Vite production build of `frontend/` (programmatic `build()` into a
  temporary out dir) and asserts it succeeds and `index.html` references `/app/`; (b) mounts the app into a jsdom `#root`
  and asserts the shell element is present.
- Mutation breaks:
  - the test fails if the shell isn't rendered;
  - the test fails if `base` isn't `/app/`;
  - type checking fails on a type error in the shell (part of `build`).
- Live check (recorded, not automated): AC5 through the edge, plus hot reload of an edit.

## Constraints
- **Security:** no env access (`import.meta.env.VITE_*`), no `fetch`, no storage, no service worker; no secrets anywhere.
- **Module boundaries:** shell code lives in `src/app/`; the entry `src/main.tsx` only mounts it.
- **TypeScript:** `strict: true`; no `any`.
- **Null handling:** the entry fails loudly (throws) if `#root` is missing, rather than silently not mounting.
- **Performance:** the test's build runs once per test run; total test time stays a few seconds.
- **Environment:** must install and run inside `node:22-alpine` with pnpm from corepack. No host tooling assumed.

## Open Questions
No blockers. Defaults chosen (for Phase 3 to challenge): minimal L13 subset now; no visible text; CI job included; versions
resolved and locked at implementation.
