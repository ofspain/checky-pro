# notification · T13 · Phase 5 — Implementation Plan

Every file below traces to the frozen brief's own Files to Create/Modify/Delete. No file is added
beyond what Phase 4 authorized. No code — signatures and behavior only.

## A note on `InappNotificationRepository`'s own visibility

Every sibling repository in this module (`TemplateRepository`, `ChannelPreferenceRepository`,
`ProcessedEventRepository`, `DeliveryLogRepository`) is package-private, consumed only from within
its own package (T11 Phase 8 Finding #3's own rejected recommendation established this convention
explicitly). `InappNotificationRepository` is different in kind, not merely by choice:
`InAppChannel` (package `channel`) is a genuine, non-test, production consumer needing direct write
access, and Phase 4 did not authorize a new same-package wrapper class the way `ContactProjectionUpdater`
provides for `ContactProjectionRepository`. `InappNotificationRepository` is therefore declared
`public` — the first repository in this module with a real cross-package consumer, not an
inconsistency with the established convention but a distinct case of it.

## Files to create

### `inapp/InappNotification.java`
```
@Entity
@Table(name = "inapp_notifications", schema = "notifications")
public class InappNotification {
    protected InappNotification() // JPA only
    public InappNotification(UUID notificationUuid, UUID accountUuid, String category, String title,
                              String body, String link, Instant createdAt)
    public Long getId()
    public UUID getNotificationUuid()
    public UUID getAccountUuid()
    public String getCategory()
    public String getTitle()
    public String getBody()
    public String getLink()
    public Instant getReadAt()
    public Instant getCreatedAt()
    public View toView()

    public record View(UUID notificationUuid, String category, String title, String body,
                        String link, Instant createdAt)
}
```
One public constructor taking every field except `id` (generated) and an explicit `createdAt`
(mirrors `DeliveryLog`'s own established precedent, T11 — never the DB's own `now()` default).
`read_at` is always `null` at construction (marking read is out of this task's own scope, per
Phase 2). `toView()` maps to the nested `View` record — the one shared DTO shape both controllers
serialize (AC10); nested here, not a separate top-level file, since Phase 4 authorized no such file
and the entity is its own most natural owner.

### `inapp/InappNotificationRepository.java`
```
public interface InappNotificationRepository extends JpaRepository<InappNotification, Long> {
    List<InappNotification> findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(UUID accountUuid);
}
```

