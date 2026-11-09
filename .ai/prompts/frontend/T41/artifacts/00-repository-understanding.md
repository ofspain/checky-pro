<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T41 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Task** | T41 — **Unauthenticated redirect.** An unauthenticated request for any other route redirects to sign-in. Verifies `unauthenticatedRoutesRedirectToSignIn`. Cites **R28** |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

No code written, no design, no requirements extraction — that is phase 1.

---

## 1 · Architecture summary

`frontend/` is a React 19 + TypeScript SPA built with Vite, mounted under the `/app` basename (L14), talking
to the platform through a single origin (L9). There is **no persistence layer and no outbox** — the words in
the prompt's deliverable list come from the backend template. What stands in for them:

| | |
|---|---|
| **Modules** | `app/` owns the router and the shell (L12, L13) · `auth/` owns the OIDC client, the session and now the route guard (`design.md:203`) · `api/` owns the generated-client boundary, the session boundary and the cross-tab channels · `i18n/` owns every user-facing string (L11) |
| **"Persistence"** | **memory only** for tokens (L3) and a single `sessionStorage` carve-out (L16): `state`, `nonce`, `code_verifier` and **the post-login return target**, single-use and deleted at callback. Nothing else. |
| **"Events"** | `BroadcastChannel` for cross-tab renewal and sign-out (L18) — `api/crossTabRenewal.ts`, `api/crossTabSignOut.ts` |
| **Security** | public OIDC client with PKCE, no secret in the bundle, `state`/`nonce` validated, **allowlisted** post-login return target falling back to home (L1) · interactive login is SAS-hosted (L2) · enumeration-safe copy on every unauthenticated surface (L4) |

The auth gate itself landed **one task ago**: `auth/RequireSession.tsx` (T40) renders `<Outlet />` when
`Boolean(accessToken.current())` and `null` otherwise, applied **once** as the pathless layout route whose
children are the eleven `AUTHENTICATED_ROUTE_PATHS`. T41 is the second half of the same requirement.

## 2 · Existing code this task touches

### Exists, and T41 will read or change it

| | |
|---|---|
| `app/routes.tsx:74` | the pathless gate route, `{ element: <RequireSession />, children: … }` |
| `app/routes.tsx:78` | `{ path: '*' }`, the catch-all, **element-less on purpose**, with its own comment saying *"T41 replaces it with the sign-in redirect"* and that it was kept **outside** the gate so a sessionless visitor can reach it |
| `auth/RequireSession.tsx:42` | `return hasSession ? <Outlet /> : null;` — the `null` is the behaviour T41 changes, or does not |
| `auth/returnTarget.ts` | **already built** (T24/T25): `RETURN_TARGET_KEY = 'themistra.auth.return_to'`, `rememberReturnTarget`, `takeReturnTarget`, `isAllowlistedTarget`, `clearReturnTarget`. `rememberReturnTarget` **applies the allowlist itself and silently ignores a target it cannot honour**, so a caller needs no branch |
| `auth/cleanReauth.ts:68-96` | `createRenewalFailureHandler` — the closest existing precedent: clear, `inAppPath()`, `rememberReturnTarget(here)`, `setSignInNotice('session-ended')`, `navigate('/sign-in')`, in that documented order |
| `auth/signInNotice.ts` | `setSignInNotice` / `takeSignInNotice` / `NOTICE_COPY_KEY` — a single-read notice the sign-in screen displays |
| `app/routes.tsx:14` | `SIGN_IN_PATH = 'sign-in'` — one spelling, already imported by three modules |
| `auth/SignInScreen.tsx:38` | the sign-in **start**: `globalThis.location.assign(url)` into `/oauth2/authorize` |

### New

- The named test `unauthenticatedRoutesRedirectToSignIn` — **the file does not exist**. Confirmed by listing
  `src/app/*.test.tsx` and `src/auth/*.test.tsx`. Task **42** is given the *same* test name in
  `tasks.md:72`, so T41 creates the file and T42 extends it — the shared-ownership pattern this package has
  used twice (T16/T40 on `routesResolveUnderAppPrefixOnly`, T37/T40 on `publicRouteSetIsExactlyTheDeclaredSet`).
- Whether any **production module** is new is a phase-2/3 question, not this one.

### Instruments of closed tasks that T41's change will fire

T40 wrote several of these *deliberately*, so the handoff is visible rather than silent. This is an
inventory, not a plan:

| | instrument | why it fires |
|---|---|---|
| `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:990` | *"leaves the location where it was on a BLOCKED path too, rather than redirecting (**R28, task 41**)"* — `historyAction === 'POP'` | this case **names T41** and exists to fail when the redirect lands |
| `…:906`, `:913`, `:1120` | T40 AC2 — `container.innerHTML === ''` on a blocked path | a redirect renders the sign-in route instead of nothing |
| `…:980`, `:1110` | T40 AC10 / boundary — `historyAction === 'POP'` on a **public** path | must **keep** passing: a public path must not be redirected |
| `…:967` | T40 AC1 — the catch-all's probe renders without a session | fires if the catch-all gains an element |
| `routesResolveUnderAppPrefixOnly.test.tsx:129` | *"sends an unmatched /app path to the silent catch-all, so the shell shows no text"* | the catch-all stops being silent |
| `…:137` | *"treats the bare /app/ as no route: only the root matches and nothing renders (**T41 owns its redirect**)"* | also names T41 |
| `…:175` and `publicRouteSet…:429` | the two screen-count pins, both at **three** | fire **only if** the redirect is an element on a route that has a `path`. T40's gate avoided them by being pathless; the catch-all is not |
| `failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx:518` | task 33's *"every authenticated path has no element"* | fires only if T41 puts an element on each authenticated path |

