# payment · T17 · Phase 4 — Freeze Task Brief

**STATUS: FROZEN** — approved as written via `AskUserQuestion`.

Consumes: `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md`

## Disposition of Phase 3 (Kimi) findings

| # | Finding | Disposition | Reason |
|---|---|---|---|
| 1 | High — no defined cross-module read API for `ReceiptService` | **ACCEPTED** | Verified directly: `InvoiceWatchView` (the only existing cross-module projection) has `id/invoiceUuid/state/payToAddress/watchId/chain/tokenContractAddress/amount/expiresAt` — confirmed missing `merchantAccountUuid`/`tokenDecimals`/`paymentUuid`/`fromAddress`/`finalizedAt`, exactly as Kimi claimed. New `PaymentReceiptView`/`InvoiceReceiptView` projections + two new read methods added. |
| 2 | High — the two `HELD` updates must be all-or-nothing | **ACCEPTED** | Real, credible race: a concurrent `chain.tx.reorged` (a separate Kafka listener/transaction) could move the payment out of `FINALIZED` in the exact window between the two atomic `HELD` updates, since they target independent rows with independent `WHERE state = :expected` guards. All-or-nothing handling (throw + rollback on a split result) added. |
| 3 | Medium — re-read current state before `attest()`, not reuse the stale first-transaction outcome | **ACCEPTED** | Cheap, closes a real post-commit staleness window. |
| 4 | Medium — the `HELD` transition needs a `VerificationRecord` audit trail | **ACCEPTED** | Verified `PaymentService` already depends on `VerificationRecordService` (not a new cross-module dependency) — every other payment state transition already gets one; `FINALIZED → HELD` was the one omission. |
| 5 | Medium — the return type must carry the `SIGNED` result, not discard it | **ACCEPTED** | Sealed `ReceiptIssueResult` (`Signed`/`Blocked`/`NotEligible`/`Failed`) replaces the brief's own "void or TBD" placeholder. |
| 6 | Medium — distinguish `409 AttestationRefusedException` from `200 BLOCKED` explicitly | **ACCEPTED, with a correction to Kimi's own framing of the brief** | Re-checked the Phase 2 brief directly: it already kept these separate in substance (AC3 only triggers `HELD` for the `BLOCKED` branch; AC6 already said the transport/refused case is "logged and swallowed, not retried" — no `HELD`). Kimi's own concern is about explicitness/wording, not a substantive gap in what was already specified. Accepted as a clarity improvement to the frozen brief's own AC6 text, not a correction of a real bug in Phase 2. |
| 7 | Low — keep dedupe/resolve/apply/outcome-update inside one `TransactionTemplate` block | **ACCEPTED** | Explicit ordering now stated. |
| 8 | Low — inject `Clock` into `ReceiptService` | **ACCEPTED** | Matches established convention. |
| 9 | Low — named `HELD` methods, not raw generic reuse | **ACCEPTED** | Consistent with existing style (`markOutcomeIfOpenOrExpired` precedent). |
| 10 | Low — accept the synchronous post-commit blocking trade-off | **ACCEPTED** | Already an established, documented trade-off for the identical class of problem (`registerReplacementWatch`, T12). |

No finding was rejected.

## Amendments folded into this frozen brief (supersede the Phase 2 TIB where they conflict)

**New cross-module projections and read methods (Finding 1):**
```java
// payment/PaymentReceiptView.java
public record PaymentReceiptView(Long id, UUID paymentUuid, Long invoiceId, String chain, String txHash,
        String tokenContractAddress, BigDecimal amount, String fromAddress, Instant finalizedAt,
        PaymentState state) {}

// invoice/InvoiceReceiptView.java
public record InvoiceReceiptView(Long id, UUID invoiceUuid, UUID merchantAccountUuid, short tokenDecimals,
        String payToAddress, InvoiceState state) {}
```
`PaymentService.findReceiptPrimitives(Long paymentId)` → `Optional<PaymentReceiptView>`.
`InvoiceService.findReceiptPrimitives(Long invoiceId)` → `Optional<InvoiceReceiptView>`.
Both read-only, scalar/projection-backed (no entity leaves its own module).

