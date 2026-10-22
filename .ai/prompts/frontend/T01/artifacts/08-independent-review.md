# frontend · T01 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T01 — Shell renders |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/` source tree, `artifacts/04-frozen-task-brief.md`, `artifacts/06-implementation-notes.md`, `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **Shell renders.** The Vite, React, and TypeScript app builds and mounts an empty shell. Verifies `shellBuildsAndMounts`. Cites L13.

---

## Review scope

I reviewed the files currently in the repository against the **frozen brief** (`artifacts/04-frozen-task-brief.md`) and the standing rules in `spec/frontend/agents.md`. I did not rewrite any code. Several findings below are deviations from the frozen brief rather than subtle bugs.

---

## Findings

### 1. React Router is imported despite being explicitly out of scope
**Issue ·** The frozen brief lists “Any router (T02)” in the **Out** section and states T01 imports no router. The current code imports `react-router-dom` in both `src/app/App.tsx` and `src/app/Shell.tsx`, and `Shell.tsx` contains `Routes`, `Route`, and `Navigate`.  
**Evidence ·** `frontend/src/app/App.tsx:1` (`import { BrowserRouter } from 'react-router-dom'`); `frontend/src/app/Shell.tsx:1` (`import { Routes, Route, Navigate } from 'react-router-dom'`); `frontend/package.json:17` (`"react-router-dom": "^6.27.0"`).  
**Recommendation ·** Remove `react-router-dom` from dependencies and from `App.tsx`/`Shell.tsx`. The shell should render a single empty element (`<div data-testid="app-shell" />`) with no routing.  
**Confidence ·** High — direct conflict with frozen brief §Scope Out and §Locked Decisions (L13 minimal stack).

### 2. The React shell renders user-facing text
**Issue ·** Frozen AC4 states “the React shell renders no text content.” `Shell.tsx` renders `<div data-testid="home-route">Checky Pro</div>`, which is visible text inside the React tree.  
**Evidence ·** `frontend/src/app/Shell.tsx:7`.  
**Recommendation ·** Replace the routed content with the empty shell element required by the frozen brief: `<div data-testid="app-shell" />`. The static `<title>Checky Pro</title>` in `index.html` is the only permitted text.  
**Confidence ·** High.

### 3. ESLint tooling and lint scripts were added prematurely
**Issue ·** The frozen brief defers ESLint and lint scripts to T04. The current tree includes `frontend/eslint.config.js`, a `lint` script in both root and frontend `package.json`, and six ESLint-related devDependencies.  
**Evidence ·** `frontend/eslint.config.js`; `package.json:12` (`"lint": "pnpm -C frontend lint"`); `frontend/package.json:12,26-29,32`.  
**Recommendation ·** Remove the ESLint config, the `lint` scripts, and the ESLint dependencies for T01. Re-introduce them in T04.  
**Confidence ·** High — frozen brief §Scope Out.

### 4. Missing `mountShell` abstraction and null-container test
**Issue ·** The frozen brief requires `src/app/mountShell.tsx` that throws a clear error when given a null container, and AC6 requires a test for it. The current `main.tsx` mounts directly using `document.getElementById('root')!` and there is no `mountShell` function or null-container test.  
**Evidence ·** `frontend/src/main.tsx:5-9`; no `src/app/mountShell.tsx`; `frontend/src/app/App.test.tsx` has no null-container assertion.  
**Recommendation ·** Introduce `mountShell(container: HTMLElement | null)` that throws on null, returns the React `Root`, and wrap it with `StrictMode`. Update `main.tsx` to call it. Add the required `mountShell(null)` throws test case.  
**Confidence ·** High — frozen brief §Files to Create and §AC6.

### 5. Required `shellBuildsAndMounts` test cases are incomplete
**Issue ·** The frozen required tests specify four cases: (1) programmatic Vite build asserts `/app/` in `index.html`, (2) mount renders empty shell, (3) negatives (no fetch/storage/worker/import.meta.env), (4) null throws. The current test file only checks that the shell renders and that a route shows text.  
**Evidence ·** `frontend/src/app/App.test.tsx`.  
**Recommendation ·** Replace/extend the test with the four frozen cases under one `describe('shellBuildsAndMounts')`. Remove the route-specific assertions because routing is out of scope.  
**Confidence ·** High.

### 6. Missing `typecheck` script
**Issue ·** The frozen brief requires `frontend/package.json` scripts `dev`, `build`, `test`, `typecheck`. The current package.json lacks `typecheck`.  
**Evidence ·** `frontend/package.json:6-13`.  
**Recommendation ·** Add `"typecheck": "tsc --noEmit"` and change `build` to `"tsc --noEmit && vite build"` to match the frozen brief exactly.  
**Confidence ·** High.

