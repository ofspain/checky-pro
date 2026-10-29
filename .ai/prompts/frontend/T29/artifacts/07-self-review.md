# frontend · T29 · Phase 7 — Self-Review and Mutation Score

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |

| | |
|---|---|
| The file | 1477 lines, **46 cases**, 29 of them controls, twelve `describe` blocks |
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean |
| The file alone | **46 / 46**, exit 0 |
| Whole suite | **1388 passed / 1 failed (1389)** — the known `lintRejectsHandWrittenBackendFetch` environmental case |
| Mutation | **18 / 18 killed, survivors: none. All 18 by T29's own file.** |
| Production change | none — `git diff --name-only` names one file, the test |

Harness: `.ai/prompts/frontend/T29/artifacts/07-mutation-harness.py`, committed, run from the repository root
with everything under `frontend/src/` committed first.

---

## S1 · My own claim about MC17 was wrong, twice over, and the measurement is the finding

This is the most useful thing in this phase, so it goes first.

Phase 6 found that `URLSearchParams` encodes a space as `+`, so the leak sweep's raw comparison would have
missed a description surviving in a URL, and added `encodings()` to search the decoded form. The obvious next
question is whether `encodings()` is load-bearing or decoration — and **the first two mutants written to
answer it both failed to answer it**:

| Mutant | Killed? | But by what? |
|---|---|---|
| **MC03** — the description carried into the sign-in URL | yes, 24 failures | **AC1's absolute `search === ''`**, which fires on any query string at all. Says nothing about the decode. |
| **MC17** — the description percent-encoded into session storage | yes, 41 failures | **measured with `encodings()` bypassed: still 3 failures.** `encodeURIComponent` leaves a single word untouched, so `suspended` survives encoding intact and the *state* needle is found raw; and `storedKeys` grows a key, which the struct control catches. |

So two mutants that looked like they tested the normalisation tested something else. **MC18** is the one that
isolates it:

- it leaks **only the `unknown` fixture's** description — *"No account exists for that email address"*, which
  is the one description in the table that does not contain its own state word;
- it leaks to **`window.name`**, which is not a field of the outcome struct.

Nothing but a sweep of the decoded form can see it. **Measured: with `encodings()` bypassed and the router
control relaxed, MC18 survives all 46 cases.** With them restored it is killed.

The harness header now records all three, including both wrong guesses. A harness that keeps a wrong
prediction in its own documentation is worse than one with no prediction at all — T28 learned that with MB12
and MB23, and this is the same lesson arriving a second time from a different direction.

## S2 · The timing claim is doing the work it says, and I checked rather than assumed

AC5 is the one claim no earlier task in the package makes, so a plausible-looking failure would be for it to
be killed by something else. **MC11** — `setTimeout(discard, 0)`, identical copy to an identical destination,
deferred by exactly one macrotask — was driven against T29's file alone:

```
× the discard has already navigated by the time the render returns
× renders exactly the discarded sentence at clock zero, for every account state
× lands on the sign-in start at clock zero as well
× finds no timer outside the three permitted modules
× the permitted rows describe exactly the timers that exist, with no slack
```

Two AC5 cases and two AC6 cases, plus the synchronicity measurement Phase 6 kept as a case. **None of the
copy, redirect or leak assertions fires**, which is exactly right: MC11 changes none of them. If AC5 were
deleted, MC11 would be caught only by the timer scan — structurally, not behaviourally — so both halves earn
their place.

## S3 · The escape mutant is caught structurally, by two independent instruments

**MC15** defers the discard by two seconds, but only for `error === 'account_state_vendor_specific'`, a code
no case drives. Driven against T29's file alone:

```
× finds no timer outside the three permitted modules
× the permitted rows describe exactly the timers that exist, with no slack
× finds no account-state-shaped or role-shaped comparison in production source
× CONTROL: the scan stays SILENT on CallbackScreen's own OAuth state comparisons
```

