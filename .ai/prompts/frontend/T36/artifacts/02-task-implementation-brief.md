<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T36 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

Build the hand-over half of sign-out: clear locally through T35's `signOut()`, then give the browser to SAS's
end-session endpoint with an `id_token_hint` and the allowlisted `post_logout_redirect_uri`. R19 clause 2.

## Purpose

So that signing out in the SPA signs the visitor out **at the authorization server**, not only in this tab.
Without clause 2 the local clear is cosmetic: the SAS session cookie survives, and the next
`/oauth2/authorize` returns a code without asking for anything — the visitor would be signed back in by the
act of visiting. R19.

## Scope

**In**

- One module that builds the end-session URL and performs the hand-over, calling `signOut()` first.
- Retaining the `id_token` so the hint can be built, **and clearing it on sign-out** — which means extending
  T35's inventory rather than loosening its scans.
- The behaviour when no `id_token` is held.

**Out**

- The `/app/signed-out` screen and its copy — **task 37**. T36 supplies the URL; what renders there is not
  its business.
- Propagating sign-out to sibling tabs — **task 39**.
- The sign-out affordance — unowned.
- `/oauth2/revoke` — removed from R19 by Q5, 2026-10-10. Nothing calls it.
- A `state` parameter — see the frozen decision below.
- Closing the cross-tab adoption hazard — **task 39**, **OQ-T35-3**.

## Business Rules

- **R19 cl. 2** — end the SAS session via the end-session endpoint with an allowlisted
  `post_logout_redirect_uri`; no `/oauth2/revoke`. *(This task.)*
- **R19 cl. 1** — clear in-memory token state. *(Task 35; T36 calls it, first.)*
- **R57** — `/connect/logout` is an identity navigation, never cached, never served from the fallback.
  *(Built, T10. It is what makes this a full-page navigation.)*