### 7. Dependency versions and `packageManager` do not match the frozen plan
**Issue ·** The frozen plan resolved versions in `node:22-alpine`: pnpm `12.14.0`, React `19.3.1`, Vite `8.3.5`, Vitest `5.0.5`, TypeScript `7.0.2`, jsdom `30.1.3`. The current files use pnpm `9.12.2`, React `^18.3.1`, Vite `^5.4.10`, Vitest `^2.1.3`, TypeScript `^5.6.3`, jsdom `^25.0.1`. The root `engines.node` is `>=20` instead of `>=22.12`.  
**Evidence ·** `package.json:4,6`; `frontend/package.json:15-34`.  
**Recommendation ·** Align with the frozen plan: set root `packageManager` to `pnpm@12.14.0`, `engines.node` to `>=22.12`, and pin the frontend dependencies to the exact versions resolved in `node:22-alpine`. Regenerate `pnpm-lock.yaml` in that environment.  
**Confidence ·** High. (Note: the local-stack frontend service runs `node:22-alpine`; the version mismatch may be contributing to the observed corepack/package.json failure.)

### 8. Root `package.json` includes an out-of-scope `lint` script
**Issue ·** The frozen brief says the root `package.json` has no dependencies; it does not list a `lint` script. The current root has a `lint` script that points to the deferred T04 lint script.  
**Evidence ·** `package.json:8-13`.  
**Recommendation ·** Remove the `lint` script from root and frontend `package.json` for T01.  
**Confidence ·** High.

### 9. Missing CI job
**Issue ·** The frozen brief requires modifying `.github/workflows/ci.yml` to add a `frontend` job gated on a `frontend` changes filter. No such workflow file exists in the repository.  
**Evidence ·** `glob .github/workflows/*` returns nothing.  
**Recommendation ·** Create `.github/workflows/ci.yml` with the required `changes` job (using `dorny/paths-filter@v3` or equivalent) and the `frontend` job as specified in the frozen brief.  
**Confidence ·** High.

### 10. `frontend/README.md` not updated with the frozen command list
**Issue ·** The frozen brief requires updating `frontend/README.md` with the local-stack commands. The current README only contains a generic one-line description.  
**Evidence ·** `frontend/README.md`.  
**Recommendation ·** Add the frozen commands: `infra/local/bin/up --frontend`, `infra/local/bin/status --wait`, the app URL, and the `docker compose … exec frontend pnpm -C frontend test` / `build` commands.  
**Confidence ·** High.

### 11. `@testing-library/react` is used where the frozen plan does not require it
**Issue ·** The frozen plan uses `act()` from `react` directly and does not list `@testing-library/react` as a devDependency. The current test imports `render`, `screen` from `@testing-library/react`. This is not a defect per se, but it is extra surface area not justified by T01.  
**Evidence ·** `frontend/src/app/App.test.tsx:2`; `frontend/package.json:21`.  
**Recommendation ·** For T01, keep tests minimal: use `act()` from `react` and direct DOM assertions, and remove `@testing-library/react` (and `@testing-library/user-event`, which is unused).  
**Confidence ·** Medium — acceptable if retained, but contradicts the minimal L13 subset.

### 12. `vite.config.ts` imports `defineConfig` from `vite`, not `vitest/config`
**Issue ·** The frozen plan specifies using Vitest's `defineConfig` from `vitest/config` so build, dev, and test share one config. The current file imports from `vite`. With Vitest 2.x this still works, but it deviates from the frozen plan and may be a minor source of config drift when upgrading to Vitest 5.x.  
**Evidence ·** `frontend/vite.config.ts:1`.  
**Recommendation ·** Change the import to `import { defineConfig } from 'vitest/config';` once the dependency versions are aligned.  
**Confidence ·** Low — functional today, but a plan deviation.

### 13. `App.tsx` is an unnecessary wrapper
**Issue ·** The frozen brief has `AppShell.tsx` as the shell and `mountShell.tsx` as the mount function; there is no `App.tsx`. The current `App.tsx` only exists to inject `BrowserRouter`. Once routing is removed, `App.tsx` becomes redundant.  
**Evidence ·** `frontend/src/app/App.tsx`.  
**Recommendation ·** Delete `App.tsx` and render `<AppShell />` directly from `mountShell`.  
**Confidence ·** High once finding #1 is accepted.

---

## Open Questions

- The current implementation does not match the Phase 6 implementation described in `artifacts/06-implementation-notes.md` (different file names, versions, dependencies, and structure). Should Phase 8 be retargeted to the code currently in the repo (as done here), or should the missing Phase 6 work be completed first?
- The local-stack frontend container failed with `Invalid package.json in package.json`. Is this caused by the pnpm version mismatch (root `packageManager` = 9.12.2 vs. the frozen plan's 12.14.0), or by a separate corepack issue in `node:22-alpine`?

---

## Summary

The current tree builds and the existing Vitest tests pass on the host, but it **does not implement the frozen brief for T01**. The most significant deviations are the inclusion of React Router, ESLint, visible shell text, missing `mountShell`, incomplete tests, and missing CI/README updates. None of these are subtle bugs; they are scope and specification mismatches that need to be reconciled before T01 can be considered complete.
