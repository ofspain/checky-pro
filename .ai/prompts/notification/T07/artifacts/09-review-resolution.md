# notification · T07 · Phase 9 — Review Resolution

Consumes:
- `artifacts/07-self-review.md`
- `artifacts/08-independent-review.md` (Kimi, commit `393b4487`)

Human sign-off: 2026-10-19, via four decisions. Each Kimi finding was checked against the source before it was put to the human.

Verification: `mvn -o -pl services/notification verify` → **408/408, BUILD SUCCESS** (+3 tests: 2 unit, 1 integration). Two new mutations were run (M7, M8 below); both were killed and both files were restored byte-identical.

## Resolution log

### Accepted, with code changes

**Kimi F3 = self-review S3 — dedup key built before the null check.**
- **Change:** `consumer/PaymentEventConsumer.java`. Each listener now checks its aggregate UUID **before** `idempotencyGuard.recordIfNew`. If the UUID is missing, it logs `"… names no <aggregate>; not recorded"` and returns without recording.
  - The secondary checks (merchant on `invoice.created`, invoice on `payment.seen` and `receipt.issued`) stay after dedupe. Those events have a valid key.
  - `payment.finalized` gained the check; it previously had none.
- **Test:** `PaymentEventConsumerTest.shouldNotRecordAnEventThatNamesNoAggregate`. Mutation **M7** moved the receipt check back after dedupe; it was killed.

**Kimi F4 = self-review S4 — routing entities not `@Immutable`.**
- **Change:** `@org.hibernate.annotations.Immutable` added to `routing/InvoiceRoute.java` and `routing/PaymentRoute.java`.

**Kimi F6 = self-review S5 — receipt subject.**
- **Change:** `V14__seed_payment_templates_v2.sql`. The `receipt.issued` EMAIL subject changed from "Your receipt is ready" to "Receipt issued".
- Editing V14 is allowed because it isn't merged or applied outside tests yet. V3 is untouched (L9).

**Kimi F5 = self-review S1 — race not exercised.**
- **Test:** `PaymentEventConsumerIntegrationTest.paymentSeenBeforeItsInvoiceIsRecordedButNeverDispatched`. It sends `payment.seen` first and waits until it is recorded in `processed_events`, then sends `invoice.created`.
- It asserts:
  - only `invoice.created` is dispatched, holding for 5 s
  - the `payment_routes` row is still stored
- Its Javadoc ties it to FU-T07-4, so adding a retry later has to change it deliberately.
- No production change. The limit stays disclosed.

**Kimi F7 — no log-leak guard.**
- **Test:** `PaymentEventConsumerTest.shouldNeverLogAddressesReceiptUriOrAmounts`. A Logback `ListAppender` (the `EmailChannelTest` precedent) captures the five not-dispatched log lines produced by fully populated events.
- It asserts none of them contains any of:
  - the addresses
  - the tx hash or token address
  - `s3://` or the bucket
  - the KMS key ID
  - the amount
  - the reference
  - `{` (no raw JSON)
- Mutation **M8** appended `event.amount()` to a log reason; it was killed.

### Accepted as disclosure only

**Self-review S2 — null `token_decimals` frozen when a receipt arrives before its invoice.**
- No change. It is the same late-arrival case as FU-T07-4. `payment.finalized`, the only later user of the amount, normally runs before `receipt.issued`. A fix would need a new UPDATE grant.

### Rejected

**Kimi F1 — `clearAutomatically` may detach entities.**
- The same setting is on `ProcessedEventRepository:25` and `ContactProjectionRepository:39` (the established precedent).
- In each listener, the dedupe insert has already cleared the context before the route insert runs.
- `DeliveryOrchestrator` loads its entities only after both inserts, so nothing it holds is cleared.
- No change.

**Kimi F2 — hand-written DTOs.**
- Already mitigated: `PaymentEventContractTest.eachDtoDeclaresExactlyItsSchemasProperties` checks all four DTOs' components against their schema properties, and `ConsumedEventSchemaConformanceTest` checks the types.
- Recording the deviation in `package.md` §4a would edit `spec/`, which is forbidden.
- It stays the platform-wide follow-up (`agents.md:38`, logged at payment T24), as the frozen brief already says (F2).

**Kimi F8 — `AmountFormatter` may throw on a null amount.**
- This is wrong. `AmountFormatter.format` returns `null` for a null input (its first statement), and `AmountFormatterTest.shouldPassThroughAnUnparseableValueAndNull` covers it.
- `putIfPresent` then skips the null.
- Both schemas also mark `amount` as required (`invoice-created.v1.schema.json:7`, `payment-finalized.v1.schema.json:7`).

### Self-review items needing no action

- **S6 (invoice link depends on configuration and a route):** goes in the PR note.
- **S7 (no token symbol):** accepted under F7 at Phase 4.

## Files changed in Phase 9

| File | Kind |
|---|---|
| `services/notification/src/main/java/com/themistra/notification/consumer/PaymentEventConsumer.java` | Production |
| `services/notification/src/main/java/com/themistra/notification/routing/InvoiceRoute.java` | Production |
| `services/notification/src/main/java/com/themistra/notification/routing/PaymentRoute.java` | Production |
| `services/notification/src/main/resources/db/migration/V14__seed_payment_templates_v2.sql` | Migration |
| `services/notification/src/test/java/com/themistra/notification/consumer/PaymentEventConsumerTest.java` | Test (+2) |
| `services/notification/src/test/java/com/themistra/notification/consumer/PaymentEventConsumerIntegrationTest.java` | Test (+1) |

No public API changed and nothing was renamed.
