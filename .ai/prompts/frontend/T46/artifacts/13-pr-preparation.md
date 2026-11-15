<!-- MODEL: Claude Opus 5 — Phase 13 (PR / Commit Preparation) -->

# frontend · T46 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T46 — Code points, not UTF-16 |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` — verdict **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12's verdict is **PASS**, so this task is ready to merge.

---

## Commit title

```
test(frontend): count password length in code points, not UTF-16 units (T46, R1)
```

## Commit message

```
test(frontend): count password length in code points, not UTF-16 units (T46, R1)

Hold R1's counting unit in the module where the count lives.

R1 bounds a password at 12 to 128 Unicode code points. Tasks 43 to 45
each built a screen on `countCodePoints` and each said in as many words
that the function's own correctness belonged to task 46; this is that
suite. It changes no production code - `passwordLength.ts` is its
subject, not its output.

Pin the unit on both sides. A string's three lengths part company in
opposite directions at the two bounds: eleven astral code points are
twenty-two UTF-16 units, so counting units would ACCEPT a password R1
excludes and say nothing, and 128 astral code points are 256 units, so
counting units would REJECT one R1 allows. Pin the other neighbour too -
a flag is two code points, a skin-toned emoji two, a family emoji five,
and all three are one thing on screen, which is why task 45's copy says
"characters" and accepts the imprecision deliberately.

Measure the order of the three answers with something outside the file.
Asserting 1, 5 and 8 as literals leaves unsaid which of them R1 names;
`Intl.Segmenter` is in this runtime, is used nowhere in src, and is the
one number here that nobody in this repository chose, so the case
asserts graphemes < code points < UTF-16 units and controls that the
witness agrees with both other units on strings with no clusters.

State the law the table samples: k astral code points plus m ASCII is
k + m code points and 2k + m units, over thirty-six generated strings,
both numbers arithmetic rather than measured.

Prove the table is alive, not merely long. Every expected count is a
literal, because the implementation is `[...value].length` and an
expectation written the same way asserts that a function equals itself.
A source scan forbids that shape in the table region; four clauses
require that some row discriminates the units, that some row crosses
each bound between them, and that no two rows hold the same string.

Assert purity at the source, because a negative behavioural assertion
about an absent side effect is how task 42 shipped a vacuous claim that
survived seven tasks. Twelve patterns in seven categories must all fire
on a synthetic impure module - per pattern, not per category, because a
category check cannot see one broken pattern with a working sibling.

Files changed:
  frontend/src/features/account/passwordLengthCountsCodePoints.test.ts

Testing:
  named suite        27/27
  whole suite        1991/1992 (one known environmental lint-script
                     failure, unrelated and pre-existing)
  tsc --noEmit       0
  oxlint src         0
  mutation harness   61 rows, 54/54 regressions killed, 4 canaries
                     killed, TR24 and TR50 the expected equivalent
                     survivors, 0 broken, 0 disagreements

Spec: spec/frontend/tasks.md task 46; requirements.md R1; design.md
      §4a L3 (asserted more strictly than L3 requires - the module
      reaches for no storage at all). L6 and L12 not engaged beyond
      L12 being the reason no production file changed.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

**On the trailer.** The phase 13 prompt template asks for
`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. That is stale — it names a model
that is not the one running this pipeline, and the session's own attribution rule says
`Claude Opus 5`. The trailer above is the correct one; the template is a standing follow-up for the
owner, carried since T36, and regenerating `.ai/` is the fix rather than editing it by hand.

## Files changed

| File | Change | Lines |
|---|---|---|
| `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts` | **new** — the named suite | +368 |

**No production file was changed by this task.** `frontend/src/features/account/passwordLength.ts` is the
read-only subject, written by task 43; the brief and phase 3's F12 both forbid touching it, and phase 12
confirms it is untouched. The package's first `.test.ts` under `features/` and its first suite with nothing
to arrange — phase 3's F1, accepted at the freeze, because `countCodePoints` needs no DOM.

