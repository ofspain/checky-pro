# frontend · T08 — Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T08 — No POST replay**. Findings are against the brief itself; no code was changed. The existing `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` (T07 cases) was also reviewed to see how the T08 cases will fit in.

---

## Findings

### 1. The POST-inventory source is read-only, but the test list will be hand-maintained
**Issue:** AC2 asks for a representative set of `auth.yaml` POST operations. The brief names `contracts/api/auth.yaml` as the source, but the test file can only read the compiled client and the existing `AUTH_PUBLIC_OPERATIONS` set. There is no mechanism to keep a hand-curated list of the 19 POST operations in sync with the contract. If a new POST endpoint is added, the T08 inventory test will silently become less representative.  
**Severity:** Medium — maintenance risk, not a functional defect.  
**Evidence ·** Brief §Scope: "The POST inventory from `contracts/api/auth.yaml` (19 POST operations, 5 public) as the source for which operations the cases name." Brief §Files to Modify: only the test file.  
**Recommendation ·** Either (a) generate the POST list from `contracts/api/auth.yaml` in CI and fail if the test file drifts, or (b) add a code comment in the test file that the inventory was derived from `auth.yaml` at a specific commit and must be updated when the contract changes. A lightweight check: assert that the generated client's typed `POST` methods match the hand list, if TypeScript exposes them.

### 2. The uncloneable-body case needs a concrete way to make `Request.clone()` fail
**Issue:** AC4 requires a 429 on a POST whose body cannot be cloned. The T05 boundary already handles uncloneable bodies by recording `entry.clone = null` and skipping the re-send on 401. For a 429, the boundary never re-sends anyway, so the test mainly proves one transport call and the original body sent once. However, constructing a body that makes `Request.clone()` throw in jsdom/Node is non-trivial; the brief does not specify how.  
**Severity:** Low — the path exists in T05 and is covered by T05 tests; T08 can reuse the same technique.  
**Evidence ·** Brief §Scope: "an uncloneable POST body"; §Constraints: "Null handling: an uncloneable body is a real `null` clone path in `sessionBoundary.ts`."  
**Recommendation ·** Add a helper in the test file (or reuse T05's pattern) that creates a `Request` whose `clone()` throws, and assert the transport sees exactly one call with the original body. Document the helper next to AC4.

### 3. The "standing guard" question is left to Phase 4
**Issue:** The brief asks whether T08 should be cases only or also a standing guard. The default is cases only, with AC5 as the structural assertion. A standing guard would need production code, which is explicitly out of scope ("If Phase 3/4 decides a standing guard is wanted, it would be an addition to the existing test file, not a new production module"). This is a design decision that affects how strongly the no-replay guarantee is enforced.  
**Severity:** Low — the cases plus mutation breaks are likely sufficient.  
**Evidence ·** Brief §Open Questions #1.  
**Recommendation ·** Resolve at Phase 4: cases only is the simpler, frozen-brief-compliant choice. If a standing guard is wanted, it should be a test-only assertion (e.g., a helper that verifies every POST 429 in the file results in exactly one transport call) rather than production code, preserving the "no new production output" scope.

### 4. The boundary between T05's 401 re-send and R23's no-replay needs pinning
**Issue:** Open Question #2 asks whether T05's 401-triggered re-send of a POST violates R23's no-replay clause. The brief's default reading is "no" — the `WHEN` of R23 is the 429 case. This interpretation is reasonable but non-obvious; if a later reader or auditor disagrees, they may try to change T05.  
**Severity:** Low — interpretation risk, not a code bug.  
**Evidence ·** Brief §Open Questions #2; AC3: "401 → one renewal → one re-send → 429 on the re-send gives exactly two transport calls."  
**Recommendation ·** Record the rationale explicitly in the frozen brief (and ideally in a code comment near AC3 in the test file): T05's re-send is part of single-flight 401 renewal (R21), not 429 handling; R23 applies once the final response is a 429. AC3 encodes this boundary.

### 5. AC5's structural assertion is hard to test as a runtime case
**Issue:** AC5 says "the 429 path adds no `onRequest` and no `onError`, and its `onResponse` returns `undefined`; the boundary takes no action for any status other than 401." This is mostly a code-shape assertion. The existing cases cover the observable consequences (no extra transport calls, 429 passed through), but there is no direct test that the notice middleware object lacks `onRequest`/`onError` or that the boundary's `onResponse` only checks `status === 401`.  
**Severity:** Low — the mutation breaks cover the behavioral consequences.  
**Evidence ·** Brief §AC5.  
**Recommendation ·** Add a lightweight structural case: assert `Object.keys(rateLimitNotice.middleware)` equals `['onResponse']`, and assert the boundary's `onResponse` returns the original response for 200/400/403/429/503. This makes AC5 a first-class test rather than an inspection note.

### 6. The test file is becoming a long monolith
**Issue:** T07 already has 29 cases. Adding nine T08 cases will push the file past 1,000 lines. The brief requires appending to the same `describe`, which is fine, but readability and maintainability will suffer if not grouped.  
**Severity:** Info.  
**Evidence ·** Brief §Files to Modify.  
**Recommendation ·** Group T08 cases under a nested `describe('no POST replay', …)` inside the existing describe, with a comment header per AC. Keep helpers local. This does not change the named test string and keeps T07's cases intact.

### 7. No explicit test that a 429 POST body is not logged or stored
**Issue:** AC8 covers privacy for T07. T08 deals with POST bodies, which are more sensitive. The existing AC8 case checks storage/cookies/logs for a GET 429. It does not send a POST body and assert the body does not appear in logs or storage.  
**Severity:** Low — the middleware never reads the body, so it cannot leak it.  
**Evidence ·** Brief §AC1: public/protected POST cases; §AC8: privacy from T07.  
**Recommendation ·** In the POST cases, include a body containing an obvious secret string and assert no console/storage/cookie contains it. This is a stronger AC8 guard for POSTs specifically.

### 8. The "representative set" for AC2 is undefined
**Issue:** AC2 says "across a representative set of `auth.yaml` POST operations." It does not say how many operations constitute representative, nor which ones. The risk is choosing too few (missing a body-shape edge case) or too many (slow, redundant tests).  
**Severity:** Low — the inventory gives a concrete upper bound.  
**Evidence ·** Brief §AC2.  
**Recommendation ·** Define the representative set explicitly in the frozen brief: e.g., the 5 public POSTs plus a sample of protected POSTs covering JSON body, no body, and a path parameter (`POST /api-keys`, `POST /accounts/me/sessions`, `POST /accounts/password-reset`, `POST /accounts/totp/confirm`). Document why each is included.

---

## Open Questions

- None blocking. The standing-guard decision (#3) and the R23 boundary interpretation (#4) should be settled at the Phase 4 gate.
