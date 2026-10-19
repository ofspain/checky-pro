# payment · T28 · Phase 0 — Repository Understanding

**Task 28** (`spec/payment-service/tasks.md:49`): check payment-service against
`SECURITY-THREAT-MODEL.md` threat **#5**, "insider alters a historical verification". The claim to
verify is that the append-only ledger plus S3 WORM defeat it. In particular, confirm that **no
code path can UPDATE/DELETE `ledger_entries` or overwrite a receipt object**.

This phase records only what exists. It contains no design work.

## 1. Architecture summary (the parts this task touches)

**Feature modules.** Package-by-feature under `com.themistra.payment`: `invoice`, `payment`,
`verification`, `receipt` (with sub-packages `receipt.ledger` and `receipt.event`), `consumer`,
`events`, `export`, and `common`. L12 is enforced by `ArchitectureTest`.

**Persistence.**
- **Database.** Postgres schema `payments`, managed by Flyway `V1`–`V10`. `V1` is the verbatim
  baseline DDL, with no grants. The grants come later, added one at a time by `V2`–`V9`.
- **Roles.** Two roles enforce the controls:
  - **`checky`** owns every table. It is the migration role and is never used at runtime.
  - **`payment_app`** is a grantee only. It owns nothing, so the grants restrict it. Owners
    bypass grants.
- **`ledger_entries`** (`V1__payments_baseline.sql:85-92`) has these columns:
  - `seq` (identity PK)
  - `receipt_id` (UNIQUE FK → `receipts`, `ON DELETE RESTRICT`)
  - `prev_hash`, `entry_hash` (UNIQUE, `CHAR(64)`)
  - `payload` (JSONB), `created_at`
- **Grants** (`V2__payment_app_role_and_grants.sql:40`, `V9__payment_app_receipts_grant.sql:4`):

  | Table | `payment_app` privileges |
  |---|---|
  | `ledger_entries` | `INSERT, SELECT` |
  | `verification_records` | `INSERT, SELECT` |
  | `receipts` | `INSERT, SELECT` |

  No other migration grants anything on these three tables. `ledger_anchors` has **no** grant,
  because R26 is deferred.
- **Receipt and ledger write path:**
  - `ReceiptService.java:179`: `receiptRepository.save(Receipt.create(…))`. The entity is always
    new.
  - `LedgerService.java:85`: `repository.save(LedgerEntry.create(…))`. The entity is always new,
    and appends are serialised by `LedgerEntryRepository.acquireAppendLock()`
    (`pg_advisory_xact_lock`).
- **`LedgerEntry` is `@Immutable`** (`LedgerEntry.java`). This is load-bearing: without it,
  Hibernate's JSON dirty-checking issued a spurious `UPDATE` (see the memory note on the Hibernate
  JSON bug). **`Receipt` is not `@Immutable`.** It has no JSON field, and `created_at` is
  `updatable=false`.
- **Repository visibility.** `LedgerEntryRepository` and `ReceiptRepository` are package-private
  `JpaRepository` interfaces. They therefore *inherit* `delete*`, `deleteAll*` and
  `save`-as-merge. A grep of `src/main` finds **no** call to any `delete*` method, no
  `@Modifying` query, and no native query that touches `ledger_entries` or `receipts`.
  - **Every `@Modifying` / `UPDATE` query** sits in `PaymentRepository`, `InvoiceRepository` or
    `ProcessedEventRepository`.
  - **The only native query** (`TransactionExportRepository:61`) is the T22 export read.
- **Verification.** `LedgerVerifier.verify()` (R22) walks the whole chain from head to tail. It
  recomputes each `entry_hash` and checks `prev_hash` linkage. It is **tamper-evident**, not
  tamper-proof. **Nothing in `src/main` calls it.** No scheduler and no endpoint does; only tests
  invoke it (`LedgerVerifierTest`, the T26 E2E). R22 is phrased "WHEN the ledger is verified".
  Who triggers that in production is not defined in code.

