# auth · T01 · Phase 5 — Implementation Plan

Consumes `artifacts/04-frozen-task-brief.md` (STATUS: FROZEN). No code written in this phase —
planning only. Every file below traces to the frozen brief's Files sections; nothing added beyond
what it authorizes.

## Files to create

- `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql`
  Full content (verbatim from `design.md` §4c, including `IF NOT EXISTS` per the frozen brief's
  Finding-5 disposition — not to be altered):
  ```sql
  -- Cleanup support for lockout rows and scheduled job coordination.
  -- No changes to existing tables; this migration is additive only.

  CREATE INDEX IF NOT EXISTS idx_lockout_state_locked_until
      ON lockout_state(locked_until)
      WHERE locked_until IS NOT NULL;

  -- ShedLock for multi-replica scheduled cleanup (refresh-token family cleanup
  -- referenced in target-design.md §7 must not run concurrently across pods).
  CREATE TABLE IF NOT EXISTS shedlock (
      name VARCHAR(64) PRIMARY KEY,
      lock_until TIMESTAMPTZ NOT NULL,
      locked_at TIMESTAMPTZ NOT NULL,
      locked_by VARCHAR(255) NOT NULL
  );
  ```

## Files to modify

- `services/auth/pom.xml` — add `flyway-maven-plugin` to `<build>/<plugins>` (alongside the existing
  `spring-boot-maven-plugin`), configured against the local Docker Compose Postgres (frozen brief,
  Finding 1/Option A + Finding 4):
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
  No dependency version needs pinning — the parent Spring Boot BOM/Flyway version already governs
  `flyway-core`; the plugin should resolve compatibly without an explicit `<version>` unless Maven
  resolution fails at execution time.

## Public methods (signatures)

None. This task has no Java code — it is a schema migration plus a build-file (pom.xml) change.

## Private methods

None — same reason.

## Entities used

None created or modified. `lockout_state` (existing, from V1) is the target of the new index but its
JPA entity mapping (if any) is untouched — no entity class exists yet for this table and none is
created by T01 (confirmed: no `LockoutState.java` in `services/auth/src/main/java`).

## Repositories used

None. No repository touches `shedlock` (ShedLock's `LockProvider` accesses it via plain JDBC, not
Spring Data — and that wiring is task #30's concern, not T01's).

## Services used

None.

## Unit/integration tests required

None named in `package.md` §8 map to this task (confirmed in Phases 1/2/4). No new test file is
created. Verification is operational, per the frozen brief's AC4:

1. `docker compose -f services/auth/compose.local.yaml up -d --wait`
2. `mvn -pl services/auth flyway:migrate`
3. Confirm via psql/JDBC:
   ```sql
   SELECT indexname FROM pg_indexes
    WHERE schemaname = 'auth' AND indexname = 'idx_lockout_state_locked_until';
   SELECT table_name FROM information_schema.tables
    WHERE table_schema = 'auth' AND table_name = 'shedlock';
   SELECT version, success FROM auth.flyway_schema_history WHERE version = '5';
   ```
4. `mvn -pl services/auth verify` — existing suite must stay green (no test depends on the new
   objects yet; this only confirms nothing regressed).

## Execution order

1. **Schema file.** Create `V5__lockout_cleanup_and_shedlock.sql` with the verbatim content above.
2. **Build config.** Add the `flyway-maven-plugin` block to `services/auth/pom.xml`.
3. **Local infra.** Start Docker Compose Postgres (`compose.local.yaml`), wait for healthy.
4. **Migrate.** Run `mvn -pl services/auth flyway:migrate`.
5. **Verify.** Run the three introspection queries and `flyway_schema_history` check (AC4).
6. **Regression check.** Run `mvn -pl services/auth verify` and confirm the existing suite is
   unaffected.

No DAO/service/API layer exists for this task, so the usual "schema → dao → service → api → tests"
front-loading collapses to "schema → build config → migrate → verify" — steps 1–2 are the only
artifacts; 3–6 are verification, not construction.
