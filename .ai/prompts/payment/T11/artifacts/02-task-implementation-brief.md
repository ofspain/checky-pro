# payment · T11 · Phase 2 — Task Implementation Brief

## Task

Add `ChainEventConsumer` with `processed_events` dedupe on `chain:txhash:eventtype` inside the
applying transaction (R13, L5). Handle `chain.tx.seen → SEEN` + emit `payment.seen` + unregister
the invoice's watch via `CryptoClient`, same transaction's own effect (R7, R7a, R8, L14);
`chain.tx.confirmed → CONFIRMING` + record confirmations (R9); `chain.tx.finalized → FINALIZED`
+ emit `payment.finalized` (R10).

## Purpose

The first real caller of `PaymentStateMachine` (T09) and `VerificationRecordService` (T10) — the
engine that actually drives a payment from first on-chain sighting through finality, enforcing
L5's idempotency guarantee and L14's one-transaction-per-invoice guarantee for real.

## Scope

**In:** `Payment`/`PaymentRepository`/`PaymentService`, `ChainEventConsumer`,
`ProcessedEvent`/`ProcessedEventRepository`, 3 event DTOs (`ChainTxSeen`/`ChainTxConfirmed`/
`ChainTxFinalized`), 2 new grant migrations, `InvoiceRepository.findByWatchId`,
`ArchitectureTest.FEATURE_MODULES` gains `consumer`.

**Out:** `chain.tx.reorged` (task 12). Amount-discrepancy computation/persistence beyond the raw
observed fields (R15/R16, task 13). Receipt/attestation/ledger (tasks 15-19). `R6`
(watch-ack → `CREATED`/`WATCHING`) — **confirmed orphaned, not this task's to fix** (Decision 1
below).

## Decision 1 (Phase 0/1 Finding A/B — real architectural call, not a default)

**No task in this 29-task breakdown implements `R6`.** Watch-ack is a synchronous T06 REST
response; T06 never created a `Payment` row. **Proposed, to invite genuine Phase 3 challenge and
a real Phase 4 decision:** `ChainEventConsumer`'s own `chain.tx.seen` handler creates the
`Payment` row for the first time, with its initial persisted state **already `SEEN`** —
`CREATED`/`WATCHING` remain real edges `PaymentStateMachine` enforces but no running code ever
transitions through as a stored-row change (same accepted pattern as `FINALIZED → ATTESTED`,
whose real caller is task 17/19). `R6`'s own orphaned status is flagged to the user directly as a
confirmed specification gap, not silently absorbed into this task.

## Decision 2 (Phase 0/1 Finding C — real design call)

`payments.payments.amount`/`to_address` are `NOT NULL`; `chain.tx.seen` carries neither.
**Proposed:** `to_address = invoice.getPayToAddress()` (already known, no placeholder — the
invoice's own pay-to address *is* what's being watched). `amount = BigDecimal.ZERO` as an
explicit, disclosed placeholder, overwritten by the real observed value once `chain.tx.finalized`
arrives. No legitimate real value can collide with this placeholder in a way that matters:
`amount_discrepancy`/R15 (task 13) only ever reads the value *after* finalization overwrites it.

## Decision 3 (new — the "transition vs. refresh vs. stale" distinction for `confirmed`/`finalized`)

`R14`'s own *detection* logic (which event types belong to which task is otherwise unclear — see
Constraints) must live here, since `chain.tx.confirmed` can legitimately arrive more than once for
the *same* payment as the confirmation count climbs — each later one is a field refresh, not a
new state transition, and `PaymentStateMachine` has no `CONFIRMING → CONFIRMING` edge (correctly —
self-transitions are never legal, T09). Proposed per-event handling, keyed on the payment's own
*current* persisted state at the moment the event is processed:

