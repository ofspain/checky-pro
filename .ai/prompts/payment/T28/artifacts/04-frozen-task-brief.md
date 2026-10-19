# payment · T28 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN.** Approved by the human on 2026-10-19. Downstream phases may not renegotiate
it.

Consumes:
- `artifacts/02-task-implementation-brief.md` (TIB)
- `artifacts/03-design-challenge.md` (Kimi)

Every finding was checked against source before disposition.

## Phase 3 dispositions

| # | Finding | Verified | Disposition |
|---|---|---|---|
| F1 | D1 doesn't cover raw SQL, JDBC or `EntityManager` writes. | True. Main code's only raw DB access is `TransactionExportRepository` (`EntityManager.createNativeQuery`, the T22 read). `ShedLockConfig` only passes a `DataSource` to `JdbcTemplateLockProvider`, whose JDBC runs inside library code. No `java.sql` or `JdbcTemplate` import exists in `src/main`. | **ACCEPTED: add a guard (human).** New rule D5 (below). Row #5 discloses that D5 confines *where* raw SQL can be issued, but not *what* the one allowed native query says; the AC1 DB grant covers that. |
| F2 | D2 covers only `S3Client`. | Partly true. Only the `software.amazon.awssdk:s3` artifact is on the classpath, and it ships `S3AsyncClient` and `S3Presigner`. `S3TransferManager` is a separate artifact that isn't present. Only `ReceiptStore` and `ReceiptStoreConfig` import `…services.s3`. | **ACCEPTED: whole package (human).** D2 is widened (below). |
| F3 | A same-key re-put isn't structurally prevented. | True. `ReceiptService.java:129` generates `UUID.randomUUID()` per issuance. That is existing, structural protection, not platform refusal. | **ACCEPTED: disclose and log a follow-up (human).** Row #5 states it prominently. No new test. The conditional `If-None-Match` put is logged as FU-T28-1. |
| F4 | The D1 name over-promises. | True. | **ACCEPTED.** It is renamed to `shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueries`, with Javadoc listing exactly what is and isn't covered. |
| F5 | Lambdas and method references may slip past D1. | True, and important. ArchUnit records `repo::delete` as a `JavaMethodReference`, not a `JavaMethodCall`, so a `callMethod`-based rule would miss it. A lambda body becomes a synthetic method containing a normal call, so that case is already caught. | **ACCEPTED.** Every new rule matches *any access* (calls **and** method references). The violation fixtures include a direct call, a call in a lambda, and a method reference. |
| F6 | D2 doesn't prove COMPLIANCE on every put. | True. | **ACCEPTED.** Row #5 attributes the claims separately: "only `putObject`" comes from D2, and "every put is COMPLIANCE plus retain-until" comes from `ReceiptStoreTest` and the T26 E2E. |
| F7 | Row #5 should separate prevention, detection and residual risk. | True. | **ACCEPTED.** Row #5 is structured that way, and says that detection is on-demand only. |

**Phase 2 OQ1:** add guards. **OQ2:** disclose, with follow-up FU-T28-1.

## Task

Check payment-service against `SECURITY-THREAT-MODEL.md` threat #5. Prove, with committed and
passing tests, that no code path can `UPDATE`/`DELETE` `ledger_entries` or `receipts`, or
delete or overwrite a receipt object. Disclose what remains, and record the payment portion in
row #5.

## Decisions (frozen)

