<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review) for crypto · T04. -->

# crypto · T04 · Phase 8 — Independent Code Review Findings

**Scope:** Review the Phase 6 implementation (`events/EventTopics.java`, `events/OutboxPublisher.java`, `events/OutboxEvent.java`, `events/OutboxEventRepository.java`, `events/OutboxRelay.java`, `events/KafkaProducerConfig.java`, `common/ClockConfig.java`, `V3__crypto_app_outbox_grant.sql`, `application.properties`, `CryptoServiceApplication.java`) and the Phase 7 self-review with fresh, adversarial eyes.

**Directive:** Do not rewrite. Report findings as **Issue · Evidence · Recommendation · Confidence**.

---

## Finding 1 — JPA validation and open-in-view settings are missing now that the first `@Entity` exists

**Issue:** `application.properties` has no `spring.jpa.hibernate.ddl-auto` or `spring.jpa.open-in-view` settings. `OutboxEvent` is the first JPA entity in crypto-service; without `ddl-auto=validate`, a mismatch between the entity mapping and the Flyway-owned schema (e.g., a typo in `@Column(name=...)`) will not be caught at boot, surfacing only as a runtime SQL error on first use. Without `open-in-view=false`, the Open EntityManager in View pattern is enabled by default, which can mask lazy-loading issues and extend transactions into the view layer.

**Evidence:**
- `services/crypto/src/main/resources/application.properties` — no `spring.jpa.*` properties.
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEvent.java` — the first `@Entity` in the service.
- `services/auth/src/main/resources/application.properties` — sets `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false`.
- This was Phase 7 self-review Finding 1; it remains unfixed.

**Recommendation:** Add `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false` to `application.properties`, mirroring auth's precedent. `hibernate.default_schema` is not needed because the T02-established `connection-init-sql` already sets `search_path`.

**Confidence:** High.

---

## Finding 2 — `OutboxRelay` blocks indefinitely on `kafkaTemplate.send(...).get()` with no timeout

**Issue:** `OutboxRelay.relayOne` calls `kafkaTemplate.send(...).get()` without a timeout. If the Kafka broker is unreachable or the producer's `delivery.timeout.ms` is misconfigured, the scheduled virtual thread can hang for an unbounded time, blocking that scheduled execution and consuming a carrier thread.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxRelay.java:67`:
  ```java
  kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();
  ```
- `services/crypto/src/main/resources/application.properties:6`: `spring.threads.virtual.enabled=true`.
- `services/auth/src/main/java/com/themistra/auth/events/OutboxRelay.java:61`: auth uses the same pattern, so this is a inherited risk, but crypto explicitly accepted virtual-thread pinning as a "revisit-if-regressed" item (T01 Phase 4).

**Recommendation:** Use `.get(timeout, TimeUnit)` (e.g., 30 seconds) and handle `TimeoutException` explicitly as a retryable failure. This bounds the hang and makes the relay's retry behavior deterministic.

**Confidence:** High.

---

## Finding 3 — `OutboxRelay` catches generic `Exception`, swallowing programming errors and interrupts

**Issue:** The `relayOne` catch block catches `Exception` around both the Kafka send and the repository save. This swallows not only expected Kafka/transport failures but also programming errors (`NullPointerException`, `IllegalArgumentException`, `ClassCastException`) and `InterruptedException`. Swallowing interrupts also masks shutdown signals and breaks the convention of restoring the interrupt flag.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxRelay.java:70-72`:
  ```java
  } catch (Exception e) {
      log.warn("Failed to relay outbox event {} to {}; will retry", event.getId(), topic, e);
  }
  ```
- No `Thread.currentThread().interrupt()` call for `InterruptedException`.

**Recommendation:** Catch only expected, retryable exceptions (e.g., `ExecutionException`, `InterruptedException`, `KafkaException`, `TransientDataAccessException`). For `InterruptedException`, restore the interrupt flag and return. Let unexpected runtime exceptions propagate so they surface in monitoring/alerts rather than being silently logged and retried forever.

**Confidence:** High.

---

## Finding 4 — `OutboxEvent` `@PrePersist` uses `Instant.now()` instead of the injectable `Clock`

**Issue:** `agents.md` mandates "use `java.time` with an injectable `Clock`." `OutboxEvent.onCreate()` sets `createdAt` via `Instant.now()` in a `@PrePersist` callback, which has no access to the Spring-managed `Clock`. This makes `createdAt` non-deterministic in tests and couples the entity to the system clock.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEvent.java:79-82`:
  ```java
  @PrePersist
  void onCreate() {
      this.createdAt = Instant.now();
  }
  ```
