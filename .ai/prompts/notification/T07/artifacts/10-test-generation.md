# notification · T07 · Phase 10 — Test Manifest

Consumes:
- `artifacts/04-frozen-task-brief.md`
- `artifacts/09-review-resolution.md`

Production code was **not** changed in this phase.

Verification: `mvn -o -pl services/notification verify` → **412/412, BUILD SUCCESS**. This phase adds 4 tests. Mutation M9 (prefer the routed merchant over the event's) was killed, and the file was restored byte-identical.

## §8 named tests

| Test | Verifies |
|---|---|
| `PaymentEventConsumerTest.shouldNotifyBothPartiesOnInvoiceCreated` | R3 / AC1. **Merchant-only, disclosed (D1, FU-T07-1).** |
| `PaymentEventConsumerTest.shouldNotifyOnPaymentSeen` | R4 / AC2 |
| `PaymentEventConsumerTest.shouldNotifyWithReceiptLinkOnReceiptIssued` | R5 / AC3. **Partial: asserts `receiptId` and that there is no link (D3, FU-T07-2).** |

## Acceptance criteria → tests

| AC | Tests |
|---|---|
| AC1 `invoice.created` | `shouldNotifyBothPartiesOnInvoiceCreated`; `shouldNotDispatchAnInvoiceEventWithoutAMerchant`; IT `paymentLifecycleIsConsumedRoutedAndDispatchedToTheMerchantExactlyOnce` (route row stored, `amount` `1.5`) |
| AC2 `payment.seen` | `shouldNotifyOnPaymentSeen`; `shouldNotDispatchWhenNoMerchantRouteIsKnown`; **new** `shouldRecordButNotRouteOrDispatchAnEventThatNamesNoInvoice`; IT lifecycle; IT `paymentSeenBeforeItsInvoiceIsRecordedButNeverDispatched` |
| AC3 `receipt.issued` | `shouldNotifyWithReceiptLinkOnReceiptIssued`; `shouldFallBackToTheRoutedMerchantWhenReceiptIssuedOmitsIt`; **new** `shouldPreferTheReceiptEventsMerchantOverARoutedOne`; IT lifecycle (fallback, no `s3://` or `/receipts/` after rendering) |
| AC4 `payment.finalized` | `shouldNotifyTheRoutedMerchantOnPaymentFinalizedWithHumanAmount`; `shouldLabelBaseUnitsWhenTheInvoiceDecimalsAreUnknown`; **new** `shouldNotDispatchPaymentFinalizedWhenOnlyTheSecondRouteIsMissing`; IT lifecycle |
| AC5 redelivery (L1) | `shouldDoNothingForARedeliveredEvent`; `shouldKeyEachEventByTypeAndAggregate`; IT lifecycle (all four redelivered, still exactly 4 dispatches) |
| AC6 unresolvable | `shouldNotDispatchWhenNoMerchantRouteIsKnown`; `shouldNotRecordAnEventThatNamesNoAggregate`; **new** `shouldRecordButNotRouteOrDispatchAnEventThatNamesNoInvoice`; IT race test (the FU-T07-4 limit is observable) |
| AC7 DTO contracts | `PaymentEventContractTest` (3: components equal the schema properties, unknown fields are ignored, producer-shaped JSON); `ConsumedEventSchemaConformanceTest` (payment DTOs, nullable array types) |
| AC8 amounts | `AmountFormatterTest` (5: shift, no scientific notation, exact for large values, unknown decimals, unparseable or null) |
| AC9 v2 templates | `NotificationBaselineMigrationIntegrationTest.paymentTemplatesHaveAVersionTwoWithoutUnfillablePlaceholders`; IT lifecycle (the real renderer picks v2 on both channels, with no `{{`) |

## Constraints and locked decisions → tests

| Item | Tests |
|---|---|
| Transactional listeners on the real topics (L1) | `everyListenerIsTransactionalOnItsPaymentsTopic` |
| No PII or URI in logs (F9, L4) | `shouldNeverLogAddressesReceiptUriOrAmounts` |
| `receiptUri` and addresses never in `eventData` (L4) | `shouldNotifyWithReceiptLinkOnReceiptIssued`; `shouldNotifyTheRoutedMerchantOnPaymentFinalizedWithHumanAmount`; IT lifecycle |
| Insert-once routing; INSERT+SELECT grant only | IT `routingWritesAreInsertOnceAndTheFirstWriterWins`; `notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnThePaymentRoutingTables` |
| Schema pins | `allTenSchemaTablesExistAndNoOthers`; `allMigrationsAreRecordedAsSuccessfulInFlywayHistory` (1–14); `launchTemplatesAreSeededWithVersionOne` (v1 only) |
| Module boundaries (L11) | `ArchitectureTest` (`routing` in `FEATURE_MODULES`); `T01SkeletonRegressionTest` allowlist |
| Kind → template mapping | Existing `DeliveryOrchestratorTest.notificationMappingsTableContainsExactlyTheSevenVerbatimEntries…` and `paymentDerivedMappingForwardsAllEventDataKeysUnchanged…` (T11) |
| Malformed input | **new** `shouldThrowOnMalformedJsonWithoutRecordingAnything` (same disposition as `AuthEventConsumer`) |

## Added in this phase (gap audit)

1. **`shouldRecordButNotRouteOrDispatchAnEventThatNamesNoInvoice`**
   - **Gap:** after Phase 9 split the null checks, the secondary "no invoice" branches of `payment.seen` and `receipt.issued` had no test.
   - **Test:** the event is recorded once, with no route and no dispatch.
2. **`shouldPreferTheReceiptEventsMerchantOverARoutedOne`**
   - **Gap:** nothing pinned that the event's own merchant wins over a routed one.
   - **Test:** the event's merchant gets the dispatch. Mutation M9 was killed.
3. **`shouldNotDispatchPaymentFinalizedWhenOnlyTheSecondRouteIsMissing`**
   - **Gap:** the existing unresolved test only covered a missing first step.
   - **Test:** the second step of the lookup is missing, and nothing is dispatched.
4. **`shouldThrowOnMalformedJsonWithoutRecordingAnything`**
   - **Gap:** the malformed-input disposition was untested for payment listeners.
   - **Test:** it locks the throw-before-record order, so the container's retry isn't swallowed by dedupe.

## Mutation summary (Phases 6–10)

All were killed: M1–M3, M4a, M4b (after its test was added), M5–M9.

## Not tested (disclosed)

- **Late-route retry (FU-T07-4).** It doesn't exist; the race test locks the current behaviour.
- **S2, frozen null decimals.** Disclosed only.
- **Real SES or in-app delivery of payment kinds.** Covered generically by T11–T13 and T17. The T07 integration test uses the spy dispatcher pattern from `AuthEventConsumerIntegrationTest`.
