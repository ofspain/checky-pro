# frontend · T04 · Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T04 — No hand-written backend fetch**. Findings are against the brief itself; no code was changed.

---

## Findings

### 1. Existing test files contain a banned form (`globalThis.fetch`)
**Issue:** The T01 test (`frontend/src/app/shellBuildsAndMounts.test.tsx`) uses `vi.spyOn(globalThis, 'fetch')`, which is a `fetch` property access on `globalThis`. The T04 config bans `fetch` on `globalThis` via `no-restricted-properties`. Therefore, AC4 (“today’s `frontend/src` lints clean”) will fail unless an override is added or the test is changed. AC5 explicitly forbids overrides.  
**Severity:** High — blocks T04 acceptance as currently written.  
**Evidence ·** T01 test manifest/implementation: `vi.spyOn(globalThis, 'fetch')` in the “makes no network call” case. T04 brief §Dependencies: probed forms include `globalThis.fetch`; §AC5: “the config has no `overrides`.”  
**Recommended brief amendment:** Either (a) change AC5 to allow one override for `*.test.ts(x)` files, or (b) add a Files to Modify entry for `frontend/src/app/shellBuildsAndMounts.test.tsx` (and any other test using the pattern) replacing `vi.spyOn(globalThis, 'fetch')` with `vi.stubGlobal('fetch', vi.fn())`, which uses a string literal and is not flagged by property-access rules. Option (b) is preferred because it keeps the blanket ban clean.

### 2. The task statement and the implementation differ in scope
**Issue:** The task statement says “A lint rule fails a build that calls `fetch` against a backend route.” The brief implements a blanket ban on `fetch` (and `XMLHttpRequest`) in all `frontend/src` code, because under L9 every relative request is a backend route. This is broader than the task statement and could surprise a reader.  
**Severity:** Low — the justification is sound, but the wording should match.  
**Evidence ·** Task statement (verbatim) in `03-design-challenge.md`: “A lint rule fails a build that calls `fetch` against a backend route.” Brief §Dependencies/§AC1: bans bare `fetch`, `window.fetch`, `XMLHttpRequest`, etc., unconditionally.  
**Recommended brief amendment:** Update the task statement in the brief to: “A lint rule fails a build that calls `fetch` (or `XMLHttpRequest`) anywhere in app code; under L9 every relative request is a backend route, so no URL check is needed.”

### 3. Other `window`-family globals are not covered
**Issue:** The probed list covers `window.fetch`, `globalThis.fetch`, and `self.fetch`. It does not cover `top.fetch`, `parent.fetch`, or `opener.fetch`, all of which are `WindowProxy` references to `fetch` in a browser context and could be used to bypass the rule.  
**Severity:** Low — unlikely in normal code, but a gap in the blanket ban.  
**Evidence ·** Brief §Dependencies: listed forms are `fetch()`, `window.fetch`, `globalThis.fetch`, `self.fetch`, `window['fetch']`, `const { fetch } = window`, `const f = fetch`, `new XMLHttpRequest()`.  
**Recommended brief amendment:** Add `top`, `parent`, and `opener` to the `no-restricted-properties` object list, or document that these are intentionally out of scope because they only apply to framed contexts and the SPA runs at the top level.

### 4. Chained property access (`window.fetch.bind(...)`) is not explicitly probed
**Issue:** The probe confirmed `window.fetch` is flagged. It is unclear whether `window.fetch.bind(window)`, `window.fetch.call(window, ...)` or `window.fetch.apply(window, ...)` are also flagged. An implementer relying on the probe could miss these bypasses.  
**Severity:** Low — edge case, but the brief claims “every listed form” is reported.  
**Evidence ·** Brief §Dependencies: “`window.fetch`” listed; no chained forms mentioned.  
**Recommended brief amendment:** Either probe and document chained forms, or add a test fixture for `window.fetch.bind(window)` in the T04 test suite to confirm it is caught. If oxlint does not catch it, the rule choice may need revisiting.

