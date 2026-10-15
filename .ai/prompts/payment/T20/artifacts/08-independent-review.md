# payment · T20 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 implementation and Phase 7 self-review against the frozen brief (`artifacts/04-frozen-task-brief.md`), `spec/payment-service/agents.md`, and `spec/payment-service/design.md`.

---

## Finding 1 — Malformed rows from raw-SQL tampering can throw instead of returning a typed `Broken`

- **Issue:** `LedgerVerifier.verify()` calls `CanonicalJson.serialize(current.getPayload())` and `current.getPrevHash().equals(...)` without guarding against `null` or unsupported value types. The columns are `NOT NULL`, but the task's own threat model assumes an insider with database-owner privileges who can bypass the application entirely. In that scenario, a row with a `NULL` payload/prevHash or an unsupported JSON value (e.g., a decimal where an integer is expected) would cause a `NullPointerException` or `IllegalArgumentException` rather than the sealed `LedgerVerificationResult.Broken` that R22 requires.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerVerifier.java` lines 67-68 and 76-82; Phase 7 self-review Finding 1 already identifies the null subset of this issue.
- **Recommendation:** Wrap the per-entry verification in a try/catch that returns `Broken(current.getSeq(), "entry could not be verified: " + e.getMessage())` for any unexpected runtime failure, or add explicit null/type guards before serialization/comparison.
- **Confidence:** Medium — the scenario is exactly the "insider alters a historical verification" threat this task is meant to detect.

---

## Finding 2 — No committed tests exercise `LedgerVerifier`

- **Issue:** The implementation notes state that the verification scratch tests were written, run, and deleted before the commit. There is no `LedgerVerifierTest.java` in the repository, and the full suite (405/405) passes unchanged. The new code is therefore unverified by CI.
- **Evidence:** `artifacts/06-implementation-notes.md` lines 19-31; absence of a `LedgerVerifierTest` under `services/payment/src/test/java/com/themistra/payment/receipt/ledger/`.
- **Recommendation:** Phase 10 must add a `LedgerVerifierTest` covering: an intact multi-entry chain returns `Intact()`; empty and genesis-only ledgers return `Intact()`; corrupted `entryHash`; corrupted `prevHash`; corrupted genesis `prevHash`; and a real `seq` gap.
- **Confidence:** High — the code cannot be considered verified without automated tests.

---

## Finding 3 — `Broken` reason strings are duplicated string literals with no compile-time enforcement

- **Issue:** The three reason strings are repeated as inline literals in `LedgerVerifier` and referenced only by convention in `LedgerVerificationResult`. A typo in any literal (or in a test that asserts it) would compile and could silently produce mismatched assertions or inconsistent reporting.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerVerifier.java` lines 70, 78, 82; `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerVerificationResult.java` lines 15-17.
- **Recommendation:** Extract the three reasons as `public static final String` constants on `LedgerVerifier` (or an enum) and reference those constants in both the implementation and tests.
- **Confidence:** Low — the current code is correct, but the shape invites future drift.

---

## Finding 4 — No test asserts the within-entry check order (AC1 before AC2/AC3)

- **Issue:** The frozen brief specifies that AC1 (hash self-consistency) is evaluated before AC2 (linkage) and AC3 (genesis `prevHash`). If an entry fails both AC1 and AC2, the reported reason must be the AC1 reason. No committed or scratch test verifies this ordering.
- **Evidence:** `artifacts/04-frozen-task-brief.md` lines 16-19 and 42-76; `LedgerVerifier.java` lines 67-83 implements the order correctly, but no test asserts it.
- **Recommended test:** Corrupt both the stored `entryHash` and the stored `prevHash` of a non-genesis row via a raw admin `UPDATE`, then assert `verify()` returns `Broken` with the AC1 reason (`"stored entryHash does not match recomputed hash"`).
- **Confidence:** Low — the implementation is correct, but the ordering decision is not regression-protected.

---

## Finding 5 — No committed test exercises the corrected `seq`-gap mechanism

- **Issue:** The `seq`-gap boundary test is listed in the frozen brief and was verified by a scratch test, but no committed test exists. The mechanism is subtle (`SELECT nextval(pg_get_serial_sequence(...))` between two real `append(...)` calls), so a regression that accidentally reintroduced `seq`-arithmetic lookup would not be caught.
- **Evidence:** `artifacts/04-frozen-task-brief.md` lines 90-92; `artifacts/06-implementation-notes.md` lines 27-31; no `LedgerVerifierTest` exists.
- **Recommended test:** In a Testcontainers test, append one entry, advance the identity sequence via `nextval` without inserting a row, append a second entry, and assert `LedgerVerifier.verify()` returns `Intact()`.
- **Confidence:** Medium — the boundary behavior is a load-bearing correctness claim and should be under CI.

---

## Summary

`LedgerVerifier` correctly implements the frozen brief's head-to-tail, positional, stop-at-first-break algorithm. The main risks are (1) malformed rows from the exact insider threat this task models can escape the sealed result type by throwing, and (2) the implementation has no committed test coverage. The highest-priority additions before considering T20 complete are fixing the exception path and adding the test suite.
