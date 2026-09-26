<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T02 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T02 — Schema V1 |
| **Spec section** | Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 Task Implementation Brief. Findings only.

---

## Finding 1 · `citext` extension dependency is unresolved for the Testcontainers test

**Severity:** High

**Evidence:** `V1__notifications_baseline.sql` (design.md §4c) declares `contact_projection.email CITEXT`, which requires the `citext` PostgreSQL extension. The TIB correctly identifies that the shared local Docker Compose Postgres needs auth's `V1__auth_baseline_schema.sql` to run first (it executes `CREATE EXTENSION IF NOT EXISTS citext`). However, the Testcontainers-backed integration test runs against a fresh Postgres 16 container where `citext` is **not** enabled by default. Crypto's `ChainBaselineMigrationIntegrationTest` (the cited precedent) has no such dependency because the chain schema does not use `citext`.

**Recommended brief amendment:** Explicitly state how `NotificationBaselineMigrationIntegrationTest` must establish the extension. Two options:
- (a) Run `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql` (or at least its `CREATE EXTENSION IF NOT EXISTS citext` line) inside the Testcontainers instance before running notification migrations; or
- (b) Execute `CREATE EXTENSION IF NOT EXISTS citext` as a setup step in the integration test.

Without this, the integration test will fail with a "type 'citext' does not exist" error regardless of how correct the VERBATIM V1 file is.

---

## Finding 2 · Runtime Flyway / `application.properties` status is ambiguous

**Severity:** High

**Evidence:** The TIB instructs the integration test to mirror crypto's `ChainBaselineMigrationIntegrationTest`, which includes a `runtimeFlywayIsDisabledInApplicationProperties()` test asserting `spring.flyway.enabled=false`. Notification-service has no `application.properties` at all yet, and the TIB's "Files to Create" does not list one. Spring Boot's default when Flyway is on the classpath is `spring.flyway.enabled=true`; without an `application.properties` (or a profile-specific file) disabling it, the running application will attempt migrations at startup. This either contradicts the precedent or leaves the integration test unable to include the same runtime-Flyway guard.

**Recommended brief amendment:** Decide and state explicitly:
- Create `services/notification/src/main/resources/application.properties` in T02 with at least `spring.application.name=notification-service`, `spring.profiles.active=local`, and `spring.flyway.enabled=false`; OR
- Remove the `runtimeFlywayIsDisabledInApplicationProperties` assertion from the integration-test scope and defer `application.properties` to task 3, with a note that the application must not be started in T02.

The first option is consistent with the crypto precedent and the test's own implied shape.

---

## Finding 3 · IN_APP template names are not specified

**Severity:** Medium

**Evidence:** The TIB requires 14 seeded template rows: 7 launch mappings × 2 channels (EMAIL + IN_APP). Design.md §4c's topic-mapping table names the 7 templates as `email.verify`, `email.password_reset`, `user.welcome`, `invoice.created`, `payment.seen`, `payment.finalized`, and `receipt.issued`. These names clearly imply the EMAIL channel; the IN_APP channel equivalents are not named anywhere. The `templates` table's unique constraint is `(name, channel, version)`, so the IN_APP rows must have explicit `name` values. A future renderer/task 9 may look up templates by event type + channel, and inconsistent naming now will force a re-seed or code workaround later.

**Recommended brief amendment:** Add a small naming table to the TIB (or reference one), e.g.:

| Event mapping | EMAIL name | IN_APP name |
|---|---|---|
| verify_email | email.verify | user.verify |
| password_reset | email.password_reset | user.password_reset |
| user.registered | user.welcome | user.welcome |
| invoice.created | invoice.created | invoice.created |
| payment.seen | payment.seen | payment.seen |
| payment.finalized | payment.finalized | payment.finalized |
| receipt.issued | receipt.issued | receipt.issued |

Alternatively, state that the same `name` is reused with a different `channel` value. Either choice is fine, but silence is not.

---

## Finding 4 · V3 seed content assertions are under-specified in the integration test

**Severity:** Medium

