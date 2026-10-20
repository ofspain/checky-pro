# open-issues sweep · Phase 10 — Test Manifest

Consumes:
- `artifacts/07-self-review.md` (fixes 1–21)
- `artifacts/09-review-resolution.md` (Kimi findings F1–F10, including F7's durable record)

Range: `8d8236da..6ed5c59f` (28 commits). The sweep wrote its tests with each fix, so this phase adds no new tests. It maps every fix to the tests that guard it, and to the deliberate breaks those tests caught.

**How deliberate breaks were run.** One real production line is changed, the named tests are run, and the file is restored from a backup. A break counts as caught only if at least one named test fails. The break lists were kept outside the repository, so the descriptions below are the record.

## Manifest by fix

Test paths are under `services/<service>/src/test/java/com/themistra/<service>/` unless a full path is given.

### crypto

| Fix | Tests | Verifies | Breaks caught |
|---|---|---|---|
| 1 — unregister keeps finality/reorg tracking (`1b64b6d7`) | `watch/WatcherTest`: `stopMatchingStopsNewObservationsButKeepsPollingTheSeenTransactionToFinality`, `aTrackingOnlyWatcherIgnoresNewObservations`, `aTrackingOnlyWatcherStillReportsFinalityForItsMatchedTransaction`, `aTrackingOnlyWatcherStillReportsAReorgOfItsMatchedTransaction`, `anObservationAlreadyInFlightWhenMatchingStopsIsIgnored`, `aRestartedWatcherDoesNotReloadAnAlreadyFinalizedCursor`, `reloadingPendingFinalityRetriesOnTheNextTickAfterACursorReadFailure`; `watch/WatcherRegistryTest`: `anUnregisteredWatchWithASeenUnfinalizedTransactionKeepsATrackingOnlyWatcherUntilItSettles`, `aReplicaStartsATrackingOnlyWatcherWithNoSubscriptionForAWatchItNeverRan`; `watch/WatchRepositoryIntegrationTest.findUnregisteredWithUnfinalizedSeenTransactionListsOnlyStoppedWatchesStillAwaitingFinality` | After unregister the watcher matches nothing new, but still reports finality and reorg for the transaction it saw. After a restart, pending finality reloads from the cursor. | 12 |
| F1 — the same flow end to end (`8d5f86bc`) | `watch/EndToEndIntegrationTest.anUnregisteredWatchStillEmitsFinalizedForItsSeenTransactionButMatchesNothingNew` | HTTP register, `chain.tx.seen`, HTTP DELETE, reconcile, then `chain.tx.finalized` on the real topic and no seen event for a second transaction. Uses a real `WatcherRegistry`, real Postgres and Kafka, and fake chain adapters. The registry's repository results are narrowed to this test's watch. | 2 (`keepTrackingOnly` removed; `stopMatching` keeps matching) |
| 2 — `QuorumEvaluator` bean; 11 failing tests (`af3c1132`) | the 11 restored tests (repository integration tests and `T01SkeletonRegressionTest`) | The context starts, and each repository test runs against its migrated schema. | 5 |
| F4 — startup with no beans replaced (`8d5f86bc`) | `ApplicationStartupIntegrationTest.theApplicationStartsWithEveryBeanReal` | Exactly one bean each of `WatcherRegistry`, `ObservationSnapshotStore`, `KmsSigner`, `ScreeningClient`, `HeldFactAlerter`, `QuorumEvaluator`, and none of them a Mockito class. | 1 (`HeldFactAlerter` not a bean) |
| 3 — inert ArchUnit annotations (`1796f7e6`, `ee06a3e8`) | `attest/KmsSignerArchitectureTest`, `common/CrossModuleEntityArchitectureTest` | Each plain `@Test` canary enforces its rule. | each allowlist entry removed |

### auth

| Fix | Tests | Verifies | Breaks caught |
|---|---|---|---|
| 4 — 8 rules without canaries (`238614f1`) | `ArchitectureTest`: the eight `…IsCheckedDuringStandardBuild` canaries | Each rule runs in the standard build. | 2 |
| 5 — role denial was a 500 (`3f05acb2`) | `account/AdminAccountAuthorizationIntegrationTest`: `aCallerWithoutAnAdminRoleGetsA403ProblemNotA500`, `anAdminCallerStillGetsTheAccount` | Real HTTP: 403 problem without detail; an admin still gets 200. It failed with a 500 before the fix. | 1 |
| 6 — 7 failing integration tests (`a4e3f1c0`) | `TestcontainersConfiguration`, `EndToEndLifecycleIntegrationTest`, `SasLoginIntegrationTest`, `RateLimitIntegrationTest`, `AccountPersistenceIntegrationTest` | The producer uses `@ServiceConnection`, and no `TestRestTemplate` request factory leaks between tests. | 2 |
| F6 — flaky audit test (`8d5f86bc`) | `audit/AuditTrailIntegrationTest.recordMirrorsToKafkaWithoutLeakingIpOrUserAgent` | Records are buffered across polls, the suspended record is chosen by key and type, and no record for the account carries an IP or user agent. | none (timing; see 09) |

### notification

| Fix | Tests | Verifies | Breaks caught |
|---|---|---|---|
| 7 — dead email links (`bb8fe26f`) | `template/TemplateRendererTest`: `everyComputedLinkTargetsTheSpaRoutes`, `everyLinkPathIsAFrontendRoute`; `template/TemplateRendererIntegrationTest` | Links use `/app/verify?token=`, `/app/reset?token=`, `/app/invoices/<uuid>`, and each path is a route in `spec/frontend/design.md`. | 3 |
| 9 — receipt email link, V15 (`3ef39a09`) | `NotificationBaselineMigrationIntegrationTest.receiptEmailHasAVersionThreeWithTheReceiptLink`, the consumer unit and Kafka integration tests | Template v3 carries `/app/receipts/<uuid>`. | 5 |
| 10 — in-app verify stored the raw token, V16 (`db341066`) | `NotificationBaselineMigrationIntegrationTest.inAppVerifyHasAVersionTwoWithoutTheVerificationLink`, `common/SecretLeakEndToEndIntegrationTest`, `template/TemplateRendererIntegrationTest` | No token reaches the in-app row, the delivery log or the logs. | 3 |
| 11 — late decimals never stored, V17 (`3a5d4007`) | `consumer/PaymentEventConsumerIntegrationTest.missingDecimalsAreFilledLaterButNeverOverwritten`, grant tests | Decimals are filled once and never overwritten; the UPDATE grant covers that one column only. | 6 |
| 12 — event before its route was dropped, V18 (`3c8666e3`) | `consumer/PaymentEventConsumerTest`: `invoiceCreatedReplaysParkedEventsInOrderAndReleasesThem`, `aParkedEventThatStillHasNoRouteStaysParked`, `receiptIssuedReplaysParkedEventsBeforeItsOwnDispatch`, `paymentSeenReplaysOnlyWhenItDispatches`; `PaymentEventConsumerIntegrationTest.eventsArrivingBeforeEveryRouteAreReplayedInLifecycleOrder`; `NotificationBaselineMigrationIntegrationTest.notificationAppCanInsertSelectAndDeleteButNotUpdateParkedPaymentEvents` | Park, replay in lifecycle order, release. | 8 |
| 20 — parked events never expired (`ef3edda7`) | `routing/ParkedEventExpiryJobTest`: `expiresEventsOlderThanTheMaxAgeAndLogsAndCountsEachOne`, `nothingToExpireLogsNothing`, `runsOnTheConfiguredIntervalUnderAShedLock`; `PaymentEventConsumerIntegrationTest.expiryRemovesOnlyEventsParkedBeforeTheCutoff` | Only events older than the cutoff go; each is logged (key only) and counted. | 5 |
| F7 — durable expired record, V19 (`6ed5c59f`) | `ParkedEventExpiryJobTest.aRecordThatCannotBeWrittenRollsTheWholeRunBack`; `PaymentEventConsumerIntegrationTest`: `theExpiryJobLeavesADurableExpiredRecordInTheDeliveryLog`, `anExpiredRecordCannotBeWrittenOutsideATransaction`; `NotificationBaselineMigrationIntegrationTest.deliveryLogAcceptsAnExpiredRowWithoutAChannelAndNothingElseWithout`; `T01SkeletonRegressionTest` (file allowlist) | One `EXPIRED` row per expired event, written in the deleting transaction; a failed write rolls the run back. The channel is null exactly when the outcome is `EXPIRED`. UPDATE stays denied. | 7 (listed in 09) |
| F5 — test controller profile gate (`8d5f86bc`) | `common/PublicEndpointsTest`, `common/ResourceServerConfigIntegrationTest` activate `resource-server-test-controller` | The test controller loads only for those two tests. | none (hygiene) |
| 19 — stale spec notes (`92a4df54`) | `T01SkeletonRegressionTest` spec guards | Notes match fixes 9–12. | 3 |

### payment

| Fix | Tests | Verifies | Breaks caught |
|---|---|---|---|
| 8 — merchant receipt read (`07f4347e`) | `receipt/ReceiptReadServiceTest` (9), `receipt/ReceiptControllerTest` (7), `receipt/ReceiptRepositoryIntegrationTest.shouldFindReceiptByItsReceiptUuid`, `common/PaymentsOpenApiContractTest`: `receiptUuidPathParameterIsDeclared`, `receiptResponseSchemaListsTheCanonicalFieldsInSignedOrder` | Owner gets the stored signed receipt in canonical order. Another merchant's receipt and an unknown one give the same 404, and S3 is never read for them. A stored object for another merchant or receipt, or a malformed one, is a storage fault and never returned. | 11 |
| F10 — receipt read end to end (`8d5f86bc`) | `PaymentEndToEndIntegrationTest.shouldDriveInvoiceToAttestedReceiptEndToEnd` (new final step) | Over real HTTP, Postgres and S3: owner 200; another merchant 404; unknown 404; the two 404 bodies equal apart from `instance`. | 1 (ownership check skipped) |
| 15 — missing `tokenDecimals` bound to 0 (`30fcfe3f`) | `invoice/InvoiceControllerTest.shouldRejectAMissingOrNullTokenDecimalsInsteadOfDefaultingToZero`, `PaymentsOpenApiContractTest` | A missing or null value is a 400. | 2 |
| 17 — spurious JSON UPDATE (`99c34a1f`) | `verification/VerificationRecordRepositoryIntegrationTest.shouldSaveADetailHoldingShortAndLongValuesWithoutASpuriousUpdate` | Saving a detail with `Short` values issues no UPDATE. It failed with "permission denied" before the fix. | reproduced first |

### Cross-service

| Fix | Tests | Verifies | Breaks caught |
|---|---|---|---|
| 13 — Docker images failed to build (`bfc1796e`) | `DockerfileModulePomsTest` in auth, crypto, notification: `dockerfileCopiesThePomOfEveryReactorModule`, `dockerfileBuildsOnlyThisModule` | Each Dockerfile copies every reactor module POM and builds only its module. All three images were also built once by hand. | 6 |
| 14 — JWKS URI was the container's loopback (`61e20d5b`) | `jwkSetUriIsOverridableByEnvironment` in payment (`ApplicationPropertiesContentTest`), crypto and notification (`ApplicationPropertiesSecurityConfigTest`) | The URI comes from an environment variable with a local default. | 3 |
| 16, 21, F2, F3, F8 — FU-1/FU-2 rules (`8639c223`, `8e9150c7`, `f009df6c`, `57060d2f`, `8d5f86bc`) | `ControllerRulesArchitectureTest` in auth, crypto, notification; `ArchitectureTest` in payment. Each has the three rules with a canary and a fails-on-violation test, a handler count (auth 34, crypto 4, notification 2), `everyHandlerUsesAKnownAccessExpression` and its `RejectsAnAlwaysTrueRule` test. Auth, crypto and notification also check that the `permitAll()` handlers equal the `PublicEndpoints` allowlist. Fixtures: `archtestfixtures/Rogue*Controller`. `common/MethodSecurityTest` in crypto (3) and notification (2) | Every handler has a known `@PreAuthorize` expression. The public set matches the allowlist (method plus path in auth, path only in crypto and notification). Controllers never touch persistence, and nothing depends on a controller. A method-security denial is a detail-free 403, not a 500. | 15 (13 in fix 21, 2 in fix 16) plus 4 for F2 |

## Not covered by a test

- **Both services together.** No test runs crypto and payment at once. Payment's side runs against a crypto stand-in (`PaymentEndToEndIntegrationTest`), and crypto's side is F1's test. A combined test needs a cross-service harness, which is an infrastructure decision.
- **F5's profile gate** and **F6's flake fix.** See `09-review-resolution.md`.
- **Docker images.** They were built by hand once. The build does not build them.
- **Alerting on the expiry counter.** Infrastructure owns it, and it is out of scope.

## Known test-design choices a reviewer should weigh

- **F1's end-to-end test narrows the registry's repository results** to its own watch, so earlier flows in the class cannot race it. The narrowing goes through a mock `WatchRepository` that calls the real queries.
- **F7's end-to-end test expires the ShedLock row** (`lock_until` in the past) before calling the job, because the scheduler's startup run holds the lock for 10 seconds.
- **The ArchUnit tests read annotation strings.** `KNOWN_ACCESS_EXPRESSIONS` is a per-service set of exact strings.
- **Kafka waits use Awaitility timeouts** (15 seconds in auth's audit test).

## Run

From the repo root, `mvn -o -pl services/<service> verify`:

| Service | Result |
|---|---|
| auth | 781/781 |
| crypto | 792/792 |
| notification | 457/457 |
| payment | 566/566 |

All BUILD SUCCESS.
