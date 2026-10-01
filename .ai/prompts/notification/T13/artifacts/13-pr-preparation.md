# notification · T13 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T13: in-app channel + store (InAppChannel, SSE stream, unread API)`

## Commit message

```
notification-service T13: in-app channel + store

Add InAppChannel, replacing NoOpInAppChannel (pre-authorized since T11)
as the real IN_APP NotificationChannel: persists every in-app
notification to inapp_notifications and pushes it live to any
connected SSE stream for that recipient. Add InappStreamController
(GET /notifications/stream, SSE, R16) and InappReadController
(GET /notifications/unread, R17) - the service's first REST/SSE
surface and its first JWT-sub-scoped resource access anywhere in the
monorepo. Both controllers derive the caller's account exclusively from
the validated JWT's own sub claim, with no client-supplied account
identifier anywhere in either endpoint's path or parameters, so
cross-account access (L8) is rejected by construction, not by a
runtime comparison alone - proven by four independent isolation tests.

Kimi's Phase 3 adversarial review found the frozen Phase 2 brief
literally unimplementable: NotificationChannel.send had no way to
supply InappChannel's own required category/title. Resolved by adding
a category parameter to NotificationChannel.send - a disclosed,
necessary amendment to two already-frozen prior tasks' own files
(T11's NotificationChannel/DeliveryOrchestrator, T12's EmailChannel),
not scope creep. title is derived from the first 100 characters of the
rendered body (ellipsis-truncated) rather than a second interface
change, since no real IN_APP template has ever had a subject; link
stays null at launch (deep links render inline in body for now).

InAppChannel.send is @Transactional, mirroring ContactProjectionUpdater's
own established write-path convention; the live SSE push is deferred via
TransactionSynchronizationManager until after the surrounding
transaction commits, eliminating (not just documenting) the race where
a client could receive a push for a row the read API can't yet see -
proven by a real-rollback integration test. InappStreamRegistry is a
ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> with
atomic, best-effort dead-emitter cleanup (no cross-replica fan-out; a
missed push is silent, never a delivery failure - the read API stays
the durable source of truth).

A real L11 module-boundary violation (InAppChannel importing the
inapp-module entity directly) was caught independently by both
self-review and Kimi's independent review, and fixed by introducing
InappNotificationAppender as the sanctioned same-package gateway -
InAppChannel now depends on it alone.

Two genuine, empirically-discovered bugs were found and fixed:
(1) the notification_app DB role had never been granted INSERT/SELECT
on inapp_notifications (new migration V8); (2) UUID.fromString(null)
throws NullPointerException, not IllegalArgumentException - an
empty/missing JWT sub claim fell through both controllers' own
accountUuidFrom to the generic 500 handler instead of the intended
400, fixed with an explicit null/blank check before UUID.fromString.

Four adversarial review rounds (Kimi Phase 3: 10 findings, including
the interface-gap finding above; Phase 8: 10 findings, including the
real L11 violation; Phase 11: 10 gaps, including the real sub-claim
bug) were each independently verified against actual source before
disposition, never taken on word.

315 tests total (271 T01-T12 unaffected + 35 new at Phase 10 closing
Kimi's "no T13-specific tests exist" finding + 9 more in the Phase 11
addendum, including the one that caught the sub-claim bug).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/channel/InAppChannel.java`
- `services/notification/src/main/java/com/themistra/notification/common/ApiExceptionHandler.java`
- `services/notification/src/main/java/com/themistra/notification/inapp/InappNotification.java`
- `services/notification/src/main/java/com/themistra/notification/inapp/InappNotificationAppender.java`
- `services/notification/src/main/java/com/themistra/notification/inapp/InappNotificationRepository.java`
- `services/notification/src/main/java/com/themistra/notification/inapp/InappReadController.java`
- `services/notification/src/main/java/com/themistra/notification/inapp/InappStreamController.java`
- `services/notification/src/main/java/com/themistra/notification/inapp/InappStreamRegistry.java`
- `services/notification/src/main/resources/db/migration/V8__notification_app_inapp_notifications_grant.sql`
- `services/notification/src/test/java/com/themistra/notification/channel/InAppChannelTest.java`
- `services/notification/src/test/java/com/themistra/notification/inapp/InAppChannelIntegrationTest.java`
- `services/notification/src/test/java/com/themistra/notification/inapp/InappNotificationAppenderTest.java`
- `services/notification/src/test/java/com/themistra/notification/inapp/InappNotificationTest.java`
- `services/notification/src/test/java/com/themistra/notification/inapp/InappReadControllerTest.java`
- `services/notification/src/test/java/com/themistra/notification/inapp/InappStreamControllerTest.java`
- `services/notification/src/test/java/com/themistra/notification/inapp/InappStreamRegistryTest.java`

