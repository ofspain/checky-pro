<!-- MODEL: Claude Opus 5 — Phase 4 (Human Approval / Freeze) -->

# frontend · T42 · Phase 4 — Freeze

| | |
|---|---|
| **Service** | `frontend` · **Task** T42 — Authenticated shell |
| **Consumes** | `artifacts/02-task-implementation-brief.md` (amended) · `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-freeze-task.md` — **the frozen brief phases 5–13 implement against** |

> **Sequence matters here and is recorded rather than smoothed over.** Kimi's phase-3 artifact was written
> against the brief **before** the owner closed OQ-T42-2, and it landed in `origin` while that amendment was
> being written. So **F7 recommends the opposite of what the owner decided** — it is not wrong, it is
> earlier. Every other finding is read on its own merits, and all sixteen citations were checked against
> source.

## Part 1 — Disposition of the sixteen phase-3 findings

| | verdict | |
|---|---|---|
| **F1** OQ-T42-1 → reading (b) | **ACCEPTED, and narrowed by the owner** | Correct that (a) would add links to ten routes with no screen. **No navigation links.** Where F1 says *"no sign-out control"*, the owner has since decided otherwise — see **D1** |
| **F2** AC9 vs AC7 vs the Out list | **ACCEPTED as a real contradiction; its recommendation (1) REJECTED in favour of (2)** | Verified: no primitive renders `<header>`, `<nav>`, `<aside>` or `<footer>`; `Screen` is `<main>` (`ui/Screen.tsx:17`), `Surface` a `<div>` (`:17`), `StatusRegion` a live region (`:19`), `Text` a `<p>` (`:14`). **D2** |
| **F3** no second `<h1>` | **ACCEPTED** | `Heading` is forbidden in the layout. **D3** |
| **F4** the layout is passive | **ACCEPTED** | No ref, no `.focus()`, no `autoFocus`, no non-negative `tabIndex`. This is T174's I9 hazard, which exists because `Heading` made every `<h1>` focusable. **D4** |
| **F5** route-table shape | **ACCEPTED verbatim**; citation checked (`app/routes.tsx:89-95`) and correct | **D5** |
| **F6** instrument predictions | **ACCEPTED, and INCOMPLETE** — two instruments it does not name will fire | **D6** |
| **F7** do not add sign-out | **OVERTAKEN** by the owner's decision of 2026-11-14 | Its reasoning was right for its moment: *"doing so without an explicit owner decision is scope creep"*. The decision is now explicit, so the objection is spent rather than refused |
| **F8** `shell.brand` | **DECLINED** | Inventing a product name in a component is copy with no basis in the package. **D7** |
| **F9** describe prefix | **ACCEPTED** | New cases live under one describe named for this task. **D8** |
| **F10** a source scan | **ACCEPTED, amended** | Its forbidden list bans `auth/` outright; three named auth imports are now required. **D9** |
| **F11** T174's `main`-scoped assertions stay | **ACCEPTED** | Verified: `homeRoute…:142-151` reads `main.children`, which the chrome sits outside of |
| **F12** nested `Surface` | **ANSWERED BY D2** | With a `Banner`, the layout renders no second `min-h-dvh` |
| **F13** an app-wide `StatusRegion` | **DECLINED** | No AC requires it, `StatusRegion`'s type requires `children` (`ui/StatusRegion.tsx:16`), and a live region with nothing to announce announces nothing. **D10** |
| **F14** concrete ids for the two parameterised paths | **ACCEPTED** | Already AC1's text; T41's suite has `SAMPLE_ID` and `concrete()` |
| **F15** re-assert the empty-string token | **ACCEPTED** | And it is stronger now: the layout is the first thing behind the gate that renders a **control** |
| **F16** candidate mutants | **ACCEPTED as a starting set, not a sufficient one** | Four of its ten rows need different predictions and five ACs have no row in it. **D11** |

## Part 2 — The eleven decisions this gate settles

### D1 — The layout is a banner and an outlet: no navigation links, and a sign-out control

OQ-T42-1 resolves to **reading (b) plus the owner's sign-out affordance**. The layout renders a banner
region containing the sign-out control, and `<Outlet />`. **It carries no navigation links**: ten of the
eleven authenticated paths render an empty document, and a link to a dead end is worse than no link — F1's
reasoning, which the sign-out decision does not touch. Navigation arrives with the first task that owns a
destination worth linking to, and D5's route shape means that task adds a link, not a restructure.

