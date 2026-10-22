# frontend · T05 · Phase 7 — Self-Review

Reviews the phase 6 diff (`429e5c7d`): `frontend/src/api/sessionBoundary.ts` and `frontend/src/api/client.ts`, against
the frozen brief and `spec/frontend/agents.md`. Findings only; fixes are applied in phase 9.

## Findings

### 1. A request that waited on a failed renewal starts a second renewal straight away
- **Issue:** this traces the code with a renewal R1 that fails.
  - Request A's 401 starts R1.
  - Request B starts while R1 is in flight, so `onRequest` awaits it (`sessionBoundary.ts:90`). R1 fails and the hook
    runs.
  - B is then sent with the unchanged old token and gets 401.
  - Because `tokenUsed === current()` (`:109`), B starts R2 immediately, and the hook runs a second time.
  - So one burst of activity produces two back-to-back renewals and two failure reports. That is close to the "error
    loop" R21 forbids, and it breaks AC4 ("no further renewals").
- **Severity:** Medium.
- **Evidence:** `frontend/src/api/sessionBoundary.ts:90` discards the awaited outcome (`.catch(() => undefined)`).
  `:109` then treats B like a fresh 401.
  **Confirmed by a scratch probe**, deleted after the run: the transport always returns 401, the first `renew()` is held
  and then rejected, B is started while it is pending, and later `renew()` calls reject. The result was
  **`renewals 2 hooks 2`**.
- **Recommendation:** in `onRequest`, remember whether the awaited renewal failed (`waitedOnFailedRenewal`). In
  `onResponse`, a 401 on such a request returns the original response with no renewal. Add a test: B starts mid-renewal,
  the renewal fails, and B's 401 leads to no second `renew()` and no second hook call.

### 2. After a failed renewal, every later wave renews again
- **Issue:** once a renewal fails, the token is unchanged. Each later wave of 401s, such as a screen's retry or a query
  refetch, starts one new renewal and one new hook call. Each wave obeys "exactly one", but a polling caller would
  renew on every poll until something leaves the protected screen.
- **Severity:** Low.
- **Evidence:** `sessionBoundary.ts:63-75`: nothing latches the failure.
- **Recommendation:** no T05 change. R22 / task 33 owns the transition: the hook routes to clean re-auth and clears the
  session, after which protected calls stop. A boundary-level latch would duplicate that state, and could strand a
  session that SAS still accepts. Record it as the contract the hook must honour.

### 3. The re-send omits openapi-fetch's second transport argument
- **Issue:** openapi-fetch calls its transport as `fetch(request, requestInitExt)` (`dist/index.mjs:123`). The re-send
  calls `clientOptions.fetch(entry.clone)` with one argument. `requestInitExt` carries only non-standard init passed to
  `createClient`, and `createAuthClient()` passes none.
- **Severity:** Low.
- **Evidence:** `sessionBoundary.ts:118`.
- **Recommendation:** no change today, because no client passes extensions. If a later client does, the boundary must
  forward them; note it for the payments/notifications client tasks.

### 4. A failed re-send bypasses `onError`
- **Issue:** if the re-send's transport rejects (a network drop), the rejection leaves `onResponse` and the call
  rejects. openapi-fetch's `onError` loop wraps only the first send. The entry was already deleted, so nothing leaks.
- **Severity:** Info.
- **Evidence:** `sessionBoundary.ts:104-105` (the delete) and `:118`.
- **Recommendation:** none. The caller sees the network error, which task-level network-failure states handle (out of
  scope).

### 5. The returned re-send response skips other middlewares' `onResponse`
- **Issue:** this is frozen finding 6, documented. There is only one middleware today.
- **Severity:** Info.
- **Evidence:** `client.ts`: a single `use()`.
- **Recommendation:** none for T05. Tasks 7–8 put 429 handling inside the boundary, as the frozen brief says.

### 6. Every protected request body is cloned
- **Issue:** `request.clone()` tees the body for every protected request, including requests that will never get 401.
  MVP bodies are small JSON, and GETs have no body.
- **Severity:** Info.
- **Evidence:** `sessionBoundary.ts:95`.
- **Recommendation:** none. The clone is the only way to re-send a consumed body.

## Checklist (no finding)
- **Correctness:**
  - the shared promise is set before it is returned, so a second caller in the same tick joins it;
  - `inFlight` is cleared in `finally`;
  - the timer is cleared on settle;
  - a late `renew()` result is swallowed;
  - a synchronous throw from `tokens.renew()` becomes a rejection (`Promise.resolve().then`).
- **Boundary conditions:**
  - a null token deletes `Authorization`, so a clone that still carries the old header can't leak it;
  - a missing entry (no `onRequest` record) returns the response unchanged;
  - `schemaPath` is the template path, so public keys match parameterised paths too.
- **Null-safety:** `entry` and `clone` are null-checked.
- **Concurrency:** JS is single-threaded. The only interleavings are at `await` points, traced in finding 1.
- **Module boundaries:**
  - no module-level mutable state;
  - `libs/**` is untouched;
  - the type-only import from `@themistra/api-client`;
  - no global `fetch`, and lint is clean.
- **Secrets:**
  - the token appears only in the `Authorization` header;
  - `RenewalTimeoutError`'s message holds the timeout only;
  - nothing is logged or stored;
  - no URL is touched.
- **Not applicable:** transactions, idempotency keys, money types, enumeration-safety.
- **Readability:** two small closures, with comments where the reason isn't obvious from the code (the timeout swallow,
  the scheduled hook, the token comparison).
