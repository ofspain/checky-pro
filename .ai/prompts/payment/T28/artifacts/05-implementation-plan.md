# payment · T28 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

There is no schema, DAO, service or API layer. All work is in tests plus one document.

## Two facts from grounding that shape the plan

Both were verified at Phase 5.

1. **Package-private targets.** `LedgerEntryRepository` (`receipt.ledger`), `ReceiptRepository`
   (`receipt`) and `TransactionExportRepository` (`export`) are package-private.
   - **Consequence for fixtures:** the D1 violation fixtures can't live in `archtestfixtures`.
     They go in the *test* source tree's `com.themistra.payment.receipt.ledger` package. This
     follows the `payment/RogueInvoiceEntityReferencer` precedent. `analyzedClasses()` uses
     `DoNotIncludeTests`, so they never reach the real canary.
   - **Consequence for the rules:** they refer to these classes by fully-qualified name, not by
     class literal.
   - The D2 and D5 fixtures depend only on public SDK, JPA and JDBC types, so they stay in
     `archtestfixtures`.
2. **Method references record the declaring type.** A throwaway `javac`/`javap` probe in the
   scratchpad showed this. For `s.delete(x)` where `s` has a sub-interface type, the
   `invokeinterface` owner is the **static receiver type** (`Sub.delete`). That is also true
   inside a lambda body. But for the method reference `s::delete`, the `MethodHandle` is
   `REF_invokeInterface Base.delete`: the **declaring type**.
   - **What this breaks:** frozen D1's note that "inherited methods are covered because
     bytecode records the static receiver type" holds for calls, but **not** for method
     references.
   - **For D1:** `ledgerEntryRepository::delete` is recorded as `CrudRepository.delete`, so
     nothing in the bytecode says which repository it was.
   - **How the plan handles it (a forced deviation, disclosed in `06`):** D1 bans a
     `delete*` *method reference* whose owner is any Spring Data generic repository interface
     (`org.springframework.data..`), for every repository. Today nothing in `src/main` uses any
     `delete*` at all, so this costs nothing now. The rule's Javadoc explains why.
   - The D2 owners (`S3Client`, `S3AsyncClient` default methods) and the D5 owners
     (`EntityManager`, `JdbcOperations`, `java.sql.Statement` subtypes) declare the banned
     methods themselves. The owner is either the declaring type or a subtype of it, so the D2
     and D5 rules use `isAssignableTo` and cover both forms.

**Guarded repositories (D1).** A guarded repository is identified by its domain type, not by
name: any analysed interface assignable to `org.springframework.data.repository.Repository`
whose direct parameterised super-interface has `LedgerEntry` or `Receipt` as its first type
argument.
- **Today this matches exactly** `LedgerEntryRepository` and `ReceiptRepository`.
- **Why by domain type:** it makes D1(b) testable against a fixture repository. It also covers a
  second repository added later for the same entity.
- This is disclosed in `06` as a strengthening.

## Files to create (test source tree)

1. **`src/test/java/com/themistra/payment/receipt/ledger/RogueLedgerEntryRepositoryUser.java`**
   (package-private). It holds a `LedgerEntryRepository` field and three methods:
   - `deleteDirectly(LedgerEntry)`: calls `repository.delete(entry)`.
   - `deleteInsideALambda(List<Long>)`: `ids.forEach(id -> repository.deleteById(id))`.
   - `deleteByMethodReference(List<LedgerEntry>)`: `entries.forEach(repository::delete)`.
2. **`src/test/java/com/themistra/payment/receipt/ledger/RogueModifyingLedgerEntryRepository.java`**
   (package-private). It is `interface … extends JpaRepository<LedgerEntry, Long>`, with one
   method annotated `@Modifying @Query("UPDATE LedgerEntry e SET e.prevHash = :h")`.
3. **`src/test/java/com/themistra/payment/archtestfixtures/RogueS3ObjectMutator.java`**. It has
   four methods:
   - `deleteDirectly(S3Client)`: `deleteObject(DeleteObjectRequest)`.
   - `copyInsideALambda(S3Client, List<CopyObjectRequest>)`: `forEach(r -> s3.copyObject(r))`.
   - `deleteByMethodReference(S3Client)`: a
     `Function<DeleteObjectRequest, DeleteObjectResponse> f = s3::deleteObject`.
   - `deleteAsync(S3AsyncClient)`: `deleteObject(...)`.

   It also calls `S3Client.builder()` outside the config class, and depends on `S3Presigner`.
4. **`src/test/java/com/themistra/payment/archtestfixtures/RogueRawSqlUser.java`**. It has
   five methods:
   - `jdbcTemplateUpdate(JdbcTemplate)`: `update("DELETE FROM payments.ledger_entries")`.
   - `statementInsideALambda(List<Statement>)`: `forEach(s -> s.executeUpdate("…"))`, wrapped
     to handle `SQLException`.
   - `removeByMethodReference(EntityManager, List<Object>)`: `forEach(em::remove)`.
   - `nativeQueryOutsideTheExport(EntityManager)`: `createNativeQuery("DELETE …")`.
   - `jpqlUpdate(EntityManager)`: `createQuery("UPDATE LedgerEntry …")`.

## Files to modify

### 1. `src/test/java/com/themistra/payment/ArchitectureTest.java`

**New constants:**
- FQN strings for `TransactionExportRepository`, `ReceiptStore` and `ReceiptStoreConfig`.
- `GUARDED_ENTITY_TYPES`, which holds `LedgerEntry` and `Receipt`.

**`shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueries`** (D1). Built as
`classes().that().resideInAPackage(ANALYZED_PACKAGE+"..").should(<custom ArchCondition>)`. For
each class the condition checks two things:

