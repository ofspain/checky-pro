# payment · T11 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T11 — Idempotent consumers |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

- **R7** — a `chain.tx.seen` event for a watched address records a payment in `SEEN` (Phase 0
  Finding B: this is also where the `Payment` row is first created at all — no earlier task ever
  persists one in `CREATED`/`WATCHING`).
- **R7a** — the same `chain.tx.seen` flow unregisters the invoice's watch (`CryptoClient`, already
  built T08) in the same flow, so no further matching transaction can reach this service at all
  (L14). Must happen **inside** the same transaction's own effect (task's own text: "same
  transaction's own effect").
- **R8** — the same first-`SEEN` flow emits `payment.seen` via the outbox, in the same
  transaction.
- **R9** — a `chain.tx.confirmed` event for a `SEEN` payment moves it to `CONFIRMING` and records
  the reported confirmation count.
- **R10** — a `chain.tx.finalized` event for a `CONFIRMING` **or** `SEEN` payment moves it to
  `FINALIZED` and emits `payment.finalized` via the outbox, in the same transaction.
- **R13** — the same `chain.tx.*` event (by its deterministic key) delivered more than once is
  applied at most once; every subsequent delivery is a no-op.

## Locked Decisions

- **L5** — idempotent, dedupe-on-key consumers: every consumed event deduped on
  `chain:txhash:eventtype` via `processed_events`, inside the same transaction that applies the
  effect. At-least-once delivery must never double-apply.
- **L14** — one transaction per invoice (Q2): `registerWatch` has no auto-unregister-on-match, so
  this service itself must unregister the watch as soon as the invoice's first payment reaches
  `SEEN` (R7a) — not only on `EXPIRED` (R5, T08) — so no further matching transaction can reach
  this service for that invoice at all.
- **L1** (context, T09) — the fixed state graph `PaymentStateMachine` already enforces; this task
  is a real caller of it for the first time.
