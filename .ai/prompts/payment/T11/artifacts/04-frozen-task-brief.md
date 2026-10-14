# payment · T11 · Phase 4 — Frozen Task Brief (Human Approval gate)

Consumes `artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md`
(Kimi 2.7). Finding 5 (R6 orphaned) was put directly to the user — **decided: accept and
document, do not reopen T06.** Finding 1 is a confirmed correctness bug, fixed directly
(self-evident, not a trade-off). Findings 2/3/4/7 are real design refinements, decided directly.
Finding 6 is an acknowledged trade-off, documented.

## Finding 1 — `chain.tx.seen` must verify the invoice is still `OPEN`, not just check for an existing payment row

**ACCEPTED and FIXED — confirmed, high-severity.** My own Decision 3 only guarded against an
*existing non-terminal* `Payment` row; it said nothing about an invoice that is `EXPIRED`/
`PAID`/`UNDERPAID`/`OVERPAID`/`HELD` with **no** payment row at all (a late `chain.tx.seen`
arriving after the invoice was already resolved some other way). Fixed: before creating a
`Payment`, assert `invoice.getState() == InvoiceState.OPEN`. If not, and no non-terminal payment
row already exists either, this is a late/stale event relative to an already-resolved invoice.
**A genuine schema-driven limitation surfaced while fixing this, disclosed, not silently
worked around:** `VerificationRecordService.recordIgnored` requires a non-null `paymentId`
(`verification_records.payment_id` is `NOT NULL`, immutable `V1`) — but in this exact scenario
there is no `Payment` row to attach a verification record to at all. **Resolution: log at `WARN`
and drop; no `VerificationRecord` is possible for this narrow case.** This is a disclosed,
narrow exception to R14's own "always record" framing, bounded by the immutable schema, not a
design choice — the same category of accepted limitation as T08's own disclosed residual-race
`WARN`-only path.

## Finding 2 — `consumer` calling `InvoiceRepository` directly crosses the module boundary

**ACCEPTED and FIXED.** `InvoiceService` gains a public `findByWatchId(UUID watchId)` method,
delegating to the (still package-private) `InvoiceRepository`. `ChainEventConsumer` depends on
`InvoiceService`, never `InvoiceRepository`, mirroring this codebase's own universal
"repository package-private, service owns the public surface" convention.

## Finding 3 — a confirmation-count "refresh" as a self-transition verification row is semantically wrong

