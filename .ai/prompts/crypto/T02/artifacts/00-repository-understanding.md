<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# crypto · T02 · Phase 0 — Repository Understanding

## Task (verbatim, `tasks.md` line 8)

"**Schema V1.** Add `V1__chain_baseline.sql` (design §4c); run `mvn -pl services/crypto
flyway:migrate` against local Docker Compose Postgres. Grant the service DB role INSERT+SELECT-only
on `observations`, `attestations`, `quorum_decisions`."

## Current state (post-T01)

`services/crypto` has: `pom.xml` (no Flyway Maven plugin yet — T01 deliberately deferred it, Finding
6, "not needed until T02"), a bare `CryptoServiceApplication`, `application.properties` (only
`spring.application.name` + virtual threads — no datasource/JPA/Flyway config yet), and
`T01SkeletonRegressionTest`. No `src/main/resources/db/migration/` directory exists. No entity/
repository Java code exists (T02's task statement doesn't ask for any — schema only).

## The verbatim migration (`design.md` §4c)

The full `V1__chain_baseline.sql` is specified byte-for-byte in `design.md` lines 86-218 — 9 tables:
`watches`, `observations` (append-only, L3), `quorum_decisions`, `provider_health`,
`chain_cursors`, `token_allowlist`, `screening_results`, `attestations` (append-only audit),
`outbox`, plus `shedlock`. All in a new `chain` schema. This is a VERBATIM artifact per design.md's
own heading ("copy exactly, do not paraphrase") — T02's job is to transcribe it into the migration
file unchanged, not redesign it.

## Established conventions to mirror

- **Migration path/naming**: `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql`
  → crypto's equivalent is `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql`
  (name given verbatim by both the task statement and design.md's own heading).
- **application.properties datasource/JPA/Flyway block**: auth's own file (already read in full at
  T01) sets `spring.datasource.url/username/password` (local-only placeholder), Hikari pool sizing,
  `connection-init-sql` for `search_path`, `spring.jpa.hibernate.ddl-auto=validate` +
  `default_schema`, and `spring.flyway.default-schema`/`schemas`/`create-schemas=true`. Crypto needs
  the equivalent block, schema `chain` instead of `auth`.
- **flyway-maven-plugin**: auth's `pom.xml` has this bound with NO `<executions>` (local-dev-only,
  runs solely via explicit `mvn flyway:migrate`, never during `package`/`verify`/CI), configured with
  a literal `checky-local-only` password. **This is not a T01-era open question re-litigated** — it's
  auth's own already-reviewed, already-accepted pattern (a well-known local-only placeholder, not a
  real secret) — T02 can mirror it directly for the same shared local Postgres instance.
  `services/auth/compose.local.yaml` runs a single `postgres:16-alpine` container, one role
  (`checky`, from `POSTGRES_USER`/`POSTGRES_PASSWORD`), one database (`checky`) — both services point
  at the same local instance/database, different schemas (`auth` vs `chain`).

## Genuinely new ground — not established anywhere in the spec

**The restricted-grant instruction has no precedent.** Auth's own migrations contain zero `GRANT`
statements (`grep GRANT services/auth/src/main/resources/db/migration/` — no matches); auth runs
entirely as the one `checky` role, no least-privilege table-level restriction anywhere. Neither
`agents.md` nor `package.md` §10 (Migration, rollout & rollback) names how a second, more-restricted
Postgres role gets created, what it's called, what password it uses locally, or which datasource
(admin-owner vs. restricted-runtime) the *application itself* connects with day-to-day versus which
one Flyway uses to run DDL. This is the one real design question T02 has to answer that T01 gave no
groundwork for — carried to Phase 1/2/3/4, not assumed here.

## Files T02 will touch (preliminary — Phase 1/2 to pin down exactly)

- **Create**: `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql`.
- **Modify**: `services/crypto/pom.xml` (add `flyway-maven-plugin`, mirroring auth's binding);
  `services/crypto/src/main/resources/application.properties` (datasource/JPA/Flyway block).
- **Open**: whether a role-creation/grant statement lives inside the migration itself, a separate
  Flyway migration, or a local-only `compose.local.yaml`-adjacent init script — genuinely undecided,
  see above.

---

**Phase 0 complete.** Proceed to Phase 1 (Specification Extraction) on approval.
