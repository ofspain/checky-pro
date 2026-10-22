# frontend · T01 · Phase 0 — Repository Understanding

Task: **T01 — Shell renders.** "The Vite, React, and TypeScript app builds and mounts an empty shell. Verifies
`shellBuildsAndMounts`. Cites L13." Date: 2026-10-22.

## 1. Architecture summary (of what exists today)

The frontend is **greenfield**. No frontend code exists; `spec/frontend/package.md` §12 says so, and the repo confirms it.

- `frontend/` holds only `README.md` (from the Phase 1 scaffold, 4c725d94). The README says React + TypeScript, a mobile-first
  PWA, API access only through `libs/ts/api-client`, and served via CloudFront behind the edge.
- `libs/ts/api-client/` holds only `.gitkeep`. Generating it is T03, not this task.
- `pnpm-workspace.yaml` (repo root) lists `frontend`, `infra`, `libs/ts/*` and `services/crypto/sidecars/*`.
  **There is no root `package.json`**, and no `package.json` anywhere in the repo (`find` returns none).
- No lockfile, no `tsconfig`, no Vite config, no ESLint config, and no Node version pin (`.nvmrc` / `engines` /
  `packageManager`).
- `.gitignore` already ignores `node_modules/`, `dist/`, `coverage/`, `*.tsbuildinfo`, `.env` and `.env.*`.
- CI (`.github/workflows/ci.yml`):
  - a `dorny/paths-filter` step already defines a `frontend` output (`frontend/**`, `libs/ts/**`, `contracts/api/**`);
  - there is **no frontend job yet**, only a comment that pnpm jobs "plug in here";
  - only `auth` has a job.
- Target architecture, from the spec (planned, not built):
  - `design.md` §6 file map: `frontend/src/{app,auth,api,features/*,i18n,pwa}`, `frontend/e2e/` (Playwright) and the generated
    `libs/ts/api-client/`.
  - T01 only touches `src/app/` ("shell, providers, router under /app (L13, L14)"). The router itself is T02.
- Persistence, events, outbox and resource-server security do not apply to this service. It is a browser SPA, and its
  client-side state rules are in `design.md` §5 and L16; none of them is exercised by an empty shell.
- Runtime environment (2026-10-22, `infra/local/`):
  - the SPA dev server runs in Docker (`frontend` compose service, profile `frontend`);
  - image `node:22-alpine`, working dir `/repo`, command
    `corepack enable && pnpm install && pnpm -C frontend dev --host 0.0.0.0 --port 5173`;
  - named volumes on `/repo/node_modules` and `/repo/frontend/node_modules`, with `CHOKIDAR_USEPOLLING=true`;
  - the Caddy edge on host `localhost:5173` routes `/app*` to `frontend:5173` **without stripping the prefix**, and `/` to a
    redirect to `/app/`.
  - The host also has Node v26.5.0 but no pnpm. The standing rule is that apps run inside Docker
    (`.ai/prompts/frontend/KIMI-LOCAL-STACK.md` §3).

## 2. Existing code this task touches

| Item | Exists? | Note |
|---|---|---|
| `frontend/` directory | yes (README only) | T01 adds the app inside it |
| `frontend/README.md` | yes | stays; may get a "how to run" line |
| Root `package.json` | **no** | needed for `pnpm install` at the repo root (workspace root); the compose command depends on it |
| `pnpm-workspace.yaml` | yes | lists `infra` and `services/crypto/sidecars/*`, which have no `package.json` today; pnpm skips globs that match nothing |
| `frontend/package.json`, `vite.config.ts`, `tsconfig*.json`, `index.html`, `src/main.tsx`, `src/app/*` | no | new |
| Test setup (Vitest config, jsdom/happy-dom) | no | new; `shellBuildsAndMounts` is the first test in the package |
| `libs/ts/api-client` | placeholder | not touched (T03) |
| `infra/local/compose.yaml` `frontend` service, `edge/Caddyfile` | yes | consumed, not changed |
| CI frontend job | no | the paths-filter output exists; whether T01 adds the job is a Phase 1/2 scoping question |

