<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T42 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` · **Task** T42 — Authenticated shell |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Swept twice.** Once from the header's scoped IDs outward, and once from the five spec files inward, grepping
for *shell*, *navigation*, *nav*, *header*, *menu*, *link*, *sign out*, *layout* and *chrome*. The second
sweep is what produced §7's first two open questions, and it is the reason this artifact says *"the
specification does not say"* in several places instead of filling them in.

## Business Rules

| ID | one sentence | status today |
|---|---|---|
| **R28** (`requirements.md:99-100`) | While a visitor is unauthenticated, the SPA serves only the public routes listed verbatim in `design.md` §4c and redirects any other route to the sign-in start. | **both clauses already built.** Clause 1 is T40's gate, clause 2 is T41's redirect. What is *not* built is the **positive** half this task's statement asserts: that a session **renders** the authenticated routes |
| **R22** (`:72-74`) | On failed renewal or an invalid session the SPA transitions to clean re-auth, never shows a half-authenticated screen, and shows neutral copy; sign-out propagates to every tab. | relevant as a **constraint**: chrome visible behind the gate must not imply more than "a token is held" |
| **R20** (`:66-67`) | Profile data comes from `/userinfo` and account status from `GET /accounts/me`, never from the access token. | relevant **only if** the chrome shows identity. The path is dormant (OQ-T33-2) |
| **R19** (`:62-65`) | **When the user signs out**, the SPA clears in-memory token state and ends the SAS session through the end-session endpoint with an allowlisted `post_logout_redirect_uri`. | built end to end (T35–T37, T39) and **has no caller in production source** — see §7 |
| **R29 / L16** (`:101`) | Only the L16 carve-out may be stored client-side. | constraint; nothing here needs storage |
| **R57** (`:205`) | The end-session navigation is served by SAS and never by the SPA shell. | constraint on any sign-out affordance |

**R28 is the only requirement `tasks.md` cites for this task, and `package.md:151` maps the named test to R28
alone.** No requirement in `requirements.md` R1–R59 commissions an authenticated navigation surface. That is
a finding, not an omission in this extraction.

## Locked Decisions

| ID | what it constrains here |
|---|---|
| **L14** (`design.md:63-66`) | the route set is **verbatim** in §4c; "everything else requires a session"; a new public route needs an ADR. This task adds **no path** |
| **L12** (`design.md:49-50`) | phase additivity — later phases *"do not replace the Phase 1 shell, routing, or auth model"*. Whatever this task builds is the thing tasks 43–137 nest inside, and `agents.md:100` repeats it |
| **L11** (`design.md:47-48`) | WCAG AA, keyboard operation, managed focus, live regions for async results, catalogue-only strings. **Navigation chrome is a keyboard surface and a landmark question**, so this is the decision with the most to say about the shape |
| **L13** (`design.md:51-52`) | the confirmed stack. React Router is in it; TanStack Query and Zustand are in it and are **not installed** (OQ-T173-5) |
| **L1** (`design.md:8-10`) | the post-login return target falls back to the home route — already honoured; this task must not change where a sign-in lands |
| **L3** (`design.md:11`) | tokens in memory only; chrome may not read or display one |
| **L4** (`design.md:18-24`) | enumeration safety on every **public or low-trust** surface. The authenticated chrome is neither, and the Q14 carve-out shows the rule is read narrowly |
| **L9 / L10** | single origin; no secret in the bundle. Not at risk here |
| `agents.md` | the LOCKED catalogue rule (no hardcoded copy in a component) and the accessibility rule (keyboard-operable, managed focus, live region for async results, WCAG AA) |

Inherited and binding though not in `design.md`: **`app/ui/index.ts` rule 2** — *"No primitive accepts the
attribute that is its own contract"* — and **T173 §6e**, no lowercase JSX tag in a render site.

## Files involved

**Existing, to read:**

| | |
|---|---|
| `src/app/routes.tsx` | `AUTHENTICATED_ROUTE_PATHS` (eleven), the pathless gate route at `:88-94`, `HomeScreen` attached at `:92` |
| `src/auth/RequireSession.tsx` | the gate; its header states that *"a screen attached later by task 42 nests INSIDE the gate"* |
| `src/app/AppShell.tsx` | **the existing shell**: `<Surface data-testid="app-shell">{children}</Surface>`, already wrapping every route |
| `src/app/mountShell.tsx` | where `AppShell` and the router are composed |
| `src/app/HomeScreen.tsx` · `src/app/ui/` | the one authenticated screen and the eight primitives |
| `src/i18n/catalogue.ts` | five groups; **no navigation group** |

**Existing, to extend (each an instrument, grown not loosened):**

| | why it fires |
|---|---|
| `src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | **the named test, T41's file, 35 cases, reserved for this task by its own header**; `describe`s are already prefixed by owning task so AC numbering cannot collide |
| `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:83-91` | `RENDER_SITES` is exactly six; a new component that imports the barrel is caught by T174's both-directions derivation whether or not it has a lowercase tag |
| `src/app/shellBuildsAndMounts.test.tsx:102-107` | `app-shell`'s `innerHTML` must stay `''` when nothing matches |
| `src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | one `<h1>`, `main.children === ['H1','P']`, focus on the heading, and *"no OTHER screen takes focus"* |
| `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx:1490` | the discharged T27-D1 case: a real sign-in must still land on the home heading **with focus** |
| `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:718` · `src/app/routesResolveUnderAppPrefixOnly.test.tsx:18` | the attached-element and screen-count pins — **blind to a pathless layout element**, which is what T40's AC13 exists for |

**New files the spec expects:** `design.md` §6's planned tree has `src/app/` for *"shell, providers, router"*
and `src/features/*` for later phases; `src/features/` **does not exist yet** and nothing in this task
belongs there. The spec names **no component file** for authenticated chrome. If the task produces one, it
belongs in `src/app/`, and the file name is Phase 2's to fix.

## Dependencies

No endpoint, no contract, no config key, no entity, no repository — this is a browser-render task.
Module dependencies: `react-router` (`Outlet`, and `Link` only through `TextLink`), `react-i18next`,
`app/ui`, and the route constants in `app/routes.tsx`. The generated client is **not** a dependency: nothing
calls `createAuthApi`, `readAccount` or `readProfile` today (OQ-T33-2).

## Acceptance Criteria

Provisional, because §7's first question is genuinely open. Each one traces to an ID; those that depend on
the unspecified chrome are marked **(shape-dependent)**.

| | criterion | traces to |
|---|---|---|
| **AC1** | With a session held, an arrival at an authenticated path renders **something observable** rather than an empty document — today ten of the eleven render nothing at all | task 42, **R28** (positive clause) |
| **AC2** | `/app/home` still renders `HomeScreen`, **nested inside** whatever this task adds, and the screen is not replaced | **L12**, task 174 |
| **AC3** | The focus landing proved by the discharged T27-D1 case is unchanged: a completed sign-in lands on `/app/home` and focus is on its `<h1>` | **L11**, T174, T27-D1 |
| **AC4** | With **no** session, every authenticated path still redirects to the sign-in start and renders no part of the authenticated surface | **R28** (both clauses), T40, T41 |
| **AC5** | No path is added, renamed or removed; `PUBLIC_ROUTE_PATHS` and `AUTHENTICATED_ROUTE_PATHS` are unchanged | **L14** |
| **AC6** | Every string rendered comes from the catalogue, asserted against the entry and as literal English, with no raw key in the DOM | **L11**, `agents.md` |
| **AC7** | The new surface renders through `app/ui` primitives only, with no lowercase JSX tag, and is named in `RENDER_SITES` | T173 §6e |
| **AC8** | Exactly one `<h1>` per rendered authenticated page, and the chrome does not take focus from the screen *(shape-dependent)* | **L11**, T174's I9 guard |
| **AC9** | The chrome is keyboard-operable and announces itself as a landmark distinct from `<main>` *(shape-dependent)* | **L11** |
| **AC10** | Nothing rendered depends on identity, and no token is read *(shape-dependent — becomes R20's problem the moment a name appears)* | **R22**, **R20**, **L3** |
| **AC11** | `app-shell`'s `innerHTML` is still `''` when no route matches | T01, `shellBuildsAndMounts:102` |

## Tests required

**Named (`package.md` §8):** `unauthenticatedRoutesRedirectToSignIn` → R28 (`:151`) — **the file exists**, is
T41's, and is reserved for this task by its own header. Also in this task's prompt header and already
closed: `publicRouteSetIsExactlyTheDeclaredSet` → R28 (`:152`) and
`homeRouteRendersLandingScreenAndManagesFocus` → R28, L11 (`:168`). This task **grows** all three rather than
creating any.

**Boundary tests implied:**

1. a session + each of the eleven paths (including `invoices/:id` and `receipts/:id` with a concrete id —
   T41's suite already has `concrete()` and `SAMPLE_ID` for this);
2. a session + a path that matches **nothing** (the catch-all is outside the gate, which T41 established);
3. the empty-string token, which `RequireSession` treats as no session (`auth/RequireSession.tsx:43-44`);
4. the home screen nested rather than replaced, with its heading, its focus and its `<main>` child list intact;
5. StrictMode double invocation, since the one effect in this area is the home screen's focus move;
6. a twelfth mutation-harness copy with at least one row per criterion.

## Open Questions

**Two are genuine blockers for Phase 2's brief, and both are Phase 3's to settle rather than mine to answer.**

- **OQ-T42-1 — What is "the shell"?** `AppShell.tsx` already exists and already renders around every route,
  so the task statement's *"a session renders the shell"* is **already literally true**. Searching all five
  spec files for chrome-shaped words returns six *shell* hits, and none of them specifies navigation, a
  header, a sign-out control or a layout. No requirement ID commissions such a surface. Readings that fit the
  statement include: (a) an authenticated **layout route** nesting inside `RequireSession` and holding
  navigation to the authenticated routes; (b) the same, but rendering only a landmark and an outlet, with no
  links until a task owns each destination; (c) nothing new at all, the statement being a restatement of
  T40 + T174 — in which case the unit is vacuous and should be recorded as such rather than padded. **The
  task cannot be specified without this.** (`package.md` §11 has no question covering it.)
- **OQ-T42-2 — Does this task wire the dormant sign-out path?** `signOut()` (T35), `endSession()` (T36), the
  signed-out landing (T37) and cross-tab propagation (T39) are all built, and **nothing in production source
  calls `endSession()` or `signOut()`** — verified by grep over `src/`, excluding tests. R19 is conditional
  (*"WHEN the user signs out"*) and commissions no affordance; task 38, the only other R19 unit, is still
  *"Blocked on Q15"*. Authenticated chrome is the natural and only home for a sign-out control, so this task
  is where the question arrives — but it is a **scope decision for the owner**, because the alternative is
  that four closed tasks' work stays unreachable until some later unit.

**Not blockers, carried:**

- **OQ-T42-3** — ten of the eleven authenticated paths have no element and each belongs to a later unit, so
  *"and the authenticated routes"* cannot mean attaching screens. Whether the ten must become observably
  different from nothing depends on OQ-T42-1.
- **OQ-T33-2** — the authenticated data path is dormant. Chrome showing a name or role would be its first
  consumer, and R20 and R22 both apply the moment it does.
- **OQ-T40-1** — the gate is a read at render, not a subscription; chrome reacting to a sign-out in another
  tab would be the first thing to want one.
- **OQ-T173-5** — TanStack Query and Zustand are in L13 and not installed.
- **OQ-T173-4 / OQ-T174-3** — `phase1ScreensPassAccessibilityChecks` is commissioned by no task. Not in this
  task's header; not claimed.
- **Spec drift, pre-existing:** `design.md:137`'s authenticated list omits `invoices/:id` and `receipts/:id`,
  which the note immediately below it adds; `package.md:368` counts them differently. Code and instruments
  both hold eleven. Recorded, not fixed — `spec/` is never edited by this pipeline.
