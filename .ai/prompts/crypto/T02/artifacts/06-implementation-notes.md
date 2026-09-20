<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# crypto · T02 · Phase 6 — Implementation Notes

## Files created

- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — verified byte-for-byte
  identical to `design.md` §4c's verbatim block via `diff` against a fresh extraction from the spec
  file itself (not eyeballed). 10 tables + 4 indexes, `chain` schema.
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql` — idempotent
  `crypto_app` role creation, `CONNECT`/`USAGE` grants, and the AC3 table grants, kept separate from
  V1 per Phase 4's structural decision.

## Files modified

- `services/crypto/pom.xml` — added `flyway-maven-plugin` (admin/`checky` credentials,
  `<schemas>chain</schemas>`), mirroring `services/auth/pom.xml` exactly otherwise.
- `services/crypto/src/main/resources/application.properties` — added `spring.profiles.active=local`,
  the runtime (`crypto_app`) datasource block, and `spring.flyway.enabled=false`.

## AC1 verification — byte-for-byte diff, not assumed

```
awk '/^```sql$/{flag=1;next}/^```$/{if(flag){flag=0}}flag' spec/crypto-service/design.md \
  > /tmp/design_v1_extract.sql
diff /tmp/design_v1_extract.sql services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql
```
→ empty diff (`IDENTICAL`).

## AC2 verification — real migration run against real Postgres

`docker compose -f services/auth/compose.local.yaml up -d postgres` (was not already running),
waited on `pg_isready`, then `mvn -pl services/crypto flyway:migrate`:

```
Migrating schema "chain" to version "1 - chain baseline"
Migrating schema "chain" to version "2 - crypto app role and grants"
Successfully applied 2 migrations to schema "chain", now at version v2
```

`psql -c '\dt chain.*'` confirms all 10 tables + `flyway_schema_history`, all owned by `checky`.

## AC3 verification — real grant/deny behavior, not inferred from the SQL text

Connected as `crypto_app` (not `checky`) and ran real statements against all three named tables:

| Table | INSERT | UPDATE | DELETE |
|---|---|---|---|
| `observations` | succeeded | `permission denied for table observations` | `permission denied for table observations` |
| `attestations` | succeeded | `permission denied for table attestations` | `permission denied for table attestations` |
| `quorum_decisions` | succeeded | `permission denied for table quorum_decisions` | `permission denied for table quorum_decisions` |

**Negative-proof beyond the stated AC**: also confirmed `crypto_app` cannot even `SELECT` from
`watches` (`permission denied for table watches`) — proving the grant is precisely the three named
tables, not accidentally schema-wide. Confirmed `checky` (the owner) still has full, unrestricted
access after `crypto_app`'s grants were applied (`SELECT count(*) FROM chain.observations` succeeds
as `checky`) — the restriction is scoped to the grantee, not a schema-wide lockdown that would also
have broken the Flyway/admin path.

Test rows cleaned up afterward (as `checky`, since `crypto_app` structurally cannot delete them —
itself a live demonstration of the grant working as intended).

## AC4 verification

`spring.flyway.enabled=false` is set. Not separately exercised by starting the app this phase (no
main-class smoke test was in scope) — the property is unambiguous and Spring Boot's own
`FlywayAutoConfiguration` is conditional on it; no further runtime proof was judged necessary beyond
Phase 12's own eventual `mvn -pl services/crypto verify` (which already ran clean, confirming nothing
about adding these properties broke the module's own build/packaging).

## Sibling-build check (AC5-equivalent, same discipline as T01)

`git status --porcelain services/auth` — empty, both before and after this phase's work. Auth's
`compose.local.yaml` was *run*, never edited.

## Notes carried from Phase 4 (Kimi Findings 7 & 10, documented not resolved)

- `outbox`'s shape here (bigint id, no `headers`/`schema_version`) differs from auth's own outbox
  table and from the still-empty `libs/java/outbox`. Reconciliation is deferred to whichever task
  actually builds the shared outbox relay (T04+ per the task list) — not this task's call to make.
- `observations.s3_snapshot_key` is nullable; the append-only INSERT+SELECT-only grant means the S3
  key must be supplied at row-insertion time (fetch-then-insert), not backfilled after the fact. A
  future need to backfill would require either a privileged non-`crypto_app` write path or revisiting
  the append-only grant — not a decision for this task.

---

**Phase 6 complete — both migrations written and verified, grants proven with real INSERT/UPDATE/
DELETE attempts, not inferred.** Proceed to Phase 7 (Self Review) on approval.
