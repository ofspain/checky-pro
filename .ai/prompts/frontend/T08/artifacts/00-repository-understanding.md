# frontend · T08 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T08 — No POST replay |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim):**
> **No POST replay.** A 429 on a non-idempotent POST is never retried automatically. Verifies `rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.

Read the repository and `spec/frontend/` (five files). No code written.

## 1. Architecture summary

The service is the SPA, not a backend module, so the prompt's persistence / outbox / resource-server headings map as
follows.

- **Modules** (`frontend/src/`): `api/` is the client boundary — `client.ts` (the generated auth client plus
  middleware registration), `sessionBoundary.ts` (T05, single-flight 401 renewal and the one re-send),
  `crossTabRenewal.ts` (T06, Web Locks leader + BroadcastChannel), `rateLimitNotice.ts` (T07, the 429 notice).
  `app/` is the shell and routing — `AppShell.tsx`, `mountShell.tsx`, `routes.tsx`, `RateLimitNotice.tsx`.
  `i18n/` is the catalogue (T07). `main.tsx` is the entry. A `pwa/` module (service worker, tasks 9–12) does not
  exist yet. Build and test config is one `vite.config.ts` (`base: '/app/'`, jsdom for Vitest).
- **Generated client**: `libs/ts/api-client` (T03), openapi-typescript 7.13.0 + openapi-fetch 0.17.0, generated from
  `contracts/api/auth.yaml` with a CI drift check. Hand-editing it is forbidden (L6), and `createAuthClient()` is the
  only transport into the auth service.
- **Persistence**: none, by design. Nothing is written to `localStorage`, cookies, logs or the URL (§5, L16, the
  `agents.md` storage rules). The only client state T05–T07 hold is in memory: one in-flight renewal promise, a
  per-request `pending` map, and T07's one `shown` boolean per tab.
- **Events**: no outbox. The only message bus is a same-origin `BroadcastChannel` for cross-tab renewal (T06).
- **Security**: the SPA is a public OIDC client; interactive login is SAS-hosted. The boundary attaches the bearer to
  protected operations and never to the five `security: []` operations in `auth.yaml`
  (`AUTH_PUBLIC_OPERATIONS`, `client.ts:6-12`). Errors are RFC 9457 problem documents; server text is never shown.

## 2. Existing code this task touches

Everything this task is about already exists; **T08 adds no new production module as far as the spec says**.

- **`src/api/sessionBoundary.ts` (T05, 141 lines) — the only code in the SPA that re-sends a request.**
  `onResponse` returns early unless `response.status === 401` (`:118`), the operation is protected, and the request
  has a `pending` entry. It then awaits the shared renewal and re-sends **one** clone straight to
  `clientOptions.fetch` (`:129`), bypassing every middleware, so a re-send cannot loop. The clone is taken in
  `onRequest` (`:101`) and is `null` when the body cannot be cloned, in which case the 401 is returned as-is. **This
  path applies to POST as well as GET** — T05's own suite asserts a re-sent `POST /api-keys` carries the fresh token
  and the original body (`parallel401sTriggerExactlyOneRenewal.test.ts:143-144`).
- **`src/api/rateLimitNotice.ts` (T07)** — the 429 middleware. `onResponse` sets one boolean and returns `undefined`
  (`:41-42`); there is no `onRequest` and no `onError`, so nothing it does can re-send anything.
- **`src/api/client.ts` (T07/T05)** — registers the notice middleware first, then the boundary's (`:24-25`).
- **`src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` (T07, 29 cases)** — the **same named test**
  this task verifies. It already asserts one transport call per call on a 429 (AC6) and that a 429 reaches the caller
  unchanged. Its POST coverage is a public `POST /accounts/password-reset` and a protected+public pair; what it does
  **not** have is a non-idempotent-POST replay inventory, or the 401-then-429 sequence on a POST.
- **`contracts/api/auth.yaml`** — **19 POST operations**, 5 of them public (`security: []`). All 19 are
  non-idempotent in the HTTP sense (no idempotency key is defined anywhere in the contract).
- New: nothing identified. Whether T08 is test-only, or also adds a structural guard, is a Phase 1/2 question.

## 3. Established patterns to follow

- **Middleware shape**: a plain object with `onRequest` / `onResponse` / `onError`, typed as `Middleware`
  (`sessionBoundary.ts:4`, derived from the generated client so the SPA needs no direct openapi-fetch dependency).
  `onResponse` returning `undefined` passes the response through untouched; returning a `Response` replaces it.
- **Registration order matters**: openapi-fetch runs `onResponse` in reverse registration order, so the
  first-registered middleware runs last and sees the final response (`client.ts:22-25`, probed at T07 phase 4).
- **Re-sends go straight to `options.fetch`**, never back through the client, which is what makes "exactly once"
  provable (`sessionBoundary.ts:127-129`).
- **Failure isolation**: a throwing caller-supplied function is rethrown in a `queueMicrotask` so it surfaces uncaught
  without touching the response chain (`sessionBoundary.ts:71-73`, `rateLimitNotice.ts:28-32`).
- **No module-level mutable state**: every unit is a factory (`createSessionBoundary`, `shareRenewalAcrossTabs`,
  `createRateLimitNotice`, `createI18n`), one instance per tab, created by wiring.
- **Lint as a guard**: `.oxlintrc.json` enables only two rules, both banning `fetch`/`XMLHttpRequest` as globals or
  off `window`/`globalThis`/`self`/`top`/`parent`/`opener`/`frames` (L6). `pnpm lint` runs with `--no-ignore`.
- **Configuration**: none for this task. No env key, no feature flag.

## 4. Testing conventions

- **Vitest + jsdom** (`vite.config.ts`), not JUnit/Testcontainers — the prompt's backend conventions do not apply to
  this package, and `agents.md` names Vitest and Playwright for the SPA.
- One test file per named test from `package.md` §8, named exactly after it. **T08 shares T07's file**, since
  `tasks.md` tasks 7 and 8 both verify `rateLimitedResponseShowsGenericCopyWithoutReplay`.
- Helpers are **local to each test file** and copied rather than imported across tasks (T05's
  `installBrowserRequest` / `installTransport` / `fakeSession` appear in T06's and T07's files independently), so a
  task never changes a previous task's suite (L12).
- The transport is stubbed with `vi.stubGlobal('fetch', …)`; relative URLs are resolved browser-style by a `Request`
  subclass. React is rendered through `react-dom/client` + `act` with `IS_REACT_ACT_ENVIRONMENT`.
- **Fixed clock**: not used here. Time is controlled with `vi.useFakeTimers({ toFake: [...] })` where a test needs it,
  and T06 learned that `vi.waitFor` polls every 50 ms, so short deadlines need `{ interval: 1 }`.
- **Mutation breaks are part of the deliverable**: each task's phase 10/12 artifacts list mutants applied to
  production code and the cases that caught them (T07: 14/14). T07's `M7` is exactly "a replay on 429", caught by 9
  cases.
- **No ArchUnit equivalent** in the SPA. The structural guards are the lint rules (T04) and source scans inside
  tests (T04 scans `src` for inline lint-disable comments).

## 5. Known gaps / unknowns

- **Whether T08 has any production deliverable at all.** T07's frozen brief says task 8 owns "the POST no-replay
  guarantee and its cases, same named test", which reads test-only; but `tasks.md` task 8 is phrased as a behaviour
  ("is never retried automatically"). The behaviour is already true of today's code. I do not know whether the author
  intends new cases only, or also a standing guard that keeps it true. Phase 1 lists it; Phase 3/4 decide.
- **Whether T05's 401 re-send of a POST counts as "replay" under R23.** R23's `WHEN` clause is the 429 case, and the
  `agents.md` API-access rule puts the no-replay sentence inside "429 handling", which suggests not. This is the one
  real interpretation question in the task, and it is a gate decision, not mine.
- **`design.md`'s "Not present in `auth.yaml`" list is stale** on one point relevant to the POST inventory: it names
  `accounts/me/mfa/totp`, `.../confirm` and `.../recovery-codes` as absent and blocked on Q1, but `auth.yaml` now
  declares all three as POST operations (`:163`, `:193`, `:214`). `spec/` is read-only for frontend tasks, so this is
  recorded, not fixed, and it only affects how many POSTs the inventory can name.
- **The live 429 surface.** Probed at T07 phase 4: `POST /accounts/password-reset` after five 400s, with
  `Retry-After: 11`. It is the only SPA-originated 429 today and it is a **public, non-idempotent POST** — the exact
  shape this task is about. R42's edge limiter may add others on any route with any body; I do not know its
  configuration.
- **No in-container verification this session.** The Docker daemon is down (phase 9/12 of T07 record the host
  workaround), so a live re-probe of the 429 is not available right now.
