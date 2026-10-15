# payment · T19 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

- **R19** (fully assembled now — the mechanism spans T15/T16/T17; this task is the first to
  carry a `Signed` outcome all the way to a persisted, durable result). WHEN a `FINALIZED`
  payment's receipt is signed, THEN the system SHALL compute the digest, call `/attest`, and
  never sign locally.
- **R21.** WHEN a receipt is issued, THEN the system SHALL append one ledger entry whose
  `prev_hash` is the SHA-256 entry hash of the current chain head, and SHALL advance the head to
  the new entry.
- **R23** (fully assembled now — the S3 write mechanism is T18's; this task is its first real
  caller). WHEN a receipt is issued, THEN the system SHALL store the signed receipt object (JSON
  and PDF) in the S3 Object Lock bucket in compliance mode, and SHALL NOT overwrite an existing
  receipt object.
- **R24.** WHEN the receipt has been signed, stored in S3, and appended to the ledger, THEN the
  payment SHALL transition to `ATTESTED`.
- **R25.** WHEN a payment reaches `ATTESTED`, THEN the system SHALL emit a `receipt.issued`
  outbox event in the same transaction as the ledger append.

## Locked Decisions

- **L2** (the single most load-bearing rule in the service). A receipt is issued only from
  `FINALIZED`, never earlier. `ATTESTED` is set only after the signature is obtained, the receipt
  is stored in S3, and the ledger entry is appended, **all durably** — this task is where that
  "all durably" clause is finally made real; T17/T18 each built one piece of it in isolation.
- **L7.** Append-only hash-chain ledger. `ledger_entries` is append-only (INSERT+SELECT only,
  already granted). `entry_hash` = SHA-256(canonical payload ‖ `prev_hash`); genesis `prev_hash`
  = 64 zeros.
- **L8.** Outbox for every publish. `receipt.issued` is written to the outbox in the same
  transaction as the state change (R25's own explicit text is the specific instance of this
  general rule for this task).
- **L9** (continuity, T18). Signed receipts are WORM; no overwrite/delete path in application
  code.
- **L12** (continuity). No feature module imports another feature module's entity class.
  `receipt`'s new `Receipt`/`LedgerEntry` entities must not be imported by `payment`/`invoice`,
  and vice versa — confirmed this task's own new entities stay inside `receipt`'s own module.

## Files involved

**Existing, to extend:**
- `receipt/ReceiptService.java` (T17) — `issueIfEligible`'s own `Signed` branch is the real
  integration point (confirmed at Phase 0).
- `payment/PaymentRepository.java`/`PaymentService.java` — new `FINALIZED → ATTESTED` atomic
  transition method, mirroring `markHeldIfFinalized`'s exact shape (T17).
- `verification/VerificationRecordService.java` — reused as-is (`record(...)`), no change.
- `events/OutboxPublisher.java`/`EventTopics.java` — reused as-is; `"receipt"` →
  `"payments.receipt.issued"` already mapped (T04).

