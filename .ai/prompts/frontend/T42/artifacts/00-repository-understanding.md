<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T42 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` · **Task** T42 — Authenticated shell |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

> **Task 42 is unblocked as of today.** Its line reads *"Blocked on 173, 174"*; T173 closed PASS 2026-11-12
> and T174 closed PASS 2026-11-14. By document order in `tasks.md` it is the next unit — task 38, which sits
> above it, is still *"Blocked on Q15"*.

## 1 · Architecture summary

A React 19 / TypeScript SPA built by Vite 8, served under the `/app` basename (L14), talking to four Spring
Boot services through one edge (L9). There is **no persistence layer in this service** — no JPA, no Flyway,
no outbox, no Kafka consumer. The prompt's template asks about those; for `frontend` the honest answer is
that they do not exist, and the equivalents are:

| backend concern | the SPA's equivalent |
|---|---|
| persistence | **none.** `sessionStorage` holds exactly the L16 carve-out: `state`, `nonce`, `code_verifier`, the return target. The access token is a module-scoped variable in `auth/accessToken.ts` and is written nowhere (L3) |
| events / outbox | **none in Phase 1.** Cross-tab coherence is a `BroadcastChannel` (L18): `auth/crossTabRenewal.ts`, `auth/signOut.ts` |
| resource-server security | the SPA is a **public OIDC client with PKCE** (L1). Authorization happens in SAS (L2); the SPA holds a bearer token in memory and gates routes on *"is a token held"* |
| error handling | per-screen, with enumeration-safe uniform copy (L4) and catalogue-only strings (L11) |
| configuration | `vite.config.ts` + the public env keys in §4c only (L10) |

Module layout, matching `design.md:202`:

```
src/
  app/     shell, providers, the router under /app, the design system in app/ui/
  auth/    PKCE, callback, token, renewal, sign-out, the route gate
  api/     the generated client's seam and the L6 lint rule
  i18n/    the catalogue (one file, English only today)
```

**The route table is one file**, `app/routes.tsx`: four exported path constants, `PUBLIC_ROUTE_PATHS` (nine),
`AUTHENTICATED_ROUTE_PATHS` (eleven), and `appRoutes`, which is the single source of the route set.

## 2 · Existing code this task touches

**Already exists — and more of it than the task statement suggests.**

| | state today | evidence |
|---|---|---|
| `app/AppShell.tsx` | **the shell exists and already wraps every route**, authenticated or not. 3 lines: `<Surface data-testid="app-shell">{children}</Surface>` | `app/AppShell.tsx:12-14`; mounted at `app/mountShell.tsx:26-34` with the router as its child |
| `app/routes.tsx` | the pathless gate route is in place with all eleven authenticated paths as its children | `app/routes.tsx:88-94` |
| `auth/RequireSession.tsx` | the gate: `Boolean(accessToken.current())` → `<Outlet />` or `<RedirectToSignIn />` | `auth/RequireSession.tsx:41-46` |
| `auth/RedirectToSignIn.tsx` | owns the destination, the return target and replace-vs-push (T41) | — |
| `app/HomeScreen.tsx` | **the only authenticated route with an element.** The other ten carry `{ path }` and nothing else | `app/routes.tsx:92-93` |
| `app/ui/` | eight primitives, `className`/`style` absent from every props type (T173) | `app/ui/index.ts:25-32` |
| `i18n/catalogue.ts` | five groups: `rateLimit`, `signIn`, `callback`, `signedOut`, `home`. **No navigation group** | `i18n/catalogue.ts` |

**The named test already exists, and its header hands this task the file.**
`auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` is 591 lines and 35 cases, and its first line reads
*"R28's SECOND clause — **TASK 41 OWNS THIS FILE, AND TASK 42 EXTENDS IT**"*. Its phase-3 F13 decision
prefixes every `describe` with the owning task *"because task 42 adds authenticated screens behind the gate
and will extend this file, and a failure naming 'AC1' must say whose AC1 it is"* — T40's phase 12 found that
collision after the fact. So this task **grows an inherited suite under a reserved naming convention**; it
does not create the named test.

**Instruments that will see anything this task renders**

| instrument | what it pins | why T42 touches it |
|---|---|---|
| `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:83-91` | `RENDER_SITES`, exactly six, and a both-directions derivation from barrel imports | a new shell component that renders primitives becomes the **seventh** render site, and the barrel-importer derivation added by T174 will catch it whether or not it has a lowercase tag |
| `app/shellBuildsAndMounts.test.tsx:102-107` | the `app-shell` element's `innerHTML` is `''` with nothing matched | holds only while the new chrome renders *inside* the gate, not inside `AppShell` |
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:718` | the attached-element list, `['sign-in', 'callback', 'signed-out', 'home', '*']` | a layout element behind the gate is pathless, so this pin **cannot see it** — T40's AC13 is the pin for an element on a pathless route |
| `app/routesResolveUnderAppPrefixOnly.test.tsx:18` | `SCREENS_ATTACHED_SO_FAR` | same question |
| `auth/loginChallenge…test.tsx:1490` | the discharged T27-D1 case: a real sign-in lands on `/app/home` and focuses its heading | **anything wrapped around the home screen must not move or steal that focus** |
| `app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | 29 cases, including *"no OTHER screen takes focus, now that every heading can"* and `main.children → ['H1','P']` | chrome containing its own `<h1>` would break the one-`<h1>` assertion; chrome inside `<main>` would break the child-list pin |

**New in this task:** whatever renders the authenticated chrome, plus its catalogue entries, plus the growth
of the pins above. **Nothing else is new** — no route, no endpoint, no contract, no config key.

## 3 · Established patterns to follow

- **The route table is declarative and additive.** Screens are attached by their own tasks; a path with no
  element stays that way deliberately, *"because attaching a placeholder would make the screen-count pins
  stop meaning anything"* (`app/routes.tsx:84-87`).
- **One layout route, not N elements.** T40 chose a pathless layout route for the gate precisely so that
  *"a screen attached later by task 42 nests INSIDE the gate rather than replacing it"*
  (`auth/RequireSession.tsx:9-13`). The same shape is the obvious home for authenticated chrome.
- **A component reads route state inside its body, never at module scope.** `app/routes.tsx:1-9` records the
  import cycle this protects, and the sign-in suite pins it.
- **No copy in a component.** Every user-facing string is a catalogue entry (L11), asserted both against the
  entry and as literal English.
- **Screens render through `app/ui` primitives**, never bare elements; `className` and `style` are not
  accepted by any primitive, and `tsc` through a real `pnpm run build` is what enforces it.
- **An ARIA contract a consumer can override is not a contract** (`app/ui/index.ts` rule 2): `role`,
  `aria-live`, `tabIndex`, `type="button"` and the heading level are set **inside** the primitive. T174 obeyed
  this literally rather than amending it.
- **One `<h1>` per screen, focus moved to it on arrival** (T174, L11).
- **Grow an inherited instrument, never loosen it.** The rule in force: editing a closed task's test is
  permitted when the instrument has become *false*, or must grow to *admit new state*; forbidden when it is
  merely *incomplete for something the new task owns*.

## 4 · Testing conventions

Vitest 5 + jsdom. **No Testcontainers, no fixed `Clock`, no ArchUnit** — none of the three exists in this
service; the structural equivalent of ArchUnit is a **source scan inside a normal test case** (forbidden
imports, forbidden patterns, pinned lists), which several suites use and which the mutation harness measures.

- `npx -y pnpm@12.14.0 exec vitest run` — bare `pnpm` is not on this host. **The exit code is the verdict**,
  not the pass count.
- Suites that mount React use `createRoot` + `act`, and most set
  `IS_REACT_ACT_ENVIRONMENT = true` at module scope (11 of 18; T174 added it to its own file at phase 11
  after finding that an isolated run and a whole-suite run were otherwise not the same measurement).
- Each task writes a **mutation harness** as `artifacts/07-mutation-harness.py`. T42's would be the twelfth
  copy. Standing rules: commit tests before running it (it reverts with `git checkout` over `REVERT_PATHS`);
  a mutant that cannot compile is BROKEN, not killed; a mutant must measure its own description; score
  canaries apart; a control measures precision where a mutant measures strength.
- **Known environmental failure**, present and unrelated: one case of 33 in
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts` fails because this host's `oxlint@1.87.0` prints no
  summary line. Whole suite today: **1862 passed, 1 failed of 1863**. The same file holds the repository's
  only end-to-end build gate, which runs a real `pnpm run build` and passes.

