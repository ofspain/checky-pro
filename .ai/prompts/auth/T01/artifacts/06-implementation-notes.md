# auth · T01 · Phase 6 — Implementation Notes

Consumes `artifacts/04-frozen-task-brief.md` (STATUS: FROZEN) and `artifacts/05-implementation-plan.md`.
Implementation of T01 only — no other task's scope touched.

## What changed

1. **Created** `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql`
   — content copied verbatim from `design.md` §4c (partial index
   `idx_lockout_state_locked_until` on `lockout_state(locked_until)`, and the `shedlock` table),
   including `IF NOT EXISTS` on both statements per the frozen brief's Finding-5 disposition (kept,
   not "fixed" to match V1–V4 style, because the SQL is a LOCKED verbatim artifact).
2. **Modified** `services/auth/pom.xml` — added the `flyway-maven-plugin` block to `<build>/<plugins>`
   with datasource binding to the local Docker Compose Postgres (`jdbc:postgresql://localhost:5432/checky`,
   user/password `checky`/`checky-local-only`, schema `auth`), per the frozen brief's Finding-1
   (Option A, human-approved) and Finding-4 resolution.

No other file was created or modified. No Java source, entity, repository, service, or test was
touched — matches the frozen brief's Files-to-Modify / Files-NOT-to-Modify sections exactly.

## Mapping to plan and acceptance criteria

Environment note: Maven was not installed on this machine; it was installed via
`brew install maven` (first attempt failed on a transient network error fetching Homebrew's
`openjdk` bottle dependency; retry was cancelled per your request in favor of a manual install,
which you completed) before verification could run.

- **AC1 (shedlock table).** Verified directly against the migrated database:
  `SELECT table_name FROM information_schema.tables WHERE table_schema = 'auth' AND table_name = 'shedlock';`
  → returns `shedlock`. **Pass.**
- **AC2 (lockout index).** Verified:
  `SELECT indexname FROM pg_indexes WHERE schemaname = 'auth' AND indexname = 'idx_lockout_state_locked_until';`
  → returns `idx_lockout_state_locked_until`. **Pass.**
- **AC3 (L1 — only V5 is new).** `git diff`/`git status` confirm `V1`–`V4` are byte-for-byte
  unchanged; the only new migration file is `V5`. **Pass.**
- **AC4 (migration runs cleanly via the literal Maven goal).** Ran, in order:
  1. `docker compose -f services/auth/compose.local.yaml up -d --wait` — Postgres and Kafka both
     came up healthy.
  2. `mvn -pl services/auth flyway:migrate` — **BUILD SUCCESS**. Output:
     `Migrating schema "auth" to version "5 - lockout cleanup and shedlock"` /
     `Successfully applied 5 migrations to schema "auth", now at version v5`.
  3. `SELECT version, description, success FROM auth.flyway_schema_history WHERE version = '5';`
     → `5 | lockout cleanup and shedlock | t`. **Pass** — matches the frozen brief's exact
     verification steps (Finding 2 / Finding 8).

## Deviation forced by reality — flagged, not hidden

`mvn -pl services/auth verify` (the frozen brief's regression check) currently returns
**BUILD FAILURE**, not from anything T01 touched:

```
[ERROR] .../token/ReuseDetectingAuthorizationService.java:[10,48] cannot find symbol
  symbol:   class OAuth2TokenType
  location: package org.springframework.security.oauth2.core
[ERROR] .../token/SecurityChainsConfig.java:[12,47] cannot find symbol
  symbol:   class JwtAuthenticationConverter
  location: package org.springframework.security.oauth2.jwt
```

**This is confirmed pre-existing and unrelated to T01.** I reproduced it with T01's only source
change (`services/auth/pom.xml`) stashed out via `git stash push -- services/auth/pom.xml`, leaving
the tree exactly as it was before this task started (the new `V5` migration file and `.ai/`
artifacts are untracked and don't affect Java compilation). The identical eight compiler errors
occurred. T01 adds no dependency and no Java source — it cannot be the cause.

Per the guardrails ("no unrelated refactoring... no scope beyond this task"), **this is not fixed
here.** It is a pre-existing dependency/version-resolution defect in the `token` module (missing
`OAuth2TokenType` / `JwtAuthenticationConverter` symbols suggests a Spring Security OAuth2
version mismatch, likely from the parent POM's dependency management), affecting the whole module's
compilation independent of any task in this package. It should be raised as its own defect,
triaged, and fixed outside T01's scope — not silently absorbed into this task's diff.

**Consequence for T01's own acceptance:** AC1–AC4 (the actual scope of this task) all pass,
verified independently of the module-wide build. The "existing suite stays green" bar from
`tasks.md` line 3 ("leave the module buildable and the test suite green") cannot be confirmed for
the module as a whole right now — but that is because the module was **already not buildable
before T01 started**, not because of anything this task did.
