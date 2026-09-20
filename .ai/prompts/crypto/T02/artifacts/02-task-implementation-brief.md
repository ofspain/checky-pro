<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# crypto · T02 · Phase 2 — Task Implementation Brief

## Task

Add `V1__chain_baseline.sql` transcribing `design.md` §4c's verbatim 9-table `chain` schema; wire
`services/crypto/pom.xml` + `application.properties` so `mvn -pl services/crypto flyway:migrate` runs
it against the local Postgres; grant the service's DB role INSERT+SELECT-only on `observations`,
`attestations`, `quorum_decisions`.

## Purpose

Establishes the persistence substrate every later crypto-service task depends on: the append-only
observation log (L3, the "defensible core" per `ARCHITECTURE.md`), the idempotency-keyed outbox
(L5), the reorg cursor (L6), the signed token allowlist (L7), and the screening/attestation audit
trails (L12, L10). No business logic yet — schema only.

## Scope

**In**: the migration file itself, the pom/properties wiring needed to run it locally, the
restricted-role grant. **Out**: any entity/repository Java class (every table's JPA mapping is added
by whichever later task first needs it — e.g. `Watch`/`WatchRepository` lands with T-whichever adds
`WatchService`); seeding `token_allowlist` (package.md §10: "seeded by a companion migration/config,"
not this one); any real provider/quorum/attest code.

## Business Rules

None directly implemented — see Phase 1. This task builds the tables several future
requirement-implementing tasks will read/write.

## Locked Decisions

L3, L5, L6, L7, L12 (schema shape only, per Phase 1); L13 (secrets discipline governs the new
datasource config and whatever the new restricted role's local password turns out to be).

## Dependencies

`flyway-maven-plugin` (pin `11.7.2`, matching `services/auth/pom.xml`'s own version and no-executions
binding). No new Java/Maven dependency beyond that — `flyway-core`, `flyway-database-postgresql`,
`postgresql` already present from T01.

## Inputs

`design.md` §4c's verbatim migration text (already transcribed in full at Phase 0); `services/auth`'s
own `pom.xml`/`application.properties`/`compose.local.yaml` as mirror sources; the current
`services/crypto/pom.xml`/`application.properties` from T01 (no datasource/Flyway config yet).

## Outputs

A new `chain` schema, 9 tables, actually created in the shared local Postgres instance by a real
`mvn -pl services/crypto flyway:migrate` run (not just a written-but-unexecuted SQL file); a second,
more-restricted Postgres role with the three-table grant in place; `services/crypto`'s own
datasource/Flyway config wired to point at it correctly.

## State Changes

First real runtime state this service creates: a live schema in the shared local Postgres container.
No application code reads or writes it yet.

## Files to Create

- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql`

## Files to Modify

- `services/crypto/pom.xml` (flyway-maven-plugin)
- `services/crypto/src/main/resources/application.properties` (datasource/JPA/Flyway block)

## Files NOT to Modify

`spec/crypto-service/**`; `services/auth/**` (mirror source, read-only); any
`services/crypto/src/main/java` entity/repository (none created this task).

## Acceptance Criteria

- AC1 — `V1__chain_baseline.sql` matches `design.md` §4c exactly (9 tables, `chain` schema,
  verbatim). No blocker — mechanical.
- AC2 — `mvn -pl services/crypto flyway:migrate` succeeds against the local Postgres and is actually
  run (not just wired), with the resulting schema verified to exist. **Blocked on Open Question 2**
  (local Postgres topology — reuse auth's compose file or add a new one).
- AC3 — `observations`, `attestations`, `quorum_decisions` are INSERT+SELECT-only for the service's
  own DB role. **Blocked on Open Question 1** — no established pattern for a second, restricted role
  in this codebase; the app-datasource-vs-Flyway-datasource split this implies needs a decision
  before it can be built.

## Required Tests

None — matches T01's own precedent; no `package.md` §8 test maps to a schema-only task.

## Constraints

- **Security (L13)**: no secret, real or placeholder, beyond the kind of well-known local-only
  placeholder auth's own Flyway plugin already uses and has already passed review with — this task
  does not get to introduce a *new* class of committed credential just because it's for a different
  role.
- **Verbatim (design.md §4c heading)**: the migration SQL is copied exactly; no paraphrasing, no
  "improving" the schema (e.g., no added columns/indexes beyond what's specified), even if a
  reviewer might otherwise suggest one — deviations from a VERBATIM artifact need their own explicit
  human-gate decision, not silent implementer judgment.
- **Build**: `mvn -pl services/auth verify` must remain unaffected (same AC5-style sibling check as
  T01, mechanically guaranteed the same way — no file under `services/auth` is touched).

## Open Questions

**Both are Phase 4 blockers**, carried unchanged from Phase 1:

1. Restricted DB role mechanics for AC3 — role name, where it's created, its local password, and
   critically whether the app's own runtime datasource uses this restricted role (requiring Flyway to
   use a separate, more-privileged datasource) or the grant merely restricts a role the app doesn't
   otherwise use differently than before.
2. Local Postgres topology for AC2 — reuse `services/auth/compose.local.yaml`'s existing container
   (same instance, new schema) vs. a new `services/crypto/compose.local.yaml`.

---

**Phase 2 complete — implementation brief written.** Proceed to Phase 3 (Design Challenge) on
approval.
