# payment · T19 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 implementation and Phase 7 self-review against the frozen brief (`artifacts/04-frozen-task-brief.md`), `spec/payment-service/agents.md`, and `spec/payment-service/design.md`.

---

## Finding 1 — Advisory lock is not acquired as the first statement in the transaction

- **Issue:** The frozen brief explicitly orders the `Signed` persistence transaction as: (1) advisory lock, (2) insert `Receipt`, (3) read head + insert `LedgerEntry`, (4) `ATTESTED` transition, (5) publish event. The implementation inserts the `Receipt` first and only acquires the lock inside `LedgerService.append`, which is called after the `Receipt` save.
- **Evidence:** `artifacts/04-frozen-task-brief.md` lines 108-144 (frozen insert order, "(1) advisory lock — first statement"); `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java` lines 167-172 (`receiptRepository.save(...)` before `ledgerService.append(...)`); `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerService.java` line 65 (`acquireAppendLock()` is not the transaction's first statement). The `LedgerService` Javadoc acknowledges this is a deliberate deviation from the frozen brief.
- **Recommendation:** Either move the lock acquisition to the start of `ReceiptService.persistSignedReceipt` (e.g., call `LedgerEntryRepository.acquireAppendLock()` directly there) and restore the frozen order, or formally amend the frozen brief to record the deviation and explain why the lock placement after the `Receipt` insert is equivalent.
- **Confidence:** High — a direct deviation from an approved, user-locked ordering decision.

---

## Finding 2 — The T19 code paths have no committed automated tests

- **Issue:** The implementation notes explicitly state that no new test content was added in Phase 6 and that the three named tests (`shouldAppendLedgerEntryWithPrevHashChainingToHead`, `shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended`, `shouldEmitReceiptIssuedAfterAttestation`) are Phase 10's job. The full suite passes 386/386, but none of those tests exercise `LedgerService`, the new entities, the abort-on-zero guard, or the `receipt.issued` event.
- **Evidence:** `artifacts/06-implementation-notes.md` lines 99-115; absence of new test files under `services/payment/src/test/java/com/themistra/payment/receipt/ledger/` or new test methods in `ReceiptServiceTest.java` for the T19 paths.
- **Recommendation:** Do not treat T19 as verified until Phase 10 adds the named tests, plus a real-grant proof for `receipts`, an abort-on-zero test, and a concurrency test (see Finding 4).
- **Confidence:** High — the code is unverified by CI for the exact behavior this task is supposed to deliver.

---

## Finding 3 — AC4 concurrency guarantee is asserted by argument, not proven under realistic load

- **Issue:** Phase 7 found a spurious Hibernate `UPDATE ledger_entries ... WHERE seq = ?` immediately after the correct `INSERT` when `LedgerService.append` was invoked from a bare `java.util.concurrent.ExecutorService` background thread. The root cause was not identified. The production path uses Spring-managed Kafka listener threads, which were not exercised. The advisory lock plus `@Transactional` on `append` is sound on paper, but the anomaly shows that thread/transaction-context interactions can produce unexpected SQL.
- **Evidence:** `artifacts/07-self-review.md` Finding 2; `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerService.java` lines 36-44 (acknowledges the `@Transactional` fix was found empirically, not by design).
- **Recommendation:** Phase 10's concurrency test must use two genuine Spring-managed parallel transactions (e.g., two `TransactionTemplate.execute(...)` calls on different threads, or two real concurrent `chain.tx.finalized` messages through `ChainEventConsumer`) and assert that the resulting chain has no fork (every `prevHash` matches exactly one prior `entryHash`). A bare `ExecutorService` harness is not representative and has already produced anomalous behavior.
- **Confidence:** Medium — the lock is almost certainly correct, but the unroot-caused anomaly makes empirical proof mandatory, not optional.

---

## Finding 4 — `issueIfEligible` is not idempotent; a partial failure can produce multiple receipts for one payment

- **Issue:** `ReceiptService.issueIfEligible` generates a fresh `receiptUuid` on every call and only prevents duplicate `Receipt` rows via the atomic `FINALIZED → ATTESTED` transition. If the first call writes signed objects to S3 but the DB transaction then rolls back (network blip, lock timeout, etc.), the payment remains `FINALIZED`. A second call (manual retry, a different trigger, or any future caller) will succeed with a new `receiptUuid`, a new S3 object, a new ledger entry, and a second `receipt.issued` event. R17 says the system "SHALL issue exactly one receipt" for a qualifying payment.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java` lines 129 and 160-188 (`UUID.randomUUID()` per call, no idempotency check before the DB transaction); `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java` lines 413-430 (`markAttestedIfFinalized` only guards against non-`FINALIZED` states).
- **Recommendation:** Add a documented warning in the class Javadoc that callers must never retry `issueIfEligible` for the same payment, or add an idempotency guard (e.g., `ReceiptRepository.existsByPaymentId(payment.id())` at the start of the DB transaction, throwing/returning `Failed` if a receipt already exists).
- **Confidence:** Medium — the normal Kafka path does not retry, but the service method itself is not safe to retry, which is an unstated coupling.

---

## Finding 5 — Advisory-lock key is derived from `hashtext`, not a fixed constant

- **Issue:** `LedgerEntryRepository.acquireAppendLock` uses `hashtext('themistra.payment.ledger.append')::bigint`. PostgreSQL's `hashtext` is an internal hash function whose implementation can change across major versions. If it ever does, the lock ID would change, breaking cross-version lock compatibility and making the value unpredictable for tests or operators.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerEntryRepository.java` lines 25-26.
- **Recommendation:** Replace the expression with a literal 64-bit integer constant (e.g., `8217349876234567890L`), expose it as a single Java constant, and reference that constant in both the repository query and any tests.
- **Confidence:** Low — the function is stable in practice, but a literal constant is safer and matches the frozen brief's intent of a "fixed, namespaced" key.

---

## Finding 6 — Physical `receipt-issued.v1.schema.json` contract file is still missing

- **Issue:** `ReceiptService` now emits `receipt.issued` events, but the schema file `contracts/events/payments/receipt-issued.v1.schema.json` does not exist in the repository. The frozen brief defers authoring to task 24, following the precedent of the other three events. Until that file exists, contract tests cannot validate the event, and R31 is not satisfied for this event.
- **Evidence:** `contracts/events/payments/*.json` glob returns no files; `services/payment/src/main/java/com/themistra/payment/receipt/event/ReceiptIssuedPayload.java` is emitted at `ReceiptService.java` lines 181-185.
- **Recommendation:** Either author the schema file now (it is small and the fields are already frozen) or ensure task 24 is tracked as a hard release dependency so contract tests are not silently skipped.
- **Confidence:** Low — a known pipeline choice, but still a gap between emitted events and the `contracts/` directory.

---

## Finding 7 — `ReceiptStore.toCanonicalMap` is public and returns a mutable map

- **Issue:** Widening `toCanonicalMap` to `public static` lets `LedgerService` reuse the canonical 15-field map, but the method returns a mutable `LinkedHashMap`. `ReceiptService.buildLedgerPayload` immediately mutates it by adding `s3Bucket` and `s3KeyPrefix`. Future callers may assume the returned map is a read-only canonical representation and accidentally alter it before serialization, producing a different ledger hash.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` lines 134-152; `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java` lines 201-207.
- **Recommendation:** Return an unmodifiable map from `toCanonicalMap`, and build the ledger payload by copying into a new `LinkedHashMap` before adding the S3 fields.
- **Confidence:** Low — the current callers are safe, but the API shape invites future mistakes.

---

## Finding 8 — `receipts.s3_key` column length is not validated against configured prefix length

- **Issue:** `Receipt.s3Key` is mapped with `length = 256`, but the actual value is `normalizedPrefix(properties.prefix()) + receiptUuid` (plus `s3://.../.json` in the event URI). A long configured prefix could exceed 256 characters and cause a runtime SQL error on insert.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/receipt/Receipt.java` line 61; `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` lines 96 and 233-235.
- **Recommendation:** Add a `@Size` or startup validation on `ReceiptProperties.prefix()` ensuring that `prefix + UUID + "/...json"` fits within the 256-character `s3_key` column, or document the limit clearly.
- **Confidence:** Low — most real prefixes will be short, but the lack of a guard is a latent operational failure.

---

## Summary

The implementation is structurally sound and follows the frozen brief for the most part, but the independent review identified one clear deviation (lock placement), one complete test-coverage gap, one unproven concurrency guarantee, and several smaller latent risks. The highest-priority items before considering T19 complete are restoring the frozen lock order (or formally amending it), adding the committed tests, and proving the advisory lock under realistic Spring-managed concurrency.
