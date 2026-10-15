# payment · T17 · Phase 9 — Review Resolution

**STATUS: resolved** — disposition approved via `AskUserQuestion`, applied below.

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

| # | Source | Comment | Decision | Reason |
|---|--------|---------|----------|--------|
| 1 | Self-review Finding 1, confirmed by Kimi Phase 8 | `handleBlocked`'s own catch only guards `IllegalStateException`, not a real DB failure from its two callees | **ACCEPTED** | Widened to `RuntimeException`. A real `DataAccessException`/similar would otherwise propagate uncaught, with no retry benefit anyway (`dedupe()` already committed). |
| 2 | Self-review Finding 2, confirmed by Kimi Phase 8 | The disclosed "no retry" gap doesn't name the crash-before-`issueIfEligible` sub-case | **ACCEPTED** | Added the crash-window sentence to `ReceiptService`'s class Javadoc, mirroring `onChainTxReorged`'s own existing precedent. |
| 3 | Kimi Phase 8 New Finding 1 | No `ReceiptService` unit tests, no `ChainEventConsumer` wiring assertions for T17 | **NOTED, Phase 10's own job** | Correctly deferred per the standard rule — this task is not test-only. |
| 4 | Kimi Phase 8 New Finding 2 | `Map.of("blockedReason", blockedReason)` throws `NullPointerException` if the Crypto Service returns `BLOCKED` with a null `reason` (a legitimate wire response) | **ACCEPTED — a real bug, confirmed** | The method's own `Objects.requireNonNull(blockedReason, ...)` guard was actually the first thing to throw (same root bug, different throw-site than Kimi named). Removed that guard; substitutes `"(no reason provided)"` before building the detail map, never passing a possible null into it. |
| 5 | Kimi Phase 8 New Finding 3 | `ChainEventConsumer`'s class-level Javadoc still claims only `onChainTxReorged` is non-`@Transactional` | **ACCEPTED** | Updated to name both `onChainTxReorged` and `onChainTxFinalized`. |
| 6 | Kimi Phase 8 New Finding 4 | `onChainTxFinalized` discards the `ReceiptIssueResult` it receives — no consumer-level observability | **ACCEPTED** | Added an `INFO` log line at the call site. Fixing this surfaced a real, unrelated test gap (below). |
| 7 | Kimi Phase 8 New Finding 5 | `PaymentRepository.markHeldIfFinalized` uses string literals (`'HELD'`/`'FINALIZED'`) instead of binding `PaymentState` enum parameters | **ACCEPTED — for a stronger reason than Kimi gave** | Kimi's own framing cited `InvoiceRepository.updateWatchIdIfOpen`'s identical literal style as precedent and called this optional. Re-checking `PaymentRepository` itself (not `InvoiceRepository`) found every *other* method in the same file (`markConfirmingIfSeen`, `markFinalizedIfCurrentState`, `markSeenIfConfirming`, `markWatchingIfSeen`) binds its enum values as parameters — the literal in the new method was the one inconsistent exception within its own file, not a match for an established convention. Fixed to bind `PaymentState.FINALIZED`/`PaymentState.HELD`. |

No finding was rejected.

## Exact changes made

- **`PaymentRepository.markHeldIfFinalized`** — now takes `expectedCurrentState`/`newState` as
  bound `PaymentState` parameters instead of literal strings (row 7).
- **`PaymentService.markHeldIfFinalized`** — removed the `blockedReason` null-rejection, added a
  `"(no reason provided)"` fallback before building the `VerificationRecord`'s detail map (row
  4); updated its own repository call to pass the two enum parameters (row 7).
- **`ReceiptService.handleBlocked`** — `catch (IllegalStateException e)` → `catch
  (RuntimeException e)` (row 1); class Javadoc gained the crash-window paragraph (row 2).
- **`ChainEventConsumer`** — class-level Javadoc corrected (row 5); `onChainTxFinalized`'s own
  `.ifPresent(...)` now logs the `ReceiptIssueResult`'s simple class name at `INFO` (row 6).
- **Self-caught while verifying row 6's fix**: logging `result.getClass().getSimpleName()`
  immediately NPE'd two existing `ChainEventConsumerTest` methods
  (`shouldMarkInvoicePaidOnExactMatch`, `shouldLogWhenInvoiceOutcomeIsNotApplied`) — both reach
  the `amountDiscrepancy == null` branch but had never stubbed `receiptService.issueIfEligible`,
  so Mockito's own default-null-return surfaced as a real `NullPointerException` once the new
  logging line dereferenced it. Fixed by adding
  `when(receiptService.issueIfEligible(any(), any())).thenReturn(new
  ReceiptIssueResult.NotEligible())` to both tests — a mechanical stubbing fix to keep the
  module buildable (same treatment as the earlier constructor-signature break at Phase 6), not
  new test content.

## Verification

`mvn -o -pl services/payment -am compile`: clean. `mvn -o -pl services/payment -am test
-Dtest=ChainEventConsumerTest`: **27/27 passing** (after the stubbing fix). `mvn -o -pl
services/payment -am test -Dtest=ChainEventConsumerIdempotencyAndOrderingIntegrationTest`:
**5/5 passing** (the real Postgres+Kafka stack test, re-confirmed after the logging/Javadoc
changes). `mvn -o -pl services/payment -am test` (full suite): **348/348 passing**, unchanged
from the Phase 6/7/8 baseline — no regression.
