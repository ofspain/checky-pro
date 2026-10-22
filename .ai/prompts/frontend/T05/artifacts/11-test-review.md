# frontend · T05 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T05 — Single-flight 401 |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **Single-flight 401.** A 401 triggers exactly one renewal attempt. Verifies `parallel401sTriggerExactlyOneRenewal`. Cites R21.

---

## Method

Reviewed the Phase 10 test manifest for `parallel401sTriggerExactlyOneRenewal` (17 cases, 12 mutation breaks) against the frozen T05 acceptance criteria (AC1–AC8) and the named test requirement in `package.md` §8. No code was rewritten.

---

## Gaps

### 1. No test for network failure on the initial request
**Gap ·** The suite covers 200/403/429/500 responses (cases 9–12) but does not cover the `onError` path: the underlying transport rejecting before a response is produced.  
**Why it matters ·** `onError` deletes the pending entry. A regression that forgets to delete the entry would leak memory or cause the next request with the same id to behave incorrectly. Also, the caller must see the network error propagate unchanged.  
**Suggested test ·** Add a case where the transport rejects with `TypeError('Failed to fetch')` for one request. Assert the promise rejects with that error, `renew` is not called, and the pending map is empty after `settle()`.

### 2. No test for request abort during a renewal
**Gap ·** A request started before a 401 may be aborted while the renewal is in flight. The middleware's `onError` runs, but there is no case covering whether the shared renewal and other waiters remain healthy.  
**Why it matters ·** Abort signals are common in React Query / TanStack Query (T08). If an aborted request incorrectly clears state needed by non-aborted waiters, the boundary could drop re-sends or double-renew.  
**Suggested test ·** Start two parallel requests that both 401. Abort one immediately after it enters the renewal wait. Resolve the renewal with the new token. Assert the non-aborted request re-sends with the new token and resolves 200; the aborted request rejects with `AbortError`; `renew` was called exactly once.

### 3. No test for a single 401 (N = 1)
**Gap ·** Every 401 test uses multiple concurrent requests (cases 1, 4, 13, 14). The simplest case — one call gets 401, one renewal runs, one re-send happens — is not explicitly covered.  
**Why it matters ·** Single-flight logic is easiest to reason about and catches off-by-one errors in renewal counting. A bug that only manifests with one request would be missed.  
**Suggested test ·** Add a case: one `GET /accounts/me` returns 401, `renew` resolves with `'new'`, the transport sees exactly two calls (`Bearer old`, then `Bearer new`), and the result is 200. `renew` ×1, hook ×0.

### 4. AC6 privacy checks only run on the success path
**Gap ·** Case 1 verifies local/session storage are empty, `location.href` is unchanged, and no console call contains the token after a successful renewal. These privacy assertions are not repeated for the failed-renewal path (case 4) or the timeout path (case 6).  
**Why it matters ·** A failed renewal is exactly when a bug might log the rejection reason (which could contain a token) or write fallback state to storage.  
**Suggested test ·** Extend cases 4 and 6 to assert the same privacy invariants as case 1: storage is empty, URL unchanged, no console call contains either token.

### 5. No explicit test that `tokens.renew()` synchronous throw becomes a single failure
**Gap ·** The implementation wraps `tokens.renew()` in `Promise.resolve().then(...)` so a synchronous throw becomes a rejection. The self-review checklist notes this is covered, but no test case explicitly targets it.  
**Why it matters ·** A regression that removes the `Promise.resolve().then` wrapper would make a throwing `renew()` crash the boundary instead of failing once.  
**Suggested test ·** Add a case where `fakeSession.renew` is `vi.fn(() => { throw new Error('sync'); })`. Assert `renew` is called once, the hook is called once with the error, and the waiter gets 401.

### 6. No test that the re-sent request truly bypasses middleware
**Gap ·** The manifest notes that the recursion mutation break was not run because openapi-fetch exposes no faithful one-line mutation. The argument is indirect: transport-call counts would expose recursion. There is no direct assertion that the re-sent request is not re-processed by `onRequest`/`onResponse`.  
**Why it matters ·** If a future change accidentally routes the re-send back through the client (not just `options.fetch`), the test suite should catch it explicitly, not just via count arithmetic.  
**Suggested test ·** Add a spy/marker: the test's transport returns a response with a custom header `x-resend: true` only when the request's `Authorization` header is `Bearer new`. Assert the final response has that header. This is weak; a stronger test would monkey-patch the middleware to throw if `onRequest` is called with `Authorization: Bearer new` and assert the call resolves 200 anyway. (Because the re-send bypasses the middleware, the throw is not hit.)

### 7. No test for `onRequest` returning `undefined` for public operations
**Gap ·** Case 15 verifies public operations get no bearer and no renewal on 401, but it does not assert that `onRequest` returns `undefined` for them. Returning a modified request (even with the bearer deleted) would still pass the transport check.  
**Why it matters ·** The brief specifies that public operations return `undefined` from `onRequest`. A regression that returns the request object could interact with later middlewares or openapi-fetch internals.  
**Suggested test ·** Add a unit-level assertion on the middleware's `onRequest` return value for a public operation, or add a transport assertion that the exact same `Request` instance passed in is the one sent out (not a clone or modified copy).

### 8. No test for two sequential successful renewals
**Gap ·** Case 3 has two renewals, but the second is triggered by a later wave after the first renewal settled. There is no case where the server rejects the new token immediately and the boundary must do a second successful renewal.  
**Why it matters ·** AC3 says "a fresh 401 wave with the current token triggers exactly one new renewal." A fresh 401 *on the re-send itself* should also be final (case 7), but a separate fresh wave should renew again. The boundary between these two scenarios should be explicit.  
**Suggested test ·** Add a case: first wave 401 → renew → 200. Then second wave 401 with the now-current token → second renew → 200. Assert `renew` ×2, no hook calls.

### 9. No test that `RenewalTimeoutError` carries no token
**Gap ·** Case 6 asserts the hook receives `RenewalTimeoutError`, but it does not assert the error message contains no token.  
**Why it matters ·** AC6 / L3 forbid tokens in logs and errors. A timeout message accidentally interpolated into the token would leak it.  
**Suggested test ·** Extend case 6 to assert `hookReason.message` does not contain either token value.

### 10. No test for `onResponse` with a missing pending entry
**Gap ·** The middleware deletes the pending entry on error and returns responses unchanged when no entry exists. There is no case that exercises a 401 response without a corresponding `onRequest` record.  
**Why it matters ·** A race or implementation bug could cause `onResponse` to run without `onRequest`. The fallback should return the response unchanged and not throw.  
**Suggested test ·** Optional: directly call `middleware.onResponse` with a 401 response and no prior `onRequest`. Assert it returns the response unchanged and `renew` is not called.

---

## Non-gaps (acknowledged, no change needed)

- **17 cases cover all AC1–AC7.** The suite is already comprehensive.
- **12 mutation breaks all caught.** Strong protection against regressions.
- **Public operations parsed from `auth.yaml`.** Good test of AC7.
- **Uncloneable body fallback tested.** Case 17.
- **Hook error surfacing tested.** Case 8.
- **Cross-boundary contrast tested.** Case 2.
- **Privacy checks on success.** Case 1.

---

## Summary

The Phase 10 test manifest is excellent: it covers the core single-flight behavior, late 401s, failed renewals, timeouts, public operations, privacy invariants, and mutation breaks. The main gaps are the untested `onError` path, abort handling, the N=1 base case, privacy checks on failure, and a few direct assertions (return value for public ops, re-send bypass, timeout error sanitization). Adding these would make the suite a complete gate for the boundary's edge cases.
