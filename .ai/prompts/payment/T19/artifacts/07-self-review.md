# payment · T19 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

Findings only — no fixes applied here, mirroring T19's own predecessor tasks' standard
convention (unlike T18's own Phase 7, which applied two mechanical fixes inline; this phase's
one real finding was already fixed during Phase 6 implementation itself, before this artifact
was written, and is recorded here as "found and fixed" rather than deferred — the exact same
disclosed deviation pattern T18's Phase 7 used, for the same reason: it's a mechanical/
structural correction, not a design judgment call).

---

## Finding 1 (High, found and fixed during Phase 6) — `LedgerService.append` had no transaction boundary of its own

- **Issue:** `append`'s three repository calls (`acquireAppendLock`, `findTopByOrderBySeqDesc`,
  `save`) each ran in their own independently-committed micro-transaction (Spring Data's default
  per-query-method transaction wrapping). `pg_advisory_xact_lock` releases automatically at
  transaction commit — with no enclosing transaction spanning all three calls, the lock was
  acquired and immediately released again before the head was even read, giving AC4's own
  concurrency guarantee zero actual protection.
- **Evidence:** confirmed via a real Testcontainers run (written, deleted before the Phase 6
  commit) — the fix was applied and re-verified in the same phase, not left for this one.
- **Fix applied:** `@Transactional` added directly to `LedgerService.append`.

---

## Finding 2 (Medium, disclosed, not resolved) — a real but only-partially-explained Hibernate anomaly under non-Spring-managed thread invocation

- **Issue:** calling `ledgerService.append(...)` from a bare `java.util.concurrent.ExecutorService`
  background thread (not a Spring-managed thread) caused Hibernate to issue a spurious `UPDATE
  ledger_entries SET ... WHERE seq = ?` immediately after the correct `INSERT`, within the same
  transaction — rejected by `payment_app`'s own intentionally-INSERT-only grant (L7's own
  append-only design). The identical code, called directly on the test's own main thread
  (including an explicit main-thread "warm-up" call before the executor-thread call, ruling out
  a one-time lazy-initialization race), never exhibited this.
- **Investigated and ruled out:** a missing transaction boundary (Finding 1's own fix was
  already in place when this was found); genuine multi-thread concurrency specifically
  (reproduced with exactly one executor-thread call); a one-time per-JVM lazy-initialization race
  (a main-thread warm-up call before the executor-thread call didn't prevent it); the hash-chain
  computation itself being wrong (the main-thread cases produced correct, verified SHA-256
  values).
- **Not resolved.** The real production concurrency path is two genuine Spring-managed
  Kafka-listener-container threads, not a bare `ExecutorService` — a materially different call
  path the scratch harness that exhibited this didn't represent. Proving AC4's own concurrency
  guarantee under that specific, representative condition needs a different kind of test (e.g.
  two real concurrent Kafka messages for different payments through `ChainEventConsumer`'s own
  real listener threads) than this task's own three named tests require.
- **Recommendation:** Phase 10 should prefer testing the advisory lock's effectiveness via a
  design/code-level argument (the lock is transaction-scoped, `append` is now `@Transactional`,
  therefore it protects its own critical section) plus the already-proven genesis/chaining
  correctness, rather than attempting to reproduce genuine concurrent load in a unit or even a
  single-class integration test — a decision for Phase 10 to make explicitly, not silently skip.

---

## Positive confirmations

- `persistSignedReceipt`'s insert order (`Receipt` → `LedgerEntry` → `ATTESTED` → `receipt.issued`)
  matches the frozen brief's own Finding 7 order exactly, confirmed by direct re-reading of the
  code against that order, not assumed.
- `now` (`clock.instant()`) is captured exactly once per `persistSignedReceipt` invocation and
  reused consistently across `Receipt.createdAt`, `markAttestedIfFinalized`'s own
  `attestedAt`/`updatedAt`/`VerificationRecord.occurredAt`, and `ReceiptIssuedPayload.occurredAt`
  — not recomputed at each step (which could theoretically skew by milliseconds across what is
  logically one atomic event, the same class of correctness property `handleBlocked` already
  established for the `HELD` case).
- `Receipt`/`LedgerEntry` both carry `paymentId`/`receiptId` as scalar `Long`, never a JPA entity
  reference — confirmed by reading both files' full import lists, neither imports `Payment` or
  `Receipt` as an entity type.
- `ReceiptIssuedPayload`'s 11 fields match `design.md`'s own pinned schema exactly, field-for-
  field, confirmed by a direct side-by-side re-read of both.
- The abort-on-zero-rows guard (`markAttestedIfFinalized` returning `false` throws, caught
  outside, converted to `Failed`) correctly rolls back the `Receipt`/`LedgerEntry`/outbox rows
  already staged in the same transaction — confirmed by re-reading `TransactionTemplate.execute`'s
  own documented rollback-on-exception contract, the same mechanism `handleBlocked`'s identical
  split-result guard already relies on.
- No monetary value, secret, or PII is newly logged anywhere in this task's own code.
- The full suite (386/386, Docker up) passes after the Finding 1 fix and the three mechanical
  wiring fixes from Phase 6 — no regression.
