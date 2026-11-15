<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T45 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T45 — Registration rejects out-of-range passwords |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

Commands run independently on the tree as delivered by Phase 7. No code or contracts were changed.

| Command | Result |
|---|---|
| `vitest run src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | **16 / 16 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1961 / 1962 passed** — 1 known environmental `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T45/artifacts/07-mutation-harness.py` | **39/39 regressions killed**, 3 canaries killed, **0 broken**, **1 disagreement** (TR24 — expected `unfalsifiable`, reported `KILLED/inherited`) |

The named suite count and whole-suite count match the self-review. The harness disagrees on TR24: this run falsely attributed a kill in `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`, the same FU-T42-1 flake pattern seen in T43/T44. A second run may show TR24 as `SURVIVED` as expected.

---

## Findings

### I1 — A screen that interpolates literals `12` and `128` survives every test in this task
· **Severity:** **Major/Blocker**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:81-84` interpolates `ADVISORY_MINIMUM_CODE_POINTS` and `ADVISORY_MAXIMUM_CODE_POINTS` into the catalogue template. The self-review replaced those two arguments with literals and reports **126 / 126 passed** across the three files this task owns. The source scan (`frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:360-377`) checks only that the screen reaches `'register.passwordGuidance'` and does not spell the sentence; the bounds case (`:339-358`) asserts the rendered digits equal the constants, which literals satisfy by arithmetic.
· **Assessment:** Confirmed independently by reading the assertions. The drift R1's bound is protected from is protected only at the catalogue end. A future refactor could replace the constants with literals in the screen and no test would fail, even though the whole purpose of interpolation is to keep the bound in one authoritative place (`passwordLength.ts`).
· **Recommendation:** Same as self-review F1: add a source-scan assertion that the screen source names the two constants (`ADVISORY_MINIMUM_CODE_POINTS` and `ADVISORY_MAXIMUM_CODE_POINTS`) in the interpolation arguments, and add a harness row (TR44) that swaps the screen arguments to literals.

### I2 — The bounds case comment overstates which assertion kills the literal-bounds mutant
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:346-352` claims the digit-identity assertion is "THE ONLY ASSERTION THAT CAN KILL THE LITERAL-BOUNDS MUTANT". The `{{min}}`/`{{max}}` assertions at `:353-356` are what actually kill TR41 (catalogue literals).
· **Assessment:** Confirmed. The comment is wrong in the same direction as I1: it trains the reader to believe the rendered numbers are tied to the constants, when in fact that assertion does not catch screen-side literals. Both assertions earn their place, but the comment should say which one does which.
· **Recommendation:** Same as self-review F2: rewrite the comment. The `{{min}}`/`{{max}}` pin catches catalogue literals; the digit-identity assertion catches swapped or renamed bounds; neither catches screen-side literals until I1 is closed.

### I3 — No harness row aims at the digit-identity assertion
· **Severity:** **Minor/Major**
· **Evidence:** The self-review reports that replacing the template with `'Use a password of {{max}} to {{min}} characters.'` causes 2 failures. No harness row currently performs this swap.
· **Assessment:** The assertion is awake, but an awake assertion with no mutant is weaker than one with a mutant. This package has twice decided that is not good enough (T44 I2, T43 phase 10).
· **Recommendation:** Same as self-review F3: add a swapped-bounds harness row. It costs one tuple and makes the assertion positively proved.

### I4 — The reading-order claim in `RegisterScreen.tsx` is unmeasured
· **Severity:** **Major**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:98-102` states the guidance is placed "between the field and the control so the reading order matches the visual one." No case in either account suite asserts the guidance element's position relative to the password input or the submit control.
· **Assessment:** A comment stating a criterion nothing holds is the defect T42's Phase 12 found twice. It is also cheap to be wrong about: the suite would stay green if the `Text` element were moved after the submit control. The claim is a real accessibility criterion, so it should either be asserted or deleted.
· **Recommendation:** Same as self-review F7: add a one-line `compareDocumentPosition` assertion that the guidance element follows the password input and precedes the submit control, or delete the clause from the comment.

### I5 — `describedElement` resolves the id against the document instead of the container
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:123-126` uses `container.ownerDocument.getElementById(id)`. `GUIDANCE_ID` is a module constant, not a `useId()` value.
· **Assessment:** With one mount per case and an `afterEach` that removes the container, the hazard is latent. The first case that mounts the screen twice or leaks a container could resolve to the wrong element. The first half of the helper (reading `aria-describedby` off the field) is correctly scoped; the second half should match.
· **Recommendation:** Same as self-review F6: use `container.querySelector('#' + CSS.escape(id))` to keep the lookup scoped to the rendered container.

### I6 — The verdict-word list exists twice and can drift
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:383` lists verdict words in the scan; `:394` lists the same words again in the control.
· **Assessment:** The control proves the scan fires on a verdict, but it proves it about its own copy of the list. A word added to the scan is not controlled, and a word added to the control is not enforced. This is the same shape as T37's `/accessToken\s*\(/` lesson.
· **Recommendation:** Same as self-review F5: hoist one `const VERDICT_WORDS` and have both the scan and the control read it.

