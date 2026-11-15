<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation). -->

# frontend · T45 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T45 — Registration rejects out-of-range passwords |
| **Consumes** | `artifacts/04-frozen-task-brief.md` (FROZEN), `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `artifacts/10-mutation-run.txt` |

## Result

| | |
|---|---|
| named suite | 16 → **19 cases** |
| whole suite | **1964 / 1965** — the known environmental lint-script failure |
| `tsc --noEmit` · `oxlint src` | **0** · **0** |
| harness rows | 46 → **48** |
| harness | **44/44 regressions killed**, 3 canaries killed, **1 expected survivor** (TR24), **0 broken**, **0 disagreements** |

Every row's verdict is identical to phase 9's two runs, row for row, with the counts shifted by the three
new cases. No flake this run: TR27, which picked up a stray failure from
`loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` in phase 9's second run, named three failures
in its own file and nothing else.

## The manifest

`frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx`
— **19 cases**, the named test from `package.md` §8. Phases 6 and 9 wrote 16 of them; phase 10 added three,
each for a claim an AC makes and nothing held.

| case | AC / ID | mutant that would break it |
|---|---|---|
| `shows the guidance for 11 ASCII characters, one short of the lower bound` | **AC1**, AC6, R1 | TR36, TR39 |
| `shows the guidance for 129 ASCII characters, one past the upper bound` | **AC2**, AC6, R1 | TR30, TR36 |
| `shows the guidance for 129 astral code points, which are 258 UTF-16 units` | **AC2**, R1 | TR30, TR36 |
| `shows the guidance for the empty string, which is zero code points` | AC1, D16 | TR36, TR39 |
| `shows the guidance for 11 astral code points, which are 22 UTF-16 units` **(new)** | **AC1**, R1 | **TR47** |
| `straddles both bounds, and carries the one astral row whose unit count is INSIDE the range` **(new)** | control for AC1/AC2 | a deleted row |
| `shows nothing for the lower bound itself` | **AC3** | TR37, TR38 |
| `shows nothing for the upper bound itself` | **AC3** | TR31, TR37 |
| `shows nothing for 128 astral code points, which are 256 UTF-16 units` | **AC3**, R1 | TR31, TR33, TR37 |
| `CONTROL: the same query finds the guidance when it IS there` | control for **AC3** | TR36, TR39 |
| `points the field at the element that holds the sentence, and at nothing else` | **AC4**, D-T45-3, L11 | TR39, **TR46** |
| `removes both the mark and the sentence when a later submit is in range` | **AC4**, state transition | TR37 |
| `keeps the sentence up while an in-range password is typed, and drops it on the submit` **(new)** | **AC5**, state transition | **TR48** |
| `shows nothing while three characters are typed` | **AC5** | TR37, TR38 |
| `shows nothing while two hundred characters are typed` | **AC5** | TR37, TR38 |
| `renders the two bounds, and they ARE the constants the check uses` | **AC7**, D-T45-2 | TR41, **TR45** |
| `reaches the catalogue for the sentence and spells no part of it` | **AC7**, L11 | TR40, **TR44** |
| `delivers guidance, not a verdict (D-T45-2)` | **AC7** (F12) | TR42, TR45 |
| `CONTROL: the verdict scan fires on a sentence that DOES deliver one…` | control for **AC7** | the scan aimed at nothing |

Inherited, in files this task edited one line of each:

| case | file | AC |
|---|---|---|
| `reaches the catalogue for every word it renders…` | T43's suite `:515-516` | **AC7** (reachability) |
| `keeps the catalogue's key set exactly as it was (AC11)` | the design-system suite `:932` | T173's, kept honest by D-1 |
| `bears no lowercase JSX tag` · `renders no lowercase JSX tag in any of the render sites` | both | the guidance renders through `Text`, not a bare element |

## Every AC, and where it is held

| AC | held by | complete? |
|---|---|---|
| **AC1** 11 code points → guidance | three rows, two of them astral-discriminating in opposite directions | ✔ |
| **AC2** 129 → **the same** guidance | two rows + the table control that stops one end being deleted | ✔ |
| **AC3** in range → nothing, with a control | three rows, both bounds and the astral upper case, one control | ✔ |
| **AC4** programmatic association | the walk from `aria-describedby`, the single-id pin, the email exemption, the reading order, the way back | ✔ |
| **AC5** nothing before the first submit | two typing rows at both ends **and** the persistence case, which is the other half | ✔ |
| **AC6** still sent, nothing disabled | asserted in every out-of-range row: one request, body unaltered, neither control disabled | ✔ |
| **AC7** catalogue, no spelling, reachable | the digit identity, both source ends of the provenance wire, the verdict scan and its control, the reachable-keys line | ✔ |

## The three things phase 10 added, and why each is not a nicety

### 1 · The end where the counting bug is silent

Every out-of-range row was out of range on **both** counts. 129 astral code points are 258 UTF-16 units, so
that row is out of range whichever unit the screen counts — its guidance is right either way, and the row
discriminates nothing.

Its own comment half-knew this: it says in as many words that a unit-counting screen would be *"out of
range for the wrong reason and the guidance would be right by accident"*. What it then claims is wrong —
*"the case below asserts the count as well as the sentence, so the two cannot agree by coincidence"*. The
count assertion is `countCodePoints(password) === 129`, which is a claim about the **helper**, not about
which unit the **screen** compared; it cannot tell the two apart. The comment is corrected rather than left
to mislead the next reader, and the correction is comment-only (see the note at the end).

