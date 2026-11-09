<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T39 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Consumes** | `artifacts/04-freeze-task.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

No production code changed in this phase. Eleven accepted findings land here, and three harness
rows were waiting to be killed.

## Gates

| | |
|---|---|
| `vitest run` | **1721 passed / 1 failed of 1722** — the known `oxlint` host case |
| `vitest run src/api/signOutPropagatesToAllTabs.test.ts` | **22 passed (22)** |
| `vitest run src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **91 passed (91)** |
| `vitest run src/api/siblingTabsShareOneRenewal.test.ts` | **23 passed (23)** |
| `tsc --noEmit` · `oxlint src` | **exit 0** · **exit 0** |
| `07-mutation-harness.py` | **31/31 regressions killed · 0 broken · 1 unfalsifiable, scored apart** |

---

## Test manifest — `src/api/signOutPropagatesToAllTabs.test.ts` (22)

| Case | Verifies |
|---|---|
| AC1 · runs the local action in the receiving tab | **R22** — the requirement's core clause |
| AC1 · reaches EVERY listening tab, not just the first | **R22** — "every open tab" is the requirement's own word, and one listener cannot measure it |
| AC2 · puts exactly one message on the channel, counted by a third endpoint | AC2 · no echo, measured not argued |
| AC2 · never calls a broadcast from `signOut.ts`, by source | AC2 · the structural guarantee that makes an echo impossible |
| AC3 · names no identity path in the propagation module | **R19** · the SAS session ends once, by the acting tab |
| AC3 · CONTROL: that scan fires on a planted navigation | the scan above is not looking for nothing |
| AC4 · is exactly a type and no other property | **L3** · a broadcast is a surface; the message carries nothing |
| AC4 · **pins the channel NAME by literal** *(new, I8)* | **L18** · version skew: two versions must agree on the channel |
| AC4 · spells no token, identifier or timestamp anywhere in the module | **L3** · no correlation value crosses the channel |
| AC5 · works with `navigator.locks` removed entirely | **L18** · why there are two channels, not a third message type |
| AC5 · names no lock anywhere in the module, by source | the same claim, held against a future edit |
| AC6 · broadcasts nothing, listens to nothing, returns a callable `close()` | degradation is a no-op, not a throw |
| AC10 · touches no store while broadcasting, listening or latching *(control added, I3)* | **L16** · nothing is stored on any path |
| AC10 · names no store and no cookie in the module, by source | **L16**, **L3** |
| AC11 · ignores every shape that is not a sign-out *(control added, I3)* | only a sign-out is acted on |
| AC11 · ACTS on a sign-out carrying an unknown extra field, deliberately | **R22** · the lenient parser as a decision (phase 4, rejecting Kimi F8's strict half) |
| AC12 · does nothing with a message that arrives after close *(reworded, F12)* | lifecycle |
| AC12 · leaves an INJECTED channel open for its owner to close | lifecycle · a channel this module did not create is not its to close |
| CONTROL · stripping comments leaves the real declarations behind | every source scan here reads stripped text |
| AC13 · imports nothing from `auth/` | layering · why the local action is a parameter |
| AC13 · CONTROL: that scan fires on a planted upward import | — |
| AC16 · declares no BroadcastChannel at module scope *(widened, I10)* | one piece of module-scope state, and it is the latch |

## `src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` — task 35's suite (91)

AC9 (6 cases) · AC9c (1, the pinned hazard) · AC9d (2) were written in phase 6. Phase 10 changed four
things: `markSignedIn()` in the `afterEach`, the `markSignedIn` entry in `PERMITTED_CALLERS`, comment
stripping in AC9d's positional scan, and the two helpers' indentation.

## `src/api/siblingTabsShareOneRenewal.test.ts` — task 21's suite (23, one new)

| Case | Verifies |
|---|---|
| **counts a signed-out tab's outcome and settles its waiters, while refusing the adopt** | **R21** holds for the state **T39 created** · and **OQ-T39-7**, asserted as a fact |

---

## What the findings became

### I10 — the rejection that reversed, and the second hole found while probing the first

Phase 9 rejected it; **XT32 survived all 1 720 tests** and reversed that. Phase 10 widened T39's own
AC16 on **two** axes, and the second was found only because the first needed a control:

| axis | what it was missing |
|---|---|
| `^const` → `^(?:export\s+)?const` | **fourteen** exported module-scope object, array and `Set` literals in production source — among them **`auth/accessToken.ts:46`**, the session object itself, and **`api/client.ts:6`**'s `new Set([…])`. Two non-exported lookup tables are caught and excused by name; the fourteen have never been looked at. |
| `new Set\s*\(` → `new Set\s*(?:<[^>]*>)?\s*\(` | a type argument on the constructor defeats it. **`pwa/registerServiceWorker.ts`** is excused with a **pinned** `declarations: 2` and holds **three** pieces of module-scope state: two `let`s and `const listeners = new Set<UpdateListener>()`. The pin says two because the third is unseen. |

A `const` binding to an object literal is not immutable — the binding is, the contents are not, and
`as const` is a compile-time claim. That is exactly why the inherited pattern includes `= {` and `= [`
at all, so the exported form is the same hazard wearing a keyword.

**Widened in T39's instrument only.** Task 35's shared pattern is read by task 22's accounting and two of
task 36's cases; changing it would alter what fourteen files in four closed tasks are measured against
**and would turn an excused file's pinned count false**. That is a decision about those tasks →
**FU-T39-1**. The T36 phase-9 rule draws the line: an inherited instrument may be edited when it has
become false or must grow to admit new state, not when it is incomplete for something another task owns.

### The control caught a defect in the control

The first version of AC16's widening control shared one `/gm` regex with the `match` above it and reported
**false for a string it matches**: `.test()` on a global regex advances `lastIndex`, so the second probe
began part-way through the third. A fresh `RegExp` per probe now. Had the probes been ordered differently
this would have shipped as a passing scan that had quietly stopped looking — the same failure shape as a
scan that reads prose, reached by a different route.

### F13 / I14 — the paragraph that nothing held

Six lines in `crossTabRenewal.ts` argue that the guard sits **below** the outcome count and the waiter
wake, because this task changes what a signed-out tab *adopts* and not how renewal is counted or settles.
**XT27 hoisted it and survived all 1 720 tests**, because the only suite that counts outcomes —
task 21's — never signed out, so the guard was inert there whatever its position.

The new case is in **task 21's suite**, where the follower rig already exists, and it asserts three things
that together separate the two placements:

- the signed-out follower's `renew()` **settles from the sibling's outcome** → the outcome was counted and
  its waiter woken under the latch;
- `b.renew` was **never called** → it followed rather than waiting out its grace and renewing itself,
  which is what the hoisted guard would force;
- `b.adopt` was **never called** and `current()` is unchanged → so the case cannot pass by deleting T39's
  guard instead of moving it.

It also writes down **OQ-T39-7** as a measured fact, which is the test half of Kimi I6: a signed-out
follower's own `renew()` **receives** the sibling's token. The latch is set and released inside the case,
in a `finally`, so no other case in that file can be reached by it.

### F3 / I3 — two negative cases that could not tell a refusal from a silence

Measured before the fix: a deaf listener failed six cases and **AC11's first passed**, as did AC10's. Both
now end with a positive control **inside the case** — a real sign-out on the same channel through the same
listener — because a channel that delivers nothing satisfies every negative assertion in the file.

### F4 / I4 — and AC2 needed it more than predicted

AC9d's positional scan now strips comments, with the measurement in its own comment: one comment-only edit
in `endSession.ts` moved `atAssign` to 225 and `atBroadcast` to 935 and failed correct code.

AC2's grep of `signOut.ts` turned out to be the more urgent of the two, for a reason created in **phase 9**:
the rewritten header **names `markSignedOut()` in prose**, so `grep -c` finds it **twice**. The assertion
"`signOut()` no longer sets the latch at all" could have passed on a comment while the call was gone. The
stripper is now one shared function with a control at each site.

### F2 / I2 — the reset that was imported and never called

`markSignedIn()` is in the `afterEach` now. **One** reset point: Kimi I15 asked for a second in
`renewalRig.close()`, and two make it ambiguous which one is load-bearing.

### I9 — the more dangerous direction

`markSignedIn: ['auth/accessToken.ts']` in `PERMITTED_CALLERS`. An unauthorized `markSignedOut()` signs a
tab out too eagerly; an unauthorized `markSignedIn()` **releases** the latch and re-opens OQ-T35-3. The
stray-caller sweep iterates this table's keys, and `markSignedIn` was not one of them, so a module calling
it anywhere was invisible. The single caller is also the design: `retainAccessToken` is the only place a
non-null token is assigned, so a second sign-in path cannot silently skip the release.

### F12 / I14 — XT18, and what a number should not claim

The two assertions that said `close()` is idempotent now say what they hold — a second call throws nothing.
XT18 removes the guard and **nothing can fail**: a double `removeEventListener` and a double
`BroadcastChannel.close()` are both no-ops by specification. Phase 9 declined to give `close()` an
observable side effect for a test's convenience, so the harness now scores the row **apart**, as
`UNFALSIFIABLE`, and the run exits 0. A row that cannot be killed without changing production is a
limitation of the code's observability, not a gap in the suite, and counting it either way would lie.

### A harness fix the transcript asked for

XT27's kill printed `INHERITED-ONLY` **with no named case**, because narrow verification read three files
and the case that kills it is in a fourth. Task 21's suite is in `OWN_TESTS` now, and the row names its
killer.

---

## Harness

**32 mutants · 31/31 regressions killed · 0 broken · 1 unfalsifiable, scored apart.** Baseline
1 failed of 1722 (environmental), 0 failed of 166 across the four own files. The attribution check against
AC9c never fired: XT23 and XT26 do fail it, each alongside a named regression.

## Open questions

**OQ-T39-6** (AC9c, pinned) · **OQ-T39-7** (now a measured fact, not a sentence) · **FU-T39-1** (the
shared scan's two holes) · **OQ-T36-5** (nothing calls `endSession()` — T39 is the ninth consecutive
dormant task) · **OQ-T32-4** · **FU-T37-2**.

## Phase 11

Adversarial test review — **Kimi 2.7**. Stop here.
