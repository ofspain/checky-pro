# frontend · T03 · Phase 6 — Implementation Notes

Date: 2026-10-22. Implements `04-frozen-task-brief.md`, following `05-implementation-plan.md`.

## Changes
- **`libs/ts/api-client/`:**
  - `package.json` (`@themistra/api-client`, source export `"."` only, the `generate` script, exact versions);
  - `tsconfig.json` (`strict`, `bundler`);
  - `src/index.ts` (type re-exports and `createAuthClient()` with `baseUrl: ''`);
  - `README.md` (do-not-edit notice, the regenerate command, and the TS 5.9/7 note);
  - `src/generated/auth.ts`: 1375 lines, written by `pnpm -C libs/ts/api-client generate`, not by hand;
  - `.gitkeep` removed.
- **`frontend/package.json`:** `"@themistra/api-client": "workspace:*"`.
- **`pnpm-lock.yaml`:** gains the `libs/ts/api-client` importer.
  - Install added 33 packages: `openapi-fetch` with its helpers at runtime, and `openapi-typescript`, `@redocly/*` and
    `typescript@5.9.3` as devDependencies of the client only.
  - The `frontend` container was restarted afterwards, as the handoff note requires.
- **`.github/workflows/ci.yml`:** in the `frontend` job, a step after install, "Generate API client and fail on drift
  (L6)". It runs `generate`, then `git diff --exit-code -- libs/ts/api-client/src/generated`.
- **`.gitattributes`:** `libs/ts/api-client/src/generated/** text eol=lf`.

## Verification (in the `infra/local` `frontend` container)
- `frontend/node_modules/typescript/bin/tsc` (7.0.2), run with `--noEmit -p libs/ts/api-client/tsconfig.json`: clean
  (AC2).
- **Workspace import.** A temporary `frontend/src/_ok.ts` imported `createAuthClient` and `paths` from
  `@themistra/api-client` and referenced `paths['/accounts/me']['get']['responses']['200']['content']['application/json']`.
  `pnpm -C frontend typecheck` was clean. The file was deleted.
- **Deep-import probe (frozen finding 7).** A temporary `frontend/src/_deep.ts` imported
  `@themistra/api-client/src/generated/auth`. The result was `TS2307: Cannot find module`. The `exports` map makes the
  boundary structural. The file was deleted.
- **Drift.** Regenerating after install left `git status` unchanged for `src/generated` (AC3, by hand; Phase 10
  automates it).
- `pnpm install --frozen-lockfile`: OK. `pnpm -C frontend test`: 16/16 (T01 and T02 suites). `pnpm -C frontend build`:
  clean.
- Live: `/app/home` through the edge → 200 after the restart.

## Deviations
None. The README's TS-version note was added in Phase 7 self-review item 2, before this commit.
