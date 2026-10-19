# notification · T07 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi, commit `c8fc3cff`).

Human decisions (2026-10-19):
- Accept Gaps 1, 3 and 4+5.
- Reject Gaps 2, 6, 7 and 8, with citations.

Each gap was checked against the tests before it was put to the human.

Verification: `mvn -o -pl services/notification verify` → **413/413, BUILD SUCCESS** (+1 test; 2 strengthened). Mutations M10 and M11 were both killed and restored byte-identical. Production code was not changed.

## Accepted

**Gap 1 — the `groupId` is not pinned.**
- **Change:** `PaymentEventConsumerTest.everyListenerIsTransactionalOnItsPaymentsTopic` now also asserts `groupId == "${spring.kafka.consumer.group-id}"` for all four listeners.
- **Mutation:** M10 hard-coded `payment.seen`'s group; it was killed.

**Gap 3 — the content of the not-dispatched log lines is not asserted.**
- **Change:** `shouldNeverLogAddressesReceiptUriOrAmounts` now also asserts every line is at INFO and says "not dispatched". It also pins the exact five lines in order, each with its topic, key and reason. That includes the "not recorded" line for a missing receipt UUID (Phase 9 F3).
- **Mutation:** M11 removed `payment.finalized`'s log line, so the drop became silent; it was killed.

**Gaps 4 and 5 — null decimals and a null or unparseable amount at the consumer level.**
- **New test:** `PaymentEventConsumerTest.shouldStillRouteAndDispatchWhenDecimalsOrAmountAreMissingOrUnparseable`.
- It asserts the following, with no exception in either case:

| Input | Route recorded | Dispatch |
|---|---|---|
| `tokenDecimals: null`, `amount: null` | `recordInvoice(INVOICE, MERCHANT, null)` | Happens, with no `amount` key |
| `amount: "not-a-number"` | `recordInvoice(…, 6)` | Carries the raw value |

## Rejected (already covered, or wrong premise)

**Gap 2 — null decimals overwriting existing ones.**
- Already covered by `PaymentEventConsumerIntegrationTest.routingWritesAreInsertOnceAndTheFirstWriterWins`. It calls `recordInvoice(invoice, m1, 6)`, then `recordInvoice(invoice, m2, null)`, and asserts `findTokenDecimals` is still `6` against the real Postgres.
- Mutation M4b (drop `ON CONFLICT`) is killed by it.

**Gap 6 — a missing contact projection.**
- That branch belongs to `DeliveryOrchestrator` (T11), not this consumer. The T07 integration test uses the spy dispatcher by design.
- It is covered by `DeliveryOrchestratorIntegrationTest.missingContactProjectionFailsEmailChannelButInAppStillProceeds`.
- Kimi's expected outcome ("no delivery attempt recorded") is also wrong. The email channel writes a failed `delivery_log` row, and in-app still delivers.

**Gap 7 — `receipt.issued` with a merchant records the payment route.**
- Already asserted. `shouldNotifyWithReceiptLinkOnReceiptIssued` uses `receiptIssuedJson(true)` (merchant present) and verifies both `recordPayment(PAYMENT, INVOICE)` and `recordInvoice(INVOICE, MERCHANT, null)`.
- Mutation M6 is killed by it.

**Gap 8 — DTO-to-schema field mapping.**
- Already covered, more strictly than suggested. `PaymentEventContractTest.eachDtoDeclaresExactlyItsSchemasProperties` asserts each DTO's record components **equal** the schema's full `properties` set, not just the required ones.
- No schema uses snake_case, so no name mapping applies.

## Files changed

`services/notification/src/test/java/com/themistra/notification/consumer/PaymentEventConsumerTest.java`
