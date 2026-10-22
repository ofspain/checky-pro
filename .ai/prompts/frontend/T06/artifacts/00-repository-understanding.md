# frontend · T06 · Phase 0 — Repository Understanding

Task 6, **Shared renewal across tabs**: "Sibling tabs wait on the one renewal instead of starting their own." Verifies
`siblingTabsShareOneRenewal`. Cites R21, L18. Date: 2026-10-22. Read-only: no code was written.

Read for this phase:
- `spec/frontend/`: `requirements.md` R21/R22, `design.md` L3/L16/L18 and §5, `agents.md` (token and storage rules),
  `package.md` §8;
- the T05 code in `frontend/src/api/`;
- the Vitest environment, probed.

## 1. Architecture summary (frontend, as built through T05)

- **Stack and modules:** unchanged from T05 Phase 0. Vite, React, TS 7, react-router, Vitest with jsdom.
  - `src/app/`: the shell and routes.
  - `src/api/`: the client boundary (new in T05).
  - `src/auth/`: does not exist yet.
- **The T05 client boundary:**
  - **`sessionBoundary.ts`:**
    - `SessionTokens { current(); renew() }` is the seam to the auth module;
    - `createSessionBoundary(tokens, onRenewalFailed, { timeoutMs })` is one per tab and owns the single in-flight
      renewal, a 30 s timeout and the once-per-renewal failure hook;
    - `boundary.middleware(publicOperations)` is the openapi-fetch middleware: bearer, wait, renew once, re-send once.
  - **`client.ts`:** `createAuthApi(boundary)` and `AUTH_PUBLIC_OPERATIONS`.
  - **Its renewal is per tab only.** `tokens.renew()` is called at most once per tab per burst, but two tabs each call
    their own.
- **Persistence:** none.
  - Tokens are memory-only (L3).
  - sessionStorage holds only the L16 items. localStorage holds public preferences only (§5).
  - Nothing may persist a token, renewal state or session marker client-side.
- **Security:**
  - renewal is silent re-auth through `/oauth2/authorize` on the SAS httpOnly cookie (L3, Q5);
  - the SAS session cookie is shared by every tab of the origin, so every tab's silent re-auth resumes the same SAS
    session.

## 2. Existing code this task touches

| Item | Status |
|---|---|
| `SessionTokens` / `createSessionBoundary` (T05) | **Exists.** T06 must extend, not rewrite (L12). Its `parallel401sTriggerExactlyOneRenewal` must keep passing. |
| A cross-tab coordinator (BroadcastChannel / Web Locks) | **New.** `design.md` §6 places "single-flight renewal and cross-tab coherence" under L18. The module map lists L18 under both `src/auth/` ("session owner") and `src/api/` ("401/429"). |
| A way for one tab to take on a token renewed by another | **Doesn't exist.** `SessionTokens` has only `current()` and `renew()`. The in-memory token holder belongs to tasks 20–22. |
| Cross-tab **sign-out** propagation | **Not T06.** It is task 39 (`signOutPropagatesToAllTabs`, R22, L18), but it will share the same channel. |

## 3. Established patterns to follow

- **Extend the T05 seam.** Renewal plugs into the boundary as a `SessionTokens`, so a cross-tab layer can wrap the local
  one without changing the boundary.
- **No module-level mutable state:** state lives in instances, created once per tab.
- **No global `fetch`:** lint. BroadcastChannel and `navigator.locks` are not restricted.
- **No storage:** coordination must not write tokens, or any renewal marker, to localStorage, sessionStorage or
  IndexedDB (L3, L16, §5).
- **No logging of tokens.**
- **Exact versions; no new dependency** unless needed. Both browser APIs are built in.

## 4. Testing conventions

- Vitest with jsdom, in `themistra-local-frontend-1`. The suite is currently 77/77.
- **Probed in the test environment (2026-10-22):**
  - `globalThis.BroadcastChannel` and `window.BroadcastChannel` are functions (Node 22.23.4's implementation). Two
    instances with the same name in one process can stand in for two tabs.
  - `navigator.locks` is **undefined** (neither jsdom nor Node provides Web Locks), so a test needs a fake lock manager
    if Web Locks is used.
- T05 test helpers (`fakeSession`, `installTransport`, `deferred`) are patterns to reuse. They are local to that test
  file, not exported.
- Mutation breaks use backups and are restored from them.

## 5. Known gaps / unknowns

1. **How a waiting tab gets the result.** "Wait on the one renewal" means a sibling tab must end up with a valid token
   without its own `/oauth2/authorize` round trip, so the renewed access token, or the failure, has to cross to the
   sibling tab. L18 names BroadcastChannel for exactly this.
   - BroadcastChannel is in-memory, same-origin messaging, not storage. Whether sending the token over it is consistent
     with L3 ("tokens in memory only … never written to storage, logs, or analytics … never placed in a URL") is an
     interpretation that Phase 1/2 must state explicitly.
   - I do not know of another way to satisfy "wait instead of starting their own".
2. **Mutual exclusion.** BroadcastChannel alone can't stop two tabs from starting at the same moment, while Web Locks
   (`navigator.locks.request`) can. L18 allows either "BroadcastChannel or a Web Locks leader".
   - I do not know whether lock grant and message delivery are ordered across tabs. I believe they are not guaranteed
     to be, which bears on a sibling that acquires the lock just as the result is in transit.
3. **A tab closing mid-renewal.** If the renewing tab closes, Web Locks releases its lock automatically. A pure
   BroadcastChannel design has no such signal.
4. **Taking on a token.** The future token holder (tasks 20–22) needs an operation for this; its shape is undefined.
5. **Browser support.** Web Locks is in all current engines; jsdom lacks it. I do not know whether a fallback for
   engines without it is wanted.
