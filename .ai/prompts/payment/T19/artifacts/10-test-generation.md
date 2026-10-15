# payment · T19 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

No production code changed this phase, except one mechanical extension to an existing
integration test file's own shared seeding/assertion infrastructure (below).

## Explicit testing-strategy decision for AC4 (Phase 9's own deferred item)

Per Phase 9's disposition (row 2), this phase makes the explicit choice Kimi's own Phase 8
Finding 3 asked for rather than silently skipping it: **AC4's concurrency guarantee is argued
from `pg_advisory_xact_lock`'s own documented, transaction-scoped Postgres semantics (now
explicit in `LedgerService`'s own Javadoc, Phase 9), not re-proven under synthetic concurrent
load in this test suite.** A real attempt at a representative concurrent test (two genuine
Spring-managed transactions racing on `append`) was judged disproportionate for this task's own
scope — the only way to exercise genuinely representative production concurrency is two real
Kafka listener threads processing two different `chain.tx.finalized` messages simultaneously,
which belongs with a future end-to-end test (`tasks.md` task 26), not a unit-level addition here.
`LedgerServiceTest` instead proves the sequential chaining property rigorously against a real
Postgres, which is what the three named tests actually require.

## Test manifest (`LedgerServiceTest`, new class — 4 methods, real Testcontainers)

| Test | Verifies |
|---|---|
| `shouldAppendLedgerEntryWithPrevHashChainingToHead` (named) | AC2/R21 — genesis `prevHash` = 64 zeros, each subsequent entry's `prevHash` equals its predecessor's real `entryHash`, across three real sequential inserts |
| `shouldComputeEntryHashAsShaTwoFiftySixOfCanonicalPayloadConcatenatedWithPrevHash` | AC2 — an independent `MessageDigest` call ties the real `entryHash` to its own expected value, mirroring T16's own Phase 11 golden-digest-tie precedent; mutation-verified (see below) |
| `shouldPersistThePayloadAsStoredJsonb` | the JSONB `payload` column round-trips correctly through a real Postgres connection |
| `shouldRejectNullReceiptId` | null-handling boundary |

## Test manifest (`ReceiptServiceTest`, existing class — 5 new methods)

| Test | Verifies |
|---|---|
| `shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended` (named) | AC1/R24 — `Receipt` saved, `LedgerService.append` called, `markAttestedIfFinalized` called with the right arguments |
| `shouldEmitReceiptIssuedAfterAttestation` (named) | AC3/R25 — the published `ReceiptIssuedPayload`'s fields match the frozen 11-field shape, `receiptUri` points at the `.json` object |
| `shouldAbortAndReturnFailedWhenMarkAttestedIfFinalizedReturnsZeroRows` | AC6, Finding 2 — the whole persist sequence aborts, no event published; mutation-verified (see below) |
| `shouldReturnFailedWhenReceiptStoreThrows` | an S3 failure before the DB transaction opens converts to `Failed`, no `Receipt` row attempted |
| `shouldReturnFailedWhenLedgerServiceThrows` | a DB failure after the S3 write (the disclosed orphan-window scenario) still fails safely, no event published |

## Test manifest (`PaymentServiceTest`, existing class — 3 new methods)

`shouldMarkAttestedAndRecordVerificationWhenFinalized`, `shouldNotRecordVerificationWhenMarkAttestedIfFinalizedFindsNoLongerFinalized`,
`markAttestedIfFinalizedShouldThrowNullPointerExceptionForNullArguments` — mirror
`markHeldIfFinalized`'s own existing test shapes exactly (T17), including the same scope
decision that class already established: the repository's own JPQL (`attestedAt`/`updatedAt`
actually being set) is proven at the mocked-repository level only, consistent with
`markHeldIfFinalized` having never had a dedicated real-DB-level test of its own JPQL either —
not a new gap introduced here, the same depth this codebase has already accepted for the
identical class of method.

## Mechanical extension (not new test content)

- **`SchemaBaselineMigrationIntegrationTest`** — `paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheTwoGrantedTables`
  renamed to `...OnTheGrantedTables` (dropping "Two," since `GRANTED_TABLES` now has three
  entries) and extended with a third `assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(app,
  "receipts", secondPaymentId)` call — a real functional proof that `payment_app` can
  `INSERT`/`SELECT` but not `UPDATE`/`DELETE` on `receipts` through a real connection, not just
  via `information_schema`. Mutation-verified (see below).

## Mutation verification performed this phase

- **`LedgerService.sha256` input order**: temporarily swapped `canonicalJson + prevHash` to
  `prevHash + canonicalJson` — `shouldComputeEntryHashAsShaTwoFiftySixOfCanonicalPayloadConcatenatedWithPrevHash`
  failed red, as expected. Reverted; confirmed `git diff` empty.
- **`ReceiptService`'s abort-on-zero guard**: temporarily replaced `if (!attested)` with
  `if (false)` — `shouldAbortAndReturnFailedWhenMarkAttestedIfFinalizedReturnsZeroRows` failed
  red, as expected. Reverted; confirmed `git diff` empty.
- **`V9__payment_app_receipts_grant.sql`**: temporarily commented out the `GRANT` statement — the
  new `receipts` assertion inside `SchemaBaselineMigrationIntegrationTest`'s own renamed test
  failed red with a real `permission denied for table receipts` error, as expected. Reverted;
  confirmed `git diff` empty and the full suite green again afterward.

## Full suite

`mvn -o -pl services/payment -am test` (Docker up — `services/auth/compose.local.yaml`):
**398/398 passing** (386 pre-task baseline + 12 new: 4 `LedgerServiceTest` + 5 `ReceiptServiceTest`
+ 3 `PaymentServiceTest`).
