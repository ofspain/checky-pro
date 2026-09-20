# auth · T01 · Phase 3 — Design Challenge

## Brief under review
`artifacts/02-task-implementation-brief.md` (auth · T01 · Phase 2 — Task Implementation Brief)

This review challenges the TIB before it is frozen. Findings are scoped to T01 only and are derived from the TIB plus the referenced spec files (`design.md` §4c, `tasks.md` task 1, `package.md` §10, `agents.md` persistence/platform rules, the actual `services/auth/pom.xml`, `compose.local.yaml`, and `application.properties`).

---

## Findings

### 1. Literal Maven goal is unrunnable without build changes
**Issue.** The task statement says to run `mvn -pl services/auth flyway:migrate`. Neither `services/auth/pom.xml` nor the root `pom.xml` declares `flyway-maven-plugin`, so the literal command cannot execute today. The TIB correctly flags the gap as an open question but does not resolve it, leaving the acceptance criterion unactionable.
**Severity.** Blocker
**Evidence.** `services/auth/pom.xml` dependencies contain `flyway-core` and `flyway-database-postgresql` only; the `<build>/<plugins>` section has only `spring-boot-maven-plugin`. Phase 1 extraction explicitly records the missing plugin.
**Recommended brief amendment.** Promote the decision from Open Questions to a Locked Decision or Files-to-Modify entry:
* Option A — add `flyway-maven-plugin` to `services/auth/pom.xml` with datasource binding to the local Postgres (`jdbc:postgresql://localhost:5432/checky`, user `checky`, password `checky-local-only`, schema `auth`), so the stated command passes.
* Option B — formally amend AC4 to accept Spring Boot migrate-on-boot (e.g., run `mvn -pl services/auth verify` or `mvn -pl services/auth spring-boot:run`) as satisfying "migration applies cleanly" and deprecate the literal `flyway:migrate` command.

### 2. Verification method is manual and untestable
**Issue.** AC4 requires the migration to "apply cleanly against the local Docker Compose Postgres," but the TIB does not define the verification command, expected observable schema state, or how a reviewer repeats the check in CI. With no named test, acceptance is essentially manual.
**Severity.** Medium
**Evidence.** `Required Tests` states "None"; `Acceptance Criteria` uses subjective language ("applies cleanly"). No psql introspection, JDBC schema assertion, or smoke-test step is provided.
**Recommended brief amendment.** Add a concrete `Verification Steps` subsection under `Acceptance Criteria`, for example:
```sql
-- index
SELECT indexname FROM pg_indexes
 WHERE schemaname = 'auth' AND indexname = 'idx_lockout_state_locked_until';
-- table
SELECT table_name FROM information_schema.tables
 WHERE table_schema = 'auth' AND table_name = 'shedlock';
```
Also state that these checks must pass after `mvn flyway:migrate` (or equivalent) and before the task is considered done.

### 3. Index rationale does not match any stated query pattern
**Issue.** The partial index `idx_lockout_state_locked_until` is justified as supporting the lockout state machine, but the scoped requirements (R17–R21) describe per-account evaluation that loads `lockout_state` by `account_id` (the existing primary key). No later task describes a scan or batch unlock by `locked_until`. The index may be dead schema, and the cleanup job named in R40 does not mention cleaning `lockout_state` rows.
**Severity.** Medium
**Evidence.** `requirements.md` R17–R21 and `tasks.md` #11–#13 only discuss incrementing, decaying, and clearing counters per account. `tasks.md` #30 (cleanup job) and R40 list expired verification tokens, old refresh-token families/archives, and stale ShedLock rows — not lockout rows. `design.md` §4c provides the index SQL without a query pattern.
**Recommended brief amendment.** Add a sentence to `Purpose` or `Business Rules` identifying the exact query that uses the index (e.g., a background job that selects currently-locked rows to clear/auto-unlock). If no such query exists, either defer the index to the task that first needs it or document that it is speculative optimization for future operations.

