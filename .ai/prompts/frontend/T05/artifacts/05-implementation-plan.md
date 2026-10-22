# frontend · T05 · Phase 5 — Implementation Plan

Consumes `04-frozen-task-brief.md` (FROZEN 2026-10-22). No code is written in this phase.

## Files to create
| File | Traces to |
|---|---|
| `frontend/src/api/sessionBoundary.ts` | Frozen brief, Files to create |
| `frontend/src/api/client.ts` | Frozen brief, Files to create |
| `frontend/src/api/parallel401sTriggerExactlyOneRenewal.test.ts` | Frozen brief, Required tests (written in phase 10, per the T03/T04 precedent) |

## Files to modify
None.

## Public API (signatures)

`sessionBoundary.ts`:
```ts
export type Middleware = Parameters<ReturnType<typeof createAuthClient>['use']>[0];
export interface SessionTokens { current(): string | null; renew(): Promise<string>; }
export const RENEWAL_TIMEOUT_MS = 30_000;
export interface SessionBoundary {
  renew(): Promise<string>;
  middleware(publicOperations: ReadonlySet<string>): Middleware;
}
export function createSessionBoundary(
  tokens: SessionTokens,
  onRenewalFailed: (reason: unknown) => void,
  options?: { timeoutMs?: number },
): SessionBoundary;
```

`client.ts`:
```ts
export const AUTH_PUBLIC_OPERATIONS: ReadonlySet<string>; // 5 keys, "POST /accounts" …
export type AuthApi = ReturnType<typeof createAuthClient>;
export function createAuthApi(boundary: SessionBoundary): AuthApi;
```

`Middleware` comes from `createAuthClient`'s `use` parameter, a type-only import from `@themistra/api-client`. If the
union of middleware shapes makes the derived type awkward (it's a union of three "at least one hook" objects), the
returned object literal satisfies it directly, because it defines all three hooks.

## Private helpers (inside `createSessionBoundary`'s closure)
- `let inFlight: Promise<string> | null`. It is the only mutable boundary state.
- `function withTimeout(attempt: Promise<string>): Promise<string>`:
  - `Promise.race` against a `setTimeout` reject, using `RenewalTimeoutError` (a local `Error` subclass, not
    exported);
  - the timer is cleared in `finally`;
  - `attempt.catch(() => {})` is attached, so a late rejection after the timeout is not an unhandled rejection.
- `function startRenewal()`:
  - `inFlight = withTimeout(Promise.resolve().then(() => tokens.renew()))`; the `then` wrapping turns a synchronous
    throw into a rejection;
  - `.catch(reason => { queueMicrotask(() => onRenewalFailed(reason)); throw reason; })`;
  - `.finally(() => { inFlight = null; })`.
  - It returns the promise.
- `renew()` returns `inFlight ?? startRenewal()`.

Inside each `middleware(...)` closure:
- `const pending = new Map<string, { clone: Request | null; tokenUsed: string | null }>()`.
- `const key = (request: Request, schemaPath: string) => \`${request.method} ${schemaPath}\``.
- `function bearer(request: Request, token: string | null)`: sets or deletes `Authorization`. It deletes because a
  clone carries the old header.
- **`onRequest({ request, schemaPath, id })`:**
  - public: return `undefined` (unchanged);
  - otherwise: `if (inFlight) await inFlight.catch(() => undefined)`; take `token = tokens.current()`;
    `bearer(request, token)`; try `request.clone()`, falling back to `null`;
  - `pending.set(id, …)`; return `request`.
- **`onResponse({ request, response, schemaPath, id, options })`:**
  - `entry = pending.get(id)`; `pending.delete(id)`;
  - return `response` if it is public, not a 401, or there is no entry;
  - if `entry.tokenUsed === tokens.current()`, `try { await renew() } catch { return response }`;
  - if `!entry.clone`, return `response`;
  - `bearer(entry.clone, tokens.current())`; return `options.fetch(entry.clone)`.
- **`onError({ id })`:** `pending.delete(id)`; return `undefined`, so the error propagates.

Phase 6 verifies that `options.fetch` exists on `MergedOptions` in 0.17.0. The phase 2 probe used it successfully.

`client.ts`: the 5 keys are listed literally, then `createAuthClient()`, `client.use(boundary.middleware(...))`, and
return the client.

## Entities / repositories / services
None (SPA).

## Tests (phase 10, one file, cases 1–8 per the frozen brief)
Shared fixtures:
- the browser-`Request` shim;
- a `fetch` stub driven by a per-test responder `(req) => status`, recording `{ method, path, auth, body }`;
- `deferred<T>()`;
- `fakeTokens(initial)`, whose `renew` is a `vi.fn` returning a test-controlled deferred, and which sets `current` on
  resolve;
- `afterEach`: `vi.unstubAllGlobals()` and `vi.useRealTimers()`.

The contract-set check parses `contracts/api/auth.yaml` text with the same awk-equivalent logic as phase 4: the path
line, then the method line, then `security: []`. The path comes from `expect.getState().testPath`.

## Execution order
1. `sessionBoundary.ts`.
2. `client.ts`.
3. `typecheck`, `lint` and `build`, plus a scratch probe of:
   - `options.fetch` presence;
   - an aborted request reaching `onError` (frozen finding 10);
   - the clone fallback path.

   The scratch file is deleted afterwards.
4. The phase 6 notes.
5. In phase 10: the test file, the full suite and the mutation breaks (backups, then restore).
