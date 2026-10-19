# payment · T28 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Check payment-service against `SECURITY-THREAT-MODEL.md` threat **#5**, "insider alters a
historical verification", and confirm three things:
- no code path can `UPDATE`/`DELETE` `ledger_entries` (or `receipts`)
- no code path can overwrite a receipt object
- what remains undefended is disclosed

Then record the payment-service portion of row #5.

## Purpose

Row #5 is currently "closed (crypto-service portion)". Its text says the ledger half is "Payment
Service … not yet built". This task is the final security gate before T29's spec bump. Every
"no code path" claim in the doc must rest on a committed, currently-passing test, not on a
one-off grep.

## Scope

**In:**
- **Verify** AC1–AC4 against the existing tests, run and passing.
- **Add structural guards** for the two claims that today have no committed proof: "no code path
  mutates the ledger or receipts" and "no code path deletes or overwrites S3 objects".
- **Update row #5** of `SECURITY-THREAT-MODEL.md`, and its prose.

**Out:**
- **Any production-code change.** That includes a conditional `If-None-Match` put (OQ2) and a
  `LedgerVerifier` scheduler or endpoint.
- **R26 / task 21**, the on-chain anchor, which is deferred.
- **Real-AWS bucket and IAM configuration**, which belongs to the infra workstream.
- **Rows #1–#4 and #6–#8.**
- **`spec/` and `contracts/`.**

## Business Rules

- **R21:** a receipt appends exactly one ledger entry, chained to the head.
- **R22:** verification recomputes every hash and reports any mismatch.
- **R23:** receipts are stored in compliance mode and never overwritten.
- **R26:** the on-chain anchor is **deferred**. It must be disclosed, not dropped.

## Locked Decisions

- **L7:** `ledger_entries` is append-only. `payment_app` has `INSERT`+`SELECT` only.
- **L9:** receipts are WORM. There is no overwrite or delete path in application code, and a
  same-key write is a bug.
- **L12:** any new guard follows `ArchitectureTest`'s conventions.

**Resolved directly** (flagged for the Phase 4 gate):

| # | Decision | Basis |
|---|---|---|
| D1 | **Ledger and receipt mutation guard.** A new ArchUnit rule, `shouldForbidLedgerAndReceiptMutation`, in `ArchitectureTest`. **(a)** No class may call any method named `delete*` on `LedgerEntryRepository` or `ReceiptRepository`. Inherited `JpaRepository` methods are included, since bytecode records the static receiver type. **(b)** Neither repository may declare a `@Modifying` method. Plain `@Test` canaries go with it: `…IsCheckedDuringStandardBuild`, plus `…ActuallyFailsAgainstAGenuineViolation` over an `archtestfixtures/` class. That class calls `delete(…)` on a repository typed like the real ones, and declares a `@Modifying` method. | AC2 has no committed proof today (Phase 0 §5.1). The doc must cite a test. Same pattern as T25. |
| D2 | **S3 operation guard.** A new ArchUnit rule, `shouldOnlyPutReceiptObjects`. Only `ReceiptStore` may call `S3Client` methods, and the only method it may call is `putObject`. That bans `deleteObject(s)`, `copyObject`, `putObjectRetention`, `putObjectLegalHold`, `restoreObject` and the rest, anywhere in `src/main`. Canaries are the same as D1. | `ReceiptStoreTest.shouldNeverCallDeleteOrAnyOverwriteApi` mocks only one call path and checks only two methods. A codebase-wide rule makes L9's "no overwrite or delete path in application code" structurally true. |
| D3 | **No re-save-as-update guard.** None is needed: `Receipt` and `LedgerEntry` have no setters or mutators (only `create(…)` and getters), and `LedgerEntry` is `@Immutable`. A loaded instance cannot be changed, so `save` can never produce an `UPDATE`. This is recorded as evidence, not tested. | Phase 2 source read. |
| D4 | **Doc update.** Row #5 becomes `closed`. It cites the named tests for the DB, code-path, S3 and detection layers. Residual risks are labelled distinctly, the way crypto T28 labelled row #3 and row #4: <br>• **Deferred feature:** the anchor (R26). Without it, an insider with owner or DBA rights (not `payment_app`) could rewrite the whole chain with consistent hashes. The S3 originals and KMS signatures still bear witness, but nothing automatically cross-checks them. <br>• **Infrastructure-controlled:** bucket-level Object Lock and IAM in real AWS. <br>• **Undefined trigger:** `LedgerVerifier` has no production caller. <br>• **Platform semantics:** a same-key re-put creates a new version, and the locked original survives. It is prevented by structure (a fresh `receiptUuid`, plus D2), not refused by S3. <br>The header status line is updated as well. | AC5; `package.md:127` disclosure rule; ARCHITECTURE §6.7. |

## Dependencies

