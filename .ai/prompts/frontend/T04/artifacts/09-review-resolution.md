# frontend · T04 · Phase 9 — Review Resolution (Human Approval gate)

STATUS: RESOLVED (2026-10-22)

Consumes `07-self-review.md` (6 findings) and `08-independent-review.md` (Kimi 2.7, 10 findings). Every claim was probed
with oxlint 1.87.0 in the frontend container before it was dispositioned. None is an architectural trade-off, so they
are settled here, as at the T01–T03 gates.

## Probe results (before the fixes)

| Fixture | Exit |
|---|---|
| `new window.XMLHttpRequest()` | 0 (**bypass**) |
| `new self.XMLHttpRequest()` | 0 (**bypass**) |
| `new globalThis['XMLHttpRequest']()` | 0 (**bypass**) |
| `const w = window; w.fetch('/x')` | 0 |
| `this.fetch('/x')` | 0 |
| `Reflect.get(window, 'fetch')` | 0 |

## Resolution log

| Source | Finding | Decision | Change |
|---|---|---|---|
| P8-1 | `XMLHttpRequest` as a property of the window objects isn't banned | **ACCEPTED** (confirmed bypass) | `frontend/.oxlintrc.json`: `no-restricted-properties` gains `XMLHttpRequest` on the same seven objects, with the same L6 message. That gives 14 entries: `fetch` × 7, then `XMLHttpRequest` × 7. |
| P8-2 / P7-2 | `.eslintignore` can hide a file | **ACCEPTED** | `frontend/package.json`: `lint` = `oxlint --no-ignore -c .oxlintrc.json src`. |
| P8-3 / P7-1 | Inline disable directives silence the rule | **ACCEPTED** (test-side) | No config or flag can forbid them in 1.87.0. Phase 10 adds a case that scans `frontend/src/**/*.{ts,tsx}` and fails on any `oxlint-disable` or `eslint-disable` text. Today there are none. |
| P8-7 / P7-3 | Assert that exactly 2 rules run | **ACCEPTED** (test-side) | Phase 10 case 3 asserts that the real-`src` output contains `with 2 rules`. |
| P8-8 | The named test doesn't exist yet | **No change** | Expected. The phase 6 prompt defers tests to phase 10 (T03 precedent). |
| P8-4 | An aliased window object (`const w = window; w.fetch`) | **REJECTED for T04; residual, documented** | Banning `window` itself would flag every legitimate `window` use. Name-based lint can't follow aliases. This falls under the frozen brief's "deeper aliasing is out of scope" constraint, and the T05–T08 client boundary and review cover it. |
| P8-5 | `this.fetch` at the top level | **REJECTED; residual** | The SPA is ES modules, where top-level `this` is `undefined`, so the call throws at runtime and cannot reach the backend. This is the module semantics. Kimi attributes it to TS `alwaysStrict`, but either way it can't make a request. |
| P8-6 | `Reflect.get` / `Object.getOwnPropertyDescriptor` | **REJECTED; residual** | The frozen brief's Constraints already name `Reflect.get(window, 'fetch')` as out of scope. |
| P8-9 / P7-4 | `$schema` resolves only after install | **No change** | Editor-only, with no lint impact. |
| P8-10 / P7-5 | Only `src` is linted | **No change** | Matches the brief: it scopes app code. `index.html` has no inline script. |
| P7-6 | Tests are bound by the ban | **No change** | Deliberate. `vi.stubGlobal` and `vi.spyOn` are allowed (AC3). |

## Deviation from the frozen brief (recorded)

- **The `lint` script string** changes from `oxlint -c .oxlintrc.json src` to `oxlint --no-ignore -c .oxlintrc.json src`.
  Phase 10 case 4 asserts the new string.
- **The AC1 form list** gains `window.XMLHttpRequest`, `self.XMLHttpRequest` and `globalThis['XMLHttpRequest']`.
- **The AC5 / case 5 assertion** becomes: the restricted pairs are exactly {7 objects} × {`fetch`, `XMLHttpRequest`}.
- **New test-side items:** the directive scan (case 6) and the "2 rules" assertion.
- **New mutation breaks:**
  - the `XMLHttpRequest` property entries removed;
  - `--no-ignore` removed, with an `.eslintignore` fixture;
  - a disable directive added to a `src` file.

All of these tighten the frozen guarantee (L6). None widens scope or touches production code.

## Verification after the changes

- `pnpm -C frontend lint` reports "0 warnings and 0 errors … on 7 files with 2 rules" and exits 0.
- **Re-probe:**
  - all three `XMLHttpRequest` property fixtures now exit 1;
  - all 17 earlier AC1 fixtures still exit 1;
  - all 4 AC3 fixtures still exit 0.
- **`.eslintignore` check:** a temp project with the same `package.json` and config, and an `.eslintignore` listing
  `src/hidden.ts`. `pnpm lint` now exits 1; before the change it exited 0.
- `pnpm -C frontend build` exits 0, and `pnpm -C frontend test` is 22/22.
