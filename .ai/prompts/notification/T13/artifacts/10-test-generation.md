# notification · T13 · Phase 10 — Test Generation

Full Phase 10 test set for T13 (`InAppChannel`/`InappNotificationAppender`/`InappStreamRegistry`/
`InappStreamController`/`InappReadController`/`InappNotification`), closing Kimi Phase 8 Finding #2
("no T13-specific tests exist"). No production code changed. 8 new test files, 35 new tests (306
total: 271 T01-T12/Phase-9 unaffected + 35 new).

## Files created

- `channel/InAppChannelTest.java` — 8 tests, plain JUnit, mocked `InappNotificationAppender`.
- `inapp/InappNotificationTest.java` — 4 tests, plain JUnit.
- `inapp/InappNotificationAppenderTest.java` — 2 tests, mocked repository/registry (the
  "synchronization active → deferred" branch needs a real transaction manager and is proven at the
  integration level instead — see below).
- `inapp/InappStreamRegistryTest.java` — 6 tests, plain JUnit, real `SseEmitter` instances +
  reflection on the registry's own private state (no public accessor exists, nor should one for
  production callers — see the file's own Javadoc for why this is the right call here).
- `inapp/InappStreamControllerTest.java` — 4 tests, `@WebMvcTest` + real `ResourceServerConfig` +
  `spring-security-test`'s `jwt()`.
- `inapp/InappReadControllerTest.java` — 6 tests, same shape.
- `inapp/InAppChannelIntegrationTest.java` — 5 tests, Testcontainers Postgres, real Spring context,
  real `DeliveryOrchestrator` → `InAppChannel` → `InappNotificationAppender` chain, `InappStreamRegistry`
  overridden with a Mockito spy (real behavior, observable) rather than a mock.

## Test manifest

