# frontend · T18 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

No production code changed in this phase.

---

## What this phase added

Auditing AC1–AC17 for genuine gaps rather than padding the count produced **three cases in two groups**,
128 → **131**.

### 1 · The D-12 ordering was asserted nowhere

T17's D-12 says an authorization error response is discarded **before** any state comparison. Nothing proved
it. The early-return no-read sweep runs over `DISCARD_SHAPES`, which carries **no `error` row**, and the two
existing error cases arrive with nothing stored — so they would pass whether the error check ran before or
after the comparison.

Two cases now seed a **matching** stored state and arrive with an error present, asserting no exchange, a
return to sign-in, and that the stored state was **not read**. A regression that reordered the two guards
would exchange an error response carrying a valid-looking state; that is now caught.

### 2 · OQ-2 is pinned as a tripwire

L16 calls the four items "single-use and **deleted at callback**", while D-7 placed deletion with task 23 by
name. The consequence — the same callback URL compares equal every time it arrives — was recorded in prose
across four artifacts and asserted nowhere.

It is now a case, in the same shape T17 used for the storage boundary that T18 later had to cross: two
arrivals of the identical URL, no re-seeding between them, asserting the second **still** reaches the seam,
with an assertion message reading `'task 23 will make this an empty array'`. **Task 23 is expected to invert
it**, and now inherits a failing test and a stated position rather than a surprise.

That pattern earned its place in T17 — the boundary assertion fired exactly when T18 crossed it, which is how
this task discovered the crossing in phase 0 rather than in phase 6.

---

## Test manifest

`frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` — **131 cases**. The named test from
`package.md` §8 (→ R8), shared with T17.

| Block | Verifies |
|---|---|
| a callback with no usable state is discarded | T17: AC1–AC3 |
| nothing is exchanged | T17: AC4, F1, F2 |
| the return navigation | T17: AC5, AC17 |
| session storage is left exactly as T15 wrote it | T17: AC7, AC8 |
| the notice is in memory and single-read | T17: AC6, AC16 |
| it handles the callback once, under StrictMode | T17: AC14, D-8 |
| accessibility and copy | T17: AC9, AC10 |
| the new files touch nothing they must not | T17: AC15, D-9 |
| the second strand of the circular import stays safe | T17: AC12 |
| the notice drains exactly once, as a unit | T17: AC16, D-3 |
| the shapes a real authorization server can send | T17: R8 boundaries; **T18-D4 read split** |
| **a callback whose state does not match is discarded** | **T18: AC1, AC2, AC3, AC6** |
| **an empty state never exchanges, whichever guard catches it** | **T18: AC3, defence in depth** |
| **the error check still precedes the comparison** | **T18: D-12 ordering** |
| **OQ-2: compared but not consumed** | **T18: tripwire for task 23** |
| **storage denial does not strand the visitor** | **T18: I1/G1** |
| **the comparison is against the stored value, not a constant** | **T18: AC5** |
| **a mismatch is indistinguishable from a missing state** | **T18: AC7, L4** |
| **a mismatch leaves storage and the page as T17 does** | **T18: AC8, AC10, AC11, AC12** |
| **the comparison is not widened in source** | **T18: AC16, AC17** |
| the seam is honest about being unfinished | T17: AC4, D-1, D-2 |

**AC14** (whole-suite regression) is discharged by running the suite, not by a case — **692 passed, 1 failed
(693)**, the failure being the known environmental `lintRejectsHandWrittenBackendFetch` AC4.

### Non-vacuity

Every matcher is fed a wrong input: the mismatch table has a positive control; AC5 runs over **two unrelated
matching values** after phase 9 proved one was not enough; the six widening patterns each carry their own
violation; the AC7 outcome comparison asserts the collected outcome is non-empty before comparing three of
them; and the empty-state block's doc comment names the two-part mutant that makes it load-bearing.

### Mutation verification

Thirteen mutants run across phases 6, 9 and 10. **Eleven killed; two are equivalent by construction** (N2, N3
— removing `stored === null` or `stored === ''`, which no test can distinguish given T17's guard) and are
documented in the code with what they defend.

---

## Verification in a real browser, against the live stack

T18 changed the callback's live behaviour, so the suite is not the last word. Driven over CDP against the full
`infra/local` stack through the Caddy edge.

```
1 real callback   /app/callback   "Finishing sign-in  Checking your sign-in…"
  states agree    true
2 tampered state  /app/sign-in    "Sign in … Your sign-in did not complete. Try again."
  stored survives true
```

A real MERCHANT sign-in reached the callback and **held** — the stored and supplied states agreed, so T18 does
not break the path T17 verified. Then **the same URL, with one character of the `state` changed and everything
else identical including the authorization code**, was discarded to the sign-in screen with the neutral copy.

That is the task's entire security property demonstrated outside the harness: same code, one character of
state, opposite outcome. The stored state survived the discard, confirming D-7 holds in the real browser and
task 23's deletion has not been pre-empted.

---

## Open questions

**OQ-2** — now a test rather than prose; task 23 inverts it.
**OQ-1** — implemented as AC2, the second row of `MISMATCH_SHAPES`.
**OQ-3** — the copy is unreviewed; T18 added no string. **Gates nothing.**
**I9 carried to phase 12** — confirm the environmental `oxlint` failure is identical on CI. The exact-total
discipline (693 here) is what stops a new failure hiding behind it.

---

## Next

**Phase 11 — test review. This is Kimi 2.7's phase, and I am stopping here for it.**
