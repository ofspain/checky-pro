<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# crypto · T02 · Phase 3 — Design Challenge

Consumed `artifacts/02-task-implementation-brief.md`, `spec/crypto-service/design.md` §4c,
`agents.md`, `services/auth/pom.xml`, `services/auth/compose.local.yaml`, and the current
`services/crypto/pom.xml` / `application.properties`.

The brief is sound for the schema-transcription scope, but several assumptions around the restricted
DB role, the Flyway runtime vs. plugin split, and local-dev topology need to be resolved before the
brief is frozen. Findings only — no redesign or implementation.

---

## Findings

### 1. A separate restricted role needs schema and sequence privileges, not just table INSERT+SELECT

- **Issue:** The task says "grant the service DB role INSERT+SELECT-only on `observations`,
  `attestations`, `quorum_decisions`." Those three tables use `GENERATED ALWAYS AS IDENTITY`, and
  they live in the `chain` schema. A role with only INSERT/SELECT on the tables cannot actually
  insert rows without `USAGE` on the schema and `USAGE`/`SELECT` on the underlying identity
  sequences.
- **Severity:** High.
- **Evidence:** `design.md` §4c defines all three tables with `BIGINT GENERATED ALWAYS AS IDENTITY`
  inside `CREATE SCHEMA chain`. The TIB mentions only table-level INSERT+SELECT grants and does not
  mention schema/sequence grants. PostgreSQL requires `USAGE` on a sequence for `nextval()` to fire
  on insert, and `USAGE` on the schema to resolve the table name.
- **Recommended brief amendment:** Add explicit grant statements to the migration (or to the infra
  grant procedure) giving the restricted role `USAGE ON SCHEMA chain` and
  `USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain`, plus `CONNECT` on the database if the role is
  created locally. Update AC3 verification to assert the restricted role can insert and cannot
  update/delete.

### 2. AC3 is unenforceable if the runtime app user owns the tables

- **Issue:** In PostgreSQL, a table owner bypasses privilege checks. If Flyway creates the tables
  with the same user the application uses at runtime, no `GRANT INSERT, SELECT ONLY` will actually
  restrict that user.
- **Severity:** High.
- **Evidence:** `services/auth/pom.xml` configures the Flyway Maven plugin to run as the `checky`
  superuser (`checky-local-only`). If `services/crypto` later uses `checky` as its runtime
  datasource user, `checky` will own the `chain` tables and ignore any limited grant. The TIB Open
  Question 1 hints at this but does not state the owner-vs-grantee requirement explicitly.
- **Recommended brief amendment:** Mandate that the runtime application datasource uses a
  **different, non-owner role** (e.g., `crypto_app`) from the Flyway/migration owner role. State
  clearly that the INSERT+SELECT-only grant targets the runtime role, not the migration role.

### 3. Runtime Spring Boot Flyway conflicts with a restricted runtime role

- **Issue:** Spring Boot auto-configures Flyway and runs it at application startup by default. If the
  runtime datasource uses the restricted role, startup will fail because that role cannot write to
  `flyway_schema_history` or create/alter objects.
- **Severity:** High.
- **Evidence:** `services/crypto/pom.xml` currently has no Flyway plugin and no
  `spring.flyway.enabled` property. Adding `spring.flyway.*` datasource config without disabling
  runtime Flyway will trigger Spring Boot's `FlywayMigrationStrategy` on every app start. The TIB
  says the Maven plugin should be no-executions-bound (like auth), but does not address runtime
  Flyway.
- **Recommended brief amendment:** Choose one of:
  1. Disable runtime Flyway (`spring.flyway.enabled=false`) and rely on the Maven plugin / CI / init
     container for migrations; **or**
  2. Provide a separate `spring.flyway.user` / `spring.flyway.password` for Flyway at runtime that
     is more privileged than the application datasource.
  Document the chosen pattern in the brief and in `application.properties`.

### 4. Local Postgres topology is unresolved and has hidden assumptions

- **Issue:** Open Question 2 is correctly flagged as a Phase 4 blocker, but the brief does not list
  the trade-offs that need to be decided: port conflicts, cross-service Docker dependency, and
  whether Kafka is also needed.
- **Severity:** Medium.
- **Evidence:** Only `services/auth/compose.local.yaml` exists. If a new
  `services/crypto/compose.local.yaml` binds Postgres to `5432`, it will conflict with auth's
  container. If it reuses auth's compose, a crypto developer must know to start the auth compose
  file. `agents.md` expects local dev to run against Postgres + Kafka.
- **Recommended brief amendment:** Decide in Phase 4 and record: either (a) reuse
  `services/auth/compose.local.yaml` and document the startup command for crypto devs, or (b) create
  `services/crypto/compose.local.yaml` using a non-conflicting Postgres port (and matching
  `application.properties` default), and decide whether Kafka is included now or deferred to T04.

### 5. The Flyway Maven plugin does not read `application.properties`

- **Issue:** The brief says "wire `services/crypto/pom.xml` + `application.properties`" as if the
  plugin will pick up the runtime datasource config. The Maven plugin has its own configuration
  block.
