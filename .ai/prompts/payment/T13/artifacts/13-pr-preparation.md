# payment · T13 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** same established practice as every prior task this session — executed
directly on the long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task
branch off `main`. `main` itself was never touched and stays deployable. The attribution trailer
below uses this session's own current identity (Claude Sonnet 5), not the generic placeholder
text baked into this prompt template's own header.

## Commit title

`payment T13: amount discrepancy and invoice-outcome marking on FINALIZED (R15, R16)`

## Commit message

```
payment T13: amount discrepancy and invoice-outcome marking on FINALIZED (R15, R16)

Compute the under/over/exact-match amount discrepancy at the moment a payment
reaches FINALIZED (R15, exact match only per Q1), persisted on the payment row
in the same atomic update that already sets amount/addresses/confirmations/
addressPoisoningFlag. R16 (persist and surface the address-poisoning flag) was
already substantially done since T11 - this task's own contribution there is
its own correctly-named regression test, confirming the flag survives the new
applyFinalized signature end-to-end.

Grounding found a real specification gap: no task in the 29-task breakdown
ever writes Invoice.state to PAID/UNDERPAID/OVERPAID, despite those values
existing since T02, and this traced to a concrete, already-reachable bug -
T08's expiry sweep doesn't exclude FINALIZED payments, so an invoice whose
payment finalizes after its own expiry had already passed would be wrongly
swept to EXPIRED on the next run. Put directly to the user; decided: widen
this task to also transition Invoice.state at the same moment, reusing T08's
already-generic updateStateIfCurrentState with zero new repository code.

PaymentService.applyFinalized gains an InvoiceWatchView parameter and returns
Optional<FinalizationOutcome> (mirrors T12's ReorgOutcome). The invoice is
looked up by the payment's own stable invoiceId via a new
findInvoiceIdByChainAndTxHash read method, never by the reorg-vulnerable
watchId - Kimi's Phase 3 review correctly judged the original watchId-based
design (reasoned safe, but an unnecessary fragile coupling) not worth keeping
once a strictly more robust alternative existed. InvoiceService
.markOutcomeIfOpenOrExpired tries OPEN then EXPIRED, closing the sweep-race
path identified above. applyFinalized also gained a tokenContractAddress
mismatch guard, mirroring applyReorged's own identical T12 guard - added
during review resolution after correcting an error in the independent
review's own suggested fix (which compared the wrong pair of values).

265/265 non-Docker tests passing (246 pre-task baseline + 19 new).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Production code:**
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java` — new
  `findInvoiceIdByChainAndTxHash`; `applyFinalized`'s new signature/return and token-mismatch
  guard; new `classifyDiscrepancy`; `tryFinalizeFrom`'s new parameter; two named constants.
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java` —
  `markFinalizedIfCurrentState`'s new `amount_discrepancy` `SET` clause/parameter.
- `services/payment/src/main/java/com/themistra/payment/payment/FinalizationOutcome.java` (new).
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java` — new
  `markOutcomeIfOpenOrExpired`.
- `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java` —
  `onChainTxFinalized`'s new flow; new `resolveInvoiceForFinalization`/`toInvoiceState` helpers.

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/payment/PaymentServiceTest.java` — 9 new
  tests.
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceTest.java` — 5 new
  tests.
- `services/payment/src/test/java/com/themistra/payment/consumer/ChainEventConsumerTest.java` —
  7 new tests, 2 existing tests strengthened (Phase 11).

No migration file — `payments.amount_discrepancy` and the full `InvoiceState` enum/`CHECK`
constraint already existed in `V1`.

## Summary

T13 closes the last gap in the `FINALIZED` transition's own data (amount discrepancy, never
populated since T11 mapped the column) and, following a scope addition approved after grounding
found a real orphaned requirement, finally gives `Invoice.state` a real writer — closing a
concrete, already-reachable sweep-race bug as a direct consequence. The harder design question
was reusing T12's own hard-won "stable invoiceId, not watchId" lesson for a new consumer, which
required inventing a new read-only lookup rather than mirroring `applySeen`'s own simpler shape.

## Testing performed

- `mvn -o compile`, `mvn -o test-compile` (forced clean after the first incremental attempt
  masked real compile errors from the signature changes): clean.
- `mvn -o test -Dtest='!*IntegrationTest,!*IT'`: **265/265 passing** (246 pre-task baseline + 19
  new tests across Phases 6/10/11).
- Full repository/entity integration coverage (Testcontainers) deferred — Docker unavailable in
  this environment, consistent with every prior task's own precedent; recorded as a remaining
  risk in Phase 12, not a blocker to this PASS.

## Specification references

- **Task:** T13 — Amount & poisoning integrity (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R15, R16 (scoped); R14 (continuity, already in force since T11).
- **LOCKED decisions:** none scoped inline; L6, L8, L12, L1, L5, L14 (continuity — all
  reconfirmed in Phase 12's own traceability matrix).
- **Scope addition (not a numbered requirement, approved directly by the user at Phase 0):**
  `Invoice.state` transition to `PAID`/`UNDERPAID`/`OVERPAID`, closing an orphaned requirement
  and the T08 sweep-race it caused.
