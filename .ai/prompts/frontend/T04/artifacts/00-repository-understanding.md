# frontend · T04 · Phase 0 — Repository Understanding

Task: **T04 — No hand-written backend fetch.** "A lint rule fails a build that calls `fetch` against a backend route.
Verifies `lintRejectsHandWrittenBackendFetch`. Cites L6." Date: 2026-10-22.

## 1. Architecture summary (after T03, `32b6f77c`)
- **`frontend/`.**
  - Stack: Vite 8.3.5, React 19.3.1, **TypeScript 7.0.2** (native compiler), Vitest 5.0.5, jsdom 30.1.3, react-router
    8.4.0.
  - Scripts:
    - `build` = `tsc --noEmit && vite build`;
    - `typecheck` = `tsc --noEmit`;
    - `test` = `vitest run`.
  - **There is no linter and no `lint` script.** T01 deliberately left ESLint and lint scripts to T04 (T01 frozen brief,
    finding 10), and the T01 Phase 9 restore removed a parallel attempt's ESLint setup.
- **`libs/ts/api-client`** (`@themistra/api-client`, T03) is the sanctioned client. Internally it calls `fetch` through
  `openapi-fetch`. It lives outside `frontend/src`, and its `node_modules` are never part of the app source.
- **`frontend/src` today:** `main.tsx`, `src/app/` (shell, router, routes) and `src/api/` (one test). No production file
  calls `fetch`, which was checked with `grep`.
- **CI** (`frontend` job): frozen install → client generate plus drift → `test` → `build`. So anything wired into `build`
  runs in CI.
- **The single-origin edge (L9).** Every relative path the SPA could request is either a backend prefix (§4c
  note 2026-10-21 (2)) or `/app/*` (the SPA's own assets, which Vite imports rather than fetching).

## 2. Existing code this task touches
| Item | State |
|---|---|
| `frontend/package.json` | Exists. It gains a linter devDependency and a `lint` script, and `build` must run it. |
| Lint config | New. |
| `pnpm-lock.yaml` | Gains the linter. |
| `frontend/src/**` | Read-only target of the rule. It is clean today. |
| `.github/workflows/ci.yml` | Already runs `build`. Possibly an explicit `lint` step. |

## 3. Established patterns to follow
- Exact pinned versions, at least about two weeks old.
- Strict TypeScript.
- A co-located Vitest test named after the `package.md` §8 test.
- Mutation breaks run with backups.
- The repo-root and tool paths are resolved from Vitest's `testPath` and Node resolution (T03 Phase 11).

## 4. Testing conventions
- Vitest runs inside the `infra/local` `frontend` container.
- Tool invocations go through `execFile` (as in the T03 generator and `tsc` cases).

## 5. Known gaps and unknowns
1. **TypeScript 7 rules out ESLint's TypeScript parser.**
   - `typescript-eslint@8.71.1` (the newest that is 2+ weeks old, 2026-10-05) has peer `typescript >=4.8.4 <6.1.0`, and
     so does `@typescript-eslint/typescript-estree`.
   - `frontend/`'s `typescript` is 7.0.2, which has no classic compiler API (T03 Phase 0 and Phase 4 probes).
   - ESLint could only lint `.ts`/`.tsx` through a second `typescript` (6.0.x) in another package. That is the T03
     workaround again, applied to a tool that runs on every build.
2. **oxlint is TS-native.**
   - `oxlint@1.87.0` (2026-10-05) is a Rust linter with its own TS/TSX parser and no TypeScript dependency.
   - Scratch probe (2026-10-22) on a `.tsx` file, with `no-restricted-globals` (`fetch`) and `no-restricted-properties`
     (`window`, `globalThis` and `self` `.fetch`):
     - it flagged all five forms: bare `fetch(...)`, `window.fetch`, `globalThis.fetch`, `self.fetch`, and the alias
       `const f = fetch`;
     - it did **not** flag a locally declared `const fetch = …` (scope-aware);
     - it exited 1.
   - **I do not know** whether the spec intends ESLint specifically. L13 lists no linter, and no spec file mentions one;
     T01's "ESLint" was an out-of-scope note, not a decision.
3. **"Against a backend route"** could mean a targeted rule (flag `fetch` only when the URL is a backend prefix) or a
   blanket rule (ban `fetch` in app code, since under L9 every same-origin fetch is a backend route). To be settled in
   Phase 1/2.
4. **The R59 exception.** The notification stream is a named L6 exception (design.md O7: a fetch-based SSE reader). Its
   file doesn't exist yet (a notification task). How T04 leaves room for it, without opening a hole now, is open.
5. **Forms the probe didn't cover:** `window['fetch']`, destructuring (`const { fetch } = window`) and `XMLHttpRequest`.
   I do not know yet whether the rule must cover them; that is for Phase 1.
