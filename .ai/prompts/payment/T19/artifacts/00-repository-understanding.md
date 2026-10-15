# payment · T19 · Phase 0 — Repository Understanding

Artifact: `.ai/prompts/payment/T19/artifacts/00-repository-understanding.md`

## 1. Architecture summary

`payment-service` (`services/payment`, package `com.themistra.payment`). T15-T18 built every
individual primitive in the attestation/receipts/ledger group but never wired them together end
to end: `ReceiptService.issueIfEligible` (T17) currently returns `ReceiptIssueResult.Signed`
on a successful `attest()` call and **persists nothing** — explicitly disclosed in its own class
Javadoc as "T19's own job, once the ledger append also exists." `ReceiptStore` (T18) has no
caller anywhere in the codebase yet. T19 is the task that finally makes a payment actually reach
`ATTESTED` — the single most load-bearing state this entire service exists to reach (L2).

## 2. Existing code this task touches

**Already exists, read/extend:**
- `receipt/ReceiptService.java` (T17) — `issueIfEligible`'s own `Signed` branch
  (`result instanceof AttestationResult.Signed signed`, line 116) is the exact integration point:
  currently `return new ReceiptIssueResult.Signed(receiptUuid, signed);` with zero persistence.
  This is where the S3 write, the `Receipt` row, the ledger append, the `ATTESTED` transition,
  and the `receipt.issued` event all need to happen.
- `receipt/ReceiptStore.java`/`ReceiptStorageLocation.java` (T18) — `store(ReceiptPayload,
  AttestationResult.Signed)` → `ReceiptStorageLocation(bucket, s3KeyPrefix, jsonKey, pdfKey)`.
  **Confirmed, not assumed**: `s3KeyPrefix` (not `jsonKey`/`pdfKey`) is the value T18's own Phase
  4 frozen brief designed to go into `receipts.s3_key` — the column is a single
  `VARCHAR(256)`, this task is the first real caller that needs to actually populate it.
- `receipt/ReceiptPayload.java`/`ReceiptDigest.java` (T16) — already used by `ReceiptService`
  to build the digest; `ReceiptDigest.digest(payload)` returns the same SHA-256 hex string that
  needs to be persisted as `receipts.digest_sha256`.
- `payment/PaymentRepository.java`/`PaymentService.java` — `markHeldIfFinalized` (T17, line
  143-144) is the exact shape to mirror for a new `FINALIZED → ATTESTED` transition method: an
  atomic `UPDATE ... WHERE state = :expectedCurrentState` taking bound `PaymentState` enum
  parameters (not literal strings — T17's own Phase 9 fix established this as the file's own
  consistent convention). `PaymentStateMachine` has permitted `FINALIZED → ATTESTED` since T09
  (`PaymentStateMachine.java:46`), never exercised until now.
- `verification/VerificationRecordService.java` — `record(Long paymentId, PaymentState
  fromState, PaymentState toState, String triggerEventKey, Integer confirmations, Map<String,
  Object> detail)` — every other state transition in this service already gets one (T17's own
  Phase 3 Finding 4 established this for `HELD`); the `ATTESTED` transition should too, for the
  same auditability reason.
- `events/OutboxPublisher.java`/`EventTopics.java` — `publish(String aggregateType, String
  aggregateId, String eventType, Object payload)` joins the caller's own already-open
  transaction (no dual write). `EventTopics` already maps `"receipt"` →
  `"payments.receipt.issued"` (T04, confirmed by direct read — no new topic-mapping work
  needed). The established call-site convention (`PaymentService.java:120,261`,
  `InvoiceService.java:72`): `aggregateType` matches the `EventTopics` key exactly,
  `aggregateId` is the relevant entity's own UUID as a string, a dedicated payload record is
  built from in-scope primitives (e.g. `PaymentFinalizedPayload`), never the entity itself.

**Does not exist yet (net new, confirmed by direct search):**
- `receipt/Receipt.java`/`ReceiptRepository.java` — the actual `receipts` table entity/
  repository. Columns (`V1__payments_baseline.sql:71-82`, confirmed): `id`, `receipt_uuid`
  (`UNIQUE`), `payment_id` (`UNIQUE REFERENCES payments(id)` — one receipt per payment, by
  design), `digest_sha256` (`CHAR(64)`), `signature` (`TEXT`), `kms_key_id` (`VARCHAR(256)`),
  `s3_bucket` (`VARCHAR(128)`), `s3_key` (`VARCHAR(256)`), `signed_at` (`TIMESTAMPTZ`).
- `ledger/LedgerEntry.java`/`LedgerRepository.java`/`LedgerService.java` — `ledger_entries`
  table (`V1__payments_baseline.sql:280-287`, confirmed): `seq` (`BIGINT IDENTITY`,
  `PRIMARY KEY`), `receipt_id` (`BIGINT NOT NULL UNIQUE REFERENCES receipts(id)` — exactly one
  ledger entry per receipt, confirmed one-to-one, not one-to-many), `prev_hash` (`CHAR(64)`),
  `entry_hash` (`CHAR(64) UNIQUE`), `payload` (`JSONB`), `created_at`.
