# frontend · T05 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T05 — Single-flight 401 |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/src/api/sessionBoundary.ts`, `frontend/src/api/client.ts`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **Single-flight 401.** A 401 triggers exactly one renewal attempt. Verifies `parallel401sTriggerExactlyOneRenewal`. Cites R21.

---

## Review scope

Reviewed the Phase 6 T05 implementation against the frozen brief and `spec/frontend/agents.md`. No code was rewritten.

---

## Findings

### 1. A request that waited on a failed renewal starts a second renewal
**Issue ·** In `onRequest`, when a renewal is in flight the middleware awaits it with `.catch(() => undefined)` and discards the failure. If the request then gets 401 with the same (still-old) token, `entry.tokenUsed === tokens.current()` is true, so it calls `renew()` again. This produces two `renew()` calls and two `onRenewalFailed` calls for one burst of activity, violating AC4 (“no further renewals”) and R21's spirit. The self-review confirmed this with a probe (`renewals 2 hooks 2`).  
**Evidence ·** `frontend/src/api/sessionBoundary.ts:90` (`await inFlight.catch(() => undefined)`); `:109` (`if (entry.tokenUsed === tokens.current())`).  
**Recommendation ·** Track whether the request awaited a failed renewal (e.g., `waitedOnFailedRenewal`). In `onResponse`, if that flag is set and the response is 401, return the original response without calling `renew()`. Add the test case recommended in the self-review.  
**Confidence ·** High — confirmed bug.

### 2. A 401 with no token still triggers a renewal
**Issue ·** If a protected request is sent when `tokens.current()` is `null` and the server returns 401, the middleware enters the renewal path because `entry.tokenUsed === tokens.current()` (both `null`). There is no token to renew, so the renewal will almost certainly fail and report a failure.  
**Evidence ·** `frontend/src/api/sessionBoundary.ts:91` (`const tokenUsed = tokens.current()`); `:109` (the equality check with `null`).  
**Recommendation ·** In `onResponse`, if `entry.tokenUsed === null`, return the original 401 immediately without renewal. Only requests that were actually sent with a token can be renewed.  
**Confidence ·** Medium — semantic defect, not a loop.

### 3. Multiple `SessionBoundary` instances can exist in one tab
**Issue ·** `createSessionBoundary` returns a boundary with its own `inFlight` promise. `createAuthApi(boundary)` accepts any boundary. If two boundaries are created in the same tab (e.g., one for auth, one for payments before a shared boundary is wired), each runs its own renewal on 401. R21 requires at most one renewal per tab.  
**Evidence ·** `frontend/src/api/sessionBoundary.ts:51` (`let inFlight: Promise<string> | null = null;` is local to the factory closure). `frontend/src/api/client.ts:16` (`createAuthApi(boundary: SessionBoundary)`).  
**Recommendation ·** Document that production code must create exactly one `SessionBoundary` per tab and share it across all `createAuthApi` calls. Better: export a singleton boundary factory or a `createTabBoundary()` that throws if called twice. For T05, at least add a code comment and a test that two clients sharing one boundary run one renewal, while two clients with separate boundaries run two.  
**Confidence ·** Medium — architectural risk for later service clients.

### 4. A renewal that times out may still complete later and race with a new renewal
**Issue ·** `withTimeout` races the real `tokens.renew()` against a timer. If the timer wins, waiters get `RenewalTimeoutError`, `inFlight` is cleared, and a subsequent 401 starts a new renewal. The original `attempt` promise is still running and may complete later, updating `tokens.current()`. For a moment two renewals are in flight, only one tracked.  
**Evidence ·** `frontend/src/api/sessionBoundary.ts:53-61` (`withTimeout`); `:55` (`attempt.catch(() => undefined)`).  
**Recommendation ·** When the timeout wins, store a flag or wrap the original attempt so that if it later resolves, it still updates state but does not interfere with the new renewal. The simplest fix is to abort the original attempt on timeout if `tokens.renew()` supports cancellation; otherwise document that a late renewal result is ignored and the next 401 may start a redundant renewal.  
**Confidence ·** Low — edge case, but observable under slow auth.

### 5. `onRenewalFailed` errors surface as unhandled rejections
**Issue ·** `onRenewalFailed` is invoked via `queueMicrotask`. If the callback throws, there is no catch, so the error becomes an unhandled rejection. In a test this fails the suite; in production it may crash the app depending on the environment.  
**Evidence ·** `frontend/src/api/sessionBoundary.ts:68` (`queueMicrotask(() => onRenewalFailed(reason));`).  
**Recommendation ·** Wrap the hook call in a try/catch and route any thrown error to `console.error` (without the token). Document that `onRenewalFailed` must not throw.  
**Confidence ·** Low — the hook is expected to schedule navigation/state updates, not throw.

### 6. The re-send does not forward openapi-fetch's second transport argument
**Issue ·** The self-review noted this. `clientOptions.fetch(entry.clone)` is called with one argument, while openapi-fetch calls `fetch(request, requestInitExt)`. Today `createAuthClient()` passes no extensions, so this is harmless.  
**Evidence ·** `frontend/src/api/sessionBoundary.ts:118`; self-review finding 3.  
**Recommendation ·** No T05 change. Record it as a future constraint for payments/notifications clients that might pass non-standard init to `createClient`.  
**Confidence ·** Low — latent issue, not active today.

### 7. The re-sent request bypasses `onError`
**Issue ·** The self-review noted this. If `clientOptions.fetch(entry.clone)` rejects (network drop), the rejection propagates directly; openapi-fetch's `onError` hook is not invoked for the re-send. The entry was already deleted, so no state leaks.  
**Evidence ·** `frontend/src/api/sessionBoundary.ts:104-105, 118`; self-review finding 4.  
**Recommendation ·** No change for T05. Document that network errors on re-send are returned to the caller as rejected promises, and task-level network-failure UI handles them.  
**Confidence ·** Low — acceptable design choice.

### 8. The T05 named test file does not exist yet
**Issue ·** `frontend/src/api/parallel401sTriggerExactlyOneRenewal.test.ts` is part of Phase 10 and has not been written. The production code is complete, but the named test is missing.  
**Evidence ·** `glob frontend/src/api/parallel401sTriggerExactlyOneRenewal.test.ts` returns nothing.  
**Recommendation ·** Complete Phase 10 and write the required test cases, including mutation breaks and the two new cases for findings 1 and 2 above.  
**Confidence ·** High — expected workflow gap.

---

## Open Questions

- None blocking. Finding 1 is a confirmed bug and must be fixed in Phase 9. Findings 2 and 3 are semantic/architectural risks that should be addressed or documented before T05 is considered complete.

---

## Summary

The `sessionBoundary.ts` implementation is close to the frozen brief and correctly introduces a tab-scoped boundary, public-operation filtering, and a renewal timeout. The critical bug is finding 1: a request that awaits a failed renewal can trigger a second renewal, violating the “no further renewals” guarantee. A 401 with no token should also skip renewal (finding 2), and the boundary should be a singleton per tab to satisfy R21 when multiple service clients exist (finding 3). The remaining findings are edge cases or deferred concerns.
