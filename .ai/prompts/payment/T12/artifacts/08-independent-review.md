# payment · T12 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T12/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `ChainEventConsumer.java`, `PaymentService.java`, `PaymentRepository.java`, `InvoiceWatchView.java`, `InvoiceService.java`, `InvoiceRepository.java`, `ChainTxReorged.java`.

---

## Executive summary

- T12 production code correctly implements the reorg reversal logic, addresses the Phase 3 Finding 1 guard interaction, uses `TransactionTemplate` to keep the DB reversal separate from the best-effort replacement-watch call, and sets `reorged_at` on reversals.
- The non-Docker suite is green (**229/229**). The unrelated T08 `InvoiceRepositoryIntegrationTest` still fails when included.
- The main new concern is that the replacement-watch lookup uses the reorg event's `watchId`, which can be stale when events for different transactions are processed out of order across Kafka partitions.
- The self-review findings about exception handling, leaked watches, dead code, documentation, and offset latency are all valid and included below.

---

## 1. Replacement-watch lookup should use `payment.invoiceId`, not `event.watchId()`

- **Issue:** `registerReplacementWatch` looks up the invoice via `invoiceService.findByWatchId(event.watchId())`. The event's `watchId` is the watch that was active when the reorged transaction was first observed. After a previous `SEEN → WATCHING` reorg for a *different* transaction on the same invoice, `Invoice.watchId` has been updated to a new value. Because different watchIds map to different Kafka partitions, the original reorg event can be delayed and arrive after the invoice's `watchId` has already changed. In that case the reversal succeeds (the `Payment` row is found by `(chain, txHash)`), but `registerReplacementWatch` fails to find the invoice and no replacement watch is registered — leaving the payment `WATCHING` with no active watch.
- **Severity:** Medium — real cross-partition ordering edge case.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:159-174`
  - `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxReorged.java:14-23` — only `watchId`, no `paymentId`/`invoiceId`
  - `services/payment/src/main/java/com/themistra/payment/payment/Payment.java:59-63` — `Payment` already stores `invoiceId`
- **Recommendation:** Change `PaymentService.applyReorged` to return a richer result (e.g., a record with `paymentId`, `invoiceId`, `newState`) instead of only `Optional<PaymentState>`. `ChainEventConsumer` should then look up the invoice by `invoiceId` (or by the current `watchId` from the result if preferred) for the replacement-watch step, rather than relying on the event's potentially stale `watchId`.
- **Confidence:** High.

---

## 2. `registerReplacementWatch` only catches `CryptoServiceUnavailableException`

- **Issue:** Confirms self-review Finding 2. Failures from `invoiceService.findByWatchId` or `updateWatchIdIfOpen` (e.g., `DataAccessException`) propagate uncaught. Because the dedupe row was already inserted inside the committed transaction, Kafka redelivery will skip `applyReorged` entirely and never retry registration, so the exception only causes a pointless retry loop before settling in the same degraded state.
- **Severity:** Medium.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:166-173`
- **Recommendation:** Widen the catch to `RuntimeException` (or at least add `DataAccessException`) so every failure in the best-effort step reaches the same logged-and-swallowed outcome.
- **Confidence:** High.

---

## 3. A successful `registerWatch` with a failed `updateWatchIdIfOpen` leaks the new watch

- **Issue:** Confirms self-review Finding 3. If `registerWatch` succeeds but the invoice is no longer `OPEN` when `updateWatchIdIfOpen` runs, the new watchId is not persisted and nothing will ever unregister the watch on the crypto-service side.
- **Severity:** Medium — resource leak.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:167-169`
- **Recommendation:** If `updateWatchIdIfOpen` returns `false`, call `cryptoClient.unregisterWatch(response.watchId())` immediately to clean up the orphaned watch.
- **Confidence:** High.

---

## 4. The `chain` half of the mismatch guard in `applyReorged` is dead code

- **Issue:** Confirms self-review Finding 1. The `Payment` was found by `findByChainAndTxHash(event.chain(), event.txHash())`, so `payment.getChain()` is already guaranteed to equal `event.chain()`. Only the `tokenContractAddress` half can ever fire.
- **Severity:** Low — not a correctness bug, but misleading.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java:237-248`
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java:15-18`
- **Recommendation:** Either drop the `chain` half or keep it with an explicit comment that it is a defense-in-depth guard against a future change to the repository query shape.
- **Confidence:** High.

---

## 5. Crash between transaction commit and `registerReplacementWatch` is under-documented

- **Issue:** Confirms self-review Finding 4. If the process crashes after the DB reversal commits but before `registerReplacementWatch` completes, Kafka redelivery cannot retry the registration step because the dedupe key is already present. The outcome is the same as a `registerWatch` failure (the invoice rides its natural expiry), but the Javadoc only names the HTTP-failure case.
- **Severity:** Low — documentation gap.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:153-158,183-190`
- **Recommendation:** Extend the Javadoc/comment to explicitly mention the crash-window case so future readers understand both degradation paths.
- **Confidence:** High.

