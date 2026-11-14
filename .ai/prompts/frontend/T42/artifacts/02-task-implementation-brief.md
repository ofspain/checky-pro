<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T42 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` · **Task** T42 — Authenticated shell |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` — **the only specification phases 5–13 use** |

## Task

`spec/frontend/tasks.md:85` (task 42): *"**Authenticated shell.** A session renders the shell and the
authenticated routes. Verifies `unauthenticatedRoutesRedirectToSignIn`. Cites R28. Blocked on 173, 174."*
Both blockers closed: T173 on 2026-11-12, T174 on 2026-11-14.

## Purpose

The eleven authenticated paths sit behind one pathless gate route. Ten of them carry **no element**: with a
valid session, an arrival at `/app/account` renders an empty document. The one that does render —
`/app/home` — hangs directly off the gate with nothing between them, so there is no layout for tasks 43
onward to nest in, and L12 forbids them from replacing the Phase 1 shell to make one. This task supplies the
authenticated layout that the gate was deliberately shaped to hold (`auth/RequireSession.tsx:9-13`:
*"a screen attached later by task 42 nests INSIDE the gate rather than replacing it"*).

**What this task does NOT do is give the ten element-less paths screens.** Each is owned by a later unit
(66–73 MFA, 74–80 API keys, and so on), and `app/routes.tsx:84-87` records why a placeholder would be worse
than nothing: *"attaching a placeholder would make the screen-count pins stop meaning anything."*

## Scope

**In:** one authenticated layout component · its attachment as the element of a pathless layout route inside
`RequireSession`'s children · its catalogue entries · the growth of the six instruments that will see it ·
the named test's extension under the describe-prefix convention T41 reserved for this task.

**Out:** any screen for the other ten paths · any new route, path, endpoint, contract, config key or storage
key · any data fetch (OQ-T33-2) · anything identity-dependent (a name, an email, a role, an account state) ·
installing TanStack Query or Zustand (OQ-T173-5) · a session subscription (OQ-T40-1) · touching
`AppShell.tsx`, `mountShell.tsx` or any file under `app/ui/` · claiming
`phase1ScreensPassAccessibilityChecks` · editing anything under `spec/`.

**Deliberately undecided here and named as Phase 3's target:** *what the layout contains.* See Open
Questions.

## Business Rules

| ID | |
|---|---|
| **R28** | While a visitor is unauthenticated, only the §4c public routes are served and anything else redirects to the sign-in start. Both clauses are built (T40, T41); this task owns the **positive** half its statement asserts — that a session renders the authenticated routes. |
| **R22** | The SPA shall never show a half-authenticated screen. A held token proves only that a token is held — not that the account is usable (a SUSPENDED account keeps a working token, T31). |
| **R20** | Profile data comes from `/userinfo` and `GET /accounts/me`, never from the access token. |
| **R19 / R57** | Sign-out clears memory and ends the SAS session through the end-session endpoint; that navigation is served by SAS, never by the SPA shell. |
| **R29 / L16** | Nothing is stored client-side beyond the L16 carve-out. |

## Locked Decisions

**L11** · **L12** · **L13** · **L14** · **L1** · **L3** · **L4** · **L9** · **L10** · **L16**, plus
`agents.md`'s LOCKED catalogue rule (no hardcoded copy in a component) and its accessibility rule
(keyboard-operable, managed focus, live region for async results, WCAG AA). Inherited and equally binding:
**`app/ui/index.ts` rule 2** (*"No primitive accepts the attribute that is its own contract"*) and **T173
§6e** (no lowercase JSX tag in a render site).

## Dependencies

