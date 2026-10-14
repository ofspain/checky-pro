# payment · T13 · Phase 2 — Task Implementation Brief

## Task

Amount & poisoning integrity. On match, compute under/overpayment against invoice amount in
base units and set `amount_discrepancy` (R15, Q1); persist and surface the address-poisoning
flag from the observation (R16). **Plus the Phase 0-approved scope addition**: transition
`Invoice.state` to `PAID`/`UNDERPAID`/`OVERPAID` at the same moment.

## Purpose

Closes the one remaining gap in the `FINALIZED` transition's own data: `Payment.amountDiscrepancy`
has been mapped since T11 but never populated. Also closes a confirmed orphaned requirement (no
task ever marks `Invoice.state` PAID) and the concrete sweep-race it caused.

## Decision 1 — invoice lookup by `watchId`, mirroring `applySeen`'s own existing shape

**Proposed, flagged for genuine Phase 3 challenge — the same category of question T12's own
Decision 1 was, reasoned through rather than assumed:**

`ChainEventConsumer.onChainTxFinalized` will look up `InvoiceWatchView` via
`invoiceService.findByWatchId(event.watchId())` **before** calling `applyFinalized`, exactly
mirroring `onChainTxSeen`'s own existing shape (not `applyFinalized`'s own current
`findByChainAndTxHash`-only design). If absent, log at `WARN` and drop — the event never reaches
`PaymentService` at all, same disposition as `onChainTxSeen`'s own "unknown watchId" case.

**Why this is safe for this event type, unlike the staleness race T12's own Phase 8 review
found**: that race existed because a *replacement* watch legitimately rotates `Invoice.watchId`
mid-flight. Here, the existing-payment guard (T11 Phase 8 Finding 1, refined by T12 Phase 3
Finding 1) structurally prevents a *second* payment from ever existing on the same invoice while
*this* payment is still in `SEEN`/`CONFIRMING` — so nothing can rotate `Invoice.watchId` out from
under a payment that is still genuinely awaiting its own `chain.tx.finalized`. By the time a
different payment on the same invoice could rotate the watchId (after this one reaches
`FINALIZED` or gets reorged out), this method would no longer be looking for it. A lookup
failure here, in practice, means the event itself is genuinely stale/unknown — the same
conclusion `applyFinalized`'s own existing `findByChainAndTxHash` would likely reach anyway.

## Decision 2 — `applyFinalized`'s new signature and return type

```java
public record FinalizationOutcome(Long paymentId, Long invoiceId, String amountDiscrepancy) {}

@Transactional
public Optional<FinalizationOutcome> applyFinalized(InvoiceWatchView invoice, ChainTxFinalized event)
```

Mirrors T12's own `ReorgOutcome` pattern exactly: `Optional.empty()` means stale/ignored (R14,
unchanged behavior); a present value carries exactly what `ChainEventConsumer` needs for the
invoice-side effect, without `PaymentService` ever calling `InvoiceService` itself (L12).
`amountDiscrepancy` is `null` (exact match), `"UNDERPAID"`, or `"OVERPAID"` — the same three
values `design.md`'s own schema comment names for the `amount_discrepancy` column, computed
**once** via `event.amount().compareTo(invoice.amount())` before either `tryFinalizeFrom` attempt
(the comparison doesn't depend on which prior state succeeded), then passed into both.

## Decision 3 — the invoice-state transition runs in the SAME transaction, not best-effort

**A deliberate contrast with T12's own `registerWatch` split, not an inconsistency.**
`InvoiceRepository.updateStateIfCurrentState` (T08, reused as-is, zero new repository code) is a
plain, fast, local atomic `UPDATE` — no outbound HTTP call, nothing that justifies splitting it
out of the transaction the way T12's `TransactionTemplate` had to for a `CryptoClient` call.
`onChainTxFinalized` stays `@Transactional` directly on the method, same shape as
`onChainTxConfirmed`/`onChainTxSeen` — `dedupe`, `applyFinalized`, and the new
`InvoiceService.markOutcomeIfOpen` call all commit together.

```java
// InvoiceService — mirrors updateWatchIdIfOpen's own shape exactly
@Transactional
public boolean markOutcomeIfOpen(Long invoiceId, InvoiceState outcome, Instant now)
```

Guards `outcome` is one of `PAID`/`UNDERPAID`/`OVERPAID` (defensive — this is an internal API
with exactly one real caller, but a misuse here would silently corrupt an unrelated invoice
state). Returns `false` if the invoice was no longer `OPEN` — not an error, mirrors
`expireInvoice`'s own convention; no cleanup/retry needed (unlike T12's orphaned-watch case,
there is nothing external to clean up here).

`ChainEventConsumer` translates `FinalizationOutcome.amountDiscrepancy()` into the matching
`InvoiceState` via a small private helper (`null → PAID`, `"UNDERPAID" → UNDERPAID`,
`"OVERPAID" → OVERPAID`) before calling `markOutcomeIfOpen`.

## Decision 4 — R16 needs no production-code change

`addressPoisoningFlag` has been persisted at `FINALIZED` time and surfaced via
`PaymentFinalizedPayload` since T11. This task's own job for R16 is its own named test
(`shouldSurfaceAddressPoisoningFlagFromObservation`), confirming the already-existing behavior
end-to-end, not new code.

## Scope