| Part | What it flags |
|---|---|
| **(a)** | Every entry in `getAccessesFromSelf()` (which includes method calls **and** method references) whose target name starts with `delete`, **where either:** <br>• the target owner is a guarded repository, or <br>• the access is a `JavaMethodReference` and the target owner resides in `org.springframework.data..` |
| **(b)** | When the class itself is a guarded repository, every declared method annotated `org.springframework.data.jpa.repository.Modifying`. |

The Javadoc spells out exactly what is covered, and lists what is covered elsewhere:
- D3, immutability
- D5, raw SQL
- AC1, the DB grant

**`shouldConfineS3SdkToReceiptStorePutObjectOnly`** (D2). A custom condition over classes in
the analysed package. It flags three things:

| Part | What it flags |
|---|---|
| **(a)** | A class other than `ReceiptStore` or `ReceiptStoreConfig` that has any dependency (`getDirectDependenciesFromSelf`) on `software.amazon.awssdk.services.s3..`. |
| **(b)** | Any access whose target owner is assignable to `S3Client` or `S3AsyncClient`, whose name is not `putObject`, and which is not `builder` called from `ReceiptStoreConfig`. |

Its Javadoc says that COMPLIANCE on every put is **not** this rule's claim; `ReceiptStoreTest`
and T26 cover that.

**`shouldConfineRawSqlAndEntityManagerWritesToTheExportRead`** (D5). A custom condition. It
flags any access whose target owner meets one of these:

| Target owner is assignable to | Flagged when |
|---|---|
| `org.springframework.jdbc.core.JdbcOperations` or `…namedparam.NamedParameterJdbcOperations` | any access |
| `java.sql.Statement` | the method name starts with `execute` |
| `jakarta.persistence.EntityManager` | the method is `remove`, `merge` or `createQuery`; or it is `createNativeQuery` and the origin class is not `TransactionExportRepository` |

**New `@Test` methods (7):**

| Test | What it does |
|---|---|
| `shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueriesIsCheckedDuringStandardBuild` | Checks the real code. |
| `shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueriesActuallyFailsAgainstAGenuineViolation` | Imports the two `receipt.ledger` fixtures, `LedgerEntryRepository` and `LedgerEntry` by name (`Class.forName`), plus the Spring Data `CrudRepository`/`JpaRepository` types. It asserts the message contains `deleteDirectly(`, `deleteInsideALambda(`, `deleteByMethodReference(` and the `@Modifying` method's name. |
| `shouldConfineS3SdkToReceiptStorePutObjectOnlyIsCheckedDuringStandardBuild` | Checks the real code. |
| `shouldConfineS3SdkToReceiptStorePutObjectOnlyActuallyFailsAgainstAGenuineViolation` | Imports `RogueS3ObjectMutator` and asserts each of its methods appears in the failure message, including the builder and the presigner dependency. |
| `shouldConfineRawSqlAndEntityManagerWritesToTheExportReadIsCheckedDuringStandardBuild` | Checks the real code. |
| `shouldConfineRawSqlAndEntityManagerWritesToTheExportReadActuallyFailsAgainstAGenuineViolation` | Imports `RogueRawSqlUser` and asserts all 5 method names appear. |
| `shouldConfineRawSqlAndEntityManagerWritesToTheExportReadExclusionIsLoadBearing` | Asserts that `analyzedClasses().get(<TransactionExportRepository FQN>)` has **exactly one** access to `EntityManager.createNativeQuery`. |

### 2. `SECURITY-THREAT-MODEL.md` (repo root)

**Row #5 Status cell**, rewritten in three parts:
- **Prevention:** cites the AC1 tests, the D1, D5 and D2 rules, D3, the `ReceiptStoreTest`
  COMPLIANCE tests, and the T26 E2E.
- **Detection:** cites the `LedgerVerifierTest` suite, the T26 `Intact` check, and the KMS
  signatures.
- **Residual risk**, with each entry labelled by kind and the FU-T28-n references:
  - **Deferred feature:** R26, the on-chain anchor.
  - **On-demand detection only:** `LedgerVerifier` has no production caller.
  - **Platform semantics:** a same-key put creates a new version.
  - **D5 limit:** the export's SQL text is not checked by the rule.
  - **Infrastructure:** real-AWS bucket lock and IAM.

The crypto-service portion is kept verbatim.

**The "Implementing task" cell** adds `payment T19/T20 (ledger), T18 (receipt store), T28
(guards)`.

**Header status line:** add "payment-service T28: row #5 payment portion verified and closed
(2026-10-19)."

**Footnote:** the sentence calling the payment portion "not yet built" is replaced with the
current state. The rest is kept.

## Methods, entities, repositories, services

- **Public API:** none.
- **Private, in tests:** the rules and their conditions.
- **Production:** nothing changes.

## Tests required

The 7 above. Each canary and failing-fixture assertion also serves as its own mutation proof.

**Extra manual mutation (Phase 6):** temporarily narrow D1(a) to calls only, and confirm that
`deleteByMethodReference(` disappears from the failure. This proves the method-reference branch
does the work.

**Expected suite total:** 497 + 7 = **504**.

## Execution order

1. Write the fixtures.
2. Write the three rules and the 7 tests.
3. Run `mvn -o -q test -Dtest=ArchitectureTest`. If ArchUnit's `getAccessesFromSelf` turns out
   not to include method references, use `getMethodReferencesFromSelf()` explicitly, and record
   that.
4. Run the manual method-reference mutation, then restore.
5. Edit `SECURITY-THREAT-MODEL.md`.
6. Run `mvn -o -pl services/payment verify` from the repo root. Expect 504/504.
7. Write `06`, commit and push.
