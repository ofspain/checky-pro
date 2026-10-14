# payment · T13 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T13 — Amount & poisoning integrity |
| **Consumes** | — (entry phase) |

## 1. Architecture summary

As of T12, `payment-service` has a complete event-driven pipeline through `FINALIZED`:
`ChainEventConsumer` (4 `@KafkaListener`s), `Payment`/`PaymentRepository` (atomic conditional
updates only), `PaymentService` (`applySeen`/`applyConfirmed`/`applyFinalized`/`applyReorged`),
`PaymentStateMachine`, `VerificationRecordService`. T13 is the natural extension: at the moment a
payment reaches `FINALIZED`, compute the amount discrepancy against the invoice's own expected
amount (R15) and confirm the already-persisted address-poisoning flag (R16) is fully wired
end-to-end, including its own named test.

## 2. Existing code this task touches

- **`Payment.amountDiscrepancy`** (`String`, length 16) — already mapped since T11 ("every real
  column is mapped here, even `amountDiscrepancy`... only populated by later tasks: 13"), never
  populated by any query. `PaymentRepository.markFinalizedIfCurrentState`'s own `UPDATE` does
  NOT set it today — this is T13's own real, substantive new work.
- **`Payment.addressPoisoningFlag`** — already persisted at `FINALIZED` time (T11) and already
  surfaced via `PaymentFinalizedPayload`'s own outbox event. R16's own persist-and-surface
  requirement for the payment side already appears fully implemented. **What's missing**: T13's
  own named test (`shouldSurfaceAddressPoisoningFlagFromObservation`) doesn't exist yet — the
  existing T11 coverage (`shouldPersistAddressPoisoningFlagWhenTrue`) is a *different*,
  already-closed test, not this task's own deliverable.
- **`PaymentService.applyFinalized`/`tryFinalizeFrom`** — currently takes only a
  `ChainTxFinalized event`, with no `Invoice` context at all (correlates by `(chain, txHash)`
  against the `Payment` row only). To compute a discrepancy against the invoice's own expected
  `amount`, this method needs the invoice's own data - it does not have it today.
- **`InvoiceWatchView`** (T12) already carries `amount` (widened for `CryptoClient.registerWatch`'s
  own needs) - directly reusable here with no further widening needed.
- **`InvoiceService.findByIdAsWatchView`/`findByWatchId`** (T12/T11) - existing passthroughs;
  one of these is the likely lookup path `ChainEventConsumer.onChainTxFinalized` needs to fetch
  the invoice before calling `applyFinalized`, mirroring `onChainTxSeen`'s own existing shape
  (which already looks up `InvoiceWatchView` via `event.watchId()` before calling `applySeen`).
- **`InvoiceRepository.updateStateIfCurrentState`** (T08) - already fully generic (any
  `InvoiceState` pair), currently only ever called with `OPEN → EXPIRED`. Directly reusable,
  with zero new repository code, for whatever disposition Phase 2 settles on regarding
  `Invoice.state` (see Finding below).

## 3. A significant specification finding from this task's own grounding — not yet actioned, put to the user

**While grounding this task, found that `Invoice.state`'s own `PAID`/`UNDERPAID`/`OVERPAID`
values (already in the enum and the `V1` `CHECK` constraint since T02) are never written by any
task in the full 29-task breakdown.** Searched `tasks.md` and `requirements.md` exhaustively:
task 5's own prose describes the `amount == invoice.amount → PAID` comparison, and R15's own text
says "...and SHALL NOT mark the invoice `PAID` on an underpayment" (a negative constraint on an
operation never positively assigned to any task), but no task - not 13 itself (whose own literal
text says only "set `amount_discrepancy`", a *payment*-row field), not 17-19 (attestation, which
transitions the *payment* to `ATTESTED`, never mentions `Invoice.state`) - ever actually performs
this write. `Invoice.state` would stay `OPEN` (or `EXPIRED`) forever, even for a fully, exactly
paid invoice, unless something sets it.

**This traced to a second, concrete, already-reachable consequence, not just a cosmetic gap**:
`InvoiceRepository.findExpiredCandidatesWithoutInFlightPayment` (T08, refined in T12) excludes
an invoice from expiry sweep only while its payment is in `CREATED`/`SEEN`/`CONFIRMING`/a
fresh `WATCHING` - **not** `FINALIZED`. Today, with T11/T12 already built and T13-19 not yet
built, an invoice whose payment finalizes *after* the invoice's own `expires_at` has already
passed (a legitimate, designed-for scenario - design.md O2: "a payment already in flight at
expiry is left alone and attests normally when it finalizes") would be a **sweep-expiry
candidate on the very next run**, since nothing currently moves `Invoice.state` away from `OPEN`
once the payment reaches `FINALIZED`. This is a real, already-latent bug path, not a future
concern - confirmed by direct reading of the live query, not assumed.

**Not yet decided whether this is T13's own job to fix.** Two live candidates:
1. **Widen T13's own scope**: transition `Invoice.state` (`OPEN → PAID`/`UNDERPAID`/`OVERPAID`)
   at the same moment the discrepancy is computed, reusing `InvoiceRepository
   .updateStateIfCurrentState` (T08, already fully generic, zero new repository code needed).
   This also closes the sweep-race above as a side effect (an invoice no longer `OPEN` is no
   longer a sweep candidate at all, `WHERE i.state = 'OPEN'` no longer matches).
2. **Log as a tracked, orphaned requirement** (mirrors T11's own `R6` precedent) and decide
   separately whether a new task needs inventing, or whether this is acceptable to leave for
   Phase 1/MVP with the sweep-race risk separately disclosed and mitigated some other way (e.g.
   widening the sweep's own exclusion to include `FINALIZED`/`ATTESTED`/`HELD` too, without
   touching `Invoice.state` at all - a narrower, more conservative fix for the concrete bug
   without resolving the broader "who marks PAID" question).

**A real Phase 2/3/4-weight decision - to put to the user directly before Phase 1 locks in
scope, not silently assumed either way.**

**Put directly to the user via `AskUserQuestion`; decided: widen T13's own scope (option 1
above).** At `FINALIZED` time, `Invoice.state` transitions `OPEN → PAID`/`UNDERPAID`/`OVERPAID`
using the same comparison that sets `Payment.amountDiscrepancy`, reusing
`InvoiceRepository.updateStateIfCurrentState` (T08, already generic, zero new repository code).
This closes the sweep-race finding as a direct side effect - a Phase 2 design detail to carry
through explicitly, not just an incidental benefit.

## 4. Established patterns to follow

Identical to T11/T12's own: atomic conditional `UPDATE` queries (never load-mutate-save),
`InvoiceWatchView` for any cross-module invoice data `consumer`/`payment` need (never the real
`Invoice` entity), `PaymentService` never calls `InvoiceService`/`CryptoClient` itself - any
cross-module side effect is the consumer's own job, with the service returning enough
information (mirrors T12's own `ReorgOutcome` record) for the consumer to act on afterward.

## 5. Testing conventions

Unchanged: plain JUnit + Mockito for `PaymentServiceTest`/`ChainEventConsumerTest` (fixed
`Clock`), Testcontainers for repository/schema integration (currently Docker-unavailable in this
environment, same carried-forward gap as every prior task), ArchUnit for module/money-type
boundaries (no new rule expected - no new entity, no new module).

## 6. Known gaps / unknowns

- **I do not know** whether `PaymentService.applyFinalized` should receive the invoice via a
  `watchId`-based lookup (mirroring `applySeen`'s own existing shape exactly) or some other path
  - a real design question for Phase 2, informed by the same cross-partition-staleness lesson
  T12's own Phase 8 review taught, though a first pass suggests the risk doesn't actually
  materialize for this specific event type (a second payment cannot exist on the same invoice
  while the first is still genuinely awaiting its own `chain.tx.finalized`, per the existing
  T11/T12 guard) - needs to be reasoned through explicitly, not assumed, at Phase 2.
- **I do not know** the disposition of the `Invoice.state`-marking finding above until the user
  decides - this blocks locking Phase 1's own scope statement.
- Both the T12-era carried-forward items (crypto-service watcher gap, Docker-unavailable
  integration-test gaps, T08's own broken `InvoiceRepositoryIntegrationTest`) remain in force
  unchanged - not re-litigated here.
