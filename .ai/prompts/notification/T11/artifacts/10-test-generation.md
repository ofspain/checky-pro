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

## Addendum (post Phase 11) — 1 of 8 gaps was a real bug, fixed with a real code change; 7 covered with new tests

Kimi's Phase 11 review raised 8 gaps. All verified against source before acting.

- **Gap #1 / Gap #5 turned out to be the same real, live bug**, not merely hypothetical: a direct
  read of `dispatchOneChannel`'s own source showed its outer `catch (Exception e)` (the one wrapping
  `preferenceResolver.resolve`, both `save()` calls in the render/missing-channel-bean branches, and
  the final `send`/`save` pair) only logged — it never recorded a fallback `FAILED` row, unlike
  `dispatch`'s own outer catch (Finding #4). Concretely: if `preferenceResolver.resolve` throws, or
  if any of this method's own `save()` calls throws (most plausibly a `NOT NULL` violation from a
  missing `sourceEventKey`, Finding #6), that channel's delivery attempt silently disappears from
  the log entirely — a direct violation of R11's "every delivery attempt is recorded." **Fixed**:
  `dispatchOneChannel`'s own outer catch now carries the identical fallback-save pattern as
  `dispatch`'s own outer catch (synthetic-key fallback + its own inner safety net). Added
  `preferenceResolverThrowingRecordsAFailedRowForThatChannelAndDoesNotPropagate` (mutation-style
  proof: stubs the resolver to throw and asserts a `FAILED` row now appears where none did before).
- **Gap #2** (no dedicated channel-bean tests) — **fixed**: added
  `channel/NoOpEmailChannelTest.java` / `channel/NoOpInAppChannelTest.java` (3 tests each — `channel()`
  value, `send` completes normally with token-bearing content, a source-scan locking that the log
  statement passes the whole `message` object, never `.subject()`/`.body()` directly) plus
  `DeliveryOrchestratorIntegrationTest.exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames`
  (a real Spring context proof that exactly two beans are scanned).
- **Gap #3** (both channels suppressed simultaneously) — **fixed**: added
  `bothChannelsSuppressedRecordsTwoSuppressedRowsAndNeitherRendersNorSends`.
- **Gap #4** (`displayName` precedence when `eventData` already has the key) — **documented, not
  changed**: current behavior (the real projection value always wins over a caller-supplied one) is
  the correct, safer choice and is unreachable in production today (`AuthEventConsumer` never
  supplies this key). Locked with
  `callerSuppliedDisplayNameInEventDataIsOverriddenByTheProjectionValueWhenBothArePresent`.
  No production change.
- **Gap #6** (no proof the rendered body actually contains `displayName`) — **fixed, with a
  disclosed scope narrowing**: `delivery_log` never persists the rendered body, so
  `DeliveryOrchestratorIntegrationTest.displayNameActuallySubstitutesIntoARealRenderedBody` replicates
  `DeliveryOrchestrator`'s own real `findDisplayName`-then-merge step against the real
  `TemplateRenderer` bean it uses internally and asserts the substitution really happens — it does
  not call `dispatch` itself, since there is no queryable persisted body to assert against.
- **Gap #7** (payment-derived mappings not exercised with their template variables) — **fixed**:
  added `paymentDerivedMappingForwardsAllEventDataKeysUnchangedToBothChannels`.
- **Gap #8** (no lock on the exact 7-entry VERBATIM mapping table) — **fixed**: added
  `notificationMappingsTableContainsExactlyTheSevenVerbatimEntriesAndExcludesAccountSuspended`, a
  source-scan test (mirrors the established static-guard convention, e.g.
  `IdempotencyGuardIntegrationTest.insertIfNewUsesOnConflictDoNothing`).

**Files touched this addendum:**
- `delivery/DeliveryOrchestrator.java` (production) — the one real fix (Gap #1/#5).
- `delivery/DeliveryOrchestratorTest.java` — +5 tests (Gaps #1, #3, #4, #7, #8).
- `delivery/DeliveryOrchestratorIntegrationTest.java` — +2 tests (Gaps #2, #6).
- `channel/NoOpEmailChannelTest.java`, `channel/NoOpInAppChannelTest.java` (**new**) — 3 tests each
  (Gap #2).

**Verification:** `mvn -pl services/notification clean verify` — 239 tests, 0 failures, 0 errors
(226 + 13 new: 5 + 2 + 3 + 3).
