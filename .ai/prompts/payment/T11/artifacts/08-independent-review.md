# payment · T11 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T11/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `ChainEventConsumer.java`, `PaymentService.java`, `PaymentRepository.java`, `Payment.java`, `ProcessedEventRepository.java`, `InvoiceService.java`, `InvoiceWatchView.java`, `InvoiceRepository.java`, `ArchitectureTest.java`, `V7__...`, `V8__...`.

---

## Executive summary

- T11 production code is mostly correct and matches the frozen brief: atomic conditional updates, `InvoiceWatchView` for cross-module lookup, `processed_events` native `INSERT ... ON CONFLICT DO NOTHING`, `unregisterWatch` last in the transaction, and `consumer` added to `FEATURE_MODULES`.
- One **real, high-severity bug remains:** `PaymentService.applySeen` does not guard against an existing non-terminal `Payment` row for the same invoice, so a narrow race can create a second `Payment` row for one invoice.
- The non-Docker suite is green (**196/196**). The unrelated T08 `InvoiceRepositoryIntegrationTest` still fails due to a missing transaction.

---

## 1. `applySeen` does not check for an existing non-terminal payment row

- **Issue:** The frozen brief's AC1 explicitly says "first `chain.tx.seen` for an `OPEN` invoice **with no existing non-terminal payment**" creates the `Payment` row. Decision 3 also calls out this exact race (a second `chain.tx.seen` slipping through before `unregisterWatch` commits) and says to log `WARN` and drop. The current `PaymentService.applySeen` checks `invoice.state() == OPEN` but never queries `payments.payments` for rows belonging to that invoice. Because `Invoice.state` stays `OPEN` until task 13 resolves it to `PAID`/`UNDERPAID`/`OVERPAID`, two concurrent/delivered-before-unregister `chain.tx.seen` events for the same watch can both pass the `OPEN` check and create separate `Payment` rows. The unique constraint is on `(chain, tx_hash)`, not `invoice_id`, so different tx hashes will succeed.
- **Severity:** High — direct violation of L14 "one transaction per invoice" and the brief's own AC1.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java:60-90` — only `invoice.state()` check
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:30-54` — no unique constraint on `invoice_id`
  - `artifacts/04-frozen-task-brief.md:107-111` and `Decision 3` — the guard was required
- **Recommendation:** Before creating a `Payment`, atomically verify that no row exists for this `invoiceId` in `CREATED`/`WATCHING`/`SEEN`/`CONFIRMING`. A native `SELECT FOR UPDATE`/`NOT EXISTS` or an `INSERT ... WHERE NOT EXISTS` (or a dedicated `PaymentRepository.existsNonTerminalByInvoiceId`) is acceptable as long as it participates in the same transaction. If a row exists, return `Optional.empty()` and let `ChainEventConsumer` log the race and skip `unregisterWatch`.
- **Confidence:** High.

---

## 2. `ChainEventConsumer` listener methods propagate `JsonProcessingException`

- **Issue:** `onChainTxSeen`/`onChainTxConfirmed`/`onChainTxFinalized` declare `throws JsonProcessingException`. If the Crypto Service (or a misconfigured producer) ever emits a malformed payload, the exception will propagate out of the listener and Spring Kafka's default error handler will retry the same offset indefinitely rather than dropping the poison message.
- **Severity:** Medium — operational risk under bad data.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:64-104`
- **Recommendation:** Catch deserialization exceptions inside the listener, log at `ERROR` with the raw payload (truncated) and event key if available, and return normally. If the project later adds a dead-letter topic, route it there. Do not let a poison message force a consumer restart loop.
- **Confidence:** High.

---

## 3. `PaymentService.applySeen` returns the `Payment` entity into the `consumer` module

- **Issue:** `applySeen` returns `Optional<Payment>`, so `ChainEventConsumer` imports `com.themistra.payment.payment.Payment` — a cross-module entity import in source. The current bytecode happens not to trip the ArchUnit rule (the `Payment` type only appears as a generic argument of a local variable), but this is fragile: a future refactor that stores the result in a field or method parameter will fail the build. It also leaks the entity's full surface into `consumer`.
- **Severity:** Low/Medium — latent boundary violation.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:11,78-83`
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java:60`
  - `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java:139-164`
- **Recommendation:** Introduce a lightweight `PaymentSeenResult` record (e.g., `paymentUuid`, `invoiceUuid`, `watchId`) and return `Optional<PaymentSeenResult>` from `applySeen`. `ChainEventConsumer` only needs to know success/failure; it never reads `Payment` fields.
- **Confidence:** High.

---

## 4. `currentState` read for ignored records may be stale

