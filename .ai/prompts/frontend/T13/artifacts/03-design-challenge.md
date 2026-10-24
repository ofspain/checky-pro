# frontend · T13 — Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

**Task statement (verbatim):**
> **No secret in the bundle.** The production bundle contains no secret value and no non-public environment key. Verifies `bundleContainsNoSecretEnvKeys`. Cites R58, L10.

---

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T13 — No secret in the bundle**. Findings are against the brief itself; no code was changed.

The brief is strong on intent: default-deny key scan, mechanism-based secret closure, negative controls, and drift guards. The main risks are in the un-specified mechanics of the scanner, the unverified baseline, and the same host dependency gap that blocked T12.

---

## Findings

### 1. The host dependency gap from T12 is still blocking
**Issue ·** The test requires a real `vite build` of the frontend, but `pnpm` is not on PATH and `node_modules` is missing packages such as `vite-plugin-pwa`. Phase 6 implementation, mutation testing, and the whole-suite baseline in AC9 therefore cannot be verified on this host.  
**Severity ·** High — the task is a verification task; without a build, the verification cannot run.  
**Evidence ·** `frontend/vite.config.ts` imports `vite-plugin-pwa`; a direct `node_modules/.bin/vitest` run fails with `ERR_MODULE_NOT_FOUND: Cannot find package 'vite-plugin-pwa'`.  
**Recommendation ·** Resolve the T12 OQ-C blocker before Phase 6. Record in the frozen brief that the baseline and negative controls are validated against a real build, and that the host must have `frontend/node_modules` installed.

### 2. The allowlist parser for `design.md` §4c is not specified
**Issue ·** AC6 requires the test's allowlist to be exactly `design.md` §4c's four keys. The section heading is "**Environment keys — PROPOSED, pending sign-off (Q6)**", and the keys live in prose inside backticks. The brief does not say how the parser finds them. A brittle regex that stops working when the heading is reworded would silently break the drift guard.  
**Severity ·** Medium — a broken drift guard becomes a false-negative.  
**Evidence ·** `spec/frontend/design.md:184-185`.  
**Recommendation ·** Lock the parser design in the frozen brief. For example:
1. Read `spec/frontend/design.md`.
2. Find the first heading line matching `/Environment keys/i`.
3. Within the paragraph immediately under that heading, extract every backtick-quoted token matching `/^VITE_[A-Z0-9_]+$/`.
4. Assert the extracted set equals `{VITE_OIDC_ISSUER, VITE_OIDC_CLIENT_ID, VITE_OIDC_REDIRECT_URI, VITE_API_BASE_PATH}`.
5. Fail if the count is not exactly four, if any key does not match the shape, or if the heading disappears.
This makes the drift guard a set-equality assertion rather than a permissive extraction.

### 3. The scanner interface is not defined, so the negative control is under-specified
**Issue ·** AC7 requires a negative control that feeds the scanner a synthetic bundle. The brief says "extract every `VITE_[A-Z0-9_]*` and `process.env.<KEY>` occurrence" but does not say whether the scanner is a directory walker, a string matcher, or a composed pipeline. Without a defined interface, the negative control may be written differently from the real scan and only prove the helper, not the production assertion.  
**Severity ·** Medium — the most important AC cannot be shown to catch leaks.  
**Recommendation ·** Define a single scanner function in the frozen brief:
```ts
function scanBundle(treeRoot: string): { keys: string[]; secrets: string[]; absoluteUrls: string[] };
```
The real test calls it on the temp build directory; the negative control creates a temp directory containing synthetic files and calls the same function. All three prohibited categories (unlisted keys, secret shapes, absolute backend URLs) go through the same code path.

### 4. `process.env.NODE_ENV` is a likely false positive
**Issue ·** The default-deny key scan targets any "`process.env`-shaped key". Browser dependencies and React production builds often contain `process.env.NODE_ENV`. If Vite/esbuild does not replace every occurrence, the scan will flag it as an unlisted environment key.  
**Severity ·** Medium — could block the suite with a value that is not a deployment secret.  
**Evidence ·** Vite does not replace `process.env.NODE_ENV` in application source by default; `esbuild` may replace it for some dependencies, but the result is not guaranteed for all transitive packages.  
**Recommendation ·** Decide explicitly before Phase 6:
- Option A (strict, preferred): keep the broad pattern and treat any surviving `process.env.*` as a failure. If `process.env.NODE_ENV` appears, the author must either configure Vite to replace it or justify an exception in the test with a comment.
- Option B: narrow the pattern to `process\.env\.VITE_[A-Z0-9_]+` so only Vite-inlined variables are caught, and document that `process.env.NODE_ENV` is considered a build-time substitution, not a deployment key.
Either choice is acceptable; neither should be left implicit.

### 5. The emitted service-worker filename is assumed to be `serviceWorker.js`
**Issue ·** AC1 requires walking the output tree so that files outside `assets/` are included. The brief names `serviceWorker.js` explicitly, but `vite-plugin-pwa` with `injectManifest` may emit `serviceWorker.js`, `serviceWorker.<hash>.js`, or a different name depending on plugin options and the `base` path.  
**Severity ·** Low–Medium — a filename mismatch would make AC1 skip the worker or fail vacuously.  
**Evidence ·** `frontend/vite.config.ts` sets `filename: 'serviceWorker.ts'` and `base: '/app/'`; the actual emitted path is not verified on this host.  
**Recommendation ·** Do not hard-code `serviceWorker.js` in the test. Instead, assert that the output tree contains exactly one file whose basename matches `/^serviceWorker\.\w+\.js$/` or `/^serviceWorker\.js$/`, or discover the filename from the output tree. Then include that file in the scan.