No runtime instrument sees it, as designed — dormant code is invisible to behaviour. It is caught by the
**timer scan** and, independently, by the **gating scan**, because the vendor code spells `account_state`.
Two instruments arriving at the same mutant from different directions is the strongest signal in this phase.

And it is the vindication of Kimi F5 over F4: a timer scan scoped to an "identity path" file list would have
caught this one only because `CallbackScreen` happened to be on the list. The whole-tree scan catches it
wherever it is put, and **MC13** — a bare `setInterval` inside `CallbackScreen` — is the companion that proves
the same point without the escape dressing.

## S4 · What the fixture-distinctness block is actually worth

Kimi F2's objection was that five identical fixtures prove nothing. The block that answers it is three cases
and it is the first block in the file. Worth being precise about what it does and does not do:

- It **does** prove the five queries, descriptions and URIs are pairwise distinct, and that each state's word
  is absent from the other four — so a leak can be *attributed*, not merely detected.
- It **does not** prove the five arrivals exercise different code paths in production. They do not. All five
  take the same branch at `CallbackScreen.tsx:101`, and that is the property under test rather than a gap.

So the honest statement of what AC1–AC3 prove is: *given five maximally different hostile inputs, the SPA
produces one indistinguishable output and retains none of the five inputs*. That is a real claim and it is
strictly stronger than the uniform-input version. It is still not R10's *"copy identical to a wrong
password"*, which is SAS's page — **T29-D1**, unchanged.

## S5 · Where the controls and the mutants disagree about what is proven

Two instruments are proven only by their controls, with no mutant that isolates them, and both deserve saying
out loud rather than being left to look like the rest:

| Instrument | Proven by | Why no mutant isolates it |
|---|---|---|
| **AC4's style pin** | two synthetic controls (a cause-keyed class, an inline style on the root) | MC07 kills it, but no production styling exists to mutate *subtly* — any class at all is caught. The pin's value is for **task 173**, when there will be styling to get wrong |
| **AC3's router-location surface** | one control, on a router parked at the callback URL | measured in Phase 6: there is no moment at which a mounted callback screen holds the query, so no mutant can leave one there |

Both are declared, not hidden. The AC11 table names them.

## S6 · Minor things I changed while reviewing, all in the test file

- `surfacesAfter` was narrowed to take the router rather than the whole `Arrival`, so the parked-router control
  needs no cast. Recorded in Phase 6.
- The AC6 allowlist case asserts the counted hits equal `PERMITTED_TIMERS` **exactly** — upper *and* lower
  bound. A row for a timer since deleted would leave budget behind and absorb a later addition silently.
- `encodings()` wraps `decodeURIComponent` in `try`/`catch`, because a malformed escape must not stop the raw
  sweep. Not reachable by any current fixture; written because a surface's text is not under this file's
  control.

## S7 · Nothing in production was changed, and nothing needed to be

`git diff --name-only` across every T29 commit names one file under `frontend/src/`: the test. The frozen brief
anticipated this and the measurement confirms it. The two things the writing found — the synchronous discard
and the `+` encoding — were facts about correct production code and a defect in my own instrument,
respectively.

## Carry-forward

Unchanged and none of it T29's: the Phase 13 template's stale `Claude Opus 4.8 (1M context)` trailer (**ten**
consecutive tasks now; the fix belongs in `.ai/generate.py`); `oidcRoundTripEndToEnd` has no suite and no
owning task; **OQ1** — the live SAS `/login` form carries no `mfaCode` field while the backend requires it in
the same POST; the composition root (`mountShell.tsx`) is unowned, which is what makes **T29-D2** necessary;
`contracts/api/token-claims.md` is wrong on `email`/`name` and silent on `amr`/`acr`.

---

**Phase 7 complete. 46 cases, 18/18 mutants killed, no production change.** Ready for Phase 8 — Kimi's
independent review.
