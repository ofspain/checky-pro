# payment · T13 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T13 — Amount & poisoning integrity |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

- **R15** — on a `FINALIZED` match, compare the observed amount to the invoice's own amount in
  base units (exact match only, Q1): equal → no discrepancy (`PAID`); less → `UNDERPAID`;
  greater → `OVERPAID`. Record the discrepancy on the payment; never mark the invoice `PAID` on
  an underpayment.
- **R16** — persist the address-poisoning flag carried by `chain.tx.finalized` on the payment and
  surface it in the payment-facing view (already done, T11 — this task's own job is the missing
  named test plus confirming no gap, not new behavior).
- **Phase 0 finding, decided by the user** — also transition `Invoice.state`
  (`OPEN → PAID`/`UNDERPAID`/`OVERPAID`) at the same moment, closing the orphaned-requirement gap
  and the T08 sweep-race this task's own grounding found. Not a numbered requirement on its own,
  but a direct, explicit scope addition approved at Phase 0 — treated with the same weight as a
  cited requirement for the rest of this pipeline.

## Locked Decisions

- **L6** — the comparison and every value involved (`Payment.amount`, `Invoice.amount`) are
  `BigDecimal`/`NUMERIC` base units; no scaling, no floating-point type anywhere in the new code.
- **L8** (context, continuity) — no new outbox event. `R30`'s own exhaustive list
  (`invoice.created`/`payment.seen`/`payment.finalized`/`receipt.issued`) has no "invoice paid"
  entry — the `Invoice.state` transition is a pure DB-side effect, mirroring `expireInvoice`'s
  own identical no-outbox shape (T08), not a gap to fill.
- **L12** — `PaymentService` still must not call `InvoiceService`/`CryptoClient` itself; any
  cross-module data this task needs arrives as a parameter, any cross-module side effect is
  `ChainEventConsumer`'s own job, mirroring T12's own `ReorgOutcome` pattern.
- **L1/L5/L14** (context, continuity) — no new state-machine edge, no new consumer/topic, no new
  guard against a second `Payment` row; all already settled by T09/T11/T12, unaffected by this
  task's own scope.

## Files involved

**Existing, to extend:**
- `PaymentService.java` — `applyFinalized`/`tryFinalizeFrom` need the invoice's own `amount` to
  compute the discrepancy; signature change likely (Phase 2's own job to settle exactly how).
- `PaymentRepository.java` — `markFinalizedIfCurrentState`'s own `@Query` needs a new
  `amount_discrepancy = :amountDiscrepancy` `SET` clause.
- `ChainEventConsumer.java` — `onChainTxFinalized` needs to look up the invoice before calling
  `applyFinalized` (mirroring `onChainTxSeen`'s own existing shape) and, per the Phase 0 scope
  addition, call a new `InvoiceService` method afterward to mark the invoice's own outcome.
- `InvoiceService.java`/`InvoiceRepository.java` — a new public method wrapping the already-generic
  `updateStateIfCurrentState` (T08) for the `OPEN → PAID`/`UNDERPAID`/`OVERPAID` transition - no
  new repository query needed, only a new service-level passthrough with the right semantics
  (mirrors `expireInvoice`'s own shape: atomic, conditional, returns whether it actually happened).
- `Payment.java` — no new field (`amountDiscrepancy` already mapped since T11); only a getter is
  already present.
- `Invoice.java`/`InvoiceState` — no new enum value (`PAID`/`UNDERPAID`/`OVERPAID` already exist
  since T02); only a new call site.

**Not involved:**
- Any migration — `amount_discrepancy` (`payments.payments`) and the full `InvoiceState` enum/
  `CHECK` constraint already exist in `V1`.
- `Receipt`/ledger/attestation (tasks 15-19, unbuilt) — R16's own "surface... in receipt views"
  half has nothing to surface into yet, confirmed out of scope (same disposition T12 gave L11's
  own post-`ATTESTED` half).
- Any new outbox event, any new REST endpoint (no `PaymentController` exists; not this task's
  job to add one).

## Dependencies

`PaymentRepository`/`PaymentService` (T11, extended), `InvoiceWatchView` (T12, already carries
`amount` - no further widening needed), `InvoiceService.findByWatchId`/`findByIdAsWatchView`
(T11/T12), `InvoiceRepository.updateStateIfCurrentState` (T08, reused as-is),
`VerificationRecordService` (T10, unchanged - the discrepancy is a field on the existing
`FINALIZED` `VerificationRecord`, not a new record type or transition).

## Acceptance Criteria

- **AC1 (R15, named `shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount`)** — a
  `chain.tx.finalized` event whose observed `amount` is less than / equal to / greater than the
  invoice's own `amount` sets `Payment.amountDiscrepancy` to `UNDERPAID` / `null` / `OVERPAID`
  respectively, as part of the same atomic `FINALIZED` update.
- **AC2 (Phase 0 scope addition)** — the same event also transitions `Invoice.state` to
  `PAID`/`UNDERPAID`/`OVERPAID` accordingly, atomically and conditionally (`WHERE state = 'OPEN'`
  — mirrors `updateStateIfCurrentState`'s own existing convention; a non-`OPEN` invoice is left
  alone, not an error).
- **AC3 (R16, named `shouldSurfaceAddressPoisoningFlagFromObservation`)** — confirms the
  already-existing persist-and-outbox-surface behavior (T11) with this task's own dedicated,
  correctly-named test; not new behavior.
- **AC4 (R14 continuity)** — the stale/unknown-payment paths `applyFinalized` already has are
  unaffected; a stale event still never touches `amount_discrepancy` or `Invoice.state`.
- **AC5 (L12)** — `PaymentService` still makes no `InvoiceService`/`CryptoClient` call directly;
  the `Invoice.state` transition is the consumer's own call, informed by whatever
  `applyFinalized` returns.
- **AC6 (L6)** — no floating-point type anywhere in the new comparison/call sites (ArchUnit
  re-run, no new canary expected).

## Tests required

- `shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount` (named, AC1) — likely 3 sub-cases
  (under/exact/over) or 3 separate test methods, Phase 2/5's own call.
- `shouldSurfaceAddressPoisoningFlagFromObservation` (named, AC3).
- An `Invoice.state` transition test for each of the 3 outcomes (AC2).
- A non-`OPEN`-invoice test for AC2's own conditional guard (mirrors `expireInvoice`'s own
  existing test shape).
- A stale/unknown-payment continuity test confirming no discrepancy/invoice-state write happens
  (AC4).
- An `ArchitectureTest` re-run (no new canary expected - no new entity, no new module).

## Open Questions

**None remaining as genuine blockers.** The one real architectural question this task's own
grounding surfaced (whether to widen scope to mark `Invoice.state`) was already put to the user
and decided at Phase 0 - carried forward into Business Rules above, not re-opened here. A real,
narrower design question remains for Phase 2 to settle explicitly (not a blocker, a decision to
make with reasoning, inviting genuine Phase 3 challenge): exactly how `applyFinalized` receives
the invoice's own `amount` without `PaymentService` depending on `InvoiceService` (the
`watchId`-based lookup mirroring `applySeen`'s own shape is the leading candidate, reasoned
through at Phase 0 §3 as safe for this specific event type, but not yet locked in).
