# frontend · T03 · Phase 4 — Frozen Task Brief (Human Approval gate)

STATUS: FROZEN (2026-10-22)

Consumes `02-task-implementation-brief.md` and `03-design-challenge.md` (Kimi 2.7: 10 findings, Low/Medium, no blocker).
Each finding was checked against the repository and against `openapi-fetch@0.17.0` / `openapi-typescript@7.13.0`, run in
scratch probes, before it was dispositioned. None is an architectural trade-off, so they are settled here, as at the
T01/T02 gates.

## What the checks showed

- **`auth.yaml` declares no error responses.**
  - There are 0 `4xx`/`5xx` response entries and no `problem` schema anywhere in the file.
  - Every operation documents only its success code.
  - So no RFC 9457 type can be generated from it. The Phase 2 line "problem types are exposed as generated types" was
    wrong, and is struck below.
- **`baseUrl` resolution.** With browser-style relative resolution against `http://localhost:5173/app/home`:
  - `''`, `'/'` and an omitted `baseUrl` all request `http://localhost:5173/accounts/me`;
  - `'/api'` requests `http://localhost:5173/api/accounts/me`.
- **Node's `Request` rejects relative URLs** (`Failed to parse URL from /accounts/me`). A browser resolves them against
  the document. In Vitest's jsdom environment the global `Request` is Node's, so the AC5 test installs a `Request` shim
  with browser semantics for the duration of that case.
- **The middleware hook.** `createClient(...).use` is a function.
- **The operation count.** `auth.yaml` has 34 `operationId`s, and the generated `operations` interface has 34 members.
- **`.gitattributes` exists** (Maven wrapper rules only). `core.autocrlf` is unset.

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| 1 | TS 5/7 split is an implicit guarantee | **ACCEPTED.** It is now a stated constraint: the generated output must type-check under TS 7.0.2 `strict`. Test case 2 enforces it on every run. If a generator or contract change breaks it, the tooling is upgraded or pinned before that change lands. |
| 2 | Problem types must be generated | **REJECTED (premise false).** The contract declares no error responses or problem schema, so there is nothing to generate. `contracts/**` is off-limits to frontend tasks. The Phase 2 line is struck. Typing RFC 9457 bodies by their standard members belongs to the client-boundary wrapper (`frontend/src/api/`, T05–T08), which is noted for those tasks. This is not a T03 blocker. |
| 3 | Drift check scope too wide | **ACCEPTED.** The CI step diffs `libs/ts/api-client/src/generated` only. |
| 4 | Middleware hook untested | **ACCEPTED.** Case 4 registers an `onRequest` middleware with `client.use(...)` and asserts it sees the request. That proves the hook T05–T08 rely on is reachable. |
| 5 | Byte-for-byte check is brittle | **ACCEPTED (note).** The versions are pinned exactly. If a deliberate generator bump changes only formatting, the bump and the regenerated file land together in one change, with no contract change mixed in. |
| 6 | Line endings | **ACCEPTED.** Add `libs/ts/api-client/src/generated/** text eol=lf` to the existing `.gitattributes`. |
| 7 | Deep imports into `src/generated` | **REJECTED for T03.** The package `exports` map has only `"."`. Under `moduleResolution: bundler`, a deep specifier such as `@themistra/api-client/src/generated/auth` doesn't resolve, so the boundary is structural. Phase 6 verifies this with a probe. T04 owns the lint rule. |
| 8 | Source export assumes a transpiling consumer | **ACCEPTED (note).** The only consumer is the Vite/TS frontend. Any other consumer needs a build step, added in its own task. |
| 9 | Operation set unchecked | **ACCEPTED.** Case 3 asserts that the generated `operations` names equal the `operationId`s in `auth.yaml`: the same set, 34 of them. |
| 10 | `baseUrl: ''` only indirectly tested | **REJECTED as stated.** The probe shows `''`, `'/'` and an omitted value behave the same, so a source check for `''` adds nothing. The stronger guard is kept: case 4 asserts the exact requested URL, `${location.origin}/accounts/me`. That fails for any absolute or prefixed `baseUrl`. |

Open Questions: none. The Phase 2 defaults are confirmed:
- `openapi-typescript` + `openapi-fetch`;
- the output is committed and CI checks it for drift;
- the test lives in `frontend/`.

---

## Frozen brief

### Task
T03 — **Generated client.** `libs/ts/api-client` is generated from `contracts/api/auth.yaml` and compiles. Verifies
`generatedClientCompiles`. Cites L6, R20.

### Purpose
Create the one sanctioned way the SPA talks to auth-service: a typed client generated from the contract (L6). It is the
base for T04 (lint), T05–T08 (the 401/429 boundary) and T30/T31 (profile and status).

### Scope
**In**
- The workspace package `libs/ts/api-client` (`@themistra/api-client`).
- The committed `generate` command.
- The committed generated types for `auth.yaml`.
- A thin entry that exports the types and `createAuthClient()`.
- The frontend's workspace dependency on the package.
- `generatedClientCompiles`.
- The CI regenerate-and-drift step.
- The `.gitattributes` LF rule.

