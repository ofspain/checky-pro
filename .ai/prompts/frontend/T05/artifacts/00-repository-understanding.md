# frontend · T05 · Phase 0 — Repository Understanding

Task 5, **Single-flight 401**: "A 401 triggers exactly one renewal attempt." Verifies
`parallel401sTriggerExactlyOneRenewal`. Cites R21. Date: 2026-10-22. Read-only: no code was written.

Read for this phase:
- `spec/frontend/`: `package.md` §8, `requirements.md` R19–R23, `design.md` §4a (L3, L6, L16, L18), §5, §6, and
  `agents.md` (token rules, "API access").
- The current `frontend/` and `libs/ts/api-client/`.
- `openapi-fetch@0.17.0`'s published types.
- The running local stack, probed through the edge at `http://localhost:5173`.

## 1. Architecture summary (frontend, as built through T04)

- **Stack:**
  - Vite 8.3.5, React 19.3.1, TypeScript 7.0.2 (`strict`), react-router 8.4.0, Vitest 5.0.5 with jsdom 30.1.3;
  - pnpm 12.14.0 workspace (`frontend`, `libs/ts/*`).
  - TanStack Query, Zustand and Workbox are confirmed by `agents.md` but not installed yet.
- **Modules (`design.md` §6):**
  - `src/app/` (T01–T02): `AppShell`, `mountShell`, `routes.tsx` (`/app` basename; 9 public and 11 authenticated
    paths; no elements yet).
  - `src/api/`: the "generated client wrapper, 401/429" (L6, L18). **It holds only tests so far.**
  - `src/auth/`: "OIDC client, session owner, guards" (L1–L3, L16, L18). **It does not exist yet.**
- **Backend access (L6, T03/T04):**
  - `@themistra/api-client` (`libs/ts/api-client/src/index.ts`) exports the generated `paths`, `components` and
    `operations` types for `auth.yaml`, plus `createAuthClient()`, which is `createClient<paths>({ baseUrl: '' })` from
    `openapi-fetch`, on same-origin relative paths.
  - oxlint (T04) bans global `fetch` and `XMLHttpRequest`, and `.fetch`/`.XMLHttpRequest` on the seven window objects,
    in all of `frontend/src`, tests included. `libs/` is not linted.
- **Persistence:** none. Tokens are memory-only (L3). sessionStorage is limited to the L16 items. There is no
  IndexedDB or cookie access from the SPA.
- **Events/outbox:** not applicable (SPA).
- **Security posture:**
  - a public OIDC client with PKCE (L1), against Spring Authorization Server in auth-service;
  - the bearer access token is kept in memory;
  - renewal is **silent re-authentication** through `/oauth2/authorize` on the SAS httpOnly session cookie. No refresh
    token is issued to `checky-spa`. This was empirically confirmed (Q5, L3; `agents.md` lines 25–34).

## 2. Existing code this task touches

| Item | Status |
|---|---|
| `libs/ts/api-client` `createAuthClient()` and the `openapi-fetch` client (`use`, `GET`/`POST`/…) | **Exists** (T03). T05 consumes it and must not edit generated code. |
| `frontend/src/api/` client-boundary wrapper (single-flight 401) | **New**. This is the planned home in `design.md` §6. |
| An access-token holder (in-memory) | **Does not exist.** Tasks 20–22 (callback; "module-scoped variable") build it under `src/auth/`. |
| The silent re-auth (renewal) implementation | **Does not exist.** Tasks 14–23 (PKCE, authorize, callback) supply the pieces, and task 33 handles renewal failure (R22). |
| Cross-tab sharing of the renewal (BroadcastChannel / Web Locks) | **Not T05.** It is task 6, `siblingTabsShareOneRenewal` (R21, L18). |
| 429 handling | **Not T05.** Tasks 7–8 (R23). |
| `frontend/.oxlintrc.json` and the T01–T04 tests | Exist. They must keep passing; the new code must lint clean. |

## 3. Established patterns to follow

- **Location and naming:** source under `frontend/src/<module>/`; one named test per task, as
  `frontend/src/<module>/<namedTest>.test.ts(x)`.
- **Backend calls:** only through `@themistra/api-client` (L6).
  - `openapi-fetch` exposes `client.use(middleware)`. Its `onRequest` may return a replacement `Request`, and its
    `onResponse` may return a replacement `Response` (`MiddlewareOnResponse`, `dist/index.d.ts`).
  - The callback params carry `request`, a unique `id` and the merged `options`, including `options.fetch`.
  - `createClient` also accepts a `fetch` option.
  - The T03 test proved that `onRequest` middleware sees every request.