Pipeline artifacts under `.ai/prompts/frontend/T46/artifacts/` are not part of the product diff.

## Summary

T46 makes R1's counting unit falsifiable in the module that owns it, rather than only in the three screens
that read it. 27 cases over seven acceptance criteria: a 15-row table whose every expected count is a
Unicode fact measured on this host, both bound-crossings where `.length` and code points disagree in
opposite directions, the grapheme neighbour on the far side of a code point, an ordering measured against
`Intl.Segmenter`, a 36-string arithmetic property, totality including lone surrogates and a
100 000-character value, non-additivity across a split surrogate pair, a source-level purity scan with a
twelve-pattern control, and the module's export shape.

Three of those are controls on the suite itself rather than on the subject, and that is deliberate: a
circularity guard (the expectations cannot be computed by the function under test), a table-liveness
control (the rows must be able to tell the two units apart and must not repeat), and a scan-liveness
control (every pattern must be shown firing). Each exists because this package has shipped the
corresponding vacuous claim at least once.

## Testing performed

| | |
|---|---|
| `vitest run src/features/account/passwordLengthCountsCodePoints.test.ts` | **27 / 27** |
| `vitest run` | **1991 / 1992** — the single failure is `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, a known environmental failure on this host (its `oxlint@1.87.0` prints no summary line). Pre-existing, unrelated, and the harness's baseline guard refuses to measure if anything else fails. |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `07-mutation-harness.py` | **61 rows** — 54/54 regressions killed, 4 canaries killed, TR24 and TR50 the expected equivalent survivors, **0 broken**, **0 disagreements**. Six full runs across phases 7–12 (one of them Kimi's), with the local Docker stack stopped per FU-T42-1, on a committed tree. |

The rows this task added and what each measures: **TR05** (task 43's `[...value]` → `value`) now dies in
21 named cases across three files with this suite named first — the task's central evidence. **TR50**
(`Array.from`) must survive and does, which is the proof the suite pins the unit and not the syntax.
**TR52** (normalise before counting) makes OQ-T46-2 falsifiable. **TR57** shows a grapheme implementation
is caught; **TR58** shows the segmenter case is what catches it once every other expectation has been
rewritten to agree. **TR59** catches a duplicated table row. **TR60** catches a blunted scan pattern whose
category keeps firing, and **TR61** the case where a whole category goes silent.

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` task **46** — *"Code points, not UTF-16."* |
| **Requirement** | **R1** (`requirements.md:9-11`) — 12 to 128 Unicode code points, advisory only, the server is the authority |
| **Named test** | `passwordLengthCountsCodePoints` (`package.md` §8) |
| **LOCKED** | **L3** (tokens in memory only) — AC6's scan is stricter than L3 needs. **L6** (generated client only) and **L7** not engaged. **L12** (do not rewrite an earlier task's work) is why no production file changed. |
| **Contracts** | Not engaged — nothing in this task calls a backend. `contracts/api/auth.yaml` remains the reason R1 is advisory: `password` is a bare string with no bounds. |

## Open questions for the owner

| | |
|---|---|
| **OQ-T46-2** | Is R1 meant to be normalisation-sensitive? A composed `é` counts 1 and a decomposed one 2, so the same password typed on two keyboards has two lengths. Held by a table row and by TR52. |
| **OQ-T46-3** | `Intl.Segmenter` is available and unused in `src`. If R1's visitor-facing "characters" is ever meant to mean what a visitor sees, the instrument exists — and OQ-T46-2 follows from the same decision. |
| **OQ-T32-4** | Sixteenth copy of the harness machinery, now 61 rows for a three-line module. |
| **Phase 13 trailer** | The `.ai/` template's `Co-Authored-By` names a stale model. Fix by regenerating, not by hand. |

**Phase 13 complete.** Ready to merge; `main` stays deployable, since this task adds one test file and
changes no production code.
