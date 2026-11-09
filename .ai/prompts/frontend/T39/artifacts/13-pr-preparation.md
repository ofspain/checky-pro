<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation) -->

# frontend · T39 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Consumes** | `artifacts/12-specification-verification.md` — **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12's verdict is **PASS**, so this phase proceeds. No code in this artifact.

> **A note on the trailer.** This phase's generated prompt asks for
> `Co-Authored-By: Claude Opus 4.8 (1M context)`. That is a stale default in the `.ai/` template — the
> model that ran every phase of this task is **Claude Opus 5**, and that is the trailer used in all
> eighteen commits. Worth regenerating via `.ai/generate.py` so the template stops asking for a
> co-author who was not here.

---

## Commit title

```
feat(frontend): propagate sign-out to every open tab (T39, R22, L18)
```

## Commit message

```
feat(frontend): propagate sign-out to every open tab (T39, R22, L18)

Close R22's last unowned clause - "Sign-out SHALL propagate to every open tab" - and
L18's sign-out half, whose renewal half task 21 already built.

Add api/crossTabSignOut.ts: a BroadcastChannel, a message that is exactly
{ type: 'session:signed-out' }, a per-tab latch, a broadcast and a listener. endSession()
broadcasts after signing this tab out and before handing over to SAS; signOut() sets the
latch first; retainAccessToken() releases it; crossTabRenewal's listener refuses to adopt
a sibling's token while it is set.

Use a SECOND channel rather than a third message type on the renewal one.
crossTabRenewal builds its channel only where Web Locks exist, and L18 permits that for
renewal because the cost is a duplicate request. For sign-out the cost would be a sibling
tab left signed in, which R22 forbids outright.

Broadcast from endSession() and not from signOut(), which is what makes a receiving tab
structurally unable to echo: a tab that handles a broadcast signs out by calling
signOut(), which broadcasts nothing. No sender id, no self-filter, nothing to get wrong.

Close OQ-T35-3, which task 35 recorded as unclosable. Its note argued that propagation
"narrows the window without closing it, because a sibling whose renewal resolves before it
handles the broadcast still posts a success and the channel orders nothing". That reasons
about when the sibling POSTS; what decides this tab's token is when THIS tab handles, and
only that is ordered, because channel delivery is never observed before a synchronous
signOut() in the same task. Measured, and both orderings are asserted. Its second
argument - that a latch means adding state in the task whose statement is removing it -
is true of task 35 and does not transfer: L18 calls this task's subject cross-tab
coherence, which is stateful by nature, and the latch is the inverse of session state.
Both arguments are kept in signOut.ts with the reason each failed.

Leave OQ-T39-6 open and pinned: a success from a renewal that started before the sign-out
can still replace a fresh token after a new sign-in, because the latch must be released by
any token assignment. Closing it needs a generation stamp on the message, which means
changing crossTabRenewal's closed outcome union. It is asserted as it IS, with the message
addressed to whoever builds renewal.

NOTHING INSTALLS THE LISTENER. endSession() has no production caller (OQ-T36-5) and no
module owns app-level listeners, so every case installs it directly and "signing out in
one tab signs out all open tabs" is verified as a mechanism rather than as a journey.
T39 is the ninth consecutive task in this position; the gap is one unassigned piece of
wiring that nine tasks share, not nine omissions.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

**9 files, +1310 / −94** (production and tests). 15 pipeline artifacts, +2676.

| File | | |
|---|---|---|
| `api/crossTabSignOut.ts` | **new**, 170 | the channel, the message, the latch, the broadcast, the listener |
| `api/signOutPropagatesToAllTabs.test.ts` | **new**, 630 | 25 cases — the named test |
| `api/crossTabRenewal.ts` | +24 | one guard, below the count and the waiter wake |
| `auth/signOut.ts` | +44 / −… | `markSignedOut()` first; the superseded hazard paragraph rewritten |
| `auth/accessToken.ts` | +7 | `markSignedIn()` at the single assignment point |
| `auth/endSession.ts` | +12 | `broadcastSignOut()` between the local sign-out and the hand-over |
| `auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | +453 / −… | task 35's suite: AC9 replaced, AC9b deleted, AC9c/AC9d added, four instruments grown |
| `api/siblingTabsShareOneRenewal.test.ts` | +54 | task 21's suite: one case, because the contract it measures is task 21's |
| `auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` | +10 | task 22's namespace allowlist |

## Summary