**Evidence:** AC3 requires "All 14 template rows exist with the content specified above, `version = 1`." The TIB's integration-test scope lists "all 8 tables exist and no others", "V1 matches design.md", "every migration recorded successful", and role-level access tests, but it does **not** explicitly list a test that queries `notifications.templates` and asserts the row count is 14, that `version = 1` for all rows, and that the 7 distinct template names (per channel pair) are present. Without this, a migration that creates the table but fails to insert rows, or inserts the wrong number, would not fail the test.

**Recommended brief amendment:** Add an explicit integration-test method, e.g. `launchTemplatesAreSeededWithVersionOne()`, that queries `notifications.templates` and asserts:
- `COUNT(*) = 14`
- `COUNT(DISTINCT name) = 7`
- every row has `version = 1`
- the expected set of `(name, channel)` pairs is present.

Keep assertions on `body`/`subject` minimal (or absent) if the content is intentionally left to the implementer, but the count/name/channel/version invariants must be enforced.

---

## Finding 5 · `notification_app` sequence grant is not acknowledged as a privilege

**Severity:** Low

**Evidence:** The TIB says V2 "grants INSERT, SELECT on `delivery_log` only — no other table" and that the integration test must prove "`notification_app` has no access to any other table in the schema." However, the structural precedent (crypto's V2) also grants `USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain TO crypto_app`. This sequence grant is necessary for `INSERT` on identity columns, but it is a real PostgreSQL privilege on schema objects. The TIB's "no access to any other table" wording is technically true (sequences are not tables), yet the integration test must be written to avoid asserting that `notification_app` has *no* privileges at all on non-`delivery_log` objects.

**Recommended brief amendment:** Clarify in the TIB that V2 grants `USAGE` on the `notifications` schema and `USAGE, SELECT` on all sequences in the schema, in addition to the narrow `INSERT, SELECT` on `delivery_log`. Add a note that the integration test should deny `SELECT/INSERT/UPDATE/DELETE` on every table except `delivery_log`, but should **not** assert that all privileges on sequences are denied.

---

## Finding 6 · Local-dev migration ordering commands are not explicit

**Severity:** Low

**Evidence:** The TIB states AC4 requires `mvn -pl services/notification flyway:migrate` to succeed against the shared local Postgres, and that auth's migrations must run first for `citext`. It does not state the exact commands to start the compose stack or run auth's migrations. A developer unfamiliar with the repo might try to run notification migrations against a fresh Postgres without knowing about `services/auth/compose.local.yaml` or the auth migration prerequisite.

**Recommended brief amendment:** Add the exact local-dev sequence to the TIB:

```bash
docker compose -f services/auth/compose.local.yaml up -d
mvn -pl services/auth flyway:migrate
mvn -pl services/notification flyway:migrate
```

This makes the environment-ordering dependency actionable, not just theoretical.

---

## Finding 7 · `shedlock` table exists without a corresponding T01 dependency

**Severity:** Low

**Evidence:** The VERBATIM V1 DDL includes a `shedlock` table, but the notification-service T01 pom deliberately excludes `shedlock-spring` and `shedlock-provider-jdbc-template` (they are scheduled for task 14). This is intentional scope discipline, but it is an unstated difference from crypto-service, where the `shedlock` table and the ShedLock dependencies both exist from early tasks. The table will be unused until task 14, which is fine, but the TIB should explicitly note this to avoid a reviewer treating it as an inconsistency.

**Recommended brief amendment:** Add a one-line note in the "In" scope or Open Questions: "The `shedlock` table is created in V1 because design.md §4c requires it; the ShedLock dependencies and `@EnableSchedulerLock` are intentionally deferred to task 14, mirroring T01's annotation discipline."

---

## Summary

The T02 brief is sound in its VERBATIM discipline, least-privilege role design, and separation of V1/V2/V3. The two high-severity gaps are the unresolved `citext` extension setup in the integration test and the unresolved `application.properties` / runtime-Flyway state. The medium findings (IN_APP template names and V3 seed assertions) affect implementer consistency and AC3 verification. The low findings are clarifications that reduce ambiguity for reviewers and future tasks.
