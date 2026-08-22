<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T39 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T39 — Update auth-decisions.md with Q1/O2-O5 resolutions |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the Phase 10 test manifest for T39. This is a documentation-only task with no runtime tests. The review focuses on whether the documentation deliverable is sufficiently protected against future drift and whether the verification record is complete.

---

## Gap 1 — No automated guard against stale citations in auth-decisions.md

**Why it matters.** D-026 through D-029 cite specific source files and line numbers (e.g., `RateLimitFilter.java:23-26`, `RecoveryCode.java:15-17,39-41`). When the corresponding source code is refactored, these line numbers will become inaccurate. There is no test or CI check that detects stale citations.

**Suggested test.** Add a lightweight CI or pre-commit check that warns when files cited in `auth-decisions.md` are modified, prompting a manual citation refresh. Alternatively, replace line-number citations with method/field names where possible, reducing fragility.

---

## Gap 2 — No test verifies that all `design.md` O-items are tracked in `auth-decisions.md`

**Why it matters.** `spec/auth-service/design.md` §4b lists O1-O5. O1 is D-025, O2 is D-026, O3 is D-027, O4 is D-028, O5 is D-029. Today this is complete, but a future O-item could be added to `design.md` without a corresponding decision-log entry. There is no test enforcing this traceability.

**Suggested test.** Add a documentation-traceability test (or manual checklist) that parses `design.md` §4b for `O\d+` items and asserts each either has a resolved D-xxx entry or an explicit unresolved note in `auth-decisions.md`. This is most easily a PR-checklist item or a static-analysis script, not a JUnit test.

---

## Gap 3 — `design.md` O3 remains open while `auth-decisions.md` records it as unresolved

**Why it matters.** T39's scope explicitly excludes editing `spec/auth-service/design.md`. This is correct, but it leaves an inconsistency: `design.md` still presents O3 as an open decision awaiting a proposal, while `auth-decisions.md` now records that it was never decided. A future reader may see `design.md` first and not realize `auth-decisions.md` has the honest status.

**Suggested test/action.** Since `design.md` cannot be modified in this task, add a cross-reference in D-027 pointing back to `design.md` §4b O3, e.g.:

> "See `spec/auth-service/design.md` §4b O3 for the original framing; this entry records that none of the three options was ever selected."

This makes the relationship explicit.

---

## Gap 4 — No test verifies the D-xxx sequence is contiguous

**Why it matters.** D-025 through D-029 are contiguous, but a future entry could be inserted out of sequence or numbered incorrectly during a manual edit.

**Suggested test.** Add a simple script or PR checklist that scans `auth-decisions.md` for `## D-\d+` headings and verifies monotonic numbering. This is low-value but cheap.

---

## Gap 5 — No automated verification that D-025 still adequately covers Q1/O1

**Why it matters.** AC1 requires confirming Q1 is already resolved and recorded as D-025. The Phase 10 manifest states this was confirmed, but there is no test that D-025's content still matches the current implementation (e.g., that `MfaSeedEncryption` still uses KMS envelope encryption).

**Suggested test.** This is already covered indirectly by the MFA integration tests and the compile-time dependency on AWS KMS. No new test is needed, but the verification record should note that D-025's claims are alive-code-corroborated, not just document-to-document.

---

## Gap 6 — The decision-log format does not include a verdict line for the new entries

**Why it matters.** The document header specifies "Accept/Modify/Reject Reason" as part of the format. Existing entries sometimes include this as "Reference influence / verdict"; the four new entries only have "Reference influence." This is consistent with most entries, but not with the header.

**Suggested test/action.** This is a formatting choice, not a correctness issue. If the project wants strict compliance, add a verdict line to D-026 through D-029 (e.g., "Verdict: new decision, no reference-project precedent to accept or reject"). Otherwise, update the header to reflect that "Reference influence" subsumes the verdict when no reference project applies.

---

## Summary

T39 correctly identifies itself as a no-test task. The deliverable is complete and accurate after Phase 9's corrections. The gaps are all about long-term documentation hygiene: preventing stale citations, ensuring traceability between `design.md` and `auth-decisions.md`, and minor format consistency. None of these are blockers for T39.

(End of Phase 11 test review.)
