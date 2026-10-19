# payment · T28 · Phase 10 — Test Manifest

Consumes:
- `artifacts/04-frozen-task-brief.md` (FROZEN)
- `artifacts/09-review-resolution.md`

T28 is a verification task, and its tests *are* the deliverable. They were written in Phase 6 and
widened in Phase 9. This phase adds no test file. It maps every test to its acceptance criterion,
and records the full-suite run against the resolved code.

No production code was changed.

## New tests (`ArchitectureTest`, plain `@Test`, Surefire-safe)

| Test | AC | Verifies |
|---|---|---|
| `shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueriesIsCheckedDuringStandardBuild` | AC2 | The real code has no `delete*`/`remove*` access to a guarded repository (call, lambda or method reference). It has no `delete*`/`remove*` method reference to any Spring Data type. No guarded repository declares `@Modifying` or a derived delete. L7/L9. |
| `shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueriesActuallyFailsAgainstAGenuineViolation` | AC2 (boundaries) | Reports:<br>• `deleteDirectly(`, `deleteInsideALambda(` and `deleteByMethodReference(` (shown as `references method <…CrudRepository.delete(`)<br>• the `@Modifying` `rewriteEveryPrevHash`<br>• `removeThroughADerivedQuery(`<br>• `RogueIndirectLedgerEntryRepository.removeByReceiptId(java.lang.Long) is a derived delete`, which proves the hierarchy walk |
| `shouldConfineS3SdkToReceiptStorePutObjectOnlyIsCheckedDuringStandardBuild` | AC3 | Only `ReceiptStore`/`ReceiptStoreConfig` depend on the S3 SDK. The only S3 operation is `putObject`, plus the `builder` chain in `ReceiptStoreConfig`. L9/R23. |
| `shouldConfineS3SdkToReceiptStorePutObjectOnlyActuallyFailsAgainstAGenuineViolation` | AC3 (boundaries) | **Part (a):** a dependency on `S3Presigner` outside the store. **Part (b) alone:** `deleteObject` (direct and by method reference), `copyObject` in a lambda, the async `deleteObject`, `S3Client.builder()` and `S3ClientBuilder.build()` outside the config, and `S3Presigner.close()`. |
| `shouldConfineRawSqlAndEntityManagerWritesToTheExportReadIsCheckedDuringStandardBuild` | AC2 | The real code uses none of: <br>• `JdbcTemplate`/`NamedParameterJdbcTemplate`/`JdbcClient` <br>• JDBC `Statement.execute*` <br>• a Hibernate session <br>• `Query.executeUpdate` <br>• `EntityManager` remove/merge/createQuery/createNamedQuery/createStoredProcedureQuery <br>`createNativeQuery` appears only in `TransactionExportRepository`. |
| `shouldConfineRawSqlAndEntityManagerWritesToTheExportReadActuallyFailsAgainstAGenuineViolation` | AC2 (boundaries) | Reports every one of the 10 fixture methods (11 banned accesses, since one method makes two calls), including the lambda and method-reference forms. |
| `shouldConfineRawSqlAndEntityManagerWritesToTheExportReadExclusionIsLoadBearing` | AC2 | `TransactionExportRepository` has exactly one `EntityManager.createNativeQuery` access, so the exemption is not vacuous. |

**Fixtures.** None is referenced by production code, and all are excluded from the real canary
by `DoNotIncludeTests`.
- `receipt/ledger/`: `RogueLedgerEntryRepositoryUser`, `RogueModifyingLedgerEntryRepository`,
  `RogueLedgerEntryRepositoryBase` and `RogueIndirectLedgerEntryRepository`. The repositories are
  marked `@NoRepositoryBean`.
- `archtestfixtures/`: `RogueS3ObjectMutator` and `RogueRawSqlUser`.

## Existing tests cited as evidence (unchanged, all passing)

