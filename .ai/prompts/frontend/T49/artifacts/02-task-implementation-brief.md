<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief). The ONLY specification later phases use. -->

# frontend · T49 · Task Implementation Brief

## Task

**T49 — Verify failure is uniform.** Invalid, expired, used, or deleted-account tokens show one uniform
failure. Verifies `verifyFailureShowsOneUniformMessage`. Cites **R4**.

## Purpose

`/app/verify` shows the verified state (T48) and **nothing at all** when verification does not succeed: the
screen holds `idle` for the round trip and keeps holding it after a failure. A visitor whose link is expired,
already used, or truncated sees a heading and empty space, with no way forward. R4 closes that with one
uniform failure message **and a neutral resend action**.

## Scope

**In** — the failure branch of `VerifyScreen`; its message and action copy; the named suite; replacing T48's
`PINS A LIMIT` handoff case.

**Out** — the resend **form** itself (tasks 50/51); any failure copy that names a reason; the password-reset
reuse (task 54); the contract's missing failure response (OQ-T48-4); R-T48-1's gateway hazard.

## Business rules

| ID | |
|---|---|
| **R4** | If the verification request fails for any reason, show one uniform failure message with a neutral resend action. |
| **R3** | T48's success branch — must not be disturbed, and must not be preceded by a flash of failure copy. |
| **R15** | Not built here, but its text makes R4's message shared copy with a second consumer (task 54). |

## Locked decisions

| ID | |
|---|---|
| **L4** | Copy, redirects, styling **and timing** identical across bad, expired and used tokens and across account states. No lockout timers. |
| **L11** | WCAG AA, keyboard operation, managed focus, **live regions for async results**, catalogue-only strings. |
| **L6** | Generated client only. |
| **L14** | Route paths verbatim in §4c; `/app/verify` already attached, `resend-verification` declared with no element. |
| **L16** | The only `sessionStorage` carve-out; the verification token is not on it. |
| **L9**, **L17** | Unchanged by this task. |
| **D7** | Every rendered catalogue key is measured. |

## Dependencies

`createPublicAuthApi()` (`src/api/client.ts`) · `POST /accounts/verify-email` (`security: []`, body
`{ token }`, declares `204` only) · primitives `Screen`, `Heading`, `StatusRegion`, `Text`, `TextLink` ·
`src/i18n/catalogue.ts`.

**openapi-fetch resolves on HTTP errors** and rejects only on transport failure, so an HTTP failure arrives as
`response.ok === false` and the `.then` rejection arm — `() => {}` in T48 — sees network faults only. **Both
arms are in scope.**

## Inputs

`?token=<token>` from `useSearchParams`; the response from the one POST, or a transport rejection.

## Outputs

A rendered failure state: one catalogue sentence in the live region plus a neutral resend action.

## State changes

Component state only. **No storage write, no navigation, no second request.** The token remains stored
nowhere, logged nowhere, rendered nowhere.

## Files to create

