# payment · T28 · Phase 6 — Implementation Notes

Consumes:
- `artifacts/05-implementation-plan.md`
- `artifacts/04-frozen-task-brief.md` (FROZEN)

## What changed

| File | Change | Plan / brief |
|---|---|---|
| `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` | **Modified.** Adds the constants `GUARDED_ENTITY_TYPES`, `SPRING_DATA_PACKAGE`, the `RECEIPT_STORE`/`RECEIPT_STORE_CONFIG`/`TRANSACTION_EXPORT_REPOSITORY` FQNs, and the helper `isGuardedRepository(JavaClass)`. Adds three rules: <br>• `shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueries` (D1) <br>• `shouldConfineS3SdkToReceiptStorePutObjectOnly` (D2, built from two named conditions, `useTheS3SdkOnlyFromTheReceiptStore` and `callOnlyPutObjectOnS3Clients`) <br>• `shouldConfineRawSqlAndEntityManagerWritesToTheExportRead` (D5) <br>Adds 7 `@Test` methods. The class goes from 12 to 19 tests. | D1, D2, D5 |
| `…/receipt/ledger/RogueLedgerEntryRepositoryUser.java` (test, new) | Deletes through `LedgerEntryRepository` three ways: a direct call, a call in a lambda, and a method reference. | D1 fixture |
| `…/receipt/ledger/RogueModifyingLedgerEntryRepository.java` (test, new) | A second `JpaRepository<LedgerEntry, Long>` with a `@Modifying @Query` UPDATE. Marked `@NoRepositoryBean`, so Spring Data never instantiates it. | D1(b) fixture |
| `…/archtestfixtures/RogueS3ObjectMutator.java` (test, new) | Five S3 violations: <br>• `deleteObject`, called directly <br>• `copyObject`, called in a lambda <br>• `deleteObject`, as a method reference <br>• async `deleteObject` <br>• its own `S3Client.builder()` <br>It also depends on `S3Presigner`. | D2 fixture |
| `…/archtestfixtures/RogueRawSqlUser.java` (test, new) | Five raw-DB violations: <br>• `JdbcTemplate.update` <br>• `Statement.executeUpdate`, in a lambda <br>• `EntityManager::remove` <br>• `createNativeQuery` outside the export <br>• a JPQL `createQuery` UPDATE | D5 fixture |
| `SECURITY-THREAT-MODEL.md` | **Header:** adds "payment-service T28 — row #5 payment portion verified and closed (2026-10-19)". <br>**Row #5 Status:** the crypto-service text is kept verbatim, and a "Payment-service portion — closed" block is appended, with **Prevention** (1)–(5), **Detection**, and **Residual risk**, each residual item labelled by kind. <br>**Row #5 Implementing task:** adds payment T18, T19–T20 and T28. <br>**Footnote:** "(not yet built)" is replaced with the current state and the caveat kinds. | D4 |

No `src/main`, migration, pom, `spec/` or `contracts/` file changed (AC7). The untracked
`prompt` file is the user's; T28 did not touch it.

## Deviations forced by reality

All three are disclosed here for the Phase 9 gate. Each one makes the rules stronger, never
weaker.

1. **D1's method-reference handling.** The frozen D1 assumed bytecode records the static
   receiver type. A throwaway `javac`/`javap` probe showed that this holds for calls, including
   calls inside lambdas, but **not for method references**: `ledgerEntryRepository::delete`
   compiles to `REF_invokeInterface CrudRepository.delete`. The real ArchUnit output confirms
   it: `references method <org.springframework.data.repository.CrudRepository.delete(…)>`.
   - **Consequence:** a rule keyed only on `LedgerEntryRepository` and `ReceiptRepository` would
     silently miss method references.
   - **What D1 does instead:** it also bans any `delete*` *method reference* to a Spring Data
     type (`org.springframework.data..`), for every repository, because the bytecode can't say
     which one.
   - **Cost today:** none. No code in `src/main` deletes through any repository. The rule's
     Javadoc explains why.
2. **Guarded repositories are found by domain type, not by name.** A repository counts as
   guarded when it is an interface assignable to Spring Data `Repository` whose direct
   parameterised super-interface's first type argument is `LedgerEntry` or `Receipt`.
   - **Today** that is exactly `LedgerEntryRepository` and `ReceiptRepository`.
   - **Why:** D1(b) needs this to be testable, since a fixture can't add `@Modifying` to the real
     repository. It also guards a second repository added later for the same entity.