### 5. Dynamic property access (`globalThis['fetch']`) is not explicitly probed
**Issue:** The probe lists `window['fetch']` as flagged, but does not mention `globalThis['fetch']` or `self['fetch']`. If the property-access rule matches only literal property names on specific objects, bracket access on the same objects should be covered, but it is not stated.  
**Severity:** Low — likely covered by the same rule, but unstated.  
**Evidence ·** Brief §Dependencies: only `window['fetch']` probed.  
**Recommended brief amendment:** Add a test fixture (or note) that `globalThis['fetch']` and `self['fetch']` are also reported.

### 6. The blanket ban may catch legitimate static-asset fetches
**Issue:** Under the blanket ban, code like `fetch('/icons/sprite.svg')` or `fetch('/locales/en.json')` would be flagged. These are same-origin relative paths but are not backend API routes. The brief argues every relative request is a backend route under L9, but that conflates static assets with API calls.  
**Severity:** Low — no current screen needs this, and the SPA should load static assets through imports.  
**Evidence ·** Brief §Locked Decisions: “L9: single origin. Every app-code request to a relative path is a backend route.”  
**Recommended brief amendment:** Add a note: “Static assets should be imported or referenced via URL at build time. If a future task needs runtime static-asset fetching, it must request an ADR amendment to L6/L9 or a file-scoped override.”

### 7. `navigator.sendBeacon` and `EventSource` are not banned
**Issue:** The brief explicitly excludes `navigator.sendBeacon`, `EventSource`, dynamic `import()`, etc. `EventSource` is the native transport for SSE, which the R59 notification stream will use (with a fetch-based reader per `design.md` O7). `sendBeacon` could be used to fire-and-forget analytics or logout beacons to backend routes.  
**Severity:** Low — out of task scope, but a real bypass.  
**Evidence ·** Brief §Scope Out: “`navigator.sendBeacon`, dynamic `import()` and other non-fetch transports.”  
**Recommended brief amendment:** Keep them out of T04, but add an Open Question tracking item: “If `sendBeacon` or `EventSource` become a concern, extend the rule set in a follow-up task.”

### 8. No test that the rule survives a custom global named `fetch`
**Issue:** The probe says a locally declared `fetch` is not reported. The test should verify that a function parameter or local variable named `fetch` is allowed, while a global reference is not. This is implicit in the rule, but a regression in configuration could change behavior.  
**Severity:** Low — covered by AC3, but worth an explicit fixture.  
**Evidence ·** Brief §AC3: “a locally declared `fetch` is not reported.”  
**Recommended brief amendment:** Ensure the test suite includes a fixture with `function helper(fetch: typeof globalThis.fetch) { fetch('/x'); }` or `const fetch = () => {}; fetch('/x');` and asserts it exits 0.

### 9. Lint config placement and naming
**Issue:** The brief uses `.oxlintrc.json` in `frontend/`. This is a hidden dotfile. Some teams prefer `oxlint.config.json` or `oxlint.config.js` for visibility.  
**Severity:** Info — not a defect, just a convention choice.  
**Evidence ·** Brief §Files to Create: `frontend/.oxlintrc.json`.  
**Recommended brief amendment:** No change needed if `.oxlintrc.json` is the oxlint convention. Document the choice in the brief if helpful.

### 10. CI `build` already runs lint, but the brief says run lint first in `build`
**Issue:** The brief modifies `frontend/package.json` `build` to `pnpm run lint && tsc --noEmit && vite build`. It also says `.github/workflows/ci.yml` already runs `build`, so no CI change is needed. This is correct, but it assumes the CI job runs `pnpm -C frontend build`. If the CI job currently runs `test` and `build` separately, lint still runs because build includes lint. Good.  
**Severity:** Info — no change needed.  
**Evidence ·** Brief §Files NOT to Modify: `.github/workflows/ci.yml` (`build` already runs lint).  
**Recommended brief amendment:** No change.

---

## Open Questions

- How should the T01/T03 test files’ use of `globalThis.fetch` be resolved? This is the only blocker for AC4.