- **Issue:** In `applyConfirmed` and `applyFinalized`, after the atomic `UPDATE` attempts fail, the code calls `repository.findById(payment.getId())` to determine the current state for the ignored-record audit entry. Because the `Payment` instance was loaded earlier in the same transaction, JPA's first-level cache may return the same stale object rather than re-reading the row. The ignored record could therefore attribute the wrong state.
- **Severity:** Low — audit accuracy only; the functional no-op decision is still correct.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java:122-127,157-162`
- **Recommendation:** Use `EntityManager.refresh(payment)` before reading `getState()`, or add a small native `SELECT state FROM payments.payments WHERE id = :id` in `PaymentRepository` that bypasses the first-level cache.
- **Confidence:** Medium.

---

## 5. No validation that consumed event matches the invoice's chain/token

- **Issue:** `applySeen` trusts the event's `chain` and `tokenContractAddress` without comparing them to the invoice's values. The Crypto Service producer should never mismatch, but a malicious or misconfigured producer could create a `Payment` row whose chain/token does not match the invoice it is linked to. The same applies to `applyConfirmed`/`applyFinalized` finding by `(chain, txHash)`.
- **Severity:** Low — trust-boundary hardening.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java:78-79`
- **Recommendation:** Add an explicit invariant check in `applySeen`: `if (!invoice.chain().equals(event.chain()) || !invoice.tokenContractAddress().equals(event.tokenContractAddress())) { log.warn(...); return Optional.empty(); }`. This makes the consumer defensive against producer-side bugs.
- **Confidence:** Low.

---

## 6. `PaymentService` confirmed/finalized paths do not call `PaymentStateMachine`

- **Issue:** The frozen brief's Constraints section says "`PaymentService` calls `PaymentStateMachine.isPermitted`/`transition` exactly as T09 designed it to be called." In practice, `applyConfirmed` and `applyFinalized` enforce legality via atomic `UPDATE ... WHERE state = :expected` instead. This is not a runtime defect — the SQL `WHERE` clause is a correct, concurrency-safe enforcement — but it is a minor inconsistency with the brief's wording.
- **Severity:** Low — documentation/traceability.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java:96-178`
  - `artifacts/04-frozen-task-brief.md:193-195`
- **Recommendation:** Either update the brief to acknowledge that confirmed/finalized use the database `WHERE` clause as the enforcement mechanism, or add a defensive `stateMachine.isPermitted(current, next)` assertion before each atomic update (with no catch of the exception — it should never throw if the `WHERE` clause is correct).
- **Confidence:** High.

---

## 7. Build context — unrelated T08 integration test is still failing

- **Issue:** `InvoiceRepositoryIntegrationTest` fails with `TransactionRequiredException` because it calls a `@Modifying` repository method outside a transaction. This is a T08 test defect, not a T11 production defect.
- **Severity:** Medium — CI is red.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceRepositoryIntegrationTest.java`
- **Recommendation:** Add `@Transactional` to the test class or call the repository through a transactional helper.
- **Confidence:** High.

---

## Positive confirmations

- `PaymentRepository` uses atomic, conditional `UPDATE` queries for every state/field change — no load-mutate-save lost-update window.
- `markFinalizedIfCurrentState` takes exactly one expected state; `applyFinalized` tries `CONFIRMING` then `SEEN` separately so the audit trail's `fromState` is exact, not inferred from a stale read.
- `InvoiceWatchView` prevents `consumer`/`payment` from ever holding the real `Invoice` entity, closing a real ArchUnit risk.
- `processed_events` dedupe uses native `INSERT ... ON CONFLICT DO NOTHING` with `clearAutomatically`/`flushAutomatically`, correctly avoiding the `exists`-then-save TOCTOU race.
- `unregisterWatch` is the last statement in `onChainTxSeen`, inside the `@Transactional` listener, satisfying L14.
- `consumer` was added to `FEATURE_MODULES` and all 5 `ArchitectureTest` canaries pass.
- V7 and V8 grants are minimal and correct: `INSERT/UPDATE` on `payments.payments`, `INSERT/SELECT` on `payments.processed_events`.
- DTO field names match the chain-event contract schemas, and `amount` deserializes as `BigDecimal` from a decimal string.
- Outbox aggregate types are `"payment-seen"` and `"payment-finalized"`, mapped correctly by `EventTopics`.
- `Payment.createSeen` hardcodes `state = SEEN` and `amount = BigDecimal.ZERO`, matching the frozen brief.

---

## Recommended follow-up order

1. Fix the missing existing-payment-row guard in `PaymentService.applySeen` (Finding 1).
2. Catch deserialization exceptions in `ChainEventConsumer` instead of propagating them (Finding 2).
3. Return a lightweight `PaymentSeenResult` from `applySeen` so `consumer` does not import `Payment` (Finding 3).
4. Refresh the `Payment` instance (or use a native state query) before recording ignored-state audit entries (Finding 4).
5. Consider adding chain/token invariant checks in `applySeen` (Finding 5).
6. Align the brief's wording with the actual confirmed/finalized enforcement mechanism (Finding 6).
7. Fix the unrelated T08 `InvoiceRepositoryIntegrationTest` transaction issue so the full build is green.