- No DB grants exist yet for either new table. **Confirmed by direct grep across every
  `V*__*.sql` migration**: `GRANT ... ON payments.ledger_entries` exists since `V2`
  (`INSERT, SELECT` — L7's own append-only rule, already correctly scoped even though nothing
  used it until now), but **`receipts` has zero grants anywhere** — this task needs a new
  migration (mirroring the exact T06/T08 "a new entity's first real caller surfaces a missing
  grant, caught only by a real Testcontainers test" pattern, confirmed twice already this
  pipeline). `payments.payments` already has `INSERT, UPDATE` (`V7`) — sufficient for the new
  `ATTESTED` transition, no new grant needed there.

## 3. Established patterns to follow

- **No entity load, no merge, for any state-conditional update** — every transition in this
  service since T08 uses an atomic `UPDATE ... WHERE state = :expected` repository method, never
  `findById` + mutate + `save`. The new `ATTESTED` transition must follow this exactly.
- **An outbound network/IO call never runs inside an open DB transaction** —
  `ChainEventConsumer.onChainTxReorged` (T12) and `onChainTxFinalized`/`ReceiptService.
  issueIfEligible` (T17) both split via `TransactionTemplate` for this exact reason. `ReceiptStore
  .store(...)` (T18) is itself an S3 call with no DB interaction at all — it must run strictly
  outside whatever transaction eventually persists the `Receipt` row and appends the ledger
  entry, mirroring the established "attest() first, then a separate transaction" shape.
- **Every state transition gets a `VerificationRecord`** (T17's own Phase 3 Finding 4
  precedent) — the `ATTESTED` transition should too.
- **An outbox event is published inside the same transaction as the state change it
  announces** (L8, `OutboxPublisher`'s own design) — R25's own text ("in the same transaction as
  the ledger append") makes this explicit for `receipt.issued` specifically, not just a general
  convention.

## 4. Testing conventions

- Unit tests for `LedgerService`/the new `Receipt`-persisting logic: mocked repositories, no
  Spring context, mirroring every prior service-layer test in this codebase.
- A genuine hash-chain correctness property (prev_hash of entry N+1 equals entry_hash of entry
  N, genesis prev_hash is 64 zeros) needs either a real Testcontainers-backed sequential-insert
  test or a carefully-mocked "current head" repository query — Docker is confirmed up this
  session (`docker ps`), so a real integration test proving the chain property across multiple
  sequential inserts is achievable and should be preferred over a fully mocked unit test for
  this specific property.

## 5. Known gaps / unknowns

**A genuine, not-yet-resolved concurrency question, confirmed not addressed anywhere in the spec
package (`requirements.md`/`design.md` both read in full for this):** R21's own text ("append
one ledger entry whose `prev_hash` is the SHA-256 entry hash of the **current chain head**, and
advance the head to the new entry") describes a strictly sequential operation, but says nothing
about what happens if two `FINALIZED → ATTESTED` transitions for two *different* payments run
concurrently. Both could read the same "current head" `entry_hash` before either commits, both
compute a `prev_hash` pointing to that same head, and both insert distinct, successfully-unique
`entry_hash` rows (since each is a different payload) that each legitimately claim to be "the
entry right after the head" — a genuine fork, not a chain, silently violating the single-
sequence invariant `LedgerVerifier` (task 20, unbuilt) is designed to check. Postgres's own
`entry_hash UNIQUE` constraint does **not** catch this (the two entries have different hashes),
and `seq`'s own `IDENTITY` ordering only orders *insertion*, not *logical* chain position — a
later-inserted row can still carry an earlier row's own `prev_hash`. This needs a real
concurrency-control decision (e.g. `SELECT ... FOR UPDATE` on the head row, or `SERIALIZABLE`
isolation scoped to just this operation) before Phase 2 can finalize a design — flagged forward
to Phase 1/2, not resolved here.

**A second, smaller open question**: R24's text ("WHEN the receipt has been signed, stored in
S3, and appended to the ledger, THEN the payment SHALL transition to `ATTESTED`") and R25's text
("in the same transaction as the ledger append") together imply the ledger append and the
`ATTESTED` transition and the `receipt.issued` event are all one atomic unit — but the S3 write
(T18) and the `attest()` call (T15) are necessarily *outside* that transaction (network calls).
If the process crashes after `attest()` succeeds but before the S3 write, or after the S3 write
but before the DB transaction commits, the payment never reaches `ATTESTED` and (per T17's own
already-disclosed gap) there is no retry mechanism. This is the same structural class of gap
T17's own Javadoc already discloses for the `attest()` call itself — this task should extend
that same disclosure to cover the now-longer post-`attest()` sequence, not treat it as new.

**Known, not a gap — confirmed above, not assumed:** `Receipt`/`LedgerEntry` entities, their
repositories, and `LedgerService` all need to be built from scratch; `receipts` has no DB grant
yet; `payments`/`ledger_entries` both already have the grants this task needs.
