# crypto · T10 · Phase 5 — Implementation Plan

## Files to create

All seven trace directly to the frozen brief's "Files to Create" list — no additional file added:

1. `services/crypto/src/main/resources/db/migration/V4__crypto_app_provider_health_grant.sql`
2. `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderHealthProperties.java`
3. `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealth.java`
4. `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealthRepository.java`
5. `services/crypto/src/main/java/com/themistra/crypto/provider/DegradationReason.java`
6. `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderDegradedPublisher.java`
7. `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealthTracker.java`

The event payload (`chain`, `provider`, `reason`, `occurredAt`) is a **nested record inside
`ProviderDegradedPublisher.java`** (`ProviderDegradedPublisher.Payload`), not a new top-level file —
mirrors `QuorumEvaluator.Result` (T09) and `FactType.DbConverter` (T08): a small type owned by, and
only meaningful in the context of, its enclosing class.

Test files (all under `services/crypto/src/test/java/com/themistra/crypto/provider/`, plus one under
`common/config/`):
8. `ProviderHealthTest.java`
9. `ProviderDegradedPublisherTest.java`
10. `ProviderHealthTrackerTest.java`
11. `ProviderHealthRepositoryIntegrationTest.java` (Docker-gated)
12. `services/crypto/src/test/java/com/themistra/crypto/common/config/ProviderHealthPropertiesTest.java`

## Files to modify

- `services/crypto/src/main/resources/application.properties` — add
  `themistra.crypto.provider-health.disagreement-threshold=3` (local-profile default), placed alongside
  the other `themistra.crypto.*` entries.

## Public methods (signatures)