**`ReceiptService.issueIfEligible(Long paymentId)`** (the actual entry point, replacing the
brief's own unnamed placeholder):
1. Read `PaymentReceiptView`/`InvoiceReceiptView` fresh (Finding 3 — not the first
   transaction's stale outcome).
2. If `payment.state != FINALIZED` or `invoice.state != PAID` (or either view is absent):
   return `ReceiptIssueResult.NotEligible`.
3. Build `ReceiptPayload` (fresh `UUID receiptUuid`, every other field from the two views),
   compute the digest, call `CryptoClient.attest(...)` — **outside any transaction**.
4. `AttestationResult.Signed` → return `ReceiptIssueResult.Signed(receiptUuid, signed)`. No DB
   write (L2/out-of-scope, unchanged from Phase 2).
5. `AttestationResult.Blocked` → open a **second**, narrow `TransactionTemplate.execute(...)`
   calling `PaymentService.markHeldIfFinalized(paymentId, blockedReason, triggerEventKey, now)`
   and `InvoiceService.markHeldIfPaid(invoiceId, now)`. If both return `true`, commit normally.
   If both return `false`, log `WARN` (already resolved elsewhere) and commit the no-op. **If
   exactly one returns `true`, throw `IllegalStateException`** to roll back the partial update —
   caught by `ReceiptService` outside the `TransactionTemplate`, logged at `ERROR`, converted to
   `ReceiptIssueResult.Failed` (never propagated to `ChainEventConsumer`, so the Kafka offset for
   the already-committed first transaction is never blocked).
6. `AttestationRefusedException` (409) → log `WARN`, return `ReceiptIssueResult.Failed`. **No
   state change** — distinct from step 5, per Finding 6.
7. `CryptoServiceUnavailableException` → log `ERROR`, return `ReceiptIssueResult.Failed`. **No
   state change.**

**`ReceiptIssueResult`** (sealed, Finding 5):
```java
public sealed interface ReceiptIssueResult {
    record Signed(UUID receiptUuid, AttestationResult.Signed signed) implements ReceiptIssueResult {}
    record Blocked(String reason) implements ReceiptIssueResult {}
    record NotEligible() implements ReceiptIssueResult {}
    record Failed(String reason) implements ReceiptIssueResult {}
}
```

**`PaymentService.markHeldIfFinalized(Long paymentId, String blockedReason, String triggerEventKey, Instant now)`**
→ `boolean` — atomic `UPDATE ... WHERE state = 'FINALIZED'`, and on success records a
`VerificationRecord` (Finding 4) with `detail` carrying `blockedReason`, keyed by
`triggerEventKey` (the original `chain.tx.finalized` idempotency key, threaded through from
`ChainEventConsumer`).

**`InvoiceService.markHeldIfPaid(Long invoiceId, Instant now)`** → `boolean` — atomic
`UPDATE ... WHERE state = 'PAID'` (Finding 9 — a named method, delegating to
`updateStateIfCurrentState` internally if convenient, but never called as a raw generic update
from `ReceiptService` directly).

**`ChainEventConsumer.onChainTxFinalized`'s own restructuring (Finding 7, explicit ordering):**
```java
public void onChainTxFinalized(String rawJson) {
    ChainTxFinalized event = deserialize(...);
    if (event == null) return;

    Optional<FinalizationOutcome> outcome = transactionTemplate.execute(status -> {
        if (!dedupe(event.idempotencyKey())) return Optional.empty();
        Optional<InvoiceWatchView> invoice = resolveInvoiceForFinalization(event);
        if (invoice.isEmpty()) return Optional.empty();
        Optional<FinalizationOutcome> result = paymentService.applyFinalized(invoice.get(), event);
        result.ifPresent(o -> invoiceService.markOutcomeIfOpenOrExpired(
                o.invoiceId(), toInvoiceState(o.amountDiscrepancy()), clock.instant()));
        return result;
    });

    outcome.filter(o -> o.amountDiscrepancy() == null)   // PAID only, per the resolved R17 scope
            .ifPresent(o -> receiptService.issueIfEligible(o.paymentId(), event.idempotencyKey()));
}
```
(Exact field/method names above are illustrative, confirmed against real signatures at Phase 5.)

**`ReceiptService` gains an injected `Clock`** (Finding 8), used for every `now` passed to the
new repository methods.

## Final scope, files, and acceptance criteria (carried from Phase 2 TIB, as amended above)

**Scope:** unchanged. **Files to Create:** `receipt/ReceiptService.java`,
`receipt/ReceiptIssueResult.java`, `payment/PaymentReceiptView.java`,
`invoice/InvoiceReceiptView.java`. **Files to Modify:** `payment/PaymentRepository.java` (new
`HELD` update query), `payment/PaymentService.java` (new `findReceiptPrimitives`/
`markHeldIfFinalized`), `invoice/InvoiceRepository.java` (new `HELD` update query),
`invoice/InvoiceService.java` (new `findReceiptPrimitives`/`markHeldIfPaid`),
`consumer/ChainEventConsumer.java` (the restructuring above).

**Acceptance Criteria** (AC1–AC6 from Phase 2, amended):
- AC1–AC5: unchanged in substance.
- AC6 (amended, Finding 6 clarity fix): `AttestationRefusedException` (409) and
  `CryptoServiceUnavailableException` are both logged (at `WARN`/`ERROR` respectively) and
  result in `ReceiptIssueResult.Failed` with **no state change at all** — distinct from
  `AttestationResult.Blocked`, which **does** trigger the `HELD` transition. Neither failure
  path is retried from this code path (disclosed gap, unchanged from Phase 2).
- **AC7 (new, Finding 2):** the two `HELD` updates are all-or-nothing — a split result
  (exactly one succeeds) throws and rolls back, never left partially applied.
- **AC8 (new, Finding 4):** a successful `FINALIZED → HELD` transition writes a
  `VerificationRecord`.

## Open Questions

None remaining.
