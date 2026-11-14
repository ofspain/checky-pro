<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T44 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T44 — Registration accepts 128 characters |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

Commands run independently on the tree as delivered by Phase 7. No code or contracts were changed.

| Command | Result |
|---|---|
| `vitest run src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | **45 / 45 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1944 / 1945 passed** — 1 known environmental `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T44/artifacts/07-mutation-harness.py` | Run 1: **29/31 regressions killed**, 2 canaries killed, **1 survivor** (TR24), **2 BROKEN** (TR18, TR20), **3 disagreements** (TR18, TR20, TR21). Run 2: **31/31 regressions killed**, 2 canaries killed, **1 survivor** (TR24), **0 broken**, **1 disagreement** (TR21). |

The whole-suite count matches the self-review (1945 cases, 1 environmental failure). The harness is **not stable across runs**: Run 1 reported TR18 and TR20 as BROKEN with "a file did not LOAD", while Run 2 killed both normally. This is a different manifestation of the same flakiness the self-review reports for TR24. The second run matches the self-review's headline (31/31 killed, 0 broken, 1 disagreement TR21).

---

## Findings

### I1 — The mutation harness is flaky in both directions
· **Severity:** **Major**
· **Evidence:** 
  - The self-review reports `KILLED inherited TR24` in one full-suite run, then `SURVIVED … as expected` when run alone and in three subsequent full-suite runs (FU-T42-1's third sighting).
  - My Run 1 reported TR18 and TR20 as **BROKEN** with "a file did not LOAD", but my Run 2 killed both normally (31/31 regressions killed, 0 broken).
  - In both of my runs TR24 survived as expected, so the self-review's TR24 flake did not reproduce here.
· **Assessment:** The harness is producing non-deterministic outcomes. The self-review saw an `unfalsifiable` row falsely promoted to "DISAGREES" via inherited kills; I saw two regressions falsely demoted to "BROKEN" via load failures. Both errors come from the same source: attribution or execution that is not stable across runs. A harness whose verdicts flip is not a reliable source of truth for phase 9.
· **Recommendation:** 
  1. Phase 9 must re-run the harness at least twice and report all runs, not just the latest.
  2. Any row labelled BROKEN or DISAGREES should be re-run in isolation before it is accepted as a finding.
  3. Consider hardening the harness's revert step (e.g., a `git clean -fd` after checkout, or a short delay between mutation and test execution) to reduce "file did not LOAD" failures.

### I2 — Three of AC4's six assertions are still unfalsified by the harness
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:420-438` asserts neither `EmailField` nor `PasswordField` contains `maxLength` or `minLength`. The harness's TR32 only adds `maxLength={128}` to `PasswordField`.
· **Assessment:** Confirmed independently. The two pins killed by TR32 are `PasswordField` has no `maxLength` and the registration suite's runtime AC4 case. The other four assertions — `EmailField` has no `maxLength`, neither field has `minLength` — are only asserted, not mutated. A negative assertion with no positive proof is the same shape as the storage-spy bug T43 fixed.
· **Recommendation:** Same as self-review F4: add one mutant — `minLength={12}` on `EmailField` — which covers the other file and the other attribute in a single realistic mistake. Do not rely on a later task to accidentally introduce it.

### I3 — The source scan hardcodes the two field names and cannot cover future field primitives
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:431` loops over `['app/ui/EmailField.tsx', 'app/ui/PasswordField.tsx']`. The comment above claims it stops the attribute being added to "a field a later task adds".
· **Assessment:** Confirmed. A future `TextAreaField`, `SearchField`, or `PhoneField` would be invisible to this scan the day it landed. The comment is false in the direction that matters.
· **Recommendation:** Same as self-review F3: scan `PRIMITIVE_FILES` and apply the `maxLength`/`minLength` check to every file whose lowercase tags include `input` (or whose name matches a field naming convention). If that is declined, rewrite the comment to match the actual loop.

### I4 — `/maxLength/i` and `/minLength/i` are substring matches
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:434,436` use case-insensitive substring regexes.
· **Assessment:** The safe direction (false positive rather than false negative) makes this acceptable, but it is inconsistent with the package's own history: T43 phase 10 fixed an identical substring trap in the catalogue scan, and T44's source scan comment even references that lesson. A variable named `maxLengthHint` or a comment containing the word would fail the scan.
· **Recommendation:** Tighten the patterns to `\bmaxLength\s*=` and `\bminLength\s*=`, which match a JSX attribute assignment and not an arbitrary identifier. If left as substring, document the breadth as deliberate.

