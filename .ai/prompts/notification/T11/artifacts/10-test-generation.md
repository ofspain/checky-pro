# notification · T11 · Phase 10 — Test Generation

Full Phase 10 test set for `DeliveryOrchestrator`, closing Kimi Phase 8 Finding #1 ("no committed
T11-specific tests exist"). No production code changed this phase. 3 new test files / additions,
30 new tests (226 total: 196 T01-T10/Phase-9 unaffected + 30 new).

## Files created / modified

- `delivery/DeliveryOrchestratorTest.java` (**new**) — 18 tests, plain JUnit + Mockito, no Spring
  context, no Docker. Mocked `PreferenceResolver`/`TemplateRenderer`/`ContactProjectionUpdater`/
  `DeliveryLogRepository`/`NotificationChannel`, fixed `Clock`. Placed in `delivery` (same package as
  `DeliveryLogRepository`, which stays package-private per Phase 9's rejection of Kimi Finding #3 —
  no visibility change, same-package test placement instead, mirroring every sibling module's own
  convention).
- `delivery/DeliveryOrchestratorIntegrationTest.java` (**new**) — 7 tests, `@Testcontainers` +
  `@SpringBootTest` + a `FixedClockConfig` import (mirrors `IdempotencyGuardIntegrationTest`'s own
  pattern exactly). Real Postgres, real `PreferenceResolver`/`TemplateRenderer`/
  `ContactProjectionUpdater`/`DeliveryLogRepository`/`NoOpEmailChannel`/`NoOpInAppChannel` — no mocks.
- `preference/ContactProjectionUpdaterIntegrationTest.java` (**modified**) — 5 new tests for
  `findEmail`/`findDisplayName` (Phase 9's own new read paths, previously untested).

## Test manifest — `DeliveryOrchestratorTest` (unit)

| Test method | Verifies | AC / Finding |
|---|---|---|
| `dispatchIsAnnotatedTransactionalWithDefaultRequiredPropagation` | `@Transactional`, `REQUIRED` | AC10, Finding #1 |
| `unknownNotificationKindWritesNoRowsAndTouchesNoCollaborator` | Unrecognized kind is a true no-op | AC2 |
| `nullNotificationKindWritesNoRowsAndTouchesNoCollaborator` | Same, `null` input | AC2 (defensive) |
| `shouldRecordEveryDeliveryAttemptAndOutcomeInLog` | Named test (R11) — every required field on a `SENT` row, both channels | R11, AC7 |
| `shouldSuppressChannelWhenRecipientOptedOut` | Named test (R10) — suppression reaches the log, other channel unaffected | R10, AC3, Finding #4 |
| `missingEmailFailsEmailChannelWithoutRenderOrSendButInAppStillProceeds` | Missing projection: `EMAIL` FAILED, `IN_APP` unaffected | AC5, Finding #3 |
| `inAppRecipientIsAlwaysTheAccountUuidStringEvenWhenEmailIsPresent` | `IN_APP` recipient never leaks the email | Finding #2/#6 |
| `renderFailureRecordsFailedRowSkipsSendAndDoesNotPropagate` | Render exception → `FAILED`, `send` never called | AC4 |
| `renderFailureErrorDetailIsRedactedBeforePersistence` | A secret-shaped render-exception message is redacted | Finding #5 |
| `missingChannelBeanRecordsFailedRowWithTheAlreadyRenderedTemplateVersionAndSkipsSend` | No bean for a channel → `FAILED`, template name/version still recorded (rendered before the bean lookup) | Frozen brief Finding #7 |
| `channelSendFailureRecordsFailedRedactedRowAndOtherChannelStillProceeds` | `send` exception → `FAILED`, redacted, other channel unaffected | AC6, Finding #5 |
| `aGenuineErrorFromAChannelPropagatesOutOfDispatchInsteadOfBeingSwallowed` | A real `Error` (not `Exception`) propagates — the deliberate, documented exception | Finding #7 |
| `findEmailThrowingRecordsFallbackFailedRowPerLaunchChannelWithoutPropagating` | Pre-loop failure still records one `FAILED` row per launch channel | Finding #4 |
| `findEmailThrowingWithANullSourceEventKeyFallsBackToASyntheticKey` | `sourceEventKey` null + pre-loop failure → `"unknown:" + accountUuid` | Finding #4/#6 |
| `dispatchNeverThrowsEvenWhenTheFallbackSaveItselfThrows` | Fallback `save` itself throwing still doesn't propagate | Finding #4's own inner safety net |
| `displayNameIsMergedIntoRenderDataWhenPresentWithoutDroppingOriginalKeys` | `displayName` merged in, original `eventData` keys preserved | Finding #2 |
| `renderDataEqualsTheOriginalEventDataWhenDisplayNameIsAbsent` | No `displayName` key added when absent | Finding #2 |
| `nullEventDataProducesAnEmptyRenderDataMapWithoutThrowing` | `eventData == null` handled safely | AC9 (defensive) |

## Test manifest — `DeliveryOrchestratorIntegrationTest` (real Postgres, real Spring context)

| Test method | Verifies | AC / Finding |
|---|---|---|
| `shouldRecordEveryDeliveryAttemptAndOutcomeInLog` | Named test (R11), real end-to-end render, all required fields on a real persisted row | R11 |
| `shouldSuppressChannelWhenRecipientOptedOut` | Named test (R10), real stored opt-out (`PAYMENT`/`EMAIL`, not the `SECURITY`/`EMAIL` hard floor) | R10 |
| `missingContactProjectionFailsEmailChannelButInAppStillProceeds` | Real DB, no projection row | AC5, Finding #3 |
| `unknownNotificationKindWritesNoRowsInARealDatabase` | Real DB, zero rows for an unknown kind | AC2 |
| `dispatchSucceedsEndToEndWhenDisplayNameIsPopulated` | Full real round-trip: JDBC-set `display_name` → `findDisplayName` → merged render, no error | Finding #2 |
| `deliveryLogRowsRollBackIfTheExternalCallersTransactionRollsBack` | AC10 — joins caller's transaction, rolls back with it | AC10, Finding #1 |
| `deliveryLogRowsCommitWhenTheExternalCallersTransactionCommits` | AC10 — commits with it | AC10 |

## Test manifest — `ContactProjectionUpdaterIntegrationTest` (additions)

| Test method | Verifies |
|---|---|
| `findEmailReturnsEmptyWhenNoRowExists` | `findEmail` absent case, real DB |
| `findEmailReturnsTheStoredEmailWhenPresent` | `findEmail` present case, real DB |
| `findDisplayNameReturnsEmptyWhenNoRowExists` | `findDisplayName` absent case (no row) |
| `findDisplayNameReturnsEmptyWhenARowExistsButDisplayNameIsStillNull` | `findDisplayName` absent case (row exists, column still null — the common case) |
| `findDisplayNameReturnsTheStoredValueWhenPresent` | `findDisplayName` present case — value set directly via JDBC, since no application write path populates this column today |

## Kimi Phase 8 Finding #3 — disposition carried into this phase

`DeliveryLogRepository` stays package-private, per Phase 9's rejection of Kimi's own recommendation
(its cited justification was verified false). `DeliveryOrchestratorTest`/
`DeliveryOrchestratorIntegrationTest` are placed in `com.themistra.notification.delivery` for
exactly this reason — the same, already-established pattern every other module test follows.

## Verification

`mvn -pl services/notification clean verify` — 226 tests, 0 failures, 0 errors (196 pre-existing +
30 new: 18 + 7 + 5). No production code was modified in this phase.
