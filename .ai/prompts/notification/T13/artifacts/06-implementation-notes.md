# notification · T13 · Phase 6 — Implementation Notes

Implemented exactly per the Phase 5 plan, with one real deviation forced by reality — disclosed in
full below, not hidden. All Spring/Spring Security/Spring MVC/Spring TX API shapes (`SseEmitter`,
`ResponseBodyEmitter`, `Jwt`/`JwtClaimAccessor`, `ProblemDetail`, `TransactionSynchronizationManager`/
`TransactionSynchronization`) were verified directly against the actual jars on the local Maven repo
(via `mvn dependency:tree` + `javap`) before writing any code, matching the discipline already
established in T12.

## Files created

- `inapp/InappNotification.java` — entity, one public constructor (mirrors `DeliveryLog`'s own
  precedent, T11), nested `View` record + `toView()` (the one shared DTO both controllers serialize).
- `inapp/InappNotificationRepository.java` — `public` (documented reasoning: the first repository in
  this module with a genuine cross-package production consumer).
- `inapp/InappStreamRegistry.java` — `ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>`;
  `register`/`push`/`deregister`.
- `channel/InAppChannel.java` — real, replaces `NoOpInAppChannel`; `@Transactional`; derives `title`
  from `body` (100-char truncation); `link` always `null`; defers the SSE push to
  `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()`. Also validates
  `message.body()` proactively (mirroring `EmailChannel`'s own T12 Phase 9 fix, applied here from the
  start rather than waiting for an identical Kimi finding).
- `common/ApiExceptionHandler.java` — `@RestControllerAdvice`; nested
  `InvalidSubjectClaimException`; handles a malformed `sub` (400) and any other unexpected exception
  (500, generic detail, no stack trace) via Spring's own `ProblemDetail`.
- `inapp/InappStreamController.java` — `GET /notifications/stream`, SSE, scoped to the JWT's own
  `sub`.
- `inapp/InappReadController.java` — `GET /notifications/unread`, JSON, same scoping.

## Files modified

- `channel/NotificationChannel.java` — `send` gains a `String category` parameter (Finding #1).
- `delivery/DeliveryOrchestrator.java` — one line, passes `mapping.category()` at the call site.
- `channel/EmailChannel.java` — signature updated, parameter accepted and ignored.
- `delivery/DeliveryOrchestratorTest.java` / `channel/EmailChannelTest.java` — mechanical updates for
  the new parameter (~24 call sites total).
- `T01SkeletonRegressionTest.java` — authorized file-inventory list (32 → 45 files across T11-T13).

## Files deleted

- `channel/NoOpInAppChannel.java`, `channel/NoOpInAppChannelTest.java` (pre-authorized since T11's
  own Javadoc).

## Deviation from the plan — a genuinely missing DB grant, found empirically

**Not anticipated by Phase 4 or Phase 5.** The full test suite's first run after implementation
failed with 6 real errors, all `org.springframework.transaction.UnexpectedRollbackException:
Transaction silently rolled back because it has been marked as rollback-only` — thrown by
`DeliveryOrchestrator.dispatch`'s own outer `@Transactional` proxy at commit time, *after* `dispatch`
itself had already returned normally (a genuine, if narrow, violation of AC9's "never throws"
guarantee, surfaced by Spring's own AOP machinery rather than any code path this task wrote).

Root-caused with `TRACE`-level Spring transaction logging (not guessed): `InAppChannel.send`'s own
`repository.save(notification)` failed with a real Postgres error,
`ERROR: permission denied for table inapp_notifications` — `notification_app` had **never been
granted** `INSERT`/`SELECT` on `inapp_notifications`. Every prior table this pipeline has written to
(`processed_events`, `contact_projection`, `channel_preferences`, `templates`) received its own
dedicated grant migration (`V4`-`V7`) in the task that first needed it; `inapp_notifications` simply
never had a task that needed it before this one. Once that inner exception occurred, Spring's own
declarative transaction advice around `InAppChannel.send` (itself `@Transactional`, joining the
outer transaction) marked the *shared* transaction `rollback-only` — a well-known Spring behavior:
an unchecked exception escaping any `@Transactional`-proxied method marks the transaction for
rollback regardless of whether an outer caller (`dispatchOneChannel`'s own `catch (Exception e)`)
later catches that same exception before it reaches the outermost boundary. `dispatch()` itself
never saw an exception it didn't already handle — but its own commit, at the very end, discovered
the poisoned flag and threw.

**Fixed** by adding `db/migration/V8__notification_app_inapp_notifications_grant.sql`
(`GRANT INSERT, SELECT ON notifications.inapp_notifications TO notification_app;`), mirroring V4-V7's
own established shape and commentary exactly. This is a new Flyway migration neither the frozen
brief nor the implementation plan authorized, because neither Phase 3's adversarial review nor this
task's own planning caught it — a real gap in review coverage, not a design decision reversed.

This also required updating `NotificationBaselineMigrationIntegrationTest` (T02, extended by every
grant-adding task since): `inapp_notifications` moved from `UNGRANTED_TABLES` to its own dedicated
`notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnInappNotifications` test (mirroring
`processed_events`'s own identical INSERT+SELECT-only shape), the Flyway-history version list now
expects `"8"`, and the three now-stale `case "inapp_notifications" -> ...` branches in the
`UNGRANTED_TABLES`-only SQL-fixture generator methods were removed as dead code.

## Mapping to acceptance criteria

- **AC1**: `InAppChannel implements NotificationChannel`, `channel()` returns `"IN_APP"`;
  `NoOpInAppChannel`/its test deleted.
- **AC2**: `send` persists every field, `notification_uuid` freshly generated, `createdAt` from
  `Clock`.
- **AC3**: push attempted after persistence; a missed push never fails `send` (nothing in
  `InappStreamRegistry.push` can propagate to its caller).
- **AC4/AC5**: both controllers parse `jwt.getSubject()` exclusively; no path/query parameter carries
  an account identifier anywhere.
- **AC6**: verified empirically — every controller test (Phase 10's own scope) will exercise the
  real, already-existing `ResourceServerConfig` chain, not a mock of it.
- **AC7**: `category` passed through; `title` derived (100-char truncation); `link` always `null`.
- **AC8**: `@Transactional` on `send`; push deferred via `TransactionSynchronizationManager`.
- **AC9**: malformed `sub` → 400 via `ApiExceptionHandler`.
- **AC10**: `GET /notifications/stream` (SSE, event name `notification`) and
  `GET /notifications/unread` (JSON), sharing `InappNotification.View`.

## Verification

- `mvn -pl services/notification clean compile` / `test-compile` — clean.
- `mvn -pl services/notification clean verify` — 271 tests, 0 failures, 0 errors (270 + 1 new grant
  test; no other new tests yet — that is Phase 10's own scope). The failure-then-fix cycle above is
  itself a real, empirical proof this task's own persistence path genuinely works end-to-end against
  a real Postgres instance under the real `notification_app` role, not merely that the code compiles.
