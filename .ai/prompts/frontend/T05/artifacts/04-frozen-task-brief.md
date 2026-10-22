# frontend · T05 · Phase 4 — Frozen Task Brief (Human Approval gate)

STATUS: FROZEN (2026-10-22)

Consumes `02-task-implementation-brief.md` and `03-design-challenge.md` (Kimi 2.7: 10 findings; 1 High, 3 Medium,
6 Low). Each finding was checked against the spec, the contract and the running local stack before it was
dispositioned. None needs an owner trade-off, so they are settled here, as at the T01–T04 gates.

## What the checks showed

- **Public endpoints reject a bad bearer (finding 2 is real).** Through the edge, with a `{}` body:
  - with no `Authorization`, `POST /accounts`, `/accounts/verify-email`, `/accounts/resend-verification`,
    `/accounts/password-reset-request` and `/accounts/password-reset` each return `400` (validation);
  - with `Authorization: Bearer abc.def.ghi`, every one returns `401`;
  - Spring's resource server authenticates any bearer that is present, even on a `permitAll` route.
- **The contract marks exactly those 5 operations public** with an explicit `security: []`. There is no top-level
  `security` block. An operation either declares `bearerAuth`/`apiKeyAuth` or says nothing; the live stack returns
  `401 WWW-Authenticate: Bearer` without a token for both kinds (Phase 0).
