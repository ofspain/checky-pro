<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review & Mutation Testing) -->

# frontend · T37 · Phase 7 — Self-Review & Mutation Testing

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/06-implementation.md` |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |

## Gates

| Gate | Result |
|---|---|
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **26 passed (26)** |
| `vitest run` (whole suite) | **1688 passed / 1 failed of 1689** — the one is the known `oxlint` host case in `lintRejectsHandWrittenBackendFetch.test.ts` |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `07-mutation-harness.py` | **23/23 regressions killed, 0 survivors, 0 broken, 0 canaries** |

## What phase 7 actually found

Phase 6 handed three things forward. All three are settled below, and two of them turned out to be
smaller than what the harness found on its own.

### 1. A mutant survived, and the gap was in the suite's reach, not its coverage

This is the finding of the phase. It came from reading the **test file** rather than the source, and
noticing that every single assertion in it reaches through `main!` — AC5's link count, AC6's
byte-identical compare across four query shapes, AC11's whole-text equality, AC13's token-held
comparison. Nothing looked at the container *outside* the landmark.

So **SL23** was written to exploit exactly that: the screen wraps its markup in a fragment and renders
a second anchor as a sibling of `<main>`.

```
SURVIVED  SL23 a second way out is rendered OUTSIDE <main>, where every case is scoped not to look
          (1 failed of 1688 — the known environmental case, and nothing else)
```

A screen whose one behavioural criterion is *"one way back, and only one"* had no case that could see
a second one placed a line outside the landmark. Twenty-five cases, 100% of the criteria exercised,
and the hole was in **where they looked**, not in what they claimed.

**Fixed, with a control.** AC5 repeats its counts at the container and asserts `<main>` is the
container's only element child; AC11's whole-text equality is asserted at the container as well as
inside the landmark, so a string moving *out* of `<main>` fails rather than merely relocating. The
control proves the two are not the same assertion twice — a node appended beside `<main>` leaves
`main.textContent` untouched and changes the container's. 25 cases became 26.

The lesson is worth keeping, because it is not about this screen: **a scoped query is a claim about a
region, and a criterion about the whole screen cannot be measured inside one.**

**And the control took three tries, each failing the same way.** Narrow verification of SL23 showed the
*control* firing alongside the case it exists to validate — twice:

1. It asserted the container holds exactly **two** anchors. That is the screen's own count, so under
   SL23 (three anchors) it failed. A control that moves with the production code is not a control.
2. Made relative, it then asserted the **landmark** holds `before` anchors — `before` being the
   *container's* count. True only when every anchor is already inside the landmark, which is precisely
   what SL23 breaks. Same bug, one layer in.
3. The landmark's count is now compared to **its own** before-value.

Both slips had the same shape: a control reaching for a number that belongs to the thing under test. It
is the same error in the same family as T35's hardcoded `SIGN_OUT_SET`, and the only reason it was
caught is that the mutant was verified **narrowly** rather than by its kill count — the harness said
`KILLED, 4 failed` all three times.

### 2. The mutant corrected its own description — and so did two code comments

**SL14** was written as *"SIGN_IN_PATH is read at module scope, which is a startup failure rather than
a test failure"*, on the authority of `routes.tsx:2-5`:

> *"…nothing touches the binding while it is still in its temporal dead zone. That property is pinned
> by a test in the sign-in suite; **if it is ever broken the application fails to start, not a test**."*

T37's own header repeated it, citing that comment. Measured, it is wrong:

| Measurement | Result |
|---|---|
| `const BACK = \`/${SIGN_IN_PATH}\`` at module scope in `SignedOutScreen.tsx` | **0 ReferenceErrors.** The screen renders. The way back becomes `href="/app/undefined"` — a dead link on the one screen whose only job is to offer a live one. 2 cases fail: AC5's href and AC13's scan. |
| `const BASE_AT_MODULE_SCOPE = APP_BASENAME` in `authorizeRequest.ts` — the cycle task 16's comment is **actually** about | **0 ReferenceErrors**, whole suite 1686/1688, and exactly **one** real failure: `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`. |

So the discipline is right and the reason given for it was not. What catches a module-scope read in a
route cycle is **a test, never the runtime** — which is why AC13 has one, and why it carries a control.
The comment's own second clause ("pinned by a test in the sign-in suite") is the part that held; its
first clause contradicted it.

