# frontend · T05 · Phase 2 — Task Implementation Brief

## Task
T05 — **Single-flight 401.** A 401 triggers exactly one renewal attempt. Verifies
`parallel401sTriggerExactlyOneRenewal`. Cites R21.

## Purpose
Give the client boundary (`agents.md`, "API access") its 401 handling. However many calls in a tab hit 401 together,
the tab renews the session once, and every call waits for that renewal. Screens never handle 401 themselves. Sibling
tabs (task 6), 429 handling (tasks 7–8) and the re-auth screen (task 33) build on this.

## Scope
**In**
- A `SessionTokens` interface: the seam to the future auth module.
- An `openapi-fetch` middleware that:
  - attaches the bearer token;
  - runs single-flight renewal on 401;
  - re-sends each 401'd request once.
- `createAuthApi(...)`, the boundary-wrapped auth client.
- `parallel401sTriggerExactlyOneRenewal`.

**Out**
- The real renewal (silent re-auth through `/oauth2/authorize`) and the in-memory token holder (`src/auth/`, tasks
  14–23).
- Cross-tab sharing (task 6).
- 429 handling (tasks 7–8).
- The clean re-auth transition and screen (task 33; T05 only exposes the failure hook).
- Network-failure states.
- The payments and notifications clients (they reuse the middleware when generated).
- Any change to `libs/ts/api-client`.

## Business Rules
- **R21 (per-tab half):**
  - a 401 leads to at most one renewal per tab, shared by every parallel 401;
  - on failure, the failure is reported once, with no loop.

## Locked Decisions
- **L18:** at most one renewal per tab.
- **L3:** the token is memory-only and never stored, logged or placed in a URL.
- **L6:** generated client only, so the re-send uses the client's own transport (`options.fetch` from the middleware
  params). There is no global `fetch` (the T04 lint).
- **L9:** relative paths.
- **L10:** no env.
- **L12:** the generated library is not edited.

## Dependencies
- None new.
  - `openapi-fetch@0.17.0` (via `@themistra/api-client`) provides `client.use(middleware)`.
  - `onRequest` may return a `Request`, and `onResponse` may return a replacement `Response`. Both receive a unique `id`
    and `options.fetch` (`dist/index.d.ts`).
- The middleware type is taken from the client (`Parameters<AuthClient['use']>[0]`), so `frontend` needs no direct
  `openapi-fetch` dependency (pnpm is strict).
- **Probed 2026-10-22** (a scratch Vitest file in the frontend container, then deleted):
  - three parallel calls (2 GET, 1 POST with a JSON body) all got 401 with the old token;
  - exactly 1 renewal ran;
  - all three were re-sent through `options.fetch` with the new token, and the POST body survived because a clone was
    taken in `onRequest`;
  - all three resolved 200.

## Inputs
- The client's requests.
- `SessionTokens.current()` (the token or `null`).
- `SessionTokens.renew()`, which resolves to the new token or rejects.

## Outputs
- Each call's final `Response`:
  - the re-sent response after a successful renewal;
  - the original 401 if renewal failed;
  - the re-sent 401 if the server rejects again.
- `onRenewalFailed(reason)` is called once per failed renewal.

## State Changes
The only state is in memory, per client instance:
- the in-flight renewal promise, cleared when it settles;
- a per-request map, keyed by request id, of the body clone and the token used. Each entry is removed on response or
  error.

Nothing is persisted.

## Behaviour (normative)
1. **`onRequest`:**
   - if a renewal is in flight, await it first, so new calls don't knowingly send a stale token;
   - set `Authorization: Bearer <current()>` when there is a token;
   - record `{ clone, tokenUsed }` under the request id.
2. **`onResponse`, not a 401:** drop the record and return the response unchanged. No renewal (AC5).
3. **`onResponse`, a 401:**
   - **If `tokenUsed` differs from `current()`,** a renewal already completed while this request was in flight. Re-send
     with the current token, and don't renew.
   - **Otherwise,** join the in-flight renewal or start exactly one.
   - **On success,** re-send the clone once with the new token through `options.fetch`, and return that response.
     The re-send bypasses the middleware, so it can't recurse. A 401 on the re-send is returned as is.
   - **On failure,** call `onRenewalFailed` once for that renewal, and give every waiter its original 401.
4. **`onError`:** drop the record. The error propagates unchanged.
5. **A 401 after the renewal has settled** starts a new, single renewal (AC3).

