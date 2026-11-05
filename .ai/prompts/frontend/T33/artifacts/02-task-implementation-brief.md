<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T33 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

Phase 1 left two blockers. This brief takes a position on both, with the evidence, so phase 3 has
something falsifiable to attack rather than an open question to re-ask.

## G1 — Clean re-auth lands on `/app/sign-in`. Decided.

`SIGN_IN_PATH` from `app/routes.tsx:15`, which is already in `PUBLIC_ROUTE_PATHS`. No new route, no new
spelling of an existing one (L14).

The three candidates and why the other two lose:

- **`/app/signed-out` — rejected.** It was built for R19's deliberate sign-out landing (`tasks.md:65`,
  task 37). A session that was lost involuntarily is not a sign-out, and landing there would tell the
  visitor something false about what happened. Its copy belongs to R19.
- **An automatic `/oauth2/authorize` navigation — rejected, on two grounds.** First, measured:
  `authorizeRequest.ts` sets **no `prompt=none`** (`:97` builds the parameter string and nothing in the
  file names `prompt`), so the request is fully interactive — an automatic navigation drops the visitor
  into the SAS-hosted login page with no action of their own, mid-task. Second, structural: silent
  re-authentication **is** the renewal (L3's Q5 resolution, `accessToken.ts:38-40`). Re-entering the
  mechanism that just failed, automatically, is the error loop R21 explicitly forbids.
- **`/app/sign-in` — taken.** It is the start of the auth flow in this SPA: `SignInScreen.tsx:37` builds
  the authorize request and navigates to SAS **on the visitor's action**. It already renders a neutral
  notice (`:73`) and already yields that notice to a fresh attempt. So "transition to clean re-auth" is
  satisfied by putting the visitor at the start of the flow with one click left, which is also what R23's
  "resume only on user action" posture implies for the rest of this package.

## G2 — T33 delivers the failure routing, not the app wiring. Decided, and flagged.

`createSessionBoundary` has no production caller (phase 0, §2). T33 implements the
`onRenewalFailed(reason)` hook as a module with injected dependencies, and does **not** construct the
boundary, the shared renewal, or any client inside `mountShell.tsx`.

Why: `tasks.md:61` scopes this task to "a failed renewal routes to re-auth with no half-session", which is
the routing. Wiring would pull in the per-tab boundary, `shareRenewalAcrossTabs`, every client, and a route
guard — and the guard is explicitly someone else's (`routes.tsx:48-50`: "T41 replaces it with the sign-in
redirect"). This package's convention is to declare a seam rather than fake one, and `accessToken.ts:29-31`
already names T33 as the consumer of this hook, not as the builder of the wiring.

The cost is stated rather than hidden: **until the wiring task exists, this routing never runs in the
running SPA.** That is the same forward-guard shape T32 had, and it needs an owner's decision, not a
silent assumption — **OQ-T33-2 stays open**.

## G3 — A second `SignInNotice` member, with its own neutral copy. Decided.

`signInNotice.ts` anticipates this in its own doc: the union "has one member today… a
forward-compatibility hook, not a commitment."

Reusing `'discarded'` was the obvious move and is **wrong on fact**: its copy is "Your sign-in did not
complete. Try again." (`catalogue.ts:18`). For a failed renewal the sign-in **did** complete — minutes or
hours ago — and then the session ended. Telling the visitor their sign-in did not complete is false, and
L4 does not ask for it: L4 requires copy to be identical across **account states and token conditions**
(unknown, locked, suspended, deleted; bad, expired, used), so that an anonymous prober learns nothing. The
visitor's own session having ended is not an account-state disclosure. Nothing in the new copy may name a
cause, a status, or a step.

## G4 — Remember the allowlisted return target before leaving. Decided.

`returnTarget.ts` already provides `rememberReturnTarget` with `isAllowlistedTarget` (`:70`, `:88`), and
L16(a) permits exactly this item in sessionStorage. A transition that forgets where the visitor was loses
more of their session than it has to, and L1 already requires an allowlisted post-login return target with
a fallback to home. This is reuse of a built, allowlisted mechanism — not new design — and it is one line
to remove if the owner disagrees.

## G5 — Clear through `clearAccessToken()`. Decided.

Never a second clearing primitive. `accessToken.ts:88-99` is the only place the token is dropped, its doc
already cites R22's half-authenticated prohibition, and a second copy would be a second thing sign-out and
re-auth both have to find.

## G6 — The transition is idempotent and runs once per renewal.

`sessionBoundary.ts:69-74` schedules the hook in a microtask, once per renewal, so a throwing hook cannot
affect the waiters. T33 must not assume once per *tab*: a tab can fail several renewals over its life, and
each is a fresh transition. Running it while already at the destination must be harmless.

## G7 — Navigation is an injected dependency.

The hook runs outside React — the boundary is per tab, created at wiring time, not in a component — so it
cannot call `useNavigate`. The module takes a `navigate` function; production supplies one backed by the
router, tests supply a recorder. This also keeps the module from importing the router and closing a cycle
(`routes.tsx:2-5` documents one such cycle already).

## Acceptance criteria

Carried from phase 1 with AC4, AC5 and AC8 now resolvable.

| AC | Criterion | Verifies |
|---|---|---|
| **AC1** | A rejected renewal clears the held token through `clearAccessToken()`, and nothing else holds a copy. | R22 cl. 2 |
| **AC2** | The transition runs **exactly once per renewal** and is idempotent when repeated. | R22 cl. 1, R21 |
| **AC3** | Every waiting call still gets its original 401, and a caller that waited behind a failed renewal starts no second renewal. | R21 |
| **AC4** | The destination is `SIGN_IN_PATH`, taken from `routes.tsx` rather than spelled again, and is a member of `PUBLIC_ROUTE_PATHS`. | L14, G1 |
| **AC5** | The copy is a new catalogue member, neutral, naming no cause, status or step — and **not** the `discarded` string, which would be false. | L4, L11, R22 cl. 3, G3 |
| **AC6** | Nothing about the failure reaches `sessionStorage`, `localStorage`, the URL, `document.title`, `window.name` or any console channel. The return target is the one permitted write (L16(a)) and carries no failure information. | L3, L16 |
| **AC7** | A **follower** tab that adopts a sibling's `renewal:failed` performs the same transition once and starts no second renewal. | L18, R21 |
| **AC8** | A thrown error from inside the transition does not reach the waiting callers — the microtask scheduling in `sessionBoundary.ts:71` is honoured and proven from the routing side, which no existing test does. | R21, R22 cl. 2 |
| **AC9** | No half-authenticated render. Instrumented as a **source-side** claim plus the measured fact that no authenticated route has an element today (`routes.tsx:46`); a rendered proof is impossible until the wiring and the guard exist. | R22 cl. 2, OQ-T33-2 |

## Test plan

One new file, `src/api/failedRenewalTriggersCleanReauthWithoutHalfSession.test.ts`, placed beside the two
existing boundary suites it extends the coverage of (`parallel401sTriggerExactlyOneRenewal.test.ts`,
`siblingTabsShareOneRenewal.test.ts`), both of which already build a fake transport and a fake
`SessionTokens` and drive real middleware. T33 reuses that shape rather than inventing a harness.

Boundary cases to drive: renewal rejects; renewal times out at `RENEWAL_TIMEOUT_MS` (a different reason,
same hook); parallel 401s during one failed renewal (one transition, not three); two successive failed
renewals in one tab (two transitions); a follower adopting `renewal:failed`; a leader that vanishes
mid-renewal; a throwing transition; a second sign-in attempt in a tab that already holds a token — the
exact shape `clearAccessToken`'s doc names as the R22 violation it exists to prevent.

## Open questions

- **OQ-T33-2 (open, for the owner)** — no task wires the session boundary into the SPA. Until one does,
  this routing is correct and unreachable. Who owns the wiring?
- **OQ-T33-1 (closed by G1)**, **OQ-T33-3 (closed by G3)**.
- **Spec drift to record, not to fix here:** `package.md:171` still maps
  `reusedRefreshOrFailedRenewalTriggersCleanReauth` to R22 though `tasks.md:62` removed its task, and
  `package.md` §11's Q5 is not struck through though `requirements.md:69-76` records it resolved. Specs
  are not mine to edit.

**Phase 2 complete.** Phase 3 is Kimi's design challenge. The two positions most worth attacking are **G1**
(is landing on sign-in a "transition to clean re-auth", or a transition to a screen that merely offers one?)
and **G2** (does delivering unreachable routing satisfy `tasks.md:61` at all?).