### D2 — A **ninth primitive**, `Banner`, rendering `<header>`. F2's option (2), not option (1)

F2's contradiction is real and option (1) — render the layout through `Surface` and relax AC9 — is the wrong
side of it, for three reasons it does not weigh:

1. **The chrome now holds a control.** A sign-out button in an anonymous `<div>` is findable by eye and not by
   landmark. L11 is *"keyboard operation"* and *"WCAG AA"*, and the one thing the chrome contains is the one
   thing a keyboard user needs to find.
2. **Rule 2 makes a consumer-set landmark impossible.** `ui/index.ts` rule 2: *"No primitive accepts the
   attribute that is its own contract. `role`, `aria-live`, `tabIndex`, `type="button"` and the heading level
   are set inside."* A landmark is a role. So a landmark can exist **only** inside a primitive — the
   alternative is not "a cheaper landmark", it is "no landmark".
3. **T173 wrote the procedure for this.** Its primitive pin's own comment is *"the eight primitives. A ninth
   must be added deliberately."* That is an invitation with a cost attached, not a prohibition. Option (3) —
   one lowercase tag in a render site — would be the **first exception to §6e**, whose whole value is that it
   has none.

`Banner` accepts `{ children }` and nothing else. It renders `<header>`; it does **not** set `role="banner"`,
because `<header>` maps to that role natively when it is not inside `article`, `aside`, `main`, `nav` or
`section`, and redundant ARIA on a native element is the worse of the two. **The mapping condition is part of
the contract and must be asserted**: the `<header>` is a child of the shell's `<div>`, never inside `<main>`.

**Cost, to be paid in the open:** T173's pins move — primitives **8 → 9** (`screensRender…:212`), lowercase-tag
bearers **7 → 8** (`:231`), the union **14 → 16** (`:246`), render sites **6 → 7**. Every one is *"must grow
to admit new state"*, the permitted reason; not one is loosened.

### D3 — The layout renders neither `Screen` nor `Heading`

`Screen` is `<main>` and its own header states the invariant: *"It nests nowhere … so there is exactly one
`<main>` in the document at any time. A second one would be invalid and would split the landmark."*
`Heading` is `<h1>`, and T174's screen already renders the page's only one (F3). The layout's own copy, if it
ever has any, is `Text`.

### D4 — The layout is passive about focus

No ref, no `.focus()`, no `autoFocus`, no non-negative `tabIndex`, no focus effect of any kind. T174 made
every `<h1>` focusable and wrote the I9 guard for exactly this hazard; **that guard grows to cover the
layout**, rather than this task inventing a second one.

### D5 — The route shape is F5's, exactly

```tsx
{
  element: <RequireSession />,
  children: [
    {
      element: <AuthenticatedLayout />,
      children: AUTHENTICATED_ROUTE_PATHS.map((path) => {
        if (path === HOME_PATH) return { path, element: <HomeScreen /> };
        return { path };
      }),
    },
  ],
}
```

One pathless layout route inside the gate. `RequireSession` stays the gate and is not edited; the eleven
paths keep their spelling and order; nothing path-attached is added, which is why the screen-count pins
should stay still — **should**, and D6 is about that word.

### D6 — Two instruments F6 does not name **will** fire, and they are named now

F6's list is right as far as it goes. It omits the two that T174 already had to edit for the same reason:

- `auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx:1246`
- `auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx:863`

Both assert ``success.text`` is exactly ```${en.home.heading}${en.home.explanation}` ``, and `text` is
**`document.body.textContent`** (`identityErrors…:919`), not something scoped to `<main>`. The sign-out
label lands inside it. Both must **grow to admit the chrome**, not be loosened to a `contains`.

Neither R30 nor L4 is threatened by that: both suites compare account-failure states against each other on
the **public** side, and the chrome exists only behind the gate.

**And no prediction here is to be trusted without a run.** T174's freeze predicted no T41 test would break
and six did (its D-5). Phase 6 measures every instrument and records each wrong prediction as a deviation.

### D7 — The catalogue gets one group and one key: `shell.signOut`

F8's `shell.brand` is declined. The package names no product string for a header, and inventing one puts
copy in a component's hand that the owner cannot change without a code edit — the thing T174's D7 was
written to avoid. `'Sign out'` is not invented: it is the mirror of `signedOut.action`'s `'Sign in'`, already
in the catalogue. If the owner wants a brand line later, it is one catalogue key and one `Text`.

