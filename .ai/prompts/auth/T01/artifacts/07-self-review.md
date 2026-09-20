# auth · T01 · Phase 7 — Self Review

Consumes `artifacts/06-implementation-notes.md`. Reviews the actual diff — two files only:
`services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql` (new) and
`services/auth/pom.xml` (modified, `flyway-maven-plugin` block added) — against the frozen brief and
`agents.md`. No rewrite performed; findings only.

Dimensions not applicable to this diff (no Java code, no runtime logic): thread-safety, module
boundaries, money types, enumeration-safety, null-safety at the object level. Noted, not scored.

## Findings

### 1. `flyway-maven-plugin` has no pinned `<version>`
**Issue.** The new plugin block in `services/auth/pom.xml` declares no `<version>`. Maven resolved
`11.7.2` this run, which happens to match the `flyway-core`/`flyway-database-postgresql` version
already in effect (confirmed via `mvn -pl services/auth dependency:tree` → both `11.7.2`) — but that
match is coincidental, not guaranteed. A future build could resolve a newer, unpinned plugin release
independently of when the project's own Flyway dependency version is bumped, risking a
plugin/runtime version skew (different migration-resolution behavior, checksum validation
differences) between `mvn flyway:migrate` and the application's own Boot-autoconfigured migration.
**Severity.** Medium.
**Evidence.** `services/auth/pom.xml`, new `<plugin>` block (`org.flywaydb:flyway-maven-plugin`, no
`<version>` element).
**Recommendation.** Pin `<version>11.7.2</version>` (or bind it to the same property/BOM-managed
version driving `flyway-core`) so plugin and runtime Flyway stay in lockstep and the build is
reproducible.

### 2. Non-`CONCURRENTLY` index build takes a write lock on `lockout_state` during migration
**Issue.** `CREATE INDEX IF NOT EXISTS idx_lockout_state_locked_until` (no `CONCURRENTLY`) runs
inside Flyway's transactional migration and takes a lock that blocks writes to `lockout_state` for
the duration of the index build. On an empty/small local/dev table this is instantaneous and
harmless (confirmed: migration completed in 0.099s). In a production rollout, if `lockout_state`
already holds many rows by the time V5 ships, this becomes a brief write-blocking window during
deploy — and `package.md` §10's rollout notes don't mention it.
**Severity.** Low (today) / worth a rollout note (future).
**Evidence.** `V5__lockout_cleanup_and_shedlock.sql` lines 4–6; `package.md` §10 "Code rollout" is
silent on index-build locking for V5 specifically.
**Recommendation.** Not actionable in T01 — the SQL is a LOCKED verbatim artifact (`design.md` §4c,
"copy exactly, do not paraphrase"; L1 forbids altering it). Flagging as a forward note for whoever
owns the production deploy runbook, not a defect to fix here.

### 3. New plugin's DB credentials are hardcoded with no override mechanism
**Issue.** The `flyway-maven-plugin` configuration hardcodes `checky` / `checky-local-only` /
`localhost:5432` directly, with no `${ENV_VAR:default}`-style override — unlike
`application.properties`, which parameterizes the same local values (`${DB_PASSWORD:checky-local-only}`).
The value itself is the same well-known local-dev placeholder already committed in
`compose.local.yaml` and `application.properties` (not a new secret), so this is not an L13/D-010
violation, but it is inconsistent with this repo's established "everything overridable, hardcoded
value only as local default" convention.
**Severity.** Low.
**Evidence.** `services/auth/pom.xml` new plugin `<configuration>` block vs.
`application.properties` lines 8–10 (`${DB_URL:...}`, `${DB_USERNAND:...}` pattern — actual keys
`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`).
**Recommendation.** Acceptable as-is since this plugin is explicitly a local-developer convenience
(matches the task statement's "against the local Docker Compose Postgres" framing) and is not bound
to any lifecycle phase (confirmed: no `<executions>` block, so `mvn verify`/`mvn install` cannot
trigger it implicitly). If this plugin is ever expected to run outside local dev, its
configuration would need externalizing first.

## Checked, no issue found

- **Idempotency.** Both new DDL statements use `IF NOT EXISTS`; re-running the migration file is
  safe, and Flyway's checksum validation prevents the file itself from being silently altered
  post-apply.
- **Unintended execution.** The new plugin has no `<executions>` binding to any lifecycle phase —
  confirmed it only runs when explicitly invoked (`mvn flyway:migrate`), so it cannot fire as a
  side effect of `mvn verify`/`mvn install`/CI's normal build.
- **Scope discipline.** No file outside the two listed above was touched; `V1`–`V4` confirmed
  unchanged (Phase 6 notes).
- **LOCKED decision L1.** Migration is additive-only, delivered as a new `V5` file. Compliant.

## Open Questions

No blockers. Finding 1 (plugin version pin) is a straightforward fix candidate for Phase 9
(Review Resolution) once Phase 8's independent review is available. Findings 2 and 3 are
informational/forward notes, not defects requiring a code change in this task.
