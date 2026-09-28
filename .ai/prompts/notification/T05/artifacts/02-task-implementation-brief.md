# notification · T05 · Phase 2 — Task Implementation Brief

## Task

Add `ContactProjection` (mapped onto T02's already-migrated `contact_projection` table) + its
repository, plus a small `ContactProjectionUpdater` service exposing an upsert operation, under
`preference/`. Grant `notification_app` the (genuinely broader than T04's) privileges this upsert
needs.

## Purpose

Gives every later consumer task (6+) a single, already-correct place to call for "record/refresh
this account's known email" — the recipient-address source R1/R2/R6's own email delivery ultimately
depends on, resolving `package.md` §11 Q1 for real rather than deferring it further.

## Scope

**In:**
- `ContactProjection` entity — `@Id` on `accountUuid` (client-assigned `UUID`, no `@GeneratedValue`
  — the account's own external identifier IS the primary key, mirroring T04's own
  `ProcessedEvent.eventKey` shape), explicit `@Table(name = "contact_projection", schema =
  "notifications")`, `email`, `displayName` (mapped but never written by this task — see Out),
  `updatedAt`. Protected no-arg constructor, read-only getters, no setters (mirrors T04's own
  `ProcessedEvent`, post-Phase-9 shape).
- `ContactProjectionRepository extends JpaRepository<ContactProjection, UUID>` — package-private, one
  native upsert query: `INSERT ... ON CONFLICT (account_uuid) DO UPDATE SET email = EXCLUDED.email,
  updated_at = EXCLUDED.updated_at WHERE contact_projection.updated_at <= EXCLUDED.updated_at`. The
  trailing `WHERE` guards against out-of-order delivery: `auth.email.requested` and
  `auth.user.lifecycle` are two *different* Kafka topics with no cross-topic ordering guarantee, so
  a later-processed but chronologically-older event (by its own `occurredAt`) must never overwrite a
  newer projection state with stale data. Mirrors T04's own native-query, bypass-the-persistence-
  context shape (`@Modifying(clearAutomatically = true, flushAutomatically = true)`) — chosen for
  consistency with the one other write path in this module, not because T04's own specific
  transaction-poisoning problem applies here (an upsert never conflicts/fails, so there is no
  exception to catch either way).
- `ContactProjectionUpdater` — a small `@Service`, `preference/` package: `public void
  upsertEmail(UUID accountUuid, String email, Instant occurredAt)`, `@Transactional` (default
  `REQUIRED`, same rationale as T04's own `IdempotencyGuard` — joins whatever transaction a future
  caller already has open). Necessary as a public wrapper, not stylistic: `ContactProjectionRepository`
  is package-private, so `consumer/AuthEventConsumer` (task 6, a different package) cannot call it
  directly. Takes the event's own `occurredAt` as `updatedAt` (not a fresh `Clock.instant()`) — the
  out-of-order guard compares *event* time, not *processing* time, so this class needs no `Clock`
  dependency at all.
- `V5__notification_app_contact_projection_grant.sql` — `INSERT, SELECT, UPDATE` (not
  `INSERT`-only like T04's own `V4`): `contact_projection` is a genuine upsert target, unlike the
  insert-once `processed_events` ledger. Comment explains why `UPDATE` is granted here specifically
  (mirrors `crypto-service`'s own `V3__crypto_app_outbox_grant.sql`, the only existing precedent in
  this codebase for a table that legitimately needs `UPDATE`). No `DELETE` — nothing in this task's
  own scope removes a projection row.
- Both a unit test (mocked repository) and a Testcontainers integration test proving the upsert,
  including the out-of-order guard specifically, mirroring T04's own test-split precedent.

**Out:**
- `display_name` population — **no data source exists**. Verified directly (Phase 0): auth's own
  `Account` domain has no display-name concept anywhere. `ContactProjection.displayName` is mapped
  (so future code can read it once *something* populates it) but this task never writes it — every
  row this task creates or updates has `display_name = NULL`. This is a documented, permanent gap
  for this task, not an oversight; `V3`'s own seed templates' `{{displayName}}` placeholder was
  already disclosed as provisional pending this exact resolution.
- Any behavior on `auth.user.lifecycle`'s `DELETED` status — `contact_projection` has no status
  column and nothing in this task's own scope calls for deleting or special-casing a projection row
  on account deletion; the upsert applies uniformly regardless of the lifecycle event's own status
  value. Not decided here whether a later task should ever prune deleted accounts' projections.
- Any `@KafkaListener`, JSON deserialization, or `contracts/events/*`-matching DTO — `consumer/AuthEventConsumer`
  (task 6) owns the real Kafka listener; this task's own methods take plain `UUID`/`String`/`Instant`
  arguments, mirroring `IdempotencyGuard.recordIfNew`'s own shape exactly.
- Any change to `auth-service` — already made, as its own separate, explicitly user-authorized
  change (commit `64557d3`, "auth events: add email to auth.email.requested and auth.user.lifecycle"),
  not part of this task's own file list.

## Business Rules

No individual R-numbered requirement is behaviorally implemented by this task (infrastructure, like
T02/T04). R1, R2, R6 (verification/reset/welcome emails) depend on `ContactProjection` existing and
being genuinely populated — this task is the real, no-longer-deferred resolution of that dependency.

## Locked Decisions

- **L2.** Consume-only — this task's own upsert never makes a synchronous call anywhere; it only
  ever writes locally from arguments a future caller already has (themselves sourced from a consumed
  event, per O1's own recommended design, now genuinely realizable).

## Dependencies

`spring-boot-starter-data-jpa` (present), `notification_app`'s DB role + this task's own new `V5`
grant. `auth.email.requested`/`auth.user.lifecycle` now genuinely carrying `email` (verified: both
JSON schemas and the real auth-service payload records, post-commit `64557d3`).

## Inputs

`contact_projection`'s already-migrated DDL (T02); `services/notification`'s own T04
(`ProcessedEvent`/`ProcessedEventRepository`/`IdempotencyGuard`) as the direct, in-module structural
precedent; `crypto-service`'s own `V3__crypto_app_outbox_grant.sql` as the one existing precedent for
a table needing `UPDATE`, not just `INSERT`.

## Outputs

`ContactProjection.java`, `ContactProjectionRepository.java`, `ContactProjectionUpdater.java`,
`V5__notification_app_contact_projection_grant.sql`; one new unit test class, one new Testcontainers
integration test class.

## State Changes

Real state change: `notification_app` gains `INSERT, SELECT, UPDATE` on
`notifications.contact_projection`.

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/preference/ContactProjection.java`
- `.../preference/ContactProjectionRepository.java`
- `.../preference/ContactProjectionUpdater.java`
- `services/notification/src/main/resources/db/migration/V5__notification_app_contact_projection_grant.sql`

## Files to Modify

- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  — its 12-file authorized production list becomes 15 (adds the 3 new `preference/` files).
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  — `contact_projection` moves from `UNGRANTED_TABLES` to its own dedicated grant-proof test (its
  own schema/grant shape — `UPDATE` now permitted, unlike T04's `processed_events` — doesn't fit the
  existing insert-only `GRANTED_TABLES` helper either); Flyway-history expectation widens to
  `"1","2","3","4","5"`.

## Files NOT to Modify

- `services/notification/src/main/resources/db/migration/V1-V4` (additive only, never edited).
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment` — the one exception already made (auth's
  event payloads) is its own separate, already-committed, already-disclosed change, not part of this
  task's own file list or scope.

## Acceptance Criteria

1. **AC1.** `ContactProjection` maps exactly onto `contact_projection`'s 4 existing columns;
   `account_uuid` is the client-assigned `@Id`.
2. **AC2.** The upsert creates a new row on a first call for a given `account_uuid`, and updates
   `email`/`updated_at` on a subsequent call for the same `account_uuid`.
3. **AC3.** The upsert is guarded against out-of-order delivery: a call whose `occurredAt` is older
   than the row's current `updated_at` must not overwrite `email`/`updated_at`.
4. **AC4.** `display_name` is never written by this task; every row it creates/updates has
   `display_name = NULL`.
5. **AC5.** `notification_app` can `INSERT`/`SELECT`/`UPDATE` on `contact_projection` at runtime
   (new `V5` grant); still cannot `DELETE`.
6. **AC6.** Both a unit test and a Testcontainers integration test pass, including a dedicated
   out-of-order proof (AC3).

## Required Tests

Mirrors T04's own split: a `ContactProjectionUpdaterUnitTest` (mocked repository) and a
`ContactProjectionUpdaterIntegrationTest` (`@Testcontainers` + `@SpringBootTest`, real Postgres, real
transaction behavior) — the second real-Spring-context test class in this module after T04's own
`IdempotencyGuardIntegrationTest`.

## Constraints

- **Transaction propagation:** `ContactProjectionUpdater.upsertEmail` must never declare
  `REQUIRES_NEW`/`NOT_SUPPORTED` — same L1-adjacent reasoning as T04's own `IdempotencyGuard`, so a
  future consumer can call this and `IdempotencyGuard.recordIfNew` together in one transaction.
- **Least privilege:** `V5` grants only `INSERT, SELECT, UPDATE` — never `DELETE`.
- **Ordering safety:** the `WHERE updated_at <= EXCLUDED.updated_at` guard is not optional — omitting
  it would let a redelivered or out-of-order older event silently regress a newer projection state.
- **Null handling:** `email`/`occurredAt` are never null in any real call path (both source events
  now guarantee `email` as a required schema field; `occurredAt` likewise) — no explicit null-guard
  beyond what column non-nullability already documents.
- **Money types / thread-safety:** not applicable.

## Open Questions

No blockers. Phase 1's own blocker (no email field in either source event) was resolved by an
explicit user decision and a real, already-committed, already-tested auth-service change (commit
`64557d3`) before this phase began.
