# frontend · T04 · Phase 4 — Frozen Task Brief (Human Approval gate)

STATUS: FROZEN (2026-10-22)

Consumes `02-task-implementation-brief.md` and `03-design-challenge.md` (Kimi 2.7: 10 findings). Kimi raised finding 1 as
High; the rest are Low or Info.

Each finding was checked against the repository and against `oxlint@1.87.0`, which was run inside the frontend container
with the planned config, before it was dispositioned. None is an architectural trade-off, so they are settled here, as
at the T01–T03 gates.

## What the checks showed

- **Today's `frontend/src` lints clean under the planned config.** oxlint reports "0 warnings and 0 errors" on 7 files
  and exits 0.
  - The only `fetch` mentions in `src` are `vi.spyOn(globalThis, 'fetch')` (T01) and `vi.stubGlobal('fetch', …)` (T03).
  - Both pass `fetch` as a string argument. Neither is a `globalThis.fetch` member access, so `no-restricted-properties`
    doesn't match them.
  - A fixture of exactly `vi.spyOn(globalThis, "fetch")` exits 0.
- **Chained and bracket forms are already caught.** Each of these is reported by `no-restricted-properties` and exits 1:
  - `window.fetch.bind(window)` and `window.fetch.call(window, …)`;
  - `globalThis['fetch'](…)` and `self['fetch'](…)`;
  - `const { fetch: f } = globalThis`.
- **Other window aliases are not caught by the Phase 2 list.** `top.fetch` and `parent.fetch` exit 0. When `top`,
  `parent`, `opener` and `frames` are added as `no-restricted-properties` objects, all four exit 1.
- **A local `fetch` is not reported.** A function parameter named `fetch` and a `const fetch = …` both exit 0.
- **`.oxlintrc.json` is oxlint's own default name.** `oxlint --help` shows `-c=<./.oxlintrc.json>`.
- **CI's frontend job already runs `pnpm -C frontend build`** (`.github/workflows/ci.yml` line 63).

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| 1 | T01 test's `vi.spyOn(globalThis, 'fetch')` breaks AC4 | **REJECTED (premise false).** It is a call taking a string argument, not a property access. oxlint passes the real `src` and that exact fixture with exit 0. No test changes and no override are needed, so AC4 and AC5 stand. Test case 3 lints the real `src` and would catch any regression. |
| 2 | The task wording is narrower than the ban | **ACCEPTED (note).** The verbatim task statement lives in `spec/` and is not edited. The brief now says how it is read: under L9 every relative app-code request is a backend route, so the rule bans `fetch` and `XMLHttpRequest` in app code outright, with no URL check. |
| 3 | `top`, `parent` and `opener` are not covered | **ACCEPTED.** `top`, `parent`, `opener` and `frames` join `window`, `globalThis` and `self` in `no-restricted-properties`. The probe confirms all four are then caught. The list stays bounded: deeper chains such as `document.defaultView.fetch` are out of scope (see Constraints). |
| 4 | Chained `window.fetch.bind/call` not probed | **ACCEPTED.** It is already caught (see above). Fixtures for `window.fetch.bind(window)` and `window.fetch.call(window, '/x')` are added to case 1. |
| 5 | `globalThis['fetch']` and `self['fetch']` not probed | **ACCEPTED.** It is already caught. Fixtures for both are added to case 1. |
| 6 | The blanket ban catches runtime static-asset fetches | **ACCEPTED (note).** Static assets are imported or referenced by URL at build time. A future runtime asset fetch needs its own task with a file-scoped override or an ADR amendment to L6/L9. |
| 7 | `sendBeacon` and `EventSource` are not banned | **ACCEPTED as a deferral.** Both stay out of T04. The owner is the notification-stream task (R59), which already adds the only override and is where non-fetch transports to backend routes get reviewed. It is recorded under Open Questions. |
| 8 | No fixture for a local `fetch` | **ACCEPTED.** Case 2 gets two fixtures: a parameter named `fetch`, and a `const fetch` declaration, each called. Both must exit 0. |
| 9 | Dotfile config name | **REJECTED (no change).** `.oxlintrc.json` is oxlint's default config name. |
| 10 | CI already runs `build` | **No change.** Confirmed. |

## Open Questions

None block T04.

- **Deferred to the notification-stream task (R59):** whether `EventSource` or `navigator.sendBeacon` to backend routes
  needs a rule. That task owns the one stream override.
- **Resolved:** finding 1 (see above).

---

## Frozen brief

### Task
T04 — **No hand-written backend fetch.** A lint rule fails a build that calls `fetch` against a backend route. Verifies
`lintRejectsHandWrittenBackendFetch`. Cites L6.

