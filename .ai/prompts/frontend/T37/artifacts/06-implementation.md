<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation) -->

# frontend · T37 · Phase 6 — Implementation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation.md` |

## What was built

| File | | |
|---|---|---|
| `frontend/src/auth/SignedOutScreen.tsx` | **new** | 46 lines, 8 of them markup |
| `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **new** | 25 cases |
| `frontend/src/i18n/catalogue.ts` | +21 | the `signedOut` section, after `callback` |
| `frontend/src/app/routes.tsx` | +9 | `SIGNED_OUT_PATH` published; the element attached |
| `frontend/src/auth/endSession.ts` | −13 | the private const and its comment deleted; the constant imported |
| `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` | +2 | `SCREENS_ATTACHED_SO_FAR` → three, marked `TASK 37` |

## Gates

| | |
|---|---|
| T37's suite | **25 passed (25)** |
| task 16's + task 36's suites together | **93 passed (93)** — both green after their edits |
| whole suite | **1687 passed / 1 failed of 1688** — the known `oxlint` host case only (baseline 1662/1663) |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |

## The plan's claim about task 16 held

Phase 5 predicted that attaching the screen would turn `routesResolveUnderAppPrefixOnly` red on
`SCREENS_ATTACHED_SO_FAR`, and said a green run there would be a finding about task 16 rather than a
convenience. It went red, with exactly the right message:

```
× attaches a screen only to the routes whose task has run (L11, L12)
AssertionError: expected [ 'sign-in', 'callback', 'signed-out' ] to deeply equal [ 'sign-in', 'callback' ]
```

Extended additively (one string, marked `TASK 37`), every original task-16 case unchanged, and that suite is
green again. **Task 37's AC9 now holds the same pin from its own side**, so the next task to attach a screen
fails twice and finds both lists.

## Two things the plan got wrong, found by running

- **`createMemoryRouter`'s `location.pathname` carries the basename.** Phase 5's risk note said
  `initialEntries` are "basename-relative"; they are basename-**prefixed**, and the router then reports
  `/app/signed-out`, not `/signed-out`. Two cases asserted the short form and failed. Worth more than the
  one-line fix: had I asserted the short form on a route that resolved to *nothing*, the case would have
  failed for the same reason and I might have read it as the route being wrong.
- **A named function expression inside `flatMap` trips `TS2684`** (`'this' context of type 'void'`) under this
  tsconfig. Rewritten as a `const` arrow with an explicit `RouteObject` parameter.

## Decisions made while building

**`<main>` + `<h1>`, matching both existing screens.** Phase 5 left this to be checked rather than assumed;
measured — `SignInScreen.tsx` and `CallbackScreen.tsx` both use exactly that, so T37 follows rather than
introducing a second convention.

**The `href` the test asserts is `/app/sign-in`, not `/sign-in`.** `Link to={`/${SIGN_IN_PATH}`}` renders the
basename into the attribute, which is the whole point of giving the router a basename, and is what a visitor's
browser will actually follow.

**AC11 asserts the *entire* rendered text**, not just the absence of an `@`:
`expect(text).toBe(`${COPY.heading}${COPY.explanation}${COPY.action}`)`. An absence list can only forbid what
it thought of; a whole-text equality forbids everything it did not.

**AC13's honest limit is written into the case.** The byte-identical comparison proves the screen does not
*branch* on a held token. It cannot prove the copy is not a false assertion — a future edit making the heading
say "You have been signed out of all devices" would still render identically. What stops that is AC4's
vocabulary scan and the catalogue comment, and the case says so rather than implying more than it measures.

## Deviations

| # | Deviation |
|---|---|
| **D1** | **The copy is derived, not specified** (OQ-T37-1). Nothing in `spec/frontend/` says what this screen contains. The words are modelled on `signIn.sessionEnded` and justified clause by clause in the catalogue comment. |
| **D2** | **AC4's cause-vocabulary list is a denylist**, so it forbids only the fourteen words it names. A copy change could disclose a cause in words not on the list. Mitigated by AC11's whole-text equality, which fails on *any* change to the rendered strings — so a new word cannot arrive unnoticed, only deliberately. |
| **D3** | **AC13 proves non-branching, not truthfulness** — stated above and in the case itself. |
| **D4** | **This file is named for a claim task 40 completes.** Its header names both halves: T37 proves this route is public and renders its screen; T40 must add that these nine and only nine are reachable without a session, which needs an auth gate that does not exist. |
| **D5** | **T37 edits two closed tasks' files** — task 16's test (additively, `SCREENS_ATTACHED_SO_FAR`) and task 36's `endSession.ts` (deleting a private constant made redundant). Both permitted under the rule T36's phase 9 wrote; the second **retires T36-D2**. |
| **D6** | **`PUBLIC_ROUTE_PATHS`'s literal became the constant.** Same nine strings, so task 16's "exactly the 20 §4c paths" case sees identical values — checked by running, not assumed. |

## Carried to phase 7

- **Nothing calls `endSession()`**, so no visitor can arrive here by signing out. Every case is a direct
  arrival. **OQ-T36-5**, eighth task.
- **AC9 is now pinned in two places** (task 16's list and T37's own). That is deliberate redundancy at the
  handoff, and phase 7 should decide whether two pins is right or whether T37's should defer to task 16's.
- A mutation harness, with the tests committed first.

**Phase 6 complete.**
