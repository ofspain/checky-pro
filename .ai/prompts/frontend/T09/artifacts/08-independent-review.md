# frontend · T09 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/src/pwa/navigationDenylist.ts`, `frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/frontend/agents.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **Denylisted navigations bypass the worker.** Navigations to the paths in `agents.md` are never answered from cache or with the shell. Verifies `sasNavigationBypassesServiceWorker`. Cites R57, L17.

---

## Review scope

Reviewed the Phase 6 T09 implementation against the frozen brief, `agents.md`, and the Phase 7 self-review. No code was rewritten.

The implementation is clean: it exports the verbatim list, segment-anchored RegExps, and two pure predicates. It addresses the design-challenge concerns about Workbox shape, segment boundaries, `/app` without a trailing slash, and non-navigation returns. The self-review identified seven issues, mostly forward-looking. This review corroborates them and adds one fresh observation.

---

## New finding

### 1. `repoRoot` is computed at module load from Vitest state
**Issue ·** The drift guard and source-scan tests compute `repoRoot` once at module load using `expect.getState().testPath`. This works because Vitest sets the path before executing the file, but it couples the test to the runner's import order and to the file's depth in the tree. A moved file or a different runner would fail with a confusing "file not found" rather than a clear test failure.  
**Severity ·** Low — stable in the current setup.  
**Evidence ·** `frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts:16`: `const repoRoot = dirname(dirname(dirname(dirname(expect.getState().testPath as string))));`.  
**Recommendation ·** Compute the repo root inside the tests that need it, using `import.meta.dirname` or `process.cwd()` with a check that `spec/frontend/agents.md` exists. If `import.meta.url` is a non-file URL in jsdom, fall back to the current Vitest-based helper but wrap it in a function called from each test.

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| 1 | A precache route could still answer a denylisted navigation | **Confirmed, and it is the task's hardest limit.** `navigationDenylist.ts` has no cache reference by design, so the "no cache read / no shell" guarantee is enforced only by how task 12 wires the worker. Task 12 must import `NAVIGATION_DENYLIST` and assert the precache manifest contains no denylisted URL. |
| 2 | Nothing in production imports the module yet | **Confirmed.** Registration is task 12, so `tsc`/`oxlint` will not catch deletion or bypass. Task 12's tests should fail if the worker's navigation route is not built from these exports. |
| 3 | Sub-paths of `/connect/logout` are denylisted | **Confirmed, acceptable.** Uniform segment matching is the safe direction; the case is flagged as beyond R57. |
| 4 | `/connect` is otherwise unguarded | **Confirmed, acceptable.** Faithful to R57; a widened worker scope plus a precached `/connect/register` would need task 12's guard. |
| 5 | AC9's banned-global scan does not strip trailing comments | **Confirmed.** The scan is over-strict in the safe direction; no current line is affected. |
| 6 | The drift guard depends on one sentence in `agents.md` | **Confirmed.** A rewording of the opening sentence would break the guard loudly, which is intended but couples `spec/` prose to a frontend test. Same pattern as T05/T08. |
| 7 | Both predicates re-check `mode` | **Confirmed, acceptable.** The tiny redundancy keeps each predicate independently correct for external callers. |

---

## Non-gaps (acknowledged, no change needed)

- **Workbox shape:** the module exports `NAVIGATION_DENYLIST` (RegExp denylist) and `isShellNavigation` (match callback). Both are pure.
- **Segment-aware matching:** `/accountsxyz`, `/apikeys`, `/application`, `/xwell-known` are all pinned negative; `/accounts/me`, `/api/v1/...` are pinned positive.
- **`/app` without trailing slash:** treated as a shell navigation.
- **Navigation-only:** non-navigate modes return `false` for both predicates.
- **R57 verbatim:** eleven paths in order, `/notifications` deliberately absent, cited in source.
- **No banned globals:** `caches`, `fetch`, `self`, `BroadcastChannel`, `RENEWAL_CHANNEL`, `navigator`, storage, `document`, `import.meta`, and URL schemes are absent from module code.
- **Mutual exclusion:** no path is both denylisted and shell.
- **L12 honoured:** no T01–T08 file touched; T01 assertions stay intact.
- **Drift guard:** compares the code list to `agents.md` and asserts order.

---

## Summary

The T09 implementation is correct and well-shaped for future Workbox wiring. The only fresh finding is the module-load `repoRoot` computation, which is low-risk. The more important limitations — precache interaction and the lack of a production consumer — are inherent to the task ordering and are already flagged for task 12. No Phase 9 code change is required for T09 itself.
