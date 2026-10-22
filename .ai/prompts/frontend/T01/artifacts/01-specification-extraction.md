# frontend · T01 · Phase 1 — Specification Extraction

Consumes `00-repository-understanding.md`. Scope: T01 only. "The Vite, React, and TypeScript app builds and mounts an empty
shell. Verifies `shellBuildsAndMounts`. Cites L13."

## Business Rules

T01 cites no requirement ID; it cites only the lock L13. No requirement in `requirements.md` R1–R59 is about the shell
existing. Two apply as constraints that the shell must not violate, though T01 does not prove them (each has its own task):

- **R58** (proved by T13, `bundleContainsNoSecretEnvKeys`): the production bundle contains no secret and no env key outside
  the §4c public list. T01's build must therefore bake in no env key at all; it needs none.
- **R57 / L17** (proved by T09–T12): the service worker. Not part of T01. T01 must not add a worker.

There are no other business rules. An empty shell has no user flows.

## Locked Decisions

| ID | Constraint on T01 |
|---|---|
| **L13** `[ALL]` | The stack is Vite, React and TypeScript (named in the task), and later React Router, TanStack Query, Zustand, `vite-plugin-pwa`, Vitest and Playwright. T01 must use Vite + React + TypeScript and must not introduce a competing tool (no CRA, Next, Jest or webpack). Vitest is the L13 unit test runner. |
| **L14** `[ALL]` | The SPA lives under `/app`. T01's build and dev server use base `/app/`. The route set itself is T02 (`routesResolveUnderAppPrefixOnly`). |
| **L9** `[ALL]` | Single origin: no per-service URLs. T01 configures no `server.proxy` and no backend URL (design.md §4c note of 2026-10-22). |
| **L10** `[ALL]` | Only public §4c keys may be baked in. T01 bakes in none. |
| **L11** `[ALL]` / agents.md i18n | "Every user-facing string is in the catalogue. No hardcoded copy in components." The catalogue is `react-i18next`, English-only, missing key falls back to English (O3/Q8, resolved). If the shell renders no text, there is nothing to catalogue. If it renders any (for example the app name "Checky Pro"), it must come from the catalogue. |
| **L12** `[ALL]` | Phase additivity: later phases build on this shell and never replace it. The shell's structure (`src/app/` as root, a single mount point) must be one later tasks can extend. |
| agents.md "Scope and stack" | React + TypeScript, mobile-first PWA, one origin behind the edge. No native or React Native code. |
| agents.md Process | Trunk-based; `main` deployable. A material design change needs an ADR (T01 makes none). |

Q7 (Tailwind + headless components) is resolved but not needed by an empty shell. It belongs to the first task with styled UI.

## Files involved

Existing (read or extend):
- `pnpm-workspace.yaml` (root): already lists `frontend`. Unchanged.
- `frontend/README.md`: extend with run/test commands (optional).
- `infra/local/compose.yaml` `frontend` service and `edge/Caddyfile` (`/app*` → `frontend:5173`, prefix kept): consumed
  unchanged. They define the dev-server contract.
- `.github/workflows/ci.yml`: the `frontend` paths-filter output exists; a job may be added (see Open Questions).