| | |
|---|---|
| `frontend/src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | The named suite (`package.md:108`). |

## Files to modify

| | |
|---|---|
| `frontend/src/features/account/VerifyScreen.tsx` | Split the state machine; render the failure branch. T48's D1 made this file shared. |
| `frontend/src/i18n/catalogue.ts` | The failure sentence and the action label. |
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | Its `PINS A LIMIT` case (`:267`) **must fail**; replace it with uniform-failure assertions, **do not delete it** — its own comment says so. |

## Files NOT to modify

`frontend/src/app/routes.tsx` (T48 attached the element; no path changes) · `spec/**` · `contracts/**` ·
`frontend/src/app/ui/**` (no new primitive is needed; `Banner` is the header landmark, not a message banner) ·
`src/auth/**`.

**And, measured rather than assumed — the four design-system pin files are genuinely NOT in scope this time:**
`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`,
`homeRouteRendersLandingScreenAndManagesFocus.test.tsx`, `unauthenticatedRoutesRedirectToSignIn.test.tsx`,
`registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx`. `RENDER_SITES` is a list of **files**, and
`features/account/VerifyScreen.tsx` is **already on it**; the pinned union is a **file-set** union of size 23,
which a new primitive *call* inside an existing file does not change. **This is the FU-T48-1 check, done
up front instead of discovered at Phase 5.**

**The one condition that would reverse that:** if Phase 3/4 decides the resend action needs its **own new
component file**, that file becomes an eleventh render site and all four pins fire. Flagged so it is a
decision, not a surprise.

## Acceptance criteria

| AC | |
|---|---|
| **AC1** | A rejected token (`400`) renders the uniform failure message from the catalogue. |
| **AC2** | `400`, `404`, `410`, `429`, `500`, with and without a problem body, render **identical DOM**. |
| **AC3** | A transport failure renders the same uniform failure. |
| **AC4** | The failure state carries a neutral resend action. |
| **AC5** | The action's presence, label and target do not vary by failure reason. |
| **AC6** | **A valid token never renders the failure copy, not even for one frame.** |
| **AC7** | The sentence is announced via `StatusRegion`, mounted before it has content; focus per the Phase 3/4 decision. |
| **AC8** | New catalogue keys render as literal English and are measured; no untranslated key reaches the DOM. |
| **AC9** | One call through `createPublicAuthApi()`; no `Authorization`; no bare `fetch`. |
| **AC10** | **In the failure state** the token appears in no DOM, no storage and no console. |
| **AC11** | T48's `PINS A LIMIT` case replaced with uniform-failure assertions, not deleted. |
| **AC12** | Primitives only; no lowercase tag, no `className`/`style`. |
| **AC13** | A `429` renders the same uniform failure as a `400`, so R4 does not depend on the rate-limit path staying dormant. |
| **AC14** | Whole suite green **and each touched file green when run alone**. |

## Required tests

Named: **`verifyFailureShowsOneUniformMessage`**.

Plus: each of `400/404/410/429/500` identical DOM · a problem body with `type`/`title`/`detail` changes
nothing · a source scan forbidding reads of `error`, `.title`, `.type`, `.detail`, `.status` · transport
rejection · **a valid token shows no failure copy at any point before success** · a tokenless arrival
(pending OQ-T49-2) · token absent from DOM, storage and console **in the failure state** · the action is
keyboard-reachable with an accessible name · catalogue keys as literal English and the key-set pin.

## Constraints

- **Security.** The token is a bearer-equivalent credential arriving in a URL: not stored, not logged, not
  sent to analytics, **not rendered** — and this task adds DOM, so that last one needs a new instrument.
- **Enumeration safety (L4).** Nothing on screen, including the action, may vary with the failure reason. The
  structural guard is that the screen **must not read the response body at all**; reading `type` or `title` is
  how uniform failure stops being uniform.
- **No new request.** The one-POST guard stays as T48 left it: set before the request, after the no-work
  return. A resend action navigates; it does not re-POST here.
- **Module boundaries.** `features/account/` imports nothing from `auth/`. Backend access through the
  generated public client only.
- **Null handling.** A missing or empty token makes no request (T48's AC12); what it *renders* is OQ-T49-2.
- **Performance / thread-safety / transactions.** Not applicable — browser, single-threaded, no persistence.

## Open questions

| ID | | Blocker? |
|---|---|---|
| **OQ-T49-1** | **R4's resend action has nowhere to go.** `/app/resend-verification` is declared with **no element** until tasks 50/51, so a link lands on a blank document today. R4 requires the action, so it cannot be silently dropped. Precedent exists both ways (T43 wrote a case *against* navigating to an element-less path; T47 attached the element and inverted it). | **Yes** |
| **OQ-T49-2** | **R4's trigger does not literally reach a tokenless arrival** — no request was made, so nothing "failed". T48's OQ-T48-3 decided this task covers it and built its handoff case on that reading. Ratify the extension, or leave a tokenless arrival rendering nothing? | **Yes** |
| **OQ-T49-3** | **Task 54 reuses this copy** (`tasks.md:88`; R15's own text). T48's catalogue group is `verify.*`. Where does the shared sentence live? | No |
| **OQ-T49-4** | **Focus when the awaited result lands.** T48 focuses the heading on arrival; nothing in the package says whether an async result should move focus, and moving it unprompted is itself a hazard. | No |
| **OQ-T49-5** | **What "neutral" constrains** beyond not revealing the reason — whether words like "expired" are barred from the message itself. | No |

### Not an open question — a defect to avoid

**T48's OQ-T48-3 wording is a trap if taken literally.** `VerifyScreen.tsx:61` initialises `status` to
`'idle'` and `:78` moves it to `'success'` **only on the response**, so `idle` is held for the whole round
trip and there is deliberately no `verifying` key (D3). Rendering the uniform failure for `idle` would
**flash the failure message at every successful visitor.** This task must **split the state machine**, not
relabel `idle`. **AC6 holds it.**

**Brief complete. Next is Phase 3 — Design Challenge (Kimi 2.7).**
