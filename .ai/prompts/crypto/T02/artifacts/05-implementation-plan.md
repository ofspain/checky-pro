<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# crypto · T02 · Phase 5 — Implementation Plan

Two new migration files, two modified files, no Java source. Plan specifies exact content per the
frozen brief.

## Files to create

### `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql`

Exact transcription of `design.md` §4c lines 86-218: `CREATE SCHEMA IF NOT EXISTS chain; SET
search_path TO chain;` followed by 10 `CREATE TABLE` statements in the order given —  `watches`,
`observations`, `quorum_decisions`, `provider_health`, `chain_cursors`, `token_allowlist`,
`screening_results`, `attestations`, `outbox`, `shedlock` — plus the 3 named indexes
(`idx_watches_chain_address`, `idx_observations_tx`, `idx_screening_address`,
`idx_outbox_unpublished`). Zero deviation: same column types, same constraints, same comments.

### `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql`

- Idempotent role creation (Postgres has no `CREATE ROLE IF NOT EXISTS`; guard via `DO $$ ... IF NOT
  EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'crypto_app') THEN CREATE ROLE ... END IF;
  END $$;`), `LOGIN PASSWORD 'crypto-app-local-only'`.
- `GRANT CONNECT ON DATABASE checky TO crypto_app;`
- `GRANT USAGE ON SCHEMA chain TO crypto_app;` (Finding 1)
- `GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain TO crypto_app;` (Finding 1 — covers the
  `GENERATED ALWAYS AS IDENTITY` sequences on all 10 tables, not just the 3 named ones, since every
  table uses the same identity pattern and a future task may need broader access; the *table-level*
  grants below remain the actual restriction).
- `GRANT INSERT, SELECT ON chain.observations, chain.attestations, chain.quorum_decisions TO
  crypto_app;` (AC3's own three named tables — INSERT+SELECT only, no UPDATE/DELETE granted, ever).
- A comment block stating this migration is **not** part of the verbatim §4c artifact — it is T02's
  own AC3 work, kept in its own file precisely so `V1` stays diffable against the spec unmodified.
- No grants on the other 7 tables in this migration — out of AC3's stated scope; a future task
  (whichever first needs `crypto_app` to read/write `watches`, `chain_cursors`, etc.) adds its own
  grant, not assumed here.

## Files to modify

### `services/crypto/pom.xml`

Add, after the existing `spring-boot-maven-plugin` entry in `<build><plugins>`:

```xml
<plugin>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-maven-plugin</artifactId>
  <version>11.7.2</version>
  <configuration>
    <url>jdbc:postgresql://localhost:5432/checky</url>
    <user>checky</user>
    <password>checky-local-only</password>
    <schemas>chain</schemas>
  </configuration>
</plugin>
```

Identical to auth's own block except `<schemas>chain</schemas>` — same admin role, same shared local
database, no `<executions>` binding (local-dev-only, matching auth).

### `services/crypto/src/main/resources/application.properties`

Append:

```properties
spring.profiles.active=local

# --- Datasource: runtime role only, never the migration owner (Kimi Finding 2) ---
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/checky}
spring.datasource.username=${DB_USERNAME:crypto_app}
spring.datasource.password=${DB_PASSWORD:crypto-app-local-only}
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=5000
spring.datasource.hikari.connection-init-sql=SET search_path TO chain, public

# --- Flyway: disabled at runtime (Kimi Finding 3) - migrations only ever run via the Maven
# plugin (checky), never by the app itself, in any environment. ---
spring.flyway.enabled=false
```

No `spring.jpa.hibernate.ddl-auto`/`default_schema` yet — no `@Entity` exists for Hibernate to
validate against; added by whichever task introduces the first one, not pre-emptively mirrored from
auth. No `spring.flyway.schemas`/`default-schema` either — with runtime Flyway disabled, Boot never
reads them; they'd be dead configuration.

## Verification steps (Kimi Finding 6 — to run for real in Phase 6, output recorded)

1. `docker compose -f services/auth/compose.local.yaml up -d` (if not already running).
2. `mvn -pl services/crypto flyway:migrate` — expect both `V1`/`V2` to apply.
3. `psql postgresql://checky:checky-local-only@localhost:5432/checky -c '\dt chain.*'` — expect 10
   tables.
4. `psql postgresql://crypto_app:crypto-app-local-only@localhost:5432/checky -c "INSERT INTO
   chain.observations (chain, tx_hash, provider, fact_type, raw_response) VALUES ('ETHEREUM',
   '0xtest', 'test-provider', 'existence', '{}');"` — expect success.
5. Same connection, `UPDATE chain.observations SET provider = 'x' WHERE tx_hash = '0xtest';` — expect
   a permission-denied error.
6. Same connection, `DELETE FROM chain.observations WHERE tx_hash = '0xtest';` — expect a
   permission-denied error (row stays; acceptable, it's fake test data in local dev only).
7. Repeat steps 4-6's shape (insert succeeds, update/delete denied) for `attestations` and
   `quorum_decisions` with minimal valid rows for each table's `NOT NULL`/`CHECK` constraints.

## Tests required

None — matches T01's own precedent; no `package.md` §8 test maps to this task, and there's no
production package for an ArchUnit/JUnit guard test to target yet (unlike T01, there's no doc/pom
content to regression-guard here beyond what a real `psql` verification already checks more directly
— a JUnit test asserting SQL grant text in a migration file would be a weaker, more indirect proxy
for the same check the verification script already performs against a live database).

## Execution order

1. Write `V1__chain_baseline.sql`, diff it against `design.md` §4c to confirm byte-for-byte match.
2. Write `V2__crypto_app_role_and_grants.sql`.
3. Edit `pom.xml` (Flyway plugin) and `application.properties`.
4. Start `services/auth/compose.local.yaml`'s Postgres if not running.
5. `mvn -pl services/crypto flyway:migrate` — run for real.
6. Run the 7-step verification script above; record actual output in Phase 6 notes.
7. `mvn -pl services/crypto validate` / `mvn -pl services/auth verify` — confirm the sibling-build
   guarantee still holds (same mechanism argument as T01: no file under `services/auth` touched).

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
