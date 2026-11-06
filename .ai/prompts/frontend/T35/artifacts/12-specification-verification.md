<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T35 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | all prior artifacts (00–11) |
| **Produces** | `artifacts/12-specification-verification.md` |

## What the spec asks

`tasks.md:63` — "**Sign-out clears memory.** Sign-out clears in-memory token state. Verifies
`signOutClearsMemoryAndEndsSasSession`. Cites R19."

`requirements.md:62-65` — R19: "WHEN the user signs out, THEN the SPA SHALL clear in-memory token state and
end the SAS session through the OIDC end-session endpoint with a `post_logout_redirect_uri` from the
allowlist. **Revised (2026-10-10, Q5 resolved):** no `/oauth2/revoke` call — the SPA holds no refresh token
to revoke for this client. The end-session navigation is served by SAS, not the SPA shell (R57)."

**T35 owns clause 1.** Clause 2 is task 36.

## Traceability matrix

Evidence cites `frontend/src/...`; the test column cites criteria in
`src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` (38 cases), plus T24's suite where noted.

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R19 cl. 1 — clears in-memory **token** state | **yes** | `auth/signOut.ts:69` `clearAccessToken()`, first statement | AC1 (`:281-368`) | no | no |
| R19 cl. 1 — and the order is load-bearing | **yes** | `auth/signOut.ts:69` before `:74`, `:81`, `:87` | AC1 (`:302-367`), **SO03** | no | **D1** — the claim was unmeasured until the mutant found it |
| R19 cl. 1 — the rest of the session state | **yes** | `:74` notice · `:81` authorize triple · `:87` return target | AC3 (`:396-425`), AC8 (`:625-645`) | no | **D2** — "token state" read as the whole inventory |
| R19 cl. 2 — end the SAS session | **not this task** | — | — | no | **task 36** (`tasks.md:64`) |
| R19 revision — no `/oauth2/revoke` | **honoured**, nothing calls it | AC11 (`:886-905`) forbids any network reach | AC11 | no | no |
| R22 — sign-out propagates to every tab | **not this task** | — | — | no | **task 39** (`tasks.md:67`) |
| R22 — but callable by a tab with nothing held | **yes**, idempotent | `auth/accessToken.ts:97-99`; `auth/returnTarget.ts:161-167` | AC2 (`:369-395`) | no | no |
| R57 — end-session is a full-page navigation | **respected**, so the clearing is synchronous | `auth/signOut.ts:59` returns `void`, no deferral | AC4 (`:426-450`) | no | no |
| L3 — tokens in memory only | **yes** | nothing in `signOut.ts` reaches a store | AC6 (`:463-518`), AC10 (`:868-883`) | no | no |
| L16 — the carve-out is exhaustive | **yes**, and **task 22 owns the question** | `auth/signOutClearsMemory…:571-681` derives from the production constants | AC8 | no | **D3** — T35 asks the narrower question and bridges |
| L4 — nothing discloses a cause | **yes**, structurally | `auth/signOut.ts:59` takes no parameter — a **compile** error to pass one | AC5 (`:451-462`) | no | no |
| L11 — catalogue-only strings | **vacuous**, it renders nothing | AC11 (`:886-905`) forbids `t(`, `catalogue`, React and JSX | AC11 | no | no |
| L14 — the declared route set | **yes**, no route added | AC11 | no | no |
| L18 — cross-tab coherence | **NO — a signed-out tab can be re-authenticated** | `api/crossTabRenewal.ts:95-111` adopts without asking | AC9 (`:682-758`), AC9b (`:759-778`) | **the guard** | **D4 — the material one** |
| L12 — phase additivity | **yes**, with two closed suites edited by justification | phases 6 and 9 | AC11 (`:886-928`) | no | **D5** |
| L6 — generated client only | **vacuous**, no backend call | AC11 | no | no |
| `tasks.md:63` — the behaviour runs | **NO — `signOut` has no production caller** | `auth/signOut.ts:31-38` | AC10 (`:779-884`) as the substitute | **the wiring** | **D6** |

## Deviations

**D4 — L18 is not satisfied, and this is the finding of the task.** A tab that has signed out can be handed a
live token again by a sibling. `crossTabRenewal.ts`'s channel listener calls `local.adopt(token)` on any
`renewal:succeeded` and never asks whether this tab signed out: tab A signs out; tab B, which has processed
no sign-out, completes a renewal and posts; **tab A holds a live session, with no visitor action.** Task 39's
propagation narrows the window and cannot close it — a sibling whose renewal resolves before it handles the
broadcast still posts, and the channel orders nothing.

**T35 cannot fix it and does not pretend to.** `signOut()` cannot reach the `SharedRenewal` closure, and the
alternative — a latch making `adopt` refuse — means **adding** state in the task whose statement is removing
it. So AC9 pins the sequence against a real `BroadcastChannel` and asserts the behaviour *as it is*, with the
message written so the *good* outcome reads as an instruction. AC9b pins that the message still names task 39,
OQ-T35-3, and what to do. **Escalated as OQ-T35-3.**

**D6 — `signOut` has no production caller.** No authenticated route has an element, `AppShell.tsx` renders a
bare `<div>`, and no unit of `tasks.md` owns a sign-out affordance. T35 is the **sixth** built-and-dormant
task after 5, 6, 30, 31 and 33, and tasks 36–39 will land in the same place. Deliberately **no dormancy
canary** here, unlike T33's AC12: that one failed on wiring because its message carried an instruction the
author needed, whereas wiring sign-out needs nothing but calling it, so the same test would only obstruct
the person doing the right thing. **AC10 is the substitute and is not claimed as more** — it stops a second,
partial sign-out being assembled elsewhere, including one assembled a single allowlist entry at a time.
**OQ-T35-2.**