- `services/crypto/src/main/java/com/themistra/crypto/common/ClockConfig.java` — a `Clock` bean exists, but the entity cannot use it.
- `spec/crypto-service/agents.md`: "No `java.util.Date`; use `java.time` with an injectable `Clock`."
- Auth's `OutboxEvent` uses the same pattern, so this is an inherited deviation, not a new regression.

**Recommendation:** Have `OutboxPublisher` set `createdAt` using the injected `Clock` before calling `repository.save()`, and change `@PrePersist` to set `createdAt` only if it is null (fallback guard). This keeps the entity testable and clock-agnostic.

**Confidence:** Medium.

---

## Finding 5 — Required T04 tests are missing

**Issue:** The frozen brief's Required Tests section lists `shouldRouteEachChainEventToItsTopic`, an unmapped-aggregate-type test, `OutboxPublisherTest`, `OutboxRelayTest`, and a Testcontainers-based migration test for `V3`. None exist under `services/crypto/src/test/java/com/themistra/crypto/events/`.

**Evidence:**
- `services/crypto/src/test/java/com/themistra/crypto/` contains T01–T03 tests but no `events/` package or test class.
- TIB §Required Tests explicitly lists:
  - `shouldRouteEachChainEventToItsTopic`
  - Unmapped-aggregate-type test
  - `OutboxPublisherTest`
  - `OutboxRelayTest`
  - Testcontainers migration test for `V3`
- TIB §Acceptance Criteria (AC1–AC10) cannot be verified without automated tests.

**Recommendation:** Add the missing tests before Phase 9 sign-off. Follow auth's test pattern in `services/auth/src/test/java/com/themistra/auth/events/` for `EventTopicsTest`, `OutboxPublisherTest`, and `OutboxRelayTest`, and extend/mirror `ChainBaselineMigrationIntegrationTest` for the `V3` grant verification.

**Confidence:** High.

---

## Finding 6 — `OutboxRelay` poison/unroutable events are never quarantined and are re-polled forever

**Issue:** When `EventTopics.forAggregateType` throws for an unmapped aggregate type, `relayOne` logs an ERROR and returns without marking the row published. The row remains unpublished and will be re-fetched and re-logged on every poll, generating unbounded ERROR spam and causing the outbox table to grow monotonically until a code fix is deployed.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxRelay.java:57-64`:
  ```java
  } catch (IllegalStateException e) {
      log.error("Unroutable outbox event {} ({}): {}", ...);
      return; // left unpublished; a config fix + redeploy will pick it up on the next poll
  }
  ```
- No metric, alert, or quarantine mechanism is mentioned.

**Recommendation:** Either (a) mark unroutable rows with a separate terminal state (e.g., `error_at`) so they are no longer polled, or (b) add a metric/alert and a maximum retry/backoff for unroutable events. At minimum, document the operational expectation that unroutable events require a redeploy and manual cleanup.

**Confidence:** Medium.

---

## Finding 7 — `KafkaProducerConfig` is missing several production-hardening producer properties

**Issue:** The producer configures `acks=all` and `enable.idempotence=true`, but omits other properties that matter for crypto-service's at-least-once/ordering semantics, such as explicit `delivery.timeout.ms`, `request.timeout.ms`, and `max.block.ms`. Relying on Kafka defaults is acceptable but makes behavior environment-dependent and harder to reason about.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/events/KafkaProducerConfig.java:24-34` — only sets bootstrap servers, serializers, acks, and idempotence.
- `services/auth/src/main/java/com/themistra/auth/events/KafkaProducerConfig.java` — identical minimal configuration.

