<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T46 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T46 — Code points, not UTF-16 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

Commands run independently on the tree as delivered by Phase 7. No code or contracts were changed.

| Command | Result |
|---|---|
| `vitest run src/features/account/passwordLengthCountsCodePoints.test.ts` | **25 / 25 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1989 / 1990 passed** — 1 known environmental `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T46/artifacts/07-mutation-harness.py` | **49/49 regressions killed**, 4 canaries killed, 2 expected survivors (TR24, TR50), **0 broken**, **0 disagreements** |

The named suite count and whole-suite count match the self-review. The harness completed cleanly with no disagreements, confirming the stale-prediction fix from Phase 7 held in a fresh run.

---

## Findings

### I1 — The AC6 environment-scan control uses a count threshold, not a category check
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts:218-231` builds a synthetic impure module and asserts `fired.length >= 5`. The self-review reports the synthetic module triggers exactly five of the twelve patterns.
· **Assessment:** Confirmed independently. A threshold says "enough patterns work" when the claim is "each category of reach is visible" (storage, clock, console, DOM, import). If someone adds a sixth matching line, the threshold still passes but can no longer detect that one of the original five has broken. The control should assert the fired set includes at least one pattern from each category by name.
· **Recommendation:** Same as self-review F4: replace the count assertion with named category checks, e.g., assert the fired patterns include one storage match, one clock match, one console match, one DOM match, and one import match.

### I2 — The long-value comment claims a performance property the test does not measure
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts:180-182` asserts `countCodePoints('a'.repeat(100_000)) === 100_000` under the comment *"A LONG VALUE COSTS ONE PASS."*
· **Assessment:** Confirmed. The case measures correctness at scale, not pass count or duration. A timing assertion would be flaky on a shared runner, and the implementation's algorithmic complexity is not observable from the test. The comment overstates the claim in exactly the way T45's phase 12 caught.
· **Recommendation:** Same as self-review F5: reword the comment to state what is measured — the function returns the right answer for a 100,000-character value without hanging or overflowing. Drop the "one pass" claim or attribute it to the implementation rather than the test.

