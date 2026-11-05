<!-- MODEL: Claude Opus 5 — Phase 4 (Frozen Task Brief) -->

# frontend · T33 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |
| **Gate** | Human Approval — decided here, not a model stop |

Kimi returned **eleven** findings (its summary says ten; the list has eleven). Every citation was verified
against source before resolution. Ten are taken; one is answered with a correction, because the resolution
it recommends is factually unavailable.

## The two blockers

### F2 — "T33 delivers routing that has no production caller." Frozen as resolution (c), because (b) does not exist.

Kimi offered three resolutions: (a) T33 also does the wiring, (b) identify the owner task, (c) acknowledge
the limit explicitly. **(b) was checked and is unavailable.** Searched all 174 units of `tasks.md`:

- Task 1 mounts **"an empty shell"** (`tasks.md:10`).
- Tasks 5 and 6 build the single-flight boundary and the cross-tab renewal (`:14`, `:15`).
- Task 41 owns R28's unauthenticated redirect; task 127 presupposes a working renewal.
- **No unit constructs `createSessionBoundary`, `shareRenewalAcrossTabs`, or an authenticated client.**

So the gap is larger than T33 and has the same shape as one the repo already records in source:
`accessToken.ts:38-40` says silent re-authentication "is the model the owner chose, and **NO TASK IN
`tasks.md` BUILDS IT**." Put together: **tasks 5, 6 and 33 are three tasks of renewal machinery with no
wiring to run in and no renewal to run.** That is a systemic finding for the owner, not something T33 can
close by widening its own scope — (a) would pull in the per-tab boundary, the shared renewal, every client,
and the route guard that `routes.tsx:48-50` assigns to T41.

**Frozen:** T33 delivers the routing module and its instruments. It claims **no** runtime proof it cannot
make, and it pins the environmental assumptions that stand in for one (G10). **OQ-T33-2 is escalated**, no
longer as "who owns the wiring" but as "three tasks of machinery are unreachable; is that intended?"

### F1 — "Landing on `/app/sign-in` offers re-auth rather than transitions to it." G1 stands, strengthened.

Kimi concedes the reading is defensible and offers two ways forward. Taking its **first**: record G1 as a
decision with its rationale, and **add an acceptance criterion proving the destination is the start of the
auth flow and not an authenticated surface** (AC10).

Its alternative — land on `/app/signed-out` with a session-ended notice and one action to
`/oauth2/authorize` — is **rejected on structure**: it has the *same* one-click shape as G1, so it is no
more a "transition to re-auth" than G1 is, and it pays for that by landing the visitor on the screen built
for R19's **deliberate** sign-out (`tasks.md:65`, task 37), which states something false about what
happened. Strictly worse on truthfulness, no gain in directness.

## Frozen decisions