### 4. Maven plugin datasource binding is unspecified
**Issue.** If Option A from Finding 1 is chosen, the brief still lacks the datasource configuration that the `flyway-maven-plugin` needs (URL, user, password, schemas). The plugin does not read `application.properties`; it reads pom properties, system properties, or its own config file.
**Severity.** Medium
**Evidence.** `compose.local.yaml` exposes `checky/checky-local-only` on `localhost:5432` and `application.properties` uses `spring.flyway.default-schema=auth`. The TIB references neither for plugin configuration.
**Recommended brief amendment.** If the literal Maven command is to remain, add a `Files to Modify` entry with a plugin configuration snippet, e.g.:
```xml
<plugin>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-maven-plugin</artifactId>
  <configuration>
    <url>jdbc:postgresql://localhost:5432/checky</url>
    <user>checky</user>
    <password>checky-local-only</password>
    <schemas>auth</schemas>
  </configuration>
</plugin>
```

### 5. `IF NOT EXISTS` in the verbatim SQL conflicts with house style
**Issue.** `design.md` §4c instructs using `CREATE INDEX IF NOT EXISTS` and `CREATE TABLE IF NOT EXISTS`. The actual V1–V4 migrations do not use `IF NOT EXISTS`; they rely on Flyway's version tracking. The TIB does not acknowledge this deviation, which could later be flagged as a style/consistency issue or as masking a partial failure.
**Severity.** Low
**Evidence.** `V1__auth_baseline_schema.sql` uses plain `CREATE TABLE`/`CREATE INDEX`; V2–V4 presumably follow the same convention. `design.md` §4c contains `IF NOT EXISTS` for both new objects.
**Recommended brief amendment.** Add a note in `Constraints` confirming that `IF NOT EXISTS` is intentional for the verbatim artifact (e.g., to make the migration idempotent against a pre-provisioned local environment), or request the author to remove `IF NOT EXISTS` so V5 matches the existing migration convention.

### 6. Docker Compose startup prerequisite is unstated
**Issue.** The brief refers to "the local Docker Compose Postgres" and the `mvn flyway:migrate` command, but it does not tell the implementer to start the containers first. This is a tiny but real gap for a junior implementer or CI script.
**Severity.** Low
**Evidence.** `compose.local.yaml` exists and exposes Postgres on 5432 with the comment `docker compose -f services/auth/compose.local.yaml up -d`. The TIB does not include this step.
**Recommended brief amendment.** Add a prerequisite bullet in `Dependencies` or `Acceptance Criteria`: start local dependencies with `docker compose -f services/auth/compose.local.yaml up -d --wait` before running any migration command.

### 7. ShedLock timestamp-type assumption
**Issue.** The TIB states the `shedlock` table matches ShedLock's default JDBC schema, but the verbatim SQL uses `TIMESTAMPTZ` for `lock_until` and `locked_at`. ShedLock sample DDL varies (some samples use plain `TIMESTAMP`); the brief does not verify that the chosen ShedLock `LockProvider` and version accept `TIMESTAMPTZ` without timezone conversion surprises.
**Severity.** Low
**Evidence.** `design.md` §4c SQL defines `lock_until TIMESTAMPTZ NOT NULL` and `locked_at TIMESTAMPTZ NOT NULL`. `agents.md` requires `java.time` with a `Clock`, and the service will run in AWS with specific JVM/Postgres timezone configurations.
**Recommended brief amendment.** Add a constraint note: confirm with task #30 that the selected ShedLock `LockProvider` reads `TIMESTAMPTZ` correctly, or explicitly choose `TIMESTAMP WITH TIME ZONE`/`TIMESTAMP` and document the timezone semantics.

### 8. No failure-mode or rollback guidance
**Issue.** The brief describes a happy-path migration but does not address what to do if `flyway:migrate` fails partway, or how to roll back a bad V5 in a local environment. Because V5 is additive the risk is low, but acceptance should still be conditioned on a clean Flyway history.
**Severity.** Low
**Evidence.** `package.md` §10 contains manual rollback instructions (`DROP INDEX ... ; DROP TABLE ...`), but the TIB does not reference them. `State Changes` says "None to existing data" but does not discuss restoring clean state after a failed run.
**Recommended brief amendment.** Add a note in `Constraints` or `Acceptance Criteria`: if the migration fails for any reason, drop any partially-created V5 objects (index/table) and let Flyway re-run the file; confirm `flyway_schema_history` shows one successful row for V5.
