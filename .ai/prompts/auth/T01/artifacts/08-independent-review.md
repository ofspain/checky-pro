# auth · T01 · Phase 8 — Independent Code Review

## Implementation under review
- `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql` (new)
- `services/auth/pom.xml` (modified — `flyway-maven-plugin` block added)

## Review environment
**Docker is NOT reachable.** `docker --version` returns `29.6.1`, but `docker info` fails with `Error response from daemon: Docker Desktop is unable to start`. Therefore dynamic verification (`docker compose up`, `mvn flyway:migrate`, integration tests) could not be rerun. This report is based on static analysis, Maven effective-POM inspection, and git history.

Static checks executed:
- `mvn -pl services/auth validate` — BUILD SUCCESS.
- `mvn -pl services/auth help:effective-pom -Dverbose=true` — inspected plugin/version provenance.
- `mvn -pl services/auth dependency:tree "-Dincludes=org.flywaydb:*"` — confirmed Flyway runtime dependencies.
- `mvn -pl services/auth compile -DskipTests` — BUILD FAILURE (8 pre-existing errors in `token` module).
- `git show`/`git status` — confirmed only the two files above changed for T01.

---

## Findings

### 1. Plugin version is managed by Spring Boot BOM — self-review skew concern is overstated
**Issue.** The Phase 7 self-review flagged the missing explicit `<version>` on `flyway-maven-plugin` as a Medium risk of plugin/runtime version skew. Static inspection shows the version is not actually "coincidental": it is inherited from the Spring Boot 3.5.4 dependency-management BOM.
**Evidence.** `mvn -pl services/auth help:effective-pom -Dverbose=true` shows `<version>11.7.2</version>` is sourced from `org.springframework.boot:spring-boot-dependencies:3.5.4`. `mvn -pl services/auth dependency:tree` shows `flyway-core:11.7.2` and `flyway-database-postgresql:11.7.2`. The plugin and runtime versions are aligned.
**Recommendation.** No code change required. The current POM follows Spring Boot convention (omit versions for BOM-managed plugins). Close the Phase 7 finding as informational rather than Medium. Optionally add a one-line XML comment if the omitted version worries future maintainers.
**Confidence.** High.

### 2. `mvn -pl services/auth verify` cannot be confirmed green because of pre-existing compile errors
**Issue.** The frozen brief's acceptance criteria include the regression bar that the existing suite stays green (`mvn -pl services/auth verify`). This cannot be verified on any machine until pre-existing compilation failures in the `token` module are fixed. The failures are unrelated to T01 (T01 adds no Java code and no dependencies).
**Evidence.** `mvn -pl services/auth compile -DskipTests` produces 8 errors in `ReuseDetectingAuthorizationService.java` (`OAuth2TokenType` not found) and `SecurityChainsConfig.java` (`JwtAuthenticationConverter` not found). Git history (`git log -p -- services/auth/pom.xml`) shows the plugin addition was committed separately from any token-module source changes; the token code predates T01.
**Recommendation.** Confirm this is a pre-existing defect outside T01 scope. Do not let it block T01's own AC1–AC4. Track the missing Spring Security OAuth2 classes as their own bug and fix before claiming the module is fully buildable.
**Confidence.** High.

### 3. Dynamic migration verification could not be repeated
**Issue.** Because Docker Desktop cannot start on this review machine, the literal task command `mvn -pl services/auth flyway:migrate` against the local Docker Compose Postgres could not be executed. AC4 was verified by the implementer on a different host (per Phase 6 notes) but cannot be independently reproduced here.
**Evidence.** `docker info` reports `Docker Desktop is unable to start`; no local Postgres container is available.
**Recommendation.** Record this as an environment limitation. Rerun AC4 on a host with working Docker before final sign-off if the project requires independent dynamic verification. Static checks show the migration file and plugin configuration are correct, and no SQL logic defect is visible.
**Confidence.** High.

### 4. Non-`CONCURRENTLY` index build will lock `lockout_state` during production deploy
**Issue.** `CREATE INDEX IF NOT EXISTS idx_lockout_state_locked_until ... WHERE locked_until IS NOT NULL` runs inside the Flyway transaction and acquires a write lock on `lockout_state` for the duration of the index build. In a production rollout where `lockout_state` already has rows, this will briefly block login-failure tracking and admin unlock operations during deploy.
**Evidence.** `V5__lockout_cleanup_and_shedlock.sql` line 4; `package.md` §10 rollout notes do not mention this locking window.
**Recommendation.** The SQL is a LOCKED verbatim artifact from `design.md` §4c, so do not alter it in T01. Add a production-runbook note that V5 should be deployed during a low-traffic window, or that the index should be created out-of-band with `CREATE INDEX CONCURRENTLY` if true zero-downtime is required. If that is unacceptable, escalate to the spec author to amend `design.md` §4c.
**Confidence.** High.

### 5. Plugin credentials are hardcoded local-dev placeholders, safely scoped to explicit invocation
**Issue.** The `flyway-maven-plugin` hardcodes `localhost:5432`, user `checky`, password `checky-local-only`, with no external override mechanism. This matches the existing local defaults in `compose.local.yaml` and `application.properties`, but it is less flexible than the `${DB_PASSWORD:...}` style used elsewhere.
**Evidence.** `services/auth/pom.xml` plugin configuration lines 138–141; `compose.local.yaml` lines 9–11; `application.properties` lines 8–10. The plugin has no `<executions>` binding, so it only runs when explicitly invoked.
**Recommendation.** Acceptable for T01. The plugin is explicitly local-dev only and cannot fire as a side effect of `mvn verify`/CI. If it is ever needed in non-local environments, externalize the configuration. Optionally add an XML comment marking the block as local-dev-only.
**Confidence.** High.

### 6. V5 SQL matches the frozen brief / `design.md` §4c verbatim
**Issue.** No deviation from the required schema artifact was found.
**Evidence.** Git diff of the new migration file shows exactly the partial index on `lockout_state(locked_until) WHERE locked_until IS NOT NULL` and the `shedlock` table with `name` PK, `lock_until`, `locked_at`, `locked_by` as `TIMESTAMPTZ NOT NULL`. The file header comments, `IF NOT EXISTS` clauses, and `TIMESTAMPTZ` types match `design.md` §4c.
**Recommendation.** No change required.
**Confidence.** High.

## Open Questions
None that block T01. The `TIMESTAMPTZ` suitability for the future ShedLock `LockProvider` remains owned by task #30, as already deferred in the frozen brief.
