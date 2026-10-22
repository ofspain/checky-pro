# frontend · T04 · Phase 10 — Test Manifest

Consumes `04-frozen-task-brief.md` and the resolved implementation (`09-review-resolution.md`). Date: 2026-10-22. All
runs were in `themistra-local-frontend-1`. No production code changed.

## File
`frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts`: Vitest, `describe('lintRejectsHandWrittenBackendFetch')`,
28 results from 7 cases. It sits in `src`, so it is linted itself and passes. Fixture code lives only in string literals,
and the directive pattern is built from parts so the file doesn't match its own scan.

**Harness.** The project's `node_modules/.bin/oxlint` runs on the real `frontend/.oxlintrc.json`, with the lint script's
flags (`--no-ignore -c`). Each fixture is its own file in an `mkdtemp` directory (`lint-l6-*`), removed in `afterAll`.
A non-zero exit is returned as a result, not thrown. There is no network.

## Mapping

| # | Case | Verifies | Assertions |
|---|---|---|---|
| 1 | `reports $form with $rule and the L6 message (AC1)`: `it.each` over 19 forms | AC1, L6 | exit 1, `eslint(<rule>)` in the output, and the exact L6 message. **Globals (3):** bare `fetch()`, `const f = fetch`, `new XMLHttpRequest()`. **Properties (16):** the seven `<object>.fetch` forms (`window`, `globalThis`, `self`, `top`, `parent`, `opener`, `frames`; findings P3-3); three bracket forms (P3-5); `.bind` and `.call` (P3-4); `const { fetch } = window`; `window.XMLHttpRequest`, `self.XMLHttpRequest`, `globalThis['XMLHttpRequest']` (P8-1) |
| 2 | `allows $form (AC3)`: `it.each` over 4 | AC3 | exit 0 for: a parameter named `fetch` and a `const fetch` (P3-8); `createAuthClient().GET('/accounts/me')`; `vi.spyOn(globalThis, 'fetch')` (P3-1) |
| 3 | `passes today's frontend/src through the lint script, running only the two L6 rules (AC4)` | AC4, P7-3 / P8-7 | runs `--no-ignore -c .oxlintrc.json src` from `frontend/`: exit 0, `Found 0 warnings and 0 errors`, `with 2 rules` |
| 4 | `runs lint first in build, with no ignore files honoured (AC2)` | AC2, P8-2 | `scripts.lint === 'oxlint --no-ignore -c .oxlintrc.json src'`; `scripts.build === 'pnpm run lint && tsc --noEmit && vite build'`; `oxlint` `1.87.0` |
| 5 | `keeps a violation listed in .eslintignore failing under the lint script` | P7-2 / P8-2 (AC1 stays enforceable) | builds a temp project with the real config, a violating `src/hidden.ts` and an `.eslintignore` naming it. It then runs the **actual `scripts.lint` arguments**: exit 1, and the file is named in the output |
| 6 | `bans exactly fetch and XMLHttpRequest, as globals and on the seven window objects, with no overrides (AC5)` | AC5, L6 | no `overrides`; the rule keys are exactly the two; both at `error`; the globals are exactly `fetch` and `XMLHttpRequest`; the property pairs are exactly 7 objects × {`fetch`, `XMLHttpRequest`}; every entry carries the L6 message |
| 7 | `finds no inline directive in frontend/src that could silence the rule` | P7-1 / P8-3 | a recursive scan of `src/**/*.{ts,tsx}`, which finds at least one file and no match for `(oxlint\|eslint)-disable` |

**AC2 end to end.** Case 4 pins the order. Phase 6 additionally showed that a real violating `build` stops at lint,
with no `tsc` or Vite output. That end-to-end run is not repeated in the suite, because it would need a violating file
under `src`.

**A correction to phase 6.** The phase 6 notes said "17 AC1 forms". The frozen list has 16, and phase 9 added 3, giving
the 19 here. Each one was probed in phase 6 or phase 9 with the same result shown in this file.

## Results
- `pnpm -C frontend test`: 4 files, **50/50**. That is 22 existing results plus 28 new; this file took about 3.8 s.
- `pnpm -C frontend typecheck`: exit 0.
- `pnpm -C frontend build`: lint ("on 8 files with 2 rules", 0 errors), then `tsc`, then Vite; exit 0.

## Mutation breaks (each applied to a backup, run, then restored from the backup)

| Mutation | Result | Caught by |
|---|---|---|
| `no-restricted-globals` removed | 6 failed | case 1 (3 globals) and case 5, which lints a bare `fetch` (1); case 6 (1); case 3's "2 rules" (1) |
| `window.fetch` entry dropped | 6 failed | case 1: `window.fetch`, `window['fetch']`, `.bind`, `.call`, the destructure (5); case 6 (1) |
| `top` dropped (both entries) | 2 failed | case 1 `top.fetch`; case 6 |
| `XMLHttpRequest` property entries removed | 4 failed | case 1 (3 XHR property forms); case 6 |
| an `overrides` entry turning both rules off for `src/**` | 2 failed | case 6 (`overrides` present); case 5, whose copied config exempts `src/hidden.ts` |
| `lint` removed from `build` | 1 failed | case 4 |
| `--no-ignore` removed from `lint` | 2 failed | case 4; case 5, where the `.eslintignore` now hides the violation |
| an `eslint-disable-next-line` comment appended to `src/app/AppShell.tsx` | 1 failed | case 7 |
| a hand-written `fetch("/accounts/me")` appended to `src/app/AppShell.tsx` | 1 failed | case 3 |

All 9 mutation breaks fail. Afterwards `git status` shows only the new test file (and the user's untracked `prompt`),
and `git diff` is empty.

## Not covered by design (frozen-brief Constraints; phase 9 P8-4/5/6)
- An aliased window object (`const w = window; w.fetch`).
- Top-level `this.fetch` (it is `undefined` in ES modules).
- `Reflect.get` and `Object.getOwnPropertyDescriptor`.
- `document.defaultView.fetch`.

These are residual limits of name-based lint, and are left to review and the T05–T08 client boundary.
