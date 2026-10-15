# payment · T19 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`, `artifacts/11-test-review-resolution.md`

## Traceability matrix

| Requirement/Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R19** (fully assembled) — compute the digest, call `/attest`, never sign locally | Yes | `ReceiptService.java:111` (`issueIfEligible`, unchanged from T15/T17) | `shouldRequestKmsSignatureViaCryptoAttestEndpoint` (named, T17) | No | No |
| **R21** — append one ledger entry whose `prev_hash` is the current head's `entry_hash`, advance the head | Yes | `LedgerService.append` | `shouldAppendLedgerEntryWithPrevHashChainingToHead` (named), `shouldComputeEntryHashAsShaTwoFiftySixOfCanonicalPayloadConcatenatedWithPrevHash`, `shouldComputeSecondEntryHashUsingTheRealPredecessorHashNotGenesis`, `shouldAppendTheRealSeventeenFieldLedgerPayloadShape` | No | No |
| **R23** (fully assembled) — store the signed receipt in S3 Object Lock, no overwrite | Yes | `ReceiptStore.store` (T18, unchanged), first real caller via `persistSignedReceipt` | `shouldStoreReceiptInS3ObjectLockWorm` (named, T18) | No | No |
| **R24** — payment transitions to `ATTESTED` once signed, stored, and ledger-appended | Yes | `ReceiptService.java:160-193` (`persistSignedReceipt`), `PaymentRepository.markAttestedIfFinalized` | `shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended` (named), `markAttestedIfFinalizedShouldUpdateStateAndSetAttestedAt` (real DB) | No | No |
| **R25** — `receipt.issued` published in the same transaction as the ledger append | Yes | `ReceiptService.java:181-185` (same `transactionTemplate.execute` block as the `Receipt`/`LedgerEntry` inserts and the `ATTESTED` transition) | `shouldEmitReceiptIssuedAfterAttestation` (named) | No | No |
| **L2** — `ATTESTED` set only after the signature is obtained, the receipt is stored in S3, and the ledger entry is appended, all durably | Yes | the full `persistSignedReceipt` sequence; aborts on any failure anywhere in that chain | `shouldAbortAndReturnFailedWhenMarkAttestedIfFinalizedReturnsZeroRows`, `shouldReturnFailedWhenReceiptStoreThrows`, `shouldReturnFailedWhenLedgerServiceThrows` | No | No |
| **L7** — `ledger_entries` is append-only, no `UPDATE`/`DELETE` ever | Yes | `LedgerEntry`'s `@Immutable` annotation (Phase 11 — also the fix for the severe bug below), `V9` grants only `INSERT, SELECT` on `receipts`, V2 already granted the same on `ledger_entries` | `SchemaBaselineMigrationIntegrationTest.paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheGrantedTables` (real, functional, as `payment_app`) | No | No |
| **L12** (continuity) — no cross-module entity import | Yes | `Receipt`/`LedgerEntry` both use scalar `Long` FKs, never `@ManyToOne`; confirmed by `ArchitectureTest.FEATURE_MODULES` including `"receipt"` | `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. `LedgerService.append` is real and correct; `ReceiptService
.persistSignedReceipt` wires the full S3 → `Receipt` → `LedgerEntry` → `ATTESTED` →
`receipt.issued` sequence atomically. The attestation flow the T15-T18 group built in isolation
now runs end to end for the first time.

**(2) Does it satisfy every acceptance criterion (frozen brief, Phase 4)?** All seven (AC1-AC7,
including the Phase 4 amendment AC6/AC7) are satisfied: AC1 (full persistence), AC2 (hash-chain
correctness, now empirically proven against a real Postgres with the real 17-field shape), AC3
(same-transaction event publish), AC4 (the advisory lock's correctness is argued from Postgres's
own documented semantics, per the explicit Phase 9/10 testing-strategy decision — not re-proven
under synthetic concurrent load), AC5 (no change to `ReceiptStore`'s own guarantees), AC6
(abort-on-zero-rows), AC7 (frozen `ReceiptIssuedPayload` fields, now all 11 individually
asserted).

**(3) Does it violate any LOCKED decision?** No. L2/L7/L12 all hold, confirmed above. L9
(continuity, T18) holds — no change to `ReceiptStore`'s own no-overwrite guarantee.

**(4) The single most important fact about this task's own closure**: a genuine, severe
production defect was found and fixed during Phase 11 — not a theoretical risk, a guaranteed
failure on every real attestation. `LedgerEntry`'s `@JdbcTypeCode(SqlTypes.JSON)` payload field
triggered Hibernate's own dirty-checking to spuriously re-UPDATE the row immediately after every
correct `INSERT` (a boxed `Short` — `tokenDecimals`, always present in the real payload — comes
back as a different boxed type on Hibernate's internal JSON round-trip snapshot comparison,
failing `.equals()` and triggering a false-positive dirty flag), which `payment_app`'s own
intentional no-`UPDATE` grant on `ledger_entries` then rejected. Confirmed via a controlled
before/after experiment, not inferred. Fixed with `@Immutable` on `LedgerEntry` — both the actual
fix and the semantically correct annotation regardless, since L7 already says "no UPDATE/DELETE
ever." This also retroactively explains the Phase 6/7 "bare `ExecutorService` thread" anomaly
that had been disclosed as unroot-caused — it was never about threads.

**(5) Remaining risks (carried forward, not blocking this task's own PASS):**
- **AC4's full concurrency guarantee under genuine Spring-managed concurrent load** is argued
  from documented Postgres semantics, not re-proven empirically under synthetic concurrency — a
  deliberate, explicit Phase 9/10 scope decision, deferred to task 26's own end-to-end test.
- **`VerificationRecord.detail` carries the identical latent `@JdbcTypeCode(SqlTypes.JSON)` risk**
  as `LedgerEntry` did, currently un-triggered only because every existing caller happens to use
  `Integer` values (which round-trip correctly). Disclosed, not fixed — no demonstrated need,
  out of this task's own scope.
- **`markHeldIfFinalized`'s own JPQL (T17)** has never been exercised against a real database
  either — the identical class of gap `markAttestedIfFinalized` just closed for its own method.
- The S3-orphan-window risk (T18/T19, L9) remains unsolved in application code, by design.
- `CanonicalJson`'s own genuine reusability (T16) is now confirmed twice over (T18's S3 JSON,
  T19's ledger payload) — no longer a disclosed risk, closed by this task's own real usage.

## Verdict

**PASS** — `LedgerService`/`ReceiptService` together fully implement R21/R23-R25/L2/L7, correctly
chain the hash ledger, atomically persist the full attestation outcome, and — critically — do so
*without* the severe, guaranteed-to-fail-in-production defect this task's own Phase 11 testing
work found and fixed before it ever shipped. `LedgerServiceTest`: 7/7. `ReceiptServiceTest`: 21/21.
`PaymentRepositoryIntegrationTest`: 2/2. `ReceiptRepositoryIntegrationTest`: 1/1. Full
payment-service suite: **405/405 passing**.
