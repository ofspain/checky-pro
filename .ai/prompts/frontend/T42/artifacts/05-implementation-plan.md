<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan) -->

# frontend · T42 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` · **Task** T42 — Authenticated shell |
| **Consumes** | `artifacts/04-freeze-task.md` (frozen) |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code here. Every file below traces to the freeze's **Files to create** / **Files to modify**; nothing else
is authorized and nothing else is planned.

## Files to create

| | |
|---|---|
| `frontend/src/app/ui/Banner.tsx` | the ninth primitive (**D2**): `<header>`, `{ children }` only, no `role` prop, no `className`, no `style` |
| `frontend/src/app/AuthenticatedLayout.tsx` | the layout (**D1**, **D5**, **D11**) |

## Files to modify

| | |
|---|---|
| `app/ui/index.ts` | one export line, and rule 2's text is **not** touched |
| `app/routes.tsx` | D5's shape, one pathless layout route |
| `i18n/catalogue.ts` | one group, one key: `shell.signOut` (**D7**) |
| `auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | the named test, under D8's describe |
| `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | four counts: primitives 8→9, bearers 7→8, union 14→16, render sites 6→7 |
| `auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | `PERMITTED_IMPORTERS` gains the layout — the list was written empty for this day |
| `auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` · `auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx` | the body-text equality grows to admit the chrome (**D6**) |
| `app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | the I9 guard grows to the layout; `main`-scoped assertions unchanged (**D4**, F11) |
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:866` · `api/signOutPropagatesToAllTabs.test.ts:9-12` | comments this task makes false |

## Public surface (signatures)

```
// app/ui/Banner.tsx
export const Banner: ForwardRefExoticComponent<{ children: ReactNode } & RefAttributes<HTMLElement>>

// app/AuthenticatedLayout.tsx
export function AuthenticatedLayout(): ReactElement
```

Both return types are **stated, not inferred** — T40's phase-8 I2 made that the convention for anything in a
route tree, because the one thing a reader needs to know is what it may render.

No other module gains an export. `app/ui/index.ts` re-exports `Banner`; nothing else changes there.

## Private surface

Inside `AuthenticatedLayout` only: one `useState<boolean>` for *"a sibling signed out"*, one `useEffect`
installing `listenForSignOut` and returning its `close`, and one callback `() => { signOut(); set(true); }`.
No module-scope state anywhere — T35's module-scope-mutable scan is live over `src/` and a `let` at column 0
would trip it.

## Entities · repositories · services

**None.** This is a browser-render task: no persistence, no repository, no service, no endpoint, no contract,
no config key. The template's four columns have no occupants and that is the honest answer rather than an
invented mapping.

Modules consumed (D9's allowlist, and nothing else): `endSession` · `signOut` · `RedirectToSignIn` ·
`listenForSignOut` · `app/ui` · `react-router`'s `Outlet` · `react-i18next` · `app/routes`' path constants.

## Tests required

| | |
|---|---|
| **named** | `unauthenticatedRoutesRedirectToSignIn`, extended: AC1 (all eleven paths with a session, the two parameterised ones with `concrete(SAMPLE_ID)`), AC4 and AC15 (no session **and** the empty-string token), AC12/AC13 (the control and what it calls), AC14 (the listener, its cleanup, and the redirect) |
| **grown** | the four counts in `screensRender…`; `PERMITTED_IMPORTERS`; the two body-text equalities; T174's I9 guard |
| **source scans** | D9's import allowlist and its forbidden list, each pattern paired with a probe that trips it — T41's `FORBIDDEN` shape, which asserts both that a pattern sees its own probe **and** that the real source does not already trip it |
| **landmark** | the `<header>` is outside `<main>` and not inside `article`/`aside`/`nav`/`section`, because that nesting is what the native `banner` mapping depends on (**D2**) |
| **harness** | twelfth copy, ≥1 row per AC1–AC15, canaries scored apart, controls as well as mutants |

## Execution order

The backend template front-loads schema → dao → service → api → tests. The equivalent here is
**primitive → layout → route table → catalogue → instruments → named test → harness**, with one inversion
that matters: **the route table is wired before the layout does anything interesting**, so the blast radius
is measured against a layout that only wraps.

1. **`Banner.tsx` + the barrel export.** Nothing consumes it yet. Run `tsc`.
2. **`AuthenticatedLayout.tsx` in its first form only**: `Banner` + `Outlet`, no control, no listener.
3. **`routes.tsx`** (D5). **Then run the whole suite and record every failure, before touching one
   instrument.** This is the measurement the freeze's D6 demands: the prediction is *the four counts in
   `screensRender…` plus the two body-text equalities, and nothing else*. T174's D-5 is why this step exists
   as a step — its freeze predicted no T41 test would break and six did. **A prediction that survives
   unmeasured is a guess that has been promoted.**
4. **Grow exactly the instruments that fired**, each edit naming task 42 and each one grown rather than
   loosened. Anything that fired and was not predicted is written down as a deviation the same hour.
5. **`catalogue.ts`** — `shell.signOut`.
6. **The sign-out control** in the layout: `ActionButton` with `onClick={endSession}`, inside `Banner`.
   Run the suite again: this is what the two body-text equalities will see, and `PERMITTED_IMPORTERS` is what
   the import will see.
7. **The listener and the redirect** (D11): the effect, the flag, `RedirectToSignIn` in place of `Outlet`.
8. **The named test**, under D8's describe, written in the order the measurements gave rather than in AC
   order — T41's own file is written that way and says why.
9. **Commit the tests.** Non-negotiable before step 10: the harness reverts with `git checkout` over
   `REVERT_PATHS` and has already cost 19 cases in T14 and 5 in T15.
10. **The harness**, twelfth copy, then the two runs the freeze asks for.

## Risks, each with the thing that would catch it

| | |
|---|---|
| **The `<header>` mapping is conditional.** `<header>` is only a `banner` when it is not inside `article`/`aside`/`main`/`nav`/`section`. A later task that nests the layout differently silently demotes it to a generic group | the landmark test asserts the ancestry, not just the tag |
| **A second `<main>`** if `Screen` is reached for in the layout | D3, and `screensRender…`'s existing one-`<main>` claim |
| **Two `<h1>`** if `Heading` is reached for | D3 + T174's one-`<h1>` assertions through the real table |
| **The layout steals focus**, now that every `<h1>` is focusable | D4 + T174's I9 guard, grown to the layout |
| **`identityErrors…` loosened instead of grown.** The lazy fix is `toContain`; it would also pass for a chrome that leaked a name | grow to the exact string, including the label, in DOM order |
| **The listener outlives the mount** and a stale callback fires | `close()` on unmount, asserted by unmounting and broadcasting after |
| **The return target is re-remembered** after `signOut()` cleared it (D11's accepted consequence) | step 3 and step 7 runs; if a T35 or T41 instrument pins the cleared target, that is a finding, not a licence |
| **`endSession()` navigates**, so a jsdom case that clicks the control will try to leave the page | the existing suites stub `globalThis.location`; T36's file is the reference for how |
