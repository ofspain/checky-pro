# frontend · T05 · Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T05 — Single-flight 401**. Findings are against the brief itself; no code was changed.

---

## Findings

### 1. Per-client-instance state conflicts with R21's "per tab" requirement
**Issue:** The brief puts the renewal promise and per-request map inside a middleware instance created per `createAuthApi(...)` call. R21 requires "at most one renewal per tab." When T05's payments and notifications clients later reuse the same middleware, each client will instantiate its own middleware and therefore its own renewal promise. Two clients getting 401 in the same tab would run two renewals in parallel, violating R21.  
**Severity:** High — architectural conflict with a locked requirement.  
**Evidence ·** Brief §State Changes: "The only state is in memory, per client instance." Brief §Behaviour normative step 3: "join the in-flight renewal or start exactly one." Brief §Open Questions: "The middleware is client-agnostic. It is applied to `createAuthClient()` now, and the payments and notifications clients reuse it later." `agents.md` / R21: "at most one renewal per tab."  
**Recommended brief amendment:** Make the renewal state tab-scoped from T05 onward. Options: (a) export a `createRenewalBoundary()` factory that returns both the middleware and the wrapped client, and ensure all service clients share one boundary instance; or (b) move the in-flight renewal promise to a module-level singleton in `frontend/src/api/renewal.ts` that all middleware instances consult, while keeping per-request maps per instance. Document that T06 will add cross-tab sharing on top of this singleton.

