# frontend · T03 · Phase 0 — Repository Understanding

Task: **T03 — Generated client.** "`libs/ts/api-client` is generated from `contracts/api/auth.yaml` and compiles. Verifies
`generatedClientCompiles`. Cites L6, R20." Date: 2026-10-22.

## 1. Architecture summary (after T02, `224db094`)

- **Workspace.**
  - The root `package.json` is the pnpm 12.14.0 workspace root (Node `>=22.22.2`). It has no dependencies or scripts.
  - `pnpm-workspace.yaml` (committed since the scaffold, `4c725d94`) already lists `frontend`, `infra`, `libs/ts/*` and
    `services/crypto/sidecars/*`.
  - `infra/` and the sidecars have no `package.json` today, and neither does `libs/ts/api-client`.
  - The lockfile has importers `.` and `frontend` only.
- **`frontend/`.**
  - Stack: Vite 8.3.5, React 19.3.1, **TypeScript 7.0.2 (the native compiler)**, Vitest 5.0.5, jsdom 30.1.3 and
    react-router 8.4.0.
  - `tsconfig.json`: `strict`, `noEmit`, `moduleResolution: bundler`, `types: ["vite/client"]`,
    `include: ["src", "vite.config.ts"]`.
  - The `src/app/` shell plus the router (T01, T02) make no backend call yet.
- **`libs/ts/api-client/`** holds only `.gitkeep`, from the scaffold. `libs/README.md` describes it as the "TypeScript
  client generated from `contracts/api`, consumed by `frontend/`".
- **The contract.**
  - `contracts/api/auth.yaml`: OpenAPI **3.0.3**, `servers: [{ url: / }]`, 947 lines, 34 `operationId`s across 26 paths.
  - Security schemes: `bearerAuth` (http bearer, JWT) and `apiKeyAuth`.
  - It documents every non-SAS auth-service endpoint (`/accounts`, `/accounts/me`, `/accounts/me/*`, `/admin/*`,
    `/api-keys`, …). By its own description, the SAS endpoints (`/oauth2/**`, `/.well-known/**`, `/userinfo`, `/login`)
    are not in it.
  - The backend proves its controllers against this file in `services/auth/.../common/AuthOpenApiContractTest.java`, so
    the YAML is the source of truth.
  - The other contracts (`payments.yaml`, `notifications.yaml`, `crypto-internal.yaml`) are out of T03's scope; T03 names
    `auth.yaml` only.
- **CI** (`.github/workflows/ci.yml`).
  - The `frontend` paths filter already includes `libs/ts/**` and `contracts/api/**`.
  - The `frontend` job does a frozen install, then runs `pnpm -C frontend test` and `build`.
  - There is no generation step yet.
- **Local stack.**
  - The `infra/local` `frontend` container runs `pnpm install` at `/repo`, then the Vite dev server.
  - `node_modules` volumes exist for the repo root and `frontend/` only. A new workspace package would get its
    `node_modules` on the bind mount, created by root inside the container. T01 already met that root-owned-directory
    effect.

## 2. Existing code this task touches

| Item | State |
|---|---|
| `libs/ts/api-client/` | Exists, empty (`.gitkeep`). Every file in it is new. |
| `pnpm-workspace.yaml` | Already includes `libs/ts/*`. Probably no change needed. |
| `pnpm-lock.yaml` | Gains an importer for the new package. |
| `contracts/api/auth.yaml` | Input only. It must not be modified (`contracts/**` is off-limits for frontend tasks). |
| `.github/workflows/ci.yml` | L6 says the client is "generated in CI", so the job likely gains a generate-and-compile step. |
| `frontend/` | Possibly a dependency on the workspace package. Nothing in `frontend/src` calls the backend yet; T05 onwards own the client boundary in `src/api/`. |

## 3. Established patterns to follow

The backend patterns (JPA/Flyway, outbox, resource-server security) don't apply to a TypeScript library. These do:
- Exact pinned versions, each at least about two weeks old (as in T01 and T02).
- TypeScript `strict`. No `import.meta.env` or absolute URLs in app source (T01's scan covers `frontend/src` only).
  L9: single origin, so the client uses relative paths, which matches the contract's `servers: [{ url: / }]`.
- Tests are co-located Vitest files, each named after the `package.md` §8 test. Mutation breaks are run with backups.
- Errors are RFC 9457 `application/problem+json` (agents.md, "API access"). The UI maps problem types to copy. T03
  generates the types; mapping belongs to later tasks.
- The design.md §6 file map shows `libs/ts/api-client/` as "generated, not hand-edited (L6, R58 scan)", and
  `frontend/src/api/` as the wrapper (401/429 handling, later tasks).

## 4. Testing conventions

- Vitest 5.0.5. The frontend runs it with the jsdom environment.
- A pure compile check is `tsc --noEmit` with TypeScript 7.0.2.
- Tests run inside the `infra/local` `frontend` container.

## 5. Known gaps and unknowns

1. **TypeScript 7 has no classic compiler API.** This blocks most generators.
   - `typescript@7.0.2`'s `exports["."]` is only `./lib/version.cjs`; the AST API sits under `./unstable/*`.
   - The common OpenAPI-to-TS generators build their output with the TS 5/6 compiler API (`ts.factory`):
     - `openapi-typescript@7.13.0` has peer `typescript ^5.x`;
     - `@hey-api/openapi-ts@0.99.0` has peer `typescript >=5.5.3 || >=6`.
   - So such a generator would need its own `typescript` 5.9.3 or 6.0.3 inside the generating package, while generated
     code is still compiled by TS 7. `orval@8.40.0` has no TypeScript peer, but it is far heavier (around 20 `@orval/*`
     packages).
   - The choice is a Phase 1/2 decision. **I do not know** yet which generator the spec intends: none is named in
     `spec/frontend/` or anywhere else in the repo.
2. **Committed vs. generated at build time.** L6 says "generated in CI", and design.md §6 says "generated, not
   hand-edited". Neither says whether the output is committed, with CI checking it is up to date, or only produced in CI.
   I do not know which is intended.
3. **What "compiles" covers.** It could mean the generated package type-checks on its own, or that `frontend/` can import
   it and type-check. To be settled in Phase 1.
4. **The R20 link.** `package.md` §8 maps `generatedClientCompiles` → R20 (read profile from `/userinfo`, account status
   from `GET /accounts/me`). `/userinfo` is a SAS endpoint and is not in `auth.yaml`; `GET /accounts/me` is (line 34).
   How T03 should reflect R20 beyond generating `/accounts/me` is for Phase 1.
5. **Runtime client vs. types only.** L6 says "every backend call uses" the client, which implies a callable client, not
   types alone. The runtime (fetch wrapper) choice is open.