- **`chain.tx.confirmed`**: if current state is `SEEN` → a real transition
  (`stateMachine.transition(SEEN, CONFIRMING)`, update `confirmations`/`confirmedAt`, record a
  real `SEEN→CONFIRMING` `VerificationRecord`). If current state is already `CONFIRMING` → a
  refresh, not a transition: apply only if the event's own `confirmations` is **strictly greater**
  than the currently-recorded value (R14's own "a lower confirmation count... ignore"); record via
  `VerificationRecordService.record(id, CONFIRMING, CONFIRMING, key, newCount, null)` — a
  self-transition row is a legitimate use of `VerificationRecordService`'s own "trusts its caller
  completely" design (T10), even though `PaymentStateMachine` itself would reject the pair. A
  *not-greater* count, or any other current state entirely, is stale: `recordIgnored`, no
  mutation.
- **`chain.tx.finalized`**: if current state is `SEEN` or `CONFIRMING` → a real transition
  (`stateMachine.transition(current, FINALIZED)`), persisting the event's own real `amount`/
  `fromAddress`/`toAddress`/`confirmations`/`addressPoisoningFlag` (overwriting Decision 2's own
  placeholder). Any other current state is stale: `recordIgnored`, no mutation.
- **`chain.tx.seen`** (Decision 1's own creation path): if `InvoiceRepository.findByWatchId`
  resolves an invoice that **already has** a non-terminal-state `Payment` row, this is the
  disclosed, accepted T08 Phase 9 residual race (a second real transaction slipping through a
  narrow unregister-timing window) — logged at `WARN` and dropped, not crashed, not a second
  competing `Payment` row. Closing this fully is the same already-flagged future hardening task
  T08 itself deferred, not attempted here.

## Decision 4 — `unregisterWatch` runs *last*, inside the transaction (unlike `registerWatch`'s own precedent)

T06's own `registerWatch` deliberately runs **outside/before** any transaction (avoids holding a
DB connection across a network call; a failure there means the invoice is never created at all —
clean, no rollback needed). `unregisterWatch` here is different, and the task's own text says so
explicitly ("same transaction's own effect"): if `unregisterWatch` fails, the *entire* `SEEN`
transition must roll back (including the `processed_events` dedupe mark), so Kafka's own
at-least-once redelivery retries the complete atomic operation — committing `SEEN` while the
watch is still live is exactly the window L14 exists to close. Placed **last** in the method body
(after `Payment` save, `VerificationRecord`, outbox publish) so nothing *after* it can fail and
leave an inconsistent "DB rolled back, REST already succeeded" state — `unregisterWatch` is
already idempotent-safe to retry (T08's own 404/410-as-success handling), so a redelivery that
reaches it again is safe.

## Decision 5 — correlation key is `watchId`, not `invoiceUuid`

Every one of this task's 3 consumed event schemas guarantees `watchId` in its own `required`
list; `invoiceUuid` is present but optional in all of them. `InvoiceRepository` gains
`findByWatchId(UUID watchId)`.

## Decision 6 — the event's own `idempotencyKey` field is the dedupe key, not a reconstructed one

All 3 schemas already carry a producer-assigned `idempotencyKey` (`chain:txHash:eventtype`) —
used directly, not reconstructed from parts (simpler, and immune to any future format drift
between this service's own reconstruction and crypto-service's own producer-side construction).

## Business Rules

- R7/R7a/R8 — first `chain.tx.seen`: create `Payment` in `SEEN` (Decision 1/2), unregister the
  watch (Decision 4), emit `payment.seen`.
- R9 — `chain.tx.confirmed`: transition or refresh per Decision 3.
- R10 — `chain.tx.finalized`: transition per Decision 3, emit `payment.finalized`.
- R13/L5 — dedupe via `processed_events`, same transaction, native `INSERT ... ON CONFLICT DO
  NOTHING` (mirrors `notification-service`'s own proven `IdempotencyGuard` exactly, adapted for
  this service's own narrower 2-column table).

## Locked Decisions

- **L1** — `PaymentStateMachine` is the sole arbiter of legal state-pair transitions; this
  service never duplicates its edge set.
- **L5** — held per above.
- **L12** — `consumer` package imports no entity across feature boundaries; `payment`'s own
  `Payment` entity is now real and protected by the same rule.
- **L14** — held per Decision 4.

## Dependencies

`PaymentState`/`PaymentStateMachine` (T09), `VerificationRecordService` (T10), `CryptoClient`
(T08), `OutboxPublisher`/`EventTopics` (T04), `Clock`, `InvoiceRepository` (extended).

## Inputs

Raw Kafka `String` payloads on `chain.tx.seen`/`chain.tx.confirmed`/`chain.tx.finalized`.

## Outputs

Persisted `Payment` rows; `VerificationRecord` rows; `payment.seen`/`payment.finalized` outbox
events; `unregisterWatch` REST calls.

## State Changes

New `Payment` rows (first `SEEN`); existing `Payment` row updates (`state`, `confirmations`,
`amount`, `fromAddress`, `toAddress`, `addressPoisoningFlag`, `*_at` timestamps); new
`processed_events`/`verification_records` rows; `Invoice` is never written by this task (its own
`PAID`/`UNDERPAID`/`OVERPAID` transition is task 13's scope).

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

- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java` —
  `findByWatchId`.
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` — `FEATURE_MODULES`
  gains `"consumer"`.

## Files NOT to Modify

- `V1`-`V6` migrations (immutable). Anything under `spec/`. `EventTopics` (already correct).
  `Invoice`'s own state/fields (task 13's scope).

## Acceptance Criteria

(Full list: see Phase 1's own AC1-AC8 — unchanged by this brief except where Decisions 1-6 above
sharpen their exact mechanics.)

## Required Tests

(See Phase 1's own list — unchanged.)

## Constraints

- **Transactions**: each `ChainEventConsumer` listener method is `@Transactional` (mirrors
  `AuthEventConsumer`'s own precedent exactly) — dedupe, `Payment` mutation,
  `VerificationRecord`, outbox publish, and (for `SEEN` only) `unregisterWatch` all inside one
  transaction.
- **Module boundaries (L12)**: `consumer` imports `PaymentState` (enum, permitted) and this
  task's own new classes only — no entity import across `invoice`/`payment`/`verification`.
- **Idempotency (L5)**: the dedupe check happens first, before any other effect; a duplicate
  delivery is a complete no-op (not even a `VerificationRecord` for the ignored-duplicate case —
  R13 and R14 are different concerns: R13 is "never apply twice," R14 is "a genuine, new,
  but-stale-relative-to-current-state event is recorded as ignored").
- **No second enforcement of L1 beyond `isPermitted`'s own intended use**: `PaymentService` calls
  `PaymentStateMachine.isPermitted`/`transition` exactly as T09 designed it to be called —
  pre-check, then either transition or `recordIgnored`.

## Open Questions

**Two real, significant architectural decisions (Decisions 1 and 2 above) are proposed, not
assumed — both to be put to a genuine Phase 3 Kimi challenge and a real Phase 4 `AskUserQuestion`,
comparable in weight to T09's own `HELD`-edge decision.** No other blockers.
