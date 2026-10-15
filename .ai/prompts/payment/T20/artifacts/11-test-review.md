# payment · T20 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the actual `LedgerVerifierTest` against the frozen brief's acceptance criteria and the named test.

---

## Gap 1 — `shouldReportBrokenNotThrowWhenPayloadIsNull` leaves the table schema altered

- **Why it matters:** The test drops the `payload NOT NULL` constraint to insert a `NULL` payload, but it never re-adds the constraint in a `finally` or `@AfterEach`. The container is class-scoped, so every subsequent test in this class runs against a `ledger_entries` table whose `payload` column is nullable. A future test that relies on the baseline schema (or a test that runs in a different order) could behave differently or pass for the wrong reason.
- **Suggested test:** Either restore the constraint in a `finally` block after the assertion, or use a separate test-scoped mechanism (e.g., a dedicated `@Nested` class with its own container, or a raw `INSERT` with `OVERRIDING SYSTEM VALUE` after dropping and immediately re-adding the constraint within the same test).

---

## Gap 2 — No test for a `NULL` `prevHash`

- **Why it matters:** The Phase 8/9 finding that motivated the catch-all handler mentions both `payload` and `prevHash` as columns an insider could set to `NULL`. The current tests only exercise the `payload` case. A regression that accidentally removed the `RuntimeException` catch or changed the check order could allow a `NULL` `prevHash` to throw instead of returning `Broken`.
- **Suggested test:** Mirror `shouldReportBrokenNotThrowWhenPayloadIsNull`: drop the `prev_hash NOT NULL` constraint, set a row's `prev_hash` to `NULL`, and assert `verify()` returns `Broken` with a reason containing `"entry could not be verified"`.

---

## Gap 3 — No test for an unsupported JSON value type in the payload

- **Why it matters:** The catch-all handler also covers values that `CanonicalJson.serialize` rejects (e.g., a `Double` deserialized from a malformed JSON number). This is a distinct failure mode from `NULL` payload and confirms the handler is truly general, not just a null check.
- **Suggested test:** Use a raw admin `UPDATE` to set `payload = '{"amount": 1.5}'` (a decimal where the real canonicalizer expects a string) on one row, then assert `verify()` returns `Broken` with a reason containing `"entry could not be verified"`.

---

## Gap 4 — No test for a deleted middle row

- **Why it matters:** The positional prior-entry lookup must detect a true insider deletion, not just a harmless rolled-back-transaction `seq` gap. The `seq`-gap test only advances the sequence; it never removes a committed row that the chain actually references.
- **Suggested test:** Append three entries, delete the middle entry via a raw admin `DELETE`, and assert `verify()` returns `Broken` because the third entry's `prevHash` no longer matches the positionally prior row's stored `entryHash`.

---

## Gap 5 — No test that corrupting the head entry is detected

- **Why it matters:** The named test corrupts a middle entry. A bug that accidentally skipped the head entry during verification (e.g., an off-by-one in the loop or the genesis check) would still pass the named test.
- **Suggested test:** Append three entries and corrupt the head (highest `seq`) entry's `payload`. Assert `verify()` returns `Broken` with the head's `seq` and `REASON_ENTRY_HASH_MISMATCH`.

---

## Gap 6 — No test for `entry_hash`-only corruption

- **Why it matters:** The named test corrupts `payload`, which indirectly causes `entryHash` mismatch. A direct corruption of only the stored `entry_hash` column isolates AC1 from AC2 and proves the verifier detects a tampered hash even when the payload and `prevHash` are internally consistent.
- **Suggested test:** Append two entries, leave `payload` and `prev_hash` untouched, and set `entry_hash = '00' * 32` (or any 64-hex value that is not the recomputed hash) on the second entry. Assert `Broken` with `REASON_ENTRY_HASH_MISMATCH`.

---

## Gap 7 — No assertion that `LedgerVerifier.verify()` is read-only

- **Why it matters:** The task is explicitly read-only, but none of the tests verify that calling `verify()` does not mutate the ledger (e.g., by asserting the row count before and after).
- **Suggested test:** In any existing Testcontainers test, query the row count in `ledger_entries` before and after `verifier.verify()` and assert they are equal.

---

## Summary

The Phase 10 test suite covers the central R22 scenarios well: intact chains, corrupted `entryHash`, corrupted `prevHash`, corrupted genesis, `seq` gaps, and the AC1-before-AC2 ordering. The most important additions are restoring the schema after the null-payload test (Gap 1), testing the symmetric `NULL prevHash` case (Gap 2), and proving detection of a real deleted row (Gap 4). Gaps 5–7 are lower-priority but would make the suite more robust against regressions.