### 2. Public endpoints receive an `Authorization` header whenever a token exists
**Issue:** The middleware attaches `Authorization: Bearer <current()>` to every request when a token exists. `auth.yaml` includes public endpoints such as `POST /accounts` (registration) and `POST /accounts/resend-verification` that do not require a token. Sending a bearer token to a public endpoint is unnecessary and could cause the server to reject a valid unauthenticated request if the token happens to be invalid or expired.  
**Severity:** Medium — functional/semantic issue that may surface in T43–T51.  
**Evidence ·** Brief §Behaviour step 1: "set `Authorization: Bearer <current()>` when there is a token." `contracts/api/auth.yaml` has unauthenticated `POST /accounts`.  
**Recommended brief amendment:** Either (a) state that the auth service ignores bearer tokens on public endpoints and no opt-out is needed; or (b) allow callers to pass an `unauthenticated: true` option per request (e.g., via openapi-fetch's `init` or a custom header) that suppresses the bearer header. If option (b) is chosen, add it now so T43–T51 can use it.

### 3. No guidance on uncloneable or streaming request bodies
**Issue:** The middleware clones the request body in `onRequest` so the re-send can reuse it. `Request.clone()` throws if the body is already consumed and can have issues with certain stream types or very large bodies. The brief assumes cloning always works.  
**Severity:** Medium — could cause runtime errors on non-JSON bodies or large uploads.  
**Evidence ·** Brief §Dependencies: "the POST body survived because a clone was taken in `onRequest`." Brief §Behaviour step 1: "record `{ clone, tokenUsed }` under the request id."  
**Recommended brief amendment:** Document the assumption that all request bodies are cloneable. For MVP scope, JSON bodies are sufficient. If future tasks (e.g., file evidence upload in Phase 2) need streaming bodies, add a fallback that skips renewal/re-send for uncloneable bodies and returns the 401 directly.

### 4. A hanging `renew()` leaves all waiters stuck forever
**Issue:** If `SessionTokens.renew()` never resolves or rejects, every request that hit 401 and every request started during the renewal will await it indefinitely. There is no timeout or abort mechanism.  
**Severity:** Medium — a stalled auth service or bug in the auth module would freeze the client boundary.  
**Evidence ·** Brief §Inputs: `SessionTokens.renew()` "resolves to the new token or rejects." Brief §Behaviour: waiters await the renewal promise.  
**Recommended brief amendment:** Either (a) state that `renew()` is required to settle and a hang is the auth module's responsibility; or (b) add an optional `renewalTimeoutMs` parameter to `createAuthApi` (default e.g., 30 s) after which the renewal promise rejects, waiters get their original 401, and `onRenewalFailed` is called once.

### 5. A 401 with no current token still triggers a renewal
**Issue:** If `current()` returns `null` when a 401 occurs, the middleware starts a renewal. But if there was never a token, there is nothing to renew; the renewal will almost certainly fail. This wastes one `renew()` call and one `onRenewalFailed` notification.  
**Severity:** Low — unlikely to loop, but noisy and semantically wrong.  
**Evidence ·** Brief §Behaviour step 3: "Otherwise, join the in-flight renewal or start exactly one." It does not check whether a token existed when the 401'd request was sent.  
**Recommended brief amendment:** In the 401 handler, if `tokenUsed` was `null`, return the 401 immediately without renewal. Only requests that were sent with a token can trigger renewal.

### 6. The re-sent request bypasses the middleware but still uses `options.fetch`
**Issue:** The brief says re-sends bypass the middleware to avoid recursion. It uses `options.fetch` directly. This is correct, but it means the re-sent request does not go through `onRequest` again, so any other middleware logic (e.g., logging, header injection added later) is skipped on re-send.  
**Severity:** Low — acceptable for T05, but a hidden constraint for future middleware.  
**Evidence ·** Brief §Behaviour step 3: "re-send the clone once with the new token through `options.fetch`, and return that response. The re-send bypasses the middleware."  
**Recommended brief amendment:** Document that the re-send bypasses all middleware. If future tasks add non-auth middleware, they must either be idempotent or be applied manually during the re-send.

### 7. No rule for concurrent `renew()` calls from separate client instances
**Issue:** Related to finding 1. Even within the auth client alone, if a screen creates two `createAuthApi(...)` instances (e.g., one for auth, one imported from a different module path), they will not share renewals.  
**Severity:** Low — the brief intends one boundary-wrapped client, but the API allows multiple.  
**Evidence ·** Brief §Files to Create: `createAuthApi(...)` returns a client with middleware installed. No singleton enforcement.  
**Recommended brief amendment:** Export `createAuthApi` as a factory but document that production code should import and reuse a single boundary instance (created in `src/api/client.ts`). For tests, multiple instances are fine because they receive independent fake `SessionTokens`.

### 8. `onRenewalFailed` signature does not allow async cleanup
**Issue:** `onRenewalFailed: (reason: unknown) => void` is synchronous. Task 33 may need to navigate to a re-auth screen, which in React is typically a state update or navigation side effect (synchronous dispatch is fine, but async redirects are not awaited).  
**Severity:** Low — probably sufficient, but worth considering.  
**Evidence ·** Brief §Outputs: "`onRenewalFailed(reason)` is called once per failed renewal." Brief §Files to Create: `onRenewalFailed: (reason: unknown) => void`.  
**Recommended brief amendment:** Keep the signature as `void`, but document that the callback must not throw and should schedule any navigation/state change synchronously. If async cleanup is needed later, the signature can be widened without breaking callers.

### 9. No test for a request that starts before any token exists and gets 401
**Issue:** The required tests cover no-token calls only in AC6 (no header is sent). They do not cover the 401 handling path when there is no token.  
**Severity:** Low — overlaps with finding 5.  
**Evidence ·** Brief §Required Tests AC5: "200, 403, 429 and 500 lead to no renewal." No 401-with-no-token case.  
**Recommended brief amendment:** Add a test case: a call made when `current()` returns `null` receives 401, `renew()` is not called, and the response is returned as-is.

### 10. Request id lifecycle is not fully specified
**Issue:** The per-request map records body clones and tokens, and removes entries "on response or error." It is unclear what happens if `onResponse` or `onError` is invoked multiple times for the same id, or if a request is aborted before either handler runs.  
**Severity:** Low — edge case.  
**Evidence ·** Brief §State Changes: "a per-request map, keyed by request id, of the body clone and the token used. Each entry is removed on response or error."  
**Recommended brief amendment:** Specify that the map entry is removed in a `finally` block (or both `onResponse` and `onError`) and that duplicate invocations for the same id are no-ops.

---

## Open Questions

- How is cross-client-instance renewal sharing resolved? Finding 1 is the only architectural blocker; the rest are refinements.