### `inapp/InappStreamRegistry.java`
```
@Component
public class InappStreamRegistry {
    public SseEmitter register(UUID accountUuid)
    public void push(UUID accountUuid, String eventName, Object data)
    private void deregister(UUID accountUuid, SseEmitter emitter)
}
```
`ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>` backing field (Finding #4) — safe for
concurrent register/deregister/iterate, since SSE connections arrive/drop on arbitrary HTTP threads
independently of the Kafka-listener threads calling `InAppChannel.send`. `register` creates a
`new SseEmitter(0L)` (no timeout — a notification stream is meant to stay open indefinitely, unlike
Spring's own ~30s default), adds it to that account's own list, and wires `onCompletion`/`onTimeout`/
`onError` to `deregister`. `push` iterates the account's own emitter list (if any — a missing entry
is not an error, just nobody connected right now) and calls `emitter.send(SseEmitter.event().name(eventName).data(data))`;
an `IOException`/`IllegalStateException` from a dead emitter removes it via `deregister` rather than
propagating (a dead connection is not `InAppChannel.send`'s own concern).

### `channel/InAppChannel.java`
```
@Component
public class InAppChannel implements NotificationChannel {
    InAppChannel(InappNotificationRepository repository, InappStreamRegistry streamRegistry, Clock clock)
    public String channel()
    @Transactional
    public void send(UUID accountUuid, String recipient, String category, TemplateRenderer.RenderedMessage message)
    private static String deriveTitle(String body)
    private void pushAfterCommit(UUID accountUuid, InappNotification.View view)
}
```
`channel()` returns `"IN_APP"`. `send` generates a fresh `notification_uuid`
(`UUID.randomUUID()`), derives `title` via `deriveTitle` (first 100 characters of `message.body()`,
appending `"…"` if truncated — AC7), constructs an `InappNotification` with `link = null` (Finding
#3) and `createdAt = clock.instant()`, persists it, then calls `pushAfterCommit`. `send` is
`@Transactional` (default `REQUIRED`, Finding #6) — joins `DeliveryOrchestrator.dispatch`'s own
already-open transaction (T11), mirroring `ContactProjectionUpdater.upsertEmail`'s own established
"explicit `@Transactional` on every write-path method" precedent (T05).

`pushAfterCommit`: if `TransactionSynchronizationManager.isSynchronizationActive()` (true in the
real, only expected call path — inside `dispatch`'s own transaction), registers a
`TransactionSynchronization` whose `afterCommit()` calls `streamRegistry.push(accountUuid,
"notification", view)` — the live push can never reach a client for a row the read API can't yet
see (Finding #6, resolved more strongly than literally asked). If no synchronization is active
(defensive — not expected given `InAppChannel`'s only real caller), pushes immediately instead of
silently dropping the notification.

`recipient` (the account UUID's own string form, T11's established `IN_APP` convention) is accepted
per the interface's own contract but not used — `accountUuid` alone is sufficient for this channel's
own needs.

### `common/ApiExceptionHandler.java`
```
@RestControllerAdvice
public class ApiExceptionHandler {
    public static class InvalidSubjectClaimException extends RuntimeException {
        public InvalidSubjectClaimException(String message)
    }
    @ExceptionHandler(InvalidSubjectClaimException.class)
    public ProblemDetail handleInvalidSubjectClaim(InvalidSubjectClaimException e)
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e)
}
```
`InvalidSubjectClaimException` is nested here (Finding #7/#9) rather than a separate top-level file
— this class's own job is exactly "what HTTP shape does each exception map to," so the exception
type it exists to handle lives with it, and Phase 4 authorized no additional top-level exception
file. Uses Spring's own built-in `ProblemDetail` (RFC 9457-native since Spring 6/Boot 3) —
`handleInvalidSubjectClaim` returns `ProblemDetail.forStatusAndDetail(BAD_REQUEST, ...)` (AC9);
`handleUnexpected` returns a generic `INTERNAL_SERVER_ERROR` detail, never the real exception's own
message or stack trace (mirrors this codebase's own established "no internal detail in an error
response" rule, `agents.md`). 401/403 remain exclusively `ResourceServerConfig`'s own handlers
(Finding #9) — not duplicated here.

### `inapp/InappStreamController.java`
```
@RestController
public class InappStreamController {
    InappStreamController(InappStreamRegistry streamRegistry)
    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt)
    private static UUID accountUuidFrom(Jwt jwt)
}
```
`stream` parses `jwt.getSubject()` via `accountUuidFrom` (throws
`ApiExceptionHandler.InvalidSubjectClaimException` on a malformed `sub`, Finding #7/AC9) and
registers a new emitter for that account via `streamRegistry.register(...)`. No path/query
parameter carries an account identifier anywhere (AC4/L8) — the caller's identity comes exclusively
from the validated token.

### `inapp/InappReadController.java`
```
@RestController
public class InappReadController {
    InappReadController(InappNotificationRepository repository)
    @GetMapping("/notifications/unread")
    public List<InappNotification.View> unread(@AuthenticationPrincipal Jwt jwt)
    private static UUID accountUuidFrom(Jwt jwt)
}
```
`unread` parses the caller's own `sub` (same `accountUuidFrom` logic, duplicated in this controller
rather than shared via a new file — a deliberate, minor duplication given the small,
already-fully-allocated file budget this phase authorizes) and returns
`repository.findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(accountUuid)` mapped to `View`
records (AC5/AC10). Same "no client-supplied identifier" construction as the stream controller.

## Files to modify

- `channel/NotificationChannel.java` — `send`'s signature gains a `String category` parameter,
  positioned before `message` (Finding #1): `send(UUID accountUuid, String recipient, String
  category, TemplateRenderer.RenderedMessage message)`.
- `delivery/DeliveryOrchestrator.java` — one line, `dispatchOneChannel`'s own
  `channelBean.send(accountUuid, recipient, message)` call becomes
  `channelBean.send(accountUuid, recipient, mapping.category(), message)` (`mapping.category()` is
  already a local value in scope there).
- `channel/EmailChannel.java` — `send`'s signature gains the same `category` parameter; the method
  body is otherwise unchanged (the parameter is accepted, never read).
- `delivery/DeliveryOrchestratorTest.java` — every `verify(emailChannel/inAppChannel, ...).send(any(),
  any(), any())`-shaped assertion (~17 occurrences) gains a 4th `any()` matcher.
- `channel/EmailChannelTest.java` — every direct `channel.send(accountUuid, recipient, message)` call
  (~7 occurrences) gains an explicit `category` literal argument (e.g. `"SECURITY"`) — the exact
  value is immaterial to these tests, since `EmailChannel` never reads it.
- `T01SkeletonRegressionTest.java` — authorized file-inventory list: remove
  `channel/NoOpInAppChannel.java`; add `channel/InAppChannel.java`, `inapp/InappNotification.java`,
  `inapp/InappNotificationRepository.java`, `inapp/InappStreamRegistry.java`,
  `inapp/InappStreamController.java`, `inapp/InappReadController.java`,
  `common/ApiExceptionHandler.java`.

## Files to delete

- `channel/NoOpInAppChannel.java`, `channel/NoOpInAppChannelTest.java`.

## Public methods (signatures)

Listed inline with each file above. Summary: `InAppChannel.channel()`, `InAppChannel.send(UUID,
String, String, RenderedMessage)`; `InappNotification.toView()` + 8 getters;
`InappNotificationRepository.findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(UUID)`;
`InappStreamRegistry.register(UUID)`, `.push(UUID, String, Object)`;
`InappStreamController.stream(Jwt)`; `InappReadController.unread(Jwt)`;
`ApiExceptionHandler.handleInvalidSubjectClaim(...)`, `.handleUnexpected(...)`.

## Private methods

`InAppChannel.deriveTitle(String)`, `.pushAfterCommit(UUID, InappNotification.View)`;
`InappStreamRegistry.deregister(UUID, SseEmitter)`; `InappStreamController.accountUuidFrom(Jwt)`;
`InappReadController.accountUuidFrom(Jwt)` (duplicated, see above).

## Entities used

`InappNotification` (new — the first entity in this module needing zero new Flyway migration, T02's
own table having waited fully-shaped since launch).

## Repositories used

`InappNotificationRepository` (new, `public` — see the visibility note above).

## Services used

`Clock` (existing `ClockConfig` bean, T04 precedent), `InappStreamRegistry` (new, this task's own
`@Component`), Spring Security's own resource-server `Jwt` support (already configured, T03).

## Unit tests required

- `InAppChannelTest` — `channel()` value; `send` persists the correct row (category/title-derivation/
  link-always-null/createdAt-from-Clock); title truncation at exactly 100 chars vs. over; the push
  fires only via a registered `afterCommit` synchronization when one is active, and immediately when
  none is (a mocked `InappStreamRegistry`, no real transaction needed for the "no synchronization
  active" branch; a real `PlatformTransactionManager`/`TransactionTemplate` for the
  synchronization-active branch, mirroring `IdempotencyGuardIntegrationTest`'s own established
  pattern for proving transactional behavior empirically).
- `InappStreamRegistryTest` — register/push/deregister-on-completion/deregister-on-dead-emitter;
  concurrent register+push across multiple accounts (mirrors `FakeEmailTransportTest`'s own T12
  concurrency-proof precedent).
- `InappNotificationTest` — `toView()` maps every field correctly.
- `InappStreamControllerTest` / `InappReadControllerTest` (`@WebMvcTest` + `spring-security-test`'s
  `jwt()` post-processor) — 401 with no token; 400 with a malformed `sub`; 200 with a valid `sub`,
  scoped correctly; no client-supplied account identifier exists in either endpoint's own
  path/parameters to even attempt a cross-account bypass with.
- Mechanical: existing `DeliveryOrchestratorTest`/`EmailChannelTest` call sites still compile and pass
  after the `NotificationChannel.send` signature change.

## Integration tests required

- A real end-to-end test (Testcontainers Postgres, real Spring context, real
  `spring-security-test`-constructed JWT) exercising `dispatch(...)` → `InAppChannel.send` → a real
  persisted row → `InappReadController.unread` returning it, and separately, a real SSE connection
  receiving a real post-commit push (`MockMvc`'s own async support, or a real embedded server —
  Phase 6's own call given whichever proves simpler in practice) — the named tests
  `shouldStreamInAppNotificationsToAuthenticatedRecipientOnly` (R16) and
  `shouldReturnUnreadInAppNotificationsForCaller` (R17) live here.
- A cross-account isolation proof: two accounts, two JWTs, account A's own request never returns/
  streams account B's notifications.

## Execution order

1. `channel/NotificationChannel.java` — signature change (everything else depends on this compiling).
2. `delivery/DeliveryOrchestrator.java` — adapt the one call site.
3. `channel/EmailChannel.java` — adapt the signature, ignore the new parameter.
4. `inapp/InappNotification.java` (entity + nested `View` + `toView()`).
5. `inapp/InappNotificationRepository.java` (public).
6. `inapp/InappStreamRegistry.java` (no dependency on the entity/repository).
7. `channel/InAppChannel.java` (depends on 4, 5, 6, and the now-updated `NotificationChannel`).
8. `common/ApiExceptionHandler.java` (defines the exception type + handlers).
9. `inapp/InappStreamController.java` (depends on 6, 8).
10. `inapp/InappReadController.java` (depends on 5, 8).
11. Delete `channel/NoOpInAppChannel.java` and `channel/NoOpInAppChannelTest.java`.
12. `delivery/DeliveryOrchestratorTest.java` — mechanical signature-change update.
13. `channel/EmailChannelTest.java` — mechanical signature-change update.
14. `T01SkeletonRegressionTest.java` — update the authorized file list.
15. Tests, in the same dependency order as the files they cover:
    `InappNotificationTest` → `InappStreamRegistryTest` → `InAppChannelTest` →
    `InappStreamControllerTest`/`InappReadControllerTest` → the end-to-end integration tests.
16. Full suite: `mvn -pl services/notification clean verify`.