At the other end it inverts. **11 astral code points are 22 UTF-16 units**, and 22 is comfortably inside
12-to-128, so a screen comparing `password.length` calls this password fine and says **nothing** to a
visitor R1 says must be told. Task 43's 7-emoji row catches that comparison — but through the `aria-invalid`
**mark**, in a file this task did not write. The **sentence** was uncovered at the one end where the failure
mode is silence rather than a wrong word, which is the harder of the two to notice in a bug report.

**TR47** is that mutant: the lower comparison counting units. It is TR33's mirror, and before this phase it
had no killer in this suite.

### 2 · The state machine's other edge

`advisory` is assigned in `submit()` and nowhere else. A sentence raised by one submit therefore stays up
while the visitor corrects the password, and comes down at the next submit. Task 43 pinned that nothing is
marked *before* a submit and stopped there; the way **back** got a case in phase 6, but the *staying up* did
not, so the sentence could have been made to vanish on a keystroke with 18 of 19 cases green.

**TR48** is the shape that change would take — `onChange` resetting the advisory, which is exactly what a
well-meaning "clear the error as they fix it" edit looks like. The case pins today's behaviour and names the
product question rather than deciding it: **OQ-T45-5**.

### 3 · A table that could lose an end

Every row in `OUT_OF_RANGE` compares against the same `GUIDANCE` constant. That satisfies AC2's "the same
guidance, word for word" **by construction** — and would go on satisfying it if someone deleted the 129
rows, leaving AC2 unmeasured with the suite green. T44 learnt this about its own table and pinned both
bounds; the guidance table now has the same control, plus the clause that keeps the discriminating astral
row from being dropped as a duplicate of the ASCII one.

## What is deliberately NOT claimed

| | |
|---|---|
| **The id's spelling** | No case asserts the id is `register-password-guidance`. The walk is the claim; the name is an implementation detail, and pinning it would make a rename a failure for no reason. A mutant renaming it changes both sides and is correctly invisible. |
| **That a screen reader announces it** | D-T45-1's accepted residual risk. jsdom has no accessibility tree and no announcement; the association is what is measurable, and it is measured. |
| **Focus** | The freeze forbids moving it (F3). Nothing here moves or asserts focus. |
| **The email field's exemption as a mutable claim** | `EmailField` accepts neither `aria-describedby` nor `aria-invalid`, so the assertion is held by the **type system**. A mutant would not compile. Recorded as OQ-T45-4 for the task that gives the address a message. |
| **"characters" vs "code points"** | A deliberate copy decision recorded in the catalogue (phase 8's I8). The sentence's exact words are pinned once, non-derived. |

## No production code changed in this phase

The diff is the named suite and the harness. `RegisterScreen.tsx`, `catalogue.ts` and `PasswordField.tsx`
are untouched since phase 9's — phase 10 is a measurement phase, and L12 holds.

## The two new rows, and what killed them

| row | killed in | and that is the point |
|---|---|---|
| **TR47** the lower comparison counts UTF-16 units | `shows the guidance for 11 astral code points, which are 22 UTF…` **(this task's new row)** and `sends 7 emoji, which \`.length\` would call 14…` **(task 43's)** | Two files, and only one of them existed before this phase. Checked row by row: of the fifteen cases in this suite before phase 10, **none** would have failed — the 11-ASCII and 129 rows are out of range on both counts, and the three AC3 rows are *in* range on both, because TR47 leaves the upper comparison alone. The mark was covered in task 43's file; the sentence was covered nowhere. |
| **TR48** the advisory resets on every keystroke | `keeps the sentence up while an in-range password is typed, and drops it on the submit` **(new)** | One case, and it is the only one that can see it. Every other case types and then submits in the same breath, so a reset on change is invisible to all of them. |

## One comment corrected after the run

The 129-astral row's comment claimed *"the case below asserts the count as well as the sentence, so the two
cannot agree by coincidence."* That is false, and phase 10's new row is why it matters: the count assertion
is about `countCodePoints`, not about which unit the screen compared. The comment now says the row
discriminates nothing and points at the one that does. It is comment-only — it cannot change a mutant's
fate — and it landed **after** the run so the run measured one tree. The named suite was re-run after it:
**19 / 19**.

Writing it down is the whole of the fix. The row stays: 129 astral is still the only row that proves the
guidance survives astral input at the upper end, and R1's "code points" is a claim about both ends.

## Open questions this phase raises

| | |
|---|---|
| **OQ-T45-5** | Should the guidance clear when the visitor edits the password, or stay until the next submit? Today it stays, because `advisory` is assigned in `submit()` and nowhere else, and task 43 made the same choice for the mark. The new AC5 case pins today's behaviour; if the owner wants it cleared on edit, that case is where the change announces itself, and TR48 is the mutant that becomes the fix. |
| **OQ-T45-4** | `EmailField` accepts neither `aria-describedby` nor `aria-invalid`, so AC4's email-field exemption is enforced by the type system rather than by a mutant. The first task that gives the address a message turns that assertion into a real claim. |
| **FU-T42-1** | Fifth sighting in phase 9. Unchanged here, and this run was clean. |
| **FU-T43-2** | Third sighting, deferred in phase 9. Unchanged. |

**Phase 10 complete.** Three cases, two harness rows, one comment corrected; no production code changed.