### I3 — The table-region regex for the circularity scan is fragile to formatting
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts:130` uses `/const CASES[\s\S]*?\n\];/` to extract the table region.
· **Assessment:** If a future edit renames the constant, moves the closing `];` to the same line as the last row, or adds a trailing comment after `];`, the regex will fail and the control will report "the table region could not be found". The failure is loud, which is good, but the regex is tighter than necessary.
· **Recommendation:** Optional: make the control more robust by scanning the whole file for the forbidden patterns and whitelisting the source-scan cases below, or by asserting the file contains no `[...` outside of comments. Either avoids coupling the control to the table's exact formatting.

### I4 — The harness inherited stale predictions from T45, causing 44 disagreements in run 1
· **Severity:** **Major**
· **Evidence:** The self-review reports run 1 had 44 disagreements, all "killed only OUTSIDE this task's files", because `OWN_TESTS` was set to the single file T46 wrote while the copied harness listed three files from T45.
· **Assessment:** Confirmed by reading `07-mutation-harness.py:81` and the self-review. Copying a harness copies its rows, anchors, and predictions; only the predictions are task-relative. This is the same shape as T45's TR21, multiplied across 42 inherited rows. The self-review says it was fixed by re-deriving predictions from `OWN_TESTS` using AST parsing.
· **Recommendation:** Same as self-review F1/F2: ensure the prediction-derivation logic is robust. The self-review notes that the first fix shared a regex bug with its verification; the final fix uses AST parsing. Phase 9 should verify the disagreement list is empty in a fresh run.

### I5 — The harness's revert is not crash-safe
· **Severity:** **Major**
· **Evidence:** The self-review reports that a killed background shell left `passwordLength.ts` mutated (TR55: `ADVISORY_MINIMUM_CODE_POINTS = 11`). `git status` caught it, and a manual checkout restored it.
· **Assessment:** A crash between mutation and revert leaves production source modified. The standing "commit before mutating" rule made the diff legible, but a leftover from an unfalsifiable mutant (TR24/TR50) would pass the baseline guard and could be committed by the next `git add`. This is a real repository-integrity risk.
· **Recommendation:** Same as self-review F3: wrap mutate/run/revert in `try/finally`, and add a startup guard that refuses to run when `git status --porcelain` over `REVERT_PATHS` is non-empty. The startup guard is the cheaper fix and catches the invisible case.

### I6 — TR50 (`Array.from(value).length`) is a meaningful expected survivor
· **Severity:** **Positive**
· **Evidence:** The self-review reports TR50 survived both runs. The harness marks it as expected unfalsifiable.
· **Assessment:** This is the right design. `Array.from` iterates code points exactly as the spread does, so a suite that killed it would obstruct legitimate refactoring. Its survival proves the suite pins the unit, not the syntax.
· **Recommendation:** None.

### I7 — TR52 (normalization before counting) makes OQ-T46-2 falsifiable
· **Severity:** **Positive**
· **Evidence:** The self-review reports TR52 is killed by exactly one case: `counts a decomposed accent as code points, not UTF-16 units`.
· **Assessment:** The open question about normalization is now held by a test, not just a comment. Whether the owner chooses to normalize or not, the decision will change a measurable outcome.
· **Recommendation:** None.

### I8 — The non-additive surrogate case is a strong adversarial addition
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts:185-197` asserts that two lone surrogates (`\uD83D` + `\uDC4D`) combine to one code point, while each half alone is one code point.
· **Assessment:** This catches any future implementation that counts incrementally over chunked input and would double-count a split surrogate pair. It is the kind of case that passes every other row but fails on a real distributed-system hazard.
· **Recommendation:** None.

### I9 — The table control is deliberately coupled to the constants
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts:115-121` uses `ADVISORY_MINIMUM_CODE_POINTS` and `ADVISORY_MAXIMUM_CODE_POINTS` in the crossing clauses.
· **Assessment:** This is correct. If R1's bounds move, the control will fail until the table gains a row that discriminates at the new bound. Literal 12/128 would let the bounds drift undetected.
· **Recommendation:** None beyond the self-review's suggestion to make the coupling explicit in the comment.

### I10 — `typeof countCodePoints === 'function'` has no mutant and is held by the type system
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts:249` asserts `typeof countCodePoints` is `'function'`.
· **Assessment:** Confirmed. The only mutant that could falsify this (exporting the name as a non-function) would fail TypeScript compilation. The assertion is harmless documentation but not a live instrument.
· **Recommendation:** Same as self-review F10: keep it, but note in the comment that the type system holds this one.

### I11 — The harness is large because it re-aimed T45's 48 rows
· **Severity:** **Minor / observation**
· **Evidence:** The self-review reports 49 regressions, 4 canaries, and 2 survivors. Many rows (TR01-TR23, TR25-TR35) are inherited from earlier tasks and test behavior in screen suites, not directly in `countCodePoints`.
· **Assessment:** This is the cost of copying a harness. The inherited rows provide regression coverage for the screens, but the task's central claim is held by a much smaller set of rows (TR05, TR47, TR52, TR55, etc.). The harness is correct but heavyweight for a pure function.
· **Recommendation:** None required for this task. For future tasks, consider whether a smaller harness focused on the actual subject would be clearer.

---

## Summary

T46's suite correctly makes R1's counting unit a property of `countCodePoints` itself, not just of the screens that consume it. The central measurement is visible in TR05's attribution: the mutant task 43 wrote now dies in this suite. Phase 10 added the right controls: the table can tell the two units apart, the circularity guard prevents self-referential expectations, and the non-additive surrogate case catches incremental counting hazards.

The main Phase 9 actions are machinery fixes: the AC6 control should use set-based category checks (**I1**), the long-value comment should not claim an unmeasured performance property (**I2**), and the harness's crash-safety should be improved (**I5**). The stale-prediction issue was fixed during Phase 7 but should be verified with a clean run (**I4**). The remaining issues are small: a fragile table-region regex (**I3**) and an unfalsified type assertion (**I10**).

**Phase 8 complete.** Eleven findings returned; no code or contract changed.
