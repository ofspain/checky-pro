# payment · T11 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T11/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `spec/payment-service/design.md` §4a/L1/L5/L11/L12/L14, `spec/payment-service/requirements.md` R6–R14, `spec/payment-service/tasks.md` T11/T14, `contracts/events/chain/*.schema.json`, `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql`, `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`, `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java`, `services/payment/src/main/java/com/themistra/payment/events/EventTopics.java`, `services/payment/src/main/java/com/themistra/payment/events/OutboxPublisher.java`.

---

## 1. `chain.tx.seen` must verify the invoice is still `OPEN`, not just check for an existing payment row

- **Issue:** Decision 3 says: if `InvoiceRepository.findByWatchId` resolves an invoice that **already has** a non-terminal-state `Payment` row, log `WARN` and drop. It does not say what to do if the invoice is `EXPIRED`, `PAID`, `UNDERPAID`, `OVERPAID`, or `HELD` but has **no** payment row. In those states a new `Payment` row must not be created:
  - An `EXPIRED` invoice with no in-flight payment is supposed to stay expired (R5).
  - A `PAID`/`UNDERPAID`/`OVERPAID` invoice has already been resolved by a previous payment; a late `chain.tx.seen` (watch-unregister race) must be ignored, not create a second payment.
- **Severity:** High — the current logic could resurrect an expired invoice or create a duplicate/ orphan payment row for a resolved invoice.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:72-77` — only checks for existing non-terminal payment row
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:30-54` — `payments` table exists and has no constraint preventing an invoice from acquiring a second payment row other than application logic
  - `spec/payment-service/requirements.md:11` (R5 revised) — expiry is final when no payment is in flight
- **Recommended brief amendment:** Before creating a `Payment`, assert `invoice.getState() == InvoiceState.OPEN` (or, for the already-handled case, a non-terminal payment already exists). If the invoice is in any terminal/resolved state, call `VerificationRecordService.recordIgnored(...)` and return without creating a payment.
- **Confidence:** High.

---

## 2. `consumer` calling `InvoiceRepository` directly couples it to the `invoice` module

- **Issue:** The brief says `InvoiceRepository` gains `findByWatchId` and the consumer uses `watchId` for correlation. If the consumer calls `InvoiceRepository` directly, `consumer` depends on `invoice`'s repository interface. L12 forbids cross-module **entity** imports, but package-by-feature intent is broader: a feature module should not reach into another feature's persistence layer. A future refactor of `InvoiceRepository` (e.g., renaming the method, changing return type, moving the lookup to a stored proc) would break `consumer`.
- **Severity:** Medium — not a runtime bug, but a maintainability/ boundary violation.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:95-98` — correlation key is `watchId`
  - `artifacts/02-task-implementation-brief.md:162-163` — `InvoiceRepository` is modified, not `InvoiceService`
  - `spec/payment-service/agents.md` — "Package-by-feature under `com.themistra.payment`. Each module owns its entities, repositories, services, and API."
- **Recommended brief amendment:** Add `InvoiceService.findByWatchId(UUID watchId)` as the public API for cross-module lookup, and have `ChainEventConsumer` call that. Alternatively, have `PaymentService` accept a `watchId` and own both invoice lookup and payment mutation, so `consumer` only talks to `PaymentService`.
- **Confidence:** Medium.

---

## 3. Recording a confirmation-count "refresh" as a self-transition verification row is not obviously correct

- **Issue:** Decision 3 proposes that when `chain.tx.confirmed` arrives and the current state is already `CONFIRMING` with a strictly greater confirmation count, the system calls `VerificationRecordService.record(id, CONFIRMING, CONFIRMING, key, newCount, null)`. A `fromState == toState` row is not a transition, not a reversal, and not an ignored stale event — the three things T10/R11/R12/R14 explicitly say to record. It will also create one verification row per confirmation bump, which may bloat the audit log.
- **Severity:** Medium — semantic mismatch with the task statement and T10's design.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:58-67`
  - `spec/payment-service/tasks.md:22` (T10) — "record every transition, reversal, and *ignored stale event*"
  - `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordService.java:43-52` — `recordIgnored` was specifically redesigned to use `fromState = null` for "no real transition"
