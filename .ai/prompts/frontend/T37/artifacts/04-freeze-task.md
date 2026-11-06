<!-- MODEL: Claude Opus 5 — Phase 4 (Human Approval / Frozen Task Brief) -->

# frontend · T37 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-freeze-task.md` |

Fourteen findings. **Eleven accepted**, **one accepted with its premise corrected**, **one rejected**,
**two no-action**. Kimi is right about the central thing: **phase 2 was not actually frozen.** It named three
catalogue keys without words and deferred a navigation decision to phase 5, and a brief that defers cannot be
the thing later phases are measured against. That is fixed here.

---

## F1 — ACCEPTED. The copy, in words, and why each one.

A brief that names `heading`, `explanation` and `action` without their strings leaves AC3, AC4, AC5 and AC12
untestable. Frozen:

```ts
signedOut: {
  heading: 'Signed out',
  explanation: 'Sign in again whenever you are ready.',
  action: 'Sign in',
},
```

Each justified, because derived copy has to carry its reasoning where specified copy would carry a citation:

- **`heading: 'Signed out'`** — two words, naming the **event** and no cause, exactly as `signIn.sessionEnded`
  does. It is a page label, not a status report: it does not say *"you have been signed out"*,
  *"successfully"*, or *"of all devices"*, each of which would be an assertion about state this screen cannot
  check (F3).
- **`explanation: 'Sign in again whenever you are ready.'`** — **an invitation, not a claim.** This is the
  clause that carries F3's burden: it says nothing about what is currently true of the visitor's session, so it
  is true for someone who just signed out and for someone who typed the URL while still holding a token.
  *"Your session has ended"* was considered and rejected for exactly that reason — it is `signIn.sessionEnded`'s
  sentence, and it would be false in the second case.
- **`action: 'Sign in'`** — distinct from `signIn.action` (`'Continue to sign in'`), which is the button that
  hands the visitor to SAS. This one only returns them to the sign-in screen, so it must not borrow the word
  that promises the hand-over. It matches `signIn.heading`, which is the screen it leads to — coherent rather
  than duplicated.

No cause anywhere, no timing claim, no account state: identical whether the visitor left deliberately, was
signed out by an expired session, or holds a locked, suspended or deleted account (L4).

**OQ-T37-1 stays open as a wording question only.** The owner may change the words; the shape is L4's.

## F3 — ACCEPTED as an explicit constraint.

Added to Constraints: **every string must be true both for a visitor who has just signed out and for one who
typed the URL while still holding a token.** This is what rules out the natural-sounding wordings, and it is
the reason `explanation` is an invitation. A case asserts the rendered text is byte-identical in both
situations.

## F2 — ACCEPTED, and the choice is frozen: a `Link`.

Correct that a frozen brief cannot defer this. **`<Link to={`/${SIGN_IN_PATH}`}>`**, and the reasons are not
only stylistic:

- It is an **in-app** navigation to a public route, which is the one navigation in this SPA that should *not*
  leave the document. Every other hand-over in the package (`SignInScreen`, `endSession`) is deliberately a
  full-page navigation to SAS; this one is the opposite, and using a router `Link` says so.
- A real anchor gives middle-click, open-in-new-tab, copy-link and keyboard activation for free. A button
  wired to `navigate()` silently removes all four.
- It needs no state, no handler and no `useNavigate`, which keeps the component a pure function of the
  catalogue — the thing AC7 asserts.

**T37 is the first use of `Link` in this package** (measured: only `RouterProvider`, `createBrowserRouter`,
`useNavigate` and `useSearchParams` appear today).

## F4 — ACCEPTED as a constraint; its **premise corrected**, because the precedent already exists.

F4 warns that `SignedOutScreen` importing `SIGN_IN_PATH` would create a new cycle `routes → SignedOutScreen →
routes`. The discipline it asks for is right. The novelty is not: **`CallbackScreen.tsx:4` already imports
`SIGN_IN_PATH` from `../app/routes`** and uses it at `:99`, inside a function, and `routes.tsx:6` imports
`CallbackScreen`. So `routes → CallbackScreen → routes` is an existing, working cycle **of exactly this
shape**, and has been since task 17.

That changes the instruction from "avoid a new hazard" to "follow a proven pattern": the binding may be
imported at module scope — ES module bindings are hoisted — and must only be **read** inside the component
body, never at module scope. A source scan pins it, with a control.

## F5 — ACCEPTED, and better than the finding proposes.

`endSession.ts:56` holds `const SIGNED_OUT_PATH = 'signed-out';`, declared privately because `routes.tsx` was
closed to T36 (**T36-D2**). T37 publishes the constant, so `auth/endSession.ts` joins **Files to Modify**: the
private const is deleted and `SIGNED_OUT_PATH` is added to the import already on `:1`
(`import { APP_BASENAME } from '../app/routes';`), which costs nothing and creates no new edge.

