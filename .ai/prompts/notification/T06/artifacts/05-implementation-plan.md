# notification · T06 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/notification/src/main/java/com/themistra/notification/consumer/dto/EmailRequestedEvent.java`
2. `.../consumer/dto/UserLifecycleEvent.java`
3. `.../consumer/NotificationDispatcher.java`
4. `.../consumer/NoOpNotificationDispatcher.java`
5. `.../consumer/AuthEventConsumer.java`
6. `services/notification/src/test/java/com/themistra/notification/consumer/dto/EmailRequestedEventContractTest.java`
   (written in Phase 6, per the frozen brief's own explicit carve-out — mirrors T02's own precedent
   for schema/contract-shape tests that encode a structural guarantee the implementation itself must
   satisfy, not deferred to Phase 10)
7. `.../consumer/dto/UserLifecycleEventContractTest.java` (same carve-out)

## Files to modify

1. `services/notification/src/main/resources/application.properties` — adds
   `spring.kafka.consumer.group-id=notification-service` and
   `spring.kafka.consumer.auto-offset-reset=latest` (Finding #5's own pinned value).

No files outside this list. `V1-V5`, `pom.xml`, and everything under `spec/` are untouched, per
frozen brief.

## Public methods (signatures)

**`EmailRequestedEvent`** (record, `consumer/dto/`)
```java
public record EmailRequestedEvent(
    UUID accountUuid,
    String purpose,
    String token,
    String email,
    Instant occurredAt
) {
    @Override
    public String toString() {
        // excludes token - mirrors auth-service's own EmailRequestedEventPayload (L4)
    }
}
```

**`UserLifecycleEvent`** (record, `consumer/dto/`)
```java
public record UserLifecycleEvent(
    UUID accountUuid,
    String status,
    String email,
    String eventType,
    Instant occurredAt
) {}
```
No sensitive fields — default `toString()` is fine.

**`NotificationDispatcher`** (interface)
```java
public interface NotificationDispatcher {
    void dispatch(UUID accountUuid, String notificationKind, Map<String, String> eventData);
}
```

**`NoOpNotificationDispatcher`** (`@Component`)
```java
@Component
public class NoOpNotificationDispatcher implements NotificationDispatcher {
    @Override
    public void dispatch(UUID accountUuid, String notificationKind, Map<String, String> eventData) {
        log.info("Dispatch (no-op): accountUuid={}, notificationKind={}, eventDataKeys={}",
                accountUuid, notificationKind, eventData.keySet());
    }
}
```
No `@ConditionalOnMissingBean` (design note, not in the frozen brief's own text): component-scan
ordering between two `@Component`-scanned beans isn't guaranteed by Spring Boot, so relying on it
here would be fragile. This class is instead documented as **replaced** (file deleted, the real
implementation added in its place) by whichever task (11/12) first provides real dispatch — not
coexisting with a real implementation.

**`AuthEventConsumer`** (`@Component`)
```java
@Component
public class AuthEventConsumer {
    public AuthEventConsumer(ObjectMapper objectMapper, IdempotencyGuard idempotencyGuard,
                              ContactProjectionUpdater contactProjectionUpdater,
                              NotificationDispatcher notificationDispatcher);

    @KafkaListener(topics = "auth.email.requested", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onEmailRequested(String rawJson) throws JsonProcessingException;

    @KafkaListener(topics = "auth.user.lifecycle", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onUserLifecycle(String rawJson) throws JsonProcessingException;
}
```
Uses Spring Boot's own autoconfigured `ObjectMapper` bean (already registers `jackson-datatype-jsr310`
transitively via `spring-boot-starter-web`, so `Instant` fields deserialize correctly with no extra
configuration) — not a hand-rolled one. Both listener methods consume the raw Kafka value as `String`
(mirroring `auth-service`'s own producer-side `KafkaTemplate<String, String>` convention exactly —
no Spring-Kafka type-mapped JSON deserializer), then `objectMapper.readValue(rawJson, X.class)`.

## Private methods

- `AuthEventConsumer` needs no private helper methods — the idempotency-key construction
  (`accountUuid + ":" + purpose/eventType + ":" + occurredAt`, Finding #1) is a single inline
  expression per listener method, not worth extracting.

## Entities used

None new — `IdempotencyGuard`/`ContactProjectionUpdater` (both already built, T04/T05) are the only
persistence this task touches, indirectly.

## Repositories used

None new (same reason).

## Services used

`IdempotencyGuard` (T04), `ContactProjectionUpdater` (T05), `NotificationDispatcher` (this task's
own new interface, injected — resolves to `NoOpNotificationDispatcher` today).

## Unit / integration tests required

Deferred to Phase 10 (per this module's own established rule), except the two contract tests named
above, written in Phase 6:

1. **`EmailRequestedEventContractTest`** — mirrors `auth-service`'s own
   `EmailRequestedEventPayloadContractTest` exactly: construct a real `EmailRequestedEvent`,
   serialize it, assert every schema `required` field is present and every serialized field is
   declared in the schema's own `properties` (`additionalProperties: false`); also assert `toString()`
   excludes the raw token value (Finding #4's own security concern, applied to this DTO too, not
   just the no-op dispatcher).
2. **`UserLifecycleEventContractTest`** — same structural shape, plus the schema's own `status` enum
   coverage check (mirrors `auth-service`'s own `everyAccountStatusValueIsCoveredByTheSchemaEnum`).

Phase 10's own planned unit/integration tests (not written this phase): `AuthEventConsumer`
deduplicates via `IdempotencyGuard` (real Testcontainers proof, not a mock — the composite key
format itself is worth proving end-to-end); calls `ContactProjectionUpdater` with the right
arguments; resolves the 3 in-scope `notificationKind` values correctly; does **not** dispatch for
unknown `purpose` or non-`user.registered` `eventType`; `NoOpNotificationDispatcher` never logs a
raw token value (captured-log assertion).

## Execution order

1. `EmailRequestedEvent`, `UserLifecycleEvent` (no dependencies on anything else new).
2. `NotificationDispatcher` (no dependencies).
3. `NoOpNotificationDispatcher` (depends on `NotificationDispatcher`).
4. `application.properties`'s own Kafka consumer keys (needed before step 5's own listener can bind
   `${spring.kafka.consumer.group-id}`).
5. `AuthEventConsumer` (depends on steps 1-4, plus the already-existing `IdempotencyGuard`/
   `ContactProjectionUpdater`).
6. `EmailRequestedEventContractTest`, `UserLifecycleEventContractTest` (step 1's own proof — written
   in Phase 6 per the frozen brief's own carve-out, not deferred to Phase 10).
