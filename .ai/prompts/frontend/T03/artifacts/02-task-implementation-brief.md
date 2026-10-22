# frontend · T03 · Phase 2 — Task Implementation Brief

## Task
T03 — **Generated client.** `libs/ts/api-client` is generated from `contracts/api/auth.yaml` and compiles. Verifies
`generatedClientCompiles`. Cites L6, R20.

## Purpose
Create the one sanctioned way the SPA talks to auth-service: a typed client generated from the contract (L6). Later tasks
build on it: T04 (lint), T05–T08 (the 401/429 boundary) and T30/T31 (profile and status).

## Scope
**In**
- The workspace package `libs/ts/api-client` (`@themistra/api-client`).
- A committed generation command. The generated types from `auth.yaml`, committed.
- A thin entry that exports the types and a same-origin runtime client.
- A frontend workspace dependency on the package.
- `generatedClientCompiles`.
- A CI step that regenerates the client and fails on drift.

**Out**
- `payments.yaml` and `notifications.yaml` (their own tasks extend the generation).
- The 401/429 handling and the `frontend/src/api/` wrapper (T05–T08).
- The lint rule (T04).
- Any screen, any real backend call, TanStack Query hooks, and `/userinfo` (an OIDC-client concern, T30).

## Business Rules
- **R20:** the client exposes a typed `GET /accounts/me` (the account-status source). `/userinfo` and the other SAS
  endpoints are not part of it. They aren't in `auth.yaml`, and the OIDC client handles them.

## Locked Decisions
- **L6:** generated client only; generated in CI.
- **design.md §6:** `libs/ts/api-client` is generated, not hand-edited.
- **L9:** single origin, relative paths.
- **L10:** no env, no secret.
- **L13:** TypeScript, Vitest.
- **agents.md, "API access":** RFC 9457 problem types are exposed as generated types.

## Dependencies
All are pinned exactly and are at least two weeks old. Each was probed (2026-10-22): generated from `auth.yaml` and
compiled with TS 7.0.2.

| Package | Version | Released | Purpose |
|---|---|---|---|
| `openapi-typescript` | 7.13.0 | 2026-02-11 | The generator (types only). Peer `typescript ^5.x`. |
| `typescript` | 5.9.3 | 2025-09-30 | Used only by the generator, a devDependency of the api-client package. The probe showed the generator **fails under TS 7.0.2** (`Cannot read properties of undefined (reading 'createKeywordTypeNode')`): TS 7 exposes no `ts.factory`. |
| `openapi-fetch` | 0.17.0 | 2026-02-11 | The runtime client. It is about 6 kB, built on `fetch`, and has a middleware hook for T05–T08. |

The project compiler stays TypeScript 7.0.2 (`frontend/`). The probe confirmed:
- the generated file plus an `openapi-fetch` client type-check with **zero errors** under TS 7.0.2 `strict`;
- the output is byte-identical across three runs (1375 lines; 34 operations).

## Inputs
`contracts/api/auth.yaml`, read-only.

## Outputs
- `libs/ts/api-client/src/generated/auth.ts` (generated, committed).
- `createAuthClient()`, which returns `createClient<paths>({ baseUrl: '' })`.

## State Changes
None.

## Files to Create
- **`libs/ts/api-client/package.json`:**
  - `@themistra/api-client`, `private`, `type: module`;
  - `exports: { ".": "./src/index.ts" }`, a source export with no build step (Vite and TS consume the source);
  - script `generate` = `openapi-typescript ../../../contracts/api/auth.yaml -o src/generated/auth.ts`;
  - dependency `openapi-fetch`; devDependencies `openapi-typescript` and `typescript@5.9.3`.
- **`libs/ts/api-client/tsconfig.json`:** `strict`, `noEmit`, `moduleResolution: bundler`, `lib` `ES2022` and `DOM`,
  `include: ["src"]`.
- **`libs/ts/api-client/src/generated/auth.ts`:** the generator's output, never edited by hand.
- **`libs/ts/api-client/src/index.ts`:**
  - re-exports `paths`, `components` and `operations`;
  - exports `createAuthClient()`, whose `baseUrl` is `''` (relative, same origin);
  - no env and no absolute URL.
- **`libs/ts/api-client/README.md`:** says the code is generated and must not be edited, and gives the regenerate
  command.
- **`frontend/src/api/generatedClientCompiles.test.ts`.**

Remove `libs/ts/api-client/.gitkeep`.

## Files to Modify
- **`frontend/package.json`:** add `"@themistra/api-client": "workspace:*"`.
- **`pnpm-lock.yaml`:** gains the new importer.
- **`.github/workflows/ci.yml`:** in the `frontend` job, after install, run
  `pnpm -C libs/ts/api-client generate`, then `git diff --exit-code -- libs/ts/api-client`.

## Files NOT to Modify
- `contracts/**`, `spec/**`, `services/**`, `infra/**`.
- `frontend/src/app/**` and `frontend/vite.config.ts`.
- `pnpm-workspace.yaml` (it already lists `libs/ts/*`).
- `prompt`.

## Acceptance Criteria
- **AC1 (L6, §6):** the client is produced by the committed `generate` command. Only `index.ts`, the config files and the
  README are hand-written.
- **AC2 (task):** `libs/ts/api-client` type-checks under TypeScript 7.0.2 `strict`, with zero errors.
- **AC3 (L6):** regenerating from `auth.yaml` reproduces `src/generated/auth.ts` byte for byte.
- **AC4 (R20):**
  - `paths['/accounts/me']['get']` exists, with a typed 200 response;
  - no generated path starts with `/oauth2`, `/.well-known`, `/userinfo`, `/login`, `/logout` or `/connect`.
- **AC5 (L9, L10):**
  - a `createAuthClient().GET('/accounts/me')` call, with `fetch` stubbed, requests the same-origin path
    `/accounts/me`;
  - `src/index.ts` has no absolute URL and no `import.meta.env`.
- **AC6 (L6):** CI regenerates the client and fails on drift. The test also runs in the existing `frontend` job.

## Required Tests
`generatedClientCompiles` (`frontend/src/api/`, Vitest) has five cases:
1. **AC3:** it runs the generator into a temp file and byte-compares the result with the committed file.
2. **AC2:** it runs TS 7 (`frontend`'s `tsc`) with `--noEmit -p libs/ts/api-client/tsconfig.json` and expects exit 0.
3. **AC4:** it checks `/accounts/me` GET (a type-level reference compiled by `frontend` `typecheck`), and runs a
   path-key scan of the generated file for SAS prefixes.
4. **AC5:** a stubbed `fetch` receives a same-origin `/accounts/me` request.
5. **AC5:** a source scan of `index.ts`.

Mutation breaks:
- a hand edit in `auth.ts`;
- a type error introduced in `index.ts`;
- `/accounts/me` removed from the generated file;
- an absolute `baseUrl`.

## Constraints
- `strict` TypeScript.
- No env, no secret, no absolute URL.
- The generated file is never hand-edited.
- No runtime network in tests: `fetch` is stubbed.
- `typescript@5.9.3` is a devDependency of the api-client only and is never imported by app code.
- Exact versions.

## Open Questions
No blockers. Defaults:
1. `openapi-typescript` + `openapi-fetch`. They are the smallest pair that compiles under TS 7; `orval` is far heavier,
   and `@hey-api/openapi-ts` also peers on the TS compiler (not probed).
2. The generated output is committed, and CI regenerates it and checks for drift. That satisfies "generated in CI" and
   keeps local dev and typecheck working without a generation step.
3. The named test lives in `frontend/`, where Vitest and TS 7 already are.
