<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# crypto · T02 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

## Decision packet

All 10 Phase 3 (Kimi) findings verified before disposition — all 10 confirmed accurate (3 High, the
rest Medium/Low), a first for this pipeline's Kimi reviews.

| # | Finding | Disposition |
|---|---|---|
| 1 | Sequence/schema grants needed, not just table grants | **Accepted.** `USAGE ON SCHEMA chain` + `USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain` added alongside the three table grants — `GENERATED ALWAYS AS IDENTITY` needs sequence `USAGE` for `nextval()` to fire on insert. |
| 2 | Table owner bypasses grants — runtime role must not be the migration-owning role | **Human-gate decision: confirmed.** `checky` (superuser, already used by auth's own Flyway plugin) remains the migration/DDL owner. A new role, `crypto_app`, is the runtime app's own datasource role and owns nothing. |
| 3 | Spring Boot auto-runs Flyway at startup by default — conflicts with a restricted runtime role | **Human-gate decision: `spring.flyway.enabled=false`.** The Maven plugin (running as `checky`) is the only thing with DDL rights, ever, in any environment — matches the task statement's own "run `mvn ... flyway:migrate`" wording and is simpler than a second `spring.flyway.user/password` pair. |
| 4 | Local Postgres topology trade-offs unstated | **Human-gate decision: reuse `services/auth/compose.local.yaml`.** Same instance, new `chain` schema — avoids a second container and a port-5432 collision; documented as a startup prerequisite, not a new file. |
| 5 | Flyway Maven plugin doesn't read `application.properties` | **Accepted, clarified.** `pom.xml`'s plugin `<configuration>` carries the admin (`checky`) connection, mirroring auth exactly except `<schemas>chain</schemas>`. `application.properties` configures only the runtime (`crypto_app`) datasource — the two are independent, as Kimi noted. |
| 6 | No concrete AC2/AC3 verification steps | **Accepted.** A verification script (list tables; connect as `crypto_app`; assert INSERT succeeds, UPDATE/DELETE rejected, on all three named tables) is run and its output recorded in Phase 6 implementation notes, not assumed. |
| 7 | Crypto's `outbox` shape differs from auth's and from empty `libs/java/outbox` | **Accepted, no schema change** (V1 stays verbatim). A note is added (Phase 6 notes, not the SQL itself) flagging this for reconciliation whenever `libs/java/outbox` actually lands. |
| 8 | Missing `spring.profiles.active=local` | **Accepted.** Added to `application.properties`, mirroring auth. |
| 9 | "9 tables" is wrong — verbatim SQL has 10 (`shedlock` was miscounted out) | **Accepted, corrected.** All artifacts from this point say "10 tables (9 business tables + `shedlock`)." Phase 0-2's "9 tables" language was a genuine miscount, not a scope change. |
| 10 | `observations.s3_snapshot_key` is nullable but the table is append-only — no defined write-timing | **Accepted, documented, no schema change** (verbatim). Phase 6 notes will state: the S3 key must be supplied at insertion time (fetch-then-insert, not insert-then-backfill); a future need to backfill it would require revisiting the append-only grant, not silently adding UPDATE. |

**Structural call not raised by Kimi, made here**: the role/grant statements (Findings 1/2) will
live in a **second migration, `V2__crypto_app_role_and_grants.sql`**, not appended to V1. This keeps
`V1__chain_baseline.sql` a byte-for-byte match of `design.md` §4c's verbatim block with zero added
noise (a reviewer can diff V1 directly against the spec), and is more conventional Flyway practice —
one concern per migration.

## Frozen brief (Phase 2 TIB, as amended)

### Task

Create `V1__chain_baseline.sql` (verbatim, 10 tables) and `V2__crypto_app_role_and_grants.sql` (new);
wire `services/crypto/pom.xml`'s Flyway Maven plugin (admin/`checky` credentials, mirroring auth) and
`application.properties` (runtime `crypto_app` datasource, `spring.flyway.enabled=false`,
`spring.profiles.active=local`); run `mvn -pl services/crypto flyway:migrate` against
`services/auth/compose.local.yaml`'s existing Postgres container; verify the grants actually work
(positive INSERT, negative UPDATE/DELETE) as `crypto_app`.

### Scope

**In**: the two migration files, the pom/properties wiring, the actual local migration run, the
grant-verification script and its recorded output. **Out**: any entity/repository Java class; seeding
`token_allowlist`; `libs/java/outbox` reconciliation (noted, not resolved); Kafka (not this task).

### Files to Create

- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql`
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql`

### Files to Modify

- `services/crypto/pom.xml` (flyway-maven-plugin, admin credentials, `<schemas>chain</schemas>`)
- `services/crypto/src/main/resources/application.properties` (runtime datasource as `crypto_app`,
  `spring.flyway.enabled=false`, `spring.profiles.active=local`)

### Files NOT to Modify

`spec/crypto-service/**`; `services/auth/**` (mirror source; its `compose.local.yaml` is reused,
not modified); no Java source.

### Acceptance Criteria

- AC1 — `V1__chain_baseline.sql` matches `design.md` §4c exactly (10 `CREATE TABLE` statements,
  `chain` schema, byte-for-byte).
- AC2 — `mvn -pl services/crypto flyway:migrate` succeeds against `services/auth`'s local Postgres
  container; both migrations apply; `\dt chain.*` confirms all 10 tables exist.
- AC3 — `crypto_app` role exists, owns nothing, has `USAGE` on schema `chain` + its sequences, and
  INSERT+SELECT-only on `observations`, `attestations`, `quorum_decisions` — verified by a real
  connection as `crypto_app`: INSERT succeeds, UPDATE and DELETE are rejected, on all three tables.
- AC4 (new, Kimi Finding 3's resolution) — `spring.flyway.enabled=false` is set; starting the app
  (if done as part of verification) must not attempt runtime migration as the restricted role.

### Constraints

- **Verbatim (`design.md` §4c)**: V1's `CREATE SCHEMA`/`CREATE TABLE` content is transcribed exactly;
  zero added columns, indexes, or grants inside that file.
- **Secrets (L13)**: `crypto_app`'s local password is a well-known local-only placeholder
  (`crypto-app-local-only`), matching auth's own already-reviewed `checky-local-only` pattern — not a
  new class of committed credential.
- **Build**: `mvn -pl services/auth verify` unaffected — no file under `services/auth` is touched
  (its compose file is *run*, not edited).

### Open Questions

None remaining — both Phase 1/2/3 blockers resolved above via human gate.

---

**Phase 4 complete — task brief frozen and approved.** Proceed to Phase 5 (Implementation Plan).
