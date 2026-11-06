<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T36 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## Business Rules

- **R19 cl. 2** (`requirements.md:62-65`) — sign-out ends the SAS session through the OIDC end-session
  endpoint with a `post_logout_redirect_uri` **from the allowlist**. Revised 2026-10-10 (Q5): **no
  `/oauth2/revoke`**. The end-session navigation is served by **SAS, not the SPA shell** (R57).
- **R19 cl. 1** — clearing in-memory token state. **Task 35, closed.** T36 calls it, and the order is already
  decided on T35's side: `signOut.ts` says clause 2 "will call this before it navigates."
- **R57** (`requirements.md:203`) — a navigation to `/connect/logout` is an identity path: it bypasses the
  service-worker fallback and is never answered from cache. **Already built** (`pwa/navigationDenylist.ts:19`,
  T10). It is what makes T36's step a **full-page** navigation rather than a router one.
- **R19 (task 37's half)** — the visitor lands on `/app/signed-out`. T36 supplies that URL as the
  `post_logout_redirect_uri`; **task 37 builds the screen**, and T36 must not depend on it rendering.

## Locked Decisions

| ID | What it constrains here |
|---|---|
| **L9** (`design.md:44`) | **Single origin.** No per-service URLs; all traffic including the SAS endpoints goes through the edge path map. So `/connect/logout` is **same-origin and relative**, derived from `location.origin`, never a configured absolute. |
| **L2** (`design.md:11`) | Interactive login is SAS-hosted. Sign-out is the same kind of hand-over: the SPA builds a URL and gives the browser away. |
| **L10 / R58** | No secret and no non-public config key in the bundle. `client_id` is public; nothing else is needed. |
| **L3** (`design.md:13`) | Tokens in memory only. **The id_token question lives here** — L3 names *access* tokens, and this is a different token. See OQ-T36-1. |
| **L16** (`design.md:58-60`) | The sessionStorage carve-out is **exhaustive** and names `state`, `nonce`, `code_verifier`, the return target and R29's form input. **It does not name a logout `state`.** See OQ-T36-2. |
| **L17** (`design.md:61-62`) | Service-worker boundaries — the denylisted navigation paths are never answered from cache. Built. |
| **L4** | Enumeration-safe copy and timing. Sign-out is the visitor's own action, so it discloses nothing; but nothing on the way out may name a cause either. |
| **L11** | Catalogue-only strings. T36 renders nothing, so this is vacuous unless it acquires copy — which would be a smell. |
| **L14** (`design.md:53-56`) | The declared route set. `signed-out` is already in `PUBLIC_ROUTE_PATHS`; T36 adds no route. |
| **L12** | Phase additivity — do not edit what tasks 5, 6, 15, 20, 21, 22, 23, 24, 33 and 35 are built on, beyond what is justified. |

## Files involved

**Read and extend:**

| File | Why |
|---|---|
| `auth/signOut.ts` | T35's clearing half. T36 calls it first. |
| `auth/authorizeRequest.ts:23-29,59-62` | `CLIENT_ID`, the endpoint-path constant pattern, and `redirectUri()` — the worked example of deriving an absolute URL from `location.origin` and a route. |
| `app/routes.tsx` | `APP_BASENAME`, and `'signed-out'` in `PUBLIC_ROUTE_PATHS`. Route names must be read **inside** a function (the import cycle at `routes.tsx:2-5`). |
| `auth/codeExchange.ts:36,76-79` | Where the `id_token` exists. |
| `auth/CallbackScreen.tsx:156-169` | Where it is dropped. **If T36 retains it, this closed screen changes by one line.** |
| `auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | T35's 38 cases. **T36 appends**, and must also extend T35's AC7/AC8 inventory if it adds in-memory state. |

**New:** one module for the end-session URL and the hand-over; and, if OQ-T36-1 resolves that way, one holder
for the `id_token`.

**Not to be touched:** `pwa/*` (T10 built the denylist) · `app/routes.tsx` (no new route) ·
`i18n/catalogue.ts` (no copy) · `api/*` · anything under `spec/`.

## Dependencies

- `signOut()` — `auth/signOut.ts:59`.
- `CLIENT_ID` — `auth/authorizeRequest.ts:26`, the constant `checky-spa`.
- `APP_BASENAME` and the `signed-out` path — `app/routes.tsx`.
- The `id_token`, from `codeExchange.ts`'s result, via whatever holder OQ-T36-1 settles on.
- **No backend call, no contract, no generated client** — the end-session step is a browser navigation, not
  an API request, so **L6** is satisfied vacuously and no `contracts/api/*.yaml` is in scope.
- **Measured, not assumed** (`SasLoginIntegrationTest:475-527`): the URL shape is
  `GET /connect/logout?id_token_hint=<JWT>&post_logout_redirect_uri=<allowlisted>&state=<s>`, the response is
  `302` to `<post_logout_redirect_uri>?state=<s>`, a non-allowlisted target is `400`, and the registered
  default post-logout URI ends `/app/signed-out`.

## Acceptance Criteria

| # | Criterion | Requirement |
|---|---|---|
| **AC1** | Sign-out **clears before it navigates**: T35's `signOut()` runs to completion first, so the hand-over cannot leave a token held. | R19 cl. 1+2 |
| **AC2** | Exactly **one** hand-over occurs, counted across **all four** mechanisms — `assign`, `replace`, the `href` setter and `window.open` — not just the one the code happens to use. | R19 cl. 2 |
| **AC3** | The URL is `/connect/logout` on the SPA's **own origin**, derived from `location.origin`, with no absolute host spelled anywhere. | L9, L10 |
| **AC4** | It carries `post_logout_redirect_uri` equal to `${origin}${basename}/signed-out` — the registered allowlisted value — and that value is **derived from the route table**, not spelled twice. | R19 cl. 2, L14 |
| **AC5** | It carries an `id_token_hint`, which is the only reason any `id_token` is retained. | R19 cl. 2 |
| **AC6** | The parameter set is **exactly** what the proven shape names; no extra parameter, and in particular no access token, account identifier or scope. | R19 cl. 2, L3, L4 |
| **AC7** | A **full-page** navigation, not a router one: no `react-router` import, no `history` use. The destination is SAS's, not the SPA's. | R57, L2 |
| **AC8** | The retained `id_token` is **in memory only** — never in any storage surface, log, console channel or analytics call. | L3, L16 |
| **AC9** | **Sign-out clears the `id_token` too.** T35's inventory grows by one piece of state, and T35's AC7/AC8 scans must account for it rather than be loosened. | R19 cl. 1, L3 |
| **AC10** | The hand-over is reached by no other module: only sign-out may end the SAS session. | R19 |
| **AC11** | T36 adds no route, no catalogue key, no backend call and no storage key. | L14, L11, L6, L16 |
| **AC12** | A tab with **no** `id_token` — a cold tab, or one whose token was adopted from a sibling — still clears and still ends the session as well as it can, without throwing. | R22, R19 |

**AC12 is the one I expect to be argued about.** A tab that adopted a sibling's access token (L18) never ran
a code exchange, so it holds **no `id_token`** and cannot build an `id_token_hint`. Whether such a tab
navigates to `/connect/logout` without a hint (and risks the `400` the backend's second test demonstrates for
a bad redirect target), or skips the navigation and only clears, is a real design decision — phase 2 freezes
it, and phase 3 should attack it.

## Tests required

**Named test:** `signOutClearsMemoryAndEndsSasSession` (`tasks.md:64`) — the same file T35 created, appended
to. T35 arranged its describes one per criterion precisely so this is additive.

Boundary cases implied beyond one per criterion:

- The hand-over counted with the watcher **installed before** the call, plus the control proving a watcher
  installed afterwards sees nothing — "the shape every one of this package's spy mistakes has taken"
  (`loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx:280-282`).
- A tab with no `id_token`: cold, and adopted-from-sibling.
- `APP_BASENAME` with a trailing slash, so `/app//signed-out` cannot be produced — the exact bug
  `redirectUri()` guards against (`authorizeRequest.ts:55-58`).
- The post-logout target compared against the **registered** allowlisted value, so a typo fails here rather
  than as a `400` in a browser nobody is watching.
- A sweep proving no token, no account identifier and no scope reaches the URL, any console channel, or any
  storage surface.

## Open Questions

**OQ-T36-1 — blocking for phase 2. Where does the `id_token` live, and for how long?** The hint is required
by the only proven shape, so something must hold it. Three candidates:

1. **A second module-level holder beside `accessToken.ts`.** Symmetric and obvious. But `accessToken.ts:7-10`
   says in so many words: "do not move it into storage, a store, a React context or a second module, and do
   not copy it anywhere — each of those is a thing sign-out would then have to find and clear." A second
   holder is exactly that, and the comment was written by the task that owns the token.
2. **Inside `accessToken.ts`, beside the access token.** Keeps one module answerable for in-memory token
   state and one place for sign-out to clear. Costs an edit to a closed, heavily-scanned module and widens
   what that module is *about*.
3. **Retain only what the hint needs**, rather than the token. *This is the one I want phase 3 to judge.*
   The `id_token_hint` must be the JWT itself, so nothing smaller suffices — unless T36 can avoid the hint,
   which the measurement in phase 0 could not establish and I will not assume.

**My reading is (2)**, on the argument that `accessToken.ts`'s own comment is an argument against a *second
location*, not against that module holding a second value — and that sign-out having exactly one module to
ask is worth more than keeping a closed file untouched. **Phase 2 freezes it; this is the decision phase 3
should attack first.**

**OQ-T36-2 — not blocking, but it needs a stated position. Does T36 send `state`?** The proven shape includes
it and the backend echoes it back. But a `state` that is sent and never verified is security theatre, and
verifying one means **storing it** — which L16's carve-out, being exhaustive, does not permit: its `state`
entry is the *authorize* state, and reusing that key would collide with a sign-in in flight. **My position:
send no `state`.** OIDC makes it optional, the backend's own test shows it is echoed rather than required,
and task 37's screen has no use for it. Recorded so phase 3 can disagree on the record.

**OQ-T36-3 — carried, the owner's, seventh time.** Nothing in the SPA can trigger sign-out: no authenticated
route has an element, `AppShell.tsx` renders a bare `<div>`, and no task owns an affordance. T36 joins tasks
5, 6, 30, 31, 33 and 35 as built and uncalled. Same substance as **OQ-T33-2** / **OQ-T35-2**.

**OQ-T36-4 — open on the record, deliberately unmeasured.** Whether SAS 1.5.1 honours a
`post_logout_redirect_uri` without an `id_token_hint` (e.g. with a `client_id` instead) is **not answered by
the green backend tests** — both supply the hint. It does not block T36, because the proven shape carries the
hint and T36 will too. It is recorded because it is the question that would let a future task drop the
retained `id_token` entirely, and answering it needs a backend measurement, not a reading.

**Not blockers:** `package.md:123`'s stale `signOutRevokesTokens…` test name, repeated in this task's own
prompt header (third drift instance; `spec/` is read-only here). **Q15** blocks task 38.

**Phase 1 complete.**
