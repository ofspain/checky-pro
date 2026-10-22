# frontend · T03 · Phase 13 — PR / Commit Preparation

Gate: Phase 12 verdict is **PASS**.

Branch note: work lands on `spec/service-specs-and-ai-framework` and is pushed after each phase. For `main`, merge or
squash the branch's final tree.

## Commit title
`frontend: generated auth-service client in libs/ts/api-client (T03)`

## Commit message
```
frontend: generated auth-service client in libs/ts/api-client (T03)

Adds @themistra/api-client, the one sanctioned way the SPA calls the
backend (L6):

- src/generated/auth.ts: types generated from contracts/api/auth.yaml by
  openapi-typescript 7.13.0 (all 34 operations), committed, never
  hand-edited.
- src/index.ts: re-exports paths/components/operations and
  createAuthClient(), an openapi-fetch 0.17.0 client on relative,
  same-origin paths (L9) with no env key (L10).
- typescript 5.9.3 is a generator-only devDependency: openapi-typescript
  needs the TS 5 compiler API, which TypeScript 7 no longer ships. The
  package's typecheck script and the test run the project's TS 7.
- CI regenerates the client and fails on drift; .gitattributes pins LF.
- generatedClientCompiles (6 cases): byte-identical regeneration, TS 7
  strict compile, every operation plus typed GET /accounts/me and no SAS
  endpoint (R20), same-origin GET and POST with middleware, no env key or
  absolute URL in the entry.

No 401/429 wrapper, screens or other contracts yet (T05-T08 and later).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed
- `libs/ts/api-client/`:
  - `package.json`, `tsconfig.json`, `README.md`;
  - `src/index.ts`, `src/generated/auth.ts`;
  - `.gitkeep` removed.
- `frontend/package.json` (workspace dependency) and `pnpm-lock.yaml`.
- `frontend/src/api/generatedClientCompiles.test.ts` (new).
- `.github/workflows/ci.yml` (the drift step) and `.gitattributes` (LF).

## Summary
The third frontend task creates the typed auth-service client that every later backend call goes through. It is
generated, compiled with the project's TypeScript 7, kept honest by a CI drift check, and talks only to the page's own
origin. Nothing on screen changes.

## Testing performed
In the `infra/local` `frontend` container:
- `pnpm install --frozen-lockfile`: OK.
- `typecheck` and `build`: clean. `pnpm -C libs/ts/api-client typecheck` (TS 7.0.2): clean.
- `pnpm -C frontend test`: **22/22**.

Mutation breaks: 9, each caught, with the typecheck included in every run.
- a hand edit;
- a type error;
- `/accounts/me` removed;
- an absolute base;
- an `/api` base;
- a SAS path;
- `components` dropped;
- a wrong `generate` output;
- a narrowed client.

Probes:
- the generator fails under TS 7 (`ts.factory` missing);
- the output is deterministic across 3 runs;
- `''`, `'/'` and an omitted `baseUrl` resolve the same;
- deep imports are refused (`TS2307`).

Reviews:
- Kimi Phase 3: 10 findings (7 accepted, 3 rejected after probes).
- Kimi Phase 8: 7 findings (1 accepted, 1 scheduled; 3 did not reproduce, because its search misses `.github` and dot
  files; 2 had no change).
- Kimi Phase 11: 8 gaps (5 added, 3 rejected as already settled).

## Specification references
- `spec/frontend/tasks.md` task 3 (T03, Generated client).
- Named test: `generatedClientCompiles` (`package.md` §8 → R20).
- LOCKED: L6 (cited); also upheld: L9, L10, L13, design.md §6.
- Requirement: R20 (its `GET /accounts/me` half; `/userinfo` is T30's).
