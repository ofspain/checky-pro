# frontend · T04 · Phase 2 — Task Implementation Brief

## Task
T04 — **No hand-written backend fetch.** A lint rule fails a build that calls `fetch` against a backend route. Verifies
`lintRejectsHandWrittenBackendFetch`. Cites L6.

## Purpose
Make L6 mechanical. App code can't reach the backend except through `@themistra/api-client`, and a violation fails the
build, and therefore CI.

## Scope
**In**
- A linter for `frontend/`.
- A config holding only the L6 rules.
- A `lint` script, run first in `build`.
- `lintRejectsHandWrittenBackendFetch`.

**Out**
- General code-quality lint rules or a style config (no task asks for them).
- The R59 stream exception itself (the notification task adds one file-scoped override).
- `navigator.sendBeacon`, dynamic `import()` and other non-fetch transports. None of them is how the SPA would call an
  API, and none is named by the task.
- `libs/ts/api-client` (its `fetch` is the sanctioned one, via `openapi-fetch`).

## Business Rules
None are cited. R59 bounds the single future exception.

## Locked Decisions
- **L6:** generated client only. The stream is the one named exception.
- **agents.md, "API access":** no hand-written `fetch` to backend routes.
- **L9:** single origin. Every app-code request to a relative path is a backend route.
- **L12:** extend, don't rewrite.

## Dependencies
- **`oxlint` 1.87.0**, exact, a devDependency of `frontend`. Released 2026-10-05.
  - Rust; it parses TS/TSX itself; no TypeScript dependency.
  - It was chosen over ESLint because `typescript-eslint@8.71.1` has peer `typescript <6.1.0` and can't parse with the
    project's TS 7.0.2 (Phase 0).
- **Probed 2026-10-22.** `no-restricted-globals` plus `no-restricted-properties` flag every listed form, all with exit 1:
  - bare `fetch()`;
  - `window.fetch`, `globalThis.fetch`, `self.fetch`;
  - `window['fetch']`;
  - `const { fetch } = window`;
  - `const f = fetch`;
  - `new XMLHttpRequest()`.

  They do not flag a locally declared `fetch`. An `overrides` entry exempts exactly one named file.

## Inputs
`frontend/src/**/*.{ts,tsx}`.

## Outputs
Lint diagnostics. A non-zero exit fails `lint`, and therefore `build`.

## State Changes
None.

## Files to Create
- **`frontend/.oxlintrc.json`:**
  - `categories` set so that only the configured rules run;
  - `no-restricted-globals`: `fetch` and `XMLHttpRequest`, each with the message "Call the backend through
    @themistra/api-client (L6).";
  - `no-restricted-properties`: `fetch` on `window`, `globalThis` and `self`;
  - no `overrides`.
- **`frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts`.**

## Files to Modify
- **`frontend/package.json`:**
  - add the `oxlint` devDependency;
  - `lint` = `oxlint -c .oxlintrc.json src`;
  - `build` = `pnpm run lint && tsc --noEmit && vite build`.
- **`pnpm-lock.yaml`.**

## Files NOT to Modify
- `frontend/src/**` production code.
- `libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`.
- `.github/workflows/ci.yml` (`build` already runs lint).
- `prompt`.

## Acceptance Criteria
- **AC1 (L6):** each form above in a `frontend/src` file is reported, and lint exits non-zero.
- **AC2 (task):** with one violation present, `pnpm -C frontend build` exits non-zero before `tsc` and Vite run.
- **AC3:** a locally declared `fetch` is not reported, and neither is a `createAuthClient().GET(...)` call.
- **AC4:** today's `frontend/src` lints clean, and `build` passes.
- **AC5:** the config has no `overrides` (nothing is exempt yet). The R59 exception will be one `overrides` entry naming
  one file.

## Required Tests
`lintRejectsHandWrittenBackendFetch` (Vitest, `frontend/src/api/`). It runs the project's `oxlint` against fixture files
written to a temp directory, using the real `frontend/.oxlintrc.json`:
1. **AC1:** each banned form, one fixture each, is reported with its rule id, and lint exits 1.
2. **AC3:** the allowed fixtures exit 0.
3. **AC4:** the real `frontend/src` exits 0.
4. **AC2:** `scripts.build` runs `lint` first, and `scripts.lint` lints `src` with the config.
5. **AC5:** the config has no `overrides`, and both rules are at `error`.

Mutation breaks:
- a rule removed;
- `window` dropped from the properties list;
- the `lint` step removed from `build`;
- an override added that exempts `src`.

## Constraints
- Exact version.
- The lint config contains only the L6 rules.
- No production code changes.
- Tests don't touch the network.
- Fixtures live in a temp directory, never under `frontend/src`, so they can't fail the real lint.

## Open Questions
No blockers. Defaults:
1. **oxlint** rather than ESLint, because of the TypeScript 7 constraint.
2. **A blanket ban** in app code rather than a URL-targeted rule. Under L9, every relative request is a backend route,
   and a URL check is bypassed by any computed URL.
3. **`XMLHttpRequest` is banned too**, because it is the other way to hand-write the same call.