- **Recommended brief amendment:** Choose one of:
  1. Treat a higher confirmation count as a field refresh only, with **no** verification row (simplest, matches R9's wording that `chain.tx.confirmed` transitions `SEEN → CONFIRMING` and records the count once).
  2. Record it as **ignored** via `recordIgnored(..., detail containing {"refresh": true, "confirmations": newCount})`, because relative to the current state the event does not cause a transition.
  3. Record it via `record(..., CONFIRMING, CONFIRMING, ...)` but explicitly document in the brief and in code that self-transition rows mean "confirmation count refresh" and add a dedicated test proving the stored shape.
- **Confidence:** Medium.

---

## 4. Consumer topology and per-payment ordering are not specified

- **Issue:** `design.md` O6 is an OPEN decision: "Propose whether the four `chain.tx.*` topics are consumed by one consumer group with a type switch or one listener per event type, and the partition-key alignment ... so ordering per payment is preserved." The brief does not make this proposal. Without a clear topology, tests in T14 ("per-payment ordering is preserved") have no design to validate, and a misconfigured deployment could process `chain.tx.finalized` before `chain.tx.seen` for the same payment.
- **Severity:** Medium — latent ordering/ correctness risk.
- **Evidence:**
  - `spec/payment-service/design.md:97` (O6)
  - `spec/payment-service/tasks.md:27` (T14) — idempotency & ordering tests
  - `contracts/events/chain/tx-seen.v1.schema.json:5`, `tx-confirmed.v1.schema.json:5`, `tx-finalized.v1.schema.json:5` — all specify partition key = `watchId`
- **Recommended brief amendment:** Explicitly state:
  - One Spring Kafka listener per event type (`@KafkaListener(topics = "chain.tx.seen")`, etc.), all using the same consumer group `spring.kafka.consumer.group-id=payment-service`.
  - The crypto-service producer partitions each topic by `watchId`, so every event for the same watch lands in the same partition and is consumed in order.
  - Concurrency (`spring.kafka.listener.concurrency`) is safe as long as partition-count ≥ concurrency, because ordering is preserved per partition.
- **Confidence:** High.

---

## 5. Decision 1 leaves `CREATED`/`WATCHING` as dead edges and R6 orphaned

- **Issue:** The brief proposes that `chain.tx.seen` creates the `Payment` row directly in `SEEN`, making `CREATED → WATCHING` and `WATCHING → SEEN` edges exist only in `PaymentStateMachine` but never in persisted state or verification history. This is a legitimate simplification, but it leaves R6 ("WHEN the Crypto Service acknowledges the watch registration ... move from `CREATED` to `WATCHING`") unimplemented and the `payments.state` default `'CREATED'` in V1 misleading.
- **Severity:** Medium — specification/ traceability gap.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:29-38` (Decision 1)
  - `spec/payment-service/requirements.md:16` (R6)
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:41` — `state VARCHAR(16) NOT NULL DEFAULT 'CREATED'`
- **Recommended brief amendment:** Either
  1. **Accept and document:** add an explicit note that R6 is intentionally not implemented in Phase 1; the first persisted state is `SEEN`. Add a test assertion that no `Payment` row is ever inserted with state `CREATED` or `WATCHING`.
  2. **Resolve properly (larger scope):** create the `Payment` row in `CREATED` during invoice creation (T06) and transition it to `WATCHING` on watch-ack (R6). This would require revisiting already-completed T06 and is probably out of scope, but it is the only way to make R6 true.
- **Confidence:** High.

---

## 6. `unregisterWatch` inside the transaction holds a DB connection across a network call

- **Issue:** Decision 4 correctly places `unregisterWatch` last inside the `@Transactional` listener so that failure rolls back the whole atomic unit (L14). The trade-off is that the database connection is held for the duration of an outbound HTTP call, increasing contention on the connection pool. The brief calls this out and accepts it; this finding is just a reminder that the decision should not be "optimized" later by moving the call outside the transaction.
- **Severity:** Low — accepted trade-off, but worth monitoring.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:79-91`
  - `spec/payment-service/design.md:18-31` (L14)
- **Recommended brief amendment:** Add a comment/test note that `unregisterWatch` is intentionally synchronous and transactional; future performance work should tune connection-pool size or HTTP timeouts rather than move the call outside the transaction.
- **Confidence:** High.

---

## 7. DTOs and outbox aggregate types must match contracts exactly

- **Issue:** The brief says to create hand-written DTOs for `ChainTxSeen`/`ChainTxConfirmed`/`ChainTxFinalized`. The contract schemas use camelCase field names (`watchId`, `txHash`, `tokenContractAddress`, `addressPoisoningFlag`, `idempotencyKey`, etc.) and require a strict field set. If the Java DTOs use different naming or miss `@JsonProperty`, Kafka deserialization will silently fail or drop fields. Likewise, outbox events must use aggregate types `payment-seen` and `payment-finalized` exactly as `EventTopics` maps them.
- **Severity:** Low/Medium — easy to get wrong, hard to debug without contract tests.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:150-155` — files to create
  - `contracts/events/chain/tx-seen.v1.schema.json`, `tx-confirmed.v1.schema.json`, `tx-finalized.v1.schema.json`
  - `services/payment/src/main/java/com/themistra/payment/events/EventTopics.java:12-17`
- **Recommended brief amendment:** Require the DTOs to be Java records with field names matching the schemas exactly, and add a contract-validation test (mirroring T24's intended pattern) that deserializes sample payloads from `contracts/events/chain/` into the DTOs. Explicitly state that `payment.seen` uses aggregate type `"payment-seen"` and `payment.finalized` uses `"payment-finalized"`.
- **Confidence:** High.

---

## Positive confirmations

- Using the event's own `idempotencyKey` as the `processed_events` primary key is correct and avoids reconstruction drift.
- Native `INSERT ... ON CONFLICT DO NOTHING` for dedupe, executed first in the listener transaction, correctly implements L5/R13 and makes retries safe.
- Correlating by `watchId` is the right call because it is required in all three consumed schemas while `invoiceUuid` is optional.
- Creating the `Payment` row with `to_address = invoice.getPayToAddress()` and `amount = BigDecimal.ZERO` is a defensible way to satisfy the `NOT NULL` columns until `chain.tx.finalized` arrives.
- Keeping `unregisterWatch` last in the `SEEN` flow is the only shape that satisfies L14's rollback guarantee.
- Mapping `Payment.invoiceId` as a plain `Long` (not `@ManyToOne Invoice`) is necessary to avoid a cross-module entity import from the `payment` module into the `invoice` module.