**What it does.** Signing out in one tab now signs out every open tab of the same origin. The acting tab
clears its own session, tells its siblings on a dedicated BroadcastChannel, then hands over to the
authorization server exactly once. A tab that hears the broadcast clears locally and does not navigate, so
one visitor intent produces one SAS logout rather than N. A tab that has signed out refuses to adopt a
token from a sibling's in-flight renewal.

**What is measured, and what the measurements changed.**

- **The inherited premise was wrong, and checking it first changed the design.** Task 35 handed this task a
  hazard with an argument that it could not be closed. Phases 1–2 measured the argument and inverted it.
  Had the argument been obeyed, the task would have shipped a narrower fix and a comment claiming it was
  the best available.
- **Five instruments in four closed tasks' suites fired during implementation**, one of them unpredicted —
  task 22 keeps a *second* allowlist for the same namespace. Task 36's source-derived count went
  *"expected 6 to be 5"*, which is the instrument working exactly as task 36 built it.
- **A rejection was reversed by the mutant written to defend it.** Kimi's phase-8 I10 asked for a scan on
  the message constant's shape; I rejected it with evidence and built XT32 to pin the rejection. XT32
  survived all 1 720 tests. The shared scan requires `^const`, so `export const X = {` is invisible to it.
- **That reversal found something wider — FU-T39-1.** Measured across production source, the shared
  module-scope-mutable scan sees **two** declarations and misses **fourteen**, including
  `auth/accessToken.ts:46`, the session object itself. A second hole, found only because the fix needed a
  control: a type argument on the constructor defeats it, so `pwa/registerServiceWorker.ts` is excused
  with a **pinned `declarations: 2`** while holding **three**. T39 widened its own instrument and left the
  shared one, because changing it alters what four closed tasks are measured against and would turn that
  pinned count false.
- **Two mutants measured something other than their own description, and both are corrected in place with
  the measurement.** One inserted a call without its import (a `ReferenceError` cascade of 46 failures
  whose intended case fired anyway, because a source scan matches an identifier whether or not it
  resolves). One inserted before removing and left its own second anchor ambiguous; preflight now replays
  each mutant's edits in memory.
- **One row is scored apart as unfalsifiable.** Removing `close()`'s idempotency guard has no observable
  effect — a double `removeEventListener` and a double `BroadcastChannel.close()` are no-ops by
  specification — so the two assertions that claimed idempotency now claim what they hold. Kimi recommended
  the same treatment for a missing channel-close, and that one turned out to be observable from outside:
  "no test sees it" and "nothing could see it" are different claims.

## Testing performed

| | |
|---|---|
| `vitest run` | **1724 passed / 1 failed of 1725** — the known environmental `oxlint` host case |
| `signOutPropagatesToAllTabs.test.ts` | **25 passed** |
| `signOutClearsMemoryAndEndsSasSession.test.tsx` | **91 passed** |
| `siblingTabsShareOneRenewal.test.ts` | **23 passed** |
| `sessionStorageHoldsOnlyTheL16Items.test.tsx` | **passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `07-mutation-harness.py` | **33 mutants · 32/32 regressions killed · 0 survivors · 0 broken · 1 unfalsifiable, scored apart** |

**Review gates:** 13 self-review findings (phase 7) · 16 independent findings (phase 8, Kimi 2.7) ·
14 adversarial test findings (phase 11, Kimi 2.7) — **43 in all**. Phase 8 independently confirmed every
major self-review finding, which had not happened before in this package. Four were rejected with reasons,
three of those by measurement; one rejection was reversed by its own mutant.

**Known environmental failure:** `src/api/lintRejectsHandWrittenBackendFetch.test.ts` — this host's
`oxlint@1.87.0` prints no summary line, so the assertion that parses it cannot pass. Pre-existing,
unrelated, and the harness refuses to run if anything *else* fails.

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` — task **39** |
| **Requirements** | **R22** (scoped) · R19, R21 (touched) |
| **LOCKED decisions** | **L18** (scoped) · L2, L3, L16 (observed) |
| **Named test** | `signOutPropagatesToAllTabs` — `frontend/src/api/signOutPropagatesToAllTabs.test.ts` |
| **Deviations** | D1–D10, listed in phase 12 |
| **Open** | OQ-T39-6 (pinned) · OQ-T39-7 (measured) · **FU-T39-1** · OQ-T36-5 · OQ-T32-4 · FU-T37-2 |

## Branch

Work is on `spec/service-specs-and-ai-framework`, branched from `main`; `main` stays deployable. Eighteen
commits, one per phase boundary, each pushed to origin.

---

**T39 CLOSED — PASS.**