**ACCEPTED, Option 1 (Kimi's own first option).** A higher confirmation count arriving for a
payment already `CONFIRMING` is a field refresh only — **no `VerificationRecord` row at all**.
It is not a transition, not a reversal, and not an ignored stale event (the three shapes T10/R11/
R12/R14 actually define); forcing it into any of those three would be a semantic mismatch and
would bloat the audit log with one row per confirmation bump. `Payment.confirmations` is simply
updated in place.

## Finding 4 — consumer topology and partition ordering were unspecified (`design.md` O6)

**ACCEPTED and RESOLVED — this task's own implementer proposal for O6, not deferred further.**
One `@KafkaListener` per event type (`chain.tx.seen`/`chain.tx.confirmed`/`chain.tx.finalized`),
same consumer group (`spring.kafka.consumer.group-id`, mirrors `AuthEventConsumer`'s own
precedent). All three event schemas already specify "Partition key = watchId" in their own
description text (confirmed by direct re-read) — crypto-service's own producer side already
guarantees every event for the same watch lands in the same partition, in order. Listener
concurrency is safe as long as partition count ≥ concurrency.

## Finding 5 — `R6` orphaned, `CREATED`/`WATCHING` are dead edges

**DECIDED (user, direct question, not a default-accept): accept and document.** `Payment` rows
are born already in `SEEN` at the first `chain.tx.seen`. `R6` remains unimplemented — a known,
disclosed specification gap, not retroactively fixed by reopening the already-closed, reviewed
T06. A guard test proves no `Payment` row is ever persisted with `state = CREATED` or
`WATCHING`.

## Finding 6 — `unregisterWatch` inside the transaction holds a DB connection across a network call

**ACKNOWLEDGED, accepted trade-off, documented.** A code comment on the listener method states
explicitly that this is intentional (L14's own rollback guarantee requires it) and that future
performance work should tune connection-pool size/HTTP timeouts, never move the call outside the
transaction.

## Finding 7 — DTOs and outbox aggregate types must match contracts exactly

**ACCEPTED.** The 3 DTOs are Java records with field names matching
`contracts/events/chain/{tx-seen,tx-confirmed,tx-finalized}.v1.schema.json` exactly (camelCase,
confirmed field-for-field at Phase 0). A lightweight deserialization test per DTO (an inline JSON
string literal matching the real schema's own required/properties fields, cited by the test's
own comment) substitutes for a full contract-test harness, since no JSON-Schema-validation
infrastructure exists yet in this codebase (that's task 24's own, separate scope). Outbox
aggregate types are `"payment-seen"`/`"payment-finalized"`, confirmed already correctly mapped
by the existing `EventTopics` (T04), no change needed there.

---

## Frozen Brief

## Task

Add `ChainEventConsumer` with `processed_events` dedupe on `chain:txhash:eventtype` inside the
applying transaction (R13, L5). Handle `chain.tx.seen → SEEN` + emit `payment.seen` +
unregister the invoice's watch via `CryptoClient` (R7, R7a, R8, L14); `chain.tx.confirmed →
CONFIRMING` + record confirmations (R9); `chain.tx.finalized → FINALIZED` + emit
`payment.finalized` (R10).

## Purpose

The first real caller of `PaymentStateMachine` (T09) and `VerificationRecordService` (T10) — the
engine that drives a payment from first on-chain sighting through finality.

## Scope

**In:** `Payment`/`PaymentRepository`/`PaymentService`, `ChainEventConsumer`,
`ProcessedEvent`/`ProcessedEventRepository`, 3 event DTOs, 2 new grant migrations,
`InvoiceService.findByWatchId` (Finding 2 — not `InvoiceRepository` directly),
`ArchitectureTest.FEATURE_MODULES` gains `consumer`.

**Out:** `chain.tx.reorged` (task 12). R15/R16 computation (task 13). Receipt/attestation/ledger
(tasks 15-19). `R6` (Finding 5 — decided, out of scope, not a deferred item).

## Business Rules

- R7/R7a/R8 — first `chain.tx.seen` for an `OPEN` invoice with no existing non-terminal payment
  (Finding 1): create `Payment` in `SEEN` (`amount = 0` placeholder, `to_address =
  invoice.getPayToAddress()`), unregister the watch (last, inside the transaction — Decision 4,
  Finding 6), emit `payment.seen`.
- R9 — `chain.tx.confirmed`: `SEEN → CONFIRMING` is a real transition; already-`CONFIRMING` with
  a strictly greater count is a field-only refresh, no verification row (Finding 3); any other
  current state, or a not-greater count, is stale (`recordIgnored`).
- R10 — `chain.tx.finalized`: `SEEN`/`CONFIRMING → FINALIZED` is a real transition, persisting
  the event's own real `amount`/`fromAddress`/`toAddress`/`confirmations`/
  `addressPoisoningFlag`; any other current state is stale (`recordIgnored`).
- R13/L5 — dedupe via `processed_events`, the event's own `idempotencyKey` field, native
  `INSERT ... ON CONFLICT DO NOTHING`, same transaction.

## Locked Decisions

- **L1** — `PaymentStateMachine` is the sole arbiter of legal state-pair transitions.
- **L5** — held, per R13 above.
- **L12** — `consumer` imports no entity across feature boundaries (Finding 2 strengthens this:
  no cross-module *repository* import either, now routed through `InvoiceService`).
- **L14** — held, per Decision 4 (unregisterWatch last, inside the transaction).

## Dependencies

`PaymentState`/`PaymentStateMachine` (T09), `VerificationRecordService` (T10), `CryptoClient`
(T08), `OutboxPublisher`/`EventTopics` (T04, unchanged), `Clock`, `InvoiceService` (extended,
Finding 2).

## Inputs

Raw Kafka `String` payloads on `chain.tx.seen`/`chain.tx.confirmed`/`chain.tx.finalized`, one
`@KafkaListener` per topic (Finding 4), same consumer group, partitioned by `watchId` (producer
side, confirmed from the contracts themselves).

## Outputs

Persisted `Payment` rows; `VerificationRecord` rows (except the narrow Finding-1 case and the
Finding-3 refresh case, both explicitly row-less by design); `payment.seen`/`payment.finalized`
outbox events; `unregisterWatch` REST calls.

## State Changes

New `Payment` rows (first `SEEN`, never `CREATED`/`WATCHING` — Finding 5); existing `Payment` row
updates; new `processed_events`/`verification_records` rows (except where explicitly none per
Findings 1/3); `Invoice` is never written by this task.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/payment/Payment.java`
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java`
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/ProcessedEvent.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/ProcessedEventRepository.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxSeen.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxConfirmed.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxFinalized.java`
- `services/payment/src/main/resources/db/migration/V7__payment_app_payments_table_write_grant.sql`
- `services/payment/src/main/resources/db/migration/V8__payment_app_processed_events_grant.sql`
- Test files mirroring the above.

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java` — add
  `findByWatchId` (Finding 2, not `InvoiceRepository` directly).
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java` — add
  the underlying `findByWatchId` query (package-private, called only by `InvoiceService`).
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` —
  `FEATURE_MODULES` gains `"consumer"`.

## Files NOT to Modify

- `V1`-`V6` migrations (immutable). Anything under `spec/`. `EventTopics` (already correct).
  `InvoiceController`/`Invoice`'s own state/fields (T06 stays closed per Finding 5's own
  decision; task 13 owns `Invoice`'s own `PAID`/`UNDERPAID`/`OVERPAID` transition).

## Acceptance Criteria

- **AC1 (R7, named)** — first `chain.tx.seen` for an `OPEN` invoice creates `Payment` in `SEEN`.
- **AC1a (Finding 1, new)** — `chain.tx.seen` for a non-`OPEN` invoice with no existing
  non-terminal payment logs `WARN` and creates nothing (no `Payment`, no `VerificationRecord`).
- **AC2 (R7a, named)** — `CryptoClient.unregisterWatch` called with the invoice's own `watchId`,
  last, inside the same transaction.
- **AC3 (R8, named)** — `payment.seen` published via the outbox, same transaction.
- **AC4 (R9, named)** — `SEEN → CONFIRMING` records `confirmations` and a real transition row.
- **AC4a (Finding 3, new)** — a strictly-greater-count refresh while already `CONFIRMING` updates
  `confirmations` with **no** `VerificationRecord` row.
- **AC4b (R14)** — a not-greater count, or any other current state, is `recordIgnored`, no
  mutation.
- **AC5 (R10, named)** — `SEEN`/`CONFIRMING → FINALIZED` persists the real `amount`/addresses/
  `addressPoisoningFlag`, emits `payment.finalized`.
- **AC6 (R13/L5, named)** — duplicate delivery (same `idempotencyKey`) is a complete no-op.
- **AC7 (L5)** — dedupe and effect application are in the same transaction.
- **AC8** — every real transition calls `VerificationRecordService.record` with the correct
  `triggerEventKey`/`confirmations`.
- **AC9 (Finding 5)** — no `Payment` row is ever persisted with `state = CREATED` or `WATCHING`.
- **AC10 (Finding 2/L12)** — `consumer` package never imports `InvoiceRepository`, only
  `InvoiceService`.
- **AC11 (Finding 4)** — one listener per event type; `ArchitectureTest`'s own canaries still
  pass with `consumer` added to `FEATURE_MODULES`.
- **AC12 (Finding 7)** — each DTO deserializes a schema-shaped sample payload correctly.

## Required Tests

- `shouldTransitionToSeenOnChainTxSeen`, `shouldUnregisterWatchOnFirstSeenAndIgnoreSecondMatch`,
  `shouldEmitPaymentSeenOnFirstSeen`, `shouldTransitionToConfirmingOnChainTxConfirmed`,
  `shouldTransitionToFinalizedOnChainTxFinalized`, `shouldDedupeDuplicateChainEventsByEventKey`
  (all named, `package.md` §8).
- A Finding-1 test: stale `chain.tx.seen` against a non-`OPEN` invoice creates nothing.
- A Finding-3 test: confirmation-count refresh updates the field, no new `VerificationRecord`.
- A Finding-5 guard test: no `Payment` ever persists in `CREATED`/`WATCHING`.
- A Finding-2 guard test (ArchUnit or source-text): `consumer` package never references
  `InvoiceRepository`.
- 3 DTO deserialization tests (Finding 7).
- `ArchitectureTest`'s own existing canaries, re-run with `consumer` added.

## Constraints

- **Transactions**: each listener method `@Transactional`, mirrors `AuthEventConsumer`.
- **Module boundaries (L12)**: `consumer` → `PaymentState` (enum) + `InvoiceService` (not
  `InvoiceRepository`) + this task's own classes only.
- **Idempotency (L5)**: dedupe check first, before any other effect.
- **`unregisterWatch` stays last, inside the transaction, synchronous (Finding 6)** — do not
  "optimize" this later by moving it outside.
- **R6 stays unimplemented (Finding 5, decided)** — do not retroactively modify T06.

## Open Questions

No blockers. Finding 5 was a genuine Human Approval decision, not a default; all others resolved
directly above.
