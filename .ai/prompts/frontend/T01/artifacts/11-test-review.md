# frontend · T01 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T01 — Shell renders |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **Shell renders.** The Vite, React, and TypeScript app builds and mounts an empty shell. Verifies `shellBuildsAndMounts`. Cites L13.

---

## Method

Reviewed the Phase 10 test manifest (`shellBuildsAndMounts.test.tsx`, 5 cases) against the frozen brief acceptance criteria (AC1–AC6), the named test `shellBuildsAndMounts`, and the L13/L14/L9/L10/L11/L12 locked decisions. No code was rewritten.

---

## Gaps

### 1. Build test relies on the test runner's working directory
**Gap ·** Case 1 derives the frontend package directory from `process.cwd()` because `import.meta.url` is not a `file:` URL under jsdom. If the test is ever invoked from a different working directory (e.g., `pnpm -C frontend exec vitest run` from the repo root, or an IDE runner), it will fail with a misleading “not the frontend package” error rather than a real build failure.  
**Why it matters ·** A test that fails because of how it is invoked is flakiness. The frozen brief says tests must run in CI and in the `infra/local` container.  
**Suggested test ·** Resolve the frontend directory from a source-location marker instead of `process.cwd()`. For example, import a known file from the app source and compute the package root from `__dirname` / `import.meta.dirname` in a small Node-side helper, or pass the project root via Vitest's `resolve.alias`. At minimum, assert that the resolved directory contains `package.json` with `"name": "@themistra/frontend"` and fail with a clear message before calling `build()`.

### 2. No automated verification of the dev-server contract
**Gap ·** AC5 requires `server.host: true`, `server.port: 5173`, `server.strictPort: true`, and **no proxy**. The automated tests verify the production build output (`base: '/app/'`) but do not assert the dev-server configuration. AC5 is recorded as a live check only.  
**Why it matters ·** A regression in `vite.config.ts` (e.g., someone adds a proxy, changes the port, or removes `strictPort`) would not be caught by the automated suite and would only surface during manual local-stack testing.  
**Suggested test ·** Add a static assertion that reads `frontend/vite.config.ts` (or imports the config in a Node test) and checks the `server` object shape: `host === true`, `port === 5173`, `strictPort === true`, and `proxy` is absent/undefined.

### 3. No check for hardcoded backend URLs or non-edge origins
**Gap ·** Case 4 asserts that no source file contains `import.meta.env`, but AC3 also requires “no backend URL” (L9 single-origin rule). A developer could hardcode `http://localhost:8080/api/v1/...` or a non-edge origin without using `import.meta.env`.  
**Why it matters ·** T01 is the foundation for a single-origin SPA; the first opportunity to enforce this is now, before later tasks introduce real backend calls through the generated client.  
**Suggested test ·** Add a source scan (like case 4) that flags any absolute `http://` or `https://` URL in `frontend/src/**/*.ts(x)` except for allowed patterns (e.g., `http://localhost:5173` for local dev comments is not allowed in source). Alternatively, assert that no source file imports from a path starting with `/api/`, `/oauth2/`, etc., until T03.

### 4. `main.tsx` boundary is not tested
**Gap ·** The frozen brief’s Constraints state: “shell code lives in `src/app/`; `main.tsx` only calls `mountShell`." None of the five cases inspects `main.tsx`.  
**Why it matters ·** If `main.tsx` later imports a router, an env key, or a side-effect module, the negative tests (cases 3 and 4) might still pass because they only exercise `mountShell` and `AppShell`.  
**Suggested test ·** Add a static assertion that `frontend/src/main.tsx` contains no `import` beyond `react`/`react-dom/client` and `./app/mountShell`, and that its top-level statements are limited to the single `mountShell(document.getElementById('root'))` call.

### 5. Service-worker absence is only tested at runtime, not at config level
**Gap ·** Case 3 stubs `navigator.serviceWorker.register` during mount. It does not assert that `vite-plugin-pwa` (or any Workbox registration) is absent from `vite.config.ts` or from the build output.  
**Why it matters ·** Once T09–T12 add the service worker, there will be tests for its behavior. Until then, T01 should guard against an accidental early registration that would violate AC3.  
**Suggested test ·** Add a static assertion that `vite.config.ts` does not import `vite-plugin-pwa` and that the production build output does not contain a `sw.js`, `manifest.json`, or `navigator.serviceWorker.register` string.

### 6. Build test does not assert `index.html` references exactly one module script
**Gap ·** Case 1 checks that `index.html` contains `src="/app/assets/*.js"`. It does not verify that there is exactly one such script, nor that no script is referenced at `/assets/` (without `/app`). A malformed `index.html` with both `/app/assets/` and `/assets/` references could pass.  
**Why it matters ·** The acceptance criterion is that assets are referenced **under `/app/`**, implying no non-`/app/` asset paths.  
**Suggested test ·** Assert that all `src` attributes of `<script type="module">` tags in `dist/index.html` start with `/app/assets/`, and that at least one exists. Also assert no `<script>` src starts with `/assets/` (without `/app`).

### 7. No assertion that `mountShell` returns a usable React Root
**Gap ·** The frozen brief says `mountShell` returns the `Root` so callers (tests) can `unmount()`. The tests use the returned root, but no case explicitly asserts that it is a React root with an `unmount` method.  
**Why it matters ·** A future refactor could change the return type and break the cleanup pattern without failing a test.  
**Suggested test ·** In case 2, after mounting, assert `typeof root.unmount === 'function'` before calling it. This is a one-line strengthening of the existing cleanup code.

### 8. Test for visible text does not guard against whitespace-only content
**Gap ·** Case 2 asserts `textContent === ''`. If `AppShell` renders a node with whitespace (e.g., `<div> </div>`), `textContent` is non-empty whitespace but visually empty. The frozen AC4 says “renders no text content,” which strictly includes whitespace.  
**Why it matters ·** A whitespace-only shell could pass today even though it violates the spirit of AC4.  
**Suggested test ·** Keep the `textContent === ''` assertion and add `expect(shell.innerHTML).toBe('')` or `expect(shell.textContent?.trim()).toBe('')` to reject whitespace-only content.

---

## Non-gaps (acknowledged, no change needed)

- **AC5 is a recorded live check, not automated.** Accepted per the frozen brief and Phase 10 manifest; it cannot be reliably automated inside Vitest without a running Caddy edge.
- **Build runs inside jsdom.** Self-review #1 confirmed this works and no per-file environment split is needed.
- **`@types/node` is required for the build test.** Flagged as a deviation in Phase 10 but justified: the test reads files via `node:fs/promises`.

---

## Summary

The Phase 10 test manifest covers the named test `shellBuildsAndMounts` well for the core behaviors (build prefix, mount, no env/network/storage/worker, null guard). The main weaknesses are **environment fragility** (case 1), **missing static/config-level guards** for the dev-server contract, backend URLs, `main.tsx` boundary, and service-worker absence, and a **minor whitespace gap** in the visible-text assertion. Adding these would make the suite robust against the most likely T01 regressions without expanding scope beyond the task.
