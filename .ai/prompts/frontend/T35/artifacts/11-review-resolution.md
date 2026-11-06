<!-- MODEL: Claude Opus 5 — Phase 11 resolution (the review itself is Kimi's 11-test-review.md) -->

# frontend · T35 · Phase 11 — Resolution of the Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | `artifacts/11-test-review.md` (Kimi 2.7) |
| **Produces** | this resolution |

Thirteen findings. **Ten into code, one held as recorded debt, two informational.** Two of my own answers
were wrong on the first attempt and the measurement said so — both are recorded below rather than quietly
corrected.

## F2 — the finding that mattered, and one of its two reasons is wrong

F2 objected that AC9's bounded poll is non-deterministic, for two reasons:

- *"on a fast runner the loop may exit early and still assert the hazard exists"* — **this one does not hold.**
  Exiting early means the token is non-`null`, which **is** the hazard. Early exit is the correct outcome.
- *"on a slow runner the bound may not be enough"* — **this one is the whole point**, and it is worse than a
  flake. The poll exhausts, the token is `null`, and AC9's assertion announces *"GOOD NEWS, the hazard is
  fixed"* when all that happened was a slow machine. A false repair report on a known defect.

**Fixed by separating the two events rather than widening the window.** A third endpoint on the same channel
observes the message, and `delivered` is awaited with **no bound** — so a delivery that never happens fails
as a Vitest timeout, which reads as a channel problem and not as a repair. Only once delivery is known to
have happened is adoption polled, and the remaining lag is one listener's turn.

## F9 — my implementation of it was false, and the measurement said so

F9 asked for a pin on sign-out's monopoly. I wrote it as *"no module other than `signOut.ts` may be
permitted to call more than one clearing primitive"*, and it failed immediately: **`CallbackScreen.tsx`
legitimately holds three** — it clears the token on a discard, consumes the authorize state, and takes the
return target, because that is the callback's own job. Two of those are in sign-out's set.

So the claim has to be about the **set**, not a count: no other module may hold all four primitives sign-out
uses. *A rule that would have forbidden the callback is a rule about nothing.*

## F3 — real, and it cost two exclusions rather than nothing

Confirmed: the scan reached `let`, `var`, `new Map()`/`new Set()` and array literals, but not
`const state = { count: 0 }`. Measured before answering, and unlike phase 9's two findings **this shape does
exist in production** — `readAccount.ts:74` and `readProfile.ts:61` each hold a `FAILURE_MESSAGE` record
that is not `as const`.

Extended anyway, with those two excused **individually and on their merits** (a message table keyed by a
closed union, never assigned to at runtime) and their declaration counts pinned. Two entries rather than one
shape-matching pattern, because an exclusion that matches by shape would excuse the next real piece of state
that happens to look like a lookup table. **SO20** is the planted object literal; the control now exercises
all five shapes the pattern has, because a control for one branch of three is a control for a third of a scan.

## F8 — a real hole, and the mutant proves it was

`sessionStorage` and `localStorage` **share `Storage.prototype`**, which is what makes `watchStorage`
alias-proof and also what made AC6 blind: a removal aimed at the wrong store matched the expected key list
and passed. The receiver of every removal is now asserted, and `localStorage.length` is checked from its own
side. **SO21** aims `clearReturnTarget` at `localStorage` and is killed.

## F6 — the third storage shape, and the claim narrowed to what is measured

AC12 tested a store that **refuses everything** and one that is **absent**. Added: one that refuses only
`removeItem`, which is the only shape with an ordering consequence — the first refused removal must not
abandon the other three, and it does not because the guard is inside `consumeAuthorizeState`'s loop.
**SO24** hoists that guard out and is killed.

`QuotaExceededError` is deliberately not a case: quota applies to writes and sign-out never writes. The
frozen criterion's "any storage condition" is narrowed in phase 12's matrix to the three actually
covered — absent, refusing everything, refusing removals — because naming them is honest and "any" was not.

## Applied as recommended

| # | Severity | What changed |
|---|---|---|
| **F4** | Minor/Major | `signOut.ts` is scanned for a React import and for JSX. The file is `.tsx` for task 36's sake, which is not a licence to put a screen in the clearing half, and nothing noticed if it did. **SO22**. |
| **F7** | Minor | One case walks the inventory and asserts each key is really gone, so AC8 finding keys and AC3/AC6 proving the clearing no longer leaves the join unheld. **SO23**. |
| **F10** | Minor | The harness notes that SO17 is the fragile half of a pair: its anchor is coupled to `cleanReauth.ts`, while AC10's stale-caller case works from the allowlist alone. |
| **F11** | Minor | `signOut.ts` now names the two things it does **not** do: it does not abort in-flight requests (`sessionBoundary.ts` reads `current()` per request, so only one already on the wire still carries a token), and it does not clear the boundary's pending-renewal map, which lives in a factory closure a zero-argument function cannot reach. Both belong with whoever wires the boundary up, and neither is a thing a parameter would fix, because the parameter would have to be the boundary itself. |
| **F12** | Minor | AC9b pins the **instruction** as well as the addressee: a message that names task 39 but no longer says what to do leaves its reader knowing they are responsible and not what for. |

## Held

- **F1** (Blocker, no wiring test) — uncloseable here. **OQ-T35-2**, sixth task. Recorded in the module, the
  suite header, phase 7 S3#1 and phase 12's matrix; AC10 is the available substitute and is not claimed as
  more.
- **F5** (duplication debt) — the `strip` helper is the fourth copy and the file is now 870 lines.
  **OQ-T32-4**, raised by eight reviews across three tasks. Extracting it is additive and still not T35's.
- **F13** — informational.

## What the preflight caught, which is worth recording

SO21's first anchor was the bare removal line, and the harness **refused to run**: that string is a
*substring* of `takeReturnTarget`'s more deeply indented copy, and `text.count` counts substrings, so it
matched twice. Re-anchored through the function signature. This is precisely the failure the preflight exists
for — the mutant would have printed `BROKEN`, and a run that ends `survivors: none` with a broken row in it
overstates the score by exactly that many.

## Gates

| Gate | Result |
|---|---|
| T35 cases | **38 passed**, exit 0 — three runs, no flake |
| whole suite | **1616 passed / 1 failed of 1617** — the known `oxlint` host case |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| mutation | **23/23 regressions killed · 1/1 canary · 0 survivors · 0 broken** |

Coarse kills verified narrowly rather than trusted: **SO12** (306 failures) → exactly AC2's two cases ·
**SO23** (58) → exactly AC8's one · **SO17** (9) → exactly AC10's one · **SO21** (9) → exactly AC6's one ·
**SO24** (7) → exactly AC12's three · **SO18** (2) → exactly AC1's one.

**Phase 11 complete.**