**S3 WORM.** `ReceiptStore.store()` (`ReceiptStore.java:92-115`):
- **Keys.** It puts `<prefix><receiptUuid>.json` and `.pdf`, each with
  `ObjectLockMode.COMPLIANCE` and `retainUntil = clock + 7y` (`:160-172`).
- **No other S3 operations.** It never deletes or copies.
  `ReceiptStoreTest.shouldNeverCallDeleteOrAnyOverwriteApi` (`:245`) verifies that. `S3Client` is
  referenced only by `ReceiptStore`, `ReceiptStoreConfig` and `ReceiptProperties`.
- **Same-key writes are not rejected.** The class's own Javadoc (`ReceiptStore.java:34-43`) is
  explicit about this. On a versioned Object Lock bucket, a second `putObject` to the same key
  creates a **new version** instead of being refused. The retained original version can't be
  deleted or overwritten, but the "current" object would change. The no-second-write guarantee is
  structural: `receiptUuid` is fresh per `attest()`, and L9 says such a write is "a bug, not an
  update". The platform does not enforce it, since there is no `If-None-Match` conditional put.

**Signing.** The receipt digest is signed by Crypto Service's KMS key (`/attest`). The signature
and `kms_key_id` are stored on the `receipts` row and in the S3 JSON. An altered receipt payload
therefore can't carry a valid signature without KMS access (threat #4, crypto-service).

**Events/outbox and security.** These are unchanged by this task: the transactional outbox, the
resource-server JWT, and `ApiExceptionHandler`. The service has only three HTTP surfaces:
`POST /api/v1/invoices`, two `GET /api/v1/invoices` endpoints, and `GET /api/v1/exports/transactions`.
**None of them touches the ledger or the receipts.** No PUT, PATCH or DELETE mapping exists
anywhere.

## 2. Existing code this task touches

| Item | Exists? | Relevance |
|---|---|---|
| `SECURITY-THREAT-MODEL.md` (repo root, **not** under `spec/`) | Yes | Row #5 is currently "closed (crypto-service portion)". Its prose says the hash-chain ledger and on-chain anchor are "Payment-Service-owned … (not yet built)". This is the doc the task verifies against, and the likely deliverable. Crypto T28's precedent updated its Status column. |
| `V1`, `V2`, `V9` migrations | Yes | The DB-grant half of "no UPDATE/DELETE". |
| `SchemaBaselineMigrationIntegrationTest` | Yes | Already proves the grant. `paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheGrantedTables` (`:227`) runs a real `UPDATE`/`DELETE` as `payment_app` on `ledger_entries`, `verification_records` and `receipts`; both are denied. `grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore` (`:296`) checks the privilege set exactly, so TRUNCATE, TRIGGER and REFERENCES would be caught too. |
| `LedgerEntry`, `LedgerEntryRepository`, `LedgerService`, `LedgerVerifier` | Yes | The code-path half. Tested by `LedgerServiceTest`, `LedgerVerifierTest` and the T26 E2E (`LedgerVerifier.verify()` returns `Intact`). |
| `Receipt`, `ReceiptRepository`, `ReceiptService`, `ReceiptStore` | Yes | The receipt half. Tested by `ReceiptStoreTest`, plus the T26 E2E, which covers LocalStack COMPLIANCE retention and a versioned DELETE returning 403. |
| `ArchitectureTest` | Yes | **It has no rule about ledger or receipt mutation.** Its rules cover money types, cross-module entity imports, KMS/chain-client imports, problem-type URIs and the `permitAll` allowlist. No rule forbids calls to `delete*` on these repositories, or `@Modifying` queries on these entities. |
| `ledger_anchors` / R26 / task 21 | Table only | Deferred (Q4, 2026-10-11). No code writes to it. This is the "on-chain anchor" leg of row #5's stated mitigation (`ARCHITECTURE.md` §6.5). |

**New:** nothing exists yet that is specific to T28.

## 3. Established patterns to follow

