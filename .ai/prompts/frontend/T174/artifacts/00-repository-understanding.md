<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T174 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T174 — Home screen |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement:** *"`/app/home` renders a landing screen and moves focus to it on arrival, so a completed
sign-in lands somewhere rather than on a blank document. Verifies
`homeRouteRendersLandingScreenAndManagesFocus`. Cites R28, L11, L1. Execute before 42."*

## 1 · Architecture, as it actually stands

This is a browser SPA, not a service: **no persistence, no events, no outbox, no transactions.** The phase-0
boilerplate questions about JPA, Flyway and resource-server security have no referent here, and saying so is
more useful than answering them about something else.

| | |
|---|---|
| **Stack** | Vite 8 · React 19 · TypeScript · react-router 8.4 under the `/app` basename · `react-i18next` · Vitest 5 + jsdom · `vite-plugin-pwa` |
| **State** | module-level singletons, deliberately. `accessToken` is held **in memory only** (tokens are never written to any storage, R58/L10); `sessionStorage` holds exactly the four L16 items |
| **Security posture** | public OIDC client, PKCE, no client secret in the bundle (**L1**); `state` and `nonce` validated; the post-login return target is allowlisted and **falls back to the home route** |
| **Routing** | one table in `app/routes.tsx`: an index route, nine public paths as direct children, eleven authenticated paths under **one pathless `RequireSession` gate**, and a catch-all. Three of the three public screens are attached; **no authenticated route has an element** |
| **Presentation** | as of T173: one stylesheet, eight primitives in `app/ui/`, and five render sites that contain no bare element |

## 2 · What exists that this task touches

| | status |
|---|---|
| `app/routes.tsx` — `HOME_PATH = 'home'`, first of `AUTHENTICATED_ROUTE_PATHS` | **exists**, as a path with **no element**: `{ path }` only |
| `auth/RequireSession.tsx` — the gate | **exists** (T40). Renders `<Outlet />` with a session, `<RedirectToSignIn />` without |
| `auth/returnTarget.ts` — allowlist, falling back to `HOME_PATH` | **exists** (T24/T35). **`/app/home` is already where a completed sign-in is sent** |
| `app/ui/` — `Screen`, `Heading`, `Text`, `FocusHolder` … | **exists** (T173). A landing screen is built from these, not from bare elements |
| `i18n/catalogue.ts` — `rateLimit`, `signIn`, `callback`, `signedOut` | **exists**; there is **no `home` group**. New copy is new catalogue entries |
| `HomeScreen` | **does not exist** |
| `homeRouteRendersLandingScreenAndManagesFocus` | **does not exist**; named in `package.md` §8 and commissioned by this task |

**So the state this task starts from is precise and slightly absurd:** the SPA already computes where a
completed sign-in should land, already defends that route with a session gate, and already renders **nothing**
when the visitor arrives. `/app/home` resolves, passes the gate, and produces a blank document. That is the
"blank document" the task statement names, and it is the whole of the gap.

## 3 · Established patterns this task must follow

1. **Screens are attached in `routes.tsx` by their own task**, one element on one path. The table's comments
   say so and two count pins enforce it.
2. **Every screen goes through the primitives** (T173). `Screen` is the `<main>`, `Heading` the single `<h1>`;
   `className` and `style` are absent from the primitive types, so a screen that wants to style itself is a
   compile error.
3. **All copy comes from the catalogue** (L11, O3), and nothing in the package spells a user-facing string in
   a component.
4. **Focus is moved with a ref to a `tabIndex={-1}` element.** `FocusHolder` exists for exactly this and is
   consumed today only by `RateLimitNotice`; `app/ui/Screen.tsx` forwards its ref, as do all eight primitives.
   **I do not yet know** whether T174 should focus the `<main>`, the `<h1>`, or a holder — that is phase 2/4's
   to decide, and the three are not equivalent for a screen reader.
5. **Module boundaries**: `auth/` modules never import screens; `app/routes.tsx` imports screens and reads
   constants inside functions to keep a known import cycle resolvable.

## 4 · Testing conventions

Vitest + jsdom, no Testcontainers and no `Clock` (neither concept exists here). The conventions that matter:

- **One named test per task**, the file `package.md` §8 names, plus growth of whatever closed instruments the
  change makes false.
- **A mutation harness per task** (`.ai/prompts/frontend/T<n>/artifacts/07-mutation-harness.py`), now in its
  tenth copy. It reverts with `git checkout` over `frontend/src/` **and** `frontend/vite.config.ts`, so tests
  must be committed before it runs.
- **Controls as well as mutants**: a harness measures strength, only a control measures precision.
- **Count pins are handoffs, not obstacles.** Two already name this task's successor in their comments.

## 5 · The blast radius, named in advance

Not measured yet — that is phase 4's job, and T173's phase 4 proved the difference between a grep (24 suites)
and a measurement (one). But three instruments are *designed* to fire here, and their firing is the handoff
working:

| | instrument | why |
|---|---|---|
| **1** | `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:688` — *"attaches an element to sign-in, callback and signed-out, and to nothing else"* | its own comment: *"A deliberate COUNT PIN, and it will fail the day the next screen is attached — which is the handoff working."* T174 attaches the fourth |
| **2** | `routesResolveUnderAppPrefixOnly.test.tsx:161` | the same total against a hand-edited list |
| **3** | `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` — `RENDER_SITES` pinned at **5**, the union at **13** | T173 froze the list so *"a sixth screen must be added to its list deliberately"*. T174 is the sixth. **This is the first test of whether that pin was built right**, and it is worth watching rather than just updating |

T40's AC13 (elements on pathless routes) should **not** fire: the home element goes on a route **with** a
path, inside the gate's children.

## 6 · Known gaps and things I do not know

- **There is no `home` copy anywhere in the package.** The catalogue has no `home` group, and `design.md` §4c
  lists `/app/home` as a path with no description of what it shows. What a landing screen *says* is not
  specified — the same shape as OQ-T173-3, where no visual direction existed. **I do not know** what this
  screen is meant to contain beyond "somewhere to land", and phase 1 must not invent a dashboard.
- **`phase1ScreensPassAccessibilityChecks`** is listed in this task's named tests and is commissioned by no
  task (OQ-T173-4, still open). T174 cites L11 and manages focus, so it is the closest any task has come to
  that test's subject — **I do not know** whether the owner intends T174 to claim it.
- **Nothing in `src/` renders an authenticated screen yet**, so there is no precedent for what a screen
  *inside* the gate looks like. T174 sets it, and task 42 ("authenticated shell") is blocked on it.
- **The whole authenticated data path is dormant** (OQ-T33-2): nothing calls `createAuthApi`, `readAccount`
  or `readProfile`. A landing screen that fetched anything would be the first consumer, which is almost
  certainly out of scope but should be ruled out explicitly rather than by omission.
- **L13's TanStack Query and Zustand are still not installed** (OQ-T173-5). If a landing screen were
  specified to show data, that gap would stop being theoretical. On the current reading it does not.

**Phase 0 complete.** No code written. The gap is exact: `/app/home` resolves, passes the gate, and renders
nothing.