- **Code under guard:** `LedgerEntryRepository`, `ReceiptRepository`, `ReceiptStore`, `S3Client`
  (AWS SDK v2).
- **Tooling:** ArchUnit, in the plain-`@Test` style forced by the Surefire 3.5.3 `@ArchTest` bug.
- **Cited tests:**
  - `SchemaBaselineMigrationIntegrationTest`
  - `LedgerServiceTest`, `LedgerVerifierTest`
  - `ReceiptStoreTest`
  - `PaymentEndToEndIntegrationTest`

## Inputs

The current `src/main` bytecode, which is what ArchUnit analyses, plus the existing test suite.

## Outputs

- The new ArchUnit rules and their canaries.
- One or two fixture classes.
- The updated row #5.
- Gate evidence: the full-suite run, and canary failure messages.

## State Changes

None. No schema, data or runtime behaviour changes.

## Files to Create

`services/payment/src/test/java/com/themistra/payment/archtestfixtures/`: the fixture class(es)
for the D1 and D2 canaries. Names are fixed in Phase 5.

## Files to Modify

- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`: two rules plus
  their canaries.
- `SECURITY-THREAT-MODEL.md` (repo root): row #5, its footnote prose, and the header status
  line.

## Files NOT to Modify

- `services/payment/src/main/**`
- every migration
- both poms
- every other test class
- `spec/**`, `contracts/**`
- other services
- `SECURITY-THREAT-MODEL.md` rows other than #5

## Acceptance Criteria

| AC | Criterion | How it is proven |
|---|---|---|
| AC1 | **DB layer.** `payment_app` holds exactly `{INSERT, SELECT}` on `ledger_entries` and `receipts`, and a real `UPDATE`/`DELETE` attempt is denied. | `SchemaBaselineMigrationIntegrationTest.paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheGrantedTables` and `.grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore`, both passing. |
| AC2 | **Code-path layer.** No main-code call to `delete*` on either repository, and no `@Modifying` method on either. | D1 rule, plus canaries that fail on the fixture. |
| AC3 | **S3 layer.** Only `ReceiptStore` touches `S3Client`, and only through `putObject`. Puts are `COMPLIANCE` with a retain-until date. A locked version can't be deleted. | D2 rule plus canaries; `ReceiptStoreTest.shouldStoreReceiptInS3ObjectLockWorm` and `.shouldNeverCallDeleteOrAnyOverwriteApi`; T26 E2E (versioned DELETE returns 403). |
| AC4 | **Detection.** Tampering with a payload, `entry_hash`, `prev_hash`, the head, or a deleted middle entry is reported. An untampered full flow returns `Intact`. | The `LedgerVerifierTest` suite (`shouldDetectBrokenHashChainOnVerification` and its siblings) and the T26 E2E. |
| AC5 | **Doc.** Row #5 states the payment portion with test names, and makes every D4 disclosure. | Review of the diff. |
| AC6 | **Suite.** `mvn -pl services/payment verify` passes. The total is 497 plus the new tests. | A full run. |

## Required Tests

- **New, D1:**
  - `shouldForbidLedgerAndReceiptMutationIsCheckedDuringStandardBuild`
  - `shouldForbidLedgerAndReceiptMutationActuallyFailsAgainstAGenuineViolation`
- **New, D2:**
  - `shouldOnlyPutReceiptObjectsIsCheckedDuringStandardBuild`
  - `shouldOnlyPutReceiptObjectsActuallyFailsAgainstAGenuineViolation`
- **Cited:** the existing tests under AC1, AC3 and AC4. All must be passing in the AC6 run.

## Constraints

- **Security.** Guards must not be bypassable in an obvious way. For example, they must cover
  inherited repository methods, and lambdas, as T25 Gap 3 did. Fixtures live only in the test
  `archtestfixtures` package, which the main rules exclude, matching the existing fixture
  convention.
- **Module boundaries.** Fixtures must not import production entities across modules in a way
  that trips the existing L12 rule.
- **Determinism.** These are pure bytecode analysis, with no Docker dependency. The cited
  integration tests need Docker, which is available.
- **Thread-safety, transactions, null handling:** not applicable, since the task adds no runtime
  code.

## Open Questions

No blockers. Two items need the human's disposition at Phase 4:

1. **D1/D2 vs. doc-only.** Should T28 add the two structural guards, or only verify and update
   the doc, as crypto T28 did?
   - **Recommendation:** add them. Otherwise the doc's "no code path" claim rests on a grep.
2. **Same-key re-put.** On a versioned bucket, a re-put creates a new version.
   - **Recommendation:** disclose it, and rely on D2 plus the fresh `receiptUuid`.
   - **Alternative:** a conditional `If-None-Match` put. That would need a production-code
     change, which is out of scope for a verification task, so it would be logged as a follow-up.
