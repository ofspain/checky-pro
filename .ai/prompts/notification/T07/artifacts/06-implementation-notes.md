# notification · T07 · Phase 6 — Implementation Notes

Consumes:
- `artifacts/04-frozen-task-brief.md`
- `artifacts/05-implementation-plan.md`

Verification: `mvn -o -pl services/notification verify` → **405/405, BUILD SUCCESS** (2026-10-19).

## What changed

### Production, all new (plan steps 1–4)

| File | Plan step | ACs | Notes |
|---|---|---|---|
| `routing/InvoiceRoute.java`, `routing/PaymentRoute.java` | 1 | AC1, AC2, AC4 | Getter-only entities. `token_decimals` is a nullable `Short`, and `created_at` is DB-defaulted (`insertable=false, updatable=false`). |
| `routing/InvoiceRouteRepository.java`, `routing/PaymentRouteRepository.java` | 1 | AC5 | Package-private. The native `INSERT … ON CONFLICT (pk) DO NOTHING` (`:16`) needs only the INSERT grant, and the first writer wins. |
| `routing/PaymentRouting.java` | 1 | L11 | Public `@Service`, the only seam `consumer` uses. Returns `UUID` / `Integer`, never entities. |
| `db/migration/V12__payment_routing_projection.sql` | 2 | D2 | `notifications.invoice_routes` and `notifications.payment_routes`. |
| `db/migration/V13__notification_app_payment_routing_grant.sql` | 2 | Constraints | `GRANT INSERT, SELECT` only, with no UPDATE or DELETE. |
| `db/migration/V14__seed_payment_templates_v2.sql` | 2 | AC9, D3, F7, L9 | Eight `version=2` rows, merchant-facing. There is no `{{currency}}`, `{{displayName}}` or `{{receiptLink}}`. `V3` is untouched. |
| `consumer/dto/{InvoiceCreated,PaymentSeen,PaymentFinalized,ReceiptIssued}Event.java` | 3 | AC7 | Records with `@JsonIgnoreProperties(ignoreUnknown=true)`. Components match each v1 schema exactly, and amounts are `String`. |
| `consumer/AmountFormatter.java` | 4 | AC8 | `BigDecimal.movePointLeft(d).stripTrailingZeros().toPlainString()`. Unknown decimals give `"<raw> base units"`; an unparseable value is passed through raw. |
| `consumer/PaymentEventConsumer.java` | 4 | AC1–AC6, L1, L4, F9 | Four `@KafkaListener @Transactional` listeners, keyed `<eventType>:<aggregateUuid>`. Each one dedupes, then records routes, then resolves the merchant, then dispatches. |

**`receipt.issued`.**
- It re-records both routes from its own fields, which repairs a route an earlier event never left.
- It prefers the event's `merchantAccountUuid` and falls back to the routed merchant.
- `eventData` carries `receiptId`, never `receiptUuid` (that key would make the renderer build the `/receipts/` link, D3), `receiptUri`, or any address.

**Unresolved merchant.** The event is dedup-recorded, one INFO line is logged with the topic, event key and UUIDs only, and the listener returns (AC6).

### Not modified (Phase 5 correction)

`DeliveryOrchestrator`. `NOTIFICATION_MAPPINGS` already maps all four kinds (T11). Brief scope item 5 needed no change.

### Tests

**New:**
- `PaymentEventConsumerTest` (11): the three §8-named tests, with D1/D3 disclosures in their Javadoc, plus finalized routing, redelivery, unresolved route, routed fallback, `receiptUri` exclusion, keys, missing merchant and listener annotations.
- `AmountFormatterTest` (5).
- `PaymentEventContractTest`.
- `PaymentEventConsumerIntegrationTest` (2):
  - It uses its **own** `apache/kafka:3.8.0` container, with all six listened topics pre-created.
  - It runs invoice → seen → finalized → receipt, the receipt without `merchantAccountUuid` (fallback). It then redelivers all four.
  - It asserts the routes persisted, exactly four dispatches, the formatted `amount` `1.5`, and no `s3://` values.
  - Every captured dispatch is rendered through the real `TemplateRenderer`. Both channels come back as version 2, with no `{{` and no `/receipts/`.
  - A second test proves the first writer wins on both route tables.

