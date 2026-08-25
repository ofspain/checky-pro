<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# crypto · T02 · Phase 8 — Independent Code Review

Reviewed the implementation (Phase 6 notes + actual touched files) and the self-review (Phase 7)
against the frozen brief AC1–AC4, `spec/crypto-service/agents.md`, `spec/crypto-service/design.md` §4c,
and `services/auth`'s mirror files.

Local Maven verification was not possible in this environment (`mvn` is not installed), so migration
and build claims are evaluated from the committed files and the Phase 6/7 evidence recorded by the
implementer.

---

## Findings

### 1. V2 migration commits a plaintext local DB password in source control

- **Issue:** `V2__crypto_app_role_and_grants.sql` creates the role with
  `PASSWORD 'crypto-app-local-only'` inside the migration. This is a committed DB credential in a
  different location than auth's `checky-local-only` placeholder, which lives only in the Maven
  plugin configuration.
- **Evidence:** `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql:13`.
  `spec/crypto-service/agents.md` L13 states "DB creds ... none committed; gitleaks gate in CI."
  The frozen brief's constraint calls this "the same class of well-known local-only placeholder auth's
  own Flyway plugin already uses," but the password is in a migration file, not a plugin config.
- **Recommendation:** Move role creation (with password) out of V2 and into
  `services/auth/compose.local.yaml` or a local setup script; keep V2 focused on grants. If the
  Phase 4 human gate wants to keep the password in V2, add an explicit security note and a gitleaks
  allowlist/exception for this local-only placeholder.
- **Confidence:** Medium.

### 2. Hardcoded database name `checky` makes V2 non-portable

- **Issue:** `GRANT CONNECT ON DATABASE checky` names the local database explicitly. In any
  environment where the database name is not `checky`, V2 will fail.
- **Evidence:** `V2__crypto_app_role_and_grants.sql:18`. The frozen brief treats V2 as the production
  migration path (it lives in `src/main/resources/db/migration`), not a local-only script.
- **Recommendation:** Either (a) document that role creation and `CONNECT` grants are local-dev-only
  and handled by infra in higher environments, removing them from V2, or (b) move the role/password
  creation into the compose/infra layer so the migration never names a specific database. If V2 must
  stay environment-agnostic, investigate Flyway placeholders for `current_database()`.
- **Confidence:** Medium.

### 3. No proof that `spring.flyway.enabled=false` disables runtime Flyway

- **Issue:** AC4 requires that the running app never attempts to migrate as the restricted
  `crypto_app` role. The self-review verified the property is present, but did not start the app to
  confirm Flyway auto-configuration is actually skipped.
- **Evidence:** `services/crypto/src/main/resources/application.properties:22`. Phase 6 notes
  explicitly state "no main-class smoke test was in scope" and that no runtime proof was judged
  necessary beyond a future `mvn ... verify`. `mvn verify` does not exercise the runtime
  `FlywayAutoConfiguration`.
- **Recommendation:** Add a one-time AC4 verification step: start the Spring context (e.g., a minimal
  `@SpringBootTest` or `mvn spring-boot:run`) with a breakpoint/log assertion that no Flyway
  `schema_version` query is issued by the runtime datasource. Record the result.
- **Confidence:** Medium.

### 4. Sibling auth-build check was not re-run

- **Issue:** The frozen brief's AC5-equivalent requires the `services/auth` build to remain
  unaffected. Phase 6/7 only checked `git status --porcelain services/auth` is empty.
- **Evidence:** `06-implementation-notes.md:73-74`, `07-self-review.md:53-54`. No Maven invocation for
  `services/auth` is recorded for T02.
- **Recommendation:** Run `mvn -pl services/auth validate` or `verify` and record the result, mirroring
  T01's AC5 evidence. The mechanism argument is strong, but the audit trail is incomplete.
- **Confidence:** Low.

### 5. V2 only grants privileges; it does not narrow pre-existing privileges

- **Issue:** If a `crypto_app` role already exists with broader privileges (from a previous iteration,
  a different branch, or environment provisioning), V2's `GRANT` statements add permissions but never
  revoke them.
- **Evidence:** `V2__crypto_app_role_and_grants.sql` contains only `GRANT` and
  `CREATE ROLE ... IF NOT EXISTS`. There is no `REVOKE` or `ALTER ROLE ... RESET`.
- **Recommendation:** Add a note to the brief/artifact that V2 assumes a fresh `crypto_app` role. In
  higher environments, ensure the role is created or reset to a known baseline before V2 runs.
  Optionally add a guarded `REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA chain FROM crypto_app`
  before the targeted grants if idempotency/safety is desired.
- **Confidence:** Low.

### 6. `outbox` table shape remains unreconciled with the shared outbox library

- **Issue:** V1's `outbox` has a `BIGINT` id, no `headers`/`schema_version`, and a unique idempotency
  key — different from auth's outbox and from the still-empty `libs/java/outbox`. The frozen brief
  deferred reconciliation to T04+.
- **Evidence:** `V1__chain_baseline.sql:112-124` vs.
  `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql:199-212`;
  `libs/java/outbox/.gitkeep`.
- **Recommendation:** Make sure the T04 task brief explicitly includes reconciling this table with
  the shared outbox relay's expected schema before any publish path is wired; otherwise the first
  integration test that exercises the outbox will fail with a schema mismatch.
- **Confidence:** Medium.

### 7. `outbox` lacks an index on `aggregate_id`

- **Issue:** The outbox relay will likely query/filter by `aggregate_id` (the Kafka partition key),
  but V1 only indexes `created_at` for the unpublished sweep.
- **Evidence:** `V1__chain_baseline.sql:118` defines `aggregate_id VARCHAR(128) NOT NULL`; the only
  index is `idx_outbox_unpublished` on `created_at` where `published_at IS NULL`.
- **Recommendation:** Verify the relay's query pattern. If it filters by `aggregate_id`, add an index
  — either via a new V3 migration now or as part of the T04 outbox task. Since V1 is verbatim, do not
  change it without a human gate.
- **Confidence:** Low.

### 8. `s3_snapshot_key VARCHAR(256)` may be too short

- **Issue:** AWS S3 object keys allow up to 1024 bytes; 256 may be exceeded by a key prefix that
  includes chain, transaction hash, timestamp, and fact type.
- **Evidence:** `V1__chain_baseline.sql:31`. `spec/crypto-service/design.md` §4c is verbatim, so this
  is not a silent change candidate.
- **Recommendation:** Log as an Open Question for the author/T08+ (Observation log / S3 snapshot
  task). If longer keys are needed, change via explicit human gate because V1 is frozen verbatim.
- **Confidence:** Low.

---

## No other material issues

`V1` matches the verbatim spec; the owner/grantee split, runtime Flyway disable, schema/sequence
grants, and local profile setup otherwise satisfy the frozen brief and `agents.md`.
