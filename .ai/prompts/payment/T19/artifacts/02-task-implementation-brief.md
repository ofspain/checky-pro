# payment · T19 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Implement `LedgerService.append(...)`: `prev_hash` = current head `entry_hash`, `entry_hash` =
SHA-256(canonical payload ‖ `prev_hash`), genesis `prev_hash` = 64 zeros (R21, L7). Then
transition the payment to `ATTESTED` and emit `receipt.issued` in the same transaction as the
ledger append (R24, R25). Wire all of it into `ReceiptService.issueIfEligible`'s own `Signed`
branch, which currently persists nothing.

## Purpose

This is where L2's own three durable steps ("the signature is obtained, the receipt is stored in
S3, and the ledger entry is appended, all durably") are finally assembled into one real,
end-to-end flow — the capstone of the T15-T18 attestation group. Before this task, a `FINALIZED`
payment that gets a `SIGNED` attest response has its signature discarded by the caller; after it,
the payment reaches `ATTESTED` for real, with a tamper-evident ledger entry and a durable S3
receipt backing it.

## Scope

**In:** `Receipt`/`ReceiptRepository` (new entity, `receipt` package), `LedgerEntry`/
`LedgerEntryRepository`/`LedgerService` (new, `ledger` package, mirroring `design.md`'s own
pinned module layout), a new `payment_app` grant migration for `receipts`, the restructured
`ReceiptService.issueIfEligible` `Signed` branch, a new `PaymentRepository.markAttestedIfFinalized`
atomic transition, a new `ReceiptIssuedPayload` outbox event record.

**Out:** `LedgerVerifier` (task 20), the daily anchor job (task 21, deferred), tax export (task
22), RFC 9457 error catalogue (task 23), contracts (task 24).

## Business Rules

- **R19** (fully assembled), **R21**, **R23** (fully assembled), **R24**, **R25**.

## Locked Decisions

- **L2/L7/L8/L9/L12** — see Phase 1.
- **New, proposed directly (low-risk engineering decision, mirrors this spec's own Q5/Q6/Q7
  "implementer may propose" precedent — not escalated to `AskUserQuestion`, since this is a pure
  concurrency-control mechanism with no product-policy dimension):**
  **Concurrency control for the ledger append: a Postgres advisory transaction lock**
  (`pg_advisory_xact_lock(<fixed constant>)`), acquired as the very first statement inside the
  transaction that reads the head and inserts the new entry, held until commit/rollback.
  Rejected alternatives and why: `SELECT ... FOR UPDATE` on the current head row cannot protect
  the genesis case (no row exists yet to lock before the very first entry is inserted — an
  empty result set locks nothing); `SERIALIZABLE` isolation would require new retry-on-conflict
  logic this codebase has no precedent for anywhere. An advisory lock keyed on a fixed constant
  (not derived from any row) uniformly serializes every ledger append — including the very
  first one — through a single critical section, with no dependency on a row already existing.
  Given this service's own expected real-world volume (a payment reaching `FINALIZED` is not a
  high-frequency event — on-chain finality itself takes minutes), a single global serialization
  point for this one specific operation is a negligible throughput cost for a correctness
  guarantee that has no other equally simple mechanism.
- **New, proposed directly — the ledger entry's own JSONB `payload` shape:** the same canonical
  map `ReceiptStore` already builds for the stored JSON (12 O4 fields + `signature`/`kmsKeyId`/
  `signedAt` = 15 fields), **plus** `s3Bucket`/`s3KeyPrefix` (17 fields total) — so the ledger
  entry is self-describing: a future verifier/auditor reading `ledger_entries` alone (per
  `ARCHITECTURE.md` §6.5's own "cryptographic answer to did we alter this record" framing) can
  locate the exact S3 object this entry attests to without a join back to `receipts`. Reuses
  `CanonicalJson.serialize(...)` (T16) for `entry_hash`'s own input — no second canonicalizer.
  `entry_hash = SHA-256(CanonicalJson.serialize(payload) + prevHash)` — **string concatenation**
  of the canonical JSON text with the raw hex `prevHash`, not a nested JSON field, matching L7's
  own literal "canonical payload ‖ `prev_hash`" notation.
- **New, proposed directly — a `ReceiptStorageException`/any persistence failure after a
  `SIGNED` attest response converts to `ReceiptIssueResult.Failed`, not left to propagate.**
  Mirrors every other failure path `issueIfEligible` already has (409, transport failure) —
  consistent with the method's own existing structure, not a new pattern.

## Dependencies

- `ReceiptStore.store(ReceiptPayload, AttestationResult.Signed)` (T18) — called strictly outside
  any DB transaction, same discipline as `attest()` itself.
- `CanonicalJson.serialize(Map<String,Object>)` (T16) — reused for the ledger payload's own
  `entry_hash` input, not a new canonicalizer.
- `PaymentRepository`/`PaymentService` (T09/T17 shape) — new `markAttestedIfFinalized`, mirroring
  `markHeldIfFinalized`'s exact atomic-update shape.
- `VerificationRecordService.record(...)` — reused as-is for the `ATTESTED` transition's audit
  trail.
- `OutboxPublisher`/`EventTopics` — reused as-is; `"receipt"` → `"payments.receipt.issued"`
  already mapped (T04).

## Inputs

`LedgerService.append(...)`'s own exact signature is a Phase 5 implementation-plan decision; it
needs, at minimum: the newly-persisted `Receipt`'s id (the `ledger_entries.receipt_id` FK
target), and enough of `ReceiptPayload`/`AttestationResult.Signed`/`ReceiptStorageLocation` to
build the 17-field payload map above.

## Outputs