**D2 — "in-memory token state" is read as the whole inventory, not one variable.** Taken literally the task
is satisfied by `clearAccessToken`, which task 21 built and already made idempotent. The useful reading is
every piece of session state the package holds, and the cost of the narrow reading is concrete: a surviving
`code_verifier` lets a callback still arrive and still complete an exchange — a session established *after*
sign-out.

**D1 — the ordering claim was documented and unmeasured.** `signOut.ts` said the first position was
load-bearing; AC1 asserted the outcome and nothing asserted the sequence. **SO03 survived the first harness
run.** It was invisible because none of the three later steps can throw in real life, which is what made the
claim feel safe. Now pinned from both ends, and the module's comment is corrected: there is no `try/catch`,
so a throw abandons what follows — *what a later failure leaves behind is residue, and what it does not leave
behind is a token.*

**D3 — L16's inventory is not re-derived here.** Task 22's `sessionStorageHoldsOnlyTheL16Items` owns it,
pins the carve-out at four and names clause (b) as the next entrant. T35 asks only *does sign-out clear each
piece that exists*, and bridges with a `themistra.`-literal scan plus a ban on keys assembled at runtime,
which catch what a constants-only derivation cannot see. Clause (b) is pre-authorized with the handoff
recorded: whether a deliberate sign-out should destroy a visitor's in-progress form is **task 26's**.

**D5 — four files outside this task were modified, where the frozen brief said one.** The frozen AC11 clause
was **false** and phase 10's audit caught it; the corrected clause and each file's authorizing phase are in
`10-test-generation.md`. `returnTarget.ts` took the one additive export (checked safe against T22's
`PERMITTED_WRITERS` first). T23's single-caller pin on `consumeAuthorizeState` was widened to three, on
T23-D2's own threat model — a drive-by `/app/callback?state=x`, which sign-out cannot be. T24's suite gained
the new export's contract, because that module is T24's. **This is the most consequential thing T35 did
outside its own files and a reviewer should push on it.**

**D7 — AC12's "any storage condition" is narrowed to three.** Absent, refusing everything, and refusing
removals. `QuotaExceededError` is excluded on the merits: quota applies to writes and sign-out never writes.

## LOCKED decisions

L3, L4, L6, L11, L12, L14 and L16 are satisfied as traced. **L18 is not** — see D4, and it is a pre-existing
defect in built code rather than something T35 introduced. No file under `spec/` was modified.

## The approving principal engineer's answers

**(1) Is the task fully complete?** **Complete as specified, dormant as built, and sitting beside a defect it
cannot reach.** R19 clause 1 is implemented and instrumented; the behaviour cannot run (D6); and L18 is
violated by code task 6 owns (D4). I would not sign this off without the owner seeing D4 — it is the only
finding in this task that describes a way a real user ends up signed in after signing out.

**(2) Does it satisfy every acceptance criterion?** Yes, at the module level: twelve frozen criteria plus
AC9b, each with at least one instrument, after phase 10's clause-by-clause audit corrected one criterion that
was **false** and closed four that were claimed and unmeasured.

**(3) Does it violate any LOCKED decision?** It does not violate one. **L18 is already violated** by the
adoption path, and T35 is the task that measured it.

**(4) Remaining risks?**

- **D4.** The one with a user-visible consequence. Pinned, not fixed, and the pin is unusual: a green suite
  here means a known hazard is still present.
- **D6.** A correct component nobody calls decays; the next author has no running behaviour to check against.
- **Two of my own answers were wrong on the first attempt** and both were caught by running them, not by
  re-reading: F9's monopoly rule would have forbidden `CallbackScreen.tsx`, which legitimately holds three
  primitives; and phase 9's AC9 poll turned a slow machine into a false "hazard fixed" report. A reviewer
  should assume there is a third.
- **Helper duplication**, the most-raised item in the package: the `strip` helper is the fourth copy, the
  boundary harness a fifth family. **OQ-T32-4**, now eight reviews across three tasks.
- **AC10's scan sees direct calls only** — an alias, a wrapper or a callback reference would pass. Accepted:
  the threat is someone assembling a second sign-out, which is written as direct calls, and an AST pass is
  disproportionate. Documented where the scan is.
- **One environmental suite failure persists on this host:**
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, because this `oxlint@1.87.0` prints no summary line.
  Pre-existing, unrelated, pinned by name in the harness baseline.
- **Spec drift, recorded not fixed:** `package.md:123` still names the test
  `signOutRevokesTokensClearsMemoryAndEndsSasSession` although Q5 removed the revoke call from R19 on
  2026-10-10. Third instance of the same `package.md` lag, after `:171`'s mapping for struck-out task 34 and
  §11's unstruck Q5.

## Gates

| Gate | Result |
|---|---|
| T35 cases | **38 passed**, exit 0 |
| T24's suite | **188 passed** |
| whole suite | **1616 passed / 1 failed of 1617** — the known `oxlint` host case |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| mutation | **23/23 regressions killed · 1/1 canary · 0 survivors · 0 broken** |

## Verdict

**PASS** — with **D4 (OQ-T35-3)** raised as a defect in built code that task 39 must close, and **D6
(OQ-T35-2)** as the owner's standing question. Every acceptance criterion is satisfied and instrumented at
the module level; the behaviour cannot run until a caller exists, and L18 remains violated by a path this
task measured and cannot reach.

**Phase 12 complete.**
