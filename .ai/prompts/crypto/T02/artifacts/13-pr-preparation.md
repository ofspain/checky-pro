<!-- MODEL: Claude Sonnet — Phase 13 (PR Preparation). -->

# crypto · T02 · Phase 13 — PR Preparation

## Title

`crypto-service T02: chain schema baseline + least-privilege runtime role`

## Summary

- Adds `V1__chain_baseline.sql`, a byte-for-byte transcription of `design.md` §4c's verbatim
  10-table `chain` schema (`watches`, `observations`, `quorum_decisions`, `provider_health`,
  `chain_cursors`, `token_allowlist`, `screening_results`, `attestations`, `outbox`, `shedlock`) —
  now enforced by an automated test, not just a one-time manual diff.
- Adds `V2__crypto_app_role_and_grants.sql`: a new, unprivileged `crypto_app` role — owns nothing,
  connects only as a grantee — with `USAGE` on the `chain` schema and its sequences, and
  INSERT+SELECT-only on the three tables the task named (`observations`, `attestations`,
  `quorum_decisions`). No password is set by the migration itself; real environments provision it
  out-of-band (External Secrets Operator), matching every other credential this service uses. Local
  dev sets it via one documented step in `services/crypto/README.md`.
- Wires `services/crypto/pom.xml`'s Flyway Maven plugin (admin/`checky` credentials, mirroring
  auth's own binding exactly except `<schemas>chain</schemas>`) and `application.properties`'
  runtime datasource (as `crypto_app`, with `spring.flyway.enabled=false` — the app itself never has
  DDL rights, in any environment; migrations only ever run via the Maven plugin).
- Adds `ChainBaselineMigrationIntegrationTest`: 10 real Testcontainers-Postgres tests proving, not
  just documenting, that the schema, the grants, the password requirement, the role/table ownership
  split, and the runtime-Flyway-disabled property all behave as designed.

## Files changed

**Created**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql`
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql`
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java`

**Modified**
- `services/crypto/pom.xml` (Flyway Maven plugin)
- `services/crypto/src/main/resources/application.properties` (runtime datasource, profile, Flyway disable)
- `services/crypto/README.md` (local dev database setup section)

**Process artifacts**
- `.ai/prompts/crypto/T02/artifacts/00-13-*.md` — full 14-phase pipeline record.

## Test plan

- [x] `mvn -pl services/crypto flyway:migrate` — run for real against `services/auth`'s shared local
      Postgres, from a clean schema+role state, twice (proving `V2`'s idempotency guard).
- [x] `ChainBaselineMigrationIntegrationTest` — 10/10 pass; the two most novel checks (byte-diff
      against the spec, table-ownership split) individually mutation-tested and confirmed to fail on
      a real regression.
- [x] `T01SkeletonRegressionTest` — still 6/6 pass alongside the new class; T02's pom changes didn't
      regress T01's own guards.
- [x] `mvn -pl services/crypto verify` — `BUILD SUCCESS`, executable jar builds.
- [x] Real host→container app boot (`mvn spring-boot:run`): correct password boots clean with zero
      Flyway log activity; a deliberately wrong password fails fast with a genuine Postgres auth
      error — both directions proven, not assumed.
- [x] `mvn -pl services/auth validate` — `BUILD SUCCESS`; `git status --porcelain services/auth` —
      empty throughout the task.

## Known, deliberate gaps (not this task's scope)

- No entity/repository Java code for any of the 10 tables — added by whichever task first needs
  each one.
- `token_allowlist` is not seeded — `package.md` §10 names this as a companion migration/config task,
  not T02's.
- `outbox`'s shape (bigint id, no `headers`/`schema_version`) is unreconciled with auth's own outbox
  or the still-empty `libs/java/outbox` — flagged for T04+ (Phase 8/9 Kimi findings 6-7), not
  resolved here since `V1` is a verbatim artifact.
- `observations.s3_snapshot_key VARCHAR(256)` may be short for real S3 keys (up to 1024 bytes) —
  flagged as an open question for T08+ (observation log task); not changed here, same verbatim
  constraint.
- No `REVOKE`/reset logic in `V2` for a hypothetical pre-existing `crypto_app` role with broader
  privileges from a prior iteration — accepted as a low-probability local-dev-only edge case.

## Reviewer notes

- Kimi's Phase 8 review (design/implementation) had all 8 findings verified accurate — 2 led to a
  real fix (V2's password and hardcoded database name both removed, replaced with an out-of-band /
  documented-local-step pattern and dynamic SQL respectively).
- Kimi's Phase 11 review (tests) had 8 of 9 findings verified accurate and actioned (4 → 10 tests).
  Its headline "blocker" finding — that the suite couldn't pass because `V2` hardcodes a database
  name — was checked directly against the file and its full git history and found to be false; that
  hardcoding was fixed in Phase 9 and has never existed in any commit of this file. Worth knowing if
  comparing Kimi's raw findings against this PR: its review environment appears to have read stale
  content at least once this task.
- A genuine gap in *this pipeline's own verification technique* (not the code) surfaced at Phase 9:
  `docker exec ... psql` connections bypass Postgres password auth entirely via the default image's
  loopback `trust` rule. All grant/ownership assertions are still valid (privilege checks are
  auth-method-independent), but any future manual verification step involving `crypto_app`'s
  password specifically should go through a real host→container TCP path, not `docker exec`.

---

**Phase 13 complete — PR description drafted, all phases 0-13 closed for crypto-service T02.**
