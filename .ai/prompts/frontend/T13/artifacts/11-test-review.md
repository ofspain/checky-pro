# frontend · T13 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **No secret in the bundle.** The production bundle contains no secret value and no non-public environment key. Verifies `bundleContainsNoSecretEnvKeys`. Cites R58, L10.

---

## Method

Reviewed the Phase 10 test manifest for `bundleContainsNoSecretEnvKeys` (32 cases, 14 mutation breaks, one survivor) against the frozen T13 acceptance criteria and the Phase 8 findings. No code was rewritten.

The Phase 9 resolution addressed the main Phase 8 findings: the URL pattern now consumes query strings and fragments, the platform-host check is broadened and exercised, the build environment scrubs `VITE_*` variables, the allowlist parser reads to the next Markdown heading, and files are read with `latin1`. The test suite is now a strong regression guard.

---

## Gaps

### 1. The `latin1` binary-asset hardening has no exercised path
**Gap ·** M12 (read files as `utf8` instead of `latin1`) survived because every emitted file is text. The fix is forward-looking, but there is no case that proves it matters.  
**Why it matters ·** Without a failing case, a future regression that reverts to `utf8` would not be caught until a real binary asset leaked a secret.  
**Suggested test ·** Add a synthetic binary file to the negative control — for example, a fake font file containing the bytes of `AKIAIOSFODNN7EXAMPLE` or `checky-local-only`. Assert the scanner reports the secret. This catches M12 without adding a binary asset to the real application.

### 2. AC4 has no mutation break
**Gap ·** The manifest honestly states that AC4 (no `.env*` under `frontend/`) has no mutant because creating one would violate the thing being tested. This is reasonable, but it leaves the directory-listing assertion unproven.  
**Why it matters ·** A bug in the assertion (e.g., reading the wrong directory, or a typo in the `startsWith('.env')` predicate) would silently pass.  
**Suggested test ·** Extract a small helper `findEnvFiles(dir: string): string[]` and unit-test it against a temporary directory containing `.env`, `.env.local`, and a non-matching file. The real assertion then calls the same helper on `frontendDir`. This keeps the real invariant intact while proving the detector works.

### 3. The allowlist parser is not unit-tested against a multi-line spec
**Gap ·** The parser was changed to read until the next Markdown heading so a bulleted list would not be cut short. There is no synthetic test that feeds it a multi-line or list-formatted section.  
**Why it matters ·** A future regression in the loop condition could again stop at a blank line, and the only way to notice would be a `design.md` refactor.  
**Suggested test ·** Add a case that calls a refactored `allowlistFromSpecText(specText)` helper with a section containing blank lines between bullet items and asserts all four keys are extracted.

### 4. The negative control does not exercise a secret in a sourcemap body
**Gap ·** The control creates a `.map` file with `{"version":3}` and asserts the file is reported. It does not assert that a secret inside a sourcemap body is caught by the content scan.  
**Why it matters ·** M11 showed sourcemaps are load-bearing because they reintroduce source text. A sourcemap containing a secret should be reported under both `sourcemaps` and `secrets`.  
**Suggested test ·** Put a secret-shaped string inside the synthetic `.map` file and assert `controlScan.secrets` contains it.

### 5. There is no regression case for a `VITE_*` variable surviving into the build
**Gap ·** Phase 9 added code that scrubs `VITE_*` variables from the child build environment, but no test asserts the scrubbing itself.  
**Why it matters ·** A regression that removed the scrubbing loop would not be caught unless a host happened to set a `VITE_*` variable.  
**Suggested test ·** This is intentionally environment-sensitive, so a full second build with a synthetic `VITE_` variable is expensive. A cheaper alternative is an assertion that `buildEnv` contains no keys starting with `VITE_` after the scrubbing loop, right before the child process is spawned. This at least pins the setup logic.

### 6. The "production build" assertion still covers only the main chunk
**Gap ·** The Phase 7 self-review S3 noted this. The manifest says the production-build case checks the main `assets/*.js` chunk for `react.dev` links and `process.env.NODE_ENV`. It does not assert the same over `serviceWorker.js`.  
**Why it matters ·** `serviceWorker.js` is built by `vite-plugin-pwa` and could be non-production independently.  
**Suggested test ·** Extend the case to iterate over every emitted `.js` file and assert the same production markers. The manifest's M10 already scans all files, but not specifically for production mode.

### 7. The positive-control limitation is acknowledged but not pinned by a test comment
**Gap ·** The manifest notes that a genuine production secret inlined by `define` cannot be detected by scanning the artifact, because Vite inlines the value, not the key name. This is a real boundary of R58.  
**Why it matters ·** A future maintainer might see 14 mutants caught and assume the scanner catches all leaks.  
**Suggested test ·** No new test can catch an unknown value, but add a prominent comment in the test header and at `ENV_KEY_PATTERNS` stating exactly what the scanner cannot do and pointing at the mechanism-closure cases as the real defence. The manifest mentions this in prose; it should be in the code.

---

## Non-gaps (acknowledged, no change needed)

- **32/32 cases pass** and **13/14 mutants caught**.
- **M1 (query-string bypass)** is now caught by a dedicated S1 regression case.
- **M2 (real-value scan removal)** is caught by both positive-control and negative-control cases.
- **M3 (`envPrefix: ''`)** is rejected by Vite itself; this is correctly documented as a frozen-brief correction.
- **M4/M13/M14 (`envPrefix` weakening)** are caught by the config scan.
- **M5 (`define` block)** is caught by the "no define block" case.
- **M6 (empty tree accepted)** is caught by the empty-tree case.
- **M7/M8 (`design.md` allowlist drift)** are caught by both allowlist cases.
- **M9 (`NODE_ENV=test` inherited)** is caught by the production-build case and URL set equality.
- **M10 (scan only `assets/`)** is caught by four cases.
- **M11 (sourcemaps emitted)** is caught by seven cases, demonstrating sourcemaps are load-bearing.
- **M12 survived** honestly; it is a forward-looking hardening with no current binary asset.
- **AC1** is pinned by scanning every emitted file and default-deny key extraction.
- **AC2/AC5** are pinned by secret-shape patterns and the platform's own `checky-local-only` value.
- **AC3** is pinned by `envPrefix` literal and `define` absence.
- **AC4** is pinned by the directory listing of `frontend/`.
- **AC6** is pinned by the spec parser and set equality.
- **AC7** is pinned by one scanner shared between real and synthetic bundles, with ten reportable categories.
- **AC8** is pinned by set equality over eight justified URLs and the broadened platform-host rule.
- **AC10** is pinned by sourcemap file detection.
- **AC11** is pinned by literal `import.meta.env.` absence over every file.
- **AC9 baseline** is 377 → 409, delta exactly 32, with the two known environmental T07 failures.
- **No T01–T12 file modified** except the `envPrefix` pin in `vite.config.ts`.

---

## Summary

T13's test suite is excellent: 32 cases, strong mutation coverage, and honest reporting of the one survivor and the value-not-name limitation. The remaining gaps are small and closing them would make the suite even more robust: a synthetic binary asset to exercise `latin1`, a helper unit test for `.env*` detection, a multi-line parser case, a sourcemap-body secret case, a `VITE_*` scrubbing assertion, a worker production-mode check, and a code comment pinning the positive-control limitation. None of these are blockers.