## 5 · Known gaps / unknowns

1. **The spec package does not say what the authenticated shell contains.** I searched all five files for
   *shell*: `design.md:50` (L12, *"do not replace the Phase 1 shell"*), `design.md:202` (the directory
   comment), `design.md:194` and `requirements.md:205` (service-worker cache), `agents.md:88` and
   `agents.md:100`. **Not one of them specifies navigation, a header, a sign-out control, or any chrome.**
   There is no requirement ID for an authenticated navigation surface, and R28 — the only requirement this
   task cites — is entirely about *which routes are reachable without a session*. What "the shell" means
   beyond what `AppShell.tsx` already does is therefore **not specified**, and it is Phase 1's job to extract
   that and Phase 3's to challenge it. I will not guess here.
2. **"and the authenticated routes" cannot mean "attach screens to them."** Ten of the eleven are owned by
   later units — `/app/account/mfa` by tasks 66–73, `/app/api-keys` by 74–80, and so on — and `tasks.md`
   says *"Execute in order"*. Whether T42 must make the ten *reachable* in some observable sense beyond
   matching is a real question, and I do not know the answer yet.
3. **`phase1ScreensPassAccessibilityChecks`** is in `package.md` §8 and is commissioned by **no task**.
   Carried as OQ-T173-4 / OQ-T174-3; this task's header does not name it, so it stays the owner's.
4. **The authenticated data path is dormant (OQ-T33-2).** Nothing calls `createAuthApi`, `readAccount` or
   `readProfile`. If the chrome were to show a name or a role it would be the first consumer — and R20 says
   profile data comes from `/userinfo` and `GET /accounts/me`, never from the token, while R22 forbids a
   half-authenticated screen. I do not know whether this task is meant to open that path.
5. **No subscription to session state (OQ-T40-1).** The gate is a read at render. A token arriving with no
   navigation leaves an already-mounted blocked route blocked. Nothing does that today; chrome that reacts to
   sign-out would be the first thing that might want to.
6. **TanStack Query and Zustand are in L13 and are not installed** (OQ-T173-5). If the shell needs shared
   state, that decision becomes live.
7. **Spec drift, pre-existing and not this task's to fix:** `design.md:137`'s authenticated route list omits
   `invoices/:id` and `receipts/:id`, which the 2026-10-19 note below it adds and which `package.md:368`
   counts differently. The code has eleven paths and the instruments pin eleven.
