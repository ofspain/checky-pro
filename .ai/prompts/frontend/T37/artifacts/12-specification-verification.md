<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T37 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/11-review-resolution.md` |
| **Produces** | `artifacts/12-specification-verification.md` |

## Verdict

**PASS**, with **seven deviations** recorded below, one of which is in the task's own sentence.

| Gate | Result |
|---|---|
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **29 passed (29)** |
| `vitest run` | **1691 passed / 1 failed of 1692** — the known `oxlint` host case |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `07-mutation-harness.py` | **35/35 killed, 0 survivors, 0 broken** |

## `tasks.md:65` — clause by clause

> **37. Signed-out landing.** After sign-out the user lands on `/app/signed-out`. Verifies
> `publicRouteSetIsExactlyTheDeclaredSet`. Cites R19, L14.

| Clause | Verified by | Status |
|---|---|---|
| the route is `/app/signed-out` | `routes.tsx:24` publishes `SIGNED_OUT_PATH = 'signed-out'` under `APP_BASENAME = '/app'`; AC10 asserts the value; task 16's suite asserts the prefix | **MET** |
| the user **lands** there — a screen, not a blank page | AC1, through the real route table rather than by rendering the component | **MET** |
| **after sign-out** | — | **NOT PROVABLE.** See D1. |
| verifies `publicRouteSetIsExactlyTheDeclaredSet` | the file carries that name and holds T37's half of the claim | **MET, partially owned** — see D2 |

## Requirements

### R19 — *clear in-memory token state and end the SAS session through the OIDC end-session endpoint with a `post_logout_redirect_uri` from the allowlist*

T37 owns **the destination**, not the mechanism: the route that `post_logout_redirect_uri` names.
`design.md:164-165` records that auth-service's own defaults register
`http://localhost:5173/app/signed-out` for `checky-spa`, and `design.md:167` that `/connect/logout` with
the hint and the allowlisted target "ends the SAS session and redirects to `/app/signed-out?state=…`".

Two things follow, and both are tested:

- **The spelling must match the registration exactly.** A misspelling is a 400 from SAS, not a 404 in the
  SPA. AC10 pins the published constant, and **SL16** (renamed to `signed-off`) and **SL28** (imported but
  hardcoded in the URL builder) are both killed.
- **SAS appends `?state=…`.** `design.md:167` says so even though T36 sends none, so the screen must be
  indifferent to a query string it did not ask for. AC6 renders four arrival shapes and compares them
  byte for byte; **SL11** (the screen renders the opaque `state`) is killed by the render compare *and* a
  source scan.

R19's *clearing* half is task 35's and its *end-session* half task 36's; neither is re-asserted here, and
AC8 proves this screen does not do task 35's job on arrival — **SL13** makes that concrete, because a
landing that cleared would turn any cross-site link into a sign-out affordance.

### L14 — *SPA route set under `/app`; the paths are listed verbatim in §4c; public: … and the signed-out landing*

Compared literally against `design.md:139`:

```
§4c:  /app/sign-in  /app/callback  /app/register  /app/register/sent  /app/verify
      /app/resend-verification  /app/reset/request  /app/reset  /app/signed-out
code: sign-in  callback  register  register/sent  verify
      resend-verification  reset/request  reset  signed-out
```

Nine for nine, in order. **MET.** AC2 asserts membership in `PUBLIC_ROUTE_PATHS`, AC10 the length of nine,
and **SL15** (the route dropped from the public set) is killed. T37 added no route and removed none — L14's
"a new public route requires an ADR" is not engaged, because `signed-out` was already declared in §4c and
in code; what T37 added is its element.

### L4 — *enumeration-safe: copy, redirects, styling and timing identical across unknown, locked, suspended and deleted accounts, and across bad, expired and used tokens*

This is the requirement that shaped the copy, and it is the reason the screen says so little. Verified
from both directions:

- **It cannot distinguish anything**, because it reads nothing — the 26-row sweep (AC7) forbids every
  session primitive and store, with a probe per row. **SL25** (a legal `.current()` read branching to
  identical markup) is killed by the scan alone: no render comparison could see it.
- **The copy names no cause.** AC4's vocabulary scan with a control, and AC3/AC11's whole-text equality.
  **SL04** names a cause; **SL05** and **SL06** assert a state the screen cannot verify, in words the
  denylist cannot see — all three killed.
- **No timing channel**: the screen is synchronous, starts no timer and sends no request (AC7, **SL12**).

**MET**, and it is the criterion with the most instruments pointed at it.

### L3 — *tokens in memory only; never written to storage, logs or analytics*

T37 writes nothing anywhere (AC2's store watch, AC10, **SL13**), and renders neither token (AC11 retains
both and asserts both absent). **SL31** — the landing calling `accessToken.adopt()` — is killed by AC2,
AC8 and the sweep. **MET.**

