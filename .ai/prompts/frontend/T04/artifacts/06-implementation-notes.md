# frontend · T04 · Phase 6 — Implementation Notes

Consumes `04-frozen-task-brief.md` and `05-implementation-plan.md`. Date: 2026-10-22. All commands ran in
`themistra-local-frontend-1` (`/repo`).

## What changed

| File | Change | Plan / AC |
|---|---|---|
| `frontend/package.json` | devDependency `"oxlint": "1.87.0"` (exact, kept in alphabetical order). Script `lint` = `oxlint -c .oxlintrc.json src`. `build` = `pnpm run lint && tsc --noEmit && vite build`. | Plan step 1; AC2, AC4 |
| `pnpm-lock.yaml` | +217 lines: `oxlint@1.87.0` and its `@oxlint/binding-*` optional platform binaries, including `linux-x64-gnu` (CI), `darwin-arm64` and `darwin-x64` (host) and `linux-arm64-musl` (the container). | Plan step 1 |
| `frontend/.oxlintrc.json` | New. `$schema` points at the package's own `configuration_schema.json`, which is present in 1.87.0, so no network is needed. All seven `categories` are `off`. `no-restricted-globals` is `error` for `fetch` and `XMLHttpRequest`. `no-restricted-properties` is `error` for `.fetch` on `window`, `globalThis`, `self`, `top`, `parent`, `opener` and `frames`. Every entry has the L6 message. No `overrides`, `plugins` or `ignorePatterns`. | Plan step 2; AC1, AC5 |

No other file changed. The T01–T03 tests are untouched (finding 1). The test file is written in phase 10, following
the phase 6 prompt and the T03 precedent.

## Verification

- **Install.** `pnpm install` added 2 packages. `pnpm install --frozen-lockfile` reports "Lockfile is up to date". The
  frontend container was restarted after the dependency change.
- **Only L6 rules run.** `pnpm -C frontend lint` reports "0 warnings and 0 errors … on 7 files with 2 rules" and exits 0
  (AC4). Default plugins add no rules, so `plugins: []` was not needed (plan contingency not used).
- **Each AC1 form**, as its own fixture under `/tmp/fx`, linted with the real config. Each one exits 1 with exactly one
  diagnostic carrying the L6 message:
  - `no-restricted-globals`: bare `fetch()`, `const f = fetch`, `new XMLHttpRequest()`;
  - `no-restricted-properties`: the 7 `<object>.fetch` forms, the 3 bracket forms, `window.fetch.bind`,
    `window.fetch.call`, and `const { fetch } = window`.

  The rule ids match the plan's `BANNED` table, so the table is fixed as planned.
- **Each AC3 form exits 0:**
  - a parameter named `fetch`;
  - a `const fetch`;
  - `createAuthClient().GET('/accounts/me')` with its import;
  - `vi.spyOn(globalThis, 'fetch')`.
- **Build (AC4):** `pnpm -C frontend build` → lint, `tsc` and Vite all pass (`index-*.js` 312.59 kB), exit 0.
- **AC2 end to end (the manual check in the plan):**
  - `frontend/src/l6Probe.ts` was added temporarily, holding `fetch("/accounts/me")`;
  - `build` printed the `no-restricted-globals` error at `src/l6Probe.ts:1:24` and exited 1;
  - it printed no `tsc` diagnostics and no Vite lines (0 matches for `vite v|transforming|built in`);
  - the probe file was then removed, and its absence was confirmed.
- **Existing suite:** `pnpm -C frontend test` → 3 files, 22/22.

## Deviations
None.

A detail of oxlint's output: for `no-restricted-globals`, the configured message is appended to the headline
("Unexpected use of 'fetch'. Call the backend …"). The `help:` line is then oxlint's generic hint. For
`no-restricted-properties`, the configured message is the `help:` line. Either way the L6 text appears exactly once
per diagnostic. The phase 10 test asserts that the message is present, not where it sits.
