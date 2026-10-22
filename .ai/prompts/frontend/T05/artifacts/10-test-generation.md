# frontend · T05 · Phase 10 — Test Manifest

Consumes `04-frozen-task-brief.md` and the resolved implementation (`09-review-resolution.md`). Date: 2026-10-22. All
runs were in `themistra-local-frontend-1`. No production code changed.

## File
`frontend/src/api/parallel401sTriggerExactlyOneRenewal.test.ts`: Vitest (jsdom),
`describe('parallel401sTriggerExactlyOneRenewal')`, 14 cases giving 17 results.

**Harness:**
- **The real stack under test:** `createAuthApi(createSessionBoundary(...))` over the real `openapi-fetch` client.
- **`installBrowserRequest()`:** the T03 shim; Node's `Request` rejects relative URLs.
- **`installTransport(respond)`:** stubs the global transport with `vi.stubGlobal('fetch', …)`, the string-keyed form
  that the T04 lint allows. It records `{ method, path, auth, body }` per request.
- **`acceptOnly(server)`:** a server that accepts exactly `Bearer <server.valid>`.
- **`fakeSession(initial)`:** `renew` is a `vi.fn` that hands the test one `deferred` per attempt. A resolved attempt
  becomes `current()`.
- **`settle()`:** one macrotask, so promise chains and the scheduled hook run.
- **`afterEach`:** real timers, `restoreAllMocks`, `unstubAllGlobals`.
- **No network:** every request hits the stub.

## Mapping

| # | Case | Verifies | Key assertions |
|---|---|---|---|
| 1 | renews once for five concurrent 401s and re-sends each once with the new token | AC1, AC2, AC6 (L3) | 5 calls in flight (4 GET across 3 paths, plus `POST /api-keys`) give `renew` ×1, 10 transport calls (5 `Bearer old`, then 5 `Bearer new`) and all 200. The re-sent POST body is exactly `{"name":"ci key"}`. No failure is reported. local/session storage are empty, `location.href` is unchanged, and no `console` call contains either token. |
| 2 | shares one renewal across every client on the boundary, and only there | AC1 (R21 "per tab"), P3-1, P8-3 | 2 clients on 1 boundary give `renew` ×1, both 200. **Contrast:** 2 boundaries give `renew` ×2. |
| 3 | re-sends a late old-token 401 without renewing, and renews once per later wave | AC3 | A held old-token 401 released after the renewal is re-sent with `Bearer new` while `renew` stays at 1. Then a 3-call wave after the server rotates gives `renew` ×2 in total, all 200. |
| 4 | on a failed renewal gives every waiter its 401, reports once and re-sends nothing | AC4 | 3 waiters all get 401. `renew` ×1, the hook is called exactly once with the rejection reason, and there are 3 transport calls (0 re-sends). |
| 5 | does not start a second renewal for a request that waited on the failed one | AC4, **P7-1/P8-1 regression** | The waiting request isn't sent until the renewal settles. After the failure: `renew` ×1, the hook ×1, and the sends are exactly `/accounts/me Bearer old` and `/api-keys Bearer old`. |
| 6 | treats a renewal that never settles as failed after the timeout, ignoring its late result | AC4, P3-4, P8-4 | `RENEWAL_TIMEOUT_MS === 30_000`. With fake `setTimeout`, nothing fails at 999 ms. At 1000 ms both calls get 401 and the hook runs ×1 with `RenewalTimeoutError`. A late resolve leads to no re-send, no new renewal and no second hook call. |
| 7 | returns a 401 on the re-send as final, with no second renewal | AC4 | The auths sent are `['Bearer old', 'Bearer new']`, the result is 401, `renew` ×1, and there is no failure report. |
| 8 | keeps a throwing failure hook away from the waiters; its error surfaces uncaught, not swallowed | AC4, P3-8; P8-5 rejected | `queueMicrotask` is wrapped to capture what the scheduled task throws. Both waiters resolve 401, and the captured error is exactly the hook's `Error`. |
| 9–12 | never renews or re-sends on a 200 / 403 / 429 / 500 (`it.each`) | AC5 | `renew` is not called, there is 1 transport call, and it carries `Bearer old`. |
| 13 | holds a request started mid-renewal until the renewal is done, then sends the new token | AC6 | While the renewal is pending, only the first request has been sent. The later one then goes out with `Bearer new`. Both 200, `renew` ×1. |
| 14 | sends no Authorization without a token, and still renews once on its 401 | AC6, P3-5/P8-2 rejection (the reload case) | The auths sent are `[null, 'Bearer restored']`, the result is 200, `renew` ×1. |
| 15 | sends the public operations without a bearer and never renews on their 401 | AC7, P3-2 | With a token present, all 5 public POSTs are sent with `Authorization` `null` and get 401. `renew` is not called. |
| 16 | lists exactly auth.yaml's security: [] operations as public | AC7 | It parses `contracts/api/auth.yaml` line by line (path line, then method line, then `security: []`). The 5 operations found equal `AUTH_PUBLIC_OPERATIONS`. |
| 17 | still renews once for an uncloneable request but returns its 401 instead of re-sending | P3-3 clone fallback | With `Request.prototype.clone` throwing: `renew` ×1, the result is 401, there is 1 transport call, and `current()` is `'new'` (the session recovered). |

AC8 (L6) is covered by the project gates rather than a case: `lint` (T04's rule over `src`, which now includes this
file), `typecheck` and `build` are all clean.

## Results
- The new file: **17/17** in about 0.7 s.
- `pnpm -C frontend test`: 5 files, **71/71** (54 existing plus 17).
- `typecheck`: exit 0.
- `lint`: 0 errors over 11 files, 2 rules.
- `build`: exit 0.

## Mutation breaks
Each was applied to a backup of `sessionBoundary.ts` or `client.ts`, run with `--testTimeout=3000`, then restored from
the backup. `git status` shows only the new test file afterwards.

| Mutation | Failed | Caught by |
|---|---|---|
| no shared promise (`if (inFlight) return inFlight` removed) | 6 | cases 1, 2, 3, 4, 6, 8 |
| in-flight slot never cleared | 1 | case 3 (the second wave reuses the settled renewal) |
| boundary state per client (each middleware gets its own boundary) | 3 | cases 2, 4, 8 |
| hook called per waiter | 4 | cases 4, 5, 6, 8 |
| renewal on 403 | 1 | case 10 (403) |
| bearer sent to public operations | 1 | case 15 |
| timeout removed | 1 | case 6 |
| the P7-1 guard removed (`waitedOnFailedRenewal`) | 1 | case 5 |
| token comparison removed (always renew) | 1 | case 3 (the late 401 renews a second time) |
| `onRequest` no longer waits for an in-flight renewal | 2 | cases 5, 13 |
| re-send keeps the old token | 6 | cases 1, 2, 3, 7, 13, 14 |
| one public operation dropped from `AUTH_PUBLIC_OPERATIONS` | 2 | cases 15, 16 |

**12 of 12 caught.**

The frozen list's "re-send routed through the middleware (recursion)" break was **not run**: openapi-fetch exposes no
way to re-enter its middleware chain for an existing `Request` without a new client call, so there was no faithful
one-line mutation. The guard is argued, not demonstrated: cases 1, 3 and 7 count the transport calls and renewals
exactly, and any re-entry would add to those counts.
