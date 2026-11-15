<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T46 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T46 — Code points, not UTF-16 |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/09-review-resolution.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/10-test-generation.md` |

Named suite: `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts` — the package's
`passwordLengthCountsCodePoints`. **No production file was changed by this phase**, and none by this task
(the brief's F12, and phase 9's verification says the same).

## What this phase added, and why each one is not decoration

T46's suite arrived at phase 10 already passing 25 cases with every mutant it aimed at dead. The question a
test-generation phase has to answer in that position is not "what else can I assert" but **what does the
file claim that nothing in it can falsify**. Three answers:

### 1 · The ordering of the three units was unmeasured

AC5 asserted that a family emoji is **1** picture, **5** code points and **8** UTF-16 units — three
literals, all correct, and all chosen by me. What the file never said is which of the three R1 names, or
that they stand in that order at all. A reader could have come away believing code points are what a
visitor counts, or what the engine stores, and every row would still have passed.

`Intl.Segmenter` is in this runtime and is used **nowhere** in `src` (measured, not assumed). That makes it
usable here as an independent witness: it is not the function under test, and unlike 1, 5 and 8 it is not a
number I picked. The new case asserts the **relation** —
`graphemes(cluster) < countCodePoints(cluster) < cluster.length` — plus a control proving the witness is
not simply returning 1: on `'👍'.repeat(3)` and on `'abc'` it agrees with both of the other units, so the
three-way split is a property of the **input** and not of the counter.

### 2 · The table is a sample; the law it samples was not stated

Fifteen rows is fifteen strings I chose, and a sample can be satisfied by accident. `value.length` cannot
pass them, but an implementation that halved the length when a value "looked astral" would pass several.
The new AC3 case asserts `countCodePoints` over **36 strings nobody wrote down** — k astral code points
plus m ASCII, for every k and m up to five — with both numbers stated **arithmetically** (k + m code
points, 2k + m units) rather than measured, so no expectation in it came from the implementation either.

That case has **no mutant of its own** in the harness, and its comment says so. Every implementation it
would kill is already killed by a named row. It is in the file because the rows are a sample and this is
the law; it is labelled because an unlabelled unfalsifiable case is how a suite grows weight without
strength, which this package has now watched happen four times.

### 3 · All three clauses of the table control passed on a table with a duplicate in it

The control asserts that some row discriminates the two units, that some row crosses the minimum between
them, and that some row crosses the maximum. A duplicated input satisfies all three unchanged — and the
**row-count guard** in the circularity control would have counted the duplicate as a fact, which is worse
than not counting at all. One clause closes it: no two rows hold the same string.

### 4 · Three string literals held invisible characters

The family emoji was stored with **literal U+200D** joiners in three places rather than the `‍`
escape. The values are identical and every assertion was already right. It is still a defect in a test
file whose whole subject is counting characters: nobody reading the line can see them. The way I found out
is the way anyone would — a phase 10 edit anchored on a line that was plainly there on screen and matched
nothing. They are escapes now.

---

## Test manifest

`frontend/src/features/account/passwordLengthCountsCodePoints.test.ts` — **27 cases**. Everything in it
verifies **R1**; the AC column is the frozen brief's (`artifacts/04-frozen-task-brief.md`, AC1–AC7).

| # | Case | AC | What fails if it breaks |
|---|---|---|---|
| 1–15 | `counts $name as code points, not UTF-16 units` — the 15-row `CASES` table, `it.each` | **AC1, AC3, AC5** | Each row asserts BOTH numbers for one string: `countCodePoints(input) === codePoints` and `input.length === utf16Units`. Both are literals measured on this host before they were written down. |
| | · the empty string · 12 ASCII · 128 ASCII | AC3, AC4 | the two bounds in the unit where both counts agree |
| | · one astral · 11 astral · 12 astral · 128 astral | **AC1, AC2** | the four rows where the units part company, including both bounds |
| | · skin-toned thumbs-up (2) · flag (2) · family emoji (5) | **AC5** | a grapheme cluster is more than one code point |
| | · composed accent (1) · decomposed accent (2) | AC3, **OQ-T46-2** | the same letter, two spellings, two counts — pinned as it behaves, not decided |
| | · lone high surrogate · lone low surrogate · lone surrogate between letters | **AC4** | the iterator yields a half pair rather than throwing |
| 16 | `CONTROL: the table can tell the two units apart` | **AC1, AC3** | four clauses on the table's content: some row discriminates the units, some row crosses the minimum between them, some row crosses the maximum, **and no two rows hold the same string** (new in phase 10). The crossing clauses read the constants, not 12 and 128. |
| 17 | `CONTROL: no expectation in this file is computed by the function under test` | **AC3** | the circularity guard: the table region contains no `[...`, no `Array.from`, no `countCodePoints(`, and the region holds all 15 rows (counted on `utf16Units: \d`, because `{ name:` misses the four multi-line rows and `name:` counts the type annotation). |
| 18 | `the lower bound: eleven astral code points are twenty-two units, so '.length' would ACCEPT what R1 excludes` | **AC2** | the silent direction of the bug: 11 code points, 22 units, and the wrong unit admits the password without saying anything. |
| 19 | `the upper bound: 128 astral code points are 256 units, so '.length' would REJECT what R1 allows` | **AC2** | the loud direction: 128 code points, 256 units, and the wrong unit refuses a password R1 allows. |
| 20 | `counts the parts, not the picture` | **AC5** | the three cluster literals, with the comment that task 45's visitor-facing "characters" is a deliberate imprecision and this is where the real unit lives. |
| 21 | `is strictly between the two units it is confused with, measured by a real segmenter` — **new in phase 10** | **AC5** | the ORDER of the three answers, measured against `Intl.Segmenter` rather than against three more numbers of mine, plus a control proving the witness agrees with both other units on strings with no clusters in them. |
| 22 | `returns a number for every string, and throws for none` | **AC4** | totality over every table row, integrality, and a 100 000-character value that answers. The comment no longer claims a pass count (phase 7's F5, phase 8's I2). |
| 23 | `is NOT additive across a concatenation, because a surrogate pair can be split` | **AC4** | 1 + 1 = 1. Any future implementation that counted incrementally over a chunked or streamed value would pass every other case in the file and fail this one. |
| 24 | `counts k astral code points and m ASCII as k + m, for every k and m up to five` — **new in phase 10** | **AC3** | the law the table samples, over 36 strings nobody wrote down, with both numbers stated arithmetically. **No mutant of its own**, and its comment says so. |
| 25 | `reaches for no storage, no DOM, no clock and no global, and imports nothing` | **AC6** | a source scan of `passwordLength.ts`, comments stripped, against twelve patterns in five categories. Asserted at the source because T43 measured that a behavioural negative about an absent side effect is vacuous. |
| 26 | `CONTROL: the environment scan fires on a module that DOES reach for one` | **AC6** | the scan is not inert: a synthetic impure module must trip **one pattern from every category by name** — `['clock', 'console', 'dom', 'import', 'storage']` — not five patterns of any kind (phase 7's F4, phase 8's I1). |
| 27 | `publishes exactly three names, and the bounds are 12 and 128` | **AC7** | the module's surface and R1's two numbers in the module's own suite, not only in the screens that read them. |

Every AC has at least one case; AC2 and AC5 have the cases the task exists for.

## Harness

`artifacts/07-mutation-harness.py` — **59 rows**, two added by this phase.

| Row | Mutant | Prediction | Outcome |
|---|---|---|---|
| **TR57** | the count becomes **graphemes** — the unit a visitor would guess | own | **KILLED/own**, 7 cases: four table rows, the three AC5 literals in one case, and the new segmenter case |
| **TR58** | the same grapheme count **with all seven of those expectations rewritten to agree with it** | own, by exactly one case | **KILLED/own**, **1 case** — `is strictly between the two units it is confused with, measured by a real segmenter` |
| **TR59** | a table row **duplicated** — fifteen facts in sixteen rows | own, by exactly one case | **KILLED/own**, **1 case** — `CONTROL: the table can tell the two units apart` |

**TR57 on its own does not justify phase 10.** It dies on expectations that all existed at phase 7, so a
suite without the segmenter case kills it just as dead. TR58 is the row that says what the new case buys:
it is the realistic shape of this mistake — somebody decides the count should be "what the visitor sees",
changes the function, runs the suite and updates the numbers it reports. Afterwards every surviving
assertion in the file is self-consistent. The units still disagree on the astral rows, both bounds still
cross, the table still discriminates, the arithmetic property still holds, totality still holds, the
exports are unchanged. The one thing that cannot be edited into agreement is
`graphemes(cluster) < countCodePoints(cluster)`, because the witness lives outside the file. TR58 was
predicted to die in exactly one case and died in exactly one case.

TR59 is the same shape for the third addition, and it is the one that would have been easiest to believe
without measuring: a duplicated row passes `it.each`, keeps the row-count guard balanced (both numbers grow
together), and satisfies all three of the control's original clauses.

The composition property (case 24) has **no row of its own** and that is stated in the file rather than
left to be found. Every implementation it would kill is already killed by a named row; the one shape it
alone would catch — a count memoised on `value.length` — is caught by the table too, because `'👍'` and a
decomposed `é` are both two UTF-16 units and are 1 and 2 code points. Inventing a contrived row to make the
case look load-bearing would be worse than recording that it is not.

---

## Verification

| | |
|---|---|
| named suite | **27 / 27** |
| whole suite | **1991 / 1992** — the one known environmental failure, `lintRejectsHandWrittenBackendFetch.test.ts` (this host's `oxlint` prints no summary line) |
| `tsc --noEmit` | **exit 0** — `Intl.Segmenter` types resolve under the package's lib settings |
| `oxlint src` | **exit 0** |
| harness | **52 / 52 regressions killed**, 4 canaries killed, 2 expected survivors (TR24, TR50), **0 broken**, **0 disagreements** |

`artifacts/10-mutation-run.txt`. Run with the local Docker stack **stopped** (FU-T42-1), on a committed
tree — the tests went in before the harness ran, as they must, because `revert()` is
`git checkout --` plus `git clean -fq`.

**Verdicts are identical row for row to phase 9's run** for all 55 rows that existed then (diff over row
id, class and verdict: empty). The three suite additions moved no mutant's fate, which is what a phase that
adds controls and a witness should do; the three new rows are the only new verdicts.

This is the **fifth** full run of this harness (three in phase 7/9, one by Kimi in phase 8, this one), and
the second since the `dirty()` startup guard went in. It refused nothing, because the tree was clean.

## Deviations

| | |
|---|---|
| **D1** | **Three new harness rows, not one.** Phase 10's plan named TR57. TR57 turned out not to measure the new case — it dies on expectations that predate it — so TR58 was written to isolate it and TR59 to isolate the uniqueness clause. A phase that adds a case and a row that the case does not uniquely kill has added weight, not strength. |
| **D2** | **Three literal U+200D characters were replaced with escapes** in the named suite. Not a test change and not a production change; the values are identical. Recorded because this phase touched a line for a reason that was not a test. |
| **D3** | **Case 24 has no mutant and says so in its own comment.** The alternative was a contrived row; the package's rule is that a mutant must measure its own description. |

## Open items carried forward

| | |
|---|---|
| **OQ-T46-2** | Is R1 meant to be normalisation-sensitive? A composed `é` counts 1, a decomposed one 2, so the same password typed on two keyboards is two lengths. Held by a table row and by TR52. The answer is the owner's. |
| **OQ-T46-3** | **New.** `Intl.Segmenter` is available in this runtime and used nowhere in `src`. If R1's "characters" copy is ever meant to count what a visitor sees, the instrument already exists — and the answer to OQ-T46-2 would follow from the same decision. |
| **OQ-T32-4** | Sixteen copies of the harness machinery; phase 8's I11 is the newest sighting. |
| **FU-T42-1** | Quiet again, stack stopped. Fifth sighting remains the last. |

## One record correction

The commit that carries this artifact is titled **`docs(frontend/T46): Phase 9 - 49/49 again, and the
guard that would have caught the crash`** — phase 9's subject line, pasted by mistake. Its body is phase
10's and its content is this artifact, `artifacts/10-mutation-run.txt` and TR57–TR59. The title was not
corrected because that would mean rewriting pushed history; it is written down here instead, because the
commit titles are how this pipeline's phases get read back later and a wrong one is a wrong record.

**Phase 10 complete.** Two cases and one clause added, three harness rows, no production file changed.