- A persisted `Receipt` row: `receiptUuid`, `paymentId`, `digestSha256` (the same digest string
  `ReceiptService` already computed before calling `attest()` — not recomputed), `signature`,
  `kmsKeyId`, `s3Bucket` = `ReceiptStorageLocation.bucket()`, `s3Key` = `ReceiptStorageLocation
  .s3KeyPrefix()` (**not** `jsonKey`/`pdfKey`), `signedAt`.
- A persisted `LedgerEntry` row: `receiptId` (FK), `prevHash`, `entryHash`, `payload` (JSONB, the
  17-field map above).
- The payment transitions `FINALIZED → ATTESTED`, with a `VerificationRecord`.
- A `receipt.issued` outbox event, published inside the same transaction.
- `ReceiptIssueResult.Signed(receiptUuid, signed)` is returned only once all of the above
  committed successfully; any failure in this sequence returns `ReceiptIssueResult.Failed(...)`
  instead (new decision, above).

## State Changes

`payments` (`FINALIZED → ATTESTED`), `receipts` (new row), `ledger_entries` (new row), `outbox`
(new row) — all in one transaction, per R24/R25's own explicit "same transaction" text.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/receipt/Receipt.java`.
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptRepository.java`.
- `services/payment/src/main/java/com/themistra/payment/ledger/LedgerEntry.java`.
- `services/payment/src/main/java/com/themistra/payment/ledger/LedgerEntryRepository.java`
  (exact class name mirrors `design.md`'s own pinned module-layout diagram).
- `services/payment/src/main/java/com/themistra/payment/ledger/LedgerService.java`.
- `services/payment/src/main/java/com/themistra/payment/payment/event/ReceiptIssuedPayload.java`
  (mirrors `PaymentFinalizedPayload`'s own package/shape convention).
- `services/payment/src/main/resources/db/migration/V9__payment_app_receipts_and_ledger_grants.sql`
  (next free Flyway version — confirmed `V8` is the current latest).

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java` — the
  `Signed` branch restructured per this brief; class Javadoc extended to disclose the longer
  post-`attest()` crash window (Phase 1 Open Question 2).
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java` — new
  `markAttestedIfFinalized`.
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java` — new
  `markAttestedIfFinalized` (mirrors `markHeldIfFinalized`'s shape, minus the `blockedReason`
  parameter `ATTESTED` has no analogue for).

## Files NOT to Modify

`ReceiptPayload`/`ReceiptDigest`/`ReceiptIssueResult`'s own shape (T16/T17, used as-is unless
Phase 3/5 find a concrete reason to add a field), `ReceiptStore`/`ReceiptStorageLocation`/
`ReceiptStoreConfig` (T18, used as-is — no wiring change to that class itself, only to its
caller), `LedgerVerifier` (task 20, not built here), anything under `spec/`.

## Acceptance Criteria

Carried from Phase 1 (AC1-AC5), unchanged in substance — restated here as the frozen set this
task's own Phase 6 implementation must satisfy:
- **AC1.** A `FINALIZED`/`PAID` payment with a `SIGNED` attest response ends with a persisted
  `Receipt` row, a persisted `LedgerEntry` row, the payment in `ATTESTED`, and a
  `VerificationRecord`.
- **AC2.** `entry_hash = SHA-256(CanonicalJson.serialize(17-field payload) + prevHash)`; the
  very first entry ever uses `prevHash` = 64 zeros.
- **AC3.** `receipt.issued` is published inside the exact same transaction as the ledger append
  and the `ATTESTED` transition.
- **AC4.** Two concurrent ledger appends (different payments) never both read the same head and
  each insert an entry claiming to follow it — enforced by the advisory lock, above.
- **AC5.** No change to `ReceiptStore`'s own no-overwrite guarantee.
- **AC6 (new).** A persistence failure after a successful `SIGNED` attest response (an S3
  failure, a DB failure) returns `ReceiptIssueResult.Failed`, never propagates uncaught and never
  silently returns a `Signed` result for work that didn't actually commit.

## Required Tests

Three named tests pre-mapped from `package.md` §8:
1. `shouldAppendLedgerEntryWithPrevHashChainingToHead` (R21) — the primary hash-chain test.
   Given the genuine concurrency property (AC4) cannot be proven by a mocked repository, this
   almost certainly needs a real Testcontainers-backed test inserting multiple entries
   sequentially and verifying the chain, plus a genuinely concurrent variant (two threads/
   connections) proving the advisory lock actually serializes them.
2. `shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended` (R24).
3. `shouldEmitReceiptIssuedAfterAttestation` (R25).

Boundary tests implied, not separately named: genesis `prevHash` = 64 zeros; a real-grant proof
that `payment_app` can actually `INSERT`/`SELECT` on `receipts`/`ledger_entries` (mirrors T06's/
T08's own "a new entity's first real caller surfaces a missing grant" precedent); a persistence-
failure-after-signed test proving AC6.

## Constraints

- **Transaction:** the `attest()` call and the `ReceiptStore.store(...)` call both run strictly
  outside any open transaction (existing discipline, T15/T17/T18). The `Receipt` insert, the
  ledger append, the `ATTESTED` transition, and the `receipt.issued` publish all run inside one
  single transaction, opened only after both outbound calls succeed.
- **Concurrency:** the advisory lock (above) is acquired as the first statement inside that one
  transaction.
- **Module boundaries:** `Receipt`/`LedgerEntry` stay inside `receipt`/`ledger` respectively; no
  cross-module entity import (L12).
- **Null handling:** trusts the caller for well-formed inputs, mirroring every prior task's own
  established stance.

## Open Questions

None remaining — both Phase 1 questions are resolved directly above (concurrency mechanism: an
advisory lock; the extended crash-window disclosure: a documentation update, not a new decision).
