# notification · T13 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T13 — In-app channel + store |
| **Consumes** | All prior T13 artifacts (Phases 0-11, including the Phase 11 addendum) |
| **Produces** | `artifacts/12-specification-verification.md` |

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R16** — authenticated recipient connects to the in-app stream → only that recipient's own notifications stream; unauthenticated connections rejected | Yes | `InappStreamController.java:35-38` (`stream`, scoped exclusively from `jwt.getSubject()`, no client-supplied identifier anywhere in path/params); `ResourceServerConfig` (T03, unchanged) rejects unauthenticated requests before this controller is ever reached | Named test `shouldStreamInAppNotificationsToAuthenticatedRecipientOnly` (`InappStreamControllerTest.java:53`, `InAppChannelIntegrationTest.java:186`, real end-to-end post-commit push); `rejectsAnUnauthenticatedConnection` (`InappStreamControllerTest.java:42`); `neverRegistersForAnyAccountOtherThanTheCallersOwn` (`InappStreamControllerTest.java:103`); `streamPushIsScopedToTheRegisteredAccountOnly` (`InAppChannelIntegrationTest.java:199`) | No | No |
| **R17** — authenticated recipient calls the read API → their own unread notifications returned, never another account's | Yes | `InappReadController.java:27-33` (`unread`, same `sub`-only scoping) | Named test `shouldReturnUnreadInAppNotificationsForCaller` (`InappReadControllerTest.java:54`, `InAppChannelIntegrationTest.java:129`, real end-to-end); `rejectsAnUnauthenticatedRequest` (`InappReadControllerTest.java:44`); `neverQueriesForAnyAccountOtherThanTheCallersOwn` (`InappReadControllerTest.java:77`); `neverReturnsAnotherAccountsNotifications` (`InAppChannelIntegrationTest.java:146`); `excludesANotificationThatHasAlreadyBeenRead` (`InAppChannelIntegrationTest.java:164`, Phase 11 addendum, proves `ReadAtIsNull` against a real row); `returnsNotificationsOrderedNewestFirst` (`InappReadControllerTest.java:133`, Phase 11 addendum) | No | No |
| **L5** — channels behind one interface, orchestrator channel-agnostic | Yes | `InAppChannel implements NotificationChannel` (`InAppChannel.java:32`); `NotificationChannel.send` (`NotificationChannel.java:42`) gained a `category` parameter (disclosed Phase 3/4 amendment), `DeliveryOrchestrator.dispatchOneChannel` (`DeliveryOrchestrator.java:194`) passes `mapping.category()` — `DeliveryOrchestrator`'s own dispatch logic otherwise untouched | `exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames` (`DeliveryOrchestratorIntegrationTest.java:291`, asserts exactly `EMAIL`/`IN_APP`, catching a silent component-scan regression) | No | No — the `category` parameter is a disclosed, necessary interface amendment (Phase 3 Finding #1/#2), not a violation; `DeliveryOrchestrator`'s own channel-agnostic dispatch logic required no new branching |
| **L8** — zero trust on the in-app surface; JWT validated as OAuth2 resource server against Auth JWKS, results scoped to caller's `sub`; no public endpoint except actuator | Yes | `ResourceServerConfig` (T03, unchanged, already validates every request against the real Auth JWKS before either controller runs); `InappStreamController.java:41-57`/`InappReadController.java:35-51` `accountUuidFrom` scope every query/registration exclusively to the validated token's own `sub`, with no client-supplied account identifier anywhere in either endpoint's path or parameters | `rejectsAnUnauthenticatedConnection`/`rejectsAnUnauthenticatedRequest` (401, real filter chain); `neverRegistersForAnyAccountOtherThanTheCallersOwn`/`neverQueriesForAnyAccountOtherThanTheCallersOwn`/`neverReturnsAnotherAccountsNotifications`/`streamPushIsScopedToTheRegisteredAccountOnly` (cross-account isolation, L8's own core guarantee); `aMalformedSubjectClaimResultsInABadRequestNotAnInternalError` + `anEmptySubjectClaimResultsInABadRequestNotAnInternalError` (Phase 11 addendum, the real Gap #10 fix — a null/blank `sub` no longer falls through to a 500) | No | No |
| **L11** — module boundaries; no feature module imports another feature module's entity | Yes | `InAppChannel.java:3-8` imports only `InappNotificationAppender` (same-package gateway) and `TemplateRenderer`; never imports `InappNotification`/`InappNotificationRepository` directly (verified by direct inspection — the Phase 9 fix for the real violation both self-review and Kimi's independent review caught at Phase 7/8); `InappNotificationRepository` is package-private again (`InappNotificationRepository.java:1-15`) | No dedicated ArchUnit test yet — that enforcement is T16's own scope ("ArchUnit/module boundaries," `tasks.md` task 16, not yet started); verified manually for this task via direct source inspection, consistent with how every prior task in this service has verified L11 before T16 existed | T16 will add the automated ArchUnit guard | No |
| **AC1-AC6** (Phase 1/2, unchanged) — `InAppChannel` persists `inapp_notifications`, derives `category`/`title`/`body`, pushes to connected streams, validates before any persistence | Yes | `InAppChannel.java:51-68` (validates `category`/`body`, derives `title`, delegates to the appender); `InappNotificationAppender.java:42-48` (persists via `InappNotificationRepository.save`, then defers the push) | `InAppChannelTest` (10 tests: delegation, truncation boundary, category/body validation, non-SECURITY passthrough, the known non-BMP limitation); `InAppChannelIntegrationTest` (6 tests, real DB) | No | No |
| **AC7** — `category` and derived `title` (first 100 chars of `body`, `"…"`-truncated) persisted on every row; `link` always `null` at launch | Yes | `InAppChannel.java:70-73` `deriveTitle`; `InappNotification.java` constructor hardcodes `link=null` via the channel's own call (`InAppChannel.java:67` passes no link argument — the appender's own signature has none) | `titleEqualsBodyWhenAtOrBelowTheHundredCharacterLimit`/`titleIsTruncatedWithAnEllipsisWhenBodyExceedsTheHundredCharacterLimit` (`InAppChannelTest.java`); `titleTruncationCanSplitANonBmpCharacterStraddlingTheBoundaryKnownDisclosedLimitation` (Phase 11 addendum — locks the disclosed edge case rather than merely documenting it) | No | No — the non-BMP truncation edge case is a disclosed, accepted, not-currently-reachable limitation (every real launch template body is plain ASCII), now locked by a test rather than only a comment |
| **AC8** — `InAppChannel.send`/appender `@Transactional` (default `REQUIRED`); SSE push fires only after commit, never before | Yes | `InappNotificationAppender.java:41-54` `@Transactional` + `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()` | `aRolledBackTransactionLeavesNoRowAndTriggersNoPush` (`InAppChannelIntegrationTest.java`, real rollback proof); `InappNotificationAppenderTest` (mocked branch coverage for "no active transaction → immediate push") | No | No |
| **AC9** — malformed/unparseable `sub` → `400 Bad Request` RFC 9457 via `ApiExceptionHandler`, not 401, not a raw stack trace | Yes | `ApiExceptionHandler.java:39-51` (`InvalidSubjectClaimException` → `ProblemDetail` 400); both controllers' `accountUuidFrom` throw it for a malformed UUID | `aMalformedSubjectClaimResultsInABadRequestNotAnInternalError` (both controller test classes) | No | No |
| **AC10** — paths `GET /notifications/stream` (SSE) / `GET /notifications/unread` (JSON); SSE event name `notification`; shared DTO shape | Yes | `InappStreamController.java:35`; `InappReadController.java:27`; `InappNotificationAppender.java` pushes with event name `"notification"` (`pushAfterCommit` → `streamRegistry.push(accountUuid, "notification", view)`); `InappNotification.View` is the one shared DTO both endpoints use | `shouldStreamInAppNotificationsToAuthenticatedRecipientOnly` (verifies the exact push arguments including event name); `shouldReturnUnreadInAppNotificationsForCaller` (verifies the DTO's own JSON field names) | No | No |
| **Finding #4 (Phase 3) — SSE registry concurrency/dead-emitter cleanup** | Yes | `InappStreamRegistry.java:39-80` (`ConcurrentHashMap`/`CopyOnWriteArrayList`, atomic `computeIfPresent` cleanup removing an emptied account entry) | `InappStreamRegistryTest` (9 tests, including `concurrentRegisterAndPushOnTheSameAccountNeverThrowsAndLosesNoRegistration`, Phase 11 addendum, real concurrent load) | No | No |
| **Missing DB grant (Phase 6, a real bug)** — `notification_app` needs `INSERT`/`SELECT` on `inapp_notifications` | Yes | `V8__notification_app_inapp_notifications_grant.sql` | `NotificationBaselineMigrationIntegrationTest.notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnInappNotifications` | No | No |

## Answers

**(1) Is the task fully complete?** Yes. Every file in the frozen brief's own "Files to Create /
Modify / Delete" list exists in its final form; `NoOpInAppChannel`/its test are confirmed deleted.
The task went through 3 rounds of adversarial review (Kimi Phases 3, 8, 11) plus this session's own
self-review (Phase 7), with every finding fixed, correctly rejected with evidence, or explicitly
disclosed as an accepted, out-of-scope limitation. Two genuine, empirically-discovered production
bugs were found and fixed along the way: the missing `notification_app` DB grant (Phase 6) and the
`UUID.fromString(null)` NPE-vs-`IllegalArgumentException` mismatch in both controllers'
`accountUuidFrom` (Phase 11 addendum, Kimi Gap #10).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC10, see matrix above. AC8
(the after-commit push ordering) is the most structurally significant guarantee this task carries and
is proven by a real rollback test against a real Postgres transaction
(`aRolledBackTransactionLeavesNoRowAndTriggersNoPush`), not a mocked transaction boundary. AC9 is now
proven for both the malformed-UUID case (Phase 10) and the previously-uncaught null/blank-subject
case (Phase 11 addendum) — the gap between "spec says 400" and "code actually returns 400 for every
real-world shape of a bad `sub`" was closed, not merely assumed from the happy-path test.

**(3) Does it violate any LOCKED decision?** No. L5 holds — `DeliveryOrchestrator`'s own dispatch
logic gained exactly one call-site argument (`mapping.category()`, already a local variable there)
and no new branching; the channel-agnostic property survives, confirmed by the bean-count regression
test still asserting exactly two channels. L8 holds — every endpoint validates the real JWT via the
existing `ResourceServerConfig` filter chain and scopes exclusively to the token's own `sub`, with no
client-supplied account identifier anywhere, proven by four independent cross-account-isolation
tests. L11 holds — `InAppChannel` depends only on the same-package `InappNotificationAppender`
gateway, not on another feature module's entity directly; this was a real violation at Phase 6,
caught independently by both self- and Kimi review, and fixed at Phase 9. (L11's *automated*
enforcement via ArchUnit is T16's own future scope, not yet built — verified manually here, as every
prior task in this service has done before T16 exists.)

**(4) Remaining risks?**
- **`link` is always `null` at launch** (Finding #3, Phase 3/4) — a disclosed, accepted launch
  limitation, not a defect. Deep links render inline in `body` for now; adding a real `link` source
  would require a schema/template change out of this task's own scope.
- **Non-BMP title truncation can split a surrogate pair** (Finding/Gap #2) — a disclosed, accepted,
  not-currently-reachable limitation (every real launch template body is plain ASCII), now locked by
  a test rather than only a Javadoc comment, so a future change to this behavior will be a deliberate,
  visible decision, not a silent regression.
- **SSE push is best-effort, same-replica-only** (Finding #4, by design) — a missed push because the
  account's connection lives on a different replica than the one handling `dispatch` is accepted and
  silent, never surfaced as a delivery failure (consistent with AC3's own already-frozen semantics);
  the read API remains the durable source of truth regardless of any missed push.
- **`InappStreamRegistry`'s own `onTimeout`/`onError` callback wiring** (Kimi Gap #5) is not
  exercised by a dedicated test — rejected at Phase 11 addendum as disproportionate (would require
  reflecting two layers into Spring-private `ResponseBodyEmitter` fields for a one-line, directly
  reviewable lambda); `push()`'s own already-tested reactive cleanup path provides the same
  end-user-visible guarantee regardless of which callback actually fires.
- **No live HTTP/SSE client test exists** (Kimi Gap #7, also considered at Phase 10) — rejected both
  times as disproportionate given the existing spy-based and real-emitter-based coverage already
  proves this task's own code; Kimi's own text conceded this is lower priority.

## Verdict

**PASS** — T13 fully satisfies R16 and R17, and every locked decision (L5, L8, L11) and acceptance
criterion (AC1-AC10) it touches. The two genuinely new architectural challenges this task introduced
— the first REST/SSE surface in this service, and the first JWT-`sub`-scoped resource access anywhere
in the monorepo — are both soundly built and tested, including a cross-task interface amendment
(`NotificationChannel.send`'s new `category` parameter) that was surfaced, disclosed, and rippled
correctly into two already-frozen prior tasks' own files. Two real, empirically-discovered bugs (a
missing DB grant; a JWT-subject NPE) were found and fixed before this task closed, not left for a
future task to discover. The full suite is green at 315 tests, 0 failures, 0 errors.