**Recommendation:** Document the chosen defaults and add explicit, environment-appropriate values for `delivery.timeout.ms` (e.g., 120000) and `max.block.ms` (e.g., 60000) so producer timeout behavior is predictable and bounded, especially given Finding 2.

**Confidence:** Low-Medium.

---

## Finding 8 — `OutboxPublisher` transaction propagation is not enforced by code or tests

**Issue:** `OutboxPublisher.publish` correctly has no `@Transactional` annotation, so `repository.save()` will join an existing caller transaction if one exists. However, nothing prevents a future maintainer from adding `@Transactional(propagation = REQUIRES_NEW)` or calling `publish` from a non-transactional context, and there is no automated test to catch such a regression.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxPublisher.java:38-54` — no `@Transactional` annotation; relies on Spring Data JPA's `save()` behavior.
- TIB Constraints: "`OutboxPublisher.publish(...)` must join the caller's existing transaction (no `@Transactional` of its own that would start a new one)."
- No test exists that rolls back a caller's transaction and asserts no outbox row was persisted.

**Recommendation:** Add a transaction-propagation integration test (e.g., `@DataJpaTest` with a test database or Testcontainers) that calls `publish` inside a `@Transactional` service method, forces a rollback, and asserts the outbox row is absent. This guards the "same transaction" guarantee required by `agents.md`.

**Confidence:** Medium.

---

## Finding 9 — `V3__crypto_app_outbox_grant.sql` has no test coverage and no re-run idempotency test

**Issue:** The migration grants `INSERT, SELECT, UPDATE` on `chain.outbox` to `crypto_app`. While PostgreSQL `GRANT` is idempotent, the TIB explicitly requires a Testcontainers-based test proving `crypto_app` can `INSERT` and `UPDATE` (and cannot `DELETE`). No such test exists.

**Evidence:**
- `services/crypto/src/main/resources/db/migration/V3__crypto_app_outbox_grant.sql`.
- TIB §Required Tests: "A Testcontainers-based migration test ... proving `crypto_app` can `INSERT` a row and then `UPDATE` its `published_at`."
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` — already has the container setup and helper methods to mirror.

**Recommendation:** Add a `V3OutboxGrantMigrationIntegrationTest` (or extend the existing migration test) that runs V1/V2/V3 against a Testcontainers Postgres, connects as `crypto_app`, and asserts successful `INSERT` and `UPDATE` on `chain.outbox` as well as denied `DELETE`.

**Confidence:** High.

---

## Finding 10 — `spring.profiles.active=local` remains hardcoded in committed properties

**Issue:** `application.properties` pins `spring.profiles.active=local`. If a higher-environment deployment forgets to override this, the service will run in local mode with placeholder credentials. This is not new to T04, but T04 is the first task to add runtime infrastructure (Kafka bootstrap, outbox relay) and makes the hazard more consequential.

**Evidence:**
- `services/crypto/src/main/resources/application.properties:8`:
  ```properties
  spring.profiles.active=local
  ```
- `agents.md` §Configuration: "Profiles: local, dev, staging, prod. ... startup FAILS on missing/invalid values in non-local profiles."

**Recommendation:** Remove `spring.profiles.active=local` from committed `application.properties` and document that local development must activate it explicitly. This prevents accidental local-profile deployment.

**Confidence:** Medium.

---

## Summary table

| # | Finding | Severity | Confidence |
|---|---------|----------|------------|
| 1 | Missing JPA `ddl-auto=validate` / `open-in-view=false` | High | High |
| 2 | `OutboxRelay` blocks indefinitely on `.get()` | High | High |
| 3 | Generic `Exception` catch swallows errors/interrupts | High | High |
| 4 | `@PrePersist` uses `Instant.now()` not injectable `Clock` | Medium | Medium |
| 5 | Required T04 tests are missing | High | High |
| 6 | Unroutable events polled forever without quarantine | Medium | Medium |
| 7 | Kafka producer missing timeout hardening | Low-Medium | Medium |
| 8 | Transaction propagation not enforced by test | Medium | Medium |
| 9 | V3 migration has no test coverage | High | High |
| 10 | Hardcoded `spring.profiles.active=local` | Medium | Medium |

(End of independent code review.)