- **Verification tasks** (crypto T28 precedent, `.ai/prompts/crypto/T28/`):
  - Each claim is verified against a named, **currently passing** test.
  - Status cells in `SECURITY-THREAT-MODEL.md` are updated with the test names.
  - Ownership splits and pre-existing defects are labelled distinctly.
  - Nothing under `spec/` is changed.
- **Structural guards** use ArchUnit as plain `@Test` methods holding a `private static final
  ArchRule`, never `@ArchTest` fields (the Surefire 3.5.3 bug). Each guard also gets
  `…IsCheckedDuringStandardBuild` and `…ActuallyFailsAgainstAGenuineViolation` canaries over a
  fixture class in `archtestfixtures/`. Precedents: T25 `shouldEnforcePublicEndpointAllowlist`,
  and the KMS-import ban.
- **DB-privilege proofs:** Testcontainers Postgres. Migrate as the owner, then connect as
  `payment_app` and attempt the real statement (`SchemaBaselineMigrationIntegrationTest`).
- **S3 proofs:**
  - **Unit:** a mocked `S3Client` with `verify(never())`.
  - **Integration:** LocalStack 3.8 with an Object Lock bucket (the T26 E2E).
- **Config:** flat `application.properties`.

## 4. Testing conventions

- **Unit:** plain JUnit 5 + Mockito, with a fixed `Clock`.
- **Integration:** Testcontainers (`postgres:16-alpine`, `apache/kafka:3.8.0`, LocalStack 3.8),
  plus WireMock 3.9.1.
- **ArchUnit** canary style, as described above.
- **Baseline suite:** `mvn -o -pl services/payment verify` gives **497/497**, as of T27.

## 5. Known gaps and unknowns

1. **No code-structure guard on ledger/receipt mutation.** The "no code path" claim currently
   rests on two things: the DB grant, a runtime control that is tested; and a grep, a manual
   check made in this phase. No committed test would fail if someone added
   `ledgerEntryRepository.delete(…)` or a `@Modifying UPDATE LedgerEntry`. The DB would reject it
   at runtime, but only once that code path runs.
2. **A same-key S3 re-put creates a new version.** The original version is retained and
   immutable, but the bucket does not refuse the write. Whether "overwrite a receipt object" in
   the task text means "destroy the retained version" (the platform prevents this) or "change the
   current version" (only application structure prevents this): **I do not know.** That is a
   Phase 1/2 interpretation.
3. **The on-chain anchor (R26) is deferred.** An insider holding the **owner** role (`checky`) or
   DBA access is not bound by `payment_app`'s grants. Such a person could rewrite `ledger_entries`
   and recompute every hash consistently, and `LedgerVerifier` would report `Intact`. What still
   catches this:
   - S3 WORM: the receipts' original versions keep their digests.
   - The KMS signatures, which the insider can't re-forge.

   There is no automated cross-check between the DB chain and the S3 objects. **I do not know**
   whether any reconciliation job is intended in Phase 1. None exists in code.
4. **S3 Object Lock in real AWS** is an infrastructure control: bucket-level object-lock
   configuration, IAM denying `s3:BypassGovernanceRetention`, `PutObjectRetention` and so on.
   It is not Java-testable. LocalStack proves only that the SDK calls behave as expected. Row #5
   already labels this an infrastructure control.
5. **Whether T28 may add tests or code, or is verification plus a doc update only.** The task
   text says "verify … confirm". Crypto T28 changed only the threat-model doc and cited existing
   tests, although its Phase 11 added test assertions. **I do not know** the intended scope here.
   It is a Phase 2 decision.
6. **`LedgerVerifier` has no production caller.** Tamper detection exists as a capability, but
   nothing runs it on a schedule or on demand. **I do not know** whether R22 intends one. The
   spec text is event-phrased. That is a Phase 1 extraction question.
7. **The `payments`/`invoices` `ON DELETE CASCADE` FKs** (`V1:33,61`) cascade toward
   `verification_records`. `payment_app` has no `DELETE` on `payments` or `invoices`, so it can't
   trigger them. `receipts` and `ledger_entries` use `RESTRICT`. Not a gap for `payment_app`;
   noted for completeness.
