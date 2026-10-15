# payment · T20 · Phase 9 — Review Resolution

**STATUS: resolved** — disposition approved via `AskUserQuestion`, applied below.

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

| # | Source | Comment | Decision | Reason |
|---|--------|---------|----------|--------|
| 1 | Self-review Finding 1, confirmed and widened by Kimi Phase 8 Finding 1 | A malformed row (null or unsupported-type `payload`/`prevHash`) throws instead of returning a typed `Broken` | **ACCEPTED** | Kimi's own Medium-confidence framing ("exactly the insider threat this task models") outweighed my own Phase 7 "not worth the complexity" judgment — the fix is cheap (one try/catch) and makes the contract genuinely total: any failure while checking one entry, of any kind, is itself reported as `Broken`, never left to propagate. Verified via a scratch test (written, run, deleted before this commit): a `NULL` payload now produces `Broken(seq, "entry could not be verified: payload must not be null")` instead of an uncaught `NullPointerException`. |
| 2 | Kimi Phase 8 Finding 2 | No committed tests exercise `LedgerVerifier` at all | **NOTED, Phase 10's own job** | Correctly deferred per the standard rule — this task is not test-only. |
| 3 | Kimi Phase 8 Finding 3 | The three `Broken` reason strings are inline literals with no compile-time enforcement | **ACCEPTED** | Extracted as package-private `static final String` constants on `LedgerVerifier` (`REASON_ENTRY_HASH_MISMATCH`/`REASON_PREV_HASH_MISMATCH`/`REASON_GENESIS_PREV_HASH_INVALID`) — both the implementation and Phase 10's own tests can reference the same constants, closing the typo-drift risk Kimi named. |
| 4 | Kimi Phase 8 Finding 4 | No test asserts the within-entry check order (AC1 before AC2/AC3) | **NOTED, Phase 10's own job** | New test content, deferred per the standard rule. |
| 5 | Kimi Phase 8 Finding 5 | No committed test exercises the corrected `seq`-gap mechanism | **NOTED, Phase 10's own job** | New test content, deferred per the standard rule. |

No finding was rejected.

## Exact changes made

- **`LedgerVerifier.verify()`** — refactored into a `checkEntry(current, priorEntry)` private
  helper returning `Broken` or `null`, called from inside a `try/catch (RuntimeException e)` in
  `verify()`'s own loop; any unexpected failure converts to `Broken(seq, "entry could not be
  verified: " + e.getMessage())` (row 1).
- **`LedgerVerifier`** — the three reason strings extracted as named constants (row 3); class
  Javadoc gained a new paragraph documenting the try/catch's own reasoning.

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`/`test-compile`: clean.
- A throwaway Testcontainers scratch test (written, run, deleted before this commit) confirmed
  the fix empirically: dropping the `NOT NULL` constraint on `payload` via a raw admin
  connection and setting it to `NULL` for a real, previously-correctly-appended entry now
  produces a typed `Broken` result instead of an uncaught exception.
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **405/405 passing**, unchanged
  — no regression, no new test content this phase (Phase 10's own job, per rows 2/4/5).
