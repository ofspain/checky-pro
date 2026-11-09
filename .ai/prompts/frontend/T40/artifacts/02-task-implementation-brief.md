<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T40 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

T40 — **Public route set is exact.** Exactly the public routes in `design.md` §4c are reachable without a
session. Verifies `publicRouteSetIsExactlyTheDeclaredSet`. Cites **R28**, **L14**.

## Purpose

R28's first clause has no mechanism: nothing in `src/` guards any route, so a visitor with no session is
served all twenty declared paths rather than the nine public ones. This task introduces the split and
makes the public set exact. **Task 41 owns the redirect** — R28's second clause — and the established
behaviour here for a route that must not render is to render nothing, silently.

## Scope

**In**

- a route guard under `auth/`, asking the session owner whether a session exists;
- `app/routes.tsx` applying it, so the eleven authenticated paths are not served without a session;
- extending `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx`, the named test;
- growing the sibling instruments in `app/routesResolveUnderAppPrefixOnly.test.tsx` that T40's change makes
  fire.

**Out**

- the **redirect** to sign-in, its return target, and `replace`-vs-push — **task 41**;
- any screen for a route that has none; the authenticated shell — **task 42**, blocked on 173/174;
- silent re-authentication, session restoration after reload — **unbuilt, no task** (see Open Questions);
- changing the catch-all;
- any change to `spec/`.

## Business rules

| ID | |
|---|---|
| **R28** | While unauthenticated, the SPA serves **only** §4c's public routes. (Its second clause — redirect anything else to sign-in start — is task 41's.) |
| R19 | The signed-out landing is one of the nine; its screen already exists. |

## Locked decisions

| ID | |
|---|---|
| **L14** | §4c's route set is verbatim: **nine** public, **eleven** authenticated. "Everything else requires a session." A new public route requires an ADR. |
| **L1–L3** | The guard asks the session owner. No new surface, no new storage. |
| **L11** | A blocked route renders no copy; user-facing text comes only from the catalogue. |
| **L12, L13** | `app/` owns the router; the shell is the mount root. |
| **L16** | The guard adds nothing to the `sessionStorage` carve-out. |
| **L15** | This task is part of the auth gate that must precede payment surfaces. |

## Dependencies

`accessToken.current()` (`auth/accessToken.ts`) · `PUBLIC_ROUTE_PATHS`, `AUTHENTICATED_ROUTE_PATHS`,
`APP_BASENAME`, `SIGN_IN_PATH`, `appRoutes` (`app/routes.tsx`) · `react-router`.
**No endpoint, no contract, no config key.**

## Inputs

The requested path, and the session owner's answer to "is a token held".

## Outputs

For a public path: the route's own element, unchanged. For an authenticated path without a session: no
content and no copy. For an authenticated path with a session: the route's own element, unchanged.

## State changes

**None.** Nothing is stored, written, or sent. The guard is a read.

## Files to create

- **one module under `auth/`** — the guard. `design.md:203` places guards in `auth/`; the name and shape
  are phase 3's to settle.

## Files to modify

- `app/routes.tsx` — apply the guard to the authenticated paths.
- `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — the named test; **extend, do not replace**.
- `app/routesResolveUnderAppPrefixOnly.test.tsx` — only where T40's change makes an existing instrument
  fire (its *"attaches a screen only to the routes whose task has run"* case, and its pinned path count).

## Files NOT to modify

`spec/**` · `auth/accessToken.ts` and the rest of the session owner · `auth/SignInScreen.tsx`,
`CallbackScreen.tsx`, `SignedOutScreen.tsx` · `app/mountShell.tsx`, `AppShell.tsx` · the catch-all route ·
every suite belonging to tasks 21, 22, 35, 36, 37, 39 unless an instrument of theirs fires.

## Acceptance criteria

| | | ID |
|---|---|---|
| **AC1** | Each of the nine public paths resolves and renders **without a session**. | R28, L14 |
| **AC2** | None of the eleven authenticated paths serves content **without a session**. | R28, L14 |
| **AC3** | **AC2 turns red when the guard is removed.** Today AC2 is true because no authenticated route has an element — eleven of eleven are `{ path }` — so a suite written against the current table would pass with no production code. | R28 |
| **AC4** | **With** a session, an authenticated path is served. The guard is a split, not a deny. | R28 |
| **AC5** | The two sets are disjoint and together exactly §4c's declared set; counts **derived from `routes.tsx`**, not written down. | L14 |
| **AC6** | The guard lives in `auth/`; `app/` imports it and never the reverse. | design.md:203 |
| **AC7** | The guard stores nothing, writes nothing, sends nothing. | L16, L3 |
| **AC8** | A blocked route renders **no text at all**. | L11 |
| **AC9** | Adding a path to one list without the other **fails a test**. | L14 |
| **AC10** | A sessionless visit to a public path performs **no navigation**: "served", not "bounced and returned". | R28 |
| **AC11** | Instruments in the sibling suite are **grown, not bypassed**. | the handoff pattern |

## Required tests

**Named:** `publicRouteSetIsExactlyTheDeclaredSet` → R28 (`app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx`).

**Boundaries:** a public path with a query string (`verify?token=…`, per §4c's 2026-10-19 note) · a public
path with a trailing slash · an authenticated path measured both with and without a session in the same
suite · the catch-all unchanged · the guard under `StrictMode`, as every suite here mounts.

**Not this task's:** `unauthenticatedRoutesRedirectToSignIn` (task 41) ·
`homeRouteRendersLandingScreenAndManagesFocus` (task 174). Both appear in the generated header because the
generator injects the task's whole requirement neighbourhood.

## Constraints

- **Module boundaries.** `app/` → `auth/` is permitted and already used. `auth/` must not import from
  `app/` for anything but the published route constants, which it already does (`cleanReauth.ts`,
  `endSession.ts`).
- **Security.** The only question available is "is a token held". A held token does **not** prove the
  account is usable — a SUSPENDED account keeps a working token, and `/accounts/me` is the only thing that
  can say otherwise (task 31). This task must not claim more than it checks.
- **Null handling.** `accessToken.current()` returns `string | null`; empty string is not a session.
- **No copy.** A blocked route renders nothing, so no catalogue entry is added.
- **Performance / threading / transactions.** Not applicable — a synchronous read in a browser.
- **One spelling.** No path string may be re-spelled; everything derives from `routes.tsx`.

## Open questions

**No blockers.** Four items carried forward, each recorded with its evidence in phase 1 and none of which
stops this task:

1. **AC3's measurement** is the task's real design content, and it is phase 3's to pick: the criterion is
   fixed here, the method is not.
2. **Every reload will land on the gate** once task 42 attaches the authenticated shell, because tokens are
   memory-only and silent re-authentication is unbuilt (`accessToken.renew()` always rejects). Spec-
   consistent per L3's Q5 resolution; the restoring half does not exist. Named so task 42 does not read it
   as a defect in T40.
3. **Spec drift at the cited paragraph:** `design.md:137` still says "PROPOSED … pending Q6" while
   `package.md:368` records Q6 resolved on 2026-10-10.
4. **§4c must be assembled** from the bullet at `:138-140` plus the 2026-10-19 note; the four reserved
   names are explicitly out of this revision.
