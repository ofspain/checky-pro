<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T41 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

T41 — **Unauthenticated redirect.** An unauthenticated request for any other route redirects to sign-in.
Verifies `unauthenticatedRoutesRedirectToSignIn`. Cites **R28**.

## Purpose

R28's second clause has no mechanism. Task 40 built the first: a sessionless visitor is served the nine public
routes and nothing else, and everything else renders **blank**. A blank screen is not a redirect — the
visitor is stranded with no way forward, and a deep link they followed is lost. T41 sends them to the
sign-in start and, where the allowlist permits it, **keeps the link they came for**, which is the case L1's
post-login return target was locked for and which has no writer today except a renewal failure.

## Scope

**In**

- the redirect to the sign-in start for **all three** sessionless arrivals R28 names:
  **(a)** any of the eleven `AUTHENTICATED_ROUTE_PATHS`, **(b)** a path matching nothing, **(c)** the bare `/app/`;
- populating the post-login return target for **(a)**, through `rememberReturnTarget`, whose allowlist makes
  **(b)** and **(c)** remember nothing without a branch;
- the named test `unauthenticatedRoutesRedirectToSignIn` — **a new file**;
- growing the instruments of tasks 16, 33, 37 and 40 that this change makes fire.

**Out**

- any **screen**, and the authenticated shell — **task 42**, blocked on 173/174;
- **focus management on the landing** — task 174, by `package.md:168`;
- what happens to an **authenticated** visitor at an unmatched path — **outside R28**, which is conditioned
  *"WHILE a visitor is unauthenticated"* (see Open Questions);
- silent re-authentication and session restoration after reload — unbuilt, no task;
- any new route, storage key, endpoint, config key or catalogue entry;
- any change to `spec/`.

## Business rules

| ID | |
|---|---|
| **R28** | While unauthenticated, the SPA serves only §4c's nine public routes and **redirects any other route to the sign-in start**. Task 40 built the first clause; this is the second. |
| R19 | The signed-out landing is one of the nine, so it is a destination and must **not** be redirected. |
| R29 | Mid-task form input survives an expiring session with a 15-minute TTL — **not this task**, but the reason T41 must not clear storage it does not own. |

## Locked decisions

| ID | |
|---|---|
| **L14** | §4c's route set is verbatim: nine public, eleven authenticated, `/app` disjoint from every edge prefix. No route added or re-spelled. |
| **L1** | **Allowlisted** post-login return target, falling back to the home route. This is why **(a)** remembers where the visitor was. |
| **L16** | The sessionStorage carve-out — `state`, `nonce`, `code_verifier`, **the post-login return target** — is the **entire** list. The return target uses the existing `RETURN_TARGET_KEY`; **no new key.** |
| **L4** | Enumeration-safe: copy, **redirects**, styling and timing identical across account and token states. The three arrivals must be indistinguishable, and none may say why. |
| **L11** | Catalogue-only strings; the redirect renders no copy of its own. |
| **L12, L13** | Phase additivity; React Router is the router, so the redirect is expressed in the router's own terms. |
| **L9** | Single origin. No new URL — the sign-in start is already built. |
| **L15** | Four of the eleven gated paths are payment surfaces, so this completes the gate they wait on. |
| **L3** | Tokens in memory only; the only question asked is `accessToken.current()`. |

## Dependencies

`accessToken.current()` (`auth/accessToken.ts`) · `rememberReturnTarget`, `isAllowlistedTarget`,
`RETURN_TARGET_KEY` (`auth/returnTarget.ts`) · `inAppPath()` (`auth/cleanReauth.ts`) · `SIGN_IN_PATH`,
`APP_BASENAME`, `PUBLIC_ROUTE_PATHS`, `AUTHENTICATED_ROUTE_PATHS`, `appRoutes` (`app/routes.tsx`) ·
`RequireSession` (`auth/RequireSession.tsx`) · `react-router`.
**Already built on the consuming side:** `CallbackScreen.tsx:201` reads the return target and falls back to
home. **No endpoint, no contract, no config key.**

## Inputs

The requested path, and the session owner's answer to "is a token held".

## Outputs

For a public path: the route's own element, unchanged, and **no navigation**. For each of the three
sessionless arrivals: a **replacing** navigation to the sign-in start, with the return target populated for
**(a)** only. For an authenticated path **with** a session: the route's own element, unchanged.

## State changes

**One, and only for arrival (a):** `sessionStorage[RETURN_TARGET_KEY]` is written through
`rememberReturnTarget`, which is already single-use, already allowlisted, and already deleted at callback
(L16). Nothing else is stored, written or sent.

## Files to create

- **the named test** `unauthenticatedRoutesRedirectToSignIn` — the file does not exist; **task 42 extends
  it**, so its describes are prefixed from the first case.
- **whether a production module is created** is phase 3's to settle; the redirect may live in modules that
  already exist.

## Files to modify

- `auth/RequireSession.tsx` — arrival **(a)**: the `null` at `:42` is what T41 changes, or does not.
- `app/routes.tsx` — arrivals **(b)** and **(c)**: the catch-all at `:78`, whose own comment reads *"T41
  replaces it with the sign-in redirect"*.