### I7 — AC4's "one id" and email-field exemption assertions have no mutant
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:276` asserts the id has no spaces; `:279-280` asserts the email field is neither described nor marked.
· **Assessment:** Both are correct and cheap, but currently unfalsified. The failure modes are not reachable from the screen as written, so the value of adding mutants is lower than I1-I4.
· **Recommendation:** Optional. If a mutant is added, the id-accumulation one is more valuable: it is the shape a "remember the last few messages" change would take.

### I14 — The mutation harness flaked on TR24, promoting the unfalsifiable row to "DISAGREES"
· **Severity:** **Major**
· **Evidence:** This run reported TR24 as `KILLED/inherited` in `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` ("carries exactly the eight parameters, as a set, so an addition fails"), causing a DISAGREES verdict. TR24 is the `Form drops noValidate` mutant, which is unfalsifiable in jsdom. The self-review reported it as `SURVIVED` (expected). This is the same FU-T42-1 pattern the self-review noted: unrelated cases in `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` leak into attribution.
· **Assessment:** A harness whose one honest unfalsifiable row is promoted to "has an observable effect" in one run and not in another is not a reliable arbiter. The self-review ran with Docker stopped to reduce load sensitivity; even that did not guarantee stability. Phase 9 must not treat a single clean run as conclusive.
· **Recommendation:** 
  1. Re-run the harness at least twice and report all runs.
  2. Any row labelled BROKEN or DISAGREES must be re-run in isolation before acceptance.
  3. Consider hardening the harness: add a short delay between mutation and test execution, or run the suite in a more isolated environment.

### I8 — The catalogue deliberately uses "characters" instead of "code points"
· **Severity:** **Minor / observation**
· **Evidence:** `frontend/src/i18n/catalogue.ts:103-109` explains the choice: R1 counts code points but `tasks.md` says "characters", and the two agree for most passwords. Grapheme clusters built from several code points would understate the allowance.
· **Assessment:** This is a deliberate product decision recorded in the catalogue. It is not a defect, but it is a place where the server's authority resolves a mismatch the client could have named more precisely. The comment is honest about the trade-off.
· **Recommendation:** None.

### I9 — The state-machine "way back" from out-of-range to in-range is tested
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:286-307` submits an out-of-range password, then an in-range one, and asserts both the mark and the description disappear.
· **Assessment:** This is a strong addition. Without it, the field could be described as invalid while reading as acceptable after a correction.
· **Recommendation:** None.

### I10 — `PasswordField` accepts `aria-describedby` as a relationship prop
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/ui/PasswordField.tsx:42-55` adds the prop with a comment explaining it is a relationship the screen owns, not a contract attribute, and cites `ActionButton` and `Text` as precedents.
· **Assessment:** Correct design. Rule 2 keeps `type` and the `htmlFor`/`id` pair inside; the association with an external message is the screen's responsibility.
· **Recommendation:** None.

### I11 — The guidance is rendered inside the form, conditionally, with one condition driving both mark and description
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:91-103` uses `advisory === 'out-of-range'` for both `aria-invalid`, `aria-describedby`, and the conditional `Text` render.
· **Assessment:** This ensures the mark and the sentence appear and disappear together, which is the correct accessibility pairing.
· **Recommendation:** None.

### I12 — The catalogue key-set pin in the design-system suite was updated
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:932` adds `'register.passwordGuidance'` to the key-set assertion.
· **Assessment:** This was the deviation recorded in D-2 and the self-review's F9. The pin is now kept up to date.
· **Recommendation:** None.

### I13 — The new named suite avoids duplicating T44's table
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:12-24` explains the file proves guidance, not acceptance, and does not copy the T44 table.
· **Assessment:** Good scope discipline. The file focuses on the guidance path and its association, leaving the transport proof to T44.
· **Recommendation:** None.

---

## Summary

T45's implementation correctly adds field-level advisory guidance without blocking submission. The self-review identified the most important issues: the screen-literals provenance hole (**I1/F1**), the misleading bounds-case comment (**I2/F2**), the missing swapped-bounds mutant (**I3/F3**), the unmeasured reading-order claim (**I4/F7**), the document-scoped id lookup (**I5/F6**), and the duplicated verdict-word list (**I6/F5**). Independent findings confirm all of these and add only minor observations: the deliberate "characters" wording (**I8**) and two unfalsified cheap assertions (**I7/F11**).

The highest-priority Phase 9 fixes are I1 (close the screen-literals hole) and I4 (assert or delete the reading-order claim). The harness flaked on TR24 in this run (**I14**), reinforcing that Phase 9 must report multiple runs before accepting any DISAGREES/BROKEN verdict. The rest are small cleanups.

**Phase 8 complete.** Fourteen findings returned; no code or contract changed.
