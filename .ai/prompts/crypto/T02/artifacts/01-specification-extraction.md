<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# crypto · T02 · Phase 1 — Specification Extraction

## Business Rules

None of `package.md` §8's 28 named tests map to T02 — all require feature code this task doesn't
add. T02 builds the persistence *substrate* several requirements will later depend on (L3's
append-only observation log, L5's idempotency key column, L6's reorg cursor table, L7's signed
allowlist table, L12's screening-results table) without implementing any of the logic that populates
or reads them yet.

## Locked Decisions

- **L3** — Observation log is verbatim and written first: the `observations` table is append-only by
  design (no `updated_at`, no update path implied by the schema); this task's own AC3 (the
  INSERT+SELECT-only grant) is what makes "append-only" a DB-enforced guarantee rather than just a
  convention — this table is one of the three named in the task statement.
- **L5** — Deterministic idempotency key on every event: `outbox.idempotency_key` carries
  `chain:txhash:eventtype`, `UNIQUE` constrained (`uq_outbox_idempotency`).
- **L6** — Reorg as first-class transition: `chain_cursors` holds `last_block`/`last_finalized_block`
  per chain/watch for cursor walk-back; no logic yet, just the column shape.
- **L7** — Token identity by contract address only: `token_allowlist` is `UNIQUE(chain,
  contract_address, version)`, carries `signature` — schema enforces the *shape* of "signed,
  versioned," not the verification itself (that's a later task).
- **L12** — Screening gates attestation, fail-closed: `screening_results` schema only; no client
  exists yet.
- **L13** — Secrets discipline: governs how the new `application.properties` datasource block and any
  new Postgres role/password are introduced — this is the phase's central open question (below).

## Files involved

**To create:**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — verbatim from
  `design.md` §4c (lines 86-218), transcribed exactly, not paraphrased or reordered.

**To modify:**
- `services/crypto/pom.xml` — add `flyway-maven-plugin`, mirroring `services/auth/pom.xml`'s own
  binding (no `<executions>`, local-dev-only, runs via explicit `mvn flyway:migrate`).
- `services/crypto/src/main/resources/application.properties` — add the datasource/JPA/Flyway block
  (schema `chain`, not `auth`), mirroring auth's own shape.

**Read-only:**
- `design.md` §4c (the verbatim migration), §5 (data model summary), §6 (package map, not this
  task's concern).
- `services/auth/pom.xml`, `services/auth/src/main/resources/application.properties`,
  `services/auth/compose.local.yaml` (mirror sources).

## Dependencies

- `flyway-maven-plugin` — pin the same version auth uses (`11.7.2`), same local-only credential
  pattern (already reviewed and accepted at auth-service, not a new secrets question to relitigate).
- No new Java dependency — `flyway-core`/`flyway-database-postgresql`/`postgresql` are already in
  `services/crypto/pom.xml` from T01.

## Acceptance Criteria

Derived from the task statement's three clauses:

| AC | Statement | Note |
|---|---|---|
| AC1 | `V1__chain_baseline.sql` added per `design.md` §4c | Mechanical transcription — unambiguous. |
| AC2 | `mvn -pl services/crypto flyway:migrate` runs successfully against local Docker Compose Postgres | Requires the pom plugin + application config from AC1's dependency work, and a running local Postgres. **Open question below**: does crypto reuse `services/auth/compose.local.yaml`'s existing container (same role/db, different schema), or does it need its own compose file? |
| AC3 | Service DB role granted INSERT+SELECT-only on `observations`, `attestations`, `quorum_decisions` | **Blocked on Open Questions below** — no established pattern anywhere in this codebase for a second, restricted Postgres role distinct from the migration-owning role. |

## Required Tests

None — matches T01's own precedent and `package.md` §8's own scope (no named test maps to a
schema-only task).

## Open Questions

**Blockers, both requiring a Phase 4 human-gate decision** (surfaced at Phase 0, re-confirmed here
after re-reading `package.md` §10 and `agents.md` directly — neither names this pattern):

1. **Restricted DB role mechanics (AC3).** Auth-service has zero `GRANT` statements anywhere and runs
   entirely as one Postgres role (`checky`). Introducing a second, least-privilege role for
   crypto-service is new ground for this codebase. Needs a decision on: the new role's name; where
   its creation lives (inside `V1__chain_baseline.sql` itself via `CREATE ROLE`/`GRANT`, a separate
   migration, or a `compose.local.yaml`-adjacent init script outside Flyway's purview); what
   local-only password it uses (same "well-known local placeholder" pattern auth already established
   for its own Flyway plugin, or something else); and — the part with real architectural weight —
   whether the *application's own runtime datasource* connects as this restricted role day-to-day
   (meaning Flyway, which needs full DDL rights, must use a **separate, more-privileged datasource**
   than the app itself), or whether the grant is a defense-in-depth restriction applied on top of the
   same role the app already uses (weaker, but far simpler — no second datasource/credential to wire
   up). Both are legitimate; auth-service's own precedent offers no signal either way since it never
   faced this question.
2. **Local Postgres topology (AC2).** Whether `services/crypto` gets its own `compose.local.yaml`
   (redundant with, and port-conflicting against, auth's if both are ever run together) or documents
   reuse of auth's existing container (same `localhost:5432`/`checky` database, different schema,
   matching how `application.properties` already works for JPA schema separation). Leaning toward
   reuse + documentation given both services are designed to share one local Postgres instance, but
   not assumed without a Phase 4 decision, since it affects whether a new file gets created here.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