| Test method | Verifies | AC / Finding |
|---|---|---|
| `InAppChannelTest.channelReturnsInApp` | `channel()` value | AC1 |
| `InAppChannelTest.sendDelegatesToTheAppenderWithTheDerivedTitleAndFixedClockInstant` | Correct delegation | AC2/AC7 |
| `InAppChannelTest.titleEqualsBodyWhenAtOrBelowTheHundredCharacterLimit` | Truncation boundary | AC7 |
| `InAppChannelTest.titleIsTruncatedWithAnEllipsisWhenBodyExceedsTheHundredCharacterLimit` | Truncation boundary | AC7 |
| `InAppChannelTest.sendThrowsForA{Null,Blank}CategoryWithoutCallingTheAppender` (2) | Category validation | Finding #8 |
| `InAppChannelTest.sendThrowsForA{Null,Blank}BodyWithoutCallingTheAppender` (2) | Body validation | AC7 |
| `InappNotificationTest` (4) | `toView()` mapping, `readAt` always null, field integrity | AC2 |
| `InappNotificationAppenderTest.appendAndPushSavesTheCorrectRowAndPushesImmediatelyWhenNoTransactionIsActive` | Persistence fields + immediate-push branch | AC2/AC3 |
| `InappNotificationAppenderTest.eachCallGeneratesAFreshNotificationUuid` | Fresh UUID per call | AC2 |
| `InappStreamRegistryTest.registerReturnsANonNullEmitterAndAddsItToTheRegistry` | Registration bookkeeping | AC3 |
| `InappStreamRegistryTest.registerAllowsMultipleEmittersForTheSameAccount` | Multi-connection support | AC3 |
| `InappStreamRegistryTest.pushToAnAccountWithNoConnectionIsASilentNoOp` | Best-effort semantics | AC3 |
| `InappStreamRegistryTest.pushToACompletedEmitterRemovesItAndTheNowEmptyAccountEntry` | Dead-emitter + empty-map-entry cleanup | Finding #4 |
| `InappStreamRegistryTest.pushRemovesOnlyTheDeadEmitterLeavingLiveOnesInPlace` | Selective cleanup | Finding #4 |
| `InappStreamRegistryTest.concurrentRegisterAcrossDistinctAccountsLosesNoRegistration` | Thread-safety | Constraint |
| `InappStreamControllerTest.rejectsAnUnauthenticatedConnection` | 401, real filter chain | AC4/AC6 |
| `InappStreamControllerTest.shouldStreamInAppNotificationsToAuthenticatedRecipientOnly` | Named test (R16) | R16 |
| `InappStreamControllerTest.aMalformedSubjectClaimResultsInABadRequestNotAnInternalError` | 400 via `ApiExceptionHandler` | AC9 |
| `InappStreamControllerTest.neverRegistersForAnyAccountOtherThanTheCallersOwn` | Cross-account isolation | L8 |
| `InappReadControllerTest.rejectsAnUnauthenticatedRequest` | 401, real filter chain | AC5/AC6 |
| `InappReadControllerTest.shouldReturnUnreadInAppNotificationsForCaller` | Named test (R17) | R17 |
| `InappReadControllerTest.neverQueriesForAnyAccountOtherThanTheCallersOwn` | Cross-account isolation | L8 |
| `InappReadControllerTest.aMalformedSubjectClaimResultsInABadRequestNotAnInternalError` | 400 | AC9 |
| `InappReadControllerTest.anUnexpectedRepositoryFailureResultsInAGenericInternalServerErrorNotARawStackTrace` | 500, no secret/stack-trace leak (Kimi's own suggested `ApiExceptionHandlerTest` scenario, folded in here) | Finding #3 |
| `InappReadControllerTest.invalidSubjectClaimExceptionIsPubliclyConstructibleFromThisPackage` | Static compile-time guard | — |
| `InAppChannelIntegrationTest.shouldReturnUnreadInAppNotificationsForCaller` | Named test (R17), real end-to-end | R17 |
| `InAppChannelIntegrationTest.neverReturnsAnotherAccountsNotifications` | Cross-account isolation, real DB | L8 |
| `InAppChannelIntegrationTest.shouldStreamInAppNotificationsToAuthenticatedRecipientOnly` | Named test (R16), real end-to-end post-commit push | R16 |
| `InAppChannelIntegrationTest.streamPushIsScopedToTheRegisteredAccountOnly` | Cross-account push isolation | L8 |
| `InAppChannelIntegrationTest.aRolledBackTransactionLeavesNoRowAndTriggersNoPush` | AC10/Finding #6, real rollback proof | AC8 |

## A note on SSE testability (why some things are proven indirectly)

A bare, never-initialized `SseEmitter.send(...)` was verified empirically (a throwaway scratch
check, not assumed) to silently buffer rather than throw or expose any observable state - real Spring
behavior, not a limitation of this test suite. This means "did the client actually receive the right
bytes over the wire" is only provable with a live HTTP/SSE client against a real embedded server, a
disproportionately heavy setup for what this task's own code (register/push/deregister bookkeeping,
and `InappNotificationAppender`'s own deferred-push timing/argument-correctness) actually needs
proven. Two deliberate, disclosed choices close this gap without that heavy setup:
- `InappStreamRegistryTest` uses `emitter.complete()` (also verified empirically to make a
  subsequent `send()` throw `IllegalStateException`, the same shape a dropped connection produces)
  to exercise the real dead-emitter cleanup path with a real emitter, not a mock.
- `InAppChannelIntegrationTest` overrides `InappStreamRegistry` with a Mockito **spy** (real
  behavior, wrapped for verification), so the real deferred-push call's timing and arguments are
  observable via `verify(...)` without needing a live SSE client - proving this task's own code, not
  re-testing Spring's own already-trustworthy `SseEmitter` wire-level implementation.

## Verification

`mvn -pl services/notification clean verify` — 306 tests, 0 failures, 0 errors. No production code
was modified in this phase.

## Addendum (post Phase 11) — 1 of 10 gaps was a real bug, fixed; 7 covered with new tests; 2 rejected

Kimi's Phase 11 review raised 10 gaps. All verified against actual source/behavior before acting —
**Gap #10 turned out to be a real, live bug**, not a hypothetical edge case.

- **Gap #10** (no test for an empty/missing `sub` claim) — **a real bug, fixed**: verified directly
  (a throwaway scratch check) that `UUID.fromString(null)` throws `NullPointerException`, not
  `IllegalArgumentException` - unguarded, `accountUuidFrom` in both controllers let a missing/blank
  `sub` fall through to `ApiExceptionHandler`'s own generic 500 handler instead of the intended 400.
  Fixed with an explicit null/blank check before `UUID.fromString` in both controllers; added
  `anEmptySubjectClaimResultsInABadRequestNotAnInternalError` to both.
- **Gap #1** (no test locks that `ApiExceptionHandler` preserves 404 for an unmapped path) —
  **fixed**: added `anUnmappedPathIsStillA404NotAGeneric500` to both controller test classes -
  verified empirically to actually return 404, not merely trusting the Phase 9 structural `javap`
  check.
- **Gap #2** (non-BMP Unicode truncation) — **fixed, as a locked-current-behavior test, no production
  change** (matches the already-accepted-as-documented disposition from Phase 9/the self-review):
  added `titleTruncationCanSplitANonBmpCharacterStraddlingTheBoundaryKnownDisclosedLimitation`,
  making the known, disclosed limitation visible and locked in the test suite itself, not only in a
  Javadoc comment.
- **Gap #3** (read-endpoint ordering) — **fixed**: added `returnsNotificationsOrderedNewestFirst` to
  `InappReadControllerTest`.
- **Gap #4** (repository's own `ReadAtIsNull` clause never proven against a real read row) —
  **fixed**: added `excludesANotificationThatHasAlreadyBeenRead` to `InAppChannelIntegrationTest`
  (sets `read_at` directly via JDBC - no application write path does this today, out of scope).
- **Gap #5** (`onTimeout`/`onError` callback wiring untested) — **rejected**: `ResponseBodyEmitter`
  stores these as private `DefaultCallback`/`ErrorCallback` fields (verified via `javap`), two layers
  of Spring-internal types deep: reflecting into them would couple this test suite to Spring's own
  private implementation details for no real gain, since the wiring itself is a trivially-reviewable
  one-line lambda per callback, and `push()`'s own reactive cleanup path (already tested) provides
  the same end-user-visible guarantee.
- **Gap #6** (concurrent register+push on the same account) — **fixed**: added
  `concurrentRegisterAndPushOnTheSameAccountNeverThrowsAndLosesNoRegistration` to
  `InappStreamRegistryTest`.
- **Gap #7** (full `RANDOM_PORT` HTTP integration test) — **rejected**: Kimi's own text concedes this
  is lower priority given existing coverage; the disproportionate complexity/flakiness risk of a live
  HTTP+SSE client test was already weighed and declined when this phase's own tests were written
  (see "A note on SSE testability," above).
- **Gap #8** (assert SSE content type in `InappStreamControllerTest`) — **rejected, already tried**:
  this exact assertion was in an earlier draft of
  `shouldStreamInAppNotificationsToAuthenticatedRecipientOnly` and was removed after it empirically
  failed - MockMvc does not set the response content type for an `SseEmitter` until the async
  dispatch is driven to completion, which would hang for this emitter (`NO_TIMEOUT`, never calls its
  own `complete()`). `request().asyncStarted()` plus a 200 status is the correct-scope proof here.
- **Gap #9** (non-`SECURITY` category) — **fixed**: added
  `sendPassesThroughANonSecurityCategoryUnchanged` to `InAppChannelTest`.

**Files touched this addendum:**
- `inapp/InappStreamController.java`, `inapp/InappReadController.java` (production) — the one real
  fix (Gap #10).
- `channel/InAppChannelTest.java` — +2 tests (Gaps #2, #9).
- `inapp/InappStreamRegistryTest.java` — +1 test (Gap #6).
- `inapp/InappStreamControllerTest.java` — +2 tests (Gaps #1, #10).
- `inapp/InappReadControllerTest.java` — +3 tests (Gaps #1, #3, #10).
- `inapp/InAppChannelIntegrationTest.java` — +1 test (Gap #4).

**Verification:** `mvn -pl services/notification clean verify` — 315 tests, 0 failures, 0 errors.
