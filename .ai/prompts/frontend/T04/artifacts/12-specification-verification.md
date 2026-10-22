# frontend · T04 · Phase 12 — Specification Verification

Date: 2026-10-22. Consumes Phases 0–11. It is verified against:
- `tasks.md` task 4 (line 13);
- `design.md` L6 (line 38) and §6;
- `package.md` §8 (line 171: `lintRejectsHandWrittenBackendFetch` → L6);
- `agents.md`, "API access" (line 62);
- the FROZEN brief, as amended in phase 9.

## Phase 11 gaps (Kimi), resolved before verification

Kimi raised 7 gaps. 4 were accepted in full, and 1 was accepted as a recorded behaviour. 2 were rejected after a probe:
`document.defaultView.fetch` was deferred, and `declare global` was not pinned. Only the test file changed; there was no
production change.

| Gap | Decision | Change | Evidence / break caught |
|---|---|---|---|
| 1 AC2 not exercised end to end | **ACCEPTED** | New case `stops build at lint, so tsc and Vite never run on a violation`. It works on a temp copy of `frontend/`: the real scripts, config, `index.html`, `tsconfig.json`, `vite.config.ts` and `src`, with `node_modules` linked. Dependencies are dropped from the copy's manifest, because `pnpm run` otherwise tries to resolve `workspace:*` and fails. **The control comes first:** the clean copy builds with exit 0 and writes `dist`. **Then** it adds a file that is both a `fetch` violation and a type error, and asserts: a non-zero exit; `eslint(no-restricted-globals)` in the output; no `error TS\d+` (so `tsc` never ran); no `built in` (so Vite never ran); and no `dist`. | Breaks: `lint; tsc && vite` (`;` instead of `&&`), lint moved last, and `build` = lint only. Each fails this case and case 4. |
| 2 `document.defaultView.fetch` | **REJECTED (not expressible); residual** | None. Probe: it exits 0. Adding `{object:"document.defaultView"}` or `{object:"defaultView"}` still exits 0, because `no-restricted-properties` matches only a plain identifier as the object. It was already named in the frozen brief's Constraints as out of scope. | — |
| 3 Asymmetric XHR bracket fixtures | **ACCEPTED** | Case 1 gains `window['XMLHttpRequest']` and `self['XMLHttpRequest']`. Both were probed first and exit 1. The banned list is now 21 forms. | covered by the existing "XHR property entries removed" break |
| 4 Binary version unchecked | **ACCEPTED** | Case 4 also runs `node_modules/.bin/oxlint --version` and expects exactly `Version: 1.87.0`. | — |
| 5 Directive regex untested | **ACCEPTED** | Case 7 first asserts the pattern matches four directive forms (`oxlint` and `eslint`, line and block). The samples are built from parts so the file doesn't match its own scan. | the existing "directive in `src`" break |
| 6 `XMLHttpRequest` as a type | **ACCEPTED (record behaviour)** | Probe: `(xhr: XMLHttpRequest) => xhr` and `let _x: XMLHttpRequest` both exit 0. A type position makes no request. It is added to `ALLOWED` as `XMLHttpRequest as a type only`, so a lint change in either direction is noticed. | — |
| 7 `declare global { const fetch … }` | **REJECTED (no test)** | Probe: it exits 0. It is a type declaration with no runtime effect, and any real *call* it enables is still a bare `fetch` (reported) or a member access on a window object (reported). Pinning it would add nothing to L6. | — |

## Final suite
- `lintRejectsHandWrittenBackendFetch`: 8 cases, **32** results (21 banned + 5 allowed + 6 single).
- `generatedClientCompiles`: 6.
- `routesResolveUnderAppPrefixOnly`: 9.
- `shellBuildsAndMounts`: 7.
- **54/54 passed.** `typecheck` exit 0. `lint` reports "0 errors on 8 files with 2 rules". `build` exit 0.

**Mutation breaks over phases 10 and 11: 12 of 12 caught.** Phase 10 had 9:
- a rule removed;
- `window` dropped;
- `top` dropped;
- the XHR property entries removed;
- a `src` override;
- lint removed from `build`;
- `--no-ignore` removed;
- a directive in `src`;
- a `fetch` in `src`.

