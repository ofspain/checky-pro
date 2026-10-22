# frontend · T05 · Phase 6 — Implementation Notes

Consumes `04-frozen-task-brief.md` and `05-implementation-plan.md`. Date: 2026-10-22. All runs were in
`themistra-local-frontend-1`.

## What changed

| File | Content | Plan / AC |
|---|---|---|
| `frontend/src/api/sessionBoundary.ts` (new, 130 lines) | See below. | AC1–AC6, AC8 |
| `frontend/src/api/client.ts` (new) | `AUTH_PUBLIC_OPERATIONS` (5 keys: `POST /accounts`, `/accounts/verify-email`, `/accounts/resend-verification`, `/accounts/password-reset-request`, `/accounts/password-reset`); `type AuthApi`; `createAuthApi(boundary)` = `createAuthClient()` + `use(boundary.middleware(AUTH_PUBLIC_OPERATIONS))` | AC7 |

`sessionBoundary.ts` contains:
- **`Middleware`**, derived from `createAuthClient`'s `use` parameter through a type-only import, so there is no direct
  `openapi-fetch` dependency;
- **`SessionTokens`** and **`RENEWAL_TIMEOUT_MS = 30_000`**;
- **`SessionBoundary`**, and **`createSessionBoundary(tokens, onRenewalFailed, { timeoutMs })`**:
  - its closure holds one `inFlight` promise;
  - `renew()` joins that promise or starts one;
  - each renewal is raced against the timeout, with the timer cleared in `finally`. A late result is swallowed, so
    there is no unhandled rejection;
  - on failure, the hook is scheduled once with `queueMicrotask`, and the renewal rejects;
  - the slot is cleared in `finally`;
- **`middleware(publicOperations)`**:
  - its closure holds a per-client `pending` map;
  - `onRequest` skips public operations. For the others, it awaits any in-flight renewal, then sets or deletes the
    bearer, records `{ clone | null, tokenUsed }` and returns the request;
  - `onResponse` deletes the entry. On a protected 401, it renews only if `tokenUsed === current()`, then re-sends the
    clone with the current token via `options.fetch`. Failure, or a missing clone, returns the original 401;
  - `onError` deletes the entry.

No other file changed. No dependency was added, and `libs/**` is untouched. The test file is written in phase 10, per
the T03/T04 precedent.

## Verification

- **`typecheck`:** exit 0. The derived `Middleware` union accepts the object literal, which defines all three hooks.
- **`lint`:** 0 errors over 10 files, 2 rules. The re-send is `clientOptions.fetch(...)`, a property on the middleware's
  options object, not on a window object, so the L6 rule rightly doesn't flag it. There are no directives.
- **`build`:** exit 0. **`test`:** 4 files, 54/54 (unchanged).
- **Scratch probe** (`src/api/zzProbe.test.ts`, deleted after the run; `git status` shows only the two new modules):

| Path | Observed |
|---|---|
| Two `createAuthApi(boundary)` clients plus a public POST, all concurrent | **1** renewal. The two GETs were sent with `Bearer old`, then re-sent with `Bearer new`, and returned 200. `POST /accounts` was sent with **no** `Authorization` and was not re-sent. |
| A request with an already-aborted signal | The call rejects with `AbortError`. `openapi-fetch` 0.17.0's `dist/index.mjs` lines 121–153 confirm that a transport rejection is passed to every middleware's `onError` before it is rethrown, so the entry is deleted (frozen finding 10). |
| `Request.prototype.clone` throwing | **1** renewal still runs. The original 401 is returned. There is exactly one transport call, so no re-send. |
| `renew()` never settling, `timeoutMs: 1000`, fake timers, and a hook that throws | Both waiters resolve 401 after 1001 ms. The hook ran **once** with `RenewalTimeoutError`. Its `Error('hook boom')` surfaced as an **uncaught exception**, not inside the waiters, as the frozen finding 8 intends. |

## Deviations
None.

**A note for phase 10:** because a throwing hook's error is deliberately uncaught, Vitest reports it as an unhandled
error. The case that covers it must capture it, for example by stubbing `queueMicrotask` to run the task inside a
`try` and record the error, rather than letting it fail the run.