**Existing, read-only (the exact data this task's own new code consumes):**
- `receipt/ReceiptPayload.java`/`ReceiptDigest.java` (T16).
- `receipt/ReceiptStore.java`/`ReceiptStorageLocation.java` (T18).
- `common/crypto/AttestationResult.java` (T15).

**New files the spec expects (confirmed absent at Phase 0):**
- `receipt/Receipt.java`/`ReceiptRepository.java` — the `receipts` table entity/repository.
- `ledger/LedgerEntry.java`/`LedgerRepository.java`/`LedgerService.java` — the `ledger_entries`
  table entity/repository and the hash-chain append logic.
- A new Flyway migration granting `payment_app` access to `receipts` (confirmed zero grants
  exist today).

## Dependencies

- `ReceiptStore.store(ReceiptPayload, AttestationResult.Signed)` (T18) — called before any DB
  transaction opens (an S3 call, same "outbound I/O never holds a transaction open" discipline
  as `attest()` itself).
- `ReceiptDigest.digest(payload)` (T16) — already computed by `ReceiptService` before `attest()`
  is called; reused as the value to persist in `receipts.digest_sha256`, not recomputed.
- A canonical serialization of the ledger entry's own payload, for `entry_hash`'s own SHA-256
  input (L7's "canonical payload" text) — `CanonicalJson.serialize(...)` (T16) is the established,
  shared canonicalizer `design.md` O4 itself names for exactly this reuse ("one shared
  `CanonicalJson` serializer also reused by the hash-chain ledger").

## Acceptance Criteria

- **AC1 (R19/R23/R24, integration).** A `FINALIZED`/`PAID` payment that receives a `SIGNED`
  attest response ends with: a persisted `Receipt` row (all 8 non-identity columns populated,
  `s3_key` = the `ReceiptStorageLocation`'s own `s3KeyPrefix`, not a full object key), a
  persisted `ledger_entries` row referencing that `Receipt`'s id, the payment in `ATTESTED`, and
  a `VerificationRecord` for the transition.
- **AC2 (R21, the hash-chain property).** The new ledger entry's `prev_hash` equals the current
  chain head's `entry_hash` (or 64 zeros if this is the first entry ever); `entry_hash` is
  `SHA-256(CanonicalJson.serialize(entry payload) ‖ prev_hash)`.
- **AC3 (R25/L8).** `receipt.issued` is published via `OutboxPublisher` inside the exact same
  transaction that appends the ledger entry and transitions the payment to `ATTESTED` — not a
  separate transaction, not best-effort.
- **AC4 (concurrency, open — Phase 1/2 question, not yet answered).** Two concurrent
  `FINALIZED → ATTESTED` transitions for two different payments must never both read the same
  chain head and each append an entry claiming to follow it — the single-sequence invariant must
  hold under real concurrent load, not just in a single-threaded test. Exact mechanism
  (`SELECT ... FOR UPDATE`, `SERIALIZABLE`, or an advisory lock) is a genuine Phase 2 design
  decision, flagged forward from Phase 0.
- **AC5 (L9, continuity).** No change to T18's own no-overwrite guarantee — this task is a new
  caller, not a redesign, of `ReceiptStore`.

## Tests required

Three named tests from `package.md` §8 are pre-mapped:
- `shouldAppendLedgerEntryWithPrevHashChainingToHead` (R21) — the primary hash-chain test,
  almost certainly needs a **real** sequential-insert proof (Testcontainers, Docker confirmed up
  this session), not a fully mocked "current head" stand-in, to genuinely prove the chaining
  property across multiple real inserts.
- `shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended` (R24).
- `shouldEmitReceiptIssuedAfterAttestation` (R25).

Boundary tests implied, not separately named:
- Genesis case: the very first ledger entry ever has `prev_hash` = 64 zeros.
- A concurrency test proving AC4 once its mechanism is decided at Phase 2 — likely a real
  Testcontainers test driving two concurrent transactions, not a unit test (a mocked repository
  cannot meaningfully prove a real database-level concurrency-control mechanism works).
- A test proving the new `receipts` DB grant actually works against a real connection (mirrors
  T06's/T08's own precedent — a mocked-repository test cannot catch a missing `GRANT`).

## Open Questions

Both carried forward from Phase 0, confirmed not addressed anywhere in `package.md` §3/§8/§11:

1. **The concurrent-head-read race (AC4 above)** — a genuine concurrency-control decision with
   no default obviously implied by the spec text, which only describes the single-writer case.
   Candidate for a real `AskUserQuestion` at Phase 2 if more than one reasonable mechanism exists
   with materially different trade-offs (locking strategy affects throughput under concurrent
   attestations, which this service's own real-world volume may or may not ever make material)
   — or a direct, reasoned proposal if one mechanism is clearly correct and low-risk, mirroring
   this session's own "propose directly when low-risk, escalate when it's a real policy call"
   pattern.
2. **The post-`attest()` crash window, extended** — T17's own disclosed "no retry" gap now
   spans a longer sequence (S3 write, DB transaction) than it did before this task existed. Not
   a new decision to make, just a documentation update to extend an already-accepted disclosure.

Neither blocks starting Phase 2.