| ID | Decision |
|---|---|
| **G1** | Clean re-auth lands on `SIGN_IN_PATH`, read from `app/routes.tsx`, already in `PUBLIC_ROUTE_PATHS`. No new route (L14), no second spelling. |
| **G2** | T33 delivers the failure-routing module, not the app wiring. The routing is unreachable until a wiring task exists, and T33 says so in the file rather than implying otherwise. |
| **G3** | A second `SignInNotice` member, `'session-ended'`, with its own catalogue string (G8). Reusing `'discarded'` is rejected: its copy says sign-in did not complete, which for a failed renewal is false. |
| **G4** | The allowlisted return target is remembered before leaving, reusing `rememberReturnTarget`. **Reversible in one line if the owner disagrees.** |
| **G5** | The token is cleared through `clearAccessToken()` only — never a second clearing primitive. |
| **G6** | The transition runs once per renewal (the hook's own contract) and its side effects are safe to repeat. |
| **G7** | `navigate` is an injected dependency; the module never imports the router. |
| **G8** | **The copy, specified:** `signIn.sessionEnded` = **"Your session has ended. Sign in again."** |
| **G9** | **The module:** `frontend/src/auth/cleanReauth.ts`. Its test: `frontend/src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.ts`. |
| **G10** | The "no half-authenticated render" claim is **environmental today and pinned**: a scan asserts every authenticated route has no element, so the assumption fails the moment it stops being true. |
| **G11** | The module **ignores** `reason`. It does not read it, branch on it, render it or log it. Proven by a test and a source scan. |
| **G12** | **Every tab runs its own hook once per failure event** and performs its own clearing and navigation. No tab transitions on another's behalf. |

### G8 — why this string

Kimi proposed `'Your session has ended. Continue to sign in.'` The second sentence is rejected: it is
verbatim the label of the button beside it (`catalogue.ts:13`, `signIn.action` = "Continue to sign in"), so
the screen would read the instruction twice. **"Sign in again."** instead.

Checked against L4: it names the **event**, not a cause. A locked, suspended or deleted account, an expired
token and a server that cannot be reached all produce the same failed renewal and therefore the same
sentence, which is exactly what L4 requires. It names no status, no step and no factor, so it promises
nothing L2 reserves to the server. It goes in the `signIn` group after `discarded`, keeping that group in
the order a visitor meets it.

### G9 — why there, and the cycle it must not close

`src/auth/`, because the module's three collaborators are all auth modules — `accessToken`, `signInNotice`,
`returnTarget` — and only `SIGN_IN_PATH` comes from `app/`. Named for what it is, not for its test:
production modules in this package are nouns (`accessToken.ts`, `returnTarget.ts`, `signInNotice.ts`).

**The import cycle is real and the mitigation is already precedent.** `returnTarget.ts:40-49` documents the
third strand of `routes -> CallbackScreen -> returnTarget -> routes`, and measured in T16 that a
module-scope read of a route constant yields `undefined` rather than throwing — "the worst of both worlds,
because an empty allowlist **fails closed silently**." So `cleanReauth.ts` **reads `SIGN_IN_PATH` inside a
function, never at module scope**, and a case asserts there is no module-scope use, exactly as the sibling
files are guarded.

### G4 — what remembering does and does not buy (Kimi F6)

It preserves the **location**, not the state of the interrupted task; what a screen shows after the return
is that screen's responsibility, not this module's. Two measured consequences, both pre-decided elsewhere:
a parameterised route such as `invoices/:id` is **not** allowlisted (`returnTarget.ts:52-57`, T24-D5), so a
visitor there returns to home; and a non-allowlisted target is **silently ignored** rather than stored
(`:88-90`), so being already at `/app/sign-in` makes the call a no-op with no special case needed.

Noted for the record: `returnTarget.ts:76-78` names **task 41** as this function's anticipated caller. T33
is a second legitimate caller of the same one-line primitive, not a reassignment of that task.

## Acceptance criteria — frozen

| AC | Criterion | Verifies |
|---|---|---|
| **AC1** | A rejected renewal clears the held token through `clearAccessToken()`; no second copy of the token survives anywhere. | R22 cl. 2 · G5 |
| **AC2** | The transition runs **exactly once per renewal**, and repeating it is harmless — including when `navigate` is a no-op because the tab is already at the destination. | R22 cl. 1 · R21 · G6 |
| **AC3** | Every waiting call still receives its original 401, and a caller that waited behind a failed renewal starts no second renewal. | R21 |
| **AC4** | The destination is `SIGN_IN_PATH` taken from `routes.tsx`, is a member of `PUBLIC_ROUTE_PATHS`, and is read inside a function, not at module scope. | L14 · G1 · G9 |
| **AC5** | The copy is `signIn.sessionEnded` from the catalogue, is not the `discarded` string, and names no cause, status or step. | L4 · L11 · R22 cl. 3 · G8 |
| **AC6** | **No failure information** reaches `sessionStorage`, `localStorage`, the URL, `document.title`, `window.name` or any of the six console channels. The **one** permitted write is the allowlisted return target, which carries no failure information. | L3 · L16 · Kimi F8 |
| **AC7** | A follower tab that adopts a sibling's `renewal:failed` performs the transition once itself and starts no second renewal. | L18 · R21 · G12 |
| **AC8** | A transition that throws does not reach the waiting callers — the microtask scheduling at `sessionBoundary.ts:71` honoured and proven from the routing side, which no existing test does. | R21 · R22 cl. 2 |
| **AC9** | The module never reads or branches on `reason`: proven by driving two different rejection reasons to an identical outcome, and by a source scan. | L4 · G11 |
| **AC10** | The destination is the **start of the auth flow and not an authenticated surface**: `SIGN_IN_PATH` is in `PUBLIC_ROUTE_PATHS`, absent from `AUTHENTICATED_ROUTE_PATHS`, and its screen builds an authorize request on the visitor's action. | R22 cl. 1 · Kimi F1 |
| **AC11** | **The environmental assumption is pinned**: every entry in `AUTHENTICATED_ROUTE_PATHS` has no element in `appRoutes`, so the "no half-authenticated render" claim fails the moment an authenticated screen lands. | R22 cl. 2 · G10 · Kimi F4 |

## Remaining findings, resolved

- **F3 (copy unspecified) — taken**, G8, with the duplicate-instruction correction.
- **F4 (AC8 environmental) — taken**, G10 and **AC11**, which is Kimi's own "add a source scan" recommendation.
- **F5 (module name and location) — taken**, G9, plus the module-scope-read guard the sibling files already carry.
- **F6 (return target preserves location, not state) — taken**, recorded in G4 and to be stated in the module's own doc.
- **F7 (idempotence when navigate is a no-op) — taken**, AC2 and a case for the already-at-destination shape.
- **F8 (AC6 wording) — taken**, AC6 reworded to name the one permitted write.
- **F9 (`reason` must not be used) — taken**, G11 and **AC9**, with both a behavioural half and a source half.
- **F10 (which tab navigates) — taken**, G12: independent per tab, which Kimi calls the simplest model and which matches R21/R22.
- **F11 (harness duplication) — ACKNOWLEDGED, NOT ACTIONED.** This is the second instance of the debt
  **OQ-T32-4** records, now in a different helper family: `parallel401sTriggerExactlyOneRenewal.test.ts`
  and `siblingTabsShareOneRenewal.test.ts` each build their own fake transport and fake session inline, and
  T33's file will be the third. Extracting it would edit two **closed** tasks' instruments, which is the
  same reason OQ-T32-4 stays open and the same decision the owner has to make. **The count of duplicating
  files is now the signal, and it is rising** — recorded as such rather than silently repeated.

## Out of scope, explicitly

- **R22 cl. 4**, sign-out propagation → **task 39** (`tasks.md:67`).
- **The reuse half of R22** → removed by Q5 (2026-10-10).
- **`signOutPropagatesToAllTabs`** → task 39. **`reusedRefreshOrFailedRenewalTriggersCleanReauth`** →
  task 34, **struck out** by `tasks.md:62`; nobody should write it, though `package.md:171` still maps it.
- **The route guard** → T41 (`routes.tsx:48-50`).
- **Silent re-authentication itself** → no task builds it (`accessToken.ts:38-40`).

**Phase 4 complete. The brief is frozen.** Phase 5 plans the implementation against these eleven criteria.
