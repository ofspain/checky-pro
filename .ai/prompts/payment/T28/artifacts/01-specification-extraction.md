# payment · T28 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

**Task 28** (`tasks.md:49`): check payment-service against `SECURITY-THREAT-MODEL.md` threat #5,
"insider alters a historical verification". Verify that it is defeated by the append-only ledger
plus S3 WORM, and confirm that no code path can UPDATE/DELETE `ledger_entries` or overwrite a
receipt object.

## Business Rules

| ID | Rule (one sentence) |
|---|---|
| R21 | When a receipt is issued, append exactly one ledger entry whose `prev_hash` is the current head's `entry_hash`, and advance the head. |
| R22 | When the ledger is verified, recompute every entry hash from payload + `prev_hash`, and report any entry whose hash or linkage does not match. |
| R23 | When a receipt is issued, store the signed JSON and PDF in the S3 Object Lock bucket in compliance mode, and **never overwrite an existing receipt object**. |
| R26 | Anchor the ledger head on-chain daily. **DEFERRED** (Q4, 2026-10-11; `tasks.md` task 21 skipped). This is the third leg of row #5's stated mitigation (`ARCHITECTURE.md` §6.5). It is not built, and must be **disclosed, not silently dropped** (`package.md:127`). |

`package.md` §9 checklist items this task directly certifies:
- `:120`: "The hash chain verifies head-to-tail after a full register→finalize→attest flow;
  tampering with any row is detectable."
- `:121`: "Receipts land in the S3 Object Lock bucket in compliance mode; no code path can
  overwrite an existing receipt object."

## Locked Decisions

- **L7 (append-only hash-chain ledger).** No `UPDATE`/`DELETE` ever. The service DB role has
  `INSERT`+`SELECT` only. `entry_hash` = SHA-256(canonical payload ‖ `prev_hash`), with a genesis
  of 64 zeros (`design.md:11`).
- **L9 (receipts are WORM).** Receipts are written once in compliance mode. **There is no
  overwrite or delete path in application code.** The key embeds `receiptUuid`, and a second
  write to the same key "is a bug, not an update" (`design.md:13`).
- **L12 (module boundaries, ArchUnit).** This constrains *how* a structural guard would be
  written, if one is added. It mirrors `ArchitectureTest`'s existing conventions.
- **Not modified:** L1–L6, L8, L10, L11, L13 and L14 are not touched by this task.

## Files involved

**Read and verify (existing):**
- **Migrations:**
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (`ledger_entries`
    DDL `:85-92`; `receipts` DDL)
  - `V2__payment_app_role_and_grants.sql:40`
  - `V9__payment_app_receipts_grant.sql:4`
  - The remaining `V3`–`V8` and `V10` grants, to prove that none widens access on the three
    append-only tables.
- **`receipt/ledger/`:** `LedgerEntry.java` (`@Immutable`), `LedgerEntryRepository.java`,
  `LedgerService.java`, `LedgerVerifier.java`.
- **`receipt/`:** `Receipt.java`, `ReceiptRepository.java`, `ReceiptService.java`,
  `ReceiptStore.java`, `ReceiptStoreConfig.java`.
- **Tests:**
  - `SchemaBaselineMigrationIntegrationTest.java`
  - `LedgerServiceTest.java`, `LedgerVerifierTest.java`
  - `ReceiptStoreTest.java`, `ReceiptServiceTest.java`
  - `PaymentEndToEndIntegrationTest.java`
  - `ArchitectureTest.java`

**Expected to change (the spec names only the doc):**
- `SECURITY-THREAT-MODEL.md` (repo root, **not** `spec/`). Row #5's Status and the
  footnote prose currently say the payment portion is "not yet built". This follows crypto T28's
  precedent, and ARCHITECTURE §6.7 says "every feature PR either updates this document or states
  why no update is needed".
- Whether test files are added or changed (for example, a structural no-mutation guard) is **not
  specified by the spec**. That is a Phase 2 decision; see Open Questions.

**Not modified:** `spec/**`, `contracts/**`, production code (unless a real defect is found, and
then only with human approval).

## Dependencies

- **Entities and repositories:** `LedgerEntry`, `LedgerEntryRepository`, `Receipt`,
  `ReceiptRepository`.
- **Services:** `LedgerService`, `LedgerVerifier`, `ReceiptService`, `ReceiptStore`.
- **DB roles and grants:** owner `checky` (the migration role), grantee `payment_app` (the runtime
  role).
- **S3:** `S3Client` (constructed in `ReceiptStoreConfig`), with config keys under
  `ReceiptProperties` (`bucket`, `prefix`, `region`).
- **Test infrastructure:**
  - Testcontainers `postgres:16-alpine`
  - LocalStack 3.8 with an Object Lock bucket
  - ArchUnit, in the plain-`@Test` canary style (because of the Surefire `@ArchTest` bug)
- **Contracts:** none. No API or event shape is involved.

