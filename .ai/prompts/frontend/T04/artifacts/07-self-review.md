# frontend · T04 · Phase 7 — Self-Review

Reviews the phase 6 diff (`aa0bff2f`): `frontend/package.json`, `frontend/.oxlintrc.json` and `pnpm-lock.yaml`. It
checks the diff against the frozen brief and `spec/frontend/agents.md`.

Three possible bypasses were probed in the frontend container with oxlint 1.87.0 and the real config, before they were
written up:
- **Inline directives.** `// oxlint-disable-next-line no-restricted-globals`, `// eslint-disable-next-line` and
  `/* oxlint-disable */` each make a violating fixture exit 0.
- **`.eslintignore`.** One listing `src/hidden.ts` makes lint exit 0 with "No files found to lint". With `--no-ignore`,
  the same setup exits 1.
- **`.gitignore` and nested configs** do not bypass the rule:
  - a `.gitignore` entry still exits 1;
  - a nested `.oxlintrc.json` turning both rules `off` still exits 1, because `-c` takes precedence.

Findings only. Fixes, if any, are applied in phase 9.

## Findings

### 1. Inline disable directives silently turn the L6 rule off
- **Issue:** a single `// oxlint-disable-next-line` or `// eslint-disable-next-line` comment above a `fetch(...)` makes
  lint pass. oxlint 1.87.0 has no flag to forbid directives; `--report-unused-disable-directives` only reports unused
  ones. So the "violation fails the build" guarantee can be defeated in one line, and a reviewer may not notice.
- **Severity:** Medium.
- **Evidence:** `frontend/package.json:8` (`lint` has no directive control). The probe fixtures `d1`–`d3` exit 0.
- **Recommendation:** add a guard to `lintRejectsHandWrittenBackendFetch` that scans `frontend/src/**/*.{ts,tsx}` and
  fails on any `oxlint-disable` or `eslint-disable` directive. Today there are none, and the config runs only the L6
  rules, so any directive in `src` can only be aimed at L6. When the R59 task adds its file override, the exception stays
  in the config, where AC5 and review see it, not in a comment.

### 2. An `.eslintignore` file can hide a source file from lint
- **Issue:** oxlint reads `.eslintignore` by default. A file listed there is skipped, and lint exits 0. `frontend/` has
  no ignore file today, but adding one is a quiet bypass.
- **Severity:** Low.
- **Evidence:** `frontend/package.json:8`. The probe shows exit 0 with `.eslintignore` and exit 1 with `--no-ignore`.
- **Recommendation:** make `lint` `oxlint --no-ignore -c .oxlintrc.json src`. Everything under `src` is app code, so
  nothing there should be ignored. This changes the frozen script string, so phase 9 records it as a deviation and
  updates the case 4 assertion to match.

### 3. A future oxlint version could bring rules back on through an unlisted category
- **Issue:** the config turns off the seven categories that 1.87.0 knows. If a later oxlint adds a category that is on
  by default, rules beyond L6 would start running. The version is pinned exactly, so this can only happen on a
  deliberate bump.
- **Severity:** Low.
- **Evidence:** `frontend/.oxlintrc.json:3` (`categories`). In phase 6, lint reported "with 2 rules".
- **Recommendation:** in case 3 (real `src`), also assert that the output reports exactly 2 rules. A bump that widens
  the rule set then fails a test, not a reviewer.

### 4. `$schema` resolves only after install
- **Issue:** `./node_modules/oxlint/configuration_schema.json` is missing until `pnpm install` runs, so editors show no
  schema before then. oxlint ignores `$schema`, so lint is unaffected.
- **Severity:** Info.
- **Evidence:** `frontend/.oxlintrc.json:2`.
- **Recommendation:** none. It is local, needs no network, and is the documented form.

### 5. Only `src` is linted
- **Issue:** `vite.config.ts` and `index.html` are outside the lint path.
- **Severity:** Info.
- **Evidence:** `frontend/package.json:8`. `index.html` contains no inline script, only `<script type="module"
  src="/src/main.tsx">`. `vite.config.ts` is build-time Node config, not app code.
- **Recommendation:** none. The brief scopes the rule to app code (`frontend/src`).

### 6. Tests under `src` are bound by the ban too
- **Issue:** test files are linted, so a future test can't write `globalThis.fetch = …`. It has to use
  `vi.stubGlobal('fetch', …)` or `vi.spyOn(globalThis, 'fetch')`. Both are allowed (AC3) and both are already used by
  T01 and T03.
- **Severity:** Info.
- **Evidence:** the AC3 fixture `ok_spy` exits 0. The `src` lint passes over both existing tests.
- **Recommendation:** none. Holding test code to the same rule is the stricter, deliberate choice.

## Checklist (no finding)
- **Correctness:**
  - all 17 AC1 forms exit 1 with the expected rule id and the L6 message;
  - all 4 AC3 forms exit 0;
  - the real `src` exits 0;
  - a violating `build` stops at lint, with no `tsc` or Vite output.
- **Module boundaries:** no production code changed. `libs/ts/api-client` is outside the lint path, so `openapi-fetch`'s
  sanctioned `fetch` is untouched.
- **Secrets / enumeration-safety:** the config and scripts hold no secrets, env keys or URLs.
- **Exact versions:** `oxlint` `1.87.0` (released 2026-10-05; more than two weeks old). The lockfile carries the CI
  (`linux-x64-gnu`), host (`darwin-*`) and container (`linux-arm64-musl`) bindings, and `--frozen-lockfile` passes.
- **Not applicable:** null-safety, thread-safety, transactions, idempotency and money types. There is no runtime code.
- **Readability and complexity:** the config is one flat file with two rules, the same message on every entry, and no
  overrides.