| # | Decision |
|---|---|
| D1 (F4/F5) | **ArchUnit rule `shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueries`** in `ArchitectureTest`. <br>**(a)** No class in the analysed main packages *accesses* (by call or method reference) any method whose name starts with `delete` and whose owner is `LedgerEntryRepository` or `ReceiptRepository`. Inherited `CrudRepository`/`JpaRepository` methods are covered, because bytecode records the static receiver type. <br>**(b)** Neither repository declares a method annotated `@Modifying`. <br>Javadoc states exactly what is covered: repository deletes and `@Modifying`. It also says what is covered elsewhere: updates by D3 immutability, raw SQL by D5, and everything by the DB grant (AC1). |
| D2 (F2/F6) | **ArchUnit rule `shouldConfineS3SdkToReceiptStorePutObjectOnly`.** <br>**(a)** No class other than `ReceiptStore` and `ReceiptStoreConfig` depends on anything in `software.amazon.awssdk.services.s3..`. <br>**(b)** No class accesses any method on `S3Client` or `S3AsyncClient` other than `putObject`. Static builder entry points used to construct a client (`S3Client.builder()`) are allowed only from `ReceiptStoreConfig`. Request-model builders are allowed. <br>Javadoc states that COMPLIANCE-on-every-put is **not** this rule's claim. |
| D3 | **No re-save-as-update guard.** None is needed. `Receipt` and `LedgerEntry` have no setters or mutators, only `create(…)` and getters, and `LedgerEntry` is `@Immutable`. Recorded as evidence. |
| D5 (F1, new) | **ArchUnit rule `shouldConfineRawSqlAndEntityManagerWritesToTheExportRead`.** <br>**(a)** No main class accesses `org.springframework.jdbc.core.JdbcTemplate`, `NamedParameterJdbcTemplate`, or the `java.sql.Statement`, `PreparedStatement` and `CallableStatement` execute methods. <br>**(b)** No main class accesses `EntityManager.remove`, `merge`, `createQuery` or `createNativeQuery`, except `TransactionExportRepository` → `createNativeQuery` (the T22 read). <br>An exclusion-is-load-bearing canary proves that the exception matches exactly the one real call. |
| D4 (F3/F7) | **Row #5 rewritten.** <br>**Prevention:** <br>• DB grant (AC1 tests) <br>• repository deletes and `@Modifying` (D1) <br>• raw SQL confined (D5) <br>• entities immutable (D3) <br>• S3 SDK confined to `putObject` (D2) <br>• every put is COMPLIANCE plus retain-until (`ReceiptStoreTest`, T26) <br>• a locked version can't be deleted (T26, LocalStack) <br>**Detection:** <br>• `LedgerVerifier` (the `LedgerVerifierTest` suite, plus T26 returning `Intact`) <br>• the KMS signatures on receipts <br>**Residual (labelled by kind):** <br>• **Deferred feature:** the on-chain anchor (R26). Without it, an insider with owner or DBA rights can rewrite the chain consistently. <br>• **Detection is on demand only:** `LedgerVerifier` has no production caller. <br>• **Platform semantics:** a same-key re-put makes a new version (FU-T28-1). <br>• **D5 limit:** the export's native SQL text is checked only by the DB grant. <br>• **Infrastructure-controlled:** real-AWS bucket lock and IAM. <br>The header status line and the footnote prose are updated as well. |

## Locked Decisions

- **L7:** append-only ledger; `payment_app` has `INSERT`+`SELECT` only.
- **L9:** receipts are WORM; there is no delete or overwrite path in application code.
- **L12:** the new rules follow `ArchitectureTest`'s plain-`@Test` and canary conventions.
- **R26:** deferred, and disclosed.

## Files

**Create:**
- `services/payment/src/test/java/com/themistra/payment/archtestfixtures/` violation fixtures
  for D1, D2 and D5, with names fixed in Phase 5.
- Each fixture covers a direct call, a call in a lambda, and a method reference, where the rule
  applies.

**Modify:**
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`: three rules
  plus their canaries.
- `SECURITY-THREAT-MODEL.md`: row #5, the header status line, and the footnote prose about
  row #5.

**NOT modify:**
- `services/payment/src/main/**`
- every migration
- both poms
- every other test class
- `spec/**` and `contracts/**`
- other services
- threat-model rows other than #5

## Acceptance Criteria (final)

- **AC1 (DB).** `payment_app` holds exactly `{INSERT, SELECT}` on `ledger_entries` and
  `receipts`, and a real `UPDATE` or `DELETE` is denied. Proof:
  `SchemaBaselineMigrationIntegrationTest.paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheGrantedTables`
  and `.grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore`, both passing.
- **AC2 (code path).** The D1 and D5 rules pass on the real code. Their canaries fail against
  fixtures for all three access forms where applicable. The D5 exclusion is proven to match
  exactly one real access.
- **AC3 (S3).** The D2 rule passes on the real code, and its canary fails on the fixture. The
  following pass:
  - `ReceiptStoreTest.shouldStoreReceiptInS3ObjectLockWorm`
  - `ReceiptStoreTest.shouldNeverCallDeleteOrAnyOverwriteApi`
  - the T26 E2E: COMPLIANCE retention, and a versioned DELETE returning 403
- **AC4 (detection).** The `LedgerVerifierTest` suite passes (`shouldDetectBrokenHashChainOnVerification`
  plus payload, `prev_hash`, head, middle-delete and genesis corruption), and the T26 E2E
  returns `Intact`.
- **AC5 (doc).** Row #5 carries the D4 structure, with every named test spelled exactly as in
  source.
- **AC6 (suite).** `mvn -o -pl services/payment verify` passes. The total is 497 plus the new
  tests.
- **AC7.** No change outside the Files list.

## Required Tests

For each of D1, D2 and D5, plus D5's exclusion:
- `…IsCheckedDuringStandardBuild`
- `…ActuallyFailsAgainstAGenuineViolation`
- D5 only: `…ExclusionIsLoadBearing`

Plus the cited existing tests under AC1, AC3 and AC4.

## Follow-ups (human-owned, out of T28)

- **FU-T28-1.** Make S3 refuse a same-key receipt write with a conditional put
  (`If-None-Match: *`). This is a production change to `ReceiptStore`.
- **FU-T28-2.** Give `LedgerVerifier` a production trigger: a scheduled job or an ops endpoint,
  with alerting on `Broken`.
- **FU-T28-3.** R26, the on-chain anchor, already tracked as task 21 and deferred. Until it
  lands, also consider an automated DB-vs-S3 digest reconciliation.

## Open Questions

None remaining.