**Out**
- `payments.yaml` and `notifications.yaml`.
- The `frontend/src/api/` wrapper (401/429, problem-body typing: T05–T08).
- The lint rule (T04).
- Screens, real backend calls, TanStack Query hooks, and `/userinfo` (T30).
- Any build step for the package.

### Business rules
- **R20:** the client exposes a typed `GET /accounts/me`. There is no `/userinfo` or other SAS operation in it.

### Locked decisions
- **L6:** generated client only; generated in CI.
- **design.md §6:** generated, not hand-edited.
- **L9:** single origin, relative paths.
- **L10:** no env, no secret.
- **L13.**

### Dependencies (exact)
- `openapi-fetch` 0.17.0 (dependency).
- `openapi-typescript` 7.13.0 (devDependency).
- `typescript` 5.9.3 (devDependency of the api-client only; the generator's peer, never imported by app code).

The project compiler remains TypeScript 7.0.2 (`frontend/`).

### Files to create
- **`libs/ts/api-client/package.json`:**
  - `@themistra/api-client`, `private`, `type: module`;
  - `exports: { ".": "./src/index.ts" }`;
  - script `generate` = `openapi-typescript ../../../contracts/api/auth.yaml -o src/generated/auth.ts`;
  - the dependencies above.
- **`libs/ts/api-client/tsconfig.json`:** `strict`, `noEmit`, `moduleResolution: bundler`, `lib` `ES2022` and `DOM`,
  `include: ["src"]`.
- **`libs/ts/api-client/src/generated/auth.ts`:** generator output, never hand-edited.
- **`libs/ts/api-client/src/index.ts`:** re-exports `paths`, `components` and `operations`, and exports
  `createAuthClient()`, which returns `createClient<paths>({ baseUrl: '' })`. No env and no absolute URL.
- **`libs/ts/api-client/README.md`:** generated, do not edit, and the regenerate command.
- **`frontend/src/api/generatedClientCompiles.test.ts`.**

Delete `libs/ts/api-client/.gitkeep`.

### Files to modify
- **`frontend/package.json`:** add `"@themistra/api-client": "workspace:*"`.
- **`pnpm-lock.yaml`:** the new importer.
- **`.github/workflows/ci.yml`:** in the `frontend` job, after install, run `pnpm -C libs/ts/api-client generate`, then
  `git diff --exit-code -- libs/ts/api-client/src/generated`.
- **`.gitattributes`:** add `libs/ts/api-client/src/generated/** text eol=lf`.

### Files not to modify
- `contracts/**`, `spec/**`, `services/**`, `infra/**`.
- `frontend/src/app/**`, `frontend/vite.config.ts`, `frontend/tsconfig.json`.
- `pnpm-workspace.yaml`.
- `prompt`.

### Acceptance criteria
- **AC1 (L6, §6):** the client comes from the committed `generate` command. Only `index.ts`, the config files and the
  README are hand-written.
- **AC2 (task):** `libs/ts/api-client` type-checks under TypeScript 7.0.2 `strict`, with zero errors.
- **AC3 (L6):** regenerating from `auth.yaml` reproduces `src/generated/auth.ts` byte for byte.
- **AC4 (R20):**
  - `paths['/accounts/me']['get']` has a typed 200 JSON response;
  - no generated path starts with `/oauth2`, `/.well-known`, `/userinfo`, `/login`, `/logout` or `/connect`;
  - the generated operation names equal the `auth.yaml` `operationId`s.
- **AC5 (L9, L10):**
  - `createAuthClient().GET('/accounts/me')`, with `fetch` stubbed and browser-style URL resolution, requests exactly
    `${location.origin}/accounts/me`;
  - an `onRequest` middleware sees that request;
  - `src/index.ts` contains no `https?://` and no `import.meta.env`.
- **AC6 (L6):** CI regenerates the client and fails on drift in `src/generated`.

### Required tests: `generatedClientCompiles`
In `frontend/src/api/`, Vitest:
1. **AC3:** run the package's generator into a temp file and byte-compare it with the committed file.
2. **AC2:** run `frontend`'s TS 7 `tsc --noEmit -p libs/ts/api-client/tsconfig.json`; expect exit 0 and no output.
3. **AC4:**
   - a type-level reference to `/accounts/me` GET's 200 body, compiled by `frontend` `typecheck`;
   - a scan of the generated file's path keys for SAS prefixes;
   - the operation names equal the YAML `operationId`s.
4. **AC5:** a stubbed `fetch` and a browser-semantics `Request` shim, restored afterwards; the exact URL; and an
   `onRequest` middleware that is invoked.
5. **AC5:** a source scan of `index.ts`.

Mutation breaks (each must fail its case):
- a hand edit in `auth.ts`;
- a type error in `index.ts`;
- `/accounts/me` removed from the generated file;
- an absolute `baseUrl`;
- a prefixed `baseUrl` (`/api`).

### Constraints
- `strict` TypeScript.
- No env, no secret, no absolute URL.
- The generated file is never hand-edited.
- The generated output must type-check under TS 7.0.2 `strict` (finding 1).
- No network in tests.
- `typescript@5.9.3` exists only for the generator.
- Exact versions.
- Source export only (finding 8).