## 3. Established patterns to follow

There are no frontend patterns in the repo yet. T01 establishes them. The binding constraints come from the spec:

- **Stack (L13, `[ALL]`):** Vite; React + TypeScript; React Router; TanStack Query; Zustand; `vite-plugin-pwa`; Vitest;
  Playwright. T01's task text names only Vite + React + TypeScript and the build-and-mount behaviour. Router (T02),
  generated client (T03), lint rule (T04) and PWA/worker (T09–T12) belong to later tasks.
- **Routes under `/app` (L14, agents.md):** the shell is served under `/app`. `KIMI-LOCAL-STACK.md` §3 and the design.md §4c
  note of 2026-10-22 already fix the dev server: `base: '/app/'`, `server.host: true`, port 5173 with `strictPort`, no
  `server.proxy`.
- **Single origin (L9):** no per-service URLs in config.
- **Secrets out of the bundle (L10, R58):** only the public keys in §4c (`VITE_OIDC_*`, `VITE_API_BASE_PATH`, still PROPOSED
  pending Q6) may ever be baked in. An empty shell needs none.
- **i18n (L11, agents.md):** "Every user-facing string is in the catalogue. No hardcoded copy in components." An empty shell
  that renders any visible text (even "Checky Pro") is subject to this.
- **Accessibility (L11):** keyboard operation, live region, WCAG AA. An empty shell has almost no surface, but its document
  should have `lang` and a title.
- **Repo conventions** (from the backend services, where applicable):
  - the spec is never edited except by dated notes;
  - no secrets in code or images, no PII in logs;
  - commit and push after each phase.

## 4. Testing conventions

- Spec: every task names one test (`package.md` §8). T01's is `shellBuildsAndMounts` → L13.
- L13 confirms Vitest for unit/component tests and Playwright for E2E under `frontend/e2e/`. The spec doesn't say which
  runner hosts `shellBuildsAndMounts`. "Builds and mounts" suggests two observable facts: the production build succeeds, and
  the app mounts into the DOM. Which tool proves each is a Phase 1/2 question.
- No frontend test infrastructure exists. The backend conventions (JUnit, Testcontainers, fixed `Clock`, ArchUnit) don't
  carry over directly. The one transferable practice is the "mutation break" check: confirm a test fails when the behaviour
  is removed, as done throughout the backend work.
- Live-stack checks (standing rule, agents.md note of 2026-10-22): each task is also exercised against `infra/local`. For
  T01 that means `bin/up --frontend` serves the shell at `http://localhost:5173/app/` through the edge.

## 5. Known gaps / unknowns

1. **Versions.** The spec pins no Node, pnpm, Vite, React, TypeScript, Vitest or Playwright versions. The Docker image is
   `node:22-alpine`. I do not know the author's preferred versions; current stable majors are the default unless told
   otherwise.
2. **Root `package.json` contents.** It is needed for the workspace install. Whether it should hold only workspace metadata
   (`private`, `packageManager`) or also shared dev tooling is not specified.
3. **How much of L13 T01 installs.** The task text says "Vite, React, and TypeScript … empty shell". Installing Router, Query,
   Zustand, PWA and Playwright now versus in their owning tasks is not stated. Guardrails say no scope beyond the task.
4. **Test environment for mounting** (jsdom vs happy-dom vs a Playwright check through the edge). Not specified.
5. **What the "empty shell" renders.** Visible copy must come from a catalogue (L11), but the i18n catalogue mechanism (O3 in
   the design's file map) is not built and its library is not chosen. I do not know whether T01 renders any text.
6. **CI job.** Whether T01 adds a `frontend` CI job (install, typecheck, test, build) is not stated by the task.
7. **`services/frontend/` vs `frontend/`.** The generated T01 README says outputs go in `services/frontend/`, but design.md
   §6, `frontend/README.md`, the workspace file, CI and the compose service all use `frontend/`. The spec wins: `frontend/`.
   The README line is a generator artifact.
8. **Q6** (route set and env keys still marked PROPOSED) does not block T01, which needs neither routes nor env keys.
