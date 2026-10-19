# notification · T07 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 diff and Phase 7 self-review against the frozen brief and `spec/notification-service/agents.md`.

---

## Finding 1 — `clearAutomatically=true` on route inserts may detach dispatcher entities

- **Issue:** `InvoiceRouteRepository.insertIfAbsent` and `PaymentRouteRepository.insertIfAbsent` use `@Modifying(clearAutomatically = true, flushAutomatically = true)`. After a route insert, Hibernate clears the persistence context. `DeliveryOrchestrator` then loads contact-projection entities and creates `DeliveryLog`/`DeliveryRetry` entities in the same transaction; if any of those were already loaded before the route insert, they become detached, and a later `save` could throw `PersistentObjectException` or silently re-attach with stale state.
- **Evidence:** `services/notification/src/main/java/com/themistra/notification/routing/InvoiceRouteRepository.java`; `services/notification/src/main/java/com/themistra/notification/routing/PaymentRouteRepository.java`; `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryOrchestrator.java`.
- **Recommendation:** Remove `clearAutomatically = true` (keep `flushAutomatically = true` if needed). The route entities are not read back as managed objects, so clearing buys nothing and adds risk.
- **Confidence:** Low — no failure observed today, but the setting is unnecessary.

---

## Finding 2 — Hand-written DTOs deviate from the generated-models standing rule

- **Issue:** `agents.md` says deserialization models are generated from `contracts/` and are never hand-written. T07 creates four hand-written records in `consumer/dto/`. While T06 set a precedent, it is not a locked exception, so the service accumulates schema-drift risk.
- **Evidence:** `spec/notification-service/agents.md` Events & messaging; `services/notification/src/main/java/com/themistra/notification/consumer/dto/*.java`.
- **Recommendation:** Either migrate to generated models in a follow-up, or add an ArchUnit/source-text rule that every DTO field is backed by the corresponding contract schema, and record the deviation in `package.md` §4a.
- **Confidence:** Low — `PaymentEventContractTest` may already mitigate this; verify it covers all four DTOs and all schema fields.

---

## Finding 3 — Dedup key is built before the null-aggregate check

- **Issue:** This aligns with Phase 7 self-review S3. An event missing `invoiceUuid`/`paymentUuid`/`receiptUuid` is recorded under `invoice.created:null` (etc.), and every subsequent malformed event of that type silently dedupes against it with no "not dispatched" log line.
- **Evidence:** `services/notification/src/main/java/com/themistra/notification/consumer/PaymentEventConsumer.java` lines 67-75, 86-95, 111-121, 131-140.
- **Recommendation:** Validate the aggregate UUID before calling `idempotencyGuard.recordIfNew`. Log and return early without recording when the required field is absent.
- **Confidence:** Low — the producer schemas require the field; this guards against contract drift.

---

## Finding 4 — Routing entities are not marked `@Immutable`

- **Issue:** This aligns with Phase 7 self-review S4. `InvoiceRoute` and `PaymentRoute` are insert-once, getter-only entities. Without `@org.hibernate.annotations.Immutable`, a future setter could be added and Hibernate would attempt an `UPDATE`, which the DB role's grant does not allow.
- **Evidence:** `services/notification/src/main/java/com/themistra/notification/routing/InvoiceRoute.java`; `services/notification/src/main/java/com/themistra/notification/routing/PaymentRoute.java`.
- **Recommendation:** Add `@Immutable` to both entities.
- **Confidence:** Low — defensive intent.

---

## Finding 5 — The cross-topic race is not exercised by an integration test

- **Issue:** This aligns with Phase 7 self-review S1. The four topics are consumed by independent listener containers. During catch-up, `payment.seen` can be processed before its `invoice.created`, causing the merchant route to be missing and the event to be dropped. The integration test sends events sequentially, so this race is only disclosed, not tested.
- **Evidence:** `services/notification/src/test/java/com/themistra/notification/consumer/PaymentEventConsumerIntegrationTest.java`; `services/notification/src/main/java/com/themistra/notification/consumer/PaymentEventConsumer.java`.
- **Recommendation:** Add an integration test that sends `payment.seen` before `invoice.created` and asserts the event is dedup-recorded but not dispatched. This makes the disclosed limitation observable and prevents accidental "fixing" that breaks idempotency.
- **Confidence:** Medium — the race is plausible in normal operations.

---

## Finding 6 — The receipt template subject is ambiguous for a merchant

- **Issue:** This aligns with Phase 7 self-review S5. The `receipt.issued` EMAIL subject is "Your receipt is ready". The merchant is the recipient, but the merchant issued the receipt for the payer, so "your" is unclear.
- **Evidence:** `services/notification/src/main/resources/db/migration/V14__seed_payment_templates_v2.sql`.
- **Recommendation:** Reword to "Receipt issued" or similar merchant-facing subject before V14 is applied beyond tests.
- **Confidence:** Low — wording only.

---

## Finding 7 — No test asserts that `receiptUri` and wallet addresses never reach logs

- **Issue:** The consumer correctly excludes `receiptUri`, `fromAddress`, `toAddress`, and `payToAddress` from `eventData` and from templates, but there is no test that asserts the INFO log line contains none of them. A refactoring could accidentally log the raw JSON payload.
- **Evidence:** `services/notification/src/main/java/com/themistra/notification/consumer/PaymentEventConsumer.java` lines 177-179; `services/notification/src/test/java/com/themistra/notification/consumer/PaymentEventConsumerTest.java`.
- **Recommendation:** Add a unit test that captures the `PaymentEventConsumer` logger, sends a fully-populated event, and asserts that no log line contains the raw `receiptUri`, `payToAddress`, `fromAddress`, or `toAddress`.
- **Confidence:** Low — the current code is correct; this is a regression guard.

---

## Finding 8 — `AmountFormatter.format` may throw on a null amount

- **Issue:** The DTOs deserialize `amount` as nullable strings (`String amount`). `PaymentEventConsumer` calls `AmountFormatter.format(event.amount(), ...)` for `invoice.created` and `payment.finalized`. If a producer omits `amount`, the formatter may throw `NullPointerException` and propagate out of the listener, causing Kafka retries.
- **Evidence:** `services/notification/src/main/java/com/themistra/notification/consumer/PaymentEventConsumer.java` lines 79-80, 124-125; DTO records.
- **Recommendation:** Either guard against null amounts (log and return, or dispatch with no amount), or mark `amount` as non-null in the DTO if the schema requires it.
- **Confidence:** Low — the schema likely requires amount; this is defensive.

---

## Summary

T07 is a solid implementation: idempotency, routing, and merchant-only dispatch are wired correctly. Findings 1 and 5 are the most important Phase 9 touches because they protect against runtime fragility and make a disclosed race observable. Findings 2–4, 6–8 are smaller correctness, security, and defensive notes. No production logic changes are needed beyond these.