| Test | AC | Verifies |
|---|---|---|
| `SchemaBaselineMigrationIntegrationTest.paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheGrantedTables` | AC1 | A real `UPDATE`/`DELETE` as `payment_app` on `ledger_entries` and `receipts` is denied. |
| `SchemaBaselineMigrationIntegrationTest.grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore` | AC1 | The privilege set is exactly `{INSERT, SELECT}`. TRUNCATE, TRIGGER and REFERENCES are excluded. |
| `ReceiptStoreTest.shouldStoreReceiptInS3ObjectLockWorm`, `.shouldSetRetentionDateToSevenYearsFromTheInjectedClock`, `.shouldNeverCallDeleteOrAnyOverwriteApi` | AC3 | Every put is COMPLIANCE with a 7-year retain-until. There is no delete and no copy. |
| `PaymentEndToEndIntegrationTest.shouldDriveInvoiceToAttestedReceiptEndToEnd` | AC3, AC4 | LocalStack shows COMPLIANCE retention, and a versioned DELETE returns 403. `LedgerVerifier` returns `Intact` after a full flow. |
| `LedgerVerifierTest.shouldDetectBrokenHashChainOnVerification`, plus `…PrevHashCorruptionDistinctFromEntryHashCorruption`, `…CorruptedGenesisPrevHash`, `…BrokenChainWhenAMiddleEntryIsDeleted`, `…CorruptionOfTheHeadEntry` and `…DirectEntryHashCorruptionWithUntouchedPayload` | AC4 | Every tamper shape is detected. R22. |
| `LedgerServiceTest.shouldAppendLedgerEntryWithPrevHashChainingToHead` | AC4 | R21 chaining. |

## Mutation evidence

All mutations were temporary, restored, and confirmed with `cmp`. Details are in `06` and `09`.

| Mutation | Failing test |
|---|---|
| M2 (export exemption removed) | D5 real-code canary (`…IsCheckedDuringStandardBuild`) |
| M3 / M8 (S3 `builder()` or builder-chain allowance removed) | D2 real-code canary (`…IsCheckedDuringStandardBuild`) |
| M4 (`putObject` disallowed) | D2 real-code canary (`…IsCheckedDuringStandardBuild`) |
| M1 (method-reference branch disabled) | D1 failing-fixture test (`…ActuallyFails…`) |
| M5 (guarded-repository detection disabled) | D1 failing-fixture test |
| M6 (`remove` prefix dropped) | D1 failing-fixture test |
| M7 (hierarchy walk disabled) | D1 failing-fixture test |
| M9 (Hibernate session ban off) | D5 failing-fixture test |
| M10 (`JdbcClient` ban off) | D5 failing-fixture test |
| M11 (`executeUpdate` ban off) | D5 failing-fixture test |

## Run evidence

| AC | Result |
|---|---|
| AC6 | `mvn -o -pl services/payment verify` from the repo root, after Phase 9: **`Tests run: 504, Failures: 0, Errors: 0, Skipped: 0`**, `BUILD SUCCESS`. 497 baseline + 7. Class results: `ArchitectureTest` 19/19, `SchemaBaselineMigrationIntegrationTest` 22/22, `LedgerVerifierTest` 15/15, `LedgerServiceTest` 7/7, `ReceiptStoreTest` 12/12, `PaymentEndToEndIntegrationTest` 3/3. |
| AC5 | `SECURITY-THREAT-MODEL.md` row #5 has its Prevention, Detection and Residual-risk structure. Every cited name is copied from source. |
| AC7 | Changes are confined to `ArchitectureTest`, the six fixtures and `SECURITY-THREAT-MODEL.md`. |

## Not tested here

These are disclosed in row #5:
- **The on-chain anchor** (R26, deferred).
- **A production trigger for `LedgerVerifier`.** None exists.
- **A same-key S3 re-put.** It is prevented by structure, not by the platform.
- **The export's SQL text.** It is bounded by the DB grant.
- **Real-AWS bucket and IAM.**

## Open follow-ups (Phase 11 Gap 8)

These are human-owned and recorded in the frozen brief. They are deliberately not written as
`@Disabled` placeholder tests.

- **FU-T28-1.** A conditional `If-None-Match: *` put in `ReceiptStore`, so S3 refuses a same-key
  receipt write.
- **FU-T28-2.** A production trigger for `LedgerVerifier`: a scheduled job or an ops endpoint,
  with alerting on `Broken`.
- **FU-T28-3.** R26, the on-chain anchor (task 21, deferred). Until it lands, consider an
  automated reconciliation of DB and S3 digests.
- **Infrastructure.** Real-AWS bucket Object Lock configuration and IAM. These are owned by the
  infra workstream.

## Open Questions

None.
