<!-- MODEL: Claude Opus 5 — Phase 7 (Self Review). -->

# frontend · T46 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T46 — Code points, not UTF-16 |
| **Consumes** | `artifacts/06-implementation-notes.md`, `artifacts/07-mutation-harness.py` |
| **Produces** | `artifacts/07-self-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/passwordLengthCountsCodePoints.test.ts` | **25 / 25 passed** |
| `vitest run` | **1989 / 1990** — the known environmental `lintRejectsHandWrittenBackendFetch` |
| `tsc --noEmit` · `oxlint src` | **0** · **0** |
| harness, run 1 (`07-mutation-run.txt`) | **49/49 regressions killed**, 4 canaries killed, 2 expected survivors (TR24, TR50), **0 broken**, **44 disagreements** |
| harness, run 2 (`07-mutation-run-2.txt`) | **49/49**, 4 canaries, 2 expected survivors, **0 broken**, **2 disagreements** |

**Verdicts are identical between the two runs, row for row** (`diff` over verdict, class and row id: empty).
Both ran with the local Docker stack stopped. Everything below about the disagreements is about
**predictions**, which are metadata: no mutant behaved differently in the two runs.

---

## Findings

### F1 — Forty-two inherited rows arrived with predictions from a task that owned four files
· **Severity:** **Major**
· **Evidence:** `07-mutation-run.txt`, final line: 44 disagreements, every one reading *"killed only OUTSIDE
  this task's files"*. `07-mutation-harness.py:81` sets `OWN_TESTS` to the single file this task wrote; the
  copy it came from listed three.
· **Assessment:** The rows were right and the harness was right; the predictions were stale. `own` means
  *killed with a named failure in a file this task owns or grew*, so a row that kills in `RegisterScreen`'s
  suite was honestly `own` for task 45 and is honestly `inherited` for task 46. Copying a harness copies its
  rows, its anchors **and its predictions**, and the third of those is the only part that is task-relative.
  This is T45's TR21 at scale — I re-aimed one row there for exactly this reason and then inherited 48
  wholesale.
· **Recommendation:** Done, and re-derived rather than fitted to the output: a row is `own` for this task iff
  it mutates `passwordLength.ts` or this suite, because those are the only things the named suite asserts.
  For the next copy of this machinery: when `OWN_TESTS` changes, **every** inherited prediction is stale
  until re-derived, and the disagreement list is the instrument that says so.

### F2 — My fix and my verification shared one bug, so the check confirmed the breakage
· **Severity:** **Major**
· **Evidence:** Run 2 still reported `TR25 expected own` and `TR34 expected own` after a run in which my
  verification script printed *"mismatched against the rule: none"*. Both scripts used
  `, "(own|inherited|unfalsifiable)"\),` — which requires the prediction to sit on the same line as the
  preceding comma. `07-mutation-harness.py`'s TR25 and TR34 carry it on a line of its own, so the search ran
  past the row and read **the next row's** prediction.
· **Assessment:** The fixer skipped two rows and the checker agreed, because they were the same instrument
  asked twice. The only reason this surfaced is that the harness computes its own expectation table from the
  file — an independent reader of the same data. This is the package's recurring lesson in a new costume: a
  control that reuses the mechanism under suspicion measures the mechanism's opinion of itself.
· **Recommendation:** Done. Both the fix and the verification now parse the `MUTANTS` list with `ast`,
  reading the structure the harness reads instead of matching text. 55 rows, no mismatches. A third run
  would show an empty disagreement list; the two runs' verdicts already imply it, and I would rather say
  that than claim a run I did not make.

### F3 — The harness's revert is not crash-safe, and a leftover mutant that breaks nothing would be invisible
· **Severity:** **Major**
· **Evidence:** The first attempt at run 2 was killed by the host (background shell reaped under memory
  pressure, mid-row). It left `frontend/src/features/account/passwordLength.ts` holding TR55's edit —
  `export const ADVISORY_MINIMUM_CODE_POINTS = 11;` — in the working tree. `git status` showed it
  immediately and `git checkout --` restored it; the named suite then passed 25/25.
· **Assessment:** The harness mutates, runs, then reverts; a kill between the first two steps leaves
  production source modified. The baseline guard would catch *this* leftover on the next run, because a
  wrong bound makes tests fail — but **only because it fails**. A leftover from a row like TR24 or TR50,
  which by construction change no observable behaviour, would leave the tree dirty, pass the baseline guard
  and be committed by the next `git add` of that path. The thing that actually protected the repository here
  was the standing rule to commit before mutating, which made the diff legible in one command.
· **Recommendation:** For a future task that owns this machinery (not this one — it is 16 copies deep and
  OQ-T32-4 already tracks that): wrap mutate/run/revert in `try/finally`, and add a startup guard that
  refuses to run when `git status --porcelain` over `REVERT_PATHS` is non-empty. The second is the cheaper
  and catches the invisible case.

### F4 — The AC6 control's threshold is met exactly, which makes it tight by accident
· **Severity:** **Minor**
· **Evidence:** `passwordLengthCountsCodePoints.test.ts`, the environment-scan control, asserts
  `fired.length` is `toBeGreaterThanOrEqual(5)`. The synthetic impure module triggers exactly five of the
  twelve patterns (`^import`, `sessionStorage`, `document`, `Date`, `console.`); the other seven cannot match
  it.