**Modified**
- `services/notification/src/main/java/com/themistra/notification/channel/EmailChannel.java` (T12
  file — `send` gains and ignores the new `category` parameter)
- `services/notification/src/main/java/com/themistra/notification/channel/NotificationChannel.java`
  (T11 file — `send` signature gains `String category`, the disclosed Finding #1 interface amendment)
- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryOrchestrator.java`
  (T11 file — one call-site line, passes `mapping.category()`)
- `services/notification/src/main/resources/db/migration/` (no other migration files touched; V8 is
  additive)
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  (T02 file — `inapp_notifications` grant coverage, 3 stale switch branches removed)
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (authorized file-inventory list)
- `services/notification/src/test/java/com/themistra/notification/channel/EmailChannelTest.java`
  (T12 file — ~7 call sites, mechanical `category` literal added)
- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryOrchestratorTest.java`
  (T11 file — ~17 `verify(...).send(...)` call sites, mechanical 4th matcher added)

**Deleted**
- `services/notification/src/main/java/com/themistra/notification/channel/NoOpInAppChannel.java`
  (pre-authorized since T11's own Javadoc)
- `services/notification/src/test/java/com/themistra/notification/channel/NoOpInAppChannelTest.java`
  (necessary consequence — its own subject class no longer exists)

**Process artifacts**
- `.ai/prompts/notification/T13/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

The fourth task in this pipeline to convert a temporary `NoOp*` placeholder into a real
implementation, and structurally the largest task in the service so far: the first REST/SSE surface
anywhere in `notification-service`, and the first JWT-`sub`-scoped resource access anywhere in the
monorepo. `InAppChannel` closes the loop `TemplateRenderer` (T09) and `DeliveryOrchestrator` (T11)
opened for the `IN_APP` channel, the same way `EmailChannel` (T12) closed it for `EMAIL`.
`DeliveryOrchestrator` itself required only the one disclosed `category`-parameter call-site change
(Finding #1) — no new branching, no new responsibility — the strongest practical proof available that
L5's "channels behind one interface" boundary survived a second real channel implementation, not only
the first.

## Testing performed

- `mvn -pl services/notification clean verify` — 315 tests, 0 failures, 0 errors, `BUILD SUCCESS`.
- A real rollback integration test (`aRolledBackTransactionLeavesNoRowAndTriggersNoPush`) proves the
  after-commit SSE push ordering against a real Postgres transaction, not a mocked boundary.
- Real `SseEmitter` instances throughout `InappStreamRegistryTest`, including empirically-verified
  (not assumed) techniques: a bare, unattached emitter silently buffers a `send()` rather than
  throwing; calling `.complete()` on it reliably makes a subsequent `send()` throw
  `IllegalStateException`, the same shape a dropped connection produces — used to exercise the real
  dead-emitter cleanup path with a real emitter, not a mock.
- A 16+16-thread concurrent register/push test on the *same* account
  (`concurrentRegisterAndPushOnTheSameAccountNeverThrowsAndLosesNoRegistration`) confirms
  `CopyOnWriteArrayList`'s snapshot-iteration semantics hold under real concurrent load, not merely
  trusted from the JDK's documented contract.
- `InAppChannelIntegrationTest` overrides `InappStreamRegistry` with a Mockito **spy** (real
  behavior, observable via `verify`), proving this task's own deferred-push timing/arguments without
  needing a live HTTP/SSE client.
- A scratch, throwaway Java program (outside Maven) confirmed `UUID.fromString(null)` throws
  `NullPointerException`, not `IllegalArgumentException`, before writing the Gap #10 fix — not
  assumed from documentation.
- A missing DB grant was found empirically via `-Dlogging.level.org.springframework.transaction=TRACE`
  surfacing a real Postgres `permission denied for table inapp_notifications` error, not guessed at.
- `git diff --stat 08bba9a..HEAD -- services/auth services/crypto services/payment` — empty; no
  sibling service touched.
- `git diff --stat 08bba9a..HEAD -- spec/` — empty; no specification file modified.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 13 ("In-app channel + store").
- **Requirements:** R16, R17.
- **LOCKED decisions:** L5, L8, L11.
- **Named tests (`package.md` §8):** `shouldStreamInAppNotificationsToAuthenticatedRecipientOnly`,
  `shouldReturnUnreadInAppNotificationsForCaller` — both present, both passing, both also proven at
  the real end-to-end integration level in addition to the `@WebMvcTest` slice level.

## Known, deliberate gaps (not this task's scope)

- **`link` is always `null` at launch** (Finding #3) — deep links render inline in `body` for now; a
  real `link` source would need a schema/template change out of this task's own scope.
- **Non-BMP (e.g. emoji) title truncation can split a UTF-16 surrogate pair** (Finding/Gap #2) — not
  currently reachable (every real launch template body is plain ASCII); disclosed and now locked by a
  dedicated test, not only a comment.
- **SSE push is best-effort and same-replica-only** (Finding #4, by design) — no cross-replica
  fan-out; a missed push is silent, never a delivery failure, consistent with AC3's already-frozen
  semantics.
- **No automated ArchUnit enforcement of L11 yet** — that is task 16's own scope
  ("ArchUnit/module boundaries"); this task's L11 compliance was verified manually, the same way every
  prior task in this service verified it before task 16 exists.
- **Retry/dead-letter logic** (task 14) and **everything downstream of task 13** are untouched — this
  task's own scope is the in-app channel and its two HTTP surfaces only.

## Reviewer notes

- **Kimi's Phase 3 Findings #1/#2 were correct, not overstated**: independently re-verified by reading
  `NotificationChannel.java`/`DeliveryOrchestrator.dispatchOneChannel` directly before accepting —
  the frozen Phase 2 brief genuinely could not be implemented without the `category` interface
  amendment.
- **Kimi's Phase 8 Finding #1 (L11 violation) was a genuine, verified violation**, independently also
  caught by this session's own self-review (Phase 7) before Kimi's review even ran — both converged
  on the same root cause and the same fix shape (a same-package gateway, mirroring
  `ContactProjectionUpdater`'s own precedent).
- **Kimi's Phase 11 Gap #10 was a real, previously-uncaught bug**, not a hypothetical edge case —
  confirmed via a scratch program that `UUID.fromString(null)` throws the "wrong" exception type, then
  fixed and locked with a new passing test in both controllers.
- **Kimi's own Phase 11 Gap #8 suggestion was a regression of an already-fixed issue**: re-adding a
  content-type assertion to the SSE stream test that had already been removed at Phase 10 after it
  was empirically found to fail (MockMvc does not set the response content type for an `SseEmitter`
  until async dispatch completes, which this deliberately-infinite stream never does) — rejected with
  that specific technical justification rather than silently re-adding a known-flaky assertion.
- **Kimi's own Phase 11 Gaps #5 and #7 were explicitly judged disproportionate**, with Kimi's own
  Gap #7 text conceding lower priority given existing coverage — both rejected with written
  justification rather than either blindly implemented or silently dropped.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T13.**