---

## 6. Kafka offset commit is coupled to the `registerWatch` HTTP round-trip

- **Issue:** Confirms self-review Finding 6. `onChainTxReorged` does not return until `registerReplacementWatch` completes. Even though the register outcome is best-effort, its latency affects when the consumer's offset is committed and therefore consumer lag.
- **Severity:** Low — throughput/latency trade-off.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:134-151`
- **Recommendation:** Document this as an accepted trade-off. If load testing later shows consumer lag, consider moving the replacement-watch registration to an async executor or a separate outbox-driven step.
- **Confidence:** High.

---

## 7. Consider checking invoice state before calling `registerWatch`

- **Issue:** `registerReplacementWatch` fetches the invoice via `findByWatchId`, calls `registerWatch`, and then conditionally updates `watchId` only if the invoice is still `OPEN`. If the invoice is already `EXPIRED`/`HELD`/`PAID`, the `registerWatch` call is wasted and the new watch will be leaked unless the `updateWatchIdIfOpen` false path is fixed (Finding 3).
- **Severity:** Low — related to Finding 3.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:160-169`
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceWatchView.java:22-23` — `state` is available
- **Recommendation:** After fetching the invoice, short-circuit with a warning if `invoice.state() != OPEN`, skipping both `registerWatch` and `updateWatchIdIfOpen`.
- **Confidence:** High.

---

## 8. Reversed payment rows retain invalidated `confirmations`/`amount` values

- **Issue:** `markSeenIfConfirming` and `markWatchingIfSeen` only update `state`, `reorgedAt`, and `updatedAt`. The `confirmations` value from the reorged `chain.tx.confirmed` event and (in the `SEEN → WATCHING` case) the placeholder `amount = 0` remain. This is not necessarily wrong — the verification record captures the reversal — but the persisted row still carries data from a transaction that no longer counts.
- **Severity:** Low — design/documentation question.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java:107-123`
- **Recommendation:** Make an explicit decision and document it in the brief. Options: (a) leave values as-is (they reflect historical observations, not current validity), or (b) reset `confirmations` to the value from the original `chain.tx.seen` event (not stored) or to `0` for `WATCHING`. Do not reset silently without documenting the rationale.
- **Confidence:** Low.

---

## 9. Idempotency-key collision for a second reorg of the same transaction

- **Issue:** Confirms self-review Finding 5. The contract fixes the idempotency key as `chain:txHash:reorged`, so a genuinely distinct second reorg of the same `(chain, txHash)` would be dropped. This is a contract-level issue, not a T12 code bug.
- **Severity:** Low — narrow edge case, out of T12 scope.
- **Evidence:**
  - `contracts/events/chain/tx-reorged.v1.schema.json:5`
- **Recommendation:** Track as a deferred observation; do not change T12 code.
- **Confidence:** High.

---

## Positive confirmations

- The T11 existing-non-terminal-payment guard was correctly narrowed using `reorgedAt` so a reorged-back `WATCHING` row does not block a replacement `chain.tx.seen`.
- `PaymentService.applyReorged` returns only `Optional<PaymentState>` and does not call `CryptoClient` or `InvoiceService`, preserving clean layer boundaries.
- `reorgedAt` is populated on both reversal queries, closing the Phase 3 Finding 2 gap.
- `InvoiceService.updateWatchIdIfOpen` is an atomic, conditional update on `state = OPEN`.
- `TransactionTemplate` is correctly used to commit the dedupe-and-reverse step before the best-effort replacement-watch call.
- The deserialization guard (`deserialize` helper) now catches malformed payloads for all listeners, closing Phase 8 Finding 2 from T11.
- L11 is satisfied: no receipt mechanism exists yet, so no revocation path can be reached.
- `InvoiceWatchView` was widened with exactly the fields `registerWatch` needs, reusing the invoice's original values.
- The non-Docker suite passes at 229/229.

---

## Recommended follow-up order

1. Use `payment.invoiceId` for the replacement-watch lookup instead of `event.watchId()` (Finding 1).
2. Widen the exception catch in `registerReplacementWatch` (Finding 2).
3. Unregister the new watch when `updateWatchIdIfOpen` returns false (Finding 3), or short-circuit on non-OPEN invoice state (Finding 7).
4. Clarify or remove the dead `chain` half of the mismatch guard (Finding 4).
5. Document the crash-window degradation path (Finding 5).
6. Decide and document whether reversed payments should retain or reset `confirmations`/`amount` (Finding 8).
7. Fix the unrelated T08 `InvoiceRepositoryIntegrationTest` transaction issue so the full build is green.
