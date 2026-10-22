# frontend · T01 · Phase 12 — Specification Verification

Date: 2026-10-22. Consumes Phases 0–11. Verified against `tasks.md` task 1, `design.md` §4a (L9–L14), `package.md` §8, the
FROZEN brief and `agents.md`.

## Phase 11 gaps (Kimi), resolved before verification

The phase 11 review raised 8 gaps.
- 7 were accepted and added to `shellBuildsAndMounts`, which now has 7 cases.
- 1 was rejected.
- Each accepted gap was proved with a mutation break: applied alone, it fails exactly its case.
- Only the test file changed; there was no production change.

| Gap | Decision | Change | Break caught |
|---|---|---|---|
| 1 cwd-dependent directory | ACCEPTED | The directory comes from Vitest's `expect.getState().testPath` instead of `process.cwd()`. It passes under `pnpm -C frontend test` and also under `vitest run --root frontend` started from the repo root. | — (fragility fix) |
| 2 dev-server contract | ACCEPTED | New case: imports `vite.config.ts`; asserts `base '/app/'`, `host true`, `port 5173`, `strictPort true`, no `proxy`. | proxy added; port changed |
| 3 hard-coded URLs | ACCEPTED | The source scan also rejects any `http(s)://` in app source. | absolute URL in shell |
| 4 `main.tsx` boundary | ACCEPTED | New case: the entry's only import is `./app/mountShell`, and its only statement is the `mountShell` call. | extra import; extra statement |
| 5 worker at build level | ACCEPTED | The build case asserts no `sw.js`, no `manifest.webmanifest` and no `serviceWorker` in emitted JS. | worker registration (caught by build case and runtime case) |
| 6 every script under `/app/` | ACCEPTED | The build case asserts every `<script src>` matches `/app/assets/*.js`, and at least one exists. | — (covered by the earlier `base: '/'` break) |
| 7 `mountShell` returns a Root | REJECTED | The return type `Root` is enforced by `tsc --noEmit` (`strict`) in `build`/`typecheck`, and `afterEach` calls `unmount()` on it. A runtime `typeof` check would add nothing. | — |
| 8 whitespace-only text | ACCEPTED | The mount case asserts `innerHTML === ''`. | whitespace text |

The six Phase 10 breaks still apply: each assertion they relied on was kept or made stricter.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| Task 1: builds (L13) | Yes | `frontend/package.json` `build` = `tsc --noEmit && vite build`; `frontend/vite.config.ts:6-17` | Case "builds for production…" (`shellBuildsAndMounts.test.tsx:54`); CI `build` step | No | No |
| Task 1: mounts an empty shell (L13) | Yes | `frontend/src/app/mountShell.tsx:11-16`; `frontend/src/app/AppShell.tsx:4`; `frontend/src/main.tsx` | "mounts the empty shell…" (`:95`) | No | No |
| L13 stack (Vite, React, TS, Vitest) | Yes | `frontend/package.json` (react 19.3.1, vite 8.3.5, typescript 7.0.2, vitest 5.0.5, jsdom 30.1.3); nothing outside L13 | Indirect (every case runs on it) | No | No |
| L14 under `/app` | Yes | `vite.config.ts:7` | `:54` (every script under `/app/assets/`), `:86` (`base`) | No | No |
| L9 single origin, no proxy | Yes | `vite.config.ts:9-13` (no `proxy`) | `:86`; `:120` (no absolute URL) | No | No |
| L10 / R58 no env key | Yes | no `import.meta.env` in `src/` | `:120`; T13 still owns the bundle scan | No | No |
| L11 no hard-coded copy | Yes | `AppShell.tsx:4` renders no text; `<title>` static (Phase 4 disposition 2) | `:95` (`innerHTML === ''`) | No | Accepted: static `<title>` |
| L12 extension point | Yes | `src/app/` root component; one mount function | `:128` (entry only calls `mountShell`) | No | No |
| AC3 no network / storage / worker | Yes | no `fetch`, storage or worker code | `:103` (runtime), `:54` (build output) | No | No |
| AC5 dev server via the edge | Yes | `vite.config.ts:9-13`; `infra/local` `frontend` service | `:86` (config); live checks in `06-implementation-notes.md` and `09-review-resolution.md` | No | Live part recorded, not automated (frozen) |
| AC6 null root throws | Yes | `mountShell.tsx:8-10` | `:140` | No | No |
| CI job | Yes | `.github/workflows/ci.yml:30` (filter), `frontend` job | CI runs `test` and `build` | No | No |
| Root workspace `package.json` | Yes | `package.json:4` (`pnpm@12.14.0`), `:6` (`node >=22.22.2`) | `pnpm install --frozen-lockfile` OK in the container | No | Engines raised from `>=22.12` (self-review 2) |
| README commands | Yes | `frontend/README.md` "Run and test locally" | n/a | No | No |

Recorded deviations, all justified and none against a lock:
- `@types/node@22.20.5`, scoped to the test (Phase 10).
- The engines floor of `>=22.22.2` (Phase 9).
- The `infra/local/bin/up` fix, committed separately (Phase 6).

## Principal-engineer answers

1. **Fully complete?** Yes. The shell builds, mounts and is served through the edge at `/app/` with hot reload.
   `shellBuildsAndMounts` (7 cases) passes in the `infra/local` container, and so do `typecheck`, `build` and
   `pnpm install --frozen-lockfile`.
2. **Every acceptance criterion?** Yes. AC1–AC4 and AC6 are automated. AC5's config half is automated, and its live half
   (through Caddy, plus hot reload) was checked twice, after Phase 6 and after the Phase 9 restore.
3. **Any lock violated?** No. L9, L10, L11, L12, L13 and L14 are each upheld and tested. No router, worker, env key, lint
   tooling or extra library was added.
4. **Remaining risks:**
   - The live half of AC5 isn't automated. A Playwright smoke through the edge arrives with the first task that names an
     E2E test.
   - The branch history contains the `2ee1c1be`/`cbfad357` divergence. The final tree is clean, but whoever squashes for
     `main` should take the tree, not replay the commits.
   - TypeScript 7 (native compiler) is new. Pinned exactly, it has caused no issues.
   - CI hasn't run yet: the job runs on the first PR or `main` push. It does exactly what was run locally, on Node 22.

**PASS**: the shell builds, mounts and is served at `/app/` through the edge, and all 7 `shellBuildsAndMounts` cases pass
with every mutation break caught and no lock violated.
