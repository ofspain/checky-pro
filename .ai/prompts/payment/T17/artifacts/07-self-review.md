# payment · T17 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

Findings only — no fixes applied here (Phase 9's job).

---

## Finding 1 (Medium) — `handleBlocked`'s own `catch` only guards against the one exception it itself throws, not a real DB failure from its callees

- **Issue:** `handleBlocked`'s `try`/`catch` only catches `IllegalStateException` — the type it
  deliberately throws on a split `HELD` result. But `paymentService.markHeldIfFinalized(...)`/
  `invoiceService.markHeldIfPaid(...)` are ordinary JPA-backed calls; a transient DB failure
  (connection pool exhaustion, a deadlock, etc.) would surface as a `DataAccessException` or
  similar — a `RuntimeException`, but **not** an `IllegalStateException` — and would propagate
  straight out of `handleBlocked`, out of `issueIfEligible`, and out of
  `onChainTxFinalized` itself, uncaught.
- **Evidence:** `ReceiptService.java`, `handleBlocked`'s own `catch (IllegalStateException e)`.
- **Impact:** An uncaught exception here surfaces as an unhandled Kafka listener error. Worse,
  this doesn't even gain anything from a retry: `dedupe()` already consumed this event's
  idempotency key in the *first*, already-committed transaction (same root cause as the already-
  disclosed "no retry for a failed `attest()` call" gap — Kafka redelivery would just
  short-circuit at `dedupe()` and never reach this code again). The narrower catch adds an
  unnecessary extra failure mode (an unhandled exception) on top of an already-accepted one (a
  swallowed, unretried failure).
- **Recommendation:** Widen the catch to `RuntimeException` (or at least add a second `catch`
  for the DB-layer exception types this codebase's own JPA stack actually throws), converting
  any failure in this block to `ReceiptIssueResult.Failed` consistently with the two existing
  `catch` clauses in `issueIfEligible` itself.

---

## Finding 2 (Low) — the disclosed "no retry" gap doesn't explicitly name the crash-before-issueIfEligible-ever-runs sub-case

- **Issue:** The frozen brief and `ReceiptService`'s own Javadoc both disclose that a failed
  `attest()` call is never retried. Neither explicitly names the narrower, more severe sub-case:
  if the process crashes *after* `onChainTxFinalized`'s own first `TransactionTemplate` block
  commits but *before* (or during) `receiptService.issueIfEligible` ever runs at all, the
  receipt is never even attempted — not just "attempted and failed." `dedupe()` already consumed
  the idempotency key, so Kafka redelivery cannot retry this step either.
- **Evidence:** `ChainEventConsumer.onChainTxFinalized`'s own two-step shape (commit, then a
  separate un-transacted continuation) — structurally identical to `onChainTxReorged`'s own
  already-disclosed crash window (that class's own Javadoc, Phase 9 Finding 5, T12), which uses
  exactly this "crash in the window between commit and the next step" framing explicitly.
- **Impact:** Low — the end consequence (no receipt, no retry) is the same as the already-
  disclosed general gap; this finding is about naming the *specific* failure mode precisely for
  whoever reads this code later, not about a new behavior to fix.
- **Recommendation:** Add a sentence to `ReceiptService`'s own class Javadoc, mirroring
  `onChainTxReorged`'s own precedent, naming the crash-window case explicitly rather than only
  the "attest call itself failed" case.

---

## Positive confirmations

- `AttestationRefusedException` (409) and `CryptoServiceUnavailableException` are each caught in
  their own `catch` clause, both mapped to `Failed` with no `HELD` call — correctly distinct from
  `AttestationResult.Blocked`, which alone reaches `handleBlocked` (AC6, Finding 6 from Phase 3).
- The split-`HELD`-result guard (`paymentHeld != invoiceHeld`) correctly throws before either
  partial update can be observed outside the transaction — confirmed by reading the
  `TransactionTemplate.executeWithoutResult` contract: an exception thrown inside the callback
  rolls the transaction back.
- `issueIfEligible` re-reads both `PaymentReceiptView`/`InvoiceReceiptView` fresh via
  `findReceiptPrimitives`, never reusing the triggering transaction's own stale
  `FinalizationOutcome` — closes the real post-commit staleness window Kimi's Phase 3 Finding 3
  identified.
- Neither `ReceiptPayload`/`ReceiptDigest` import nor `ReceiptService` itself imports `Invoice`/
  `Payment` — confirmed by direct inspection of every new/modified file's own import list.
- `PaymentService.markHeldIfFinalized`'s own `isPermitted` pre-check mirrors every other
  transition method's identical defensive-assertion style in the same class.
- `clock.instant()` is captured once per `handleBlocked` invocation and reused for both `HELD`
  updates, not called twice (which could theoretically produce two different timestamps for what
  is logically one atomic event).
- No monetary value, secret, or PII is newly logged anywhere in this task's own code.
- The real Postgres+Kafka integration test (T14) confirms the restructured
  `onChainTxFinalized` wires correctly in a live Spring context and that the disclosed
  degradation (a real, unreachable crypto-service) is genuinely swallowed, not just
  mocked-and-assumed.
