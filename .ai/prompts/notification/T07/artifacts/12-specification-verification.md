# notification · T07 · Phase 12 — Specification Verification

Consumes:
- artifacts `00`–`11` (including `11-test-review-resolution.md`)
- `spec/notification-service/{requirements,design,tasks}.md`

Final state: commit `077d7b59`; `mvn -o -pl services/notification verify` → **413/413, BUILD SUCCESS**. Eleven deliberate code breaks (M1–M11) were all caught by a test.

Paths below are relative to `services/notification/src/main/java/com/themistra/notification/` (abbreviated `…/`), or to `src/main/resources/db/migration/`.

## Traceability matrix

| Requirement | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **Task 7** (`tasks.md:16`): four topics → templates | Yes | `consumer/PaymentEventConsumer.java:66`, `:90`, `:118`, `:143`. The mappings already existed: `delivery/DeliveryOrchestrator.java:97`–`:104` | `everyListenerIsTransactionalOnItsPaymentsTopic` (topic, group, transaction); `DeliveryOrchestratorTest.notificationMappingsTableContainsExactlyTheSevenVerbatimEntries…` | — | — |
| **Task 7: "confirm Q7"** | Yes, decided | Frozen brief D1 (human, 2026-10-19) | — | The spec's Q7 isn't updated (`spec/` is off-limits) | Recorded in the brief; FU-T07-3 aligns the spec in T20 |
| **R3** (`requirements.md:9`): `invoice.created` notifies the recipients | Partial (merchant only) | `PaymentEventConsumer.java:80` (route), `:85` (dispatch to `merchantAccountUuid`) | `shouldNotifyBothPartiesOnInvoiceCreated`; IT lifecycle | Payer notification | **D1:** the §4c matrix (`design.md:35`) says "merchant + payer", but no payer identity exists anywhere. FU-T07-1 |
| **R4** (`:10`): `payment.seen` notifies the configured recipients | Yes (merchant) | `:104` (payment route), the `findMerchantForInvoice` lookup, `:113` dispatch | `shouldNotifyOnPaymentSeen`; IT lifecycle | Payer (as R3) | **D2:** the merchant is resolved through an event-fed routing projection, a human-granted interpretation of L2 |
| **R5** (`:11`): `receipt.issued` notifies both parties, with the receipt link | **Partial** | `:159`/`:162` (route repair), merchant from the event with a routed fallback, `:174` `receiptId`, `:175` dispatch | `shouldNotifyWithReceiptLinkOnReceiptIssued`; `shouldFallBackToTheRoutedMerchant…`; `shouldPreferTheReceiptEventsMerchant…`; IT (rendered with no `/receipts/`) | (a) the payer; (b) the link | **D1** + **D3:** no link until a receipt read endpoint exists (payment FU-T29-1). FU-T07-1, FU-T07-2 |
| Task-only: `payment.finalized` (§4c `design.md:37`) | Yes (merchant) | `:118`–`:138`: two-step route lookup, amount from routed decimals | `shouldNotifyTheRoutedMerchantOnPaymentFinalized…`; `…WhenOnlyTheSecondRouteIsMissing`; IT | Payer | D1, D2 |
| **R7/R8 via L1** (`design.md:5`): dedupe on a stable key, same transaction | Yes | `recordIfNew` at `:73`, `:97`, `:125`, `:150`, before any side effect. Listeners are `@Transactional`; `PaymentRouting` joins the transaction | `shouldDoNothingForARedeliveredEvent`; `shouldKeyEachEventByTypeAndAggregate`; `shouldNotRecordAnEventThatNamesNoAggregate`; IT redelivery | — | — |
| **L2** (`design.md:6`): consume-only, no live call, contact projection the only projection | Yes, as interpreted | `routing/` is fed only by consumed payment events; no HTTP client (ArchUnit L2 rule) | `ArchitectureTest`; IT | — | **D2:** the literal "only permitted projection" is widened to "contact plus payment routing", granted by the human. FU-T07-3 |
| **L3**: append-only delivery log | Yes (unchanged) | Through `DeliveryOrchestrator` (T11/T19) | T19 suite | — | — |
| **L4 / R15** (`:8`): no secrets or tokens in messages or logs | Yes | `receiptUri`, `kmsKeyId` and addresses never enter `eventData`; the log helper carries only the topic, key and UUIDs | `shouldNeverLogAddressesReceiptUriOrAmounts` (exact lines); `shouldNotifyWithReceiptLink…`; IT `s3://` check | — | — |
| **L6**: preference default (`PAYMENT`) | Yes (unchanged) | `PreferenceResolver` through the orchestrator, category `PAYMENT` | T08/T11 suites | — | — |
| **L9** (`:13`): versioned templates, no runtime edits | Yes | `V14__seed_payment_templates_v2.sql`; V3 untouched | `paymentTemplatesHaveAVersionTwoWithoutUnfillablePlaceholders`; `launchTemplatesAreSeededWithVersionOne`; IT renders version 2 | — | — |
| **L11** (`:15`): module boundaries | Yes | `consumer` imports only `routing.PaymentRouting` (a service); the repositories are package-private | `ArchitectureTest` (`routing` in `FEATURE_MODULES`) | — | — |
| R19: consumed-contract conformance (T15's scope, extended) | Yes | Four DTOs in `consumer/dto/` | `PaymentEventContractTest`; `ConsumedEventSchemaConformanceTest` | Generated DTOs | Platform-wide `agents.md:38` gap (logged at payment T24) |
| Routing grants (frozen-brief constraint) | Yes | `V12__payment_routing_projection.sql`, `V13__…grant.sql` (INSERT, SELECT) | `notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnThePaymentRoutingTables`; `routingWritesAreInsertOnceAndTheFirstWriterWins` | — | — |
| No floating-point money (engineering addition) | Yes | `consumer/AmountFormatter.java` (`BigDecimal.movePointLeft`) | `AmountFormatterTest` (5); `shouldStillRouteAndDispatchWhen…Unparseable` | — | — |

## Principal-engineer answers

1. **Is the task fully complete?**
   - Yes for the scope frozen at Phase 4. All four topics are consumed, deduped, routed and dispatched.
   - The spec text is broader in two places: both parties (R3–R5) and the receipt link (R5). The human explicitly narrowed both (D1, D3) because the platform has no payer identity and no receipt read endpoint.

2. **Does it satisfy every acceptance criterion?**
   - Yes: AC1–AC9 of the frozen brief, each mapped to tests in `10-test-generation.md`.
   - Against the spec's own R5 wording, it is **partial**. That is disclosed, not hidden.

3. **Does it violate any LOCKED decision?**
   - No unsanctioned violation. L2 is satisfied in purpose (no live cross-service call) under the human-granted D2 interpretation. Its literal wording still needs aligning in the spec (FU-T07-3, for T20).
   - L1, L3, L4, L6, L9 and L11 hold.

4. **Remaining risks:**
   - **FU-T07-4.** During catch-up after lag, the four topics are consumed in parallel. A `payment.seen` or `payment.finalized` processed before its route exists is recorded and **never** notified, with no retry. This is pinned by `paymentSeenBeforeItsInvoiceIsRecordedButNeverDispatched` and logged at INFO.
   - **S2.** A receipt processed before its invoice stores a null `token_decimals` for good. Disclosed only.
   - **Placeholders and links.** No token symbol appears in amounts (F7). `{{invoiceLink}}` depends on `AUTH_EMAIL_LINK_BASE_URL` and an unconfirmed frontend route (Q4), the same as the auth emails.
   - **Shared-broker first run.** The auth integration tests rely on auto-created topics. A fresh `localhost:9094` broker delays the consumer group now that six listeners share it (06 disclosure 2). The new T07 integration test is isolated on its own broker.
   - **Spec drift until T20.** The R3–R5, §4c, L2 and Q7 text still describe "both parties" and a link.

**Verdict: PASS.** The frozen-brief scope is fully implemented and tested (413/413, M1–M11 killed). The R3–R5 narrowing (merchant only, no receipt link) and the L2 interpretation were decided by the human at Phase 4 and are disclosed for spec alignment in T20.