## Acceptance Criteria

| AC | Criterion | Maps to |
|---|---|---|
| AC1 | **DB layer.** `payment_app` cannot `UPDATE`, `DELETE` or `TRUNCATE` `ledger_entries` or `receipts`. Its privilege set on each is exactly `{INSERT, SELECT}`, and no later migration widens it. Proven by a named, passing test. | L7, L9, R21, R23 |
| AC2 | **Code-path layer.** No `src/main` code path issues an `UPDATE` or `DELETE` against `ledger_entries` or `receipts`. That rules out repository `delete*` calls, `@Modifying` JPQL or native statements on those entities or tables, and `JdbcTemplate` writes to them. Both entities are inserted only as new instances. | L7, L9; task text "no code path" |
| AC3 | **S3 layer.** No code path deletes, copies over, or re-puts an existing receipt object. Every put carries `COMPLIANCE` mode plus a retain-until date. A locked version cannot be deleted (shown against LocalStack). | R23, L9; `package.md:121` |
| AC4 | **Detection.** Tampering with any ledger row (payload, `entry_hash` or `prev_hash`) is detected by `LedgerVerifier`. An untampered chain verifies head-to-tail after a full flow. | R22; `package.md:120` |
| AC5 | **Threat-model doc.** `SECURITY-THREAT-MODEL.md` row #5 records the payment-service portion with named, currently-passing tests. Every non-closed leg is disclosed explicitly: the on-chain anchor (R26, deferred) and the infrastructure-controlled S3/IAM side. | Task text; R26 disclosure (`package.md:127`) |
| AC6 | `mvn -pl services/payment verify` stays green. The baseline is 497. | Task 27 continuity |

## Tests required

**Existing named tests that already exist and that this task cites:**

| Test | Covers |
|---|---|
| `SchemaBaselineMigrationIntegrationTest.paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheGrantedTables` | AC1, functional |
| `SchemaBaselineMigrationIntegrationTest.grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore` | AC1, exact privilege set |
| `LedgerServiceTest.shouldAppendLedgerEntryWithPrevHashChainingToHead` | R21 |
| `LedgerVerifierTest.shouldDetectBrokenHashChainOnVerification` | R22, AC4 |
| `ReceiptStoreTest.shouldStoreReceiptInS3ObjectLockWorm` | R23 |
| `ReceiptStoreTest.shouldNeverCallDeleteOrAnyOverwriteApi` | AC3, unit |
| `PaymentEndToEndIntegrationTest.shouldDriveInvoiceToAttestedReceiptEndToEnd` | AC3: COMPLIANCE retention plus a versioned DELETE returning 403. AC4: `LedgerVerifier` reports `Intact` after a full flow. |

**Boundary checks the task text implies, which may currently be missing (Phase 2 to decide):**
- **AC2 has no committed guard today.** The proof is a Phase 0 grep. A structural (ArchUnit)
  rule would need to ban calls to `delete*`/`deleteAll*` on `LedgerEntryRepository` and
  `ReceiptRepository`, and `@Modifying` queries on those repositories. It would follow the
  `…IsCheckedDuringStandardBuild` / `…ActuallyFailsAgainstAGenuineViolation` canary pattern.
- **AC1 completeness.** Tamper scenarios involving `TRUNCATE` are covered implicitly by the
  exact-privilege-set check. Whether to name that explicitly is a judgement call.
- **AC3, the same-key re-put.** No test shows that a re-put cannot happen through code. It is
  guaranteed structurally by a fresh `receiptUuid` per `attest()`.

## Open Questions

None of these blocks the spec (`package.md` §11 Q1–Q8 are all closed). All three are scope or
interpretation choices for the Phase 2 brief and the Phase 4 human gate:

1. **Verification only, or verification plus a guard?** The task says "verify … confirm".
   Crypto T28 only cited tests and updated the doc. AC2 currently has no committed test, so a
   doc citing "no code path" would rest on a one-off grep.
2. **What "overwrite a receipt object" means.** On a versioned Object Lock bucket, a same-key
   `putObject` adds a *new version*, and the locked original survives. L9 frames such a write as
   a bug that the application prevents structurally (by a fresh `receiptUuid`), not as something
   the platform rejects. Should the doc say exactly that? Or does the task want a platform-level
   refusal, such as a conditional put with `If-None-Match: *`? That would be a production code
   change, outside a verification task.
3. **The residual risks to disclose in row #5:**
   - The on-chain anchor is deferred (R26).
   - An insider with owner or DBA rights, rather than `payment_app`, can rewrite the ledger with
     consistent hashes, and `LedgerVerifier` cannot detect that without the anchor. The S3 WORM
     originals and the KMS signatures still bear witness.
   - Nothing in `src/main` calls `LedgerVerifier`: it has no scheduled or on-demand trigger.

   Recommendation: disclose all three without fixing them, because each fix is a feature (R26,
   or a verification job), not a T28 check.
