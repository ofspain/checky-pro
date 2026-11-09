<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T41 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

No design, no implementation. Three of phase 0's four unknowns are **answered here from the spec and the
source**; the fourth is reduced to one genuine open question with a recommendation.

---

## 1 · Business rules

| ID | |
|---|---|
| **R28** | *"WHILE a visitor is unauthenticated, THE SPA SHALL serve only the public routes listed verbatim in `design.md` §4c and SHALL **redirect any other route to the sign-in start**."* (`requirements.md:99-100`) Task 40 built the first clause; **T41 is the second.** |
| R19 | The signed-out landing is one of the nine public routes, so a visitor sent there must **not** be redirected — it is a destination, not a gap. |
| R29 | If a session expires *during a task*, non-sensitive form input survives with a 15-minute TTL. **Not this task** — there is no form here — but it is the requirement that a future mid-task redirect must respect, and it is why T41 must not clear storage it does not own. |

### What "any other route" denominates — settled from R28's own text

R28 says *only the nine public routes* are served and *any other route* is redirected. The complement of the
nine, inside `/app`, is **three** kinds of arrival, not one:

| | arrival | reached by |
|---|---|---|
| **(a)** | one of the eleven `AUTHENTICATED_ROUTE_PATHS` with no session | the gate route — `RequireSession` renders `null` today |
| **(b)** | a path matching **nothing** (`/app/no-such-route`) | `{ path: '*' }`, the catch-all, which T40 deliberately left **outside** the gate |
| **(c)** | the bare `/app/` | matches the root alone; `routesResolveUnderAppPrefixOnly.test.tsx:132-142` pins that it renders nothing and names *"T41 owns its redirect"* |

All three are "any other route" under R28, so **all three are in scope.** That they are reached by different
mechanisms is an implementation fact, not a scoping one — and it is the reason phase 0 could not tell whether
one module answers all three. **The requirement does not care; the three arrivals must each end at the
sign-in start.**

## 2 · Locked decisions that constrain this task

| ID | | what it constrains here |
|---|---|---|
| **L14** | the route set is verbatim §4c: nine public, eleven authenticated; *"Everything else requires a session"*; the `/app` prefix is disjoint from every edge prefix | the nine must keep being served; no route may be added or re-spelled; a redirect must not resolve outside `/app` |
| **L1** | public OIDC client with PKCE, *"**allowlisted post-login return target, falling back to the home route**"* | **this is the decision that answers phase 0's second unknown — see §5** |
| **L16** | the sessionStorage carve-out may hold *"(a) `state`, `nonce`, `code_verifier`, **and the post-login return target**, single-use and deleted at callback"* — *"This list is the entire carve-out"* | a return target written here uses the **existing** `RETURN_TARGET_KEY`; **no new key may be added** |
| **L4** | enumeration-safe UI on every unauthenticated surface: copy, **redirects**, styling and timing identical across account and token states | the redirect must be identical for every arrival. It may not say *why*, and it may not differ between "you have no session" and "that route does not exist" in any way a prober could read |
| **L11** | WCAG AA, keyboard operation, **managed focus across redirect and MFA steps**, catalogue-only strings | see §5 — the focus obligation for the landing is **task 174's**, by `package.md:168` |
| **L12** | phase additivity: later phases add routes, they do not replace the Phase 1 shell, routing or auth model | T41 changes behaviour **inside** the existing table; it does not restructure it |
| **L13** | React Router is the confirmed router | the redirect is expressed in the router's own terms, not by reaching for `location` |
| **L9** | single origin, all traffic through the edge path map | the sign-in start is already `SignInScreen` → `/oauth2/authorize` on the same origin; T41 adds no URL |
| **L15** | the auth gate must precede payment surfaces | four of the eleven gated paths are payment surfaces, so T41 completes the gate they wait on |
| **L3** | tokens in memory only | nothing here reads or writes a token except by asking `accessToken.current()` |

## 3 · Files involved

### To read / extend (all exist)

