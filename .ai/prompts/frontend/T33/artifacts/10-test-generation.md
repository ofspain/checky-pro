<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T33 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |
| **Requirement** | R22, clauses 1–3 |

The frozen brief's eleven criteria were read **clause by clause** against the 31 cases that existed after
phase 9 — the same audit that found T32's uninstrumented AC3 clause. It found one gap here too.

## What this phase added

**AC10's second clause had no instrument.** The criterion reads: the destination "is the **start of the
auth flow and not an authenticated surface**: `SIGN_IN_PATH` is in `PUBLIC_ROUTE_PATHS`, absent from
`AUTHENTICATED_ROUTE_PATHS`, **and its screen builds an authorize request on the visitor's action**."

The first two clauses were pinned. The third was represented by a case asserting that the sign-in route has
**some** element — which is not the same claim. A route table carrying a *different* element at
`sign-in` would have passed it, **and would have passed T15's suite too**, because T15 renders
`SignInScreen` directly rather than through the route table. So nothing tied the route this task navigates
to, to the screen that can actually start re-auth. That is the whole of Kimi's phase 3 finding F1, and it
was resting on an assertion one step short of it.

The new case ties them: the element at `SIGN_IN_PATH` must be a `SignInScreen` element. What that screen
then does with the click stays T15's to prove, and T15 does prove it.

**`NR18`** records it, and the kill was verified narrowly rather than taken on the `own` column: with the
mutant applied and the suite filtered to the new case by name, that case is the one that fails —
`1 failed | 31 skipped`. Worth doing, because NR18's full-suite kill is **38 failures**, far too coarse to
tell which case saw it. Like T32's MR05 it is a structural kill, not a precision one.

## Test manifest

One file, `frontend/src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx`, **32 cases**.

| # | Case | Verifies |
|---|---|---|
| 1 | leaves no token held after the transition | AC1 · R22 cl. 2 |
| 2 | clears FIRST, so a later step that throws cannot leave a token behind | AC1 · the ordering claim |
| 3 | clears through the one primitive, holding no second copy (source) | AC1 · G5 |
| 4 | transitions ONCE when three parallel 401s share one failed renewal | AC2, AC3 · R21 |
| 5 | transitions again on a SECOND failed renewal in the same tab | AC2 · G6 |
| 6 | is harmless to repeat while the tab is already at the destination | AC2 · Kimi F7 |
| 7 | does not start a second renewal for a call that waited behind the failed one | AC3 · R21 |
| 8 | hands the caller its 401, and the throw escapes as an uncaught exception | AC8 · `sessionBoundary.ts:69-71` |
| 9 | the real handler does not throw at any location it can run at | AC8 |
| 10 | navigates to `SIGN_IN_PATH`, basename-relative | AC4 · G1 |
| 11 | is a PUBLIC route and not an authenticated one | AC10 · L14 |
| 12 | has a screen attached | AC10 |
| 13 | **and that screen is the one that STARTS the flow** | **AC10 third clause — new in this phase** |
| 14 | reads the route names INSIDE a function, never at module scope (source) | AC4 · G9 · T16's measurement |
| 15 | pins that every authenticated path has no element — a T41 obligation | AC11 · G10 |
| 16 | sets the session-ended notice, which maps to its own catalogue sentence | AC5 · G3 |
| 17 | names no cause, status, step or factor | AC5 · L4 |
| 18 | draws the line at naming the EVENT, which L4 does not forbid | AC5 · Kimi I5 |
| 19 | puts THAT sentence on the sign-in screen, and not the discarded one | AC5 · S1 |
| 20 | CONTROL: the screen still shows the discarded sentence for a discarded callback | AC5 · R8, T18 |
| 21 | is taken once, so a reloaded sign-in page stops saying it | AC5 · `signInNotice` contract |
| 22 | takes no parameter at all, so there is nothing to branch on | AC9 · G11 |
| 23 | identical outcome for a local failure and a sibling tab failure | AC9, AC7 · L4 |
| 24 | does not name the reason, nor bind a caught error (source) | AC9 · Kimi I4 |
| 25 | imports no router, so the injected navigate cannot be bypassed (source) | G7 · Kimi I9 |
| 26 | a follower tab treats a sibling failure as its own and renews nothing further | AC7 · L18, G12 |
| 27 | remembers the allowlisted location and NOTHING else | AC6 · G4, L16(a) |
| 28 | remembers nothing for a location the allowlist cannot honour | AC6 · T24-D5 |
| 29 | remembers nothing when the tab is not inside the SPA at all | AC6 |
| 30 | leaves no trace on any of the seven surfaces or six console channels | AC6 · L3, L16 |
| 31 | CONTROL: the sweep reports a flag on every surface and channel it watches | AC6 dirty-proof |
| 32 | CONTROL: a scan of nothing is refused, not reported clean | package convention · T22-D14 |

**Every criterion is covered:** AC1 (1–3), AC2 (4–6), AC3 (4, 7), AC4 (10, 14), AC5 (16–21), AC6 (27–31),
AC7 (23, 26), AC8 (8, 9), AC9 (22–24), AC10 (11–13), AC11 (15).

## Named tests (`package.md` §8)

| Named test | File | Owner |
|---|---|---|
| `failedRenewalTriggersCleanReauthWithoutHalfSession` | `src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx` | **T33** |
| `signOutPropagatesToAllTabs` | not written | **task 39** (`tasks.md:67`) |
| `reusedRefreshOrFailedRenewalTriggersCleanReauth` | not written, and should not be | **task 34, removed** by `tasks.md:62`; `package.md:171` still maps it |

## Testing conventions

Vitest with jsdom, not the JUnit/Testcontainers/fixed-`Clock` conventions the phase prompt recites — those
are the Spring services'. The cases drive the **real** session boundary from `src/api/`, not a stub,
because AC2, AC3, AC7 and AC8 are statements about how the hook is *called*.

Two conventions this file follows that are worth naming, because both were paid for elsewhere: the URL and
the DOM are reset in `afterEach` and not at the end of the case that dirties them, so a case that fails
before its own cleanup cannot leak into the next; and `document.head.innerHTML` is cleared **before**
`document.title`, because assigning `document.title` writes a `<title>` into the head that clearing the
head would then delete.

## Gates

| Gate | Result |
|---|---|
| T33 cases | **32 passed** (29 at phase 7 → 31 at phase 9 → 32 here) |
| Whole suite | **1569 passed / 1 failed of 1570** — the known `oxlint` environmental case |
| Mutation | **17/17 regressions killed + 1/1 canary**, 0 survivors, 0 broken, `HARNESS_EXIT=0` |
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |

**Phase 10 complete.** Phase 11 is Kimi's.