- **R19 (task 37's half)** — the visitor lands on `/app/signed-out`. *(T36 names the URL only.)*

## Locked Decisions

**L9** single origin, so the URL is own-origin and derived · **L2** the hand-over is to SAS, as login is ·
**L3** tokens in memory only · **L4** nothing names a cause · **L10/R58** no secret in the bundle ·
**L11** catalogue-only strings (vacuous — nothing renders) · **L12** phase additivity · **L14** the declared
route set · **L16** the sessionStorage carve-out is exhaustive · **L17** service-worker boundaries (built).

## Dependencies

| Thing | Where | Note |
|---|---|---|
| `signOut()` | `auth/signOut.ts:59` | called first; synchronous and complete on return by T35's AC4 |
| `CLIENT_ID` | `auth/authorizeRequest.ts:26` | `checky-spa`, public |
| `APP_BASENAME`, `'signed-out'` | `app/routes.tsx:10,31` | read **inside** a function (the cycle at `routes.tsx:2-5`) |
| `redirectUri()`'s shape | `auth/authorizeRequest.ts:59-62` | the worked example of `origin` + de-slashed basename + route |
| the `id_token` | `auth/codeExchange.ts:36` → held per the decision below | |
| the proven URL shape | `SasLoginIntegrationTest:519-527` | `/connect/logout?id_token_hint&post_logout_redirect_uri&state`, `302` to `<uri>?state=`, `400` for a non-allowlisted target |

**No backend call, no contract, no generated client.** The end-session step is a browser navigation, so **L6**
is vacuous and no `contracts/api/*.yaml` is in scope.

## Inputs

**None from the caller.** No parameter: there is no session identifier to pass, and nothing to branch on.

## Outputs

**None.** The function hands the browser away; there is no return value a caller could act on, and after the
navigation there is no caller.

## State Changes

- Everything `signOut()` changes (T35): the held token, the pending notice, four sessionStorage keys.
- **The retained `id_token` → `null`**, by the same call.
- `globalThis.location` → SAS's end-session URL. Nothing else.

## Files to Create

- `frontend/src/auth/endSession.ts`
- No new test file: **T36 appends to `auth/signOutClearsMemoryAndEndsSasSession.test.tsx`**, which T35
  arranged one describe per criterion so that this is additive.

## Files to Modify

| File | Change | Checked |
|---|---|---|
| `auth/accessToken.ts` | hold the `id_token` beside the access token; clear both | T21's two scans require `let held: string \| null = null;` present and `sessionStorage` absent (`tokensAreNeverWrittenToAnyStorage.test.tsx:986-988`); a second `let` breaks neither |
| `auth/CallbackScreen.tsx` | one line — retain the `id_token` on the far side of the nonce check, beside `retainAccessToken` | the ordering comment at `:164-168` governs; the new line goes with it, not before it |
| `auth/signOut.ts` | nothing, if clearing is routed through the existing primitive | prefer this |
| `auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | T36's criteria appended, **plus T35's AC7 strengthened** — see below | |

## Files NOT to Modify

`pwa/*` · `app/routes.tsx` · `i18n/catalogue.ts` · `api/*` · `auth/returnTarget.ts` ·
`auth/authorizeRequest.ts` · `auth/signInNotice.ts` · every other closed suite · anything under `spec/`.
If the work appears to need one, **stop and log it**.

## Acceptance Criteria

| # | Criterion |
|---|---|
| **AC1** | `signOut()` completes **before** the hand-over, so no token is held when the browser leaves. |
| **AC2** | Exactly **one** hand-over, counted across `assign`, `replace`, the `href` setter and `window.open` — with a control proving a watcher installed after the call sees nothing. |
| **AC3** | The URL is `/connect/logout` on the SPA's own origin, derived from `location.origin`; no absolute host is spelled anywhere in production source. |
| **AC4** | `post_logout_redirect_uri` is `${origin}${basename}/signed-out`, derived from the route table, and equal to the registered allowlisted value. A trailing slash on `APP_BASENAME` cannot produce `/app//signed-out`. |
| **AC5** | `id_token_hint` carries the retained `id_token`, and that is the only reason it is retained. |
| **AC6** | The parameter set is **exactly** `id_token_hint` and `post_logout_redirect_uri` — no access token, no account identifier, no scope, no `state`. |
| **AC7** | A full-page navigation: no `react-router` import, no `history` use, no router navigation. |
| **AC8** | The retained `id_token` reaches no storage surface, no console channel and no analytics call. |
| **AC9** | **Sign-out clears the `id_token`.** T35's inventory gains a row, and **T35's AC7 is strengthened to pin declaration counts for *cleared* files as well as excused ones** — today it counts only the excused, so a second mutable added to `accessToken.ts` and left uncleared would pass. T36 is the first task to add one. |
| **AC10** | Only sign-out ends the SAS session: no other production module builds that URL or reaches the endpoint. |
| **AC11** | T36 adds no route, no catalogue key, no backend call and no storage key. |
| **AC12** | A tab holding **no** `id_token` still clears, and still navigates with the hint omitted rather than skipping the hand-over. |

## Required Tests

Appended to `signOutClearsMemoryAndEndsSasSession`. Beyond one case per criterion:

- The hand-over watcher **installed before** the call, and its control installed after.
- A tab with no `id_token` — cold, and the adopted-from-sibling shape.
- `APP_BASENAME` with a trailing slash.
- The post-logout value compared against the registered allowlisted default, so a typo fails here rather
  than as a `400` in a browser nobody is watching.
- A sweep proving no token, account identifier or scope reaches the URL, any console channel or any store.
- Controls for every scan and sweep, each shown firing on a planted positive.

Gates: the suite green; whole suite no worse than **1616 passed / 1 failed of 1617** (this host's `oxlint`
case); `tsc --noEmit` 0; `oxlint src` 0; a mutation harness with canaries scored apart, asserting its own
baseline, recording each mutant's failure count, with coarse kills verified narrowly.

## Constraints

- **Security.** No access token, account identifier, scope or failure detail in the URL, a log, a console
  channel or an error message (L3, L4). The `id_token` goes in exactly one query parameter and nowhere else.
- **Module boundaries.** Clear through the owning primitive; never assign a module variable from outside it.
- **Derive, never spell.** No absolute origin, no second spelling of a route or key.
- **Full-page navigation is correct here** and is the opposite of T33's and T35's constraint. Both of those
  forbid `globalThis.location`; T36 requires it, because SAS serves the end-session response (R57) and a
  router navigation cannot leave the SPA. **Say this out loud in the module** so a reader who knows the other
  two rules does not read this as a violation.
- **Order is load-bearing**, as in T35: clear first, hand over second. A hand-over that happened first would
  leave a tab that is navigating away while still holding a token.
- **No module-level mutable state in the new module.**

## Open Questions

**OQ-T36-1 — FROZEN. The `id_token` is held inside `auth/accessToken.ts`, beside the access token.** The
module's own comment — "do not move it into storage, a store, a React context or a second module, and do not
copy it anywhere" — is an argument against a second *location*, not against that module holding a second
value, and sign-out having exactly one module to ask is worth more than leaving a closed file untouched.
Checked rather than assumed: T21's two scans over this file require `let held: string | null = null;` to be
present and `sessionStorage` to be absent, and a second `let` breaks neither.

**OQ-T36-2 — FROZEN: no `state` parameter.** The proven shape includes one and the backend echoes it, but a
`state` that is sent and never verified is theatre, and verifying one means storing it — which L16's
exhaustive carve-out does not permit, since its `state` entry is the *authorize* state and reusing that key
would collide with a sign-in in flight. OIDC makes it optional.

**OQ-T36-3 — FROZEN, and measured.** AC12's no-`id_token` tab is **unreachable in production today**:
`adopt` is called only from `crossTabRenewal.ts:103` on a `renewal:succeeded`, and `accessToken.renew()`
always rejects because silent re-authentication is not built and **no task in `tasks.md` builds it**. So no
production path produces a tab with an access token and no `id_token`. The criterion is kept anyway, and the
behaviour frozen as **navigate with the hint omitted**, because the alternative is worse: skipping the
hand-over leaves the SAS session cookie alive, and the next `/oauth2/authorize` would return a code without
asking for anything — a visitor who believes they signed out being signed back in by the act of visiting.
A failed end-session leaves them locally signed out and confused; a skipped one leaves them signed in and
reassured.

**OQ-T36-4 — open, the owner's, deliberately unmeasured.** Whether SAS 1.5.1 honours a
`post_logout_redirect_uri` without an `id_token_hint` is **not answered by the green backend tests** — both
supply it (`SasLoginIntegrationTest:487,512`). It does not block T36. It is recorded because answering it is
what would let a future task stop retaining the `id_token` at all, and it needs a backend measurement rather
than a reading.

**OQ-T36-5 — the owner's, seventh time.** Nothing in the SPA can trigger sign-out. T36 joins tasks 5, 6, 30,
31, 33 and 35 as built and uncalled. Same substance as **OQ-T33-2** / **OQ-T35-2**.

Otherwise **no blockers**.
