# frontend · T11 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/src/pwa/apiRequestPolicy.ts`, `frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/frontend/design.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **API routes are NetworkOnly.** No API response is served from cache. Verifies `serviceWorkerNeverCachesApiResponses`. Cites R57, L17.

---

## Review scope

Reviewed the Phase 6 T11 implementation against the frozen brief, `design.md`, and the Phase 7 self-review. No code was rewritten.

The implementation is solid: default-deny, twelve verbatim prefixes normalized from `/api/v1` to `/api`, segment-aware cacheable boundaries, pure predicates, and a composition test with T09. The self-review found seven issues, mostly structural/forward-looking. This review corroborates them and adds three fresh observations.

---

## New findings

### 1. AC5's composition test covers only five of the twelve overlapping prefixes
**Issue ·** The test asserts that `/accounts`, `/admin`, `/api-keys`, `/userinfo`, and `/api/v1/invoices` are declined by T09 as navigations and marked NetworkOnly by T11. It does not exercise `/login`, `/logout`, `/oauth2`, `/.well-known`, `/error`, `/connect`, or `/notifications`. A regression that made one of those cacheable would be caught by AC1, but the explicit compatibility pairing with T09 would be missing.  
**Severity ·** Low — AC1 covers the NetworkOnly half; the composition test is more illustrative than load-bearing.  
**Evidence ·** `frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts:160-168`.  
**Recommendation ·** Extend AC5 to iterate over all twelve prefixes, asserting the appropriate T09/T11 relationship for each: for the eleven R57 paths (including `/connect/logout` rather than `/connect`) T09 declines the navigation; for `/connect` and `/notifications` T09 simply does not claim them as shell, which is still compatible with T11's NetworkOnly request classification.

### 2. No case exercises a future `/api/v2` path
**Issue ·** The module normalizes `/api/v1` to `/api` so future API versions are safe by default. AC1 tests `/api/v1/invoices/.../payments`, but there is no case for `/api/v2/...` or a bare `/api/health`.  
**Severity ·** Low — the normalization logic is covered by the drift guard and by the `/api` prefix test; a `/api/v2` case would only make the intent explicit.  
**Evidence ·** `apiRequestPolicy.ts:35`; test file AC1/AC9.  
**Recommended test ·** Add one AC1 or AC6 row: `/api/v2/accounts` is NetworkOnly.

### 3. `repoRoot` is computed at module load from Vitest state
**Issue ·** The drift guard and purity scan compute `repoRoot` at module load via `expect.getState().testPath`. This is stable in the current Vitest setup and now wrapped in a clear `repoFile` helper, but it still couples the tests to the runner's import order and the file's depth.  
**Severity ·** Low.  
**Evidence ·** `serviceWorkerNeverCachesApiResponses.test.ts:29-41`.  
**Recommendation ·** No change required for T11. If a future task opens this file for other reasons, consider moving the root computation into each test (e.g., using `import.meta.dirname` with a fallback).

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| 1 | Default-deny makes most AC1 rows weak; strength rests on AC2/AC10 | **Confirmed.** The design is sound, but the test count is less meaningful than it looks. The mutant that inverts the default is the real proof. |
| 2 | `segmentPrefixPattern` duplicated from T09 | **Confirmed, acceptable.** A shared helper needs a task that legitimately opens `navigationDenylist.ts`; task 12 is the natural place. |
| 3 | Exported prefix list is unused by the decision | **Confirmed, important carry-forward.** The list is load-bearing for task 12's matchers, defended only by AC9. Task 12 must import it and must not restate it. |
| 4 | `/app/sign-in` as a request is NetworkOnly | **Confirmed, acceptable.** Defensible per §5, and practically unobservable because SPA routes arrive as navigations handled by T09. |
| 5 | No drift guard against `agents.md:90` | **Confirmed, low.** AC9 guards against `design.md`; a sentence-level guard against `agents.md` "API routes use NetworkOnly" would be a nice addition. |
| 6 | Harness proves decision, not plumbing | **Confirmed.** The file header and comments already state this; task 12 must wire a true `NetworkOnly` strategy. |
| 7 | AC10 purity scan keeps trailing comments | **Confirmed.** Same safe-direction over-strictness as T09/T10. |

---

## Non-gaps (acknowledged, no change needed)

- **Default-deny is structural:** `isNetworkOnlyRequest` is the exact negation of `isCacheableRequest` (AC10).
- **Twelve prefixes match `design.md`** in order, with `/api/v1` normalized to `/api` (AC9).
- **Segment boundaries hold:** `/application`, `/app2`, `/appfoo`, `/app/assetsx/evil.js`, `/app-assets/main.js` are NetworkOnly; `/app/assets/...` is cacheable.
- **Sensitive body kinds named:** account, token, receipt, evidence (AC3).
- **Method independence:** predicates take only a `URL`; AC4 exercises four methods.
- **T09 composition:** imports T09 predicates without editing T09 (AC5).
- **`/notifications` covered:** with the SSE reasoning (AC8).
- **Purity:** no `caches`, `fetch`, `self`, storage, navigator, document, `import.meta`, or absolute URL in module code (AC10).
- **L12 honoured:** two new files only; `navigationDenylist.ts`, T09/T10 tests, and T01–T08 untouched.

---

## Summary

The T11 implementation correctly encodes the NetworkOnly policy as a narrow cacheable set and a default-deny everything else. The main carry-forwards are for task 12: use the exported prefix list when building Workbox matchers, and wire a true `NetworkOnly` strategy that neither reads nor writes the cache. The fresh findings are minor: extend the T09 composition test to all twelve prefixes, add a future `/api/v2` case, and consider moving the module-load repo-root computation. No Phase 9 code change is required.
