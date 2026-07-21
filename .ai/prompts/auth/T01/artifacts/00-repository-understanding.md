# auth · T01 · Phase 0 — Repository Understanding

Task: T01 — Schema V5 (add `V5__lockout_cleanup_and_shedlock.sql`, run
`mvn -pl services/auth flyway:migrate` against local Docker Compose Postgres). No code written in
this phase.

## 1. Architecture summary

`services/auth` is a Spring Boot 3.5.4 / Java 21 single-module service, package-by-feature under
`com.themistra.auth`: `account`, `authn`, `authz`, `apikey` (empty), `admin`, `audit`, `mfa` (empty),
`token`, `events`, `common`.

- **Persistence.** PostgreSQL, one schema (`auth`), owned exclusively by this service
  (`spring.flyway.default-schema=auth`). Flyway is DDL-only and authoritative; Hibernate runs with
  `ddl-auto=validate` — it never generates schema. Four migrations exist today: `V1` (baseline schema:
  accounts, mfa_enrollments, recovery_codes, api_keys, verification_tokens, **lockout_state**,
  auth_audit, outbox), `V2` (refresh-token family tracking), `V3` (RBAC keyed on account_uuid), `V4`
  (audit keyed on account_uuid). `lockout_state` already exists from V1 with
  `(account_id PK, failed_attempts, last_failed_at, locked_until, lock_count)` — T01 does not create
  this table, only indexes it.
- **Events/outbox.** `events` module: `OutboxEvent` + `OutboxEventRepository` written in the same
  transaction as domain changes, `OutboxRelay` polls and publishes to Kafka, `EventTopics` maps
  aggregate type → topic. `KafkaProducerConfig` wires the producer. This module is intentionally
  domain-agnostic (`ArchitectureTest.events_module_stays_domain_agnostic`) so it can later be
  extracted to `libs/java/outbox` unchanged.
- **Security.** Spring Authorization Server (SAS) issuer plus this service acting as its own resource
  server (`token` module: `AuthorizationServiceConfig`, `SecurityChainsConfig`, `JwksConfig`,
  `TokenClaimsCustomizer`, `ReuseDetectingAuthorizationService` for refresh-token reuse detection).
  `common.PublicEndpoints` is the single, CI-enforced (ArchUnit) unauthenticated-path allowlist.
  `common.SecurityBeansConfig` supplies the delegating BCrypt-strength-12 `PasswordEncoder` and an
  injectable `Clock` bean (`Clock.systemUTC()`), used so time-dependent logic is unit-testable.

## 2. Existing code this task touches

T01 touches almost nothing at the Java level — it is a pure schema task.

- **Existing, must not be altered:** `V1__auth_baseline_schema.sql` through
  `V4__audit_key_on_account_uuid.sql` are immutable (L1). `lockout_state` (defined in V1) is the table
  T01's new index targets; its columns are already exactly as `design.md` §4c expects.
- **New in this task:** `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql`
  only — containing `idx_lockout_state_locked_until` (partial index, `WHERE locked_until IS NOT NULL`)
  and the `shedlock` table (`name` PK, `lock_until`, `locked_at`, `locked_by`). No entity, repository,
  service, or controller code is created or modified for T01.
- Nothing under `authn/`, `mfa/`, `apikey/` (T01's siblings that will consume this schema later) exists
  yet beyond empty `package-info.java` placeholders — confirmed by directory listing.

## 3. Established patterns to follow

- **Migrations.** One file per version, `V<n>__snake_case_description.sql`, additive only, a short
  header comment stating what it does and that it's additive (see V2–V4 for the house style). V5's
  content is already given verbatim in `design.md` §4c — copy it exactly (L1), do not paraphrase.
- **Schema/table conventions.** `BIGINT GENERATED ALWAYS AS IDENTITY` for internal PKs, `TIMESTAMPTZ`
  everywhere (no bare `TIMESTAMP`), explicit `NOT NULL DEFAULT` where the domain has one, partial
  indexes with `WHERE` clauses when the predicate is selective (`lockout_state`'s new index only
  indexes rows that are actually locked).
- **Config.** Flat `application.properties`, `${ENV_VAR:default}` placeholders, no secrets committed
  (D-010) — matches repo-wide convention confirmed in `agents.md`.
- **The `shedlock` table shape matches ShedLock's default JDBC schema** (`name`, `lock_until`,
  `locked_at`, `locked_by`) — i.e., it is being pre-provisioned for `net.javacrumbs.shedlock`'s
  standard JDBC LockProvider, ahead of the scheduled-job code that will use it (task #30).

## 4. Testing conventions

- Unit tests: plain JUnit, no Spring context, fixed `java.time.Clock` injected rather than
  `Instant.now()` calls (see `SecurityBeansConfig`'s `clock()` bean and its doc comment).
- Integration tests: `@SpringBootTest` + `TestcontainersConfiguration` (real Postgres 16-alpine +
  real Kafka via Testcontainers `@ServiceConnection`, never a shared/live DB — enforced by comment
  citing "gap-analysis §1 #25").
- Architecture/boundary tests: `ArchitectureTest` (ArchUnit) — module-private entities, no
  cross-module entity imports, repositories never `public`, every `Admin*` controller handler must
  carry `@PreAuthorize`, `PublicEndpoints` referenced only from `token`/`common`.
- No named test in `package.md` §8 maps to T01 (confirmed in the task header); the acceptance check
  for T01 is operational — the migration applies cleanly and `mvn -pl services/auth verify` stays
  green — not a new unit/integration test.

## 5. Known gaps / unknowns

- **`flyway-maven-plugin` is not configured** in `services/auth/pom.xml` or the root `pom.xml`. The
  task statement says to run `mvn -pl services/auth flyway:migrate`, which requires that plugin (with
  DB connection coordinates) to be present in the build. Today, `flyway-core` +
  `flyway-database-postgresql` are only regular dependencies, which give Spring Boot
  autoconfiguration migrate-on-startup behavior, not a standalone Maven goal. **I do not know**
  whether the plugin is expected to be added as part of T01 or was assumed already present — the spec
  package does not say. This should be raised as an open question rather than assumed either way.
- **Local Docker Compose Postgres exists** at `services/auth/compose.local.yaml`
  (`postgres:16-alpine`, db `checky`, user `checky`, port 5432) and matches
  `application.properties` defaults — confirmed present, not a gap.
- The four contract files listed in this task's header
  (`contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
  `contracts/events/auth/email-requested.v1.schema.json`,
  `contracts/events/auth/security-audit.v1.schema.json`) are not relevant to T01 — see
  `AI_CONTEXT_ANALYSIS.md` at the repo root for the full audit of why the generator attaches the same
  four contracts to every task header. Not treated as a T01 dependency here.
- No ShedLock library dependency (`net.javacrumbs.shedlock:shedlock-provider-jdbc-template` or
  similar) exists yet in `services/auth/pom.xml`. That is expected — T01 only provisions the table;
  the library and its `@SchedulerLock`-annotated job are task #30's concern, not T01's.