### D8 — The named test's new cases live under `describe('T42 — the authenticated layout', …)`

T41's file reserved this (`unauthenticatedRoutesRedirectToSignIn.test.tsx:20-22`): *"task 42 adds
authenticated screens behind the gate and will extend this file, and a failure naming 'AC1' must say whose
AC1 it is."* Verified in source.

### D9 — The layout's import list is **three** auth modules and the scan says so

F10's scan is adopted with its forbidden list amended, because the owner's decision requires reaching into
`auth/` and the honest form of that is a short allowlist rather than a ban with exceptions in prose:

| permitted | why |
|---|---|
| `endSession` from `auth/endSession` | the button's action. R19 and R57 are satisfied **inside** it |
| `signOut` from `auth/signOut` | the **receiving** tab's action. `api/crossTabSignOut.ts:130-133` prescribes it: *"Signing out here must NOT broadcast, or two tabs would ping-pong - which is why `signOut()` sets the latch and `endSession()` does the broadcasting"* |
| `RedirectToSignIn` from `auth/RedirectToSignIn` | how the receiving tab **leaves**, see D11 |
| `listenForSignOut` from `api/crossTabSignOut` | the listener |

Forbidden, and asserted pattern by pattern: `accessToken`, `idToken`, `takeIdToken`, `broadcastSignOut`,
`markSignedOut`, `markSignedIn`, `hasSignedOut`, `END_SESSION_PATH`, `globalThis.location`, `history`,
`document.cookie`, `sessionStorage`, `localStorage`, `indexedDB`, `fetch(`, `XMLHttpRequest`, `userinfo`,
`accounts/me`. The layout must not compose what `endSession()` composes: **the order is its criterion**
(`auth/endSession.ts:89-96`).

### D10 — No app-wide `StatusRegion` (F13 declined)

No AC requires it, its type requires `children`, and a live region mounted with nothing to say is a
measurement nobody takes. The first task with an app-level async result owns it.

### D11 — How a receiving tab leaves: it renders `RedirectToSignIn`, it does not navigate itself

**This is OQ-T42-4, and it is settled here.** The gate is *"a read at render, not a subscription"*
(`auth/RequireSession.tsx` header, OQ-T40-1): a listener that clears the token changes nothing on screen,
and R22 forbids showing a screen to a visitor who no longer has a session. Three shapes were weighed:

1. **`useNavigate()` to the sign-in path** — rejected. It duplicates the destination, the return target and
   the replace-vs-push decision, all three of which `RedirectToSignIn` owns, and T41 built it to be the one
   place they live.
2. **Force the gate to re-evaluate** — impossible without editing it: the gate is the layout's **parent**, so
   no state the layout holds can re-run it, and `RequireSession.tsx` is a file this task may not modify.
3. **Render `<RedirectToSignIn />` in place of `<Outlet />`** — **chosen.** One boolean of state in the
   layout, and every decision about *where* stays with the component that owns it.

So the listener's callback is `() => { signOut(); setSignedOut(true); }` and the layout renders the redirect
rather than the outlet once that flag is set. The listener is installed once per mount and **closed on
unmount** — `listenForSignOut` returns `{ close() }` and it is idempotent.

**One consequence is accepted rather than hidden.** `signOut()` clears the return target (T35) and
`RedirectToSignIn` then remembers the path this tab was on. The net effect is that a cross-tab sign-out
behaves exactly like an expired session, which is what L1's allowlisted return target exists for. Phase 6
**must measure** whether any T35 or T41 instrument pins the cleared target across a redirect; if one does,
it is a finding, not a licence to loosen it.

## Part 3 — The frozen brief

### Task
`spec/frontend/tasks.md:85` (task 42): *"**Authenticated shell.** A session renders the shell and the
authenticated routes. Verifies `unauthenticatedRoutesRedirectToSignIn`. Cites R28. Blocked on 173, 174."*
Both blockers closed (T173 2026-11-12, T174 2026-11-14).

### Purpose
Give the eleven authenticated paths a layout to nest in, and give the sign-out path its first caller. Ten of
the eleven paths render an empty document today, and `signOut()`, `endSession()`, the signed-out landing and
cross-tab propagation have been built and unreachable since tasks 35–39.

