<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T36 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

> **Task:** "Sign-out calls the end-session endpoint with an allowlisted `post_logout_redirect_uri`.
> Verifies `signOutClearsMemoryAndEndsSasSession`. Cites R19." (`tasks.md:64`)

## 1. Architecture summary

Vite + React 19 + TypeScript SPA under the `/app` basename. **The phase-0 prompt's headings assume a Spring
service** — persistence, outbox, resource-server security, Flyway; none exist here and I will not invent
frontend analogues. What matters for T36:

| Concern | How it works here |
|---|---|
| **Modules** | `auth/` the OIDC flow, the token, and now sign-out's clearing half · `api/` the client boundary and cross-tab coordination · `app/` shell, route table, mount · `i18n/` the catalogue · `pwa/` the service worker and its path policies |
| **Persistence** | none beyond L16's exhaustive sessionStorage carve-out. **T35 just made sign-out remove all four of its keys.** |
| **Security posture** | public OIDC client, PKCE, no secret in the bundle (L1, L10). The access token lives in one module-level `let` (`auth/accessToken.ts:24`) and nowhere else (L3). |
| **Navigation** | router navigations inside `/app`; **identity paths are full-page navigations** and are on the service-worker denylist (`pwa/navigationDenylist.ts:19` lists `/connect/logout`, R57, L17). |
| **Configuration** | derived, not configured. `client_id` is the constant `checky-spa`; `redirect_uri` and `post_logout_redirect_uri` come from `location.origin` plus the route (`FIRST-UI-READINESS.md:40-46`). No runtime config file. |

## 2. Existing code this task touches