- **T37's header is corrected** (commit `docs(frontend/T37): the screen's header repeated a claim
  measurement contradicts`), and states the measured consequence with the numbers.
- **SL14's description is corrected** to what it measures.
- **`routes.tsx:5` still carries the false consequence.** It is a comment in task 16's closed file, and a
  comment is not an instrument, so the rule T36's phase 9 wrote does not license editing it. Recorded
  for the owner as **FU-T37-2**.

A note on the limit of this measurement: it is the behaviour of **Vite's SSR transform under Vitest**. A
production Rollup bundle hoists modules into one scope and may genuinely throw. The honest claim is the
one now in the header — nothing throws here, and a test is what catches it — not that no build could.

### 3. AC12 was the only criterion with no mutant

Counted after the first full run: every mutant landed on a copy, a route, or an affordance criterion,
and **AC12 — "renders and stays put rather than redirecting" — had none.** That is the criterion that
holds the design phase 4 *rejected*: redirecting a token-holding tab away from this screen, turned down
on the measurement that `/app/home` renders nothing.

**SL22** plants it (`if (accessToken() !== null) return <Navigate replace to="/home" />`) and dies on 12
cases. A rejected design with no mutant is a rejection nothing is holding.

### 4. AC9's two pins — decided, with the subset measured

Phase 6 asked whether T37's count pin should defer to task 16's. **Both stay**, and the reason is now
measured rather than asserted:

- Against every mutant here, T37's AC9 is a **strict subset** of task 16's `:161`. Task 16 compares to
  the literal `'signed-out'`, so it also fails when the published constant is renamed. T37's spells both
  sides with `SIGNED_OUT_PATH` and is **blind to a rename by construction** — confirmed by **SL16**,
  which renames the constant and is killed by AC10's spelling case and task 16's literal list, while
  AC9 passes.
- It is kept anyway because **task 40 extends this file** with the exhaustive public-route claim, and
  this case is the seed it grows from: the total expressed in the route table's own vocabulary. The day
  task 40 writes that claim, the pin to retire is **task 16's**, not this one.
- Reviewing it also found it **missing the control task 16's twin has**: it asserted the total without
  ever checking that `walk` could return anything else. A `walk` that always returned a three-element
  list would have passed it. Control added — two attached routes (one nested, one via `errorElement`)
  report both; a bare path reports none.

### 5. A family the criteria name nowhere: document structure

Nothing in T37's thirteen criteria, in L4, or in R19 says anything about landmarks or heading level, so
three mutants were added to find out what the suite *would* say.

| Mutant | Outcome |
|---|---|
| **SL19** `<main>` becomes a `<div>` | Killed, 11 failures — claimed here to be **on a `TypeError`**. **CORRECTED IN PHASE 9 (see `09-review-resolution.md`, I10):** verified narrowly, the first failure is AC1's *named* assertion, so the landmark is pinned by a criterion. The real defect was its message — `the route resolved to nothing`, which is false under this mutant. Split in two in phase 9. |
| **SL20** `<h1>` drops to `<h2>` | Killed, 4 — AC3's `querySelector('h1')` returns nothing. Incidental, but the message names the heading. |
| **SL21** the way back is rendered above the heading | Killed, 2 — AC11's whole-text equality, which is order-sensitive. |

All three die, so no instrument is added; but **SL19's kill is the kind to be suspicious of** — a crash
is not a claim. Recorded as **FU-T37-1**: the screen's structure is pinned only as a side effect of
assertions about other things.

### 6. Two stale labels and one limit in the inherited machinery

Sixth copy of T35's harness (the extraction is still **OQ-T32-4**, now ten reviews across five tasks):

- `baseline T35 cases:` printed above **T37's** own baseline — fixed to `baseline own cases:`.
- `REFUSING TO RUN: T35's own cases fail …` — fixed to `this task's own cases`.
- `wanted = sys.argv[1]`, so a filter takes **one** name; `SL22 SL23` silently ran only SL22 and
  reported `1/1 killed`. The tally is honest about what it ran, but the count can be mistaken for the
  whole set. Not fixed — it is the inherited design, and the fix belongs with the extraction.

## What D2's mitigation measured

Phase 6's **D2** said AC4's cause-vocabulary list is a denylist, mitigated by AC11's whole-text
equality. That is now a measurement rather than an argument:

| Mutant | AC4 (the denylist) | AC3/AC11 (whole-text) |
|---|---|---|
| **SL04** `'Your session expired. Sign in again.'` | **fires** | fires |
| **SL05** `'You have been signed out of all devices'` | **silent** — no denied word appears | **fires** |
| **SL06** `'Your session has ended. Sign in again.'` | **silent** | **fires** |

SL05 and SL06 are the two mutants the denylist cannot see, and both assert something the screen cannot
verify — SL06 in `signIn.sessionEnded`'s own words, which phase 4 rejected **by name**. The whole-text
equality is what holds the criterion; the denylist only makes the *common* failure legible.

## The 23 mutants, and the case that kills each

Verified narrowly: each mutant applied alone, only T37's file run, every failing case named, so a
coarse kill is checked against the case written for the regression rather than assumed. The intended
case fires for **all 23**.

| | Mutant | Killed by |
|---|---|---|
| SL01 | route loses its element | AC1 (+10 incidental) |
| SL02 | heading spelled in the component | AC3 *holds no user-facing literal* — **only this case** |
| SL03 | catalogue section renamed | AC3 *adds no key outside its own section* (+8) |
| SL04 | copy names a cause | AC4 vocabulary scan, AC3, AC11 |
| SL05 | heading asserts unverifiable state | AC3, AC11 — **AC4 silent** |
| SL06 | explanation borrows `signIn.sessionEnded` | AC3, AC11 — **AC4 silent** |
| SL07 | action borrows hand-over wording | AC5, AC3, AC11 |
| SL08 | `Link` → button + `navigate` | AC5 *is a real anchor* |
| SL09 | a second way out | AC5 link count |
| SL10 | way back points elsewhere | AC5 href, AC13 scan |
| SL11 | reads the query string | AC6 *cannot be reading the query, by source* + AC6 render compare |
| SL12 | reports its arrival to the backend | AC7 both cases |
| SL13 | clears the session on arrival | AC8 — **only this case** |
| SL14 | module-scope read → `/app/undefined` | AC13 scan, AC5 href |
| SL15 | dropped from the public set | AC2 *is declared PUBLIC* (+12) |
| SL16 | published constant misspelled | AC10 spelling — **AC9 blind, by construction** |
| SL17 | `endSession.ts` reverts to a private copy | AC10 spelling — **only this case** |
| SL18 | a fourth screen attached | AC9 count pin, and task 16's `:161` |
| SL19 | landmark → `div` | a `TypeError`, not a case — **FU-T37-1** |
| SL20 | `h1` → `h2` | AC3 heading read |
| SL21 | action rendered above the heading | AC11 whole-text (order-sensitive) |
| SL22 | token-holding tab bounced to `/app/home` | AC12 (+11) — the design phase 4 rejected |
| SL23 | second way out **outside** `<main>` | **SURVIVED** until AC5/AC11 were widened to the container; now AC5's container count + AC11's container text, with the control passing on both trees |

## Still true, and not fixable here

- **Nothing calls `endSession()`.** No visitor can reach this screen by signing out; every case is a
  direct arrival, which AC12 says out loud. **OQ-T36-5**, eighth consecutive dormant task.
- **OQ-T37-1 stands.** The copy is derived, not specified. Phase 7 can prove the words cannot change
  silently; it cannot prove they are the right words. That is the owner's.
- **Task 38 still reads "Blocked on Q15"** in `tasks.md:66`, and Q15 is about revocation semantics, not
  sign-out (`package.md:421-430`; `SasLoginIntegrationTest:475` already asserts the post-logout
  behaviour task 38 would test). Task 38 is the only end-to-end proof of R19. Unchanged from T36 —
  `spec/` is not mine to edit.

## Follow-ups opened here

| | |
|---|---|
| **FU-T37-1** | The screen's document structure — landmark, heading level, order — is pinned only as a side effect of assertions about other things, and SL19 dies on a `TypeError`. No criterion names it. |
| **FU-T37-2** | `routes.tsx:5` states that a module-scope read in a route cycle makes the application fail to start. Measured, it throws nothing and fails one test. Task 16's closed file; a comment, not an instrument. |

## Phase 8

Kimi 2.7's gate. Nothing self-produced.