**`ProviderHealthProperties`** (record, mirrors `FinalityProperties`'s style exactly):
```java
@ConfigurationProperties(prefix = "themistra.crypto.provider-health")
@Validated
public record ProviderHealthProperties(@Positive int disagreementThreshold) {}
```

**`ProviderHealth`** (entity):
```java
@Entity
@Table(name = "provider_health", schema = "chain")
public class ProviderHealth {
    public static ProviderHealth create(String chain, String provider, Instant now);

    public void markHealthy(Instant now);
    public void markUnhealthy(Instant now);
    public void recordDisagreement(Instant now);

    public Long id();
    public String chain();
    public String provider();
    public boolean healthy();
    public Instant lastOkAt();
    public Instant lastDisagreementAt();
    public Instant updatedAt();
}
```
`create` sets `healthy=true` (matching the DB column's own `DEFAULT TRUE`) and `updatedAt=now`;
`lastOkAt`/`lastDisagreementAt` stay `null` until their respective mutator is first called. No
`@Column` maps `chain`/`provider` with `unique = true` individually — the DB's own `UNIQUE (chain,
provider)` composite constraint (`V1__chain_baseline.sql:59`) is the enforcement point; Hibernate does
not need a matching `@Table(uniqueConstraints=...)` declaration for correctness (it would only add a
redundant DDL-validation-time check, and this task adds no new DDL of its own beyond the grant).

**`ProviderHealthRepository`** (package-private):
```java
interface ProviderHealthRepository extends JpaRepository<ProviderHealth, Long> {
    Optional<ProviderHealth> findByChainAndProvider(String chain, String provider);
}
```

**`DegradationReason`** (enum):
```java
public enum DegradationReason { UNHEALTHY, LAGGING, REPEATED_DISAGREEMENT }
```

**`ProviderDegradedPublisher`**:
```java
@Component
public class ProviderDegradedPublisher {
    public ProviderDegradedPublisher(OutboxPublisher outboxPublisher);

    public void publish(String chain, String provider, DegradationReason reason, Instant occurredAt);

    public record Payload(String chain, String provider, DegradationReason reason, Instant occurredAt) {}
}
```
`publish` builds `aggregateId = chain + ":" + provider`, `idempotencyKey = chain + ":" + provider +
":degraded:" + occurredAt + ":" + UUID.randomUUID()`, and calls
`outboxPublisher.publish("provider", aggregateId, "chain.provider.degraded", idempotencyKey, new
Payload(chain, provider, reason, occurredAt))`. `OutboxPublisher` itself serializes the payload (no
`ObjectMapper` dependency needed here, mirroring how `ObservationLog`/`QuorumDecisionService` never take
one either when they don't do their own JSON validation).

**`ProviderHealthTracker`**:
```java
@Component
public class ProviderHealthTracker {
    public ProviderHealthTracker(ProviderHealthRepository repository, ProviderDegradedPublisher publisher,
            Clock clock, ProviderHealthProperties properties);

    @Transactional
    public void recordHealthy(String chain, String provider);

    @Transactional
    public void recordUnhealthy(String chain, String provider, DegradationReason reason);

    @Transactional
    public void recordDisagreement(String chain, String provider);
}
```
This is the first task since T04 whose coordinator needs an explicit `@Transactional` (frozen brief
Constraints: the `save` + `publish` pair must be atomic) — unlike `ObservationLog`/`QuorumDecisionService`,
which deliberately omit it because their single write is already individually transactional via
`SimpleJpaRepository.save`. Here, `recordUnhealthy`/`recordDisagreement`'s transition path is two calls
(`repository.save` then `publisher.publish`, which itself calls `outboxRepository.save` internally via
`OutboxPublisher`) that must commit together or not at all.

## Private methods

- `ProviderHealthTracker`:
  - `private ProviderHealth fetchOrCreate(String chain, String provider)` — `repository
    .findByChainAndProvider(chain, provider).orElseGet(() -> ProviderHealth.create(chain, provider,
    clock.instant()))`.
  - `private void transitionToUnhealthy(String chain, String provider, ProviderHealth health,
    DegradationReason reason, Instant now)` — shared by `recordUnhealthy` and `recordDisagreement`'s
    threshold branch: `health.markUnhealthy(now)`, `repository.save(health)`, remove the provider's
    entry from the disagreement-count map, `publisher.publish(chain, provider, reason, now)`.
  - `recordHealthy` body: fetch-or-create, `markHealthy(clock.instant())`, `repository.save`, remove the
    disagreement-count map entry.
  - `recordUnhealthy` body: fetch-or-create; if `health.healthy()`, call `transitionToUnhealthy(...,
    clock.instant())`; otherwise no-op (AC2).
  - `recordDisagreement` body: fetch-or-create, `health.recordDisagreement(now)`; if already unhealthy,
    `repository.save(health)` and return (Kimi Issue 6 — counter untouched); otherwise increment the
    counter (`ConcurrentHashMap<ProviderKey, AtomicInteger>`,
    `computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet()`); if below threshold,
    `repository.save(health)` and return; if at/above threshold, call `transitionToUnhealthy(...,
    DegradationReason.REPEATED_DISAGREEMENT, now)` (which itself saves). Exactly one `repository.save`
    call per invocation on every path — no redundant double-save.
  - `private record ProviderKey(String chain, String provider) {}` — the disagreement-count map's key
    type, package-private/nested, not exposed.

## Entities used

- `ProviderHealth` (new, this task) — the service's first update-in-place entity.

## Repositories used

- `ProviderHealthRepository` (new, this task) — `.findByChainAndProvider(...)` and `.save(...)` only;
  no delete call anywhere.
- `OutboxEventRepository` (T04) — transitively, via `OutboxPublisher.publish(...)` inside
  `ProviderDegradedPublisher`; not called directly by anything in this task's own code.

## Services used

- `Clock` bean (`common/ClockConfig`, T04).
- `OutboxPublisher` (T04) — the only sanctioned event-emission path.
- No other existing service/component is consumed.

## Unit / integration tests required

**`ProviderHealthPropertiesTest`** (plain JUnit, `jakarta.validation.Validator`, mirrors
`FinalityPropertiesTest`'s established pattern for validating a `@ConfigurationProperties` record
directly):
- A positive `disagreementThreshold` passes validation.
- Zero or negative fails validation (`@Positive`).

**`ProviderHealthTest`** (plain JUnit):
- `create` sets `healthy=true`, `updatedAt`, leaves `lastOkAt`/`lastDisagreementAt` null.
- `markHealthy` sets `healthy=true`, `lastOkAt`, `updatedAt`; does not touch `lastDisagreementAt`
  (Kimi Issue 10 — historical marker, not cleared).
- `markUnhealthy` sets `healthy=false`, `updatedAt`; does not touch `lastOkAt`.
- `recordDisagreement` sets `lastDisagreementAt`, `updatedAt`; does not touch `healthy`.
- No public mutator beyond `markHealthy`/`markUnhealthy`/`recordDisagreement` (reflection-based, mirrors
  `ObservationTest`/`QuorumDecisionTest` — but asserting exactly these three named methods are the only
  non-getter public methods, since this entity, unlike the append-only ones, legitimately has mutators).

**`ProviderDegradedPublisherTest`** (`@ExtendWith(MockitoExtension.class)`, mocked `OutboxPublisher`):
- `publish` calls `outboxPublisher.publish` with `aggregateType="provider"`,
  `aggregateId="{chain}:{provider}"`, `eventType="chain.provider.degraded"`.
- The payload (captured via `ArgumentCaptor<Object>`, cast to `Payload`) carries the given `chain`,
  `provider`, `reason`, `occurredAt`.
- Two calls with the same `chain`/`provider`/`occurredAt` produce different idempotency keys (UUID
  component, Kimi Issue 7).

**`ProviderHealthTrackerTest`** (`@ExtendWith(MockitoExtension.class)`, mocked
`ProviderHealthRepository`/`ProviderDegradedPublisher`, fixed `Clock`):
- `shouldEmitProviderDegradedWhenAProviderIsUnhealthy` (named test) — `recordUnhealthy` on a
  healthy/new provider transitions and publishes.
- `recordUnhealthy` on an already-unhealthy provider does not save or publish again.
- `recordHealthy` resets a previously-incremented disagreement counter (proven by a subsequent
  `recordDisagreement` sequence needing the full threshold again, not just one more call).
- `recordDisagreement` below `disagreementThreshold - 1` calls does not transition/publish; the
  `disagreementThreshold`-th call does.
- `recordDisagreement` while already unhealthy updates the entity (`lastDisagreementAt`) but does not
  publish and does not affect the counter (proven by recovering, then confirming the full threshold is
  needed again, not fewer calls) (Kimi Issue 6).
- Null-guard tests for `chain`/`provider`/`reason` on each public method.

**`ProviderHealthRepositoryIntegrationTest`** (Docker-gated Testcontainers, mirrors
`QuorumDecisionRepositoryIntegrationTest`'s exact pattern):
- A saved `ProviderHealth` round-trips every field against a real Postgres.
- `findByChainAndProvider` returns the matching row; returns empty when none exists.
- An `UPDATE` (re-saving a fetched, mutated row) succeeds against the real `crypto_app` grant (AC4) —
  the first integration test in this service proving an `UPDATE` succeeds, not fails, the inverse of
  every prior append-only entity's own integration test.
- An attempted `DELETE` still fails (`DataIntegrityViolationException`) — `V4` grants no `DELETE`.

## Execution order

1. `V4__crypto_app_provider_health_grant.sql` — schema/grant first, per the phase's own front-loading
   instruction; no Java code depends on it compiling, but nothing can be verified against a real
   database without it.
2. `ProviderHealthProperties.java` (+ `ProviderHealthPropertiesTest.java`) — no dependencies on anything
   else in this task.
3. `ProviderHealth.java` (+ `ProviderHealthTest.java`) — depends on nothing new.
4. `ProviderHealthRepository.java` — depends on `ProviderHealth` (step 3).
5. `DegradationReason.java` — no dependencies.
6. `ProviderDegradedPublisher.java` (+ `ProviderDegradedPublisherTest.java`) — depends on
   `DegradationReason` (step 5) and `OutboxPublisher` (T04, existing).
7. `ProviderHealthTracker.java` (+ `ProviderHealthTrackerTest.java`) — composes steps 2, 4, 6, and
   `Clock` (T04, existing).
8. `ProviderHealthRepositoryIntegrationTest.java` — exercises steps 3-4 against a real Postgres,
   including the `V4` grant from step 1 (Docker-gated; expected to compile but not execute in this
   environment, per every prior task this session).
9. `application.properties` — add the `disagreement-threshold` default.
10. Full `mvn -pl services/crypto test-compile` then targeted `mvn -pl services/crypto test -Dtest=...`
    for the five new unit-scope test classes, then a full `mvn -pl services/crypto -am test` regression
    pass.
