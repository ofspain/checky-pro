# payment · T20 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/payment-service/agents.md`, `spec/payment-service/design.md` (L7), and the existing T19 ledger implementation.

---

## Finding 1 — `LedgerEntryRepository` read method is unspecified

- **Issue:** The TIB says "add a method returning every entry ordered head-first" but does not name it, specify its return type, or pin the sort order. An implementer could choose a method name or sort direction that the rest of the brief assumes but never states, making tests brittle and the interface ambiguous.
- **Severity:** Medium — directly affects the `LedgerVerifier` implementation and the repository contract.
- **Evidence:** TIB Scope ("new `LedgerEntryRepository` read method") and Files to Modify; no signature appears in Inputs/Outputs.
- **Recommended brief amendment:** Freeze the method as `List<LedgerEntry> findAllByOrderBySeqDesc()` (Spring Data derived query, head-first) or, if using `@Query`, `SELECT e FROM LedgerEntry e ORDER BY e.seq DESC`.

---

## Finding 2 — `LedgerVerifier.verify()` method signature is not frozen

- **Issue:** The TIB repeatedly refers to "`LedgerVerifier`'s own verification method" but never gives it a name or signature. Without this, acceptance criteria AC1–AC5 cannot be tested directly.
- **Severity:** Medium.
- **Evidence:** TIB Scope/Outputs/Acceptance Criteria.
- **Recommended brief amendment:** Add: `public LedgerVerificationResult verify()` takes no parameters and returns `LedgerVerificationResult`.

---

## Finding 3 — `Broken` reason strings are not specified

- **Issue:** `LedgerVerificationResult.Broken(Long seq, String reason)` leaves the reason values open. The named test `shouldDetectBrokenHashChainOnVerification` and the boundary tests for `entryHash`-only vs `prevHash`-only corruption need stable, predictable reasons to assert on.
- **Severity:** Medium — untestable as written.
- **Evidence:** TIB Outputs and Required Tests.
- **Recommended brief amendment:** Enumerate the three reasons, e.g.:
  - `"stored entryHash does not match recomputed hash"`
  - `"stored prevHash does not match prior entry's stored entryHash"`
  - `"genesis prevHash is not 64 zeros"`

---

## Finding 4 — Order of checks within a single entry is unspecified

- **Issue:** AC4 says verification stops at the first entry (head-to-tail) that fails AC1, AC2, or AC3, but it does not say which check is evaluated first when an entry could fail more than one. For a corrupted genesis entry, both AC1 and AC3 could fail; the reported reason depends on the evaluation order.
- **Severity:** Low.
- **Evidence:** TIB Acceptance Criteria AC4.
- **Recommended brief amendment:** State the within-entry evaluation order: AC1 first, then AC2, then AC3 (for the tail entry).

---

## Finding 5 — `LedgerVerifier` Spring bean status is not specified

- **Issue:** The TIB says `LedgerVerifier` is "stateless beyond its injected `LedgerEntryRepository`, consistent with every other service class," but also says there is no runtime caller. It is unclear whether it should be a `@Component`/`@Service` Spring bean, a plain class, or package-private.
- **Severity:** Low — ArchUnit and test wiring depend on the choice.
- **Evidence:** TIB Constraints; T19 `LedgerService.java` is a `@Component`.
- **Recommended brief amendment:** State that `LedgerVerifier` is a public `@Component` with constructor-injected `LedgerEntryRepository`, mirroring `LedgerService`.

---

## Finding 6 — No statement about consistency with concurrent `LedgerService.append` calls

- **Issue:** `LedgerVerifier` reads the whole ledger without acquiring the advisory lock or opening a transaction. It therefore verifies a point-in-time snapshot of committed entries, not the absolute latest head if an append is in flight. This is a reasonable read-only behavior, but it is unstated and could be mistaken for a live barrier.
- **Severity:** Low — could mislead future callers.
- **Evidence:** TIB Constraints ("read-only; no transaction boundary concern at all").
- **Recommended brief amendment:** Add: "`verify()` reflects the committed ledger state at query-start time; it does not synchronize with concurrent appends and should be treated as a point-in-time audit, not a live stop-the-world check."

---

## Finding 7 — Guidance for simulating a `seq` gap is missing

- **Issue:** The TIB lists a boundary test proving that a `seq` gap does not cause a false `Broken` report. Because `seq` is `GENERATED ALWAYS AS IDENTITY`, creating a real gap in tests requires either deleting a middle row or inserting with `OVERRIDING SYSTEM VALUE` via an admin connection. This is non-obvious.
- **Severity:** Low — important for the boundary test but not for the core implementation.
- **Evidence:** TIB Required Tests boundary list; T19 `V1__payments_baseline.sql` (`ledger_entries.seq BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`).
- **Recommended brief amendment:** Add implementation note: "Simulate a positional gap by deleting a middle entry via the admin JDBC connection (allowed only in tests), then verify the remaining chain still reports `Intact()` because linkage is positional, not `seq`-arithmetic."

---

## Summary of recommended brief amendments to fold in Phase 4

1. Freeze the repository read method name/signature (`findAllByOrderBySeqDesc`).
2. Freeze the `LedgerVerifier.verify()` signature.
3. Specify the three `Broken` reason strings.
4. Specify within-entry check order (AC1 → AC2 → AC3).
5. State that `LedgerVerifier` is a public `@Component`.
6. Document the point-in-time snapshot semantics for concurrent appends.
7. Explain how to create a `seq` gap in the boundary test.