## 3 · Established patterns to follow

| | |
|---|---|
| **`replace`, not push, for a redirect** | T24/T25 pin `historyAction === 'REPLACE'` for the callback's navigation (`returnTargetOutsideAllowlistFallsBackToHome.test.tsx:599`, `:1406`) so the visitor cannot bounce back into a consumed callback. The same argument applies to a gate redirect |
| **One spelling** | every path comes from `routes.tsx`; no module re-spells `sign-in`. Route constants are read **inside** functions, never at module scope — T16 measured that a module-scope read yields `undefined` rather than throwing, which fails closed *silently* (`returnTarget.ts:40-49`, `cleanReauth.ts:88-91`) |
| **Remember the location, not the task's state** | `cleanReauth.ts:78-81`: `rememberReturnTarget` preserves where the visitor was; what a screen shows after the return is that screen's business |
| **The allowlist is the gate on the return target** | `rememberReturnTarget` refuses anything outside `AUTHENTICATED_ROUTE_PATHS`, so an unmatched path or a public path remembers nothing, with no branch at the call site |
| **Order is load-bearing and documented** | `cleanReauth.ts:70-74` explains why clearing happens first. T41's own ordering, if it has side effects, is expected to carry the same reasoning |
| **Enumeration-safety (L4)** | copy, redirects, styling and timing identical across account and token states on every unauthenticated surface. A redirect that said *why* would breach it |
| **Catalogue-only strings (L11)** | a redirect that renders nothing needs no catalogue entry; one that renders a message does |
| **Injected navigation outside React** | `cleanReauth.ts` takes `navigate` as a parameter because it runs outside a component, and that also keeps `auth/` from importing the router and closing a cycle (`routes.tsx:2-7` documents one such cycle) |

## 4 · Testing conventions

- **Vitest + jsdom**, run as `npx -y pnpm@12.14.0 exec vitest run` (bare `pnpm` is not on this host). **The
  exit code is the verdict, not the pass counts.** No Testcontainers, no fixed `Clock`, no ArchUnit — the
  structural equivalents are **source scans** with a paired probe per pattern and a control that fires every
  one, plus **count pins** and **tree-shape pins** on the route table.
- Suites mount **the real route table** through `createMemoryRouter` with the `/app` basename, so a routing
  claim is a claim about routing and not about React. `StrictMode` arms are standard.
- **Mutation harness per task** (`artifacts/07-mutation-harness.py`), which reverts with
  `git checkout -- frontend/src/` — **tests must be committed before it runs.** Its expectation field now
  claims *killed by a case this task owns*, so a kill landing only in an unrelated suite is a disagreement,
  not cover.
- One **known environmental failure**: `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, because this
  host's `oxlint@1.87.0` prints no summary line. Whole-suite baseline today: **1744 / 1745**.
- **A shared test file can hold two tasks' AC numbering.** T40 found its describes colliding with T37's in
  one file and prefixed its own `T40`. T41 writes a file T42 will extend, so the same discipline applies from
  the first case.

## 5 · Known gaps and unknowns

| | |
|---|---|
| **Where the redirect belongs** | R28's *"any other route"* covers two different things — the eleven authenticated paths **without** a session, and paths that match **nothing**. They are reached by different mechanisms (the gate vs the catch-all) and nothing in the spec says whether one module answers both. **Phase 1/2's question**, and the instruments in §2 are what will make each choice visible |
| **Whether the redirect remembers a return target** | L1 mandates an allowlisted post-login return target and L16(a) permits storing it, and `rememberReturnTarget` is built — but **no requirement says the unauthenticated redirect must populate it.** I do not know whether the author intends it; phase 1 must read R28 and L1 together and say so explicitly rather than inferring |
| **Whether the count pins must grow** | if the redirect is an element on the catch-all, both screen-count pins go from three to four, and a redirect is not a screen. T40 kept its guard off those pins by being pathless; the catch-all has a path, so that escape does not exist. **Phase 3's to settle** |
| **What a redirected visitor sees on the way** | L11 requires managed focus "across redirect and MFA steps". `SignInScreen` already manages its own focus; whether a gate redirect has any visible moment at all depends on whether it renders anything before navigating. **I do not know** and will not guess |
| **The screen is still dormant** | there is no authenticated screen to be redirected *away from* — task 42 is blocked on 173/174 — so T41, like the ten tasks before it, will be verified against the route table and probes rather than a real screen |
| **Spec drift, unchanged and still the owner's** | `design.md:137` reads *"PROPOSED … pending author confirmation (Q6)"* while `package.md:368` records Q6 resolved on 2026-10-10 |

**Phase 0 complete.** No code, no design, no requirement extraction.