- **L12** (context) — module boundaries; the new `consumer` package imports no entity from
  `invoice`/`payment`/`verification`/`events`, only `PaymentState` (an enum, already confirmed
  permitted by T09's own ArchUnit rule) and whatever DTOs/services this task's own files define.

## Files involved

**New (this task's own scope, per Phase 0's own scope reading — entity/repository/service,
mirroring T10's identical precedent for an undernamed-but-behaviorally-implied scope):**
- `services/payment/src/main/java/com/themistra/payment/payment/Payment.java` (entity)
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java`
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/ProcessedEvent.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/ProcessedEventRepository.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/dto/{ChainTxSeen,ChainTxConfirmed,ChainTxFinalized}.java`
  (this task's own 3 consumed event shapes; `ChainTxReorged` is task 12's)
- Two new Flyway migrations (grants, see Dependencies).
- Test files mirroring the above.

**Existing, to read/extend:**
- `PaymentState`/`PaymentStateMachine` (T09).
- `VerificationRecordService` (T10) — its first real caller.
- `CryptoClient.unregisterWatch` (T08) — its first real caller for the `SEEN` path (it already
  has one caller, `InvoiceExpirySweepJob`, for the `EXPIRED` path).
- `Invoice`/`InvoiceRepository` — needs a new `findByWatchId` query (Phase 0 Finding, §5:
  `watchId`, not `invoiceUuid`, is the one correlation key every consumed event schema
  guarantees).
- `OutboxPublisher`/`EventTopics` (T04) — `"payment-seen"`/`"payment-finalized"` aggregate types
  already map to the right topics; no `EventTopics` change expected.
- `contracts/events/chain/tx-seen.v1.schema.json`, `tx-confirmed.v1.schema.json`,
  `tx-finalized.v1.schema.json` — authoritative DTO shapes (verbatim field lists already
  extracted at Phase 0).
- `ArchitectureTest.FEATURE_MODULES` — needs a `consumer` entry (and a `payment` business-logic
  entry now has a real entity to protect, where before T09 it had none).

**Not involved:**
- `chain.tx.reorged` handling — task 12.
- Amount/discrepancy comparison, address-poisoning persistence beyond the raw flag — task 13
  (R15/R16), even though `chain.tx.finalized`'s own payload carries `addressPoisoningFlag`; this
  task only needs to get the payment to `FINALIZED` with whatever fields the event carries
  persisted, not *act* on the discrepancy/poisoning signal yet.
- Receipt/attestation/ledger — tasks 15-19.

## Dependencies

- `PaymentState` (T09), `VerificationRecordService` (T10), `CryptoClient` (T08),
  `OutboxPublisher`/`EventTopics` (T04), `Clock` (`ClockConfig`, T04).
- **Two new migrations, confirmed necessary by direct grant-state inspection (Phase 0):**
  - `payment_app` currently has `SELECT`-only on `payments.payments` (T08's own `V5`) — this task
    needs `INSERT`+`UPDATE` too (creating and repeatedly updating a `Payment` row). A new
    migration widens this grant.
  - `payment_app` currently has **no grant at all** on `processed_events` — a new migration grants
    `INSERT`+`SELECT` only, mirroring `notification-service`'s own identical, already-proven
    `V4__notification_app_processed_events_grant.sql` precedent exactly (append-only ledger
    philosophy: a key is never revised or deleted once recorded).
- The native `INSERT ... ON CONFLICT (event_key) DO NOTHING` dedupe pattern
  (`notification-service`'s own `IdempotencyGuard`/`ProcessedEventRepository`), adapted for this
  service's own narrower `processed_events` schema (no `event_type` column here).

## Acceptance Criteria

- **AC1 (R7, named `shouldTransitionToSeenOnChainTxSeen`)** — a `chain.tx.seen` event for a
  previously-unseen `watchId` creates a `Payment` row in `SEEN` (Phase 0 Finding B's own resolved
  reading — a real design call, see Open Questions).
- **AC2 (R7a, named `shouldUnregisterWatchOnFirstSeenAndIgnoreSecondMatch`)** — the same flow
  calls `CryptoClient.unregisterWatch` with the invoice's own `watchId`, in the same transaction;
  a *second* `chain.tx.seen` for the same watch is proven never delivered at all (task's own
  explicit test framing), not merely ignored at the application layer — meaning this AC's own
  test is really proving `CryptoClient.unregisterWatch` was actually called with the right
  argument, not simulating a live Crypto Service.
- **AC3 (R8, named `shouldEmitPaymentSeenOnFirstSeen`)** — the same flow publishes `payment.seen`
  via the outbox, same transaction.
- **AC4 (R9, named `shouldTransitionToConfirmingOnChainTxConfirmed`)** — a `chain.tx.confirmed`
  event for a `SEEN` payment moves it to `CONFIRMING` and records the reported `confirmations`
  value.
- **AC5 (R10, named `shouldTransitionToFinalizedOnChainTxFinalized`)** — a `chain.tx.finalized`
  event for a `CONFIRMING` (or `SEEN`) payment moves it to `FINALIZED`, persists the real
  `amount`/`fromAddress`/`toAddress`/`addressPoisoningFlag` the event carries (overwriting any
  placeholder from AC1), and emits `payment.finalized` via the outbox, same transaction.
- **AC6 (R13, named `shouldDedupeDuplicateChainEventsByEventKey`; L5)** — the same event
  (identical `chain:txHash:eventType` key) delivered twice is applied once; the second delivery
  is a genuine no-op (no second `Payment` mutation, no second outbox event, no second
  `VerificationRecord`, no second `unregisterWatch` call) — proven via the real
  `processed_events` dedupe mechanism, not an application-level "already in that state" check.
- **AC7 (L5)** — dedupe-check and effect-application happen inside the same transaction (a crash
  between them must roll back both, never commit one without the other).
- **AC8 (general, "every transition" continuity from T10)** — every transition this task drives
  (`→SEEN`, `→CONFIRMING`, `→FINALIZED`) calls `VerificationRecordService.record(...)` with the
  real `triggerEventKey`, and `chain.tx.confirmed`'s own `confirmations` value is passed through.

## Tests required

- `shouldTransitionToSeenOnChainTxSeen` (named, R7).
- `shouldUnregisterWatchOnFirstSeenAndIgnoreSecondMatch` (named, R7a).
- `shouldEmitPaymentSeenOnFirstSeen` (named, R8).
- `shouldTransitionToConfirmingOnChainTxConfirmed` (named, R9).
- `shouldTransitionToFinalizedOnChainTxFinalized` (named, R10).
- `shouldDedupeDuplicateChainEventsByEventKey` (named, R13/L5) — the task's own text additionally
  specifies this must be proven as "never delivered," which (per AC2 above) is really about
  `unregisterWatch` being called, not a live redelivery simulation.
- A same-transaction proof for AC7 — implied, not individually named; likely a structural/
  `@Transactional`-presence check mirroring `InvoiceExpirySweepJobTest`'s own
  `sweepMethodIsNotAnnotatedTransactional`-style precedent, or a genuine Testcontainers rollback
  test mirroring `notification-service`'s own `AuthEventConsumerTransactionRollbackIntegrationTest`.
- `VerificationRecordService.record` invocation-argument tests for all 3 transitions (AC8),
  mirroring T10's own established Mockito-verify convention.
- `ArchitectureTest`'s own existing canaries, re-run after `FEATURE_MODULES` gains `consumer` (and
  confirms `payment` now has a real entity to protect).

## Open Questions

**Two genuine, significant architectural questions carried forward from Phase 0 — neither is
covered by any already-resolved `package.md` §11 entry (confirmed by direct read: Q1-Q8 are all
unrelated). Both need real design reasoning in Phase 2, a genuine Phase 3 Kimi challenge, and very
likely a real Phase 4 `AskUserQuestion`** (comparable in weight to T09's own `HELD`-edge decision):

1. **Phase 0 Finding A/B — the orphaned `R6` and the "`Payment` is born already `SEEN`" design.**
   No task in this breakdown implements `R6` (`CREATED → WATCHING` on watch-ack) — watch-ack is a
   synchronous T06 REST response, not an async event, and T06 never created a `Payment` row. This
   task's own `ChainEventConsumer` must therefore create the very first `Payment` row at
   `chain.tx.seen` time, with its initial persisted state already `SEEN` — `CREATED`/`WATCHING`
   become edges `PaymentStateMachine` defines but no running code ever actually transitions
   through as a real stored-row change. **Flag `R6`'s own orphaned status to the user directly as
   a confirmed specification gap** — not task 11's to silently fix, but real enough to deserve a
   decision on whether it's tracked as a future task/retroactive T06 amendment, or accepted as
   permanently out of scope.
2. **Phase 0 Finding C — the `NOT NULL` `amount`/`to_address` columns vs. `chain.tx.seen`'s own
   payload.** `to_address` resolves cleanly from `Invoice.payToAddress` (already known, no
   placeholder needed). `amount` has no real value until `chain.tx.finalized` — a placeholder
   (`0`, the natural candidate) is needed at row-creation time, later overwritten. Confirm this
   reasoning is sound (no legitimate real value could ever collide with the `0` placeholder in a
   way `amount_discrepancy`/R15 — task 13's own, later scope — would misread) and lock in the
   exact placeholder value explicitly, rather than leaving it implicit.