### Scope
**In:** `app/ui/Banner.tsx` (the ninth primitive) · `app/AuthenticatedLayout.tsx` · its pathless layout route
inside `RequireSession` · `shell.signOut` in the catalogue · the sign-out control calling `endSession()` ·
the cross-tab sign-out listener and the redirect it causes · the growth of the instruments in D6 and D2 ·
the named test's extension under D8's describe.

**Out:** screens for the other ten paths · navigation links · any new route, path, endpoint, contract, config
key or storage key · any data fetch (OQ-T33-2) · anything identity-dependent (name, email, role, account
state) · TanStack Query or Zustand (OQ-T173-5) · a session subscription on `accessToken` (OQ-T40-1) · editing
`AppShell.tsx`, `mountShell.tsx`, `RequireSession.tsx`, `RedirectToSignIn.tsx`, `returnTarget.ts`, any
**existing** file under `app/ui/`, `app/styles.css`, or anything under `spec/` · an app-wide `StatusRegion`
(D10) · task 38, which is still *"Blocked on Q15"*.

### Business rules
**R28** (both clauses built; this task owns the positive half) · **R19** (this task supplies the *"when"* it
is conditional on) · **R22** (no half-authenticated screen; sign-out propagates to every tab) · **R57** (the
end-session navigation is SAS's, never the shell's) · **R20 / R29 / L16** (nothing identity-dependent,
nothing stored).

### Locked decisions
**L11** · **L12** · **L13** · **L14** · **L18** · **L1** · **L3** · **L4** · **L9** · **L10** · **L16**, plus
`agents.md`'s catalogue and accessibility rules, plus **`ui/index.ts` rule 2** and **T173 §6e**.

### Files to create
`frontend/src/app/ui/Banner.tsx` · `frontend/src/app/AuthenticatedLayout.tsx`

### Files to modify
`app/routes.tsx` (D5) · `app/ui/index.ts` (one export) · `i18n/catalogue.ts` (D7) ·
`auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` (the named test) ·
`app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` (four counts, D2) ·
`auth/signOutClearsMemoryAndEndsSasSession.test.tsx` (`PERMITTED_IMPORTERS`, which was written empty for this
day) · `auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx:1246` ·
`auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx:863` ·
`app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` (the I9 guard grows to the layout; `main`-scoped
assertions do **not** change) · `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:866` and
`api/signOutPropagatesToAllTabs.test.ts:9-12` (comments this task makes false) · anything else **only if
measured**.

### Acceptance criteria
AC1–AC15 as in the amended brief, with these three fixed by this gate:

| | |
|---|---|
| **AC9** (restated) | The chrome is a **`<header>` banner landmark**, rendered by `Banner`, outside `<main>` and not inside `article`/`aside`/`nav`/`section`; every interactive element in it is keyboard-reachable with a visible focus ring |
| **AC7** (extended) | `Banner` is the ninth primitive: the primitive list is nine, the lowercase-tag bearers eight, the union sixteen, the render sites seven — each naming **task 42** |
| **AC14** (fixed by D11) | The layout installs the listener once per mount, closes it on unmount, and on a sibling's broadcast calls `signOut()` and renders `RedirectToSignIn` instead of the outlet |

### Required tests
`unauthenticatedRoutesRedirectToSignIn` (`package.md:151` → R28), extended under D8's describe, plus the
instrument growth above. A mutation harness (**twelfth copy**) with at least one row per criterion AC1–AC15 —
F16's ten rows are the starting set and are **not** sufficient: nothing in it aims at AC12, AC13, AC14, AC15
or the `<header>`-nesting half of AC9. Standing rules carry: commit tests before running it; BROKEN is not a
kill; a mutant must measure its own description; score canaries apart; controls measure precision.

### Constraints
D9's import allowlist · D4's focus passivity · no storage, no network, no token read · `<Outlet />` must
tolerate a matched child with no element, which is ten paths' state today · L12: tasks 43 onward nest inside
this and must not have to restructure it to add a link.

### Open questions
**No blockers.** OQ-T42-1 **closed by D1**; OQ-T42-2 **closed by the owner**; OQ-T42-4 **closed by D11**;
OQ-T36-5 closes for its sign-out half when the layout lands, and stays open for its data half as **OQ-T33-2**.

Carried, each with an owner: **OQ-T42-3** (ten element-less paths, each a later unit's) · **OQ-T33-2** ·
**OQ-T40-1** · **OQ-T173-5** · **OQ-T173-4 / OQ-T174-3** · **OQ-T174-5** · the `design.md:137` vs
`package.md:368` route-count drift · task 38's Q15 block.
