# frontend · T04 · Phase 1 — Specification Extraction

Consumes `00-repository-understanding.md`. Date: 2026-10-22.

## Business Rules
- No requirement is cited. T04 implements lock L6.
- **R59** marks the only allowed exception: the notification stream's fetch-based reader. It is not implemented here.
  The rule must allow that one named file when its task arrives.

## Locked Decisions
- **L6:** every backend call uses `libs/ts/api-client`. The single exception is the notification stream (R59, O7), which
  is a *named* exception.
- **agents.md, "API access":** "No hand-written `fetch` to backend routes. The only exception is named in `design.md` L6."
- **L9:** single origin. Every relative request goes through the edge, which sends `/app*` to the SPA and every other
  prefix to a backend. So any app-code `fetch` to a non-`/app` path is a backend route.
- **L13:** the confirmed stack. It names no linter, so the linter is a tooling choice, not a lock.
- **L12:** extend T01–T03, don't rewrite them.

## Files involved
- **Read and extend:** `frontend/package.json` (scripts, devDependencies) and `pnpm-lock.yaml`.
- **New:**
  - a lint config for `frontend/`;
  - the named test `lintRejectsHandWrittenBackendFetch`.
- **Possibly:** `.github/workflows/ci.yml`. `build` already runs in CI, so an explicit step is only for clearer failure
  output.
- **Not touched:**
  - `libs/ts/api-client/**`: the sanctioned fetch lives there, outside the linted tree;
  - `frontend/src/**` production code: clean today.

## Dependencies
- A linter that parses TS/TSX under the project's TypeScript 7 setup. Phase 0 shows ESLint + typescript-eslint can't
  without a second TypeScript; oxlint 1.87.0 can.
- No contracts, no config keys and no env.

## Acceptance Criteria
| # | Criterion | Source |
|---|---|---|
| AC1 | A lint step rejects app code in `frontend/src` that calls `fetch` directly: bare, via `window`, `globalThis` or `self`, or through an alias. | task, L6 |
| AC2 | The build fails when that lint step fails: `pnpm -C frontend build` exits non-zero, so CI's `build` fails. | task ("fails a build") |
| AC3 | Calls through `@themistra/api-client` and local identifiers named `fetch` are not flagged (no false positive on the sanctioned path). | L6 |
| AC4 | Today's `frontend/src` passes lint, so the build stays green. | L12 |
| AC5 | The R59 stream exception can be granted later, to one named file only, without loosening the rule elsewhere. Nothing is exempt yet. | L6 (named exception) |

## Tests required
- **`lintRejectsHandWrittenBackendFetch`** (named; `package.md` §8 → L6): AC1–AC4, plus the config half of AC5.
- Boundary checks implied:
  - each fetch form is flagged;
  - a shadowed `fetch` is not flagged;
  - the build script invokes lint before the compile step.

## Open Questions
None are blockers; `package.md` §11 has no linter item. These are left for the Phase 2 defaults:
1. Which linter: oxlint (TS-native) or ESLint (needs TS ≤ 6.0 alongside TS 7).
2. A blanket ban versus a URL-targeted rule.
3. Whether to also ban `XMLHttpRequest` and computed or destructured access (`window['fetch']`,
   `const { fetch } = window`).