## Files to Create
- **`frontend/src/api/singleFlightRenewal.ts`:**
  - `export interface SessionTokens { current(): string | null; renew(): Promise<string> }`;
  - `export function singleFlightRenewal(tokens: SessionTokens, onRenewalFailed: (reason: unknown) => void):
    Middleware`.
- **`frontend/src/api/client.ts`:**
  - `export function createAuthApi(tokens: SessionTokens, onRenewalFailed: (reason: unknown) => void)`;
  - it returns `createAuthClient()` with the middleware installed. This is the one boundary-wrapped client that
    screens will use.
- **`frontend/src/api/parallel401sTriggerExactlyOneRenewal.test.ts`.**

## Files to Modify
None.

## Files NOT to Modify
- `libs/**` (generated and hand-written api-client).
- `frontend/src/app/**`, `frontend/.oxlintrc.json`, `frontend/package.json`.
- `contracts/**`, `spec/**`, `services/**`, `infra/**`.
- `prompt`.

## Acceptance Criteria
- **AC1 (R21, L18):** N ≥ 2 calls in flight together that all get 401 trigger exactly one `renew()`.
- **AC2 (R21):** each of them is re-sent once with the renewed token and resolves with the re-sent response.
- **AC3:**
  - a 401 arriving after the renewal settled, for a request sent with the old token, is re-sent without a second
    renewal;
  - a fresh 401 wave with the current token triggers exactly one new renewal.
- **AC4 (R21):**
  - on a failed `renew()`, every waiter resolves with its original 401;
  - `onRenewalFailed` is called exactly once;
  - there are no further renewals or re-sends;
  - a re-sent request that gets 401 again is returned with no further renewal.
- **AC5 (R21):** a 2xx response or a non-401 error (403, 429, 500) triggers no renewal and no re-send.
- **AC6 (L3):**
  - each request carries `Authorization: Bearer <current token>`;
  - a request started during a renewal waits for it and carries the new token;
  - with no token, no `Authorization` header is sent;
  - no token reaches storage, the URL or the console.
- **AC7 (L6):** the module names no global `fetch`, and `pnpm -C frontend lint` stays clean.

## Required Tests
`parallel401sTriggerExactlyOneRenewal` (Vitest, `frontend/src/api/`) uses:
- `vi.stubGlobal('fetch', …)` and the browser-`Request` shim (T03 pattern);
- a fake `SessionTokens` whose `renew()` is a deferred promise the test resolves or rejects;
- `createAuthApi(...)` against the real `openapi-fetch` client.

Cases:
1. **AC1/AC2:** 5 parallel calls (GET + POST) → 1 renewal → 5 re-sends with the new token, all 200. The POST body is
   intact.
2. **AC3:** a late 401 from the old token leads to a re-send with no renewal. Then a new wave leads to exactly one
   more renewal.
3. **AC4:** a failed renewal: all waiters get 401, `onRenewalFailed` is called once, and there are 0 re-sends. A re-send
   that gets 401 again leads to no second renewal.
4. **AC5:** 200, 403, 429 and 500 lead to no renewal.
5. **AC6:**
   - the header is correct;
   - a call started mid-renewal waits for it and uses the new token;
   - with no token, there is no header;
   - local and session storage are empty, the URL is unchanged, and no `console` call contains the token.

Mutation breaks:
- no shared promise (renew per 401);
- the in-flight promise never cleared;
- re-send through the middleware (recursion), or a re-send without a new token;
- `onRenewalFailed` called per waiter;
- renewal on 403.

## Constraints
- `strict` TypeScript.
- No new dependency.
- No global `fetch` and no lint directive.
- No token in storage, a URL or a log.
- No automatic 429 retry (R23).
- No network in tests.
- The middleware is per client instance. There is no module-level mutable state beyond what the instance closes over.

## Open Questions
No blockers. Defaults, for phase 3 and 4 review:
1. **A 401'd request is re-sent once after a successful renewal**, including a POST. A 401 is rejected at
   authentication, before the handler runs, so this is not R23's 429 duplicate-side-effect case. A second 401 is
   final.
2. **Renewal is an injected `SessionTokens`.** The auth tasks supply the real silent re-auth. T05 ships no fake
   renewal in production code.
3. **The middleware is client-agnostic.** It is applied to `createAuthClient()` now, and the payments and notifications
   clients reuse it later.
4. **Failure reporting:** one `onRenewalFailed` call per failed renewal. Waiters get their original 401. Task 33 wires
   the hook to clean re-auth.
