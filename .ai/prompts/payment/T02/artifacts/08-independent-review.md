# payment · T02 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T02/artifacts/08-independent-review.md`  
Consumed: Phase 6 implementation, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/payment-service/agents.md`  
Scope: T02 only. Findings only — no rewrite.

---

### 1. `SchemaBaselineMigrationIntegrationTest.java` is missing

- **Issue:** The frozen brief explicitly requires `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java` to assert AC1–AC8 against a real Testcontainers Postgres instance. The implementation created `V1`, `V2`, and the README change, but never created the integration test. Manual checks performed in Phase 6 are not a substitute for the permanent, CI-enforced regression guard the brief mandates.
- **Evidence:**
  - `artifacts/04-frozen-task-brief.md:110-114` (Files to Create)
  - `artifacts/04-frozen-task-brief.md:146-157` (Required Tests)
  - `services/payment/src/test/java/com/themistra/payment/` contains only `T01SkeletonRegressionTest.java`; no `SchemaBaselineMigrationIntegrationTest.java` (verified directly)
- **Recommendation:** Create the test class before T02 is considered complete. It must apply `V1`/`V2` via the Flyway Java API, provision a test-only `payment_app` password in `@BeforeAll`, connect as `payment_app` over real JDBC, and assert all of AC1–AC8 (table existence, migration success, INSERT/SELECT success, UPDATE/DELETE denial, identity-column insert, no grant on the other seven tables, no password in `V2`, table ownership by `checky`, DDL denial).
- **Confidence:** High.

---

### 2. Self-review declared "no findings" without checking the frozen brief's full deliverables list

- **Issue:** Phase 7 reviewed only `V1`, `V2`, and `README.md`, then concluded there was nothing to report. It never verified that the required integration test file exists, which is why the missing test in finding 1 was not caught.
- **Evidence:**
  - `artifacts/07-self-review.md:48-54` ("No findings")
  - `artifacts/07-self-review.md:3-5` (lists only the SQL files and README as reviewed)
  - `artifacts/04-frozen-task-brief.md:110-114` (test file is a required output)
- **Recommendation:** In future Phase 7 work, cross-check the filesystem against every item in the frozen brief's Files to Create and Required Tests. Any intentionally deferred test should be recorded as an explicit deviation, not omitted from the review.
- **Confidence:** High.

---

### 3. README `docker exec` command assumes the default Docker Compose project name

- **Issue:** `services/payment/README.md:22` uses `auth-postgres-1` as the container name. That name is correct only when the compose project name defaults to the directory name `auth`. A developer who runs `docker compose -f services/auth/compose.local.yaml up -d` from a different directory or with `-p <other>` will get a differently named container and the `docker exec` command will fail.
- **Evidence:**
  - `services/payment/README.md:22`
  - `services/auth/compose.local.yaml:6` (service name is `postgres`)
- **Recommendation:** Make the README command robust to project-name changes by using `docker compose -f services/auth/compose.local.yaml exec postgres psql -U checky -d checky -c "..."` instead of `docker exec -it auth-postgres-1 ...`. This is a documentation-only, low-risk improvement.
- **Confidence:** Low.

---

### 4. V1 is byte-for-byte identical to `design.md` §4c, and V2 matches the frozen brief

- **Issue:** Not a finding — positive confirmation. `V1__payments_baseline.sql` is a verbatim copy of the SQL fence in `spec/payment-service/design.md` §4c (verified by direct comparison). `V2__payment_app_role_and_grants.sql` correctly creates the role with `LOGIN`, grants dynamic `CONNECT`, schema/sequence usage, and INSERT+SELECT-only on `ledger_entries` and `verification_records`, with no password committed in executable SQL.
- **Evidence:**
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql`
  - `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql:19-40`
  - `spec/payment-service/design.md` §4c
  - `artifacts/04-frozen-task-brief.md:128-145` (AC1–AC6)
- **Recommendation:** None.
- **Confidence:** High.

---

### 5. README password placeholder is covered by the documented L13 carve-out

- **Issue:** Not a finding — positive confirmation. `README.md:23` contains the local-dev-only `ALTER ROLE payment_app PASSWORD 'payment-app-local-only'` instruction. The frozen brief explicitly carves this out as the same cross-service local-dev placeholder pattern used by `crypto-service` and `notification-service`.
- **Evidence:**
  - `services/payment/README.md:23`
  - `artifacts/04-frozen-task-brief.md:78-83` (L13 carve-out)
- **Recommendation:** None.
- **Confidence:** High.
