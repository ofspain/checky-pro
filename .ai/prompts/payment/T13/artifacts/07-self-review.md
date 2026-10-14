# payment · T13 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

Reviewed the Phase 6 diff against the frozen brief and `agents.md`. Findings only — no fixes
applied here. Checked specifically for a repeat of T11/T12's own JPA first-level-cache lesson,
since this task introduces a second lookup by `(chain, txHash)`.

## Findings

### 1. `onChainTxFinalized`'s body has grown to a 4-step pipeline, readability could improve

- **Issue:** the method now does: resolve `invoiceId` → resolve `InvoiceWatchView` → call
  `applyFinalized` → call `markOutcomeIfOpenOrExpired`, with an inline early-return/log after each
  of the first two steps. This is correct but denser than the other listeners' own bodies.
- **Severity:** Low (readability only — no correctness issue).
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java`
  (the rewritten `onChainTxFinalized`).
- **Recommendation:** optionally extract the first two steps into a small private helper (e.g.
  `resolveInvoiceForFinalizedEvent(ChainTxFinalized event): Optional<InvoiceWatchView>`), returning
  empty with the logging already done, so `onChainTxFinalized`'s own body reads as a flatter
  3-step pipeline. Not required — the current form is correct and each step is individually easy
  to follow.

### 2. The two `(chain, txHash)` lookups return the same cached `Payment` instance, not two independent reads

- **Issue:** `findInvoiceIdByChainAndTxHash` and `applyFinalized`'s own internal
  `findByChainAndTxHash` both run inside the same `@Transactional` boundary
  (`onChainTxFinalized`), i.e. the same Hibernate session. The second call still issues its own
  SQL (the query is by `(chain, txHash)`, not by primary key, so Hibernate cannot skip the
  round-trip), but upon materializing the row, Hibernate returns the *already-managed* instance
  from the first call rather than a freshly hydrated one. This is harmless here — `invoiceId` is
  set once at `Payment.createSeen` and never mutated — but it is worth naming precisely, since
  the codebase has twice before (T11 Phase 8 Finding 4, T13's own Decision 1 reasoning) had to
  reason carefully about exactly this caching behavior for a *mutable* field, where it would not
  be harmless.
- **Severity:** Low (informational — confirmed safe, not a defect).
- **Evidence:** `PaymentService.findInvoiceIdByChainAndTxHash` and `applyFinalized`, both calling
  `repository.findByChainAndTxHash` within the same transaction.
- **Recommendation:** no code change. Worth a one-line comment on `findInvoiceIdByChainAndTxHash`
  noting this explicitly, so a future reader relying on this same double-lookup pattern for a
  *mutable* field doesn't assume the same safety without re-checking.

## Positive confirmations

- `classifyDiscrepancy` uses `BigDecimal.compareTo` (not `equals`), correctly immune to any
  incidental scale difference — though none is possible here, since both `Invoice.amount` and
  `Payment.amount` are always `NUMERIC(78,0)`.
- `markOutcomeIfOpenOrExpired`'s two-attempt structure cannot double-fire: the first attempt
  succeeding short-circuits before the second is ever tried, and `applyFinalized`'s own atomic
  guard ensures a payment reaches `FINALIZED` (and therefore calls into this method) at most once.
- The "payment references a non-existent invoice" branch in `onChainTxFinalized` is correctly
  unreachable in practice (invoices are never deleted) and correctly logged at `ERROR`, not `WARN`.
- No new mutable shared state introduced anywhere; all beans remain stateless.
- `PaymentService` still has no `InvoiceService`/`CryptoClient` field at all (L12 preserved
  structurally, not just by convention).
- No floating-point type introduced anywhere in the new code (L6).
