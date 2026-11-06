<!-- MODEL: Claude Opus 5 — Phase 13 (PR / Commit Preparation) -->

# frontend · T35 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | `artifacts/12-specification-verification.md` — verdict **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12's verdict is **PASS**, so this phase proceeds. Branch: `spec/service-specs-and-ai-framework`, cut
from `main`; `main` stays deployable.

## Commit title

```
feat(frontend): sign-out clears memory, and the way back in it cannot close (T35)
```

## Commit message

```
feat(frontend): sign-out clears memory, and the way back in it cannot close (T35)

Give the SPA a sign-out that leaves the tab holding nothing: drop the
access token, discard any pending sign-in notice, remove the three PKCE
items and the remembered return target. Four calls, no state, no
parameter, nothing deferred - task 36 follows this with a full-page
navigation to /connect/logout, and a full-page navigation waits for no
microtask.

It composes rather than reaches. Every piece of session state already
has a module that owns it and a primitive that clears it, each already
idempotent and already guarded against a store that throws, so this
function assigns nothing and calls no storage API itself. The knowledge
of which key holds what stays where that key is written, and a second
spelling here is what task 22's scan forbids. clearReturnTarget is new,
in returnTarget.ts, because sign-out REMOVES where the callback
CONSUMES: it has no navigation to feed, so takeReturnTarget's
destination would be a value with nowhere to go, plus a read that
sign-out otherwise never makes (T24-D4).

Only the first call's position matters, and the reason is precise:
there is no try/catch, so a throw abandons what follows. None of the
later three can throw today, but the clear is the one step whose
omission is a security defect rather than an untidy store - with it
first, the worst a later failure leaves behind is residue, and residue
is not a session. A mutant that moved it last survived the suite as
first written, which is how the claim stopped being a comment.

What this commit cannot do is close the way back in. A tab that has
signed out can be handed a live token again by a sibling:
crossTabRenewal's listener adopts on any renewal:succeeded and never
asks whether this tab signed out. Task 39's propagation narrows that
window without closing it, since a sibling whose renewal resolves
before it handles the broadcast still posts and the channel orders
nothing. It is not reachable from here - signOut cannot reach the
SharedRenewal closure, and a latch means adding state in the task whose
statement is removing it - so the suite drives a real BroadcastChannel
and pins the sequence as it is, with the message written so the good
outcome reads as an instruction. L18 is recorded as NOT satisfied.

Nothing in the SPA calls this and nothing can: no authenticated route
has an element and no task owns an affordance. Deliberately no dormancy
canary, unlike task 33's - that one failed on wiring because its message
carried an instruction the author needed, and this one would only
obstruct the person doing the right thing. AC10 guards the shape
instead: no module may assemble a second, partial sign-out, including
one assembled a single allowlist entry at a time.

Three closed suites were touched. T23's single-caller pin on
consumeAuthorizeState now reads three, on T23-D2's own threat model: it
narrows deletion to the consuming path so a drive-by
/app/callback?state=x cannot end a pending sign-in, and sign-out is
unreachable by a link - while leaving the triple would let a callback
still arrive and still complete an exchange, a session established
after sign-out. T24's suite gained the new export's contract, because
that module is T24's and a refactor of it should fail there.

Verifies signOutClearsMemoryAndEndsSasSession. Cites R19.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

> **Trailer note, carried forward for the seventeenth consecutive task.** The phase 13 prompt mandates the
> `Claude Opus 4.8 (1M context)` trailer above, and it is reproduced verbatim because the prompt is the
> contract. It is stale: this work ran on Opus 5, and the repository's commits use
> `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`. The single source is `.ai/generate.py:476`;
> fixing it there corrects every task's phase 13 at once. Unchanged because the generator is not T35's to edit.

## Files changed

**Production (2 files, one new):**

| File | Change |
|---|---|
| `frontend/src/auth/signOut.ts` | **new**, 88 lines — `signOut(): void` |
| `frontend/src/auth/returnTarget.ts` | **+28** — one additive export, `clearReturnTarget()`. No existing line changed. |

**Tests (3 files, one new):**

| File | Change |
|---|---|
| `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **new**, 992 lines, **38 cases** over AC1–AC12 plus AC9b |
| `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | **+55** — four cases holding `clearReturnTarget` to T24's contract |
| `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx` | **+20/−1** — T23's single-caller pin widened to three, with the argument |

**Pipeline artifacts:** `.ai/prompts/frontend/T35/artifacts/00`–`13`, including `07-mutation-harness.py`
(24 mutants). **No file under `spec/` was modified.**

## Summary

A visitor who signs out now holds no token, no pending notice, no PKCE triple and no return target, with the
clearing complete and synchronous before anything navigates. The function takes no parameter — passing one is
a compile error — so there is no failure reason to leak or branch on, and it reaches no storage API itself.

Two things a reviewer should not miss:

1. **L18 is violated, by built code, and this task is what measured it.** A signed-out tab can be
   re-authenticated by a sibling's renewal. Pinned (AC9), escalated (**OQ-T35-3**), and task 39's to fix.
2. **Three closed suites were touched**, T23's security pin among them. The arguments are in the tests and in
   phases 6, 9 and 12.

## Testing performed

| Gate | Result |
|---|---|
| T35 cases | **38 passed**, exit 0 — run three times, no flake |
| T24's suite | **188 passed** |
| whole suite | **1616 passed / 1 failed of 1617** — the failure is `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, pre-existing and environmental on this host (`oxlint@1.87.0` prints no summary line for the assertion that parses it) |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| mutation | **23/23 regressions killed · 1/1 canary killed · 0 survivors · 0 broken** |

