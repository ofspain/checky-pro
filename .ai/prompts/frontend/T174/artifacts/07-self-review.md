<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). Findings only; fixes are applied in Phase 9. -->

# frontend · T174 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` · **Task** T174 — Home screen |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` · `07-mutation-harness.py` · `07-mutation-run.txt` |
| **Harness** | eleventh copy · **17/17 regressions killed**, 1 canary killed, 1 unfalsifiable as predicted, **0 BROKEN** |
| **Suite** | 1858 cases, 1 failing — the documented environmental one |

---

## The harness

Nineteen rows, at least one per criterion AC1–AC10, plus one canary and one row predicted unfalsifiable.
Every row's full output is in `07-mutation-run.txt`.

**Two machinery changes, both of which the tenth copy deferred or caused:**

1. **`in:` now names only files holding a NEW failure.** The tenth collected files from `FAIL` lines and case
   names from `×` lines as two independent sets, so a file whose only failure was the environmental one was
   reported as a killer. A `FAIL` line carries both halves (`FAIL <file> > <suite> > <case>`), so they are
   read from the same line and stay paired.
2. **A defect I introduced while making that fix, found by reading my own output.** Taking case names from
   the `FAIL` lines *as well as* the `×` lines double-counted every kill, because vitest prints each failing
   case twice — once in the file listing, once in the summary. The first run printed every kill name twice.
   Names now come from `×` lines only; the `FAIL` line is used for pairing and nothing else. **A measuring
   instrument that prints a doubled list is wrong even when its verdict is right**, and the verdict was.

**Preflight earned its place again.** `RE04`'s first anchor was `tabIndex={-1}`, which matches twice in
`Heading.tsx` — once in the code and once in the doc comment explaining it. The run refused to start. A
mutant whose anchor matches prose is a mutant that might edit the prose and measure nothing.

---

## Findings

### F1 — The `own` / `inherited` axis is miscalibrated for this task, and I will not re-label it to hide that

**Severity:** Minor (reporting, not strength) · **Evidence:** `07-mutation-run.txt`, the five disagreements.

Five rows predicted `inherited` and came back `KILLED/own`: RE09, RE15, RE16, RE17, RE19. The harness prints
this as *"stronger than claimed"*. **It is not stronger; it is a definition colliding with itself.**

`OWN_TESTS` holds the named suite *and the five instruments phase 6 grew*, which is right — the growth is
this task's work. But a row like RE17, which reverts `SCREENS_ATTACHED_SO_FAR` to three, is killed by a case
whose **logic is task 16's** and whose **data is task 174's**. File-level attribution cannot express that, so
it reports `own`.

The expectations stay as written. T173's lesson was explicit: *an expectation rewritten to match an outcome
is how a harness stops measuring anything.* What is recorded instead is that for a task whose work is largely
**growing other tasks' instruments**, this axis answers a question nobody asked. The honest reading of the
run is: **17/17 killed, and five of those kills came through a closed task's logic reading this task's data.**

### F2 — AC10 is carried by cases that are not about AC10

**Severity:** Minor · **Evidence:** RE18 survived (predicted); RE01/RE02/RE03 each list AC10's case among
their kills.

RE18 — the focus effect running on every render instead of once — has no observable effect, exactly as
predicted, because `focus()` on the already-focused element is a no-op. So AC10's StrictMode case is never
killed by a StrictMode-specific defect; it dies alongside AC2 whenever focus breaks at all. **AC10 is
corroboration, not an instrument.** That is worth stating rather than counting as coverage: no row in this
harness can distinguish "idempotent under double invocation" from "works at all", and I could not construct
one with a single edit.

### F3 — AC8's structural assertions are measured; its behavioural claim remains unmeasured

**Severity:** Minor (known, carried) · **Evidence:** RE04 killed by both this task's AC8 case and the
narrowed `signInStarts…` selector.

RE04 is the row that mattered most for the phase-6 narrowing. When `[tabindex]` was narrowed to exclude
negative values, the risk was that the selector had been narrowed into blindness. RE04 puts
`tabindex="0"` on the heading and `signInStarts…` kills it, so the narrowing removed the false positive and
kept the true one. **OQ-T174-5 is unchanged**: no Tab keypress is measured anywhere, and the suite's own
header says so.

### F4 — `HomeScreen.tsx` is a 48-line comment on a 12-line component

**Severity:** Informational · **Evidence:** `src/app/HomeScreen.tsx`.

Noted and deliberate, but worth a reviewer's judgement rather than silence. The ratio is justified only if
the comment records decisions a reader cannot recover from the code — why the heading and not `<main>`, why
`tabIndex` is not a prop, why the screen is empty. It does. If phase 8 disagrees, the cut should be the
paragraphs that restate the code, not the ones that record the rejected alternatives.

### F5 — The end-to-end case is now load-bearing in a file that does not belong to this task

**Severity:** Minor · **Evidence:** RE01/RE02/RE03 each killed in
`loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`.

The discharged T27-D1 case is the only place the *arrival* is proved, and it lives in task 27's file. That is
the right home — it is the only suite that drives the real flow — but it means a future edit to that file can
silently remove task 174's strongest guarantee. The case's own header names task 174 and explains what it
proves, which is the cheapest available mitigation. **Recommendation for phase 9:** consider whether the
named suite should assert that this case exists by name, the way AC9 names the build gate.

### F6 — Two deferrals in this package were wrong, and nothing in the process caught them

**Severity:** Major (process, not code) · **Evidence:** phase 6 notes; `T41/09-review-resolution.md:109`.

OQ-T41-2 deferred the end-to-end landing proof to task 42. This task's **own freeze deferred it again**, as
D8, after reasoning about it rather than looking. The case had existed and been failing-by-design for eleven
tasks. Two review phases and a design challenge passed over it.

**Recommendation:** a deferral is a claim about what cannot be measured yet, and it should carry the same
evidence any other claim does — at minimum a grep for the thing being deferred. This is a `.ai/` process
observation, not a T174 code change, and it belongs in phase 13's notes for the owner.

---

## What the harness does NOT measure

- **A real Tab keypress** (OQ-T174-5) — no dependency supports it; AC8 is structural.
- **StrictMode-specific idempotence** (F2) — no single-edit mutant isolates it.
- **Screen-reader announcement.** Every AC2 case asserts `document.activeElement`. That focus lands on an
  `<h1>` carrying the screen's name is the best proxy available in jsdom; what a screen reader actually says
  is not measurable here and is not claimed anywhere.
- **The copy being good.** AC3 asserts the DOM matches the catalogue, which is a consistency claim, not a
  quality one. The words are the owner's to change (D7).