- **Renewal needs no old token (finding 5's premise is false).**
  - L3 and `agents.md`: renewal is silent re-auth through `/oauth2/authorize` on the SAS httpOnly session cookie.
  - After a page reload, the in-memory token is `null` but the SAS session is still valid. That case is exactly when
    renewal *does* succeed.
- **A side note from probing,** not a T05 item and left alone per the open-items rule: auth-service answers a malformed
  `Content-Type` with `500` rather than `415`. A first probe hit this through a shell-quoting mistake, which was
  corrected and re-run before any conclusion.

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| 1 | Per-client state breaks "one renewal per tab" | **ACCEPTED.** The renewal state moves to a **session boundary** created once per tab: `createSessionBoundary(tokens, …)`. It owns the single in-flight renewal. Each client gets its own middleware from `boundary.middleware(publicOperations)`; the per-request map is per client, but the renewal is shared. A test proves two clients on one boundary trigger one renewal. Task 6 extends the same boundary across tabs. |
| 2 | A bearer is sent to public endpoints | **ACCEPTED** (confirmed live: a bad bearer turns 400 into 401). Each client passes its contract's public operations, as `"<METHOD> <schemaPath>"` keys. For these the middleware **sends no `Authorization`** and **never renews or re-sends**. `AUTH_PUBLIC_OPERATIONS` holds the 5 `security: []` operations of `auth.yaml`, and a test asserts it equals the contract's set, parsed from the YAML text. |
| 3 | Uncloneable bodies | **ACCEPTED (fallback).** If `request.clone()` throws, the request is recorded with no clone. On its 401 the request still joins or starts the renewal, so the session recovers, but its original 401 is returned, not re-sent. The MVP's bodies are JSON. |
| 4 | A hanging `renew()` | **ACCEPTED.** The boundary bounds each renewal by `RENEWAL_TIMEOUT_MS = 30_000`; tests can override it through an option. On timeout, the renewal counts as failed: waiters get their 401, and the failure hook runs once. A late result from the timed-out `renew()` is ignored. |
| 5 | A null-token 401 shouldn't renew | **REJECTED (premise false).** Renewal is silent re-auth on the SAS cookie and needs no prior token, and the reload case relies on it. A 401 on a **non-public** operation renews whatever `tokenUsed` was. |
| 6 | The re-send bypasses all middleware | **ACCEPTED (documented).** The re-send goes straight to `options.fetch`, with no other middleware. A future middleware that must see re-sends (such as task 7's 429) must be applied by the boundary itself, not as a separate `use()`. Tasks 7–8 own that. |
| 7 | Multiple boundary instances | **ACCEPTED (documented).** Production creates exactly one boundary per tab, in the auth wiring task. Tests create their own. |
| 8 | `onRenewalFailed` sync and throwing | **ACCEPTED.** The hook is `(reason: unknown) => void` and is called via `queueMicrotask`. A throwing hook can't break the waiters, and its error surfaces as an uncaught error rather than being swallowed. |
| 9 | No 401-with-no-token test | **ACCEPTED, inverted per finding 5.** The test asserts that a null-token 401 on a protected operation triggers exactly one renewal and is re-sent with the new token. It also asserts that a public operation's 401 triggers none. |
| 10 | Request-id lifecycle | **ACCEPTED.** The entry is deleted in both `onResponse` (on every path) and `onError`, and `Map.delete` is idempotent. Phase 6 checks that an aborted request reaches `onError`. |

Open Questions: none. The Phase 2 defaults stand, amended as above:
1. A 401'd protected request is re-sent once after renewal.
2. Renewal is injected through `SessionTokens`.
3. Each client gets its own middleware, with the boundary shared.
4. The failure hook runs once per failed renewal.

---

## Frozen brief

### Task
T05 — **Single-flight 401.** A 401 triggers exactly one renewal attempt. Verifies
`parallel401sTriggerExactlyOneRenewal`. Cites R21.

### Purpose
This is the client boundary's 401 handling. However many calls hit 401 together, across every client in the tab, the
tab renews once, and each protected call is re-sent once with the new token. Screens never handle 401.

### Scope
**In**
- `SessionTokens` (the seam to the auth module).
- `createSessionBoundary` (single-flight renewal, timeout, failure hook).
- `boundary.middleware(publicOperations)` (bearer attachment, 401 handling, re-send).
- `createAuthApi(boundary)` and `AUTH_PUBLIC_OPERATIONS`.
- `parallel401sTriggerExactlyOneRenewal`.

**Out**
- The real silent re-auth and the token holder (`src/auth/`, tasks 14–23).
- Cross-tab sharing (task 6).
- 429 (tasks 7–8).
- The clean re-auth transition (task 33; T05 only calls the hook).
- Network-failure states.
- Other clients.
- `libs/**`.

### Business rules
- **R21, the per-tab half:** at most one renewal per tab, shared among parallel 401s, and a failure reported with no
  loop.

### Locked decisions
- **L18:** one renewal per tab.
- **L3:** memory-only token; never stored, logged or put in a URL.
- **L6:** the generated client and its own transport; no global `fetch`.
- **L9, L10, L12.**

### Dependencies
None new. `openapi-fetch@0.17.0` middleware (via `@themistra/api-client`). The middleware type is derived from the
client, with no direct `openapi-fetch` dependency.

### Files to create
**`frontend/src/api/sessionBoundary.ts`:**
- `export interface SessionTokens { current(): string | null; renew(): Promise<string> }`;
- `export const RENEWAL_TIMEOUT_MS = 30_000`;
- `export interface SessionBoundary`, with:
  - `renew(): Promise<string>`, the shared single-flight renewal;
  - `middleware(publicOperations: ReadonlySet<string>): Middleware`;
- `export function createSessionBoundary(tokens: SessionTokens, onRenewalFailed: (reason: unknown) => void, options?:
  { timeoutMs?: number }): SessionBoundary`.

**`frontend/src/api/client.ts`:**
- `export const AUTH_PUBLIC_OPERATIONS: ReadonlySet<string>`, the 5 `security: []` operations of `auth.yaml`, as
  `"POST /accounts"` and so on;
- `export function createAuthApi(boundary: SessionBoundary)`, which returns `createAuthClient()` with
  `boundary.middleware(AUTH_PUBLIC_OPERATIONS)` installed.

**`frontend/src/api/parallel401sTriggerExactlyOneRenewal.test.ts`.**

### Files to modify
None.

### Files not to modify
- `libs/**`.
- `frontend/src/app/**`, `frontend/.oxlintrc.json`, `frontend/package.json`.
- `contracts/**`, `spec/**`, `services/**`, `infra/**`.
- `prompt`.

### Behaviour (normative)
**`boundary.renew()`:**
- If a renewal is in flight, return it.
- Otherwise start `tokens.renew()`, raced against the timeout. Clear the in-flight slot when it settles.
- On rejection or timeout, schedule `onRenewalFailed(reason)` exactly once via `queueMicrotask`, and reject.

**The middleware:**
1. **`onRequest`, public operation** (`"<METHOD> <schemaPath>"` in the set): pass the request through unchanged, with no
   `Authorization`.
2. **`onRequest`, protected operation:**
   - await any in-flight renewal, ignoring its outcome;
   - set `Authorization: Bearer <current()>` if there is a token;
   - record `{ clone, tokenUsed }` under `id`; the clone is `null` if cloning throws.
3. **`onResponse`:**
   - delete the record on every path;
   - a non-401 or a public operation is returned unchanged.
4. **`onResponse`, a 401 on a protected operation:**
   - if `tokenUsed !== current()` (already renewed), skip renewal;
   - otherwise await `boundary.renew()`;
   - **on success, with a clone:** set the current token on the clone, send it once via `options.fetch`, and return
     that response. A 401 on it is final;
   - **on success, without a clone:** return the original 401;
   - **on failure:** return the original 401.
5. **`onError`:** delete the record. The error propagates.

### Acceptance criteria
- **AC1 (R21, L18):** N ≥ 2 concurrent protected 401s trigger exactly one `tokens.renew()`. This holds across two
  clients that share one boundary.
- **AC2 (R21):** each of them is re-sent once with the new token and resolves with the re-sent response. A POST body is
  intact.
- **AC3:**
  - a late 401 for an old-token request, after the renewal settled, is re-sent with no new renewal;
  - a fresh wave triggers exactly one new renewal.
- **AC4 (R21):**
  - when renewal fails or times out, every waiter gets its original 401;
  - `onRenewalFailed` runs exactly once;
  - there are no re-sends and no further renewals;
  - a re-send that gets 401 is final;
  - a throwing hook doesn't affect the waiters.
- **AC5 (R21):** 2xx, 403, 429 and 500 trigger no renewal and no re-send.
- **AC6 (L3):**
  - protected requests carry `Bearer <current()>`;
  - a request started mid-renewal waits for it and carries the new token;
  - with a null token, no header is sent, and a 401 still renews once (finding 5);
  - no token reaches local or session storage, the URL or the console.
- **AC7 (finding 2):**
  - the public operations get no `Authorization` even when a token exists, and their 401 triggers no renewal;
  - `AUTH_PUBLIC_OPERATIONS` equals the `security: []` operations of `auth.yaml`.
- **AC8 (L6):** no global `fetch`; `lint`, `typecheck` and `build` stay clean.

### Required tests: `parallel401sTriggerExactlyOneRenewal`
In `frontend/src/api/`, Vitest. The fixtures:
- a `fetch` stub and the browser-`Request` shim (T03 pattern);
- a fake `SessionTokens` whose `renew()` is a deferred promise;
- the real `openapi-fetch` client via `createAuthApi`.

Cases:
1. **AC1/AC2:** 5 concurrent calls (GET and POST) → 1 renewal → 5 re-sends with the new token; the POST body is intact.
2. **AC1 across clients:** two `createAuthApi(boundary)` clients → 1 renewal.
3. **AC3:** the late old-token 401, then a second wave.
4. **AC4:**
   - failure: all 401, the hook once, 0 re-sends;
   - a timeout (fake timers): the same, with the late result ignored;
   - a re-send that 401s is final;
   - a throwing hook.
5. **AC5:** 200, 403, 429 and 500.
6. **AC6:** the header, the mid-renewal wait, the null token, and the storage/URL/console scan.
7. **AC7:** the public calls carry no header and don't renew; the set equals the contract's `security: []` operations.
8. **The clone fallback:** with `Request.prototype.clone` throwing, renewal still runs once and the original 401 is
   returned.

Mutation breaks:
- renewal per 401;
- the in-flight slot never cleared;
- the boundary state made per client;
- the hook called per waiter;
- renewal on 403;
- a bearer sent to public operations;
- the timeout removed (the timeout case hangs, so a test timeout fails it);
- the re-send routed through the middleware.

### Constraints
- `strict` TypeScript.
- No new dependency.
- No global `fetch`; no lint directive.
- No token in storage, a URL or a log.
- No automatic 429 retry.
- No network in tests.
- No module-level mutable state: all state lives in a boundary or middleware instance.
