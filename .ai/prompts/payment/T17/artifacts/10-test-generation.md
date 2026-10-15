# payment · T17 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

No production code changed this phase.

## Test manifest (`ReceiptServiceTest`, new class — 14 methods)

| Test | Verifies |
|---|---|
| `shouldIssueReceiptOnlyFromFinalizedState` (named) | AC1/R17 — `FINALIZED`+`PAID` triggers `attest` |
| `shouldNeverIssueReceiptBeforeFinalized` (named) | AC1/R18 — not `FINALIZED` → `NotEligible`, no `attest`, no invoice lookup even attempted |
| `shouldReturnNotEligibleWhenInvoiceIsUnderpaidOrOverpaid` | The resolved R17/R18 scope decision |
| `shouldReturnNotEligibleWhenPaymentDoesNotExist` / `...WhenInvoiceDoesNotExist` | Defensive boundary cases |
| `shouldRequestKmsSignatureViaCryptoAttestEndpoint` (named) | AC2/R19 — a real 64-char hex digest is computed and passed |
| `shouldCarryTheSameReceiptUuidInTheSignedResult` | AC4/Finding 5 — the sealed result carries the signed data forward |
| `shouldNotIssueReceiptWhenAttestReturnsBlocked` (named) | AC3/R20 — both `HELD` methods called with the right arguments |
| `shouldReturnBlockedWithoutErrorWhenBothHeldUpdatesAreAlreadyNoOps` | The already-resolved-elsewhere race, still `Blocked` not `Failed` |
| `shouldThrowAndRollBackWhenOnlyPaymentHeldUpdateSucceeds` / `...OnlyInvoiceHeldUpdateSucceeds` | AC7/Finding 2 — the split-result atomicity guard, both directions |
| `shouldReturnFailedWithoutStateChangeWhenAttestIsRefused` | AC6/Finding 6 — 409 never triggers `HELD` |
| `shouldReturnFailedWithoutStateChangeWhenCryptoServiceIsUnavailable` | AC6 — transport failure never triggers `HELD` |
| `issueIfEligibleShouldThrowNullPointerExceptionForNullArguments` | Null-handling boundary |

## Test manifest (`PaymentServiceTest`, 5 new methods)

`shouldReturnReceiptViewWhenPaymentExists`/`...WhenPaymentDoesNotExist`,
`shouldMarkHeldAndRecordVerificationWhenFinalized`, `shouldToleratesNullBlockedReasonWithoutThrowing`
(the Phase 9 bug fix's own regression test — mutation-relevant: proves the method no longer
throws for a null `blockedReason` and substitutes the documented fallback text),
`shouldNotRecordVerificationWhenNotFinalized` (AC8).

## Test manifest (`InvoiceServiceTest`, 5 new methods)

`shouldReturnReceiptViewWhenInvoiceExists`/`...WhenInvoiceDoesNotExist`, `shouldMarkHeldWhenPaid`/
`shouldNotMarkHeldWhenNotPaid`, `markHeldIfPaidShouldThrowNullPointerExceptionForNullArguments`.

## Test manifest (`ChainEventConsumerTest`, 3 methods strengthened)

`shouldMarkInvoicePaidOnExactMatch` now also verifies `receiptService.issueIfEligible` is
called with the exact `paymentId`/`triggerEventKey` — not just `any()`.
`shouldMarkInvoiceUnderpaidOnUnderpayment`/`shouldMarkInvoiceOverpaidOnOverpayment` now verify
it is **never** called — closing Kimi's Phase 8 New Finding 1's own second half (the consumer
never previously asserted the wiring between the finalization outcome and receipt issuance at
all).

## Why no additional tests are needed

- Every acceptance criterion (AC1–AC8) from the frozen brief has at least one direct test.
- The split-`HELD`-result race (Finding 2) is tested in both directions (payment-only,
  invoice-only), not just one.
- The 409-vs-`BLOCKED` distinction (Finding 6) is tested as two separate, clearly-named cases.
- The real Postgres+Kafka integration test (T14's own
  `ChainEventConsumerIdempotencyAndOrderingIntegrationTest`) already exercises the full
  restructured `onChainTxFinalized` end-to-end against a live Spring context, confirmed
  stable through every phase of this task — no new integration test is needed on top of it,
  since this task introduces no new entity/schema/Testcontainers surface of its own.

## Verification

`mvn -o -pl services/payment -am test-compile`: clean. `mvn -o -pl services/payment -am test
-Dtest=ReceiptServiceTest`: **14/14 passing**. `mvn -o -pl services/payment -am test
-Dtest=PaymentServiceTest,InvoiceServiceTest`: **66/66 passing** (37 + 29). `mvn -o -pl
services/payment -am test -Dtest=ChainEventConsumerTest`: **27/27 passing**. `mvn -o -pl
services/payment -am test -Dtest=ChainEventConsumerIdempotencyAndOrderingIntegrationTest`:
**5/5 passing** (re-confirmed, real stack, unaffected). Full suite `mvn -o -pl services/payment
-am test`: **372/372 passing** (348 pre-task baseline + 24 new: 14 `ReceiptServiceTest` + 5
`PaymentServiceTest` + 5 `InvoiceServiceTest`; the 3 strengthened `ChainEventConsumerTest`
methods add assertions to already-counted tests, not new methods).
