# frontend · T10 — Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T10 — End-session navigation bypasses the worker**. Findings are against the brief itself; no code was changed.

---

## Findings

### 1. The end-session URL shape is not spelled out in the brief
**Issue:** The test is meant to use "the real end-session URL shape taken from the backend's green test," but the brief does not include an example. The tester has to infer the OIDC end-session query parameters (`id_token_hint`, `post_logout_redirect_uri`) from R19. A wrong guess makes AC1/AC4/AC5 cases test the wrong URL.  
**Severity:** Medium — functional risk if the backend uses different parameter names or a different path.  
**Evidence ·** Brief §Scope: "the real end-session URL shape taken from the backend's green test, its variants"; §Required Tests: "a JWT-shaped `id_token_hint`".  
**Recommendation ·** Add a concrete example to the frozen brief, e.g.:
```
/connect/logout?id_token_hint=<JWT>&post_logout_redirect_uri=https://app.example.test/app/signed-out
```
and list the six AC4 variants (extra params, different `post_logout_redirect_uri`, encoded token, missing optional params, etc.).

### 2. The `routes.tsx` drift guard is specified as "read from disk"
**Issue:** The brief says `frontend/src/app/routes.tsx` is read from disk for AC7. `routes.tsx` is TypeScript/TSX and exports `PUBLIC_ROUTE_PATHS`, a typed array. Parsing source text with regex to find `'signed-out'` is brittle; a refactor that renames the constant, reorders routes, or changes syntax could break the guard even though the route still exists.  
**Severity:** Medium — maintenance fragility.  
**Evidence ·** Brief §Dependencies: "`frontend/src/app/routes.tsx` read from disk for AC7."  
**Recommendation ·** Prefer importing `PUBLIC_ROUTE_PATHS` from `../app/routes.tsx` and asserting it includes `'signed-out'`. If the drift guard must remain source-based, use a simple regex that looks for the literal `'signed-out'` anywhere in the public-routes array, and document that it is a lightweight guard, not a typed contract check.

### 3. The test harness is duplicated from T09
**Issue:** The constraint requires `simulateNavigationFallback` to be duplicated into T10's file with a comment pointing at T09. Any bug fix or behavior change to the harness must be made in two places, and the comment is easy to miss.  
**Severity:** Low — test-only code, but a real maintenance cost.  
**Evidence ·** Brief §Constraints: "A local harness copy. `simulateNavigationFallback` is duplicated into this file (about twelve lines) with a comment pointing at T09's."  
**Recommendation ·** At the Phase 4 gate, consider whether L12 really forbids a new shared test-only helper file such as `frontend/src/pwa/__tests__/navigationFallbackHarness.ts`. If sharing is rejected, add a prominent header comment in both files warning maintainers to keep the two copies in sync until task 12 replaces them with Workbox wiring.

### 4. AC2's "token never cached" assertion tests the harness, not production
**Issue:** T10 adds no production code. The assertion that the ID token does not appear in Cache Storage is therefore an assertion about the test's own `simulateNavigationFallback`, not about the eventual worker. A real worker could still cache the end-session response if task 12 wires it differently.  
**Severity:** Low — inherent to a test-only task, but the limitation must be recorded plainly.  
**Evidence ·** Brief §Files to Modify: "None expected"; §Inputs/Outputs/State: "the module under test is pure and this task adds no production code."  
**Recommendation ·** State explicitly in the frozen brief and in the Phase 13 summary: "T10 demonstrates that T09's policy, when wired correctly, prevents the end-session response and the ID token from entering Cache Storage. Task 12 must ensure the real Workbox navigation route applies the same denylist-before-cache ordering."

### 5. The precache-hole carry-forward should be locked
**Issue:** `/app/signed-out` is a shell route and could be answered by a precache route before the navigation fallback sees it. The brief notes this as Open Question 4 / task 12's problem, but it is easy to lose track of.  
**Severity:** Low — forward dependency.  
**Evidence ·** Brief §Scope Out: "The precache manifest guard — task 12 owns the manifest"; §Open Questions #4.  
**Recommendation ·** Add an explicit locked decision or carry-forward note in the frozen brief: "Task 12 must assert that the precache manifest does not contain `/connect/logout` and that the navigation fallback still answers `/app/signed-out` even when it is in the precache manifest."

### 6. The 302 redirect is simulated, not driven by a real `FetchEvent`
**Issue:** The two-step sequence `/connect/logout` → `/app/signed-out` will be tested by calling `simulateNavigationFallback` twice. This proves policy but not the real browser redirect chain (the 302 target is a new navigation that the shell answers).  
**Severity:** Info — acceptable within jsdom's limits.  
**Evidence ·** Brief §Scope: "the two-step `/connect/logout` → `/app/signed-out` sequence"; §Dependencies: "jsdom has no `ServiceWorkerGlobalScope`, `FetchEvent` or `caches`."  
**Recommendation ·** Document in the test file that the second call represents the browser following the SAS 302 redirect; the real behavior is validated by task 12's integration tests.

### 7. No mutation break for the token appearing in a cache *key*
**Issue:** The mutation breaks include "remove `/connect/logout` from the list," "decide on `url.href`/`url.search`," "let the shell answer `/connect/logout`," "read the cache before the denylist check," and "make the shell matcher reject `/app/signed-out`." There is no mutation that makes the shell cache key include the query string, which could be how an ID token leaks if `/app/signed-out` were ever requested with the token's query params.  
**Severity:** Info — the token is on `/connect/logout`, not `/app/signed-out`, so this path is unlikely.  
**Evidence ·** Brief §Required Tests "Mutation breaks."  
**Recommendation ·** Optional: add an AC4/AC5 variant where the post-logout redirect URI includes a query string, and assert the shell answer does not cache under a key containing that query. If the redirect URI is always exact-match allowlisted without query, this is not needed.

---

## Open Questions

- None blocking. The expected outcome is test-only (Open Question 1). Findings 1 and 2 should be resolved before implementation starts; findings 3–5 are documentation/carry-forward items.
