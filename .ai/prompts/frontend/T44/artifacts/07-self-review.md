<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review). Findings only; fixes are phase 9's. -->

# frontend · T44 · Phase 7 — Self-Review

| | |
|---|---|
| **Consumes** | `artifacts/06-implementation-notes.md`, the frozen brief |
| **Harness** | `artifacts/07-mutation-harness.py` (34 rows) · output in `artifacts/07-mutation-run.txt` |
| **Result** | **31/31 regressions killed · 0 broken · 2 canaries killed · 2 disagreements** |

## The measurement this task existed to make

**TR32 (`maxLength={128}` on `PasswordField`) was killed by exactly the two pins phase 6 added, and by
nothing else:**

```
KILLED  own  TR32 …caps its own length at 128 UTF-16 units…  (3 failed of 1945, exit 1)
  └ in: ['…screensRenderThroughDesignSystemPrimitivesOnly.test.tsx',
         '…registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx']
  └ T44 AC4 — neither field caps its own length, which would count the wrong unit and truncate
  └ gives neither field a length cap of its own, because the attribute counts the wrong unit (T44)
```

Three failures of 1945 is the one environmental failure plus those two cases. **Not one behavioural case
saw it** — which is what the freeze predicted from a direct jsdom measurement and what phase 3's F4 denied.
F4 said *"the table's body-equality assertion would catch this"*; 45 cases in that file, every one of them
typing a value into the field, and none of them noticed a cap that would halve a real visitor's password.
Both pins are load-bearing, and neither is vacuous.

The other upper-bound rows isolate as designed: **TR30** (the `<=` half deleted) dies by **one** case, the
129 row; **TR33** (`password.length` in the upper comparison only) dies by exactly the astral row and the
AC2 DOM case, with every task 43 row still passing; **TR31** (`<` for `<=`) dies by the two rows that sit
*on* 128 plus the DOM case; **TR29** (the maximum widened to 999) dies by the 129 row, the constant
assertion and the extended control.

---

## Findings

### F1 — TR24's "kill" was a flake, and the flake inverted a verdict
· **Severity: Major** · **Evidence:** `07-mutation-run.txt:235-238` — `KILLED inherited TR24 … in:
['src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx']`, naming *"re-arms
the attempt itself: a second click after a failure succeeds"* and *"writes exactly the three items T15
writes, and calls no console method"*.

Neither case has anything to do with `noValidate`, and jsdom performs no interactive constraint validation
at all. I re-ran the row alone: **`SURVIVED … as expected`, 1 failed of 1945.** Three clean full-suite runs
in a row also show 1944/1945 with no sign of those two cases.

This is **FU-T42-1's third sighting** — the same file and the same pair of cases that added +2 to T43's
TR18 in two separate runs. What is new is the consequence: until now the flake added noise to a row that
was killed anyway. Here it **flipped the suite's one honest `unfalsifiable` row into "DISAGREES — it has an
observable effect"**, which is a claim about jsdom that is false.

· **Recommendation:** phase 9 re-runs the harness and records both runs. More durable: a disagreement is a
**hypothesis**, not a result — re-run the row alone before believing it. The harness cannot do this for
itself, because attribution subtracts case names that fail *at baseline*, and a flaky case passes there.

### F2 — TR21's disagreement is my own prediction, not a hole
· **Severity: Minor** · **Evidence:** `07-mutation-run.txt:221-223` — TR21 killed by
`homeRouteRendersLandingScreenAndManagesFocus.test.tsx`, reported `inherited` against an expectation of
`own`.
· **Assessment:** The outcome is right and the prediction is wrong. T43's `OWN_TESTS` held eight files
because T43 grew six instruments; T44 grew two, so that focus guard is **not** this task's file. The row
copied T43's expectation along with its text.
· **Recommendation:** re-aim TR21 to `inherited` with the reason in its comment — never by relabelling the
outcome.

### F3 — The source scan's comment claims coverage the scan does not have
· **Severity: Major** · **Evidence:** `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` (phase 6
diff) — the comment says the scan *"stops the attribute being added to a primitive no screen in that suite
renders, **or to a field a later task adds**"*, while the loop hardcodes
`['app/ui/EmailField.tsx', 'app/ui/PasswordField.tsx']`.
· **Assessment:** A `TextAreaField` or a `SearchField` added by task 47 would be invisible to it. The
comment is false in exactly the direction that matters — it tells the next reader the hazard is handled.
· **Recommendation:** scan `PRIMITIVE_FILES` (the list the file already pins by size) rather than two
names, so a new primitive is covered the day it is added; the unit argument applies to every field. If that
is declined, the comment must be cut back to what the loop does.

### F4 — Three of AC4's six assertions have no mutant aimed at them
· **Severity: Major** · **Evidence:** TR32 adds `maxLength` to `PasswordField` only. The two pins also
assert `EmailField` has no `maxLength`, and that **neither** field has `minLength` — four assertions no row
in the harness can falsify.
· **Assessment:** This is the shape of the hole T42's AC11 had: a negative assertion with no positive proof
is vacuous until something makes it fail. Two of the six are now proven live by TR32; the other four are
asserted on faith.
· **Recommendation:** one more row — `minLength={12}` on `EmailField` — covers the other file *and* the
other attribute in a single mutant, and is a realistic mistake (the lower bound expressed in the wrong unit
on the wrong field).

### F5 — `/maxLength/i` is a substring match
· **Severity: Minor** · **Evidence:** the same loop — `.not.toMatch(/maxLength/i)`.
· **Assessment:** A field named `maxLengthHint` would fail the scan. The direction is the safe one (a false
positive fails loudly rather than passing quietly), but this package has been bitten twice by substring
matching in the other direction, and the asymmetry is worth stating rather than rediscovering.
· **Recommendation:** optional. If it is tightened, `\bmaxLength\s*=` is the shape that matches an attribute
and not a word; if it is left alone, say in the comment that the breadth is deliberate.

### F6 — The `T44 AC4` case sits under a `describe` numbered by T43's ACs
· **Severity: Minor** · **Evidence:** `describe('AC2/AC3/AC4 the advisory range marks and never blocks')`
now contains `it('T44 AC4 — …')`, so the reporter prints `AC2/AC3/AC4 > T44 AC4`.
· **Assessment:** The prefix was the plan's deliberate answer to two tasks sharing one file's numbering, and
it works, but the collision of the literal string `AC4` on both sides of the `>` is the least readable spot
in the diff.
· **Recommendation:** none required; if phase 9 touches it, naming the claim rather than the number reads
better than either numbering.

## The standing checklist

| | |
|---|---|
| **Correctness** | The clause under test was already correct; this task made it falsifiable. Five rows now fail if it changes. |
| **Boundary conditions** | Both ends of the range are now pinned ON the bound and one past it: 11/12 and 128/129, plus the astral boundary at each end. |
| **Null-safety** | `countCodePoints` is total and unchanged; the empty-string row still passes. |
| **Thread-safety / transactions / money types** | Not applicable — browser, no persistence, no money. |
| **Module boundaries** | No production file touched; nothing imported that was not already imported. |
| **Idempotency** | Unchanged: submit is not guarded against double invocation, which remains OQ-T43-11 and out of scope. |
| **Enumeration-safety / secrets** | Test passwords are `'a'.repeat(n)` and `'👍'.repeat(n)`; no realistic secret enters the tree; no branch distinguishes a long password from a short one. |
| **Readability / complexity** | Three rows and two cases; no new helper, no new file, no restructuring — L12 satisfied as the freeze required. |