Kimi asks for "a test that asserts the two spellings are equal." **Rejected as unnecessary, which is the
stronger outcome:** after this there is only **one** spelling, so there is nothing to drift. T36's AC4 case
already asserts the derived target's segment is in `PUBLIC_ROUTE_PATHS`, and that case now passes through the
same constant the route table uses. A drift test for a single source of truth is a test about nothing.

**T36-D2 is retired by this edit**, which is recorded in T37's phase 12 rather than by amending T36's closed
artifacts.

## F6 — concern ACCEPTED, rename REJECTED, with the reason.

The name does over-promise: `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` will hold one route of nine until
task 40 finishes it.

The rename is rejected because of what it costs. `signedOutRouteIsPublicAndRendersItsScreen.test.tsx` maps to
**no entry in `tasks.md` or `package.md` §8**, so a reader auditing coverage — *which named tests exist?* —
finds a file they cannot trace to a task, and T40 then creates the properly-named file anyway, leaving **two
files covering this one route**. One file that grows is better than two that overlap.

So the shared name is kept and the file's header states, in its first lines, exactly which clause T37 proves
(this route is public and renders its screen) and which clause **task 40 must add** (that these nine and only
these nine are reachable without a session, which needs the auth gate that does not yet exist). F14 is
answered by the same measurement: `tasks.md:65` does name `publicRouteSetIsExactlyTheDeclaredSet` for T37.

## F8 — ACCEPTED, now that F2 is frozen.

The action is a `Link`, so the assertion is on the anchor: exactly one element with role `link`, its `href`
ending `/app/sign-in`, and it is the **only** interactive element on the screen — no button, no second link.
Asserting the *absence* of other actions is what makes AC5's "only action" clause real rather than decorative.

## F9 — ACCEPTED.

A source scan asserts `SignedOutScreen.tsx` imports neither `useSearchParams` nor `useLocation` and never
reads `location.search`. This is the structural half of AC6: the behavioural cases prove identical rendering
across four query strings, and the scan proves the component cannot be reading one. With a control.

## F7, F11, F12 — ACCEPTED as documentation.

- **F7** — the edit to `routesResolveUnderAppPrefixOnly.test.tsx` (task 16's) is **additive**: one string
  appended to `SCREENS_ATTACHED_SO_FAR`, marked `TASK 37`, with every original case unchanged. It is permitted
  under the rule T36's phase 9 wrote: an instrument that **must grow to admit new state**.
- **F11** — AC9's count pin is **deliberate** and will fail the day the next screen is attached, which is the
  handoff working. The comment says so and names the list to extend, as task 16's message already does.
- **F12** — `SignedOutScreen` follows `SignInScreen`: `useTranslation()`, no props, keys under `en.signedOut`.

## F10, F13 — no action, as both findings conclude.

---

## Frozen brief — amendments only

### Files to create

- `frontend/src/auth/SignedOutScreen.tsx` — no props, no state, no handler; `useTranslation()` and a `Link`.
- `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — T37's clause only; header names T40's.

### Files to modify

| File | Change |
|---|---|
| `app/routes.tsx` | attach `<SignedOutScreen />`; publish `SIGNED_OUT_PATH` |
| `i18n/catalogue.ts` | the `signedOut` section above, placed after `callback` — "in the order a visitor meets them" |
| `auth/endSession.ts` | **F5** — delete the private `SIGNED_OUT_PATH`, import it from `routes.tsx` |
| `app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` → three, marked `TASK 37` |

### Acceptance criteria

AC1–AC12 from phase 1, with three sharpened:

| # | Criterion |
|---|---|
| **AC5** | The way back to sign-in is a **`Link`** whose `href` ends `/app/sign-in`, and it is the **only** interactive element on the screen (F2, F8). |
| **AC6** | Identical rendering with no query, with `?state=…`, with an unknown parameter, and with both — **and** the component imports no `useSearchParams`/`useLocation` and reads no `location.search` (F9). |
| **AC13** | **New.** Every rendered string is byte-identical whether or not a token is held, so no string asserts session state (F3). And `SignedOutScreen.tsx` reads `SIGN_IN_PATH` only inside the component body, never at module scope (F4). |

### Constraints — added

- **Truth in both arrivals** (F3): no string may assert the visitor's session state.
- **Module-scope reads are forbidden** for anything imported from `routes.tsx` (F4), following
  `CallbackScreen.tsx`'s proven pattern rather than inventing one.
- **One spelling of the route** after F5: the constant lives in `routes.tsx` and nowhere else.

### Open questions

**OQ-T37-1** — wording only; the shape is L4's. **OQ-T37-2** — frozen as (a), unchanged.
**OQ-T37-3** — frozen, shared file name kept (F6). **OQ-T37-4** — frozen, indifference, now with F9's scan.
**OQ-T37-5** — nothing calls `endSession()`; eighth task affected by **OQ-T36-5**.
**OQ-T37-6** — task 38's "Blocked on Q15" appears mis-assigned; `spec/` is not this task's to edit.

**Phase 4 complete. Brief frozen — this time with the words in it.**