| | |
|---|---|
| `app/routes.tsx` | `:74` the pathless gate route · `:78` the element-less catch-all, whose comment already says *"T41 replaces it with the sign-in redirect"* · `:14` `SIGN_IN_PATH` |
| `auth/RequireSession.tsx` | `:42` `return hasSession ? <Outlet /> : null;` — the `null` is arrival **(a)**'s current answer |
| `auth/returnTarget.ts` | `rememberReturnTarget` (`:88`) applies the allowlist itself and silently ignores what it cannot honour; `RETURN_TARGET_KEY` (`:39`) is the only key L16 permits |
| `auth/cleanReauth.ts` | `inAppPath()` (`:43`) already converts `globalThis.location.pathname` to a basename-relative path and normalises the bare `/app` to `/`; `createRenewalFailureHandler` (`:68`) is the precedent for the whole sequence |
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` · `app/routesResolveUnderAppPrefixOnly.test.tsx` · `auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx` | the instruments phase 0 inventoried; three of them **name this task in their own titles** |

### New, expected by the spec

- **`unauthenticatedRoutesRedirectToSignIn`** — `package.md:151` maps it to R28 and **no file by that name
  exists**. T41 creates it. `tasks.md:72` gives **task 42** the same test name, so T41 writes the file task 42
  will extend, and **prefixes its own describes** from the first case (the collision T40 found between its
  describes and T37's in one shared file).
- Whether any new **production** module is needed is phase 2/3's call, not this phase's.

## 4 · Dependencies

| | |
|---|---|
| **Modules** | `auth/accessToken.ts` (`current()`) · `auth/returnTarget.ts` (`rememberReturnTarget`, `isAllowlistedTarget`) · `auth/cleanReauth.ts` (`inAppPath`) · `app/routes.tsx` (`SIGN_IN_PATH`, `APP_BASENAME`, the two path arrays, `appRoutes`) · `react-router` |
| **Consumer already built** | `auth/CallbackScreen.tsx:201` calls `takeReturnTarget()` and navigates there, falling back to home. **The return-target path has a reader and, in production, exactly one writer** — `cleanReauth.ts:81`. Verified by grep. |
| **No new** | endpoint · contract · config key · storage key · catalogue entry (unless the redirect renders copy, which §5 argues against) · event |

## 5 · Phase 0's unknowns, answered

### (i) Where the redirect belongs — **scope settled, mechanism still open**
All three arrivals in §1 are in R28's scope. *Which module answers each* is phase 2/3's, and the evidence is
already laid down: `routes.tsx:78`'s own comment assigns the catch-all to T41, and
`RequireSession.tsx:17-22` says the redirect for a blocked route is T41's. **Nothing in the spec requires one
module to answer all three** — and nothing forbids it.

### (ii) Does the redirect remember the return target? — **YES, and L1 is why**
L1 locks *"allowlisted post-login return target, falling back to the home route."* The machinery exists
(T24/T25) and the callback already reads it. In production it has **exactly one writer**,
`cleanReauth.ts:81`, on renewal failure — so today a visitor who follows a deep link to `/app/invoices/123`
with no session would sign in and land on **home**, with the link discarded. That is the case L1's allowlist
was built for, and arrival **(a)** is the only place that can populate it. **T41 populates it**, using
`rememberReturnTarget`, which applies the allowlist itself.

Two consequences fall out of the allowlist rather than needing a decision: arrival **(b)** remembers
**nothing** (an unmatched path is not in `AUTHENTICATED_ROUTE_PATHS`), and arrival **(c)** remembers nothing
(`inAppPath()` normalises the bare `/app/` to `/`, which `rememberReturnTarget` rejects —
`cleanReauth.ts:36-40` says so explicitly). No branch is needed at any call site, and **L4 is satisfied
because the visible outcome is identical in all three cases.**

### (iii) Managed focus — **not this task's, by `package.md:168`**
`package.md:168` maps `homeRouteRendersLandingScreenAndManagesFocus` → **R28, L11**, and that test belongs to
**task 174**. The focus obligation for what the visitor lands on is therefore assigned elsewhere, and
`SignInScreen.tsx` has **no** focus management today (only an `inFlight` ref) — a pre-existing gap owned by
task 16's surface, recorded here, **not T41's to fix**. T41's own contribution renders nothing of its own, so
it has no focus to manage.

### (iv) Whether the screen-count pins must grow — **still open, and it is a real design question**
If the redirect is an element on the catch-all, `routesWithElements` and T37's AC9 both go from three to
**four**, and *a redirect is not a screen*. T40's gate escaped those pins by being **pathless**; the catch-all
has a path, so that escape does not exist. Carried to phase 2 and flagged for the phase-3 challenge.

## 6 · Acceptance criteria

| | | ID |
|---|---|---|
| **AC1** | **(a)** A sessionless arrival at any of the eleven authenticated paths ends at the **sign-in start**, not at a blank screen. | R28 |
| **AC2** | **(b)** A sessionless arrival at a path matching nothing ends at the sign-in start. | R28 |
| **AC3** | **(c)** A sessionless arrival at the bare `/app/` ends at the sign-in start. | R28 |
| **AC4** | The nine public routes are **still served** and **still not redirected** — including the signed-out landing (R19), with a query string, and with a trailing slash. | R28, L14, R19 |
| **AC5** | **With** a session, an authenticated path is **not** redirected. The redirect is the unauthenticated half of the gate, not the gate. | R28 |
| **AC6** | The redirect **replaces** rather than pushes, so the visitor cannot bounce back into the route that refused them. | the package's own pattern (`returnTargetOutsideAllowlistFallsBackToHome.test.tsx:599`, `:1406`) |
| **AC7** | **(a)** remembers the arrival as the post-login return target, through `rememberReturnTarget`; **(b)** and **(c)** remember nothing — by the allowlist, not by a branch. | **L1**, L16 |
| **AC8** | **No new storage key.** `sessionStorage` holds only L16's items; the return target uses `RETURN_TARGET_KEY`. | **L16** |
| **AC9** | The three arrivals are **indistinguishable** to the visitor: same destination, same history action, same rendered output, no copy naming a cause. | **L4** |
| **AC10** | The redirect adds **no catalogue entry** and renders no copy of its own. | L11 |
| **AC11** | One spelling: the destination is built from `SIGN_IN_PATH`, read **inside** a function, never at module scope. | the package's own pattern (T16's measurement) |
| **AC12** | Instruments of tasks 16, 33, 37 and 40 are **grown, not bypassed** — in particular the three whose titles name this task. | the handoff pattern |
| **AC13** | The route **set** is unchanged: still nine public, eleven authenticated, one catch-all, and `pathTree`'s shape holds except where the redirect provably changes it. | L14, L12 |

## 7 · Tests required

**Named:** `unauthenticatedRoutesRedirectToSignIn` → R28 — **a new file**, which task 42 will extend.

**Boundaries implied:** each of the eleven authenticated paths, including the two with an `:id` segment ·
an unmatched path, and one that *looks* like a public path but is not (`/app/sign-in-x`) · the bare `/app/`
with and without the trailing slash · each of the nine public paths, unredirected · a public path with a
query string, and the query surviving the non-redirect · an authenticated path **with** a session · the
return target after each of the three arrivals · `historyAction` on every arrival · `StrictMode`.

**Not this task's:** `publicRouteSetIsExactlyTheDeclaredSet` (task 40, closed — but its instruments fire
here) · `homeRouteRendersLandingScreenAndManagesFocus` (task 174) · the authenticated shell (task 42).

## 8 · Open questions

**One genuine design question, and no blockers.**

1. **Does the catch-all redirect an *authenticated* visitor too?** R28 is conditioned *"WHILE a visitor is
   unauthenticated"*, so a signed-in visitor at `/app/no-such-route` is **outside R28 entirely**, and
   `tasks.md:72` (task 42) says only that *"a session renders the shell and the authenticated routes"*.
   Nothing in the spec answers it. **Recommendation, for phase 2 to state and phase 3 to challenge:** treat
   it as out of scope and keep today's behaviour — render nothing — so T41 adds no claim the spec does not
   make. **Not a blocker:** every acceptance criterion above is about the unauthenticated case.
2. **The screen-count pins** (§5 iv) — a design question, carried to phase 2, not a spec gap.
3. **Spec drift, unchanged and the owner's:** `design.md:137` still reads *"PROPOSED … pending author
   confirmation (Q6)"* while `package.md:368` records Q6 **resolved 2026-10-10**. Recorded for the fourth
   consecutive task; `spec/` is not modified here.

**Phase 1 complete.** Three of phase 0's four unknowns answered from the spec and the source; one design
question and one recorded drift remain.
