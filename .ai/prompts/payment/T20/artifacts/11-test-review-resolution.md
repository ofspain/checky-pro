# payment · T20 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi)

This companion note records the disposition, mirroring T18/T19's own identical precedent.

## Disposition of Kimi's 7 gaps

| # | Gap | Disposition | Reason |
|---|---|---|---|
| 1 | Schema altered by the null-payload test is never restored, leaking into later tests in the class-scoped container | **ACCEPTED** | New `withNullableColumn(column, action)` helper: drops the constraint, runs the test body, then restores it in a `finally` block. Discovered mid-fix that restoring `SET NOT NULL` itself fails if the corrupted row is still present — the `finally` block deletes all rows first, then restores the constraint. |
| 2 | No test for a `NULL` `prevHash` | **ACCEPTED, with a real correction found while writing it** | The test initially asserted the same catch-all reason the null-payload test uses — it failed, revealing a genuine, confirmed fact about the implementation: Java's own `+` string-concatenation operator tolerates a null operand (producing the literal text `"null"`), so AC1's own hash recomputation runs safely and produces a mismatch *before* AC2 would ever reach the `.equals(...)` call that would actually throw on a null `prevHash`. The catch-all handler's real purpose is narrower than this one scenario — it exists for failures string concatenation can't silently paper over (a null payload, an unsupported value type), not a null `prevHash`. Test renamed to `shouldReportEntryHashMismatchNotThrowWhenPrevHashIsNull` and its assertion corrected to match the real, confirmed behavior, not the original assumption. |
| 3 | No test for an unsupported JSON value type in the payload | **ACCEPTED** | New test using a bare decimal value `CanonicalJson` itself rejects. |
| 4 | No test for a deleted middle row (the true-tampering complement to the harmless `seq`-gap test) | **ACCEPTED** | New test proving a real `DELETE` of a committed, previously-correctly-chained row *is* correctly detected as `Broken` — confirms the positional lookup correctly distinguishes "a row was never committed" (harmless) from "a row was removed after being committed" (true tampering), the exact distinction reasoned through at Phase 4 when rejecting Kimi's own earlier, incorrect Phase 3 Finding 7 suggestion. |
| 5 | No test that corrupting the head entry specifically is detected | **ACCEPTED** | New test, ruling out an off-by-one in the loop. |
| 6 | No test for a direct `entry_hash`-only corruption with payload/prevHash both untouched | **ACCEPTED** | New test, isolating AC1 from the named test's own payload-corruption shape. |
| 7 | No assertion that `verify()` is read-only | **ACCEPTED** | New test comparing row count before/after a `verify()` call. |

No finding was rejected.

## Exact changes made

- **`LedgerVerifierTest`** — added `withNullableColumn(...)` (a `finally`-based schema-restore
  helper, closing Gap 1), 6 new test methods (Gaps 2–7), and corrected the Gap 2 test's own
  expected reason after the real, empirically-confirmed behavior diverged from the initial
  assumption.

No production code changed this phase.

## Verification performed this phase

- `mvn -o -pl services/payment -am test -Dtest=LedgerVerifierTest`: **15/15 passing** (9
  Phase-10 baseline + 6 new).
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **420/420 passing**, confirming
  the schema-restoration fix (Gap 1) does not leak into or break any other test in the class or
  the wider suite.
