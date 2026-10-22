# frontend · T01 · Phase 9 — Review Resolution

Date: 2026-10-22. Consumes `07-self-review.md` and `08-independent-review.md` (Kimi 2.7).

## What Kimi reviewed

Kimi reviewed a different implementation from the one Phase 6 produced, and its Open Question 1 noticed this. Git history
shows why:

- `9f7aca56` (Phases 4–7) committed the frozen-brief implementation: `AppShell.tsx`, `mountShell.tsx`, pnpm 12.14.0,
  React 19.3.1, Vite 8.3.5, Vitest 5.0.5, TypeScript 7.0.2, jsdom 30.1.3.
- `2ee1c1be` ("extraneous", parent `273c87e9`, so made without seeing Phases 4–7) added a separate T01 attempt:
  - React Router (`App.tsx`, `Shell.tsx` rendering "Checky Pro");
  - ESLint (`eslint.config.js`, `lint` scripts);
  - `@testing-library/*`, `App.test.tsx`, `src/test/setup.ts`, `tsconfig.node.json`;
  - pnpm 9.12.2, React 18, Vite 5, Vitest 2, TypeScript 5;
  - an unrelated `crypto-branch-review.html` at the repo root.
- Merge `cbfad357` resolved every file the two touched in favour of `2ee1c1be` (`package.json`, `frontend/package.json`,
  `vite.config.ts`, `tsconfig.json`, `main.tsx`, `pnpm-lock.yaml`). Files only `9f7aca56` had survived (`AppShell.tsx`,
  `mountShell.tsx`, README, `ci.yml`), but nothing used them.

Kimi's 13 findings are therefore all deviations from the frozen brief, and each of them recommends restoring what the brief
specifies. Kimi's finding 9 ("no workflow file") and its "Invalid package.json" container failure don't reproduce here:
`ci.yml` with the `frontend` job is present at HEAD, and the frontend container starts cleanly once the frozen files are
back (below). Both are artifacts of Kimi's view of the merged tree.

## Resolution

| Source | Comment | Decision | Change |
|---|---|---|---|
| Kimi 1 | React Router imported | ACCEPTED | `App.tsx`, `Shell.tsx` deleted; `react-router-dom` gone from `frontend/package.json` |
| Kimi 2 | Shell renders "Checky Pro" text | ACCEPTED | `Shell.tsx` deleted; the shell is `AppShell` (`<div data-testid="app-shell" />`) again |
| Kimi 3, 8 | ESLint and `lint` scripts | ACCEPTED | `eslint.config.js` deleted; lint scripts and ESLint deps removed (T04 owns them) |
| Kimi 4 | No `mountShell` / null guard | ACCEPTED | `main.tsx` restored to call `mountShell` (which was still present) |
| Kimi 5 | Test cases incomplete | ACCEPTED | `App.test.tsx` deleted; the four frozen cases are written in Phase 10 |
| Kimi 6 | No `typecheck` script | ACCEPTED | `frontend/package.json` restored (`build` = `tsc --noEmit && vite build`, `typecheck` = `tsc --noEmit`) |
| Kimi 7 | Versions and `packageManager` off-plan | ACCEPTED | Root and frontend `package.json` and `pnpm-lock.yaml` restored from `9f7aca56` (pnpm 12.14.0 and the Phase 5 versions) |
| Kimi 9 | CI job missing | REJECTED (does not reproduce) | `.github/workflows/ci.yml` at HEAD already has the frozen `frontend` job and filter |
| Kimi 10 | README not updated | REJECTED (does not reproduce) | `frontend/README.md` at HEAD already has the frozen command list |
| Kimi 11 | `@testing-library/*` | ACCEPTED | Removed with the restored `frontend/package.json`; tests use `act()` and the DOM directly |
| Kimi 12 | `defineConfig` from `vite` | ACCEPTED | `vite.config.ts` restored (imports from `vitest/config`) |
| Kimi 13 | `App.tsx` redundant | ACCEPTED | Deleted (as for 1) |
| Self 1 | Build case under jsdom | ACCEPTED, handled in Phase 10 | Checked when the test runs; split by per-file environment only if needed |
| Self 2 | Root `engines.node` below jsdom's floor | ACCEPTED | Root `package.json` `engines.node` is now `>=22.22.2` |
| Self 3 | `mountShell` twice → double root | REJECTED | Only `main.tsx` calls it; tests use a fresh container and `unmount()` |
| Self 4 | No pnpm cache in CI | REJECTED (deferred) | CI speed isn't a T01 concern |
| Self 5 | `server.host: true` | REJECTED | Required by the frozen brief and the Docker stack |
| Self 6, 7 | `allowedHosts`, static `<title>` | No change (Info) | Accepted in Phase 4 |

Also deleted: `frontend/tsconfig.node.json` and `frontend/src/test/setup.ts`, the support files of the removed attempt.
`crypto-branch-review.html` is not T01's and is left untouched.

After resolution:
- `git diff 9f7aca56 -- frontend pnpm-lock.yaml .github` is empty;
- the root `package.json` differs only by the engines floor;
- the recreated `frontend` container logs `pnpm v12.14.0`, then `VITE v8.3.5 ready`;
- `GET /app/src/main.tsx` through the edge → 200.

**Approval note:** this gate deletes files from a commit made under the developer's identity. They are recoverable from
`2ee1c1be`. Deleting them follows the frozen brief and Kimi's own recommendations, and it is called out to the developer in
the hand-back message, so they can say if that attempt should be kept.