### L11 — *accessibility and i18n on every screen: WCAG AA, keyboard operation, managed focus, catalogue-only strings*

| Clause | Status |
|---|---|
| catalogue-only strings | **MET** — AC3, plus a scan forbidding the literals in the component; **SL02** and **SL03** killed |
| keyboard operation | **MET** — a real `<Link>`, so activation, middle-click and open-in-new-tab come free; **SL08** (a button wired to `navigate`) and **SL29** (`role="button"`) killed |
| landmark and heading structure | **MET** — the phase 10 case, with phase 11's semantics; **SL19**, **SL20**, **SL21**, **SL32**, **SL33** killed |
| managed focus across redirect and MFA steps | **N/A** — a static screen with one link and no steps |
| live regions for async results | **N/A** — nothing async |
| WCAG AA **contrast and styling** | **NOT MET, and not T37's** — see D3 |

### L12, L16, L17, R57 — not engaged, checked anyway

- **L12 phase additivity** — T37 attaches an element to a route already declared. Nothing replaced. **MET.**
- **L16 the sessionStorage carve-out** — T37 contributes nothing to it; AC10 asserts storage is empty after
  arrival and AC2 that nothing was written. **MET.**
- **L17 / R57 service-worker boundaries** — `/connect/logout` is in the navigation denylist, which is task
  36's and is still pinned by its suite. `/app/signed-out` is an ordinary SPA route and belongs in the
  shell. **Unchanged by T37.**

## Deviations

| | Deviation |
|---|---|
| **D1** | **"After sign-out the user lands on `/app/signed-out`" is not provable.** Nothing in the SPA calls `endSession()`, so no visitor can arrive here by signing out; every case is a direct arrival, which AC12 states in the test itself. The route, the screen and the spelling SAS needs are all verified — the *journey* in the task's sentence is not. **OQ-T36-5**, eighth consecutive dormant task. The backend half is independently proven (`SasLoginIntegrationTest.endSession*`, `design.md:167`). |
| **D2** | **This file holds half the claim its name makes.** `tasks.md:65` and `:71` name `publicRouteSetIsExactlyTheDeclaredSet` for **both** task 37 and task 40. T37 proves one route is public and renders; task 40 must prove these nine *and only* these nine are reachable without a session — which needs an auth gate that does not exist, so it is not merely unwritten but currently untestable. Raised three times (phase 3 F6, phase 8 I4, phase 11 F5) and kept each time; the explanation is in the file header **and** the describe block. |
| **D3** | **L11's WCAG AA contrast and styling half is unmet** — there is no design system yet (O2 resolved to Tailwind + headless; task 173 owns it). This is true of every screen in the SPA, not of T37, and T37 adds no styling to be wrong about. |
| **D4** | **The copy is derived, not specified.** Nothing in `spec/frontend/` says what this screen contains. The words are justified clause by clause against L4 in the catalogue comment and frozen by three instruments. **OQ-T37-1** — the wording is the owner's call. |
| **D5** | **AC13 proves non-branching, not truthfulness.** A screen that renders identically with and without a token is not asserting session state. Without an auth gate that is the strongest available statement. Carried from phase 6 D3. |
| **D6** | **T37 edited two closed tasks' files** — task 16's test additively (`SCREENS_ATTACHED_SO_FAR`), and task 36's `endSession.ts` to delete a private constant made redundant, which **retires T36-D2**. Both permitted under the rule T36's phase 9 wrote. |
| **D7** | **`routes.tsx:5` carries a false consequence** — measured in phase 7, a module-scope read in a route cycle throws nothing and renders `href="/app/undefined"`. Task 16's closed file, and a comment rather than an instrument, so recorded not edited. **FU-T37-2.** |

## Spec drift observed, not fixed

`spec/` is not mine to edit. Unchanged from T36:

- **`tasks.md:66`** marks task 38 **"Blocked on Q15"**, but `package.md:475` lists Q15 as *resolved*
  (2026-10-10, session revocation) and `package.md:421-430` shows it is about revocation semantics, not
  sign-out. `SasLoginIntegrationTest:475` already asserts that a post-logout `/oauth2/authorize` redirects
  to `/login`. **Task 38 is the only end-to-end proof of R19**, so this block is load-bearing and looks
  mis-assigned.
- **`package.md:123`** names `signOutRevokesTokensClearsMemoryAndEndsSasSession` → R19, but R19 was revised
  on 2026-10-10 to drop the revoke; `tasks.md:63-64` use the shorter
  `signOutClearsMemoryAndEndsSasSession`, which is the name the code uses.
- **`design.md:138`** still calls the SPA route set "PROPOSED paths, pending author confirmation (Q6)",
  while §11 lists Q6 among the resolved questions.

## Phase 13

PR preparation.
