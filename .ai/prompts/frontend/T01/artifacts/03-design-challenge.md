# frontend · T01 · Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T01 — Shell renders**. Findings are against the brief itself, not against any partial implementation. No code was changed in this phase.

---

## Findings

### 1. Routing boundary is ambiguous
**Issue:** The brief lists only `react` and `react-dom` as runtime dependencies and explicitly places **Routing (T02)** in the **Out** list. Yet AC1 requires `base: '/app/'`, AC5 tests the app at `/app/`, and `L14` locks the SPA route set under `/app`. A reader could implement T01 either as a router-less component (cleanest for T01) or conclude that a router is already required to satisfy the `/app` prefix.  
**Severity:** Medium — likely to cause either scope creep into T02 or an AC5 failure if the shell cannot be mounted at `/app` without a router.  
**Evidence:** Brief §Scope Out: “Routing (T02)”. Brief §Locked Decisions: “L14: the app is served under `/app` (`base: '/app/'`)”.  
**Recommended brief amendment:** State explicitly whether T01 is allowed to import any router. If not, clarify that `base: '/app/'` and the edge route are sufficient to satisfy AC5, and that no client-side route matching is required yet.

### 2. “No user-facing text” is not precise
**Issue:** AC4 says “the shell renders no user-facing text.” `index.html` is expected to contain a `<title>` element (standard HTML), and the brief itself says the app name is “Checky Pro”. Static `<title>` text is user-facing but is not rendered by React.  
**Severity:** Low — cosmetic, but could fail a strict interpretation of AC4.  
**Evidence:** Brief §Files to Create: `frontend/index.html` (`lang="en"`, `#root` mount element); Brief §Acceptance Criteria AC4: “the shell renders no user-facing text.”  
**Recommended brief amendment:** Change AC4 to: “the React shell component renders no user-facing text; static meta/title text in `index.html` is permitted.”

### 3. Required test conflates two concerns in one named test
**Issue:** The required test `shellBuildsAndMounts` is defined as both (a) a programmatic Vite build asserting `/app/` in `dist/index.html`, and (b) a jsdom mount test. These are different failure modes and may be easier to maintain as separate tests (one for build output, one for runtime mount).  
**Severity:** Low — functional, but makes mutation-break messages less specific.  
**Evidence:** Brief §Required Tests: “`shellBuildsAndMounts` (Vitest): (a) runs a Vite production build … (b) mounts the app …”.  
**Recommended brief amendment:** Split into two named tests, e.g. `productionBuildReferencesAppPrefix` and `shellMountsIntoRoot`, while keeping both under the T01 test plan.

### 4. CI job has an unstated dependency on a `changes` job
**Issue:** The brief instructs adding a `frontend` CI job gated on `needs.changes.outputs.frontend`. If `.github/workflows/ci.yml` does not already contain a `changes` job, this instruction is incomplete.  
**Severity:** Medium — could block T01 completion if the workflow file is missing or structured differently.  
**Evidence:** Brief §Files to Modify: “add a `frontend` job gated on `needs.changes.outputs.frontend` (Node 22, corepack pnpm, …)”.  
**Recommended brief amendment:** Add a note: “If the repository does not yet have a `changes` job, create one using `dorny/paths-filter` or `tj-actions/changed-files` that emits `frontend: true` when `frontend/**` or root lockfile/package files change.”

### 5. `typecheck` script is undefined
**Issue:** The brief says `frontend/package.json` must have scripts `dev`, `build`, `test`, `typecheck`, but never defines what `typecheck` does. Without a definition, implementers may duplicate `build` (which also emits files) or omit it.  
**Severity:** Low — functional gap.  
**Evidence:** Brief §Files to Create: “`frontend/package.json` (scripts `dev`, `build`, `test`, `typecheck`)”.  
**Recommended brief amendment:** Define `typecheck` as `"tsc --noEmit"` and clarify that `build` already runs `tsc`, so `typecheck` is for fast CI/type-only checks.

### 6. Negative requirements in AC3 lack test instructions
**Issue:** AC3 requires that the shell reads no env key, makes no network request, writes no storage, and registers no service worker. The brief does not say how to verify these negative properties, beyond hand-waving that they are “trivially” satisfied.  
**Severity:** Medium — T13 will prove the bundle has no secret env keys, but T01 still needs to assert the shell itself does not access `import.meta.env`, `fetch`, `localStorage`, etc.  
**Evidence:** Brief §Acceptance Criteria AC3: “the shell reads no env key, makes no network request, writes no storage and registers no service worker.” Brief §Required Tests: only `shellBuildsAndMounts`.  
**Recommended brief amendment:** Add explicit assertions to the test(s), e.g. assert that `import.meta.env` is not referenced in the shell source (static string check or AST search), and that mounting does not call `navigator.serviceWorker.register` or `fetch`.

### 7. Loud failure for missing `#root` is not tested
**Issue:** The brief’s Constraints state “the entry fails loudly (throws) if `#root` is missing, rather than silently not mounting.” No test covers this, and the brief’s file list does not mention a test for entry error handling.  
**Severity:** Low — edge case, but explicitly called out as a constraint.  
**Evidence:** Brief §Constraints: “Null handling: the entry fails loudly (throws) if `#root` is missing, rather than silently not mounting.”  
**Recommended brief amendment:** Either add a test `mountThrowsWhenRootIsMissing` or downgrade the constraint to a recommendation, since the primary T01 concern is successful mount.

### 8. `frontend/README.md` update is underspecified
**Issue:** The brief says to modify `frontend/README.md` to “add run/test commands (through `infra/local`)” but does not list the commands.  
**Severity:** Low — minor ambiguity.  
**Evidence:** Brief §Files to Modify: “`frontend/README.md`: add run/test commands (through `infra/local`).”  
**Recommended brief amendment:** List the exact commands to document: `infra/local/bin/up --frontend`, `infra/local/bin/status --wait`, `docker compose -p themistra-local -f infra/local/compose.yaml exec frontend pnpm -C frontend test`, and `docker compose … exec frontend pnpm -C frontend build`.

### 9. File naming convention is not stated
**Issue:** The brief proposes `src/app/AppShell.tsx` and `src/app/shell.test.ts(x)`. The repo may have an existing casing or co-location convention (e.g. PascalCase components with adjacent `.test.tsx` files).  
**Severity:** Low — consistency risk.  
**Evidence:** Brief §Files to Create lists `AppShell.tsx` and `shell.test.ts(x)`.  
**Recommended brief amendment:** Either state “follow the repo convention of co-locating `<Name>.test.tsx` next to `<Name>.tsx`” or make the file names mandatory.

### 10. ESLint / lint script is not in scope
**Issue:** The brief does not list ESLint or a `lint` script in the dependency/script list, and places “Lint rule (T04)” in the **Out** list. Adding lint tooling in T01 could conflict with the brief’s minimal L13 subset and the explicit deferral of lint work to T04.  
**Severity:** Low — scope boundary issue.  
**Evidence:** Brief §Scope Out: “Lint rule (T04)”. Brief §Dependencies: build + test tools only.  
**Recommended brief amendment:** Explicitly state that no lint script or ESLint config is required for T01; linting is deferred to T04.

---

## Open Questions

- None that block freezing. The ambiguities above can be resolved by brief amendments without architectural change.