New (from design.md §6, `frontend/` layout):
- Root `package.json` (workspace root: `private`, `packageManager` pnpm pin), required for `pnpm install` at the root.
- `frontend/package.json`, `frontend/index.html`, `frontend/vite.config.ts`, `frontend/tsconfig*.json`.
- `frontend/src/main.tsx` (entry, mounts into the root element) and `frontend/src/app/` (the shell component, per §6 "shell,
  providers, router under /app").
- The test for `shellBuildsAndMounts`, plus the Vitest config/environment.
- `pnpm-lock.yaml` (generated, committed).

Not touched: `libs/ts/api-client` (T03), `frontend/src/{auth,api,features,i18n,pwa}` (later tasks), `frontend/e2e/`
(no Playwright test is named for T01).

## Dependencies

- **Toolchain:** Node 22 (the `infra/local` image is `node:22-alpine`); pnpm via corepack.
- **Runtime:** `react`, `react-dom`.
- **Build:** `vite`, `@vitejs/plugin-react`, `typescript`, `@types/react`, `@types/react-dom`.
- **Test:** `vitest` and a DOM environment for "mounts" (jsdom or happy-dom); optionally `@testing-library/react`.
- **Config keys:** none. No `VITE_*` key is needed by an empty shell (§4c keys arrive with the tasks that use them).
- **Contracts:** none. The shell makes no backend call.
- **Dev-server contract** (`KIMI-LOCAL-STACK.md` §3; design.md §4c note of 2026-10-22): `base: '/app/'`,
  `server.host: true`, `server.port: 5173`, `server.strictPort: true`, no `server.proxy`; it must start with
  `pnpm -C frontend dev --host 0.0.0.0 --port 5173` after `pnpm install` at the repo root.

## Acceptance Criteria

| # | Criterion | Source |
|---|---|---|
| AC1 | `pnpm -C frontend build` (Vite production build with TypeScript type checking) succeeds with no errors and emits `index.html` plus hashed assets referenced under `/app/`. | task ("builds"), L13, L14 |
| AC2 | Loading the app mounts a React root into the document's mount element, and the shell component renders. | task ("mounts an empty shell"), L13 |
| AC3 | The shell is empty: no route, no backend call, no stored state, no service worker and no env key. | task ("empty"), L9, L10, R58 (non-violation) |
| AC4 | Any visible text the shell renders comes from the catalogue, or the shell renders none. | L11, agents.md i18n |
| AC5 | `pnpm install` at the repo root, then `pnpm -C frontend dev --host 0.0.0.0 --port 5173`, serves the shell. Through `infra/local` it is reachable at `http://localhost:5173/app/`. | design.md §4c note 2026-10-22, standing live-stack rule |

## Tests required

- **`shellBuildsAndMounts`** (named, `package.md` §8 → L13). It must prove both halves of AC1 and AC2: the build succeeds,
  and the app mounts and renders the shell.
- Implied boundary checks:
  - **Fails when the shell is removed or broken.** The mount assertion must fail if the shell isn't rendered (mutation
    break).
  - **Built asset paths use `/app/`** (AC1, L14). A build with the wrong base would pass "builds" but break behind the edge.
    This partly overlaps T02's `routesResolveUnderAppPrefixOnly`, which owns routing; T01 owns only the asset base.
- **Live check** (not a unit test; standing rule): through `infra/local` (`bin/up --frontend`), `GET /app/` returns the
  shell's HTML, its assets load through the edge, and hot reload reflects an edit.

## Open Questions

None blocks T01. These are scoping choices for Phase 2, each with a default:

1. **How much of L13 to install now.** The task names Vite + React + TypeScript. Guardrails say no scope beyond the task.
   Default: install only what T01 needs (React, Vite, TypeScript, Vitest + DOM env). Router, Query, Zustand, PWA and
   Playwright arrive with their owning tasks (T02 and later).
2. **Shell text.** Default: the shell renders no copy, so react-i18next (O3/Q8) is not pulled in by T01. If a visible name
   is wanted, the first text-bearing task brings in the catalogue.
3. **CI job.** CI has a `frontend` filter but no job. Default: add a minimal `frontend` job (install, `test`, `build`), so
   `shellBuildsAndMounts` runs on PRs as the backend tests do. This could be argued out of scope; Phase 3 should challenge it.
4. **Versions.** Not pinned by the spec. Default: current stable majors compatible with Node 22, exact versions locked by
   `pnpm-lock.yaml`, pnpm pinned through `packageManager`.
5. The T01 README's "`services/frontend/`" output path contradicts design.md §6 and every other repo reference. `frontend/`
   is used (Phase 0 §5.7). This is not a spec conflict, only a generator artifact.
