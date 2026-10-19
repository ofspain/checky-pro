# payment · T28 · Phase 9 — Review Resolution

Consumes:
- `artifacts/07-self-review.md` (S1–S7)
- `artifacts/08-independent-review.md` (Kimi F1–F8)
- the Phase 6 deviations 1–3 (`06`)

**Human sign-off:** 2026-10-19, at the Phase 9 gate, in two rounds of questions. Every
recommended option was chosen.

## Verification

Each finding was checked against source before the gate.
- **All eight Kimi findings were accurate.**
- **F1, F2, F3 and F7** duplicate self-review S1, S3, S2 and S5.
- **F4 to F6** were new and correct.
- **Unused today:** a grep of `src/main` found no use of `org.hibernate.Session`,
  `StatelessSession`, `JdbcClient`, `createNamedQuery`, `unwrap(` or `executeUpdate`. The only
  `Query` use is `getResultList` in `TransactionExportRepository:65`. So none of the widened bans
  can cause a false positive.

## Resolution log

| Comment | Decision | Change made |
|---|---|---|
| **Deviation 1** (`06`): method-reference deletes banned for every Spring Data repository | **ACCEPTED** | None; already in place. S4 (over-breadth) is accepted as the documented trade-off. |
| **Deviation 2**: guarded repositories found by domain type | **ACCEPTED** | None; already in place. |
| **Deviation 3**: D1 fixtures placed in the test `receipt.ledger` package | **ACCEPTED** | None. |
| **S1 / F1**: `removeBy…` derived deletes evade D1 | **ACCEPTED** | New helper `isDeleteName`, matching a `delete` or `remove` prefix, used by D1(a). D1(b) now also flags any guarded-repository method *declared* with that prefix, reported as "is a derived delete". New fixtures `RogueLedgerEntryRepositoryBase` and `RogueIndirectLedgerEntryRepository` (which declares `removeByReceiptId`), plus `RogueLedgerEntryRepositoryUser.removeThroughADerivedQuery`. |
| **F6**: guarded-repository detection reads only direct super-interfaces | **ACCEPTED** | New recursive helper `hasGuardedEntityTypeArgument`. A parameterised super-interface whose first type argument is guarded ends the search; any other interface is searched further up. Proven by the indirect fixture above. |
| **S2 / F3**: Hibernate `Session` and named/stored queries | **ACCEPTED** | D5 bans any access to `org.hibernate.SharedSessionContract`, the supertype of `Session` and `StatelessSession`. `ENTITY_MANAGER_BANNED_METHODS` is now `remove`, `merge`, `createQuery`, `createNamedQuery` and `createStoredProcedureQuery`. |
| **S3 / F2**: `JdbcClient` | **ACCEPTED** | D5 bans any access to `org.springframework.jdbc.core.simple.JdbcClient`. |
| **F4**: `Query.executeUpdate` | **ACCEPTED** | D5 bans `jakarta.persistence.Query.executeUpdate`. |
| | | The `RogueRawSqlUser` fixture gains `jdbcClientUpdate`, `hibernateMutationQuery`, `statelessSessionDelete`, `namedAndStoredProcedureQueries` and `executeAPassedInQuery`. |
| **F5**: D2 allowed other S3 classes inside `ReceiptStore`/`ReceiptStoreConfig` | **ACCEPTED** | D2 part (b) now covers every access to the S3 SDK outside `s3.model`, in every class. The only allowed accesses are: <br>• `putObject` on `S3Client` / `S3AsyncClient` <br>• from `ReceiptStoreConfig` only: `S3Client.builder()` and the `S3ClientBuilder` chain <br>The fixture assertions now include `S3ClientBuilder.build()` and `S3Presigner.close()`. |
| **S5 / F7**: stale "Threats #1–#6 owned by crypto-service" | **ACCEPTED** | `SECURITY-THREAT-MODEL.md` now says "#1–#4 and #6 … crypto-service; #5 is shared with payment-service (ledger and receipts, task 28)". Row #5's Prevention (2), (3) and (5) were also updated to describe the widened rules. |
| **S6**: bare `FU-T28-1` ID in the Javadoc | **ACCEPTED** | Replaced with "Making S3 refuse it (a conditional `If-None-Match` put) is a logged follow-up". |
| **S7**: the exact-match `putObject` isn't documented | **ACCEPTED** | The D2 Javadoc now lists `putObjectRetention`, `putObjectLegalHold`, `putObjectAcl` and `putObjectTagging` as banned. |
| *(housekeeping)* | | D2's Javadoc block had ended up above the part (a) condition, giving two stacked Javadocs. It was moved back onto the rule. No behaviour change. |
| **F8**: source-text check that the export SQL is read-only | **REJECTED** | None. The export reads `payments`/`invoices`, not ledger or receipts. `payment_app`'s grants already bound it, and row #5's residual "rule limit" item discloses it. |
| **S4**: method-reference ban is over-broad | Accepted as the trade-off under deviation 1 | None. |

## Files changed in Phase 9

- **`ArchitectureTest.java`:**
  - helpers `isDeleteName`, `hasGuardedEntityTypeArgument` and `ENTITY_MANAGER_BANNED_METHODS`
  - D1, D2(b) and D5 widened
  - fixture assertions extended
  - Javadoc updated
  - It still has 19 tests: no test methods were added.
- **New test fixtures:**
  - `receipt/ledger/RogueLedgerEntryRepositoryBase.java`
  - `receipt/ledger/RogueIndirectLedgerEntryRepository.java`
- **Extended test fixtures:** `RogueLedgerEntryRepositoryUser`, `RogueRawSqlUser`.
- **`SECURITY-THREAT-MODEL.md`:** the ownership sentence, plus row #5 Prevention (2), (3) and (5).
- **Unchanged:** production code, migrations and poms.

## Verification

- `mvn -o -q test -Dtest=ArchitectureTest` gives **19/19** on the real code, including the
  tightened D2.
- **Mutation proofs.** Each was temporary. The restore was confirmed with `cmp`, and a clean
  re-run returned exit 0.

| # | Mutation | Failing test |
|---|---|---|
| M6 | `remove` prefix dropped | D1 `…ActuallyFails…` |
| M7 | Hierarchy walk disabled | D1 `…ActuallyFails…` |
| M8 | Config builder-chain allowance removed | D2 **`…IsCheckedDuringStandardBuild`**. The real `ReceiptStoreConfig` builder chain is seen. |
| M9 | Hibernate session ban off | D5 `…ActuallyFails…` |
| M10 | `JdbcClient` ban off | D5 `…ActuallyFails…` |
| M11 | `executeUpdate` ban off | D5 `…ActuallyFails…` |
| M1 (re-run) | Method-reference branch off | D1 `…ActuallyFails…` |
| M4 (re-run) | `putObject` disallowed | D2 **`…IsCheckedDuringStandardBuild`**. The real `ReceiptStore` puts are seen. |

- **Full suite:** `mvn -o -pl services/payment verify` from the repo root: `Tests run: 504, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` in 01:36. The total is unchanged, since no test methods were added.

## Open Questions

None.