Phase 11 added 3: `;` instead of `&&`, lint moved last, and lint-only. Every break was applied to a backup and
restored from it; `git diff` was clean apart from the test file.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| tasks.md 4: a lint rule fails a build that calls `fetch` against a backend route | Yes | `frontend/.oxlintrc.json:13` (`no-restricted-globals`), `:24` (`no-restricted-properties`); `frontend/package.json:7` (`build` runs lint first) | Cases 1 and 8 (`…test.ts:98`, `:130`) | No | Read as a blanket ban in app code, since under L9 every relative request is a backend route (P3-2, frozen) |
| L6: every backend call uses `libs/ts/api-client`; the stream is the only exception | Yes | Same config. No `overrides` (the R59 task adds the one exemption). `libs/` is outside the lint path. | Cases 2 (`:105`, the generated client is allowed) and 6 (`:192`, no overrides) | No | None |
| agents.md "API access": no hand-written `fetch` to backend routes | Yes | as above; `XMLHttpRequest` banned too | Case 1 (21 forms) | No | XHR included (frozen Open Question 3) |
| package.md §8: `lintRejectsHandWrittenBackendFetch` → L6 | Yes | `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` | itself | No | None |
| AC1: banned forms reported, exit 1 | Yes | config `:13`, `:24` (2 globals; 7 objects × `fetch`/`XMLHttpRequest`) | Case 1 `:98` | No | +5 forms beyond the frozen 16 (P8-1, P11-3) |
| AC2: a violating build exits before `tsc` and Vite | Yes | `package.json:7` | Case 4 `:117` (script) and case 8 `:130` (end to end, with a control) | No | None |
| AC3: a local `fetch`, the generated client and `vi.spyOn` are allowed | Yes | rule semantics (probed) | Case 2 `:105` (5 forms, incl. XHR as a type) | No | None |
| AC4: today's `src` lints clean; `build` passes | Yes | `pnpm -C frontend lint` / `build` exit 0 | Case 3 `:110` (also "with 2 rules") | No | None |
| AC5: no `overrides`; rules at `error` | Yes | `.oxlintrc.json` | Case 6 `:192` (exact rule keys, names, pairs, message) | No | The pair set extended to XHR (P8-1) |
| Bypass hardening: `.eslintignore` | Yes | `package.json:8` `--no-ignore` | Cases 4 and 5 (`:176`) | No | The frozen `lint` string changed (P9, recorded) |
| Bypass hardening: inline directives | Yes (test-side) | — | Case 7 `:212` | No | Test-only guard; oxlint 1.87.0 can't forbid directives |
| Only the L6 rules run | Yes | `.oxlintrc.json:3` (all categories off) | Case 3 ("with 2 rules") | No | None |
| Exact, two-week-old version | Yes | `package.json:24` `oxlint` `1.87.0` (2026-10-05) | Case 4 (manifest and `--version`) | No | None |
| No production code change; spec, contracts, services and infra untouched | Yes | the diff touches only `frontend/package.json`, `frontend/.oxlintrc.json`, `pnpm-lock.yaml` and the new test | — | No | None |
| CI enforcement | Yes | `.github/workflows/ci.yml:63` runs `pnpm -C frontend build`; `:57` `--frozen-lockfile` passes; the lockfile carries `linux-x64-gnu` | — | No | None (no CI edit needed) |

## Principal-engineer review

1. **Is the task fully complete?** Yes.
   - The lint rule exists, and lint runs first in `build`, which CI runs.
   - The named test exists and passes.
   - All the hardening accepted at phases 4, 9 and 11 is in place.
2. **Does it satisfy every acceptance criterion?** Yes, AC1–AC5, each with a direct test. AC2 is now proven end to end
   against a control build.
3. **Does it violate any LOCKED decision?** No.
   - **L6:** it enforces L6, with no exemption yet.
   - **L9:** respected.
   - **L12:** additive only.
   - **L13:** Vitest.
   - **agents.md:** honoured, and `spec/` is untouched.
4. **Remaining risks:**
   - **Name-based limits**, documented in the frozen Constraints and in phases 9 and 11: an aliased window object,
     `document.defaultView.fetch`, `Reflect.get`/`getOwnPropertyDescriptor`, and top-level `this` (inert in ES modules).
     Code review and the T05–T08 client boundary cover them.
   - **Non-fetch transports** (`EventSource`, `sendBeacon`) are deferred to the R59 notification-stream task.
   - **Directives** are guarded only by the test, not by oxlint itself. Because the test runs in CI, the build-time
     guarantee still holds.
   - **An oxlint bump** must keep case 3 ("with 2 rules") and the `--version` check green. Both are deliberate tripwires.

**PASS:** the L6 lint rule fails a violating build before `tsc` and Vite run, as proven end to end. The named test covers
every acceptance criterion and catches 12 of 12 mutation breaks.