3. **D1 fixtures are not in `archtestfixtures`.** `LedgerEntryRepository` is package-private, so
   they sit in the test tree's `receipt.ledger` package. This follows the existing
   `payment/RogueInvoiceEntityReferencer` precedent. They are excluded from the real canary by
   `DoNotIncludeTests`. The brief's Files list named `archtestfixtures/`, so this placement is a
   deviation.

## Change made in this phase, beyond the plan

**The D2 canary was confounded, so the rule was split.** I printed the real violation messages.
Every S3 operation in the fixture was reported twice: once by the dependency check, part (a),
and once by the operation check, part (b). The dependency check alone would therefore have made
the operation assertions pass. So:
- D2 is now built from two named `ArchCondition`s.
- The failing-fixture test checks part (b) **on its own**:
  `classes().should(callOnlyPutObjectOnS3Clients)`.
- It asserts the exact `calls method <…S3Client.deleteObject(` /
  `references method <…S3Client.deleteObject(` / `…copyObject(` / `…S3AsyncClient.deleteObject(`
  / `…S3Client.builder()` texts.

The rule's meaning is unchanged.

## Evidence per acceptance criterion

| AC | Evidence |
|---|---|
| **AC1** (DB) | `SchemaBaselineMigrationIntegrationTest`: 22/22 pass, including `paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheGrantedTables` and `grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore`. |
| **AC2** (code path) | The D1 and D5 `…IsCheckedDuringStandardBuild` tests pass on the real code. The failing-fixture tests report every form: `deleteDirectly(`, `deleteInsideALambda(` and `deleteByMethodReference(` (shown as `references method <…CrudRepository.delete(`), plus `rewriteEveryPrevHash`; and for D5 `jdbcTemplateUpdate(`, `statementInsideALambda(`, `removeByMethodReference(` (`references method <…EntityManager.remove(`), `nativeQueryOutsideTheExport(` and `jpqlUpdate(`. `…ExclusionIsLoadBearing` finds exactly one `createNativeQuery` access in `TransactionExportRepository`. |
| **AC3** (S3) | The D2 rule passes on the real code. The fixture fails part (a) on `presign(` (an `S3Presigner` parameter), and part (b) alone on all five operations. `ReceiptStoreTest` passes 12/12, including `shouldStoreReceiptInS3ObjectLockWorm` and `shouldNeverCallDeleteOrAnyOverwriteApi`. `PaymentEndToEndIntegrationTest` passes 3/3, covering COMPLIANCE retention and a versioned DELETE returning 403. |
| **AC4** (detection) | `LedgerVerifierTest` passes 15/15, including `shouldDetectBrokenHashChainOnVerification`, `…PrevHashCorruption…`, `…CorruptedGenesisPrevHash`, `…AMiddleEntryIsDeleted`, `…CorruptionOfTheHeadEntry` and `…DirectEntryHashCorruptionWithUntouchedPayload`. `LedgerServiceTest` passes 7/7. The T26 E2E shows `Intact`. |
| **AC5** (doc) | Row #5 has the D4 structure. Every cited test name was copied from source. |
| **AC6** (suite) | `mvn -o -pl services/payment verify` from the repo root: `Tests run: 504, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` in 01:29 (497 + 7). |
| **AC7** | `git status` shows only the files above. |

### Mutation proofs

Each mutation was temporary. The restore was confirmed with `cmp`, and a clean re-run returned
exit 0.

| # | Mutation | Result |
|---|---|---|
| M1 | Method-reference branch disabled | D1 failing-fixture test fails: the `deleteByMethodReference` violation is no longer reported. |
| M2 | Export exemption removed | D5 real-code canary fails on `TransactionExportRepository`. |
| M3 | `builder()` exemption removed | D2 real-code canary fails on `ReceiptStoreConfig.s3Client` → `S3Client.builder`. |
| M4 | `putObject` no longer allowed | D2 real-code canary fails on both of `ReceiptStore.store`'s `putObject` calls. That proves the rule sees the real S3 calls, not an empty set. |
| M5 | Guarded-repository detection disabled | D1 failing-fixture test fails. |

## Follow-ups (frozen brief, unchanged)

- **FU-T28-1.** A conditional `If-None-Match: *` put.
- **FU-T28-2.** A production trigger for `LedgerVerifier`.
- **FU-T28-3.** R26, the anchor, with interim DB-to-S3 reconciliation.

## Open Questions

None. Deviations 1–3 are flagged for the Phase 9 gate.