**How the task is read (finding 2):** under L9 every relative request from app code is a backend route. The rule
therefore bans `fetch` and `XMLHttpRequest` in app code outright, with no URL check. A URL check could be bypassed by any
computed URL.

### Purpose
Make L6 mechanical. App code reaches the backend only through `@themistra/api-client`, and a violation fails the build,
and therefore CI.

### Scope
**In**
- oxlint for `frontend/`.
- A config holding only the L6 rules.
- A `lint` script, run first in `build`.
- `lintRejectsHandWrittenBackendFetch`.

**Out**
- General code-quality or style rules.
- The R59 stream override (the notification task).
- `navigator.sendBeacon`, `EventSource` and dynamic `import()` (deferred; see Open Questions).
- Runtime static-asset fetches (finding 6).
- `libs/ts/api-client`, whose `fetch` is the sanctioned one, via `openapi-fetch`.

### Locked decisions
- **L6:** generated client only; the stream is the one named exception.
- **agents.md, "API access":** no hand-written `fetch` to backend routes.
- **L9:** single origin.
- **L12:** extend, don't rewrite.

### Dependencies (exact)
- `oxlint` 1.87.0 (released 2026-10-05), a devDependency of `frontend`.
  - It parses TS/TSX itself and needs no TypeScript dependency.
  - It was chosen over ESLint because `typescript-eslint@8.71.1` peers on `typescript <6.1.0`.

### Files to create
- **`frontend/.oxlintrc.json`:**
  - every `categories` entry set to `off`, so that only the configured rules run;
  - `no-restricted-globals` at `error` for `fetch` and `XMLHttpRequest`, each with the message "Call the backend
    through @themistra/api-client (L6).";
  - `no-restricted-properties` at `error`, the same message, for property `fetch` on `window`, `globalThis`, `self`,
    `top`, `parent`, `opener` and `frames`;
  - no `overrides`.
- **`frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts`.**

### Files to modify
- **`frontend/package.json`:**
  - add the devDependency `"oxlint": "1.87.0"`;
  - `lint` = `oxlint -c .oxlintrc.json src`;
  - `build` = `pnpm run lint && tsc --noEmit && vite build`.
- **`pnpm-lock.yaml`.**

### Files not to modify
- Any other `frontend/src/**` file, including the T01–T03 tests (finding 1).
- `libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`.
- `.github/workflows/ci.yml`.
- `prompt`.

### Acceptance criteria
- **AC1 (L6):** each banned form in a fixture file is reported with its rule id, and lint exits 1. The forms are:
  - bare `fetch()`;
  - `window.fetch`, `globalThis.fetch`, `self.fetch`;
  - `top.fetch`, `parent.fetch`, `opener.fetch`, `frames.fetch`;
  - `window['fetch']`, `globalThis['fetch']`, `self['fetch']`;
  - `window.fetch.bind(window)` and `window.fetch.call(window, …)`;
  - `const { fetch } = window`;
  - `const f = fetch`;
  - `new XMLHttpRequest()`.
- **AC2 (task):** with one violation present, `build` exits non-zero before `tsc` and Vite run.
- **AC3:** these are not reported, and lint exits 0:
  - a parameter named `fetch`;
  - a `const fetch` declaration;
  - a `createAuthClient().GET(...)` call;
  - `vi.spyOn(globalThis, 'fetch')`.
- **AC4:** the real `frontend/src` lints clean, and `build` passes.
- **AC5:** the config has no `overrides`, and both rules are at `error`.

### Required tests: `lintRejectsHandWrittenBackendFetch`
In `frontend/src/api/`, Vitest. The test runs the project's `oxlint` against fixtures written to a temp directory,
using the real `frontend/.oxlintrc.json`:
1. **AC1:** one fixture per banned form. Each is reported with its rule id and exits 1.
2. **AC3:** the allowed fixtures exit 0.
3. **AC4:** the real `frontend/src` exits 0.
4. **AC2:** `scripts.build` runs `pnpm run lint` first, and `scripts.lint` is exactly `oxlint -c .oxlintrc.json src`.
5. **AC5:** the config has no `overrides`; both rules are at `error`; and the restricted object list is exactly the
   seven names.

Mutation breaks (each must fail its case):
- a rule removed;
- `window` dropped from the properties list;
- `top` dropped from the properties list;
- the `lint` step removed from `build`;
- an override added that exempts `src`.

### Constraints
- Exact version.
- The config contains only the L6 rules.
- No production code changes.
- No network in tests.
- Fixtures live in a temp directory, never under `frontend/src`.
- The rule is bounded to the listed globals and objects. Deeper aliasing (`document.defaultView.fetch`,
  `Reflect.get(window, 'fetch')`, indirect `eval`) is out of scope. Review and the client boundary (T05–T08) cover it,
  not lint.
