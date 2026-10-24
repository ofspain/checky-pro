# frontend · T11 — Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T11 — API routes are NetworkOnly**. Findings are against the brief itself; no code was changed.

---

## Findings

### 1. The twelve prefixes are not enumerated in the brief
**Issue:** The brief says `apiRequestPolicy.ts` will contain "the twelve edge prefixes as a verbatim, drift-guarded list" but never lists them. An implementer has to derive them from `design.md` §4c(2), which is inside a blockquote and mixes path styles (`/oauth2` without a slash, `/api/v1` with a version). A wrong inference makes AC1 and the drift guard fail.  
**Severity:** Medium — functional and maintenance risk.  
**Evidence ·** Brief §Scope: "the twelve edge prefixes"; §AC1: "the twelve prefixes and their sub-paths NetworkOnly"; `spec/frontend/design.md:158-160` lists the edge map.  
**Recommendation ·** Add the exact list to the frozen brief, e.g.:
```ts
const NETWORK_ONLY_PREFIXES = [
  '/oauth2', '/login', '/logout', '/connect', '/.well-known',
  '/userinfo', '/error', '/accounts', '/admin', '/api-keys',
  '/api',      // covers /api/v1 and future API versions
  '/notifications',
] as const;
```
Note the intentional normalization of `/api/v1` to `/api` so future versions are safe by default, and document that this is a deliberate superset of the edge map.

### 2. The module's Workbox interface is not specified
**Issue:** The brief says task 12 will be handed "pure predicates," but it does not define their names, signatures, or return semantics. T09 settled on `isDenylistedNavigation(url, mode)` and `isShellNavigation(url, mode)`; T11 needs an equivalent pair for request classification. Without an explicit contract, task 12 may write a mismatched integration.  
**Severity:** Medium — integration boundary risk.  
**Evidence ·** Brief §Scope: "the pure predicates task 12 will hand to Workbox"; §Inputs/Outputs: "A cacheability decision plus the exported prefix list."  
**Recommendation ·** Define exports in the frozen brief, for example:
```ts
export const NETWORK_ONLY_PATH_PREFIXES: readonly string[];
export function isNetworkOnlyRequest(url: URL): boolean;
export function isCacheableRequest(url: URL): boolean;
```
where `isNetworkOnlyRequest` returns `true` for every path that is not `/app` shell or `/app/assets/*`, and `isCacheableRequest` is the narrow positive case.

### 3. The drift-guard parser for `design.md` is unspecified and the map is in a blockquote
**Issue:** AC7 requires the code list to match the edge map in `design.md`. The map is inside a `>` blockquote that spans multiple lines and uses commas and arrows (`→`). A line-based regex that does not strip the `>` prefix or handle the line break after `/api-keys` will fail.  
**Severity:** Medium — test fragility.  
**Evidence ·** Brief §Dependencies: "`spec/frontend/design.md` read from disk for AC7"; `design.md:158-160`.  
**Recommendation ·** Define the parser explicitly: strip leading `> ` from each line, concatenate the paragraph that contains "Edge path map", extract backtick-delimited paths, and normalize `/api/v1` to `/api` before comparing. Add a test that fails with a clear message if the paragraph cannot be found.

### 4. `/api/v1` vs `/api/` is unresolved
**Issue:** `design.md` names `/api/v1` as the payment-service prefix, while R57 names `/api/` and the proposed list in finding 1 uses `/api`. A drift guard that expects the literal `/api/v1` string will fail. A module that uses `/api/v1/` will not cover `/api/v2` by default.  
**Severity:** Medium — default-safety and drift-guard alignment.  
**Evidence ·** `spec/frontend/design.md:159`: "`/api/v1` → payment-service"; `agents.md:89`: "`/api/`"; brief §AC6: "`/notifications` explicitly."  
**Recommendation ·** Lock the decision: the NetworkOnly prefix is `/api` (segment boundary), which covers `/api/v1` and any future version. The drift guard normalizes `/api/v1` to `/api` before comparison, and the frozen brief records this normalization.

### 5. The cacheable rule needs a segment-boundary guard
**Issue:** AC2 says `/app` shell and `/app/assets/*` are cacheable. A naive prefix match on `/app` would also claim `/application` or `/app2`, which are not SPA routes and should not be cached.  
**Severity:** Low — likely no such routes exist, but the rule should be robust.  
**Evidence ·** Brief §Constraints: "Cacheable is the narrow case (`/app` shell and `/app/assets/*`)"; §AC2.  
**Recommendation ·** Add an AC4-style boundary case: `/application` and `/app2` are NetworkOnly, while `/app`, `/app/`, `/app/sign-in`, and `/app/assets/foo.js` are cacheable. Reuse the segment-prefix pattern from T09.

### 6. Agreement with T09 should be asserted by importing T09's predicates
**Issue:** AC4 says the API policy must agree with T09 where an API path is also in R57's navigation list. The brief mentions one case, but does not say how to implement the comparison without editing T09.  
**Severity:** Low — a test can do it.  
**Evidence ·** Brief §Constraints: "Agree with T09, never contradict it"; §AC4.  
**Recommendation ·** In the T11 test, import `isDenylistedNavigation` and `isShellNavigation` from `navigationDenylist.ts` and assert for each overlapping path (e.g., `/accounts`, `/admin`, `/api-keys`, `/userinfo`, `/api/`) that T09 declines the navigation and T11 marks the request NetworkOnly. This imports T09 without modifying it.

### 7. The `/default-ui.css` path is not addressed
**Issue:** The Caddyfile routes `/default-ui.css` to the auth service. It is a SAS UI stylesheet, not an API route, and it is absent from both R57 and the task's prefix list. Under default-deny it becomes NetworkOnly, which is safe but possibly surprising if someone expects SAS assets to be cacheable.  
**Severity:** Info.  
**Evidence ·** `infra/local/edge/Caddyfile:12`.  
**Recommendation ·** Document in the frozen brief that SAS-hosted assets such as `/default-ui.css` are outside the SPA's cacheable set and are therefore NetworkOnly in the worker's request policy. They are not API responses, so this does not violate R57; it is a consequence of default-deny.

### 8. The mutation break "harness put an API response after fetching it" targets test code
**Issue:** Because the policy module is pure, the "nothing is written" guarantee cannot be enforced by the module itself. The mutation break is applied to the test harness, not production code. This is the same pattern as T09/T10 and is acceptable, but it should be explicit.  
**Severity:** Info.  
**Evidence ·** Brief §Constraints: "The pure policy cannot enforce that at runtime — task 12's wiring must"; §Required Tests mutation breaks.  
**Recommendation ·** State in the frozen brief and Phase 13 summary: "T11 demonstrates the policy; task 12 must wire Workbox so that API routes use a true NetworkOnly strategy that neither reads nor writes the cache."

### 9. No explicit rule for non-GET API methods
**Issue:** AC4 says method independence. The brief notes input includes method. The test should ensure a `POST /accounts/password-reset` is NetworkOnly just like `GET /accounts/me`.  
**Severity:** Info — covered by AC4 but worth a concrete test row.  
**Evidence ·** Brief §AC4: "method- and mode-independence."  
**Recommendation ·** Add at least one AC1/AC4 row that uses `POST /accounts/password-reset` and asserts NetworkOnly.

---

## Open Questions

- None blocking. The default-deny vs prefix-list decision (Open Question 1) should be settled at the Phase 4 gate; the recommendation here is default-deny plus the explicit list for matchers and drift guard.