`app/ui/` (T173's eight primitives) · `react-router`'s `Outlet` · `react-i18next` · `i18n/catalogue.ts` ·
the path constants in `app/routes.tsx` · `auth/RequireSession.tsx` (read only). **No endpoint, no contract,
no config key, no generated-client call.**

## Inputs

None. The layout takes no props, reads no parameter, no query and no storage. It receives its content
through `<Outlet />`.

## Outputs

Rendered DOM only: a landmark structure that wraps whatever the matched authenticated route renders, and
nothing when no session is held.

## State Changes

**None.** No storage write, no network call, no module state, no catalogue key removed or renamed, no path
added or renamed.

## Files to Create

- the authenticated layout component, one file under `src/app/` (`design.md` §6 puts *"shell, providers,
  router"* there; `src/features/` does not exist and nothing here belongs in it)

## Files to Modify

- `src/app/routes.tsx` — one pathless layout route inside `RequireSession`'s children; the eleven paths
  become **its** children, unchanged in spelling and order
- `src/i18n/catalogue.ts` — a new group for the layout's own copy
- `src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` — **the named test**, T41's file, extended under
  its reserved describe-prefix convention
- `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` — `RENDER_SITES` six → seven, and the
  union count
- `src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` — only where the nesting makes an
  assertion **false** or requires it to **admit new state** (the one-`<h1>` count, `main.children`, and the
  I9 focus guard are the three candidates)
- `src/app/shellBuildsAndMounts.test.tsx` · `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` ·
  `src/app/routesResolveUnderAppPrefixOnly.test.tsx` — **only if** measurement shows they fire. The last two
  are structurally blind to a pathless element, so a prediction that they do **not** fire must be measured
  rather than assumed (T174's D-5 is the precedent: the freeze predicted no T41 test would break and six did)

## Files NOT to Modify

`src/app/AppShell.tsx` · `src/app/mountShell.tsx` · `src/auth/RequireSession.tsx` ·
`src/auth/RedirectToSignIn.tsx` · `src/auth/returnTarget.ts` · anything under `src/app/ui/` ·
`src/app/styles.css` · anything under `spec/`.

## Acceptance Criteria

| | criterion | traces to |
|---|---|---|
| **AC1** | With a session held, an arrival at **each** of the eleven authenticated paths renders the layout — including the ten with no element of their own, and `invoices/:id` and `receipts/:id` with a concrete id | task 42, **R28** |
| **AC2** | `/app/home` renders `HomeScreen` **nested inside** the layout, not replaced by it; the screen's `<main>`, its single `<h1>` and its copy are unchanged | **L12**, task 174 |
| **AC3** | The discharged T27-D1 case still passes unchanged: a real callback through a real exchange lands on `/app/home` with focus on its `<h1>` | **L11**, T27-D1 |
| **AC4** | With no session — and with the empty-string token, which is not a session — every authenticated path redirects to the sign-in start and renders **no part** of the layout | **R28**, T40, T41 |
| **AC5** | `PUBLIC_ROUTE_PATHS` and `AUTHENTICATED_ROUTE_PATHS` are unchanged in content, spelling and order; no path is added | **L14** |
| **AC6** | Every string the layout renders comes from the catalogue, asserted against the entry **and** as literal English, with no raw key in the DOM | **L11**, `agents.md` |
| **AC7** | The layout renders through `app/ui` primitives only, bears no lowercase JSX tag, and is named in `RENDER_SITES` with the union count grown | T173 §6e |
| **AC8** | Exactly one `<h1>` per rendered authenticated page, and the layout does not take focus on mount — the I9 guard T174 wrote for exactly this hazard | **L11**, T174 |
| **AC9** | The layout is a landmark distinct from the screen's `<main>`, and every interactive element in it is keyboard-reachable with a visible focus ring | **L11** |
| **AC10** | Nothing the layout renders depends on identity: no name, email, role or account state, no token read, no `auth/` import | **R22**, **R20**, **L3** |
| **AC11** | `app-shell`'s `innerHTML` is still `''` when no route matches, and the layout makes no network call and writes no storage | T01, **R29**, **L16** |

## Required Tests

`unauthenticatedRoutesRedirectToSignIn` (`package.md:151` → **R28**), extended in T41's file under its
reserved prefix convention, plus the instrument growth above. A mutation harness (**twelfth copy**) with at
least one row per criterion AC1–AC11, every row predicting `own` / `inherited` / `unfalsifiable`, and
**controls as well as mutants** — the harness measures strength, only a control measures precision. Standing
harness rules carry: commit tests before running it; a mutant that cannot compile is **BROKEN, not killed**;
a mutant must measure its own description; score canaries apart from regressions; a row whose anchor matches
prose may edit the prose.

## Constraints

- **Module boundaries:** the layout may import from `app/ui`, `react-router` (`Outlet`) and `react-i18next`,
  and from `app/routes` if it needs the path constants. It must not import from `auth/`, must not read
  `accessToken`, and must not reach `globalThis.location`, `history`, `document.cookie`, `indexedDB` or any
  storage.
- **Security:** no PII, no token, no account state, no role in the DOM or the source. A held token proves
  only that a token is held (R22, T31).
- **Null handling:** the layout takes no input. `<Outlet />` renders nothing when the matched child has no
  element, and that is the ten paths' state today — it must be tolerated, not worked around.
- **Performance / thread-safety / transactions:** not applicable to a browser layout with no state.
- **Phase additivity (L12):** tasks 43 onward nest their screens in this. It must be something to build on,
  not a placeholder to delete, and it must not take the element slot a later task needs.

## Open Questions

**One blocker, and it is Phase 3's target.**

- **OQ-T42-1 — what the layout contains is not specified anywhere in the package.** Searching all five spec
  files for *shell*, *navigation*, *nav*, *header*, *menu*, *sign out*, *layout* and *chrome* returns six
  *shell* hits, none of which specifies any of it, and **no requirement ID commissions an authenticated
  navigation surface**; `package.md:151` maps the named test to R28 alone. Three readings fit the statement:
  **(a)** a layout with navigation to the authenticated routes; **(b)** a layout rendering only a landmark
  and an `<Outlet />`, with no link until a task owns each destination; **(c)** nothing new — the statement
  restating T40 plus T174, making the unit vacuous, which would then be **recorded as vacuous rather than
  padded** (T40's precedent: *"a criterion can be vacuously true and that is the task"*). Every AC above
  holds under (a) and (b); AC6, AC7 and AC9 are empty under (c). **Phase 3 settles it.**

**Three carried, none of which stops work:**

- **OQ-T42-2 — the dormant sign-out path.** `signOut()` (T35), `endSession()` (T36), the signed-out landing
  (T37) and cross-tab propagation (T39) are built, and **nothing in production source calls `endSession()`
  or `signOut()`** (verified by grep over `src/`, tests excluded). R19 is conditional — *"WHEN the user signs
  out"* — and commissions no affordance; task 38, the only other R19 unit, is still *"Blocked on Q15"*.
  Authenticated chrome is the natural and only home for such a control, so the question arrives here. It is
  **a scope decision for the owner**, carried into the Phase 4 approval gate rather than taken silently: the
  alternative is that four closed tasks' work stays unreachable until a later unit.
- **OQ-T42-3** — ten of the eleven paths have no element and each belongs to a later unit; whether they must
  become observably different from nothing follows from OQ-T42-1, not from a separate decision.
- **OQ-T33-2 · OQ-T40-1 · OQ-T173-5 · OQ-T173-4/OQ-T174-3** — the dormant data path, the gate as a read
  rather than a subscription, the two uninstalled L13 libraries, and the named test no task commissions. All
  pre-existing, all the owner's, none claimed here.