Each mutant's failure count is recorded, not just its verdict, so a kill through the wrong instrument is
visible. Six coarse kills were verified narrowly by filtering to their own criterion rather than trusted:
SO12 (306 failures) → AC2's two cases · SO23 (58) → AC8's one · SO17 (9) → AC10's one · SO21 (9) → AC6's one ·
SO24 (7) → AC12's three · SO18 (2) → AC1's one. The lone canary, **SO15**, is the inverse mutant: because AC9
asserts the hazard *as it is*, the mutant that kills it is the one that **fixes** the hazard.

Adversarial review: **Kimi 2.7** at phases 3, 8 and 11 — twelve, twelve and thirteen findings. Nine of
phase 8's and ten of phase 11's went into code; two recommendations were **declined with a measurement**
(phase 8 I9 asked for a case that cannot pass; phase 3's F4 canary would have obstructed the wiring), and two
of my own answers were corrected the same way (F9's monopoly rule would have forbidden `CallbackScreen.tsx`;
phase 9's AC9 poll could report a slow machine as a repaired defect). Phases 4 and 9 passed the Human
Approval gates.

## Specification references

- **Task:** `spec/frontend/tasks.md` task **35** — "Sign-out clears memory."
- **Requirement:** **R19** (`requirements.md:62-65`), clause 1; its `/oauth2/revoke` call was removed by Q5
  on 2026-10-10.
- **Adjacent, not regressed:** **R22** (cross-tab propagation, task 39) · **R57** (identity navigations
  bypass the service-worker fallback) · **R8** (the discarded-callback notice this clearing meets) ·
  **R19 cl. 2** (task 36).
- **LOCKED decisions** (`design.md` §4a): **L3** tokens in memory only · **L4** enumeration-safe copy ·
  **L6** generated client only · **L11** catalogue-only strings · **L12** phase additivity · **L14** the
  declared route set · **L16** the sessionStorage carve-out · **L18** cross-tab coherence — **recorded as not
  satisfied, see D4**.
- **Named test:** `signOutClearsMemoryAndEndsSasSession` (`tasks.md:63`, `:64`). Task 36 extends this file.
- **Not this task:** `signOutThenSignInRequiresCredentials` is task 38, blocked on Q15 ·
  `signOutPropagatesToAllTabs` is task 39.
- **Drift recorded, not fixed:** `package.md:123` still names the test
  `signOutRevokesTokensClearsMemoryAndEndsSasSession`, with the revoke step Q5 removed from R19.

## Open questions carried out of T35

- **OQ-T35-3 (NEW, Blocker, task 39's).** A signed-out tab can be re-authenticated by a sibling's renewal
  (`crossTabRenewal.ts:95-111`). Confirmed by measurement against a real `BroadcastChannel`. The fix is a
  decision about closed modules: either `adopt` refuses after a sign-out, which means a latch, or sign-out
  closes the `SharedRenewal`, which means it must receive the instance.
- **OQ-T35-2 (the owner's, sixth time).** Nothing offers sign-out and nothing can. Tasks 5, 6, 30, 31, 33 and
  now 35 are built and uncalled; 36–39 will follow. Same substance as **OQ-T33-2**.
- **OQ-T35-1 (frozen).** Task 36 retains the `id_token`, not task 35. Task 36 should first read
  `SasLoginIntegrationTest.endSession*`, which `design.md:168-169` names as the proof end-session works, to
  settle whether the `id_token_hint` is needed at all or `client_id` suffices.
- **OQ-T32-4.** Helper duplication — the `strip` helper is the fourth copy, the boundary harness a fifth
  family. Raised by **eight** reviews across three tasks.

**Phase 13 complete. T35 closed.**
