# crypto · T04 · Phase 9 — Review Resolution

**Human Approval gate. Approved 2026-09-03.** Combines Phase 7 (self-review) and Phase 8 (Kimi
independent review) findings into one resolution log. Only accepted comments were applied; no
refactoring, public-API changes, or renames were made beyond what each accepted fix required (one
signature addition — `OutboxPublisher`/`OutboxEvent.create` gained a `Clock`/`Instant` parameter —
was necessary to implement accepted Finding 4 and is documented below, not a rename).

## Resolution log

| # | Comment (source) | Decision | Reason | Change made |
|---|---|---|---|---|
| 1 | Missing `spring.jpa.hibernate.ddl-auto`/`open-in-view` now that `OutboxEvent` is the first JPA entity (self-review Finding 1 / Kimi Finding 1) | **ACCEPTED** | Confirmed against auth's identical precedent; a mapping mistake would otherwise surface only as a runtime SQL error, not a boot-time failure | Added `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false` to `application.properties` |
| 2 | `kafkaTemplate.send(...).get()` blocks indefinitely, no timeout (Kimi Finding 2) | **ACCEPTED** | Cheap, bounds a real hang risk without changing any already-reasoned-about behavior | Added `.get(30, TimeUnit.SECONDS)`; `TimeoutException` now falls into the existing warn-and-retry path alongside `ExecutionException` |
| 3 | Bare `catch (Exception e)` swallows interrupts and programming errors (Kimi Finding 3) | **ACCEPTED, scoped** | Kimi's own "let unexpected exceptions propagate" is safe here — Spring's `@Scheduled` infrastructure logs and continues on an uncaught exception, it doesn't kill the scheduler | Split into `catch (ExecutionException \| TimeoutException)` (warn + retry), `catch (InterruptedException)` (restore interrupt flag, warn), `catch (DataAccessException)` (warn + retry — Kafka send already succeeded, only the mark-published save failed) |
| 4 | `@PrePersist` uses `Instant.now()`, not the injectable `Clock` (Kimi Finding 4) | **ACCEPTED** | agents.md's fixed-`Clock` rule is unconditional; auth has the same deviation but that doesn't make it correct for new code | Injected `Clock` into `OutboxPublisher`; `OutboxEvent.create(...)` now takes `Instant createdAt` explicitly, set from `clock.instant()`; `@PrePersist` is now a null-guarded fallback only |
| 5 | Required T04 tests are missing (Kimi Finding 5) | **ACKNOWLEDGED, not a Phase 9 action** | Test authorship is Phase 10 by this pipeline's own design | No change — Phase 10's job |
| 6 | Unroutable events polled and re-logged forever, no quarantine (Kimi Finding 6) | **ACCEPTED documentation only** | Both suggested mechanisms are out of reach: a new terminal-state column would violate `outbox`'s immutable/verbatim schema (design §4c); alerting/backoff infrastructure is out of this task's stated scope and premature (no aggregate type this service currently emits is actually unroutable) | Strengthened the inline comment in `OutboxRelay.relayOne` explaining the limitation and why no mechanism was added |
| 7 | Kafka producer missing `delivery.timeout.ms`/`max.block.ms` (Kimi Finding 7) | **REJECTED** | Speculative tuning with no driving incident; overlaps with #2's fix, which already bounds the operationally significant hang risk on the consumer side | No change |
| 8 | Transaction propagation untested (Kimi Finding 8) | **ACKNOWLEDGED** | Already `AC11` / the planned `OutboxTransactionIntegrationTest` (frozen brief amendment #5, Phase 5 plan) | No change — Phase 10's job |
| 9 | V3 migration untested (Kimi Finding 9) | **ACKNOWLEDGED** | Already `AC9` / the planned `OutboxGrantMigrationIntegrationTest` (Phase 5 plan) | No change — Phase 10's job |
| 10 | Hardcoded `spring.profiles.active=local` (Kimi Finding 10) | **REJECTED** | Identical finding already rejected on T03 for the same reasoning: pre-existing from T01, not touched by this task, and real-environment profile activation is a deployment-pipeline concern (`SPRING_PROFILES_ACTIVE` override), not application code | No change |

**5 accepted (4 code fixes + 1 doc-only), 2 rejected, 3 acknowledged as already-tracked Phase 10 work.**

## Files changed this phase

- `services/crypto/src/main/resources/application.properties` — added `spring.jpa.hibernate.ddl-auto=validate`
  and `spring.jpa.open-in-view=false` (item 1).
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxRelay.java` — bounded `.get()`
  timeout, narrowed exception handling, strengthened unroutable-event comment (items 2, 3, 6).
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEvent.java` — `create(...)` now
  takes `Instant createdAt`; `@PrePersist` is a null-guarded fallback (item 4).
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxPublisher.java` — injects `Clock`,
  passes `clock.instant()` to `OutboxEvent.create(...)` (item 4).

All four files were already on the frozen brief's Files-to-Create/Modify list — no file outside that
list was touched. `mvn -pl services/crypto -am compile` / `test-compile` — both `BUILD SUCCESS` after
all four fixes.

No public API was removed and no class was renamed; `OutboxPublisher`'s constructor and
`OutboxEvent.create`'s signature each gained one parameter (`Clock` / `Instant createdAt`
respectively) as the direct, necessary implementation of accepted Finding 4 — not an unrelated
refactor.
