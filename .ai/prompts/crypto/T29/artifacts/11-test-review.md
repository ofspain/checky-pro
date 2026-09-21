# crypto · T29 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T29 — Bump spec status |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

---

## Summary

The three T29 tests added to `T01SkeletonRegressionTest` are appropriate regression guards for a documentation-only task: they protect the header bump, the resolution notes on Q1/Q2/Q3/Q7, and the honest disclosure of §9 item 13. The main weaknesses are (1) the item-13 guard can be satisfied even if the checkbox is silently ticked `[x]`, and (2) the date-based resolution guard misses a future Q4/Q5/Q6 resolution with a different date. Otherwise the coverage is proportionate to the task's scope.

---

## Recommendations

### 1. Item 13 guard doesn't verify the checkbox stays `[ ]`

- **Gap:** `item13StaysHonestlyDisclosedAsGenuinelyFailingUntilTheFollowUpLands` only asserts that `package.md` contains the phrase `"genuinely fails"`. It does not assert that §9 item 13 is still `[ ]`.
- **Why it matters:** A future edit could mark item 13 `[x]` while keeping the historical note that it "genuinely fails" — the test would still pass, giving false confidence that the failure is still disclosed.
- **Suggested test:** Parse the single §9 line for item 13 and assert it starts with `- [ ]` and contains `"genuinely fails"`. This forces the follow-up task to update both the checkbox and the test when the build is actually fixed.

### 2. The Q4/Q5/Q6 guard is tied to the specific T29 resolution date

- **Gap:** `resolvedOpenQuestionsCarryTheirResolutionNoteAndUnresolvedOnesDoNotClaimThisTasksResolution` asserts Q4/Q5/Q6 do **not** contain `Resolved (2026-09-21`.
- **Why it matters:** If someone later resolves Q4/Q5/Q6 on a different date (e.g., `Resolved (2026-10-15)`), this test would still pass, even though those questions would no longer be unresolved.
- **Suggested test:** For Q4/Q5/Q6, assert the stronger invariant that the line does not contain any `Resolved (` substring. That matches the intent: these questions are still open and must not carry any resolution note.

### 3. Q1/Q2/Q3/Q7 resolution guard only checks the first line of each bullet

- **Gap:** The `rowStartingWith` helper returns the first line that starts with `- Qn.`, but the resolution notes are long and could be reflowed onto continuation lines in a future edit.
- **Why it matters:** If the `Resolved (2026-09-21` phrase moves to a wrapped continuation line, the test fails even though the note is still present; conversely, if a malicious edit removes the note but keeps the first line, the test passes.
- **Suggested test:** Instead of matching a single line, match the multi-line block from `- Qn.` up to the next `- Q(n+1).` or section boundary, then assert the block contains the resolution date.

### 4. Header guard is brittle to table formatting

- **Gap:** `packageSpecHeaderReflectsReadyForImplAndVersionZeroTwo` asserts exact substrings `| Version | `0.2` |` and `| Status | `READY FOR IMPL` |`.
- **Why it matters:** Minor whitespace or styling changes to the header table will break the build, even though the semantic content is unchanged.
- **Suggested test:** Use a small regex that tolerates extra spaces, e.g., `\\|s*Version\\s*\\|\\s*`0?2`\\s*\\|`. This is a low-cost hardening of the existing assertion.

### 5. No guard that §9 items 1–12 remain `[x]` and item 13 remains `[ ]`

- **Gap:** The tests guard item 13's prose disclosure and the header values, but do not verify that the other 13 checklist items are still checked or that item 13 is the only unchecked one.
- **Why it matters:** A future edit could silently uncheck an item that genuinely passed, or check item 13 prematurely, without failing a test.
- **Suggested test:** Add a single scan of the §9 checklist that counts lines starting with `- [x]` and `- [ ]` and asserts the expected totals (13 `[x]`, 1 `[ ]`), plus the item-13 text guard. This complements rather than replaces the existing tests.

### 6. The tests assume the working directory is the `services/crypto` module root

- **Gap:** All three tests read `Path.of("../../spec/crypto-service/package.md")`. This matches the existing tests in the file, but it is fragile if the test is ever executed from a different base directory.
- **Why it matters:** A CI job that runs tests from the repo root or a different working directory could make these tests fail for environmental reasons.
- **Suggested test:** (Optional) Resolve the path relative to the class's resource location or use a `ClassLoader` resource, so the test is independent of the launch working directory. Keep the existing style if the build already standardizes the working directory.

---

## Confirmations

- The T29 test set is small and proportionate to a spec-status task with no production code changes.
- The tests cover the three things T29 actually changes: the `Version`/`Status` header, the resolution notes on Q1/Q2/Q3/Q7, and the honest disclosure of §9 item 13.
- No named tests from `package.md` §8 are directly relevant to T29; the task's acceptance criteria are met by these regression guards.