- **The lint constraint (T04):** wrapper code under `frontend/src` cannot name global `fetch`.
  - Re-sending a request has to go through the client's own transport, for example the middleware's `options.fetch`
    or a new call through the client.
  - It cannot use a direct `fetch(...)` or an inline disable directive (T04 case 7 fails on any directive).
- **Config/env:** none. No env keys are read in app code (L10, T03 case 6).
- **Exact dependency versions**, at least about two weeks old. A frontend container restart is needed after a
  dependency change.
- **Errors:** RFC 9457 problem bodies per `agents.md`. But `auth.yaml` declares no error responses (T03 finding), so
  problem typing is by the standard members, at the wrapper.

## 4. Testing conventions (frontend)

- Vitest in the jsdom environment. Run in `themistra-local-frontend-1` at `/repo`: `pnpm -C frontend test`, `typecheck`,
  `lint`, `build`. The suite is currently 54/54.
- **No network in unit tests.**
  - `fetch` is stubbed with `vi.stubGlobal('fetch', …)` and restored with `vi.unstubAllGlobals()`.
  - Node's `Request` rejects relative URLs, so the T03 tests install a browser-semantics `Request` shim that resolves
    against `window.location.href`.
- **Paths** come from `expect.getState().testPath`, because jsdom gives `import.meta.url` a non-file URL.
- **Mutation breaks** are applied to backups and restored from them, never with `git checkout`.
- **Live check:** per `agents.md`, each frontend task is also checked against the Docker stack (`infra/local`).
  - Never touch `auth-postgres-1` or `auth-kafka-1`.
  - The demo account is `demo.merchant@example.com`, with TOTP via `infra/local/bin/totp`.
- Not applicable: JUnit, Testcontainers, fixed `Clock`, ArchUnit (backend conventions). The frontend equivalent of a
  fixed clock is Vitest fake timers, used only if a test needs time.

## Live facts (local stack, probed 2026-10-22)

- **A 401 from each backend family:**
  - `/accounts/me`, `/api-keys` and `/admin/accounts` (auth-service) return `401` with `WWW-Authenticate: Bearer`.
  - With a malformed bearer, the response is `Bearer error="invalid_token", …`.
  - `/api/v1/invoices`, `/api/v1/notifications` and `/api/v1/payments/x` (payment, notification) return `401` with
    `WWW-Authenticate: Bearer`.
- **The SAS session cookie is safe from API 401s.**
  - Auth-service's API 401s set a fresh `JSESSIONID` (`Path=/`, `HttpOnly`) **only when the request carries none**.
  - Sent with an existing session cookie, with or without a bad bearer, the 401 sets no cookie, so it doesn't replace
    the SAS session that silent re-auth depends on.
  - Payment and notification 401s set no cookie at all.
- **Framing:** `/oauth2/authorize` responds `302` with `X-Frame-Options: DENY`. `/app/callback` sends no frame header.

## 5. Known gaps / unknowns

1. **The renewal function's concrete implementation.** I do not know which mechanism (hidden iframe or background
   navigation) the auth tasks will choose. `agents.md` allows either. The `DENY` header on the authorize redirect may
   matter for an iframe, and that is unverified in a browser. T05 therefore needs renewal as an **injected operation**,
   with the real one wired by the auth tasks.
2. **The token holder's shape.** Tasks 20–22 own it. I do not know its final API. T05 needs only "current access token"
   and "renew → new token or failure".
3. **Whether a POST that got a 401 is re-sent after renewal.** R21 says "at most one renewal" and R23 forbids automatic
   replay of non-idempotent POSTs, but only for **429**. The spec doesn't say whether a request rejected with 401 is
   retried once after a successful renewal. Phase 1/2 must settle this.
4. **What "on failure, route to clean re-auth" (R21) means for T05.** The re-auth transition and its "no half-session"
   check are task 33 (R22). T05's share is presumably to report the failure once, with no retry loop. To be fixed in
   Phase 1.
5. **Scope of clients.** Only `auth.yaml` is generated today; payments and notifications clients come in later tasks.
   I do not know whether T05 should wrap only `createAuthClient` or expose a generic middleware for any
   `openapi-fetch` client.
