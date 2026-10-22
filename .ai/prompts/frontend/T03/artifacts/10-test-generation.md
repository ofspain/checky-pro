# frontend · T03 · Phase 10 — Test Generation (test manifest)

Consumes the FROZEN brief and `09-review-resolution.md`. Date: 2026-10-22. No production code was changed in this phase.

## Test file
`frontend/src/api/generatedClientCompiles.test.ts` contains one `describe('generatedClientCompiles')`. It is the named
test (`package.md` §8 → R20), with five cases.

The repo root comes from Vitest's `testPath`, as in T01.

| # | Case | Verifies |
|---|---|---|
| 1 | It checks the resolved package is `@themistra/api-client`. It runs the package's own `openapi-typescript` bin on `contracts/api/auth.yaml` into a temp file, and asserts it is byte-identical to the committed `src/generated/auth.ts`. | AC1, AC3 |
| 2 | It runs `frontend`'s TS (asserting `Version 7.0.2`) with `--noEmit -p libs/ts/api-client/tsconfig.json`, and expects exit 0 with empty output. | AC2; frozen finding 1 |
| 3 | **Type level:** `expectTypeOf` on `paths['/accounts/me']['get']['responses']['200']['content']['application/json']`, which `frontend` `typecheck` enforces. **Runtime:** `/accounts/me` is among the generated path keys, no path key starts with a SAS prefix, and the generated `operations` names equal the `auth.yaml` `operationId`s as a sorted set. | AC4, R20; frozen finding 9 |
| 4 | It installs browser-semantics `Request` (relative URLs resolved against `window.location`) and a stubbed `fetch` via `vi.stubGlobal`. Then it runs `createAuthClient()`, registers an `onRequest` middleware, and calls `GET('/accounts/me')`. Exactly one fetch is made, to `${location.origin}/accounts/me` with `GET`, and the middleware saw that URL. | AC5, L9; frozen findings 4 and 10 |
| 5 | `libs/ts/api-client/src/index.ts` contains no `import.meta.env` and no `https?://`. | AC5, L10 |

`afterEach` runs `vi.unstubAllGlobals()`. No case touches the network.

## Results (in the `infra/local` `frontend` container)
- `pnpm -C frontend typecheck`: clean. This also type-checks the test's type-level assertion and the package source,
  through the workspace import.
- `pnpm -C frontend test`: **21 passed (21)**. That is `generatedClientCompiles` 5, `routesResolveUnderAppPrefixOnly` 9
  and `shellBuildsAndMounts` 7.
- `pnpm -C frontend build`: clean.

## Mutation breaks
Each break was applied alone, run, then restored from a backup. `diff` confirmed the restore.

| Break | Failing cases | Result |
|---|---|---|
| a) Hand edit in `auth.ts` (a comment line added) | 1 | 1 failed, 4 passed |
| b) Type error in `index.ts` | 2 | 1 failed, 4 passed |
| c) `/accounts/me` removed from the generated file | 1, 3 | 2 failed, 3 passed. `frontend` `typecheck` would fail as well. |
| d) Absolute `baseUrl` (`https://auth.example.com`) | 4, 5 | 2 failed, 3 passed |
| e) Prefixed `baseUrl` (`/api`) | 4 | 1 failed, 4 passed |
| f) A SAS path (`/userinfo`) added to the generated file | 1, 3 | 2 failed, 3 passed |

The five breaks in the frozen brief, plus extra break f, are all caught.
