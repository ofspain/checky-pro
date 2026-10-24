# frontend · T11 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

### Step 1 — `frontend/src/pwa/apiRequestPolicy.ts`
The D4 exports. `isCacheableRequest` is the only real decision: `url.pathname` is one of
`SHELL_DOCUMENT_PATHS`, or it matches `^/app/assets(?:/|$)`. `isNetworkOnlyRequest` is its negation and nothing
more, so D1's default-deny is structural rather than a list lookup. `NETWORK_ONLY_PATH_PREFIXES` is exported for
task 12's matchers and AC9's drift guard, and is **not** consulted by either predicate — which is the point of D1
and what the AC6 default-deny cases prove.

`segmentPrefixPattern` is duplicated from `navigationDenylist.ts:40` (module-private there, D5) under a comment
naming T09's copy.

### Step 2 — `frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts`
- `simulateRequestRoute(path, cache, method)`, mirroring Workbox's two request strategies: a NetworkOnly decision
  returns without touching the cache; a cacheable path does `match`, then `put` on a miss. The `method` parameter
  exists so an AC4 mutant that consults it has somewhere to be consulted.
- The same spied fake cache and recorded-key list as T09/T10, so "touched the cache at all" is assertable.
- `repoFile()` for the one on-disk read (D7's parse of `design.md`).
- Groups AC1–AC10, with `it.each` tables for the twelve prefixes, the asset/shell set, the boundary rows and the
  overlap pairs.

### Step 3 — Verify
`vitest run src/pwa` (three files now), the whole suite for AC11, `tsc --noEmit`, `oxlint`. The known environmental
`lintRejectsHandWrittenBackendFetch` AC4 failure stays reported as environmental.

### Step 4 — Commit, then mutate
The T09 lesson: the mutation harness reverts tracked files with `git checkout --`, so the test is committed before
the eight AC-M mutants run.

### Risks
- **A vacuous default-deny.** Almost everything is NetworkOnly, so most AC1 rows would pass even if the predicate
  were `() => true`. AC2 is the guard — the cacheable set must still be cacheable — and the AC-M "invert the
  default" mutant is what proves AC2 earns its place.
- **Duplicated helper drift.** T09's `segmentPrefixPattern` and this copy must stay identical in behaviour; AC7's
  boundary rows are what would catch a divergence here, and T09's own AC4 rows cover the other side.
