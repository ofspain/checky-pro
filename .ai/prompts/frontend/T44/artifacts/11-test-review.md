<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T44 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T44 — Registration accepts 128 characters |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md`, `artifacts/10-mutation-run.txt` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | **45 / 45 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1945 / 1946 passed** — 1 known environmental `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T44/artifacts/07-mutation-harness.py` | **32/32 regressions killed**, 2 canaries killed, **1 expected survivor** (TR24), **0 broken**, **0 disagreements** |

The named suite count, whole-suite count, and harness outcome all match the Phase 10 manifest exactly.

---

## Findings

### F1 — The length-cap source scan now covers every primitive and carries a control
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:420-447` loops over `PRIMITIVE_FILES` (not a hardcoded pair) and asserts no primitive contains `maxLength` or `minLength`. `:449-467` adds a control proving the scan can see caps in three realistic shapes (JSX attribute, spread, forwarded prop) and does not match an uncapped file.
· **Assessment:** This closes Phase 8 I3 (hardcoded field names) and Phase 8 I2/I4 (unfalsified negative assertions / substring breadth) in one change. The comment honestly explains why the broad regex is deliberate.
· **Recommendation:** None.

### F2 — The rendered AC4 case now carries a positive control for `hasAttribute`
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:354-369` creates a probe input, sets `maxlength`/`minlength`, and asserts `hasAttribute` returns true; it also asserts `hasAttribute('maxlenght')` returns false.
· **Assessment:** This closes the other half of Phase 8 I2. The control also corrects the earlier misconception that casing was the risk — `hasAttribute` is ASCII-case-insensitive, so the real risk is a simply wrong name.
· **Recommendation:** None.

### F3 — TR35 was added to falsify the `minLength`/`EmailField` assertions
· **Severity:** **Positive**
· **Evidence:** `.ai/prompts/frontend/T44/artifacts/07-mutation-harness.py:416-417` defines `TR35 EmailField floors its own length at 12 UTF-16 units, which is R1's bound on the wrong field`.
· **Assessment:** This directly addresses Phase 8 I2. Combined with TR32 (`maxLength` on `PasswordField`), every assertion in both AC4 pins now has a mutant aimed at it.
· **Recommendation:** None.

### F4 — The `PRIMITIVE_FILES` scan is a broadening, not a narrowing
· **Severity:** **Minor / observation**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:440-446` now applies the `maxLength`/`minLength` check to all 13 primitives, including `Form`, `SubmitButton`, `TextLink`, etc.
· **Assessment:** This is correct for the stated hazard: no primitive should cap input length because the attribute counts the wrong unit. It does mean the scan will fail if a non-field primitive ever legitimately needs `maxLength` (e.g., a future `TextAreaField` with a different purpose), but that is the desired signal — such a change should be justified.
· **Recommendation:** None.

### F5 — The 129-astral row is still absent
· **Severity:** **Minor**
· **Evidence:** The `PASSWORDS` table has a 129 ASCII row but no 129-astral row.
· **Assessment:** The advisory check compares code points, so 129 ASCII and 129 emoji are both out of range. The 129 ASCII row is sufficient to falsify a widened maximum. An astral 129 row would add symmetry and a second chance to catch a `.length`-based upper comparison, but it is not required by the brief.
· **Recommendation:** Optional: add `{ password: '👍'.repeat(129), codePoints: 129, inRange: false }` for symmetry. No structural change needed.

### F6 — No case asserts the 128-ASCII password's DOM value length is 128
· **Severity:** **Minor**
· **Evidence:** The table-driven case asserts `countCodePoints(password) === codePoints` but not `passwordInput(container).value.length === codePoints`. For the 128 ASCII row, code points equal UTF-16 units.
· **Assessment:** A `.length`-based check would pass ASCII rows at both ends, so this is not a falsifiable claim for 128 ASCII. The 128-astral case and the table's astral rows catch the false reject. Not a defect.
· **Recommendation:** None.

### F7 — This harness run was clean, but the machinery has shown flakiness before
· **Severity:** **Minor/Major**
· **Evidence:** This run: **32/32 regressions killed**, 0 broken, 0 disagreements. Phase 8 I1 documented non-deterministic outcomes (TR18/TR20 BROKEN in one run, killed in another; the self-review's TR24 flake).
· **Assessment:** Phase 10 did not change the harness machinery; it only added rows TR29-TR35. This run was stable, but the machinery has previously produced false BROKEN and false DISAGREES verdicts. One clean run does not erase that history.
· **Recommendation:** For closure, re-run the harness at least once more and report both runs. Any BROKEN or DISAGREES row in any run must be re-run in isolation before acceptance.

### F8 — The `PASSWORDS` table comment still documents the 256-unit correction
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:247-252` notes that 128 astral code points are 256 UTF-16 units, correcting the earlier 512-unit prediction.
· **Assessment:** Honest record of measurement correcting a design assumption.
· **Recommendation:** None.

### F9 — The table control case pins both bounds
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:404-418` asserts rows on both `ADVISORY_MINIMUM_CODE_POINTS` and `ADVISORY_MAXIMUM_CODE_POINTS`, plus a row above the maximum.
· **Assessment:** Strong boundary coverage. A silently widened maximum now fails.
· **Recommendation:** None.

### F10 — AC1-AC5 are all mapped to tests and mutants
· **Severity:** **Positive**
· **Evidence:** Phase 10 manifest maps every acceptance criterion to the test(s) and mutant(s) that hold it live.
· **Assessment:** This is the documentation shape that makes the suite auditable. Every claim has a falsification mechanism.
· **Recommendation:** None.

### F11 — No production code was changed
· **Severity:** **Positive**
· **Evidence:** The diff is limited to test files and the harness script.
· **Assessment:** Confirms T44 as a measurement task. L12 is respected.
· **Recommendation:** None.

---

## Summary

Phase 10 closed all the major holes from Phases 7–8: the length-cap scan now covers every primitive and carries a control (**F1**), the rendered AC4 case has a positive control for `hasAttribute` (**F2**), and TR35 falsifies the `EmailField`/`minLength` assertions (**F3**). The remaining concerns are minor: the optional 129-astral row (**F5**), the already-documented harness flakiness (**F7**), and the absence of a redundant DOM-length assertion for ASCII rows (**F6**). The acceptance criteria are fully mapped to tests and mutants (**F10**), and no production code was touched (**F11**).

This harness run was clean (32/32 killed, 0 broken, 0 disagreements), matching the Phase 10 manifest. Because the same machinery produced flaky verdicts in Phase 8, one more clean run would strengthen closure.

**Phase 11 complete.** Eleven findings returned; no code changed.
