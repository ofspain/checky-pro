<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation) -->

# frontend · T35 · Phase 6 — Implementation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | `artifacts/04-freeze-task.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation.md` |

## What was built

| File | Lines | What |
|---|---|---|
| `frontend/src/auth/signOut.ts` | 78 | `signOut(): void` — four calls, no state, no parameter |
| `frontend/src/auth/returnTarget.ts` | +27 | `clearReturnTarget(): void`, additive only |
| `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | 456 | **27 cases**, AC1–AC12, controls for every scan and sweep |
| `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx` | +18 | one pin widened, with justification — **see the collision below** |

## Gates

| Gate | Result |
|---|---|
| T35 cases | **27 passed**, exit 0 |
| whole suite | **1601 passed / 1 failed of 1602** — the one failure is `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, the known `oxlint` host case, exactly the recorded baseline |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0**, no output |
| the three guard suites for `returnTarget.ts` | **285 passed**, run immediately after the additive export, as phase 5 ordered |

## The collision phase 4 did not find

**Task 23 pins `consumeAuthorizeState` at exactly one caller, package-wide**, at
`stateSurvivesRedirectAndIsConsumedOnce.test.tsx:821`. `signOut.ts` is a second, so the suite went red on
first run. Phase 4's F10 resolution said no closed suite would be touched; that was decided without knowing
this pin existed, and a frozen brief contradicted by a measurement gets corrected rather than worked around.

**Resolution: the pin is widened to three callers, and the decision is admitted rather than circumvented.**
Checked against T23-D2's own argument before changing anything:

- T23-D2 narrows deletion to the consuming path "so that anyone who can get a signed-in visitor to open
  `/app/callback?state=x` cannot end that tab's pending sign-in" — a **drive-by navigation** threat. The pin's
  own comment scopes the dependency to "no **discard path** anywhere calling this". Sign-out is neither: it is
  unreachable by a link, and when an affordance exists it will be a deliberate gesture. **Nothing an attacker
  can cause now deletes the three.**
- And leaving them is the worse outcome, which is the positive case for the change rather than merely its
  absence of harm. The triple outlives the tab that wrote it. A visitor who signs out while a sign-in is in
  flight elsewhere would otherwise leave a live `code_verifier` waiting for a callback that can still arrive
  and still complete an exchange — **a session established after sign-out**, which is the one thing R19 exists
  to prevent. Sign-out wins over a pending sign-in.
- Nothing else in T23's file changes: a discard still deletes nothing, and the deleted set is still exactly
  the three keys the constant names. Its other 28 cases pass.

Two rejected alternatives, recorded because each looked reasonable: spelling the three keys inside `signOut.ts`
(task 22's scan forbids a second spelling, and rightly), and leaving the triple behind with a note (it keeps a
closed pin intact at the price of the post-sign-out exchange above).

Kimi's F10 was therefore **more right than it knew** — it suspected a comment framing `consumeAuthorizeState`
as callback-only; the framing is enforced by a test.

## OQ-T35-3 is confirmed by measurement, not by reasoning

Phase 5 carried the risk that the cross-tab hazard might not reproduce, and committed to withdrawing the
finding if it did not. **It reproduces.** AC9 stands up a real `BroadcastChannel`, signs out, posts
`renewal:succeeded` from a sibling end, and the signed-out tab holds the sibling's token on the next tick.
The case asserts that as it is, with a message that reads as a handover: if the token stays `null`, the reader
is told that is the fix OQ-T35-3 asked for, to replace the case with the opposite assertion and delete the
open question.

A minimal `TabLocks` stub whose `request` throws was enough, because `shareRenewalAcrossTabs` only needs both
a channel and a lock manager to be present to install the listener — and the stub throwing is itself the proof
that no lock was taken, so the hazard is in the listener and not in the renewal path.

## What the implementation measured

- **The zero-parameter guarantee is a compile error, not just a test.** `signOut(undefined as never)` fails
  `tsc` with "Expected 0 arguments, but got 1". The case now records that, because it is stronger than the
  runtime assertion it replaced: a caller cannot even express the reason it would want to pass.
- **`accessToken.adopt` really does route through `retainAccessToken`.** AC1's second case drives the
  cross-tab entry point rather than the exchange's, so "the only place a non-null token is assigned" is
  measured instead of taken from a comment.
- **Exactly six `themistra.` literals exist in production source**, five distinct: the four storage keys, plus
  one channel-and-lock name used twice. AC8 asserts the scan's set equals the inventory **in both directions**,
  so a stale inventory row is a failure rather than a pass.
- **The authorize triple is built by the real authorize step** in AC3, not by three hand-written `setItem`
  calls, so the production writer and this reader must agree on the key names for the case to pass at all.

## Deviations from the plan

1. The plan expected to modify one file; **two** were modified — `returnTarget.ts` as authorized, plus T23's
   suite for the collision above.
2. AC4's "not a thenable" assertion was dropped: `toHaveProperty` cannot run on `undefined`, and
   `toBeUndefined()` already forecloses both claims. Stated in the case rather than silently removed.
3. No case needed JSX, so the `.tsx` extension is carried purely for task 36, as the frozen brief allowed.

## Still open

**OQ-T35-3** (confirmed, Blocker, task 39's) · **OQ-T35-2** (the dormancy, sixth task) · **OQ-T35-1** (frozen:
task 36 retains the `id_token`) · `package.md:123`'s stale test name.

Tests are committed in this phase's commit, **before** any mutation harness exists, because the harness reverts
with `git checkout` and has silently destroyed uncommitted test edits twice in this package.