### I5 — The T44 AC2 case is well designed but duplicates the 128 count claim with the total-function case
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:307-332` proves 128 emoji are 256 UTF-16 units and 128 code points through the DOM. `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:392` asserts `countCodePoints('👍'.repeat(128)) === 128`.
· **Assessment:** The duplication is intentional and justified: task 46 owns the helper's correctness, task 44 owns the screen's behavior at the boundary. The two assertions are at different layers. No action needed, but worth noting as a positive.
· **Recommendation:** None.

### I6 — The 129 row is ASCII only; no astral row sits one past the upper bound
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:260` adds `{ name: '129 ASCII characters, one past the upper bound', password: 'a'.repeat(129), codePoints: 129, inRange: false }`. There is no `{ password: '👍'.repeat(129), codePoints: 129, inRange: false }`.
· **Assessment:** The advisory check compares code points, so 129 ASCII and 129 emoji are both out of range and both would mark `aria-invalid="true"`. The ASCII row is sufficient to falsify a widened maximum. An astral 129 row would add symmetry and a second chance to catch a `.length`-based implementation at the upper end, but it is not required by the brief.
· **Recommendation:** Optional: add a 129-astral row for symmetry. The table already accepts it without restructuring.

### I7 — The table-driven case does not assert the DOM value length for ASCII rows
· **Severity:** **Minor**
· **Evidence:** The `it.each(PASSWORDS)` case (`:269-293`) asserts `countCodePoints(password) === codePoints` but not `passwordInput(container).value.length === codePoints`.
· **Assessment:** For ASCII rows, `value.length` equals code points, so a `.length`-based check would pass them. The astral rows and the dedicated AC2 case catch the false reject. The table case is not the place to duplicate that. Not a defect.
· **Recommendation:** None.

### I8 — The runtime AC4 case's comment overstates what body-equality assertions could have caught
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:340-343` says the negative cannot be proved by body-equality assertions because `maxLength` constrains user editing only, and the test sets value through the prototype setter.
· **Assessment:** The measured finding is correct and important. The comment is accurate: jsdom does not enforce `maxLength` on programmatic value assignment. This is exactly why the source scan and the harness mutant are load-bearing.
· **Recommendation:** None — the comment is good documentation.

### I9 — The `PASSWORDS` table comment honestly records the astral row as a surprise
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:230-234` notes that phases 0-3 predicted only the two ASCII rows, and the astral row was added because it is the only row that fails in the direction this end of the range fails in.
· **Assessment:** This is honest documentation of design evolution. It also corrects the earlier 512-unit miscalculation (the factor is two, so 128 emoji = 256 UTF-16 units, not 512).
· **Recommendation:** None.

### I10 — The table control case now pins both ends of the range
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:404-418` asserts rows exist on both `ADVISORY_MINIMUM_CODE_POINTS` and `ADVISORY_MAXIMUM_CODE_POINTS`, and that at least one row is above the maximum.
· **Assessment:** Strong closure of Phase 3 F3. A silently widened maximum would now fail.
· **Recommendation:** None.

### I11 — The T44 AC2 case proves the upper-bound false reject through the DOM
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:307-332` types 128 emoji, asserts `value.length === 256`, `countCodePoints === 128`, `aria-invalid` is null, and the body carries the full string.
· **Assessment:** This is the central falsifiable claim of T44 and it is tested at the right layer (DOM input value, not just unit helper).
· **Recommendation:** None.

### I12 — No production files were modified
· **Severity:** **Positive**
· **Evidence:** The diff is limited to the named test file and the design-system instrument file. `RegisterScreen.tsx` and `passwordLength.ts` are unchanged.
· **Assessment:** This confirms the task's measurement-only nature and respects L12.
· **Recommendation:** None.

---

## Summary

T44's implementation correctly extends T43's table and instruments without touching production code. The self-review found the most important issues: the TR24 harness flake (**I1/F1**), the unfalsified AC4 assertions on `EmailField` and `minLength` (**I2/F4**), the hardcoded field-name scan that cannot cover future primitives (**I3/F3**), and the substring regexes (**I4/F5**). Independent findings add only minor observations: the 129 row could be symmetric with an astral counterpart (**I6**), and the ASCII DOM length is not redundantly asserted (**I7**). The core upper-bound case is strong (**I11**), the table control pins both ends (**I10**), and the honest comment about the surprise astral row is a plus (**I9**).

The highest-priority Phase 9 fixes are the harness reliability (re-run and record multiple times) and adding a `minLength` mutant on `EmailField` to make AC4's negative assertions positive-proof.

**Phase 8 complete.** Twelve findings returned; no code or contract changed.