- **Severity:** Medium.
- **Evidence:** `services/auth/pom.xml` hardcodes the Flyway plugin's `url`, `user`, `password`, and
  `schemas` directly in the plugin `<configuration>`. The runtime datasource in
  `services/auth/application.properties` is independent. The crypto TIB does not distinguish the two.
- **Recommended brief amendment:** Clarify that the `flyway-maven-plugin` block in `pom.xml` carries
  the local admin connection (mirroring auth: no lifecycle binding, local-only placeholder),
  while `application.properties` configures the runtime datasource (which may use the restricted
  role).

### 6. No concrete verification steps for AC2/AC3

- **Issue:** AC2 says "run `flyway:migrate` and verify the schema exists"; AC3 says "grant
  INSERT+SELECT-only." Neither specifies how to verify success, especially the negative grant cases.
- **Severity:** Medium.
- **Evidence:** TIB Acceptance Criteria section lists AC2 and AC3 but no verification commands. With
  no automated tests required, the only guard is a manual check.
- **Recommended brief amendment:** Add a short verification script to the brief, e.g.:
  - `psql -c "\dt chain.*"` to list created tables;
  - connect as the restricted role and assert `INSERT INTO observations ...` succeeds;
  - assert `UPDATE observations ...` and `DELETE FROM observations ...` are rejected;
  - repeat for `attestations` and `quorum_decisions`.

### 7. Crypto outbox schema differs from auth's outbox and from `libs/java/outbox` (still empty)

- **Issue:** `design.md` §4c defines a crypto-specific `outbox` table (bigint id, idempotency key,
  no `headers`/`schema_version`). Auth's `V1__auth_baseline_schema.sql` defines an outbox with UUID
  id, `headers`, and `schema_version`. `libs/java/outbox` currently contains only `.gitkeep`.
- **Severity:** Medium.
- **Evidence:** Side-by-side comparison of `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql`
  and `spec/crypto-service/design.md` §4c. The shared outbox relay has not been built yet, so the
  contract is unverified.
- **Recommended brief amendment:** Add an explicit note that the `outbox` table in V1 is the
  crypto-service contract and must be reconciled with `libs/java/outbox` when that library lands
  (T04+). Do not change the verbatim V1 without a human-gate decision.

### 8. Crypto `application.properties` lacks an active local profile

- **Issue:** Auth sets `spring.profiles.active=local` so local-only defaults are not active in higher
  environments. Crypto does not. If T02 adds datasource defaults with local placeholders, those
  defaults will apply in every profile unless overridden.
- **Severity:** Medium.
- **Evidence:** `services/auth/src/main/resources/application.properties:10` sets the active profile.
  `services/crypto/src/main/resources/application.properties` currently has no profile directive.
  `agents.md` requires startup to fail on missing/invalid config in non-local profiles.
- **Recommended brief amendment:** Add `spring.profiles.active=local` to crypto's
  `application.properties` as part of this task, mirroring auth, and use `${ENV:default}` placeholders
  only for values that are safe defaults in the `local` profile.

### 9. "9 tables" claim contradicts the verbatim migration

- **Issue:** The TIB repeatedly says the migration creates 9 tables, but `design.md` §4c's verbatim
  SQL creates 10 (`watches`, `observations`, `quorum_decisions`, `provider_health`,
  `chain_cursors`, `token_allowlist`, `screening_results`, `attestations`, `outbox`, `shedlock`).
- **Severity:** Low.
- **Evidence:** TIB lines 51 and 77 say "9 tables"; `design.md` §4c contains 10 `CREATE TABLE`
  statements.
- **Recommended brief amendment:** Update the brief to "10 tables (9 business tables +
  `shedlock`)" or explicitly state that `shedlock` is intentionally excluded from the count.

### 10. Append-only grant assumes `s3_snapshot_key` is never updated

- **Issue:** `observations` is one of the INSERT+SELECT-only tables, yet its `s3_snapshot_key` is
  nullable. If any future process fills the S3 key after the row is inserted, that process would need
  UPDATE privileges, violating both the grant and the append-only intent (`design.md` L3).
- **Severity:** Low.
- **Evidence:** `design.md` §4c defines `observations.s3_snapshot_key VARCHAR(256)` as nullable and
  states the observation log is append-only (INSERT + SELECT only). The TIB does not address how the
  S3 key gets populated.
- **Recommended brief amendment:** Add a design note that `s3_snapshot_key` must be supplied at row
  insertion time; any S3 snapshot write must complete before the observation row is persisted. If a
  post-insert update is ever needed, it must be performed by a privileged non-app role or the
  append-only invariant must be revisited.

---

## Open questions for Phase 4 (already flagged in TIB, reinforced here)

1. **Restricted role mechanics:** What is the runtime app role name, where is it created, what is its
   local password, and does the local runtime datasource actually use it? This drives findings 1–3.
2. **Local Postgres topology:** Reuse auth's compose file or create a crypto-specific one? This
   drives finding 4.

These two questions are genuine Phase 4 blockers; the rest can be folded into the brief once they are
answered.