**Updated:**

| Test | Change |
|---|---|
| `NotificationBaselineMigrationIntegrationTest` | `allEightBaselineTablesExistAndNoOthers` is now `allTenSchemaTablesExistAndNoOthers` (+2 routing tables). Flyway history now covers 1–14. The version-1 seed test filters `WHERE version = 1` and still asserts the same 14 rows. |
| `NotificationBaselineMigrationIntegrationTest` (new tests) | `notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnThePaymentRoutingTables` and `paymentTemplatesHaveAVersionTwoWithoutUnfillablePlaceholders`. |
| `ConsumedEventSchemaConformanceTest` | Covers the four payment DTOs. The type matcher now accepts the `["string","null"]` array form. |
| `T01SkeletonRegressionTest` | Allowlist gains the 11 new production files (61 total). |
| `ArchitectureTest.FEATURE_MODULES` | Gains `routing`. |

No assertion was weakened. Each pinned expectation was widened only by the T07 additions.

## Mutation checks

Each mutation was applied, run and restored, and the restore was confirmed byte-identical with `filecmp`.

| # | Mutation | Result |
|---|---|---|
| M1 | Drop the dedupe on `payment.seen` | Killed (3 unit tests) |
| M2 | Dispatch `invoice.created` to the invoice UUID | Killed (unit + IT) |
| M3 | Put `receiptUri` into `eventData` | Killed (unit + IT) |
| M4a | Drop `ON CONFLICT` on `payment_routes` | Killed (IT) |
| M4b | Drop `ON CONFLICT` on `invoice_routes` | **Survived at first.** The lifecycle IT's receipt has no merchant, so the second invoice insert never happens. Added `routingWritesAreInsertOnceAndTheFirstWriterWins`; now killed. |
| M5 | Floating-point amount division | Killed (`shouldStayExactForLargeValues`) |
| M6 | `receipt.issued` skips invoice-route repair | Killed (unit) |

## Deviations and disclosures

1. **Tests are written in Phase 6.** The Phase 6 prompt says tests belong to Phase 10, but the frozen brief and plan put them here, as notification T06/T11 and payment did. Phase 10 will review and extend them, not write them from scratch.
2. **Shared-broker first-run delay (existing, not T07-specific).**
   - The first verify run timed out two shared-broker tests after 40 s: `AuthEventConsumerIntegrationTest.passwordResetIsConsumed…` and `VerifyEmailRedeliveryIntegrationTest`.
   - **Cause:** the four new listeners joined the same consumer group while `payments.*` didn't yet exist on `localhost:9094`. Auto-creating those topics and the resulting group rebalances held the auth listeners past their wait.
   - Once the topics existed, both tests passed alone and in two full runs.
   - Any fresh shared broker shows the same behaviour for the auth topics, since those tests also rely on auto-creation. The new IT avoids it by pre-creating topics on its own container.
   - Not changed here: the existing tests are out of scope.
   - The shared broker (`auth-kafka-1`) now holds four auto-created `payments.*` topics. The container itself was not touched.
3. **Conformance test message.** The type-mismatch message now prints a textual type as plain text (`'string'`), so the existing synthetic test's message check still holds.
4. **Carried from the brief:**
   - merchant-only (D1, FU-T07-1)
   - no receipt link (D3, FU-T07-2)
   - L2 interpretation (D2, FU-T07-3)
   - no deferred retry for late routes (AC6, FU-T07-4)
   - hand-written DTOs (the platform-wide `agents.md:38` gap)

## Open Questions

None blocking.
