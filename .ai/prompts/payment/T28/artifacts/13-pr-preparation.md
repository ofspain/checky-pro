# payment · T28 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

This artifact contains no code; it is documentation only.

**Branching:** as with every prior task in this pipeline, the work ran on the long-running
`spec/service-specs-and-ai-framework` branch. `main` was never touched and stays deployable.

**Trailer:** the template names "Claude Opus 4.8 (1M context)". The trailer below names the
model that actually did the work, following the T23–T27 precedent.

## Commit title

`payment T28: threat #5 guards (append-only ledger/receipts, S3 WORM) and threat-model row #5`

## Commit message

```
payment T28: threat #5 guards (append-only ledger/receipts, S3 WORM) and threat-model row #5

Verify SECURITY-THREAT-MODEL.md threat #5 (an insider alters a historical
verification) for payment-service. Back every "no code path" claim with a
committed, mutation-proven test. Three ArchUnit rules are added to
ArchitectureTest.

shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueries
- Bans any delete*/remove* access to a LedgerEntry or Receipt repository.
  Repositories are found by domain type, walking the interface hierarchy.
  Direct calls, lambdas and method references are all caught.
- Bans any delete*/remove* method reference to a Spring Data type. javac
  records the declaring type for method references, so the receiving
  repository can't be identified.
- Bans @Modifying or derived-delete methods on those repositories.

shouldConfineRawSqlAndEntityManagerWritesToTheExportRead
- Bans JdbcTemplate, NamedParameterJdbcTemplate and JdbcClient.
- Bans JDBC Statement.execute* and Query.executeUpdate.
- Bans Hibernate Session and StatelessSession.
- Bans EntityManager remove, merge, createQuery, createNamedQuery and
  createStoredProcedureQuery.
- Allows createNativeQuery only in TransactionExportRepository. A further
  test proves that exemption covers exactly one real access.

shouldConfineS3SdkToReceiptStorePutObjectOnly
- Only ReceiptStore and ReceiptStoreConfig may use the S3 SDK.
- Only ReceiptStore may call S3Client.putObject. The match is exact, and
  the async client is excluded.
- Only ReceiptStoreConfig may build the client.
- Nothing may use any other S3 operation.

Each rule has a real-code check and a failing-fixture check. A further test
pins the guarded repositories to exactly LedgerEntryRepository and
ReceiptRepository.

Update SECURITY-THREAT-MODEL.md row #5:
- add the payment portion as Prevention / Detection / Residual risk, citing
  named tests
- disclose the deferred on-chain anchor (R26)
- disclose that LedgerVerifier has no production caller
- disclose the same-key S3 version semantics
- disclose the infrastructure-controlled S3 and IAM side

The header and the ownership note are corrected too.

Test-only plus documentation; no production code changed.
Full suite: 505/505 (497 baseline + 8).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed

- **`services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`:** three rules,
  their helpers, and 8 new tests. The class goes from 12 tests to 20.
- **New test fixtures:** none is referenced by production code, and all are excluded from the
  real canary.
  - `archtestfixtures/`: `RogueS3ObjectMutator`, `RogueRawSqlUser`.
  - `receipt/ledger/`: `RogueLedgerEntryRepositoryUser`, `RogueModifyingLedgerEntryRepository`,
    `RogueLedgerEntryRepositoryBase`, `RogueIndirectLedgerEntryRepository`.
  - `receipt/`: `RogueReceiptRepositoryUser`, `RogueModifyingReceiptRepository`.
- **`SECURITY-THREAT-MODEL.md`** (repo root, not `spec/`): the header status, row #5's Status and
  Implementing-task cells, and the ownership footnote.
- **Not changed:** production code, migrations, poms, `spec/`, `contracts/`.
- **Pipeline artifacts:** `.ai/prompts/payment/T28/artifacts/00`–`13`, including
  `11-test-review-resolution.md`.

## Summary

T28 closes the payment-service half of threat #5. Before it, the claims "no code path can
UPDATE/DELETE `ledger_entries` or overwrite a receipt" rested on two things: the DB grant, which
was tested, and a manual grep. Now three independently tested layers back them:

1. **Database:** `payment_app` holds only INSERT and SELECT.
2. **Code path:** ArchUnit bans every repository, raw-SQL and S3 route around that.
3. **Platform:** S3 Object Lock in COMPLIANCE mode, shown on LocalStack.

`LedgerVerifier` adds detection on top.

**Two findings changed the rules' shape:**
- **Method references.** A `javac`/`javap` probe showed that `repo::delete` is recorded as
  `CrudRepository.delete`, the declaring type. A rule keyed on the repository class would have
  silently missed it.
- **Derived deletes.** Spring Data's `removeBy…` derived deletes need no `@Modifying`, so they
  would have slipped past the first D1 rule.

**Human decisions:**
- **Phase 4:** add guards rather than only update the doc; add a raw-SQL rule; cover the whole S3
  SDK package; disclose the same-key re-put; restructure row #5.
- **Phase 9:** accept the three forced deviations; widen D1 and D5 (`removeBy`, the hierarchy
  walk, Hibernate sessions, `JdbcClient`, `Query.executeUpdate`, named and stored queries) and
  D2 (no other S3 class, even inside the store).
- **Phase 11:** `putObject` allowed only from `ReceiptStore` and never async; receipt-side
  fixtures; a test pinning the real guarded set.

Kimi's findings were checked against source at every gate. All of them were accurate in T28.
Phase 11 Gaps 6 and 7 were rejected: the mechanism was already proven, and the doc parser would
have been brittle.

**Open follow-ups (human-owned, out of T28 scope):**
1. **FU-T28-1.** A conditional `If-None-Match: *` put, so S3 refuses a same-key receipt write.
2. **FU-T28-2.** A production trigger for `LedgerVerifier` (a scheduled job or an ops endpoint),
   with alerting.
3. **FU-T28-3.** R26, the on-chain anchor (task 21, deferred), with interim DB-to-S3 digest
   reconciliation.
4. **Infrastructure.** Real-AWS bucket Object Lock and IAM.
5. **Earlier follow-ups, still open:**
   - **FU-T27-1:** the sibling Dockerfiles.
   - **FU-T27-2:** the hard-coded `jwk-set-uri`.
   - **G2:** partition-key drift.
   - **FU-1 and FU-2.**
   - **Crypto-service:** `unregisterWatch` stops tracking matched transactions.

## Testing performed

- **Full suite.** `mvn -o -pl services/payment verify` from the repo root passes **505/505**
  with `BUILD SUCCESS`. It was run at Phase 6 (504), Phase 9 (504) and Phase 11 (505). Class
  results:

  | Class | Result |
  |---|---|
  | `ArchitectureTest` | 20/20 |
  | `SchemaBaselineMigrationIntegrationTest` | 22/22 |
  | `LedgerVerifierTest` | 15/15 |
  | `LedgerServiceTest` | 7/7 |
  | `ReceiptStoreTest` | 12/12 |
  | `PaymentEndToEndIntegrationTest` | 3/3 |

- **Mutation proofs (14).** Each was temporary and restored, confirmed with `cmp`. 13 failed on
  their intended test:

  | Phase | Mutations |
  |---|---|
  | 6 | M1 method-reference branch; M2 export exemption; M3 builder exemption; M4 `putObject`; M5 guarded detection |
  | 9 | M6 `remove` prefix; M7 hierarchy walk; M8 builder chain; M9 Hibernate session; M10 `JdbcClient`; M11 `executeUpdate` |
  | 11 | M12 `Receipt` typo; M13 `putObject` from any class |

  M2, M3, M4 and M8 failed the real-code canaries, which proves the rules see the real
  `TransactionExportRepository`, `ReceiptStore` and `ReceiptStoreConfig` code. M14 (the async
  owner) can't be proven by a fixture, because no fixture can occupy `ReceiptStore`'s name. It is
  enforced by construction, and explained in `11`.

- **Violation messages were printed and inspected.** This exposed that the original D2 fixture
  assertions were confounded by the dependency check, so part (b) is now asserted on its own.
- **Probe.** A throwaway `javac`/`javap` probe in the scratchpad confirmed the declaring-type
  encoding of method references.
- **Doc check.** Every test name cited in row #5 was confirmed with `grep "void <name>("` against
  `services/*/src/test`.

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 28 (`:49`).
- **Requirements:**
  - verified: R21, R22, R23
  - R26: deferred and disclosed
- **LOCKED decisions honoured:** L7 (append-only ledger) and L9 (WORM receipts), both now
  structurally enforced; L12 (module boundaries, for the fixtures).
- **Checklist:** `package.md:120`, `:121` and `:127`.
- **Threat model:** `SECURITY-THREAT-MODEL.md` row #5, and `ARCHITECTURE.md` §6.5 and §6.7.