| File | What it already is | Relevance to T36 |
|---|---|---|
| `auth/signOut.ts` | **T35, closed yesterday.** `signOut(): void` — four clearing calls, no state, no parameter, synchronous. Its header says clause 2 "is task 36, and it will call this before it navigates." | **T36's entry point.** The ordering is already decided and documented on T35's side. |
| `auth/authorizeRequest.ts:59-62` | `redirectUri()` — strips a trailing slash from `APP_BASENAME` and returns `${origin}${base}/callback`. | The exact shape T36 needs for `post_logout_redirect_uri`, one route along. Reusing vs. duplicating is phase 2's call; T35's F3 precedent says a near-copy needs a reason. |
| `auth/authorizeRequest.ts:23-29` | `AUTHORIZE_ENDPOINT_PATH`, `CLIENT_ID = 'checky-spa'`, `REQUESTED_SCOPES`. | Where the sibling endpoint constant lives. `/connect/logout` has no constant yet in `auth/`. |
| `auth/codeExchange.ts:36,76-79` | The exchange returns `{ idToken, accessToken }`; it validates the `id_token` is a non-empty string. | **The id_token exists here and is dropped one module later.** See the gap below. |
| `auth/CallbackScreen.tsx:156-169` | Checks the id_token's nonce, then `retainAccessToken(response.accessToken)` — **the id_token is never retained.** | If T36 needs an `id_token_hint`, this is the line that has to change, and it is in a closed task's screen. |
| `app/routes.tsx:31` | `'signed-out'` is in `PUBLIC_ROUTE_PATHS`, **with no element**. | The `post_logout_redirect_uri` target. **Task 37** attaches the screen; T36 must not depend on it rendering. |
| `pwa/navigationDenylist.ts:19,21`, `pwa/apiRequestPolicy.ts:27` | `/connect/logout` and `/logout` already bypass the service worker. | **Already built (T10).** T36's navigation is not intercepted. |
| `auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | T35's 38 cases, arranged one describe per criterion **specifically so T36 appends**. | T36 extends this file. Its header says T35 owns the "clears memory" half only. |
| `auth/accessToken.ts:7-10` | "Do not move it into storage, a store, a React context or a second module, and do not copy it anywhere." | If T36 retains an id_token, it is a second piece of in-memory token state, and this comment is the standing argument against carelessness about where it lives. |

**New in T36:** a module building and performing the end-session navigation. Nothing in `src/` does it today:
`grep -rni "logout"` over production source returns only the two PWA path policies and three doc comments.

## 3. Established patterns to follow

- **Derive the URL from `location.origin` plus a route constant**, never a configured absolute. `redirectUri()`
  is the worked example and `FIRST-UI-READINESS.md:40-46` records the decision and its evidence.
- **Read route names inside a function, never at module scope** — `routes.tsx:2-5` explains the import cycle
  this closes, and T33/T35 both have source scans asserting it.
- **Build query strings with `URLSearchParams`**, as `authorizeRequest.ts:86-91` does, so encoding is the
  platform's job.
- **Inject the effect rather than importing the router**; but note that T36's navigation is a **full-page**
  one, so the router is the wrong instrument entirely and `globalThis.location` is the right one — which is
  the opposite of T33's and T35's constraint and will need saying out loud.
- **Source-scanning tests**, one named test file per task, and a per-task mutation harness that reverts with
  `git checkout` (**commit tests first**), asserts its own baseline, scores canaries apart, and records each
  mutant's failure count.

## 4. Testing conventions

Vitest 5.0.5 + jsdom 30.1.3, run as `npx -y pnpm@12.14.0 exec vitest run …` — bare `pnpm` is not on this
host, and **the exit code is the verdict**. No Testcontainers, no ArchUnit, no injected `Clock` in this
service. Current whole-suite baseline: **1616 passed / 1 failed of 1617**, the one failure being
`src/api/lintRejectsHandWrittenBackendFetch.test.ts` (this host's `oxlint@1.87.0` prints no summary line).

jsdom does not navigate: assigning `location.href` does nothing observable by default, so T36's navigation
will have to be observed through a stubbed `location` — a technique this package has not needed yet, and the
first real unknown of this task.

## 5. Known gaps and unknowns

1. **The `id_token` is gone by the time T36 runs, and this is the task's central problem.**
   `codeExchange.ts:76-79` produces it, `CallbackScreen.tsx:156` checks its nonce, and nothing retains it.
   **I measured the backend rather than reasoning about it**, as T35's OQ-T35-1 said to:
   `SasLoginIntegrationTest.endSessionEndsTheSasSessionAndReturnsToTheSignedOutRoute` (`:475`) and
   `…RefusesAPostLogoutRedirectOutsideTheAllowlist` (`:502`) both build the URL as
   `/connect/logout?id_token_hint=<JWT>&post_logout_redirect_uri=<allowlisted>&state=<s>` (`:519-527`), and
   the first asserts `302` to `postLogout + "?state=s1"` with a subsequent `/oauth2/authorize` going to
   `/login` — the session really is gone. **Neither test omits `id_token_hint`**, so whether SAS honours a
   `post_logout_redirect_uri` without it is **not answered by the green tests**. Spring Boot 3.5.4 /
   Spring Authorization Server **1.5.1** is the version in use; I do not know from reading alone whether its
   logout endpoint accepts a `client_id` in place of the hint, and I am not going to assert it.
   **The consequence either way is the same:** the proven shape carries the hint, T10's frozen brief pinned
   that shape, so T36 should retain the `id_token`. That means **T36 adds in-memory state in order to spend
   it**, and T35's AC7 and AC8 instruments will fail the moment it does — which is exactly the handover T35
   built them for.
2. **Retaining an `id_token` is a posture question that phase 1 must answer, not assume.** L3 names *access*
   tokens, and in-memory is not L16's "stored client-side", so neither is violated on its face. But an
   `id_token` is a signed document containing the visitor's email, and `accessToken.ts:7-10` argues against a
   second copy of anything token-shaped on principle. Where it lives, how long, and whether sign-out clears
   it are all phase 1's.
3. **Nothing can trigger sign-out, and T36 does not change that.** No authenticated route has an element,
   `AppShell.tsx` renders a bare `<div>`, no task owns an affordance. T35 was the sixth built-and-dormant
   task; T36 will be the seventh. **OQ-T35-2 / OQ-T33-2.**
4. **`/app/signed-out` has no screen** (task 37), so after T36 the visitor lands on a blank route. The
   navigation is real; what renders is someone else's task — the same situation `returnTarget.ts`'s T24-D11
   records for `/app/home`.
5. **jsdom cannot navigate.** How to observe a full-page navigation without asserting on a stub that proves
   only that the stub was called is a genuine open question for phase 2. The nearest precedent in the package
   is T15's authorize navigation; I have not yet read how it does it.
6. **Spec drift, recorded not fixed (third instance).** `package.md:123` still names the test
   `signOutRevokesTokensClearsMemoryAndEndsSasSession` — with the revoke step **R19's 2026-10-10 revision
   removed** — while `tasks.md:63` and `:64` both say `signOutClearsMemoryAndEndsSasSession`. T36's own
   prompt header repeats `package.md`'s stale spelling. `spec/` is not mine to edit.
7. **Q15 blocks task 38, not this one**, but it is adjacent: whether sign-out then sign-in shows the password
   step depends on what SAS does with its session cookie, which the SPA cannot assert.

**Phase 0 complete.**
