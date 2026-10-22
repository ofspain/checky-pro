# frontend · T07 · Phase 0 — Repository Understanding

Task 7, **429 generic copy**: "An SPA-originated 429 shows fixed copy with no countdown and no interval." Verifies
`rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23. Date: 2026-10-22. Read-only: no code was written.

Read for this phase:
- `spec/frontend/`:
  - `requirements.md` R23;
  - `agents.md`: API access, and Accessibility and i18n;
  - `design.md`: L6, L11, O2, O3, §6, and the "not present in `auth.yaml`" list;
  - `package.md` §8 and Q12(b);
  - `tasks.md` tasks 7 and 8.
- The T01–T06 code in `frontend/src/`.
- The auth-service rate limiter.
- The openapi-fetch 0.17.0 middleware loop.
- The live stack, probed.

## 1. Architecture summary (frontend, as built through T06)

- **Stack:** Vite 8.3.5, React 19.3.1, TS 7.0.2, react-router 8.4.0, Vitest 5.0.5 with jsdom 30.1.3, and oxlint 1.87.0
  (the L6 fetch ban). There is no UI library, i18n library or CSS framework yet.
- **`src/app/`:**
  - `AppShell` is an empty `<div data-testid="app-shell">`. Its doc says it "renders no copy of its own, because
    user-facing text comes only from the i18n catalogue (L11)".
  - `routes.tsx` has the route set with no screens attached. `mountShell.tsx` mounts it.
- **`src/api/`, the client boundary:**
  - `sessionBoundary.ts` (T05): one per tab; renews once on 401 and re-sends once. **A 429 passes through unchanged**
    (T05 test case "429").
  - `client.ts` (T05): `createAuthApi(boundary)` = `createAuthClient()` + `use(boundary.middleware(AUTH_PUBLIC_OPERATIONS))`.
  - `crossTabRenewal.ts` (T06).
- **`libs/ts/api-client`:** `createAuthClient()` only, generated from `auth.yaml`. There is no client yet for
  payments, notifications or crypto.
- **Persistence:** none. A 429 state must not be persisted either (§5 allows only public preferences in localStorage
  and the L16 items in sessionStorage).

## 2. Existing code this task touches

| Item | Status |
|---|---|
| A boundary component that recognises a 429 on any SPA call | **New.** `agents.md`: "The client boundary owns … fixed generic 429 handling … Screens do not reimplement them." |
| `createAuthApi` (`client.ts`) | **Exists.** The natural place to install a 429 middleware alongside T05's. It must stay additive, so T05's suite stays green. |
| The T05 middleware's handling of **public** operations | **Exists.** It skips them entirely. The only live SPA-originated 429 today is on a public operation (below), so 429 handling can't sit inside T05's middleware. |
| An i18n catalogue | **Doesn't exist.** O3 is resolved as `react-i18next`, English-only, with fallback to English and never a raw key. `design.md` §6 reserves `src/i18n/`. **No task in `tasks.md` sets it up.** T07 is the first task that shows a user-facing string. |
| A UI surface for the copy (a notice / live region) | **Doesn't exist.** O2 is resolved as Tailwind + headless components (Radix), and neither is installed. |
| The 429 POST replay rule | **Task 8**, the same named test. T05 already never replays a 429. |

## 3. Established patterns to follow

- **openapi-fetch middleware**, as in T05:
  - `onResponse` may return a replacement `Response`;
  - middleware runs `onResponse` in **reverse registration order** (`dist/index.mjs` loop
    `for (i = length - 1; i >= 0; i--)`). A middleware registered **before** T05's therefore sees the final response,
    including a 429 returned by T05's re-send through `options.fetch`, which bypasses middleware.
- **Instances, not module state:** one per tab, created in wiring, like the T05 boundary.
- **Failure-hook style** (T05): a hook scheduled with `queueMicrotask`, so a throwing listener can't affect the caller.
- **The L6 lint:** no global `fetch` in `src`, tests included. Use `vi.stubGlobal('fetch', …)`.
- **Copy rules:**
  - every user-facing string is in the catalogue, with no hardcoded copy (`agents.md`, L11);
  - async results are announced through a live region;
  - the UI maps problem types to copy and never shows server detail (`agents.md`: errors).
- **Dependencies:** exact pins, at least about 2 weeks old. Restart the frontend container after a dependency change.

## 4. Testing conventions

- Vitest with jsdom in `themistra-local-frontend-1`, currently **99/99**.
- Transport stubs via `vi.stubGlobal('fetch')`, with a fake `SessionTokens` as in the T05 tests.
- React rendering in tests: T01/T02 render through `react-dom/client` in jsdom (`shellBuildsAndMounts`).
  `@testing-library/react` is **not** installed.
- Mutation breaks use backups and are restored from them.

## 5. Live facts (probed 2026-10-22 through the edge, `localhost:5173`)

- **Where the backend rate-limits** (auth-service `RateLimitFilter`): `POST /login` (a SAS-hosted page, **out of SPA
  scope** per R23), `POST /accounts/password-reset` (keyed by the token hash), and the `/oauth2/token` **refresh_token**
  grant (never used; no refresh token is issued, Q5). Payment, crypto and notification services have no 429 source.
- **So the one live SPA-originated 429 today is `POST /accounts/password-reset`**, a **public** (`security: []`)
  operation. A junk token, so no real account was affected, gave 400 ×5, then:
  - `HTTP 429`;
  - `Content-Type: application/problem+json;charset=ISO-8859-1`;
  - `Retry-After: 11`;
  - body `{"type":"https://checky.pro/problems/rate-limit-exceeded","title":"Too many requests","status":429,…}`.
- **The contract:** `auth.yaml` documents no 429 or `Retry-After`. Q12(b) closed this as the repo convention (success
  responses only), so the SPA must not depend on the body or the header.

## 6. Known gaps / unknowns

1. **What "shows" means with no screen and no catalogue yet.** The named test says "shows generic copy". That needs:
   - a catalogue string;
   - something that renders it in a live region;
   - an i18n setup (react-i18next, O3), which no task owns.

   Whether T07 introduces the minimal catalogue plus a notice component, or only a boundary signal with a copy key,
   is a Phase 2 decision. I do not know of a task that would otherwise own the catalogue setup.
2. **How the notice is dismissed or cleared.** R23 says "resume only on user action". I do not know whether "resume"
   means dismissing the notice, retrying, or simply the user's next action. Phase 1/2 must read it, without inventing a
   timer.
3. **Non-auth clients.** Only `createAuthClient` exists. The 429 handling must be reusable when payments,
   notifications and crypto clients arrive. I do not know their shape yet.
4. **Several 429s at once** (several requests in one burst): presumably one notice, not one per call. Phase 2 must
   state it.
5. **A 429 on T05's re-send:** visible to a middleware registered first, by the ordering above. A Phase 2 probe should
   confirm it.
