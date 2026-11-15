<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# frontend · T46 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T46 — Code points, not UTF-16 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/passwordLengthCountsCodePoints.test.ts` | **27 / 27 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1991 / 1992 passed** — 1 known environmental `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T46/artifacts/07-mutation-harness.py` | **52 / 52 regressions killed**, 4 canaries killed, 2 expected survivors (TR24, TR50), **0 broken**, **0 disagreements** |

## Phase 10 changes consumed

Phase 10 added two cases and one table-control clause:

1. Case 21: `is strictly between the two units it is confused with, measured by a real segmenter` — asserts the ordering `graphemes < codePoints < utf16Units` using `Intl.Segmenter` as an independent witness.
2. Case 24: `counts k astral code points and m ASCII as k + m, for every k and m up to five` — a property over 36 generated strings, with both expectations stated arithmetically.
3. Clause in case 16: `no two rows hold the same string` — prevents duplicated rows from inflating the table's apparent coverage.

Phase 10 also fixed the three literal U+200D joiners, addressed Phase 8's I1 (AC6 category set check) and I2 (overstated performance comment), and added three harness rows (TR57–TR59).

## Findings

### F1 — The harness is not crash-safe against SIGTERM
· **Severity:** **Major**
· **Evidence:** The first harness run was launched in the background. While it was still mutating files, the foreground whole-suite run showed 46 failures; `git status --short` revealed `frontend/src/features/account/RegisterScreen.tsx` modified with the TR07 hand-written `fetch` mutant. The background process was then killed with SIGTERM, after which `git status` showed `frontend/src/app/ui/Form.tsx` also mutated. A subsequent foreground harness run completed cleanly, killed 52/52 regressions, and left `git status` clean.
· **Assessment:** This is the crash-safe revert hazard documented in Phase 8 (I5) and Phase 10. Phase 10 added a `dirty()` startup guard, but that guard only protects against a dirty tree at startup; it does not protect against the harness itself being killed mid-run. Because `revert()` happens only after all rows finish, a SIGTERM (or any crash between mutate and revert) leaves production source modified. The named T46 suite would still pass, but the suite-wide baseline would be corrupted.
· **Recommendation:** Wrap each mutant's mutate/run/revert in a `try/finally` (or per-mutant restore) so a killed process leaves at most the one currently active mutation, and that mutation is reverted before the process exits. Alternatively, record mutation backups and install a signal handler that restores them. A startup `dirty()` guard is necessary but not sufficient.

### F2 — The AC6 environment-control sample does not exercise every pattern category
· **Severity:** **Minor / Major**
· **Evidence:** `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts:305-323` builds a synthetic impure module and asserts the fired categories equal `['clock', 'console', 'dom', 'import', 'storage']`. The `ENVIRONMENT` array defines seven categories: `storage`, `dom`, `network`, `clock`, `console`, `platform`, `import`. The sample contains no `fetch`, `crypto`, or `process` usage.
· **Assessment:** The test's own comment says it proves "every KIND of reach is visible", but two kinds (`network` and `platform`) are never shown to be visible. If `/\bfetch\s*\(/`, `/\bcrypto\b/`, or `/\bprocess\b/` were broken, this control would not catch them, and the first AC6 test would not catch them either because `passwordLength.ts` does not use those APIs.
· **Recommendation:** Extend the synthetic impure module with one line each for `fetch('/x')`, `crypto.getRandomValues(...)`, and `process.env.X`, and assert the fired category set includes `network` and `platform` alongside the existing five.

### F3 — The circularity scan still couples its success to the table's formatting
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts:139` extracts the table region with `/const CASES[\s\S]*?\n\];/`. Phase 10 added a row-count guard that mitigates the fragility but does not remove it.
· **Assessment:** The guard makes a partial-match failure loud, which is good. A future edit that puts the closing `];` on the same line as the last row, or adds a trailing comment after `]`, will still break the regex. The control is better than in Phase 8 but still punctuation-sensitive.
· **Recommendation:** Optional: replace the regex with a parser-aware scan, or extract the region by locating the `CASES` identifier and the first subsequent `];` across balanced braces. Not urgent if the row-count guard is kept.

### F4 — `Intl.Segmenter` is assumed present without a runtime guard
· **Severity:** **Minor**
· **Evidence:** Case 21 calls `new Intl.Segmenter('en', { granularity: 'grapheme' })` directly. The comment notes it is available in this runtime and unused in `src`.
· **Assessment:** Node 16+ and modern browsers support `Intl.Segmenter`, so the test is safe in the current CI environment. If the test matrix is ever widened to an older Node or a minimal jsdom, this case will fail with a `ReferenceError` rather than a clear skip message.
· **Recommendation:** Optional: add a guard that skips the segmenter case when `Intl.Segmenter` is absent, or assert its presence as a test assumption. This keeps the case informative on capable runtimes without breaking on older ones.

### F5 — The composition property (case 24) still has no mutant of its own
· **Severity:** **Observation**
· **Evidence:** The case comment explicitly states it has no mutant in the harness. Phase 10's TR57–TR59 do not target it.
· **Assessment:** Phase 10's reasoning is sound: every implementation the case would kill is already killed by a named row. Adding a contrived mutant would be worse than honest documentation. The case is weight-bearing as a law statement, not as a mutant killer.
· **Recommendation:** None.

### F6 — The three new harness rows (TR57–TR59) are well-isolated
· **Severity:** **Positive**
· **Evidence:** Phase 10 reports TR58 and TR59 each died in exactly one case, and TR57 died on expectations that predate the new segmenter case.
· **Assessment:** This is the right shape for phase-10 additions. TR58 justifies the segmenter case; TR59 justifies the uniqueness clause; TR57 documents that the existing table already catches a grapheme-count implementation.
· **Recommendation:** None.

### F7 — Phase 8 findings I1 and I2 are resolved in Phase 10
· **Severity:** **Positive**
· **Evidence:** AC6 now asserts fired categories by name, not by count, and the long-value comment no longer claims a pass count.
· **Assessment:** Phase 10 closed both Phase 8 issues cleanly.
· **Recommendation:** None.

---

## Summary

T46's test file is in good shape after Phase 10. The new segmenter case and the composition property close real holes: the order of the three units was previously unmeasured, and the table was a sample whose underlying law was not stated. The uniqueness clause prevents a duplicate row from inflating coverage.

Final verification numbers are clean: the named suite passes **27/27**, the whole suite passes **1991/1992** (the one known environmental lint failure), and the harness kills **52/52 regressions** with **0 broken** and **0 disagreements**.

The outstanding Phase 11 concerns are procedural and instrumental:

- **F1** is the most serious: the harness is not crash-safe against SIGTERM. A mid-run kill leaves production source mutated. This was observed directly during this review when the background run was terminated.
- **F2** leaves the AC6 control short of its own claim: two pattern categories (`network`, `platform`) are not validated by the synthetic impure module.
- **F3** and **F4** are minor robustness issues (table-region regex, `Intl.Segmenter` availability).

No production or test code was changed during this review. The only file modified is this artifact.

**Phase 11 complete.**
