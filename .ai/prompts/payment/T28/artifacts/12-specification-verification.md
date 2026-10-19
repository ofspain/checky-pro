# payment · T28 · Phase 12 — Specification Verification

Consumes artifacts `00`–`11`.

**Checked against:**
- `spec/payment-service/tasks.md:49` (task 28)
- `requirements.md` R21, R22, R23 and R26
- `design.md` L7 (`:11`) and L9 (`:13`)
- the `package.md` §9 checklist (`:120`, `:121`, `:127`)
- `SECURITY-THREAT-MODEL.md` row #5
- the frozen brief's AC1–AC7

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| tasks.md:49: verify threat #5 is defeated by append-only ledger + S3 WORM | Yes | `SECURITY-THREAT-MODEL.md` row #5: the payment portion, with Prevention / Detection / Residual | All tests below | The on-chain-anchor leg (R26) is deferred and disclosed | None |
| tasks.md:49: no code path can UPDATE/DELETE `ledger_entries` | Yes | DB layer: `V2__payment_app_role_and_grants.sql:40` (INSERT+SELECT only). Code-path layer: `ArchitectureTest` D1 and D5 rules. Entities: `LedgerEntry` is `@Immutable` and has no mutators. | `SchemaBaselineMigrationIntegrationTest.paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheGrantedTables`, `.grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore`; `ArchitectureTest.shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueries…` (×3), `…shouldConfineRawSqlAndEntityManagerWritesToTheExportRead…` (×3) | Hibernate/JPA paths not listed in D5 are bounded only by the DB grant | Stronger than D1, human-approved: method references; guarded set by domain type, walking the hierarchy; `remove*` deletes |
| tasks.md:49 (+ receipts, L9): no UPDATE/DELETE on `receipts` | Yes | `V9__payment_app_receipts_grant.sql:4`; D1 and D5; `Receipt` has no mutators | As above; `…GuardsTheRealRepositories` pins `ReceiptRepository` | — | None |
| tasks.md:49: no code path can overwrite a receipt object; R23; L9 | Yes, at the SDK-call level | `ReceiptStore.java:105-112` (the only two `putObject` calls), `:160-172` (COMPLIANCE + retain-until). The D2 rule: only `ReceiptStore` calls `S3Client.putObject`; no other S3 operation anywhere; S3 SDK use confined to `ReceiptStore`/`ReceiptStoreConfig` | `ArchitectureTest.shouldConfineS3SdkToReceiptStorePutObjectOnly…` (×2); `ReceiptStoreTest.shouldStoreReceiptInS3ObjectLockWorm`, `.shouldSetRetentionDateToSevenYearsFromTheInjectedClock`, `.shouldNeverCallDeleteOrAnyOverwriteApi`; `PaymentEndToEndIntegrationTest.shouldDriveInvoiceToAttestedReceiptEndToEnd` (versioned DELETE returns 403) | A same-key re-put would make a new version, with the locked original surviving. It is prevented by a fresh `receiptUuid` (`ReceiptService.java:129`), not refused by S3 (FU-T28-1). The async-client ban has no separate mutation proof (M14, explained in `11`). | None |
| R21: append chained to head | Yes (T19) | `LedgerService.java:85` | `LedgerServiceTest.shouldAppendLedgerEntryWithPrevHashChainingToHead` | — | None |
| R22, package.md:120: tampering with any row is detectable | Yes (T20) | `LedgerVerifier` | `LedgerVerifierTest` 15/15, including `shouldDetectBrokenHashChainOnVerification` and the corruption tests for `prev_hash`, genesis, middle-delete, head, and `entry_hash`-only; T26 E2E returns `Intact` | Nothing in `src/main` calls the verifier (FU-T28-2) | None |
| R26: anchor deferred; must be disclosed (package.md:127) | Disclosed | Row #5 Residual: "*deferred feature*". Manifest follow-ups: FU-T28-3 | n/a | Deferred by the product owner (Q4) | None |
| package.md:121: no code path can overwrite an existing receipt object | Yes | As in the R23 row | As in the R23 row | As in the R23 row | None |
| Frozen D4: row #5 structure; test names exact | Yes | `SECURITY-THREAT-MODEL.md` header, row #5, footnote | Every test name cited in row #5 was checked at this phase with `grep "void <name>("` against `services/*/src/test`. All resolve, both payment and crypto. | — | None |
| AC6: suite | Yes | `mvn -o -pl services/payment verify`: **505/505**, `BUILD SUCCESS` (Phase 11 re-run) | — | — | None |
| AC7: scope | Yes | Changed: `ArchitectureTest.java`, 8 fixtures (`archtestfixtures/RogueS3ObjectMutator`, `RogueRawSqlUser`; `receipt/ledger/` ×4; `receipt/` ×2), and `SECURITY-THREAT-MODEL.md` | — | — | Fixtures placed in feature test packages (deviation 3, approved) |

## Principal-engineer assessment

1. **Is the task fully complete?** Yes. Every claim in task 28 now rests on a committed,
   passing, mutation-proven test, at three layers:
   - **DB grant:** a real UPDATE or DELETE is denied.
   - **Code path:** ArchUnit bans repository deletes and bulk updates (calls, lambdas and method
     references), raw SQL, `EntityManager` and Hibernate session writes, and any S3 operation
     other than `ReceiptStore`'s `putObject`.
   - **Platform:** COMPLIANCE retention, and a refused versioned delete, shown on LocalStack.

   Detection is covered by `LedgerVerifier`. Row #5 records all of it.
2. **Does it satisfy every acceptance criterion?** Yes, AC1–AC7. Across Phases 6, 9 and 11,
   fourteen mutations were run. Thirteen failed on their intended test. M14 is an explained
   limit of what a fixture can prove, not a gap in the rule.
3. **Does it violate any LOCKED decision?** No.
   - **L7 and L9** are upheld, and now structurally enforced.
   - **L12:** fixtures reference only their own module's entities.
   - **Untouched:** production code, migrations, poms, `spec/` and `contracts/`.
   - **R26** is deferred as decided, and disclosed.
4. **Remaining risks** (all recorded in row #5, plus FU-T28-1/2/3):
   - **The on-chain anchor is not built.** An insider with owner or DBA rights could rewrite the
     whole chain consistently. The S3 originals and KMS signatures would contradict it, but
     nothing cross-checks them automatically.
   - **`LedgerVerifier` has no production trigger,** so tampering is detected only on demand.
   - **A same-key S3 re-put is not refused by the platform.** It is prevented only by structure.
   - **The export's native SQL text** is bounded only by the DB grant.
   - **Real-AWS bucket lock and IAM** are infrastructure-controlled.

**PASS.** Threat #5's payment portion is verified by committed, mutation-proven guards at the DB,
code-path and S3 layers. Every residual risk is disclosed in row #5.
