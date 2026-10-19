# notification · T07 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the final `PaymentEventConsumerTest`/`PaymentEventConsumerIntegrationTest` against the frozen acceptance criteria.

---

## Gap 1 — No test pins the `groupId` of the four `@KafkaListener` methods

- **Why it matters:** `everyListenerIsTransactionalOnItsPaymentsTopic` verifies topics and `@Transactional`, but it does not check that the `groupId` expression is `${spring.kafka.consumer.group-id}`. A future edit that hard-codes a different group would break multi-replica consumption or replay semantics without failing a test.
- **Suggested test:** Extend `everyListenerIsTransactionalOnItsPaymentsTopic` to assert `KafkaListener.groupId()` equals `"${spring.kafka.consumer.group-id}"` for each method.

---

## Gap 2 — No test pins the S2 "null decimals do not overwrite existing decimals" behaviour

- **Why it matters:** `InvoiceRouteRepository.insertIfAbsent` uses `ON CONFLICT DO NOTHING`. When `receipt.issued` (which carries no `tokenDecimals`) is processed after `invoice.created` (which does), the second insert is a no-op and the decimals are preserved. A future change to `DO UPDATE` could silently freeze decimals to null.
- **Suggested test:** In the integration test, send `invoice.created` with `tokenDecimals=6`, then send `receipt.issued` without decimals, and assert that `paymentRouting.findTokenDecimals(invoiceUuid)` still returns `6`.

---

## Gap 3 — No test asserts the exact log line for not-dispatched events

- **Why it matters:** The FU-T07-4 race limitation is observable today because the event is recorded but not dispatched. A regression that accidentally threw an exception or silently swallowed the event would change operational behaviour. The current tests only assert no dispatch, not that a clear log line is emitted.
- **Suggested test:** Capture the `PaymentEventConsumer` logger and assert that `onPaymentSeen`/`onPaymentFinalized`/`onReceiptIssued` each emit an INFO log containing "not dispatched" and the reason when the route is missing.

---

## Gap 4 — No test verifies `invoice.created` with null `tokenDecimals`

- **Why it matters:** The DTO marks `tokenDecimals` as nullable, and the code passes it through. If `invoice.created` ever omits `tokenDecimals`, the route should still be recorded and the invoice notification should still dispatch (without a formatted amount).
- **Suggested test:** Add a unit or integration test with `"tokenDecimals":null` and assert the route is inserted and dispatch succeeds.

---

## Gap 5 — No test verifies that an unparseable/null amount does not blow up the listener

- **Why it matters:** `AmountFormatterTest` covers the formatter in isolation, but `PaymentEventConsumer` calls it directly. A null or unparseable amount in a real event would throw and cause Kafka retries.
- **Suggested test:** Add a unit test with `"amount":null` (or `"amount":"not-a-number"`) and assert the listener logs and returns without throwing, or dispatches with a safe fallback.

---

## Gap 6 — No test verifies the missing-contact-projection branch

- **Why it matters:** AC6 says an event with no resolvable recipient is dedup-recorded, logged, and not dispatched. The current tests mock `PaymentRouting` and assert dispatch doesn't happen when no merchant route is known. They do not test the next step: when a merchant route exists but the contact projection has no email/in-app identity for that merchant.
- **Suggested test:** In the integration test, route to a merchant UUID that does not exist in `contact_projection` and assert no delivery attempt is recorded.

---

## Gap 7 — No test verifies that `receipt.issued` records the payment route even when it also provides a merchant

- **Why it matters:** `onReceiptIssued` records the payment route when `paymentUuid` is non-null. This matters for future `payment.finalized` events. The existing tests verify recording when merchant is omitted, but not when merchant is present.
- **Suggested test:** Add an assertion to `shouldNotifyWithReceiptLinkOnReceiptIssued` (or a new test) that `paymentRouting.recordPayment(PAYMENT, INVOICE)` is called even when `merchantAccountUuid` is provided.

---

## Gap 8 — No test verifies the DTO-to-schema field mapping for every schema field

- **Why it matters:** `PaymentEventContractTest` and `ConsumedEventSchemaConformanceTest` exist, but the manifest does not state that every property of every v1 schema is represented by a DTO field. A schema field added later would be ignored (good, per AC7), but a field renamed or removed could silently break rendering.
- **Suggested test:** Add a source-text or reflection test that iterates the four v1 schemas and asserts each required property has a matching DTO field name (accounting for snake_case→camelCase).

---

## Summary

T07's test suite is comprehensive after Phase 10: it covers the happy path, dedupe, routing, race conditions, malformed input, log PII, amount formatting, and contract conformance. The highest-value additions are Gap 1 (groupId), Gap 2 (null decimals preservation), and Gap 3 (observable not-dispatched logging). Gaps 4–8 are smaller boundary or completeness notes.
