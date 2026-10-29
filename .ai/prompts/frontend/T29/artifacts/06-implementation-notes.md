# frontend · T29 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

One file, `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` — 1133 lines,
**46 cases**, 29 of them controls, twelve `describe` blocks. **No production code changed**, confirmed by
`git diff --name-only`.

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean |
| The file alone | **46 / 46**, exit 0 |
| Whole suite | **1388 passed / 1 failed (1389)** — the one is `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, pre-existing and environmental |

---

## What the writing found that the plan did not

### 1 · There is no moment at which a mounted callback screen holds the state words in its location

The plan's AC3 had a control that mounted the route **without** flushing, on the assumption that the router
would still be at the callback URL with the discard not yet run. **It failed, with the needle absent.**

Measured: `CallbackScreen`'s error branch is synchronous — `params.get('error')` is read at `:86`, the guard
at `:101-104` calls `discard()`, and `discard` navigates at `:98`, with nothing awaited anywhere between. So
the effect, the discard and the navigation all complete inside the `act` that renders, and `mountRoute`
returns with the router already at `/app/sign-in` and an empty `search`.

That is good news about the production code and bad news about the instrument: the only way to show the
router-location surface is live is to **park a router at the callback URL and never render it**. The control
does that now, and the measurement is kept as a case of its own, because it is a fact about the code rather
than about the test:

> *"the discard has already navigated by the time the render returns, so the location never holds the query"*

### 2 · `URLSearchParams` encodes a space as `+`, and the leak sweep would have missed it

The reworked control then failed a second time, and this one was a **real hole in the instrument rather than
a wrong guess in the case**. The assertion predicted `%20`; the measurement said:

```
"search":"?error=unauthorized_client&error_description=This+account+has+been+suspended+by+an+administrator&…"
```

The needle is `This account has been suspended by an administrator`, with spaces. `leaks()` compared raw text
to raw needle, so **the sweep would have reported CLEAN on a description that survived in a URL** — which is
the single most likely shape for this leak to take, because a URL is where the description arrives in the
first place. Every other surface in the sweep holds plain text, which is why nothing earlier in the package
had met this.

Fixed with an `encodings()` helper: every surface is searched in its raw form **and** in
`decodeURIComponent(text.replace(/\+/g, ' '))`, guarded, since a malformed escape must not stop the raw sweep.
The control now runs through `leaks()` rather than reading the surface text, so it exercises the normalisation
it discovered, and it asserts both directions:

```ts
expect(raw).toContain('suspended+by+an+administrator');
expect(raw).not.toContain('suspended by an administrator');
```

A thirteenth row was added to AC11's instrument table for it.

### 3 · The fake-clock hazard was real and was avoided by having measured it first

Phase 5's M1 held exactly as measured. `arriveUnderFakeClock` installs the clock **before** the render and
flushes microtasks only; `turn()` is never called under it. Had AC5 reused `arriveAt`, the five cases would
have hung until vitest's timeout rather than failed — the worst shape a timing test can take, because a hang
reads as an environment problem rather than a defect.

`vi.useRealTimers()` is in `afterEach`, placed **after** the unmount block: the unmount runs inside `act` and
may schedule work, and restoring the clock first would hand that work a real timer mid-teardown.

## Structure

| Block | Cases | AC |
|---|---|---|
| the five fixtures are genuinely five | 3 | AC2's precondition |
| every account state ends in the one discard closure | 3 | AC1 |
| five account states, one outcome | 3 | AC2 |
| the state, its description and its URI do not survive | 6 | AC3 |
| no failure screen carries a class or an inline style | 3 | AC4 |
| the sentence is rendered with the clock at zero | 2 | AC5 |
| the clock-at-zero assertion can fail | 1 | AC5 · F9 |
| no production module holds an unexplained timer | 4 | AC6 |
| no production module names an account state | 5 | AC7 |
| no user-facing string names an account state | 8 | AC8 |
| nothing gates a failed sign-in on a state or a role | 3 | AC9 |
| two failure sentences, exactly six `signIn` keys | 3 | AC10 |
| every instrument shown to report dirty | 2 | AC11 |

**The distinctness block comes first, deliberately.** Everything after it compares five arrivals and asserts
they are indistinguishable; five fixtures that were secretly one fixture would satisfy all of it while proving
nothing. That is Kimi F2's objection, and the ordering is the answer to it: the fixtures are shown to be five,
with five distinct descriptions, five distinct URIs and five distinct queries, and each state's word is shown
to be **absent from the other four** — so a leak can be attributed, not merely detected.

## Decisions worth recording

- **`surfacesAfter` takes the router, not the whole arrival.** Changed while reworking the control in §1: the
  only parameter it used from `Arrival` was `.router`, and a control that needs to park a router without
  rendering cannot build an `Arrival`. Narrowing the parameter was cheaper and more honest than a cast.
- **The timer allowlist is an upper *and* lower bound.** One case asserts the counted hits equal
  `PERMITTED_TIMERS` exactly. A row for a timer that has since been deleted would leave budget behind, and a
  later addition in that file would be absorbed silently — which is how an allowlist stops being evidence.
- **The source scan uses only the globally-forbidden tier.** The sign-in-scoped words (`expired`, `revoke`,
  `invalid`, `denied`, `blocked`, …) are legitimate future *identifiers* as well as copy, so scanning source
  for them would fail on `isExpired` or `revokeSession` the day either lands.
- **`aria-disabled` is excluded by never putting `disabled` in the pattern at all**, rather than by a negative
  lookbehind. L11 requires the attribute, `SignInScreen.tsx:61` uses it, and a lookbehind would have been a
  cleverness nobody reading the pattern later would trust.
- **AC4's walker includes the root node itself.** A control proves it, because a `style` on the container
  would otherwise be invisible to a `querySelectorAll('*')` sweep.

## What this file does not do

Carried forward from the frozen brief, unchanged: it cannot read SAS's own `/login` page (**T29-D1**,
`oidcRoundTripEndToEnd` has no suite); it says nothing about the three token states (**T29-D2**, task 59 owns
them and the composition root mounts neither `sessionBoundary` nor `rateLimitNotice`); its visual-treatment
claim is a pin for task 173 (**T29-D3**). The vocabulary is enumerative, which is why every tier has a control
and why T27's and T28's patterns had to be diffed against each other to find the `two-factor`/`multi-factor`
hole.

---

**Phase 6 complete.** Ready for Phase 7 — self-review and mutation score.