· **Assessment:** The control passes with no margin, so it is sensitive today — but for the wrong reason.
  A threshold says *"enough patterns work"* when the claim is *"each kind of reach is visible"*. If someone
  added a sixth matching line, the threshold would stop being able to detect one broken pattern.
· **Recommendation:** Replace the count with the **set**: assert the fired patterns include one storage one,
  one clock one, one console one, one DOM one and the import one, by name. Same cost, and it keeps measuring
  what it claims after the next edit.

### F5 — A comment claims a performance property the case does not measure
· **Severity:** **Minor**
· **Evidence:** The totality case asserts `countCodePoints('a'.repeat(100_000))` is `100_000`, under a
  comment reading *"A LONG VALUE COSTS ONE PASS."*
· **Assessment:** Nothing in the case measures a pass count or a duration, and a timing assertion here would
  be flaky on a shared runner. The assertion is worth keeping — it proves the function answers at that size
  rather than hanging or overflowing — but the comment states a stronger claim than the code holds, which is
  the defect T45's phase 12 caught twice in production prose.
· **Recommendation:** Reword to what is measured: a 100,000-character value returns the right number, which
  is the part that matters for an advisory check that must not become a gate. Drop the complexity claim or
  attribute it to the implementation rather than to the test.

### F6 — TR05's attribution is the task's central claim, and it moved
· **Severity:** **Positive**
· **Evidence:** `07-mutation-run-2.txt`, TR05: killed with **21 named failures across three files**, the
  first of which is `src/features/account/passwordLengthCountsCodePoints.test.ts` — eleven of the named
  cases are this suite's.
· **Assessment:** This is what T46 was for, and it is visible in one line of output. The mutant is task 43's,
  three tasks old; until now every case that killed it was a claim about **what a visitor is told**. The unit
  is now also a property of the function. TR05's comment records the new obligation, so a future task that
  sees a kill there *without* this file named knows this suite has gone inert.
· **Recommendation:** None.

### F7 — TR50 survived both runs, which is the point of including it
· **Severity:** **Positive**
· **Evidence:** `Array.from(value).length` — `SURVIVED`, both runs, 1 failed of 1990 (the baseline).
· **Assessment:** A surviving row is usually a hole. This one is the proof that the suite pins **the unit R1
  names** and not **the syntax of today's implementation**: `Array.from` iterates code points exactly as the
  spread does, so a suite that killed it would obstruct the next person who touches the file for a good
  reason. It is the only row in sixteen copies of this harness whose survival is an argument for the suite.
· **Recommendation:** None.

### F8 — TR52 makes OQ-T46-2 falsifiable, and dies in exactly one case
· **Severity:** **Positive**
· **Evidence:** TR52 (`[...value.normalize()].length`) — killed, 2 failed of 1990, one named case:
  `counts a decomposed accent as code points, not UTF-16 units`.
· **Assessment:** The open question — whether R1 means to be normalisation-sensitive — is recorded in a
  comment *and* held by a test, which is the difference between a question and a note. One row, one case, and
  the owner's answer either way now changes a measurable thing.
· **Recommendation:** None. The question itself stays open for the owner.

### F9 — The table control is coupled to the constants, deliberately
· **Severity:** **Minor / observation**
· **Evidence:** `CONTROL: the table can tell the two units apart` is among the named killers of **TR29**
  (maximum widened to 999) and **TR55** (minimum becomes 11), because its crossing clauses are written
  against `ADVISORY_MINIMUM_CODE_POINTS` and `ADVISORY_MAXIMUM_CODE_POINTS` rather than against 12 and 128.
· **Assessment:** This is intended and worth stating so a later reader does not "fix" it. The control claims
  the table straddles **the bounds R1 actually has**; if a bound moves, the table must gain a row that
  discriminates at the new bound, and until it does the control fails. The alternative — literal 12 and 128 —
  would let a bound move with the table left behind, which is the drift this package has spent three tasks
  closing at the copy level.
· **Recommendation:** None, beyond one sentence in the comment making the coupling explicit.

### F10 — `typeof countCodePoints === 'function'` has no mutant
· **Severity:** **Minor**
· **Evidence:** The last line of the export-shape case.
· **Assessment:** Unfalsified, and the only mutant that could reach it (exporting the name as a non-function)
  would not type-check. It is a cheap readability assertion rather than an instrument; the honest thing is to
  know which it is.
· **Recommendation:** Keep, and say in the comment that the type system holds this one — the same disposition
  T45 gave its email-field exemption.

---

## Summary

The suite does what the freeze asked and the harness says so: **49/49 regressions killed in both runs, 0
broken, verdicts identical row for row**, with the two survivors both predicted and both meaningful. The
central measurement is **F6**: the mutant task 43 wrote now dies in the module's own suite, eleven named
cases of it, so R1's counting unit is finally a claim about the function rather than only about what a
visitor is told. **F7** and **F8** are the two rows that make the suite honest rather than merely green.

The three findings worth phase 9's time are all about the **machinery**, not the subject: forty-two stale
predictions inherited with the harness (**F1**), a fix and a check that shared one blind spot so the check
confirmed the bug (**F2**), and a revert that is not crash-safe — proven by an actual crash that left R1's
lower bound off by one in the working tree (**F3**). The rest are small and mine: a control whose threshold
is met exactly (**F4**), a comment claiming a performance property nothing measures (**F5**), and one
assertion the type system holds (**F10**).

**Phase 7 complete.** Ten findings returned; no production code changed, and the tree is clean.
