# frontend · T11 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **API routes are NetworkOnly.** No API response is served from cache. Verifies `serviceWorkerNeverCachesApiResponses`. Cites R57, L17.

---

## Method

Reviewed the Phase 10 test manifest for `serviceWorkerNeverCachesApiResponses` (74 cases, 11 mutation breaks) against the frozen T11 acceptance criteria (AC1–AC10) and the Phase 8 findings. No code was rewritten.

The manifest already resolves the Phase 8 findings: AC5 now pairs all twelve prefixes with their T09 navigation classification, and AC6 includes `/api/v2/accounts` and `/api/health` future-path rows.

---

## Gaps

### 1. No drift guard against `agents.md:90`
**Gap ·** AC9 compares the prefix list to `design.md`'s edge map, but there is no test that `agents.md` still contains the sentence "API routes use NetworkOnly."  
**Why it matters ·** If that standing rule were softened or removed, every T11 case would still pass because the policy is derived from `design.md`. The task cites R57/L17 and `agents.md:90`, so the standing rule should be guarded.  
**Suggested test ·** Add a case that reads `spec/frontend/agents.md`, locates the "API routes use NetworkOnly" sentence, and asserts it exists. This mirrors T09/T10's sentence-level drift guards.

### 2. `segmentPrefixPattern`'s trailing-slash branch is unexercised in T11
**Gap ·** The manifest honestly notes that `segmentPrefixPattern`'s `path.endsWith('/')` true-branch is not exercised here because the module only calls it with `/app/assets`. It is covered by T09's copy, but the duplication means T11's own coverage is incomplete.  
**Why it matters ·** A regression that broke the trailing-slash handling in T11's copy would not be caught by T11's suite, and T09's tests might not run together in all configurations.  
**Suggested test ·** Add a cacheable row with an asset path that has a trailing slash, e.g., `/app/assets/`, and assert it is cacheable. Alternatively, collapse the helper into a shared module in task 12 as already planned.

### 3. No cache-hit test for the shell document
**Gap ·** AC2 tests that shell documents and assets are cacheable on a miss, and that a cached asset is served on a hit. There is no equivalent hit case for `/app/index.html`.  
**Why it matters ·** A bug where the shell path was used as a cache key on miss but a different key on hit would pass the current AC2 rows.  
**Suggested test ·** Add a row that mocks `cache.match` to return `'cached shell'` for `/app/index.html` and asserts the shell is served from cache without a `put`.

### 4. No assertion that a cacheable miss writes the exact pathname key
**Gap ·** AC2 asserts `cache.match` is called with the path, but it does not assert that `cache.put` is called with the same pathname on a miss.  
**Why it matters ·** A mutant that keyed the write differently from the read would still return `network-then-cached` and pass the current assertions.  
**Suggested test ·** In AC2's miss rows, assert `cache.put` was called with the same pathname that `cache.match` received.

### 5. No test for query/hash on cacheable paths
**Gap ·** AC4 tests query/hash invariance for denylisted navigation indirectly through URL parsing, but there is no case for a cacheable asset or shell with a query string or hash.  
**Why it matters ·** The predicates use `url.pathname`, so query/hash should be ignored, but this is not explicitly pinned for the cacheable side. A regression that switched to `url.href` would turn `/app/assets/main.js?v=2` into NetworkOnly and be missed.  
**Suggested test ·** Add AC2 rows: `/app/index.html?state=s1` and `/app/assets/main-abc123.js#chunk` are cacheable and keyed by pathname only.

### 6. No negative segment-boundary test for every prefix
**Gap ·** AC7 tests `/application`, `/app2`, `/appfoo`, `/app/assetsx/evil.js`, and `/app-assets/main.js`, but it does not test a similarly near-miss for each backend prefix (e.g., `/accountsxyz`, `/apikeys`, `/oauth2x`).  
**Why it matters ·** The default-deny rule would catch most of these anyway, but a prefix-specific boundary bug (e.g., `/accountsxyz` accidentally treated as `/accounts`) would be more cleanly caught by per-prefix negative rows.  
**Suggested test ·** Optional: add one row per prefix that appends a suffix without a slash and asserts NetworkOnly.

### 7. No test that non-GET methods on cacheable paths are still cacheable
**Gap ·** AC4 covers methods on API paths. There is no case that a `HEAD` or `POST` to `/app/assets/foo.js` is still classified as cacheable (even if a real worker would not cache a POST response).  
**Why it matters ·** The predicate is method-independent; the test harness could be mutated to consult the method only for cacheable paths and nothing would notice.  
**Suggested test ·** Optional: add one row `POST /app/assets/main-abc123.js` returns `network-then-cached` and `isCacheableRequest` is true.

### 8. No test for `/app` with a trailing slash on the shell hit path
**Gap ·** `SHELL_DOCUMENT_PATHS` includes `/app/`, and AC2 tests it on a miss. There is no hit case for `/app/`.  
**Why it matters ·** Minor, but combined with gap 3 it leaves the shell hit path uncovered.  
**Suggested test ·** Include `/app/` in the proposed shell hit test from gap 3.

---

## Non-gaps (acknowledged, no change needed)

- **74/74 cases pass**, and **10 of 11 mutants caught**; M9 survived as an intentional guard-weakening check, with M9b proving order is still pinned elsewhere.
- **Every AC has dedicated cases**, including 25 for AC1, 14 for AC5, and 7 for AC6.
- **Default-deny is structural** — `isNetworkOnlyRequest` is the exact negation of `isCacheableRequest` (AC10).
- **M2 (invert default)** is caught by AC6/AC7, and **M10 (nothing cacheable)** is caught by AC2.
- **Twelve prefixes match `design.md`** in order, with `/api/v1` normalized to `/api` (AC9).
- **Segment boundaries hold** for `/application`, `/app2`, `/appfoo`, `/app/assetsx/evil.js`, `/app-assets/main.js` (AC7).
- **Sensitive body kinds named:** account, token, receipt, evidence (AC3).
- **Method independence** exercised with GET/POST/PUT/DELETE on an API path (AC4).
- **T09 composition** covers all twelve prefixes and `/connect/logout` (AC5).
- **`/notifications` SSE reasoning** is recorded and tested (AC8).
- **Purity scan** is in AC10.
- **No production file touched** beyond the two new files.

---

## Summary

The T11 test suite is very strong: 74 cases, a clean mutation pass with one honestly reported survival, and both under- and over-restriction counterweights. The main remaining gaps are the missing `agents.md:90` drift guard, the unexercised trailing-slash branch, and a handful of cacheable-side edge cases (shell hit, put key, query/hash on cacheable paths). Adding these would make the suite a complete regression guard for the request-side policy.
