# payment · T02 · Phase 3 — Design Challenge Findings

Artifact: `.ai/prompts/payment/T02/artifacts/03-design-challenge.md`  
Consumes: `.ai/prompts/payment/T02/artifacts/02-task-implementation-brief.md`  
Authoritative standing rules: `spec/payment-service/agents.md`, `spec/payment-service/design.md` §4c  
Scoped to T02 only. Findings only — no redesign, no implementation.

---

### 1. V2 description omits the `LOGIN` attribute and `GRANT CONNECT ON DATABASE`

- **Issue:** The brief says V2 contains "idempotent role creation, schema/sequence `USAGE`, and INSERT+SELECT-only" grants, but never states that `payment_app` must be created with `LOGIN` or that it needs `CONNECT` on the database. Without both, the application role cannot authenticate over JDBC, even with table grants.
- **Severity:** High — the role would be unusable at runtime as written.
- **Evidence:**
  - `02-task-implementation-brief.md:18-19` (V2 scope)
  - `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql:21-23`
  - `services/notification/src/main/resources/db/migration/V2__notification_app_role_and_grants.sql:27-29`
- **Recommended brief amendment:** Add explicit requirements to V2/AC: `CREATE ROLE payment_app LOGIN` inside the `IF NOT EXISTS` guard, and `GRANT CONNECT ON DATABASE current_database() TO payment_app` (dynamic, not hardcoded to `checky`), matching the sibling migration shape.

---

### 2. Table count mismatch: design.md §4c creates 9 tables, the brief says 8

- **Issue:** AC1 lists 8 tables (`invoices`, `payments`, `verification_records`, `receipts`, `ledger_entries`, `ledger_anchors`, `processed_events`, `outbox`) and says V1 matches `design.md` §4c verbatim. `design.md` §4c also includes a `shedlock` table. Because V1 must be verbatim, the migration will create 9 tables; a test or implementer expecting 8 will fail or be confused.
- **Severity:** Medium — functional, but directly contradicts the brief's own enumeration.
- **Evidence:**
  - `02-task-implementation-brief.md:9`, `:86-88` ("all 8 tables")
  - `spec/payment-service/design.md` §4c `V1__payments_baseline.sql` (includes `shedlock`)
- **Recommended brief amendment:** Update AC1 and the Purpose/Scope wording to "9 tables" and add `shedlock` to the list, noting it is created now but used later when ShedLock is added (task 8).

---

### 3. No verification that baseline tables are owned by the migration role

- **Issue:** AC3 proves UPDATE/DELETE are denied to `payment_app`, but that denial is only meaningful if the tables are owned by the admin/migration role. In PostgreSQL, table owners bypass GRANT/REVOKE. The brief mentions this owner/grantee split in Constraints but does not require any AC or test to prove it.
- **Severity:** Medium — a real bug (e.g., V2 accidentally creating tables as `payment_app`) would silently nullify AC3.
- **Evidence:**
  - `02-task-implementation-brief.md:111-113` (Constraints — owner/grantee split)
  - `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` includes `baselineTablesAreOwnedByTheMigrationRoleNeverByCryptoApp`
- **Recommended brief amendment:** Add an AC or Required Tests assertion that every baseline table in `payments` is owned by the migration/admin role (e.g., `checky`) and never by `payment_app`.

---

### 4. README password placeholder is a credential string not explicitly carved out

- **Issue:** AC6 requires `README.md` to document the local-dev `ALTER ROLE payment_app PASSWORD ...` step. The natural implementation will hard-code a placeholder password like `payment-app-local-only` in the README. `agents.md` and the service's L13 discipline forbid committed credentials; T01's freeze explicitly carved out the Flyway plugin password in the POM, but T02 does not do the same for the README.
- **Severity:** Low — it follows the sibling-service README pattern, but the brief should be explicit to avoid gitleaks/secret-scan surprises.
- **Evidence:**
  - `02-task-implementation-brief.md:21`, `:97-99`
  - `services/crypto/README.md:26` and `services/notification/README.md:44` (both contain the local-only password placeholder)
  - `spec/payment-service/agents.md` Security rules
- **Recommended brief amendment:** Add to L13/AC6 an explicit carve-out for the local-dev-only `ALTER ROLE` password placeholder in `README.md`, mirroring the T01 POM carve-out, or require a non-committed placeholder such as `<set-local-password>`.

---

### 5. Required Tests do not specify how `payment_app` connects in Testcontainers

- **Issue:** V2 creates the role with no password (correctly). The integration test must connect as `payment_app` via TCP/JDBC to prove privilege boundaries. The brief does not say the test should set a test-only password in `@BeforeAll` or that it must use real JDBC (not `docker exec`, which bypasses password auth via loopback trust).
- **Severity:** Low/Medium — an implementer might write a test that does not actually exercise authentication.
- **Evidence:**
  - `02-task-implementation-brief.md:103-107` (Required Tests)
  - `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java:60-72` (sets test password and uses `DriverManager` as `crypto_app`)
- **Recommended brief amendment:** Add to Required Tests that the test provisions a test-only password via `ALTER ROLE payment_app PASSWORD '...'` after migration and connects over JDBC as `payment_app` (wrong password must fail, correct password must succeed).

---

### 6. Integration test migration runner is not specified

- **Issue:** The brief says the integration test uses Testcontainers Postgres but does not say how V1/V2 are applied. Running via Spring Boot's auto-migration (`spring.flyway.enabled`) would depend on `application.properties`, which does not exist until task 3, and would not exercise the same path as the local-dev `mvn flyway:migrate` step. Sibling baseline tests explicitly use the Flyway Java API.
- **Severity:** Low — the test could be implemented correctly by analogy, but ambiguity invites drift.
- **Evidence:**
  - `02-task-implementation-brief.md:103-107`
  - `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java:62-68` (uses `Flyway.configure(...).load().migrate()`)
- **Recommended brief amendment:** State in Required Tests that the test applies V1/V2 through the Flyway Java API against the Testcontainers database, not via Spring Boot auto-migration.

---

### 7. No DDL-denial test for `payment_app`

- **Issue:** AC3/AC5 cover DML grants, but there is no check that `payment_app` cannot create or drop tables in the `payments` schema. If `payment_app` were accidentally granted `CREATE` on the schema, the table-level grant restrictions would be undermined.
- **Severity:** Low — defense in depth.
- **Evidence:**
  - `02-task-implementation-brief.md:91-98` (AC3/AC5)
  - `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java:191-204` (`cryptoAppCannotPerformDdlInTheChainSchema`)
- **Recommended brief amendment:** Add a Required Tests assertion that `payment_app` cannot `CREATE TABLE payments.evil_test(...)` (permission denied) and cannot `DROP TABLE payments.ledger_entries` (must be owner).