### 6. `envPrefix` pinning is left to Phase 5
**Issue ·** AC3 says `envPrefix` must be absent or exactly `['VITE_']`. The brief calls pinning a "hardening" that Phase 5 may or may not apply. Leaving it optional means the implementation must handle both states, and a future Vite change to the default could silently broaden the prefix.  
**Severity ·** Low — not wrong, but misses an easy hardening.  
**Evidence ·** Brief §Scope; AC3.  
**Recommendation ·** Lock the frozen brief to pin `envPrefix: ['VITE_']` in `vite.config.ts` with a comment citing R58. This makes AC3 a static assertion on a literal and removes reliance on Vite's default.

### 7. The baseline `375/377` in AC9 is unverified and probably stale
**Issue ·** The brief asserts a whole-suite baseline of 375 passing / 377 total. This number depends on the final T12 test count and cannot be checked on this host because dependencies are not installed. If it is wrong, AC9 will fail before T13's own tests are judged.  
**Severity ·** Medium — acceptance criterion built on an unverified claim.  
**Evidence ·** Earlier T12 notes used `371/373`; the jump to `375/377` is plausible but not confirmed.  
**Recommendation ·** Replace the literal baseline in the frozen brief with a rule: "baseline equals the count from `vitest run` immediately before this task, plus the new T13 tests, and the only failures are the two known environmental T07 cases." Record the actual numbers during Phase 6.

### 8. AC8 known-good exceptions are not enumerated
**Issue ·** The absolute-URL scan forbids any `http(s)` URL naming a backend host. The brief says exceptions, if any, must be enumerated and justified. No exceptions are named. This is fine if the build truly contains none, but the implementation has no guidance on what to do if one appears.  
**Severity ·** Low.  
**Recommendation ·** Add a procedure to the frozen brief: run the scan once; if any absolute URL is found, the author must add it to an explicit `KNOWN_ABSOLUTE_URLS` array with a justification comment, and the test asserts the found set equals that array. This prevents silent pattern loosening.

### 9. The sourcemap leak path is only noted, not guarded
**Issue ·** The brief notes that sourcemaps are an obvious leak path if enabled, but it excludes them from scope. A future change that enables `build.sourcemap` would bypass all bundle-scan protections because the test only scans emitted files and sourcemaps are not emitted today.  
**Severity ·** Low — speculative.  
**Evidence ·** Brief §Scope: "Sourcemap contents — none are emitted (no `build.sourcemap`). If they are ever enabled they become the obvious leak path, and that is a note for the owner, not work here."  
**Recommendation ·** Add a cheap guard: assert that the output tree contains no `*.map` files. The assertion passes today and becomes a hard failure if someone enables sourcemaps later.

### 10. No plan for closing the `import.meta.env` mechanism beyond the key scan
**Issue ·** The mechanism-based approach closes Vite's inlining routes via `envPrefix`, `define`, and `.env` files. But the test does not verify that `import.meta.env` references are absent from the output. If `import.meta.env.SOME_KEY` survives into the bundle because `envPrefix` was bypassed or a plugin injected it, the key scan may catch the key name but not the mechanism.  
**Severity ·** Low — the key scan is the primary guard, but the mechanism closure is incomplete.  
**Recommendation ·** Add a secondary assertion: no emitted file contains the literal `import.meta.env.`. This catches any Vite-shaped access that slips through.

### 11. The negative control does not cover a real secret shape from the platform
**Issue ·** AC7 requires a private-key block, a JWT, a `Bearer ey…`, and a `client_secret` literal. It does not require testing against the platform's own distinctive value (`checky-local-only`). While the synthetic shapes are good, a scanner tuned only to generic patterns could miss a value that is not obviously a key or token.  
**Severity ·** Info.  
**Recommendation ·** Add `checky-local-only` to the negative-control bundle and assert it is reported under the real-value scan (AC2). This proves the scanner is not purely pattern-driven.

### 12. OQ-1 is unresolved and will surface again in T14+
**Issue ·** T01 forbids any source from naming `import.meta.env`; §4c declares four public keys. The brief correctly leaves this to the owner, but the conflict is real: an auth task that wants `VITE_OIDC_ISSUER` will fail T01.  
**Severity ·** Info — not a T13 blocker, but should be tracked.  
**Evidence ·** Brief §Open Questions OQ-1.  
**Recommendation ·** Add a note in the frozen brief that T13 does not resolve OQ-1 and that the owner must address it before any task consumes the §4c keys.

---

## Open Questions

- **OQ-C (host dependencies)** is the only blocker and must close before Phase 6.
- **OQ-1 (T01 vs §4c)** is out of scope for T13 but should be explicitly tracked.
- **OQ-2 (definition of "secret value")** is acceptably resolved by the mechanism + pattern approach, provided AC7 and the negative control are implemented as a single scanner.

The remaining findings are design decisions that should be locked in the frozen brief before implementation: scanner interface, allowlist parser, `envPrefix` pinning, filename discovery, baseline rule, and absolute-URL exception procedure.
