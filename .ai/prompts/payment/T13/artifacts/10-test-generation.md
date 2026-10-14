# payment · T13 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

Tests only — no production code changed in this phase.

## Test manifest

| Test | File | Verifies |
|---|---|---|
| `shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount` (named) | `PaymentServiceTest` | AC1/R15 — under/exact/over all three computed and persisted correctly, reused as the `FinalizationOutcome.amountDiscrepancy()` value |
| `shouldSurfaceAddressPoisoningFlagFromObservation` (named) | `PaymentServiceTest` | AC3/R16 — both halves (persist on `Payment`, surface via `PaymentFinalizedPayload`), confirmed end-to-end against the new `applyFinalized` signature |
| `shouldCarryTheInvoiceIdOnFinalizationOutcome` | `PaymentServiceTest` | `FinalizationOutcome.invoiceId()` is the invoice's own id, for `ChainEventConsumer`'s own outcome-marking step |
| `shouldIgnoreFinalizedEventWhenTokenContractAddressMismatchesTheStoredPayment` | `PaymentServiceTest` | Phase 9 Finding 3 — the corrected mismatch guard (`event` vs `payment`, not `invoice` vs `payment`) |
| `findInvoiceIdByChainAndTxHashShouldReturnTheInvoiceIdOfAKnownPayment` / `...EmptyForAnUnknownPayment` | `PaymentServiceTest` | Phase 4 Finding 2 — the stable-lookup passthrough itself |
| `applyFinalizedShouldThrowNullPointerExceptionForNullArguments` | `PaymentServiceTest` | null-safety, mirrors the other `apply*` methods' own convention |
| `shouldMarkOutcomeFromOpen` | `InvoiceServiceTest` | AC2 — the `OPEN` attempt |
| `shouldMarkOutcomeFromExpiredWhenOpenAttemptFails` | `InvoiceServiceTest` | AC2/Phase 3 Finding 1 — the widened `EXPIRED` attempt, closing the T08 sweep-race path |
| `shouldNotUpdateAnInvoiceInAnyOtherState` | `InvoiceServiceTest` | Neither attempt matches (e.g. `HELD`) → `false`, no exception |
| `markOutcomeIfOpenOrExpiredShouldThrowNullPointerExceptionForNullArguments` | `InvoiceServiceTest` | AC8/Phase 4 Finding 5 |
| `markOutcomeIfOpenOrExpiredShouldRejectAnyStateNotPaidUnderpaidOrOverpaid` | `InvoiceServiceTest` | The defensive `IllegalArgumentException` guard |
| `shouldLogAndDropChainTxFinalizedForAnUnknownPayment` | `ChainEventConsumerTest` | AC7's own "no known payment" path, now surfaced via `findInvoiceIdByChainAndTxHash` |
| `shouldLogAndDropChainTxFinalizedWhenInvoiceIsMissing` | `ChainEventConsumerTest` | The defensive "payment references a non-existent invoice" branch |
| `shouldNotMarkInvoiceOutcomeWhenFinalizedOutcomeIsEmpty` | `ChainEventConsumerTest` | A stale/unknown `applyFinalized` result never reaches the invoice-marking step |
| `shouldMarkInvoicePaidOnExactMatch` / `...Underpaid...` / `...Overpaid...` | `ChainEventConsumerTest` | AC2 — `toInvoiceState`'s own three-way mapping, end-to-end through the consumer |
| `shouldLogWhenInvoiceOutcomeIsNotApplied` | `ChainEventConsumerTest` | Phase 4 Finding 1's own "already moved on" branch doesn't throw |

## Acceptance criteria not requiring a new test (continuity, already covered)

- **AC4 (R14 continuity)** — the stale/unknown-payment paths of `applyFinalized` were already
  covered by `shouldRecordIgnoredWhenFinalizedEventIsStale`/`shouldLogAndDropChainTxFinalizedForAnUnknownPayment`
  (fixed for the new signature in Phase 6); re-verified neither path reaches `classifyDiscrepancy`'s
  own persisted result or returns a `FinalizationOutcome`. One precise nuance, not a gap: the
  *stale* path (payment found, but neither prior state matches) does compute `classifyDiscrepancy`
  before discovering the event is stale - a harmless, unused computation (never persisted, since
  both atomic update attempts return 0; never returned, since the method returns
  `Optional.empty()`), not a violation of AC4's own intent (no discrepancy is ever *recorded*).
- **AC5 (L12)** — unchanged from T12's own established proof: `PaymentService`'s constructor has
  no `InvoiceService`/`CryptoClient` parameter at all.
- **AC6 (L6)** — `ArchitectureTest` re-run (already part of the full suite run below) — no new
  canary needed, no new entity, no new module.
- **Repository-level integration test for `markFinalizedIfCurrentState`'s new `amount_discrepancy`
  column and `updateStateIfCurrentState`'s own already-generic query** — deferred, Docker
  unavailable in this environment, same precedent as every prior task.

## Verification

`mvn -o compile`, `mvn -o test-compile`: clean. `mvn -o test -Dtest='!*IntegrationTest,!*IT'`:
**265/265 passing** (246 baseline + 19 new).
