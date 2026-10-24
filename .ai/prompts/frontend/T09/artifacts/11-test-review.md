# frontend · T09 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **Denylisted navigations bypass the worker.** Navigations to the paths in `agents.md` are never answered from cache or with the shell. Verifies `sasNavigationBypassesServiceWorker`. Cites R57, L17.

---

## Method

Reviewed the Phase 10 test manifest for `sasNavigationBypassesServiceWorker` (55 cases, 11 mutation breaks) against the frozen T09 acceptance criteria (AC1–AC9) and the Phase 8 findings. No code was rewritten.

The manifest already covers the Phase 8 findings: the Workbox shape is exported as both `NAVIGATION_DENYLIST` and `isShellNavigation`; segment-aware matching is pinned by AC4; `/app` without a trailing slash is in AC2; non-navigation returns are in AC6; the drift guard parses `agents.md`; and the cache-read ordering is exercised by mutating the test harness (M9).

---

## Gaps

### 1. No test for a denylisted path with percent-encoded characters
**Gap ·** AC4 tests query strings and hashes, but not percent-encoded path segments such as `/login%2Fstep-two` or `/%2Ewell-known/`.  
**Why it matters ·** Browsers normalize paths before the worker sees them, but a subtle difference between `URL.pathname` normalization and the segment regex could change the decision.  
**Suggested test ·** Add one or two AC4 rows: `/login%2Fstep-two` should be treated as `/login/step-two` (denylisted) by a normalized URL, and `/app%2Fsign-in` should be treated as `/app/sign-in` (shell).

### 2. No test for absolute URLs from another origin
**Gap ·** Every test uses `new URL(path, ORIGIN)`. The predicates use `url.pathname`, so origin is ignored, but this is not asserted.  
**Why it matters ·** A worker can see navigations to any origin if the scope is wide enough. A regression that accidentally considered the origin would change the policy.  
**Suggested test ·** Add a case with `new URL('https://other.example.test/login', ORIGIN)` (or simply an absolute URL) and assert it is still denylisted because only `pathname` matters.

### 3. No test for the root path `/`
**Gap ·** The SPA owns only `/app/*`, so `/` is not a shell route. There is no case asserting that a navigation to `/` is neither denylisted nor shell.  
**Why it matters ·** If the worker scope ever widens to `/`, the fallback behavior for `/` becomes observable. Documenting the expected classification now prevents a later task from assuming `/` should return the shell.  
**Suggested test ·** Add an AC4 row: `/` is `denylisted: false, shell: false`.

### 4. No test that the `NAVIGATION_DENYLIST` regexes are derived in the same order as the paths
**Gap ·** AC7 asserts the list order matches `agents.md` and that there is one pattern per path with correct behavior, but it does not assert that `NAVIGATION_DENYLIST[index]` corresponds to `DENYLISTED_NAVIGATION_PATHS[index]`.  
**Why it matters ·** A future refactor that maps the list out of order (e.g., sorts it before creating regexes) would keep AC7 green while breaking the verbatim-order contract.  
**Suggested test ·** Extend the AC7 pattern test to assert `NAVIGATION_DENYLIST[i]!.test(DENYLISTED_NAVIGATION_PATHS[i]!)` is true for every index, and that no earlier/later pattern matches the base path.

### 5. No test that the source scan catches inline comments
**Gap ·** The self-review noted that the AC9 banned-global scanner strips block comments and whole-line comments but not inline trailing comments. This is documented but not tested.  
**Why it matters ·** A banned token in an inline comment would cause a spurious failure; conversely, a banned token hidden in an inline string literal would pass if the scan only looks at code tokens.  
**Suggested test ·** Optional: add a one-off probe that writes a temporary file with a banned token inside an inline comment and verifies the scanner fails. This is a meta-test and may be overkill.

### 6. No test for the module's behavior when `url.pathname` is empty
**Gap ·** The predicates read `url.pathname`. For a navigation to `https://app.example.test` (no path), `pathname` is `/`. This is neither denylisted nor shell.  
**Why it matters ·** It is an edge case that confirms the module never falls back to shell by accident.  
**Suggested test ·** Add an AC4/AC6 row: `new URL('https://app.example.test')` with mode `navigate` is neither denylisted nor shell.

### 7. No test that the shell fallback mock ordering is pinned for every denylisted path
**Gap ·** AC3 runs all 22 denylisted paths through `simulateNavigationFallback` and asserts no cache read. This covers ordering at the harness level, but the harness is test code; a production worker could still be wired to read the cache first.  
**Why it matters ·** T09 cannot enforce production wiring, but the test manifest should make this limitation explicit. The Phase 8 finding already flags it.  
**Suggested test ·** No additional test is possible in T09; record this as a carry-forward requirement for task 12's test suite.

---

## Non-gaps (acknowledged, no change needed)

- **55/55 cases pass** and **11/11 mutants caught**.
- **Every AC has dedicated cases**, with AC1 and AC4 having extensive tables.
- **R57 verbatim list** is exported, cited, and drift-guarded against `agents.md`.
- **Segment-aware matching** is guarded by `/accountsxyz`, `/apikeys`, `/logouts`, `/application`, `/xwell-known`, and `/app/accounts`.
- **Non-navigation modes** are guarded by AC6.
- **Mutual exclusion** is asserted over all 47 paths used in the file.
- **Cache read/write behavior** is guarded by AC3 and AC5.
- **No banned globals** is guarded by AC9.
- **`/app` without trailing slash** is included in AC2.
- **Order sensitivity** is guarded by AC7 and the sub-path table coverage case.
- **M9 (cache read before denylist)** is applied to the test harness because the production module is pure; this is the intended Phase 4 resolution.

---

## Summary

The T09 test suite is thorough: 55 cases, 11 mutation breaks, every branch of the four-line module exercised, and all acceptance criteria covered. The remaining gaps are edge cases (percent-encoding, absolute URLs, root path `/`, empty pathname, regex order) and a forward-looking carry-forward for task 12's worker wiring. Adding the edge-case rows would make the classification table complete, but the suite already provides a strong regression guard for the task's scope.