- `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` · `app/routesResolveUnderAppPrefixOnly.test.tsx` ·
  `auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx` — **only** where an instrument fires.
  Three of those instruments name this task in their own titles.

## Files NOT to modify

`spec/**` · `auth/accessToken.ts` and the rest of the session owner · `auth/returnTarget.ts` (it already does
what is needed; a change there would alter four closed tasks' claims) · `auth/CallbackScreen.tsx`,
`SignInScreen.tsx`, `SignedOutScreen.tsx` · `auth/cleanReauth.ts` · `app/mountShell.tsx`, `AppShell.tsx` ·
`i18n/catalogue.ts` · the two route arrays · every suite of tasks 21, 22, 24, 25, 35, 36, 39 unless an
instrument of theirs fires.

## Acceptance criteria

| | | ID |
|---|---|---|
| **AC1** | **(a)** a sessionless arrival at **each** of the eleven authenticated paths ends at the sign-in start. | R28 |
| **AC2** | **(b)** a sessionless arrival at a path matching nothing ends at the sign-in start. | R28 |
| **AC3** | **(c)** a sessionless arrival at the bare `/app/` ends at the sign-in start. | R28 |
| **AC4** | the nine public routes are **still served and not redirected** — the signed-out landing among them (R19), with a query string, and with a trailing slash. | R28, L14, R19 |
| **AC5** | **with** a session, an authenticated path is **not** redirected. | R28 |
| **AC6** | the redirect **replaces** rather than pushes, so the visitor cannot bounce back into the route that refused them. | the package's pattern |
| **AC7** | **(a)** remembers the arrival as the post-login return target; **(b)** and **(c)** remember nothing — **by the allowlist, not by a branch**. | **L1**, L16 |
| **AC8** | **no new storage key**: `sessionStorage` holds only L16's items. | **L16** |
| **AC9** | the three arrivals are **indistinguishable** — same destination, same history action, same rendered output, no copy naming a cause. | **L4** |
| **AC10** | **no catalogue entry** is added and the redirect renders no copy of its own. | L11 |
| **AC11** | one spelling: the destination is built from `SIGN_IN_PATH`, read **inside** a function, never at module scope. | the package's pattern |
| **AC12** | instruments of tasks 16, 33, 37 and 40 are **grown, not bypassed**. | the handoff pattern |
| **AC13** | the route **set** is unchanged — nine, eleven, one catch-all — and `pathTree`'s shape holds except where the redirect provably changes it. | L14, L12 |

## Required tests

**Named:** `unauthenticatedRoutesRedirectToSignIn` → R28 (new file).

**Boundaries:** each of the eleven, including both `:id` paths · an unmatched path, and one that *resembles*
a public path without being one (`/app/sign-in-x`) · the bare `/app/`, with and without the trailing slash ·
each of the nine, unredirected · a public path with a query string, the query surviving · an authenticated
path **with** a session · the return target after each of the three arrivals · `historyAction` on every
arrival · the redirect under `StrictMode`.

## Constraints

- **Security.** The only question available is "is a token held". A held token does **not** prove the account
  is usable — a SUSPENDED account keeps a working token, and `/accounts/me` is the only thing that can say
  otherwise (task 31). **L4 binds harder here than anywhere in T40:** a redirect is observable to an
  unauthenticated prober, so the three arrivals must not differ in destination, timing, history action or
  rendered output. The return target differs between them, but it is in the prober's **own** `sessionStorage`
  and reflects only the path they typed.
- **Module boundaries.** `app/` → `auth/` is permitted and used. `auth/` must not import from `app/` except
  for the published route constants, which it already does. A module running outside React takes `navigate`
  as a parameter rather than importing the router (`cleanReauth.ts:60-62`, and `routes.tsx:2-7` documents a
  cycle that already exists).
- **Null handling.** `accessToken.current()` returns `string | null`; **the empty string is not a session**
  (`accessToken.ts:90` is `held = token;` with no guard). `inAppPath()` returns `string | null` and
  normalises the bare `/app` to `/`.
- **No copy, no catalogue, no new key.**
- **Performance / threading / transactions.** Not applicable — a synchronous read in a browser.
- **One spelling.** No path string is re-spelled; everything derives from `routes.tsx`.

## Open questions

**No blockers.** Three items carried, each with its evidence in phase 1:

1. **Does the catch-all redirect an *authenticated* visitor?** R28 is conditioned *"WHILE a visitor is
   unauthenticated"*, so a signed-in visitor at an unmatched path is **outside the requirement**, and
   `tasks.md:72` says only that a session renders the shell and the authenticated routes. **Recommendation:
   out of scope — keep today's behaviour**, so T41 claims nothing the spec does not. Phase 3's to challenge.
2. **The two screen-count pins.** If the redirect is an element on the catch-all, `routesWithElements` and
   T37's AC9 go from three to **four**, and a redirect is not a screen. T40's gate escaped them by being
   **pathless**; the catch-all has a path, so that escape does not exist. **Phase 3's to settle** — it is the
   same shape of decision phase 3 settled for T40, and it is this task's real design content.
3. **Spec drift, the owner's:** `design.md:137` reads *"PROPOSED … pending author confirmation (Q6)"* while
   `package.md:368` records Q6 resolved 2026-10-10. Fourth consecutive task; `spec/` is not modified here.
