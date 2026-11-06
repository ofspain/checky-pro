<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T37 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

Attach a screen to `/app/signed-out` — the route SAS already redirects to after `/connect/logout` — so that a
visitor who signs out arrives somewhere with words on it. R19's landing half, L14's public route.

## Purpose

T36 points SAS at this route (`post_logout_redirect_uri`), and `design.md:164` confirms auth-service registers
it for `checky-spa`. Today the path is declared with **no element**, so a successful sign-out lands the
visitor on a blank page — which reads as a broken application at precisely the moment they were told nothing
went wrong.

## Scope

**In** — one screen component, its catalogue entry, the route attachment, and the handoff edit to
`routesResolveUnderAppPrefixOnly`.

**Out** — the sign-out affordance (unowned, **OQ-T36-5**) · propagation to sibling tabs (**task 39**) ·
`signOutThenSignInRequiresCredentials` (**task 38**) · the exact-public-route-set claim across all nine paths
(**task 40**) · styling and the design system (**task 173**) · `/app/home` (**task 174**).

## Business rules and locked decisions

**R19** the landing half · **R28** only public routes without a session · **L14** the route set, which names
*"the signed-out landing"* · **L4** enumeration-safe copy · **L11** catalogue-only strings · **L12** phase
additivity · **L9** single origin.

## Dependencies

| Thing | Where | Note |
|---|---|---|
| the route | `app/routes.tsx:31` | declared, public, **no element** |
| the screen pattern | `auth/SignInScreen.tsx` | `useTranslation()`, `t()` per string, no props |
| the catalogue | `i18n/catalogue.ts` | nested per screen, ordered as a visitor meets them |
| the test mount | `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:205` | `createMemoryRouter(routes.appRoutes)` + `RouterProvider` — mounts the **real** route table |
| the attached-screen pin | `routesResolveUnderAppPrefixOnly.test.tsx:17` | `SCREENS_ATTACHED_SO_FAR = ['sign-in', 'callback']` |

## Files to create

- `frontend/src/auth/SignedOutScreen.tsx`
- `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — **T37's subset only**, see the frozen
  decision below.

## Files to modify

| File | Change |
|---|---|
| `app/routes.tsx` | attach `<SignedOutScreen />` to the `signed-out` path; publish `SIGNED_OUT_PATH` |
| `i18n/catalogue.ts` | one `signedOut` section |
| `app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` → three, marked `TASK 37` |

**Checked, not assumed:** no suite pins `Object.keys(en)` at the top level, so a new `signedOut` section breaks
nothing. `callbackValidatesNonce.test.tsx:822-825` pins `Object.keys(en.signIn)` and `Object.keys(en.callback)`
individually, and T37 touches neither.

## Frozen decisions

**OQ-T37-1 — FROZEN, and recorded as derived.** The copy is **not specified anywhere in the package**. It is
derived from L4 and modelled on `signIn.sessionEnded`: name the **event**, never a cause, with one way back.
Three keys — `heading`, `explanation`, `action`. The owner may reword; the *shape* is L4's and a rewording is a
copy change, not a redesign.

**OQ-T37-2 — FROZEN as option (a): the screen makes no claim it cannot verify, and checks nothing.** A visitor
who types `/app/signed-out` while still holding a token sees the screen. That is accepted, and the copy is
written so it is not a lie: it says what *this route is for*, not "you are signed out of everywhere."
Clearing on arrival is **rejected** — it would make a URL a sign-out affordance any cross-site link could
fire. Redirecting a token-holding tab to `/app/home` is **rejected for now** on a measurement: no
authenticated route has an element, so `/app/home` renders nothing, and the redirect would move a visitor from
a page with words to a blank one. Available to task 174, not to T37.

**OQ-T37-3 — FROZEN.** T37 **creates** `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` holding only its own
route's claims, and **task 40 completes it** for all nine public paths. This keeps the house convention — a
test file named after the task's named test in `tasks.md` — while the file's own header states plainly that
T37 owns one route of nine and T40 owns the exhaustive claim. The alternative (putting everything in
`routesResolveUnderAppPrefixOnly`) would hide T37's work in a suite named for route resolution.

**OQ-T37-4 — FROZEN.** The screen is indifferent to the query string. T36 sends no `state`;
`design.md:167` describes the backend test's URL, which supplies one. AC6 is the criterion.

## Inputs / Outputs / State changes

**None, none, and none.** The component takes no props, returns markup only, and changes no state — no token
read, no storage write, no backend call, no timer. That emptiness is the deliverable: everything this screen
could usefully *do* belongs to a task that owns it.

## Acceptance criteria

AC1–AC12 as frozen in phase 1, unchanged.

## Required tests

- The screen rendered **through the real route table** at `/signed-out`, with no token and nothing in storage.
- The same with a token held, a `state` parameter, an unknown parameter, and no query — all rendering
  identically (AC4, AC6, OQ-T37-2).
- Every rendered string traced to a catalogue key; a scan proving the component source holds no user-facing
  literal (AC3).
- A scan proving the component reads no token, touches no storage, and makes no request — with controls (AC7,
  AC8, AC11).
- The way back to sign-in is present, is the only action, and resolves to `/app/sign-in` (AC5).
- `routesWithElements(appRoutes)` equals exactly three (AC9).
- Direct arrival with no prior sign-out renders and does not redirect (AC12).
- Controls for every scan, each firing on a planted positive.

Gates: whole suite no worse than **1662 passed / 1 failed of 1663**; `tsc --noEmit` 0; `oxlint src` 0; a
mutation harness with canaries scored apart, asserting its own baseline, broken rows excluded from the kill
count, and coarse kills verified narrowly.

## Constraints

- **No literal copy in the component** (L11). **Nothing that names a cause** (L4).
- **T37 would be the first use of react-router's `Link`** in this package — measured. Phase 5 should confirm
  a real anchor is wanted over a button, since this is the one navigation in the SPA that is purely in-app.
- **No module-level mutable state.**
- **`app/routes.tsx` is modified here**, and it was closed to T36. T37 is the task that owns attaching this
  screen, so the edit is in scope; publishing `SIGNED_OUT_PATH` also retires **T36-D2**, which had to declare
  the spelling privately because this export did not exist.

## Open questions

**OQ-T37-5** — nothing calls `endSession()`, so no visitor can reach this screen by signing out. Eighth task
affected by **OQ-T36-5**. AC12 is what keeps the screen testable meanwhile.

**OQ-T37-6** — `package.md:123`'s `signOutRevokesTokensClearsMemoryAndEndsSasSession` still carries `Revokes`,
removed from R19 by Q5 on 2026-10-10; `tasks.md:66` still marks **task 38 "Blocked on Q15"** though
`package.md:28` lists Q15 among Phase 1a's **resolved** questions and `:421-430` records the decision as
closed with only a backend follow-up outstanding. **Q15 is about revocation semantics** — whether revoke-all
and password reset must also end SAS sessions — **not about sign-out**, and
`SasLoginIntegrationTest:475` already asserts a post-logout `/oauth2/authorize` redirects to `/login`, which
is task 38's whole claim. The blocker looks **mis-assigned**. Not acted on: reassigning it is a `spec/`
decision and `spec/` is not this task's to edit.

**Phase 2 complete. Brief frozen.** Next is **phase 3 — Kimi 2.7's design challenge.**
