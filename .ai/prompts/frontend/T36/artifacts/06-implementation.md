<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation) -->

# frontend · T36 · Phase 6 — Implementation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation.md` |

## What was built

| File | Lines | |
|---|---|---|
| `frontend/src/auth/idToken.ts` | 71 | **new** — `retainIdToken`, `takeIdToken`, one `let`, no plain reader |
| `frontend/src/auth/endSession.ts` | 120 | **new** — `END_SESSION_PATH`, `endSession`, two private helpers |
| `frontend/src/auth/signOut.ts` | +12 | one added call, second in the body |
| `frontend/src/auth/CallbackScreen.tsx` | +8 | one added line beside `retainAccessToken` |
| `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | +488 | 37 cases appended; five T35 structures extended |

## Gates

| | |
|---|---|
| T36/T35 suite | **75 passed (75)** — 38 of them task 35's, unchanged |
| whole suite | **1653 passed / 1 failed of 1654** — the known `oxlint` host case only (baseline 1616/1617) |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |

## The plan's one claim about T35, measured — and it was half wrong

Phase 5 predicted that adding a fifth clearing primitive to `signOut.ts` would turn **four** of T35's
structures red — `INVENTORY`, `PERMITTED_CALLERS`, `DEFINERS` and the `SIGN_OUT_SET` monopoly case — and said
that a green run would be a finding about T35 rather than a convenience to accept.

**One fired, not four: 1 failed | 37 passed.** AC7's module-scope mutable scan caught `idToken.ts`'s new `let`
with exactly the message it was written to give. The other three did not, and the reasons separate into two
kinds:

- **`INVENTORY` and `DEFINERS` are correct not to have fired.** AC8 derives *storage keys* from source and
  checks the inventory accounts for them. `idToken.ts` introduces no storage key, so no key escaped. The
  inventory had to grow for AC7's `cleared` list, and it did, but nothing was wrong with the instrument.
- **`PERMITTED_CALLERS` fired one step later, and that is the instrument working.** It went red as soon as
  `takeIdToken` was added to it, because the stale-caller case found `auth/endSession.ts` did not yet exist. A
  primitive absent from the record is simply not checked, which is right: an unknown primitive is not a stale
  caller.

**`SIGN_OUT_SET` is the real finding.** T35's monopoly case asserts that only `signOut.ts` holds all of
`['clearAccessToken', 'takeSignInNotice', 'consumeAuthorizeState', 'clearReturnTarget']` — a **hardcoded
four**. After T36, `signOut.ts` calls five primitives and the hardcoded four are still held only by it, so the
case passes without noticing the change. Its description ("is the only module permitted to call the whole
sign-out set") therefore now overstates what it measures: the set is frozen at T35's four rather than being
*the set sign-out actually calls*. Nothing is broken today — `takeIdToken`'s callers are pinned by
`PERMITTED_CALLERS` at exactly `endSession.ts` and `signOut.ts`, so a sixth caller still fails — but the
monopoly case would not catch a *second* module assembling five-of-five. **Recorded as a finding against T35,
carried to phase 7, and deliberately not fixed here**: deriving `SIGN_OUT_SET` from `signOut.ts`'s source is a
change to a closed task's test logic, and phase 7 is where it earns or loses its place.

## F8 answered by running, not by reasoning

Phase 4 argued from source that no T19/T20/T23 case would break. Then it was run:
`callbackValidatesNonceAndKeepsAccessTokenInMemory`, `stateSurvivesRedirectAndIsConsumedOnce`,
`callbackValidatesNonce`, `callbackWithMismatchedStateIsDiscarded` and `tokensAreNeverWrittenToAnyStorage`
together — **442 passed**. T19's `:1019` slice still contains its `return;` with a line added beside
`retainAccessToken`, exactly as predicted. The prediction is now a measurement.

## Decisions made while building

**The ID token is second in `signOut()`, not first and not last.** T35's argument for `clearAccessToken()`
first is that the body has no `try/catch`, so a throw abandons what follows, and the clear is the one step
whose omission is a security defect rather than an untidy store. An ID token is a credential by that test too,
so it belongs with the first step rather than among the three stores. Second rather than first because T35's
existing case pins `clearAccessToken()` as the first statement — and because, between two credentials, the
bearer token is the one an attacker can spend.

**`endSession()` calls `takeIdToken()` and so does `signOut()`, and that is not redundancy.** `signOut()` can
be entered on its own, and on that path its own call is what makes the ID token gone. On the `endSession()`
path the second call finds `null`. One idempotent primitive serving both entry points is what lets neither
know about the other, and AC9's first case is the one that would otherwise have been missed: a suite that only
ever called `endSession()` would pass with `signOut()` ignoring the ID token entirely.

**`assign`, not `replace`.** A visitor who presses Back lands on an SPA route that will send them to sign-in,
rather than on a blank history entry.

**The hint is absent when there is no token, never empty.** `URLSearchParams` serialises `''` into
`id_token_hint=`, which asks SAS to end the session identified by the empty string, and `?? ''` is the exact
shape that mistake takes. The case asserts `has(...)` is `false`, not that the value is `''`.

## Deviations

| # | Deviation |
|---|---|
| **D1** | **AC4 says "derived from the route table"; `'signed-out'` is declared privately in `endSession.ts`.** `routes.tsx` publishes `SIGN_IN_PATH`, `CALLBACK_PATH` and `HOME_PATH` but has no `SIGNED_OUT_PATH` — the route exists there only as a literal inside `PUBLIC_ROUTE_PATHS`, and that module is closed to this task. The suite asserts `PUBLIC_ROUTE_PATHS` contains the derived segment, which also proves the target is a *declared* and *public* route. **Checked against** the route table, which is one derivation step short of *derived from* it. |
| **D2** | **AC10's scan permits two production modules, not one.** `pwa/navigationDenylist.ts:19` names the path as an R57 denylist entry, which is not a way of reaching the endpoint. Both are named with their reasons and a third fails. |
| **D3** | **AC2's source scan bans `open(` outright in `endSession.ts`**, which would also ban a legitimate unrelated `open`. There is no such use and the module is 120 lines; a narrower pattern would be precision spent on a hazard that does not exist here. |
| **D4** | **AC3's absolute-host scan is package-wide, not scoped to the new modules.** The criterion is about this task's source, but the hazard (L9, R58) is about the bundle. Scoping it to two files would have been a weaker test for no saving. |
| **D5** | **`AC14`'s "exactly one place" scan excludes `auth/idToken.ts` by path.** `export function retainIdToken(` matches a call-shaped needle; T35's `DEFINERS` draws the same distinction for the same reason. **Found by failing**, not foreseen: the case reported the owning module as its own caller before the exclusion, and a control was added so the exclusion cannot empty the scan. |
| **D6** | **`endSession()` is not idempotent**, pinned by AC13 rather than latched. A latch is module-level mutable state — the one thing AC15 forbids this module — traded for a second navigation that cannot happen in a browser that has already left the page. |
| **D7** | **`CLIENT_ID` is unused.** Phase 2's dependency table listed it; the end-session endpoint takes no client id and AC6 freezes the parameter set at two. Dropped in phase 5, recorded here. |

## Carried to phase 7

- The **`SIGN_OUT_SET` finding** above.
- **`watchHandover` is the fifth duplicated helper** (`strip` is the fourth). OQ-T32-4, now nine reviews across
  four tasks.
- **OQ-T35-3 is now producible.** Before T36 a signed-out tab adopting a sibling's renewal held an access
  token and nothing else. Now that same tab holds an access token and **no ID token** — which is precisely
  AC12's shape, a case phase 2 recorded as *unreachable in production*. T36 did not create the hazard, but it
  created the combination, and AC12 is no longer purely hypothetical. Task 39's.
- Nothing calls `endSession()`. **OQ-T36-5**, seventh task.

**Phase 6 complete.**
