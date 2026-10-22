# frontend · T03 · Phase 1 — Specification Extraction

Consumes `00-repository-understanding.md`. Date: 2026-10-22.

## Business Rules
- **R20:** the SPA reads profile data from `/userinfo` (scopes `openid profile email`) and account status from
  `GET /accounts/me`, and never reads PII or verification state from the access token.
  - T03's share is the generated, typed operation for `GET /accounts/me` (`auth.yaml` line 34).
  - `/userinfo` is a SAS endpoint and is deliberately absent from `auth.yaml`. The OIDC client reads it (T30), not the
    generated client.
  - Using the generated client to read the profile and the account status belongs to T30/T31.
- Context, not implemented here: R58. Nothing secret or env-derived goes into the generated client (L10).

## Locked Decisions
- **L6:** every backend call uses `libs/ts/api-client`, generated in CI from `contracts/api/*.yaml`. The single exception is
  the notification stream (R59). For T03 this means:
  - the client is generated from the contract, not hand-written;
  - it is a callable client, not just types, because "every backend call uses" it;
  - it is produced by a generation step that CI runs.
- **design.md §6:** `libs/ts/api-client/` is "generated, not hand-edited (L6, R58 scan)". `frontend/src/api/` is the
  wrapper for 401/429 handling, and belongs to later tasks.
- **L9:** single origin. The client uses relative paths with no per-service base URL, which matches the contract's
  `servers: [{ url: / }]`.
- **L13:** the confirmed stack (TypeScript, Vitest). TanStack Query is part of L13, but its hooks are not in T03.
- **L10:** no secret in the bundle. The generated client reads no env key.
- **agents.md, "API access":**
  - no hand-written `fetch` to backend routes;
  - errors are RFC 9457 `application/problem+json`, which the generated types should expose for later mapping.

## Files involved
- **Read:** `contracts/api/auth.yaml` (input, read-only), `libs/README.md`, `pnpm-workspace.yaml` (already lists
  `libs/ts/*`), `.github/workflows/ci.yml`, `frontend/tsconfig.json` and `frontend/package.json`.
- **New (expected by the spec):** the contents of `libs/ts/api-client/`:
  - a package manifest;
  - a generation script/config;
  - the generated sources;
  - a small entry point that exports the client and types.
- **Modified:**
  - `pnpm-lock.yaml`, for the new importer;
  - `.github/workflows/ci.yml`, because L6 says "generated in CI";
  - possibly `frontend/` if the named test lives there (Vitest and TS 7 are installed there).

## Dependencies
- The contract `contracts/api/auth.yaml` (OpenAPI 3.0.3; 34 operations; `bearerAuth` and `apiKeyAuth` schemes).
- A generator and a runtime fetch client. None is named in the spec.
  - Phase 0 found that TypeScript 7.0.2 exposes no classic compiler API.
  - TS-API-based generators therefore need their own TS 5.9 or 6.0 in the generating package.
- The TypeScript 7.0.2 compiler (`frontend/`), to prove the output compiles under the project's compiler.
- No config keys and no env.

## Acceptance Criteria
| # | Criterion | Source |
|---|---|---|
| AC1 | `libs/ts/api-client` contains a client generated from `contracts/api/auth.yaml` by a committed, reproducible generation command. Nothing in it is hand-written except the thin entry/config. | task, L6, §6 |
| AC2 | The generated client compiles under the project's TypeScript (7.0.2, `strict`) with zero errors. | task ("compiles"), L13 |
| AC3 | The generated output matches the contract: regenerating from `auth.yaml` reproduces it exactly, so it is not stale or hand-edited. | L6 ("generated in CI"), §6 |
| AC4 | The client exposes a typed `GET /accounts/me` operation (R20's account-status source). It has no `/userinfo` or other SAS operation. | R20, `auth.yaml` description |
| AC5 | The client uses relative, same-origin paths, with no absolute URL and no env key. | L9, L10 |
| AC6 | CI generates the client and checks that it compiles. | L6 |

## Tests required
- **`generatedClientCompiles`** (named; `package.md` §8 → R20): AC2–AC5, plus the AC1 reproducibility half.
- Boundary checks implied:
  - a stale or hand-edited output is detected;
  - a compile error in the output is detected;
  - a missing `/accounts/me` operation is detected.

## Open Questions
None are blockers; `package.md` §11 has no open item on the client. These are left for the Phase 2 defaults:
1. Which generator and runtime to use. The spec doesn't name one, and the TS 7 constraint is in Phase 0.
2. Whether the output is committed (with CI checking for drift) or produced only in CI.
3. Where the named test lives, given that Vitest and TS 7 live in `frontend/`.