**In:** `PaymentService.applyFinalized`'s new signature/return; `PaymentRepository
.markFinalizedIfCurrentState`'s new `amount_discrepancy` SET clause; `ChainEventConsumer
.onChainTxFinalized`'s new invoice lookup + invoice-state call; `InvoiceService
.markOutcomeIfOpen` (new); `FinalizationOutcome` (new record).

**Out:** Any migration (all columns/enum values already exist). Any new outbox event (R30's own
list has no "invoice paid" entry). `Receipt`/ledger (tasks 15-19). Any `PaymentController`/API
surface (none exists; R16's own "payment views" half stays deferred, same disposition as T12's
own treatment of L11's post-`ATTESTED` half).

## Business Rules

- R15 — exact-match discrepancy computation, persisted on the payment.
- R16 — confirmed already satisfied; this task adds its own named test only.
- Phase 0 scope addition — `Invoice.state` transition, same moment, same comparison.

## Locked Decisions

- L6 — `BigDecimal` throughout; no new floating-point surface.
- L8 (context) — no new outbox event; the invoice-state write mirrors `expireInvoice`'s own
  no-outbox shape.
- L12 — `PaymentService` still never calls `InvoiceService`/`CryptoClient`; `FinalizationOutcome`
  carries only primitives/a `String`, never an entity or a view.
- L1/L5/L14 (context, continuity) — unaffected.

## Dependencies

`PaymentRepository`/`PaymentService` (T11, extended), `InvoiceWatchView` (T12, `amount` already
present), `InvoiceService.findByWatchId` (T11), `InvoiceRepository.updateStateIfCurrentState`
(T08, reused as-is), `VerificationRecordService` (T10, unchanged).

## Inputs/Outputs

Unchanged wire shape — `ChainTxFinalized` already carries `amount`/`watchId`; no DTO change.
Outputs: `Payment.amountDiscrepancy` set; `Invoice.state` transitioned; existing
`VerificationRecord`/`PaymentFinalizedPayload` unchanged in shape.

## State Changes

`Payment.amountDiscrepancy` (new, this task), `Invoice.state` (`OPEN → PAID`/`UNDERPAID`/
`OVERPAID`, new, this task's own scope addition). No new `PaymentState`/state-machine edge.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/payment/FinalizationOutcome.java`
- Test files for all of the above.

## Files to Modify

- `PaymentService.java` — `applyFinalized`/`tryFinalizeFrom` signatures and bodies.
- `PaymentRepository.java` — `markFinalizedIfCurrentState`'s `@Query`.
- `ChainEventConsumer.java` — `onChainTxFinalized`.
- `InvoiceService.java` — new `markOutcomeIfOpen`.

## Files NOT to Modify

- Any migration (`V1`-`V8`). `InvoiceState`/`PaymentState` enums (values already exist).
  `InvoiceRepository.java` (zero new query - `updateStateIfCurrentState` is reused verbatim).
  Anything under `spec/`. `Receipt`/ledger (unbuilt).

## Acceptance Criteria

- **AC1 (R15, named)** — under/exact/over sets `amountDiscrepancy` to `UNDERPAID`/`null`/
  `OVERPAID` correctly, as part of the one atomic `FINALIZED` update.
- **AC2 (scope addition)** — the same three outcomes transition `Invoice.state` to
  `UNDERPAID`/`PAID`/`OVERPAID`; a non-`OPEN` invoice is left alone (`false` return, no error).
- **AC3 (R16, named)** — the already-existing persist+outbox-surface behavior, now with its own
  correctly-named test.
- **AC4 (R14 continuity)** — a stale/unknown-payment event touches neither
  `amountDiscrepancy`/`Invoice.state` nor computes a discrepancy at all.
- **AC5 (L12)** — `PaymentService` makes no `InvoiceService`/`CryptoClient` call; verified the
  same way T12's own AC6 was (no such dependency exists in its constructor at all).
- **AC6 (L6)** — no floating-point type anywhere in the new code (`ArchitectureTest` re-run).
- **AC7 (Decision 1)** — an unknown/stale `watchId` on a `chain.tx.finalized` event is logged and
  dropped before `applyFinalized` is ever called, mirroring `onChainTxSeen`'s own shape.

## Required Tests

`shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount` (named, likely 3 cases), 1 test per
`Invoice.state` outcome (or folded into the same 3 cases), a non-`OPEN`-invoice guard test,
`shouldSurfaceAddressPoisoningFlagFromObservation` (named), a stale/unknown continuity test, an
unknown-watchId consumer test, an `ArchitectureTest` re-run.

## Constraints

- Discrepancy computed exactly once per event, reused across both `tryFinalizeFrom` attempts.
- The invoice-state write stays inside the same `@Transactional` boundary as the rest of this
  event's handling — no `TransactionTemplate` split (Decision 3's own rationale).
- No second enforcement of L1: the atomic `UPDATE`'s own `WHERE` clause remains the only real
  enforcement; `isPermitted` sanity checks continue unchanged for the payment-state transitions
  themselves (the invoice-state transition isn't a `PaymentStateMachine` concern at all).

## Open Questions

**Decision 1 (the watchId-lookup safety argument) is flagged for genuine Phase 3 Kimi
challenge** — reasoned through explicitly above, not assumed, but a fresh adversarial pass
should still pressure-test it the way T12's own analogous reasoning was tested (and found, in
that case, to have a real gap worth fixing). No other open questions remain.
