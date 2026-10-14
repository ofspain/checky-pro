# payment · T13 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN** (approved by the user, 2026-10-14 — "Approve as written")

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T13 — Amount & poisoning integrity |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |

## Disposition of each Phase 3 (Kimi) finding

### Finding 1 (Medium) — ACCEPTED, widened

Confirmed real, via a slightly different path than Kimi's own framing: the common Q3/O2
scenario (payment genuinely in flight at sweep time) is already correctly excluded by T08's own
sweep query. The real path is T08's own **already-disclosed** residual race (sweep's atomic
`UPDATE` re-checks only `Invoice.state`, not the payment state, against a candidate list
snapshotted moments earlier) — a payment could transition into a blocking state in that narrow
window, and the sweep's own `UPDATE` would still fire. Without this fix, a payment that later
finalizes against such a wrongly-`EXPIRED` invoice would leave it stuck there forever, since
nothing previously cared about `Invoice.state` after sweep.

**Resolution:** `InvoiceService.markOutcomeIfOpenOrExpired` (renamed from `markOutcomeIfOpen` to
disclose the widening in its own name) tries `OPEN` first, then `EXPIRED`, as two atomic
attempts — mirrors `PaymentService.tryFinalizeFrom`'s own exact two-attempt shape. A `HELD`
invoice (not reachable yet - no task builds that path) or an already-terminal `PAID`/
`UNDERPAID`/`OVERPAID` invoice is correctly left alone either way.

### Finding 2 (Low/Medium) — ACCEPTED, redesigned (not merely amended)

Confirmed: the `watchId`-based lookup, while reasoned safe for this specific event type, is an
unnecessary fragile coupling when a strictly more robust alternative already exists -
`payment.invoiceId`, the same stable-foreign-key lesson T12's own Phase 8 review already taught.
Kimi's own suggested fix (get `invoiceId` from `applyFinalized`'s own return, then look up the
invoice afterward) doesn't actually work as stated — the discrepancy must be computed **before**
`applyFinalized`'s own atomic `UPDATE` runs (it's one of that query's own `SET` clauses), so the
invoice's `amount` must be available to the caller **before** calling `applyFinalized`, not after.

**Resolution:** new `PaymentService.findInvoiceIdByChainAndTxHash(String, String):
Optional<Long>` — a plain, read-only passthrough returning only the stable `invoiceId` (never an
entity, mirrors `ReorgOutcome`'s own shape). `ChainEventConsumer.onChainTxFinalized` calls this
first; if empty, logs `WARN` ("no known payment") and drops, exactly matching `applyFinalized`'s
own pre-existing message for the same condition. If present, calls
`invoiceService.findByIdAsWatchView(invoiceId)` (T12, reused as-is) to get the invoice, then
`applyFinalized(invoice, event)` as originally planned. **Disclosed trade-off**: this means the
same `Payment` row is looked up by `(chain, txHash)` twice per event — once lightly (just the
`invoiceId`) by the new method, once fully inside `applyFinalized` itself (for the real atomic
update's own `WHERE p.id = :id`). Accepted as a minor, low-traffic cost for completely
eliminating the watchId-staleness question, rather than relying on a safety argument at all.

### Finding 3 (Low) — REJECTED as a new enum type; ACCEPTED as named constants

A new `AmountDiscrepancy` enum would be a real, if small, refactor of `Payment.amountDiscrepancy`'s
own already-settled `String` representation (fixed at T11, when the column was first mapped) -
beyond what R15 requires, and the schema itself already constrains the real values (Kimi's own
point). **Lighter mitigation adopted instead**: the two literal values (`"UNDERPAID"`,
`"OVERPAID"`) are declared as named `private static final String` constants on `PaymentService`,
used everywhere instead of inline magic strings - removes the typo risk without a new type.

### Finding 4 (Low) — ACCEPTED, documentation only

A one-line note added to this brief's own Locked Decisions section (below) - no code change.

### Finding 5 (Low, High confidence) — ACCEPTED outright

`markOutcomeIfOpenOrExpired` calls `Objects.requireNonNull(outcome, ...)` first. A dedicated
null-argument test is added.

### Finding 6 (Low) — ACCEPTED, documentation only, forward note for task 24

No code change (contracts don't exist yet, task 24's own scope, per Kimi's own explicit
framing). Noted below as a suggested input for whoever authors `contracts/events/payments/
payment-finalized.v1.schema.json`.

## Final Scope

**In:** `PaymentService.findInvoiceIdByChainAndTxHash` (new), `applyFinalized`'s new signature
(`InvoiceWatchView`, `ChainTxFinalized`) and return (`Optional<FinalizationOutcome>`),
`FinalizationOutcome` (new record), `PaymentRepository.markFinalizedIfCurrentState`'s new
`amount_discrepancy` `SET` clause, `ChainEventConsumer.onChainTxFinalized`'s new two-step invoice
lookup + invoice-outcome call, `InvoiceService.markOutcomeIfOpenOrExpired` (new, reuses
`InvoiceRepository.updateStateIfCurrentState` twice, zero new repository code).

**Out (unchanged):** any migration, any new outbox event, `Receipt`/ledger (15-19), any
`PaymentController`/API surface, a new `AmountDiscrepancy` enum type (Finding 3, rejected).

## Business Rules / Locked Decisions

R15, R16, the Phase 0 scope addition - unchanged from Phase 1/2, now with Finding 1's widened
guard folded in as how the T08 sweep's own already-disclosed residual race and this task's own
`Invoice.state` write coexist without a permanent stuck state. **New note (Finding 4)**: no
outbox event is emitted for the `Invoice.state` transition - `design.md` L8's own event list has
no "invoice paid" entry; downstream consumers must infer it from `payment.finalized` plus their
own read model, or wait for a future task to add one explicitly. Not an oversight.

## Files to Modify (final)

- `PaymentService.java` — new `findInvoiceIdByChainAndTxHash`; `applyFinalized`/`tryFinalizeFrom`
  new signatures/bodies; two named `String` constants for the discrepancy values.
- `PaymentRepository.java` — `markFinalizedIfCurrentState`'s `@Query` gains `amount_discrepancy`.
- `ChainEventConsumer.java` — `onChainTxFinalized`'s new two-step lookup + outcome call.
- `InvoiceService.java` — new `markOutcomeIfOpenOrExpired` (null-checked, two-attempt).

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/payment/FinalizationOutcome.java`
- Test files for all of the above.

## Files NOT to Modify (final)

- Any migration (`V1`-`V8`) — all columns/enum values already exist.
- `InvoiceState`/`PaymentState` enums (no new value). `InvoiceRepository.java` (zero new query).
- `PaymentFinalizedPayload.java` (Finding 6 - forward note only, not a T13 change).
- Anything under `spec/`. `Receipt`/ledger (unbuilt). Any new outbox event/contract file.

## Acceptance Criteria (final)

- **AC1 (R15, named)** — under/exact/over sets `Payment.amountDiscrepancy` to
  `"UNDERPAID"`/`null`/`"OVERPAID"` correctly, computed once, as part of the one atomic
  `FINALIZED` update.
- **AC2 (scope addition, Finding 1 widened)** — the same three outcomes transition
  `Invoice.state` from either `OPEN` **or** `EXPIRED` to `UNDERPAID`/`PAID`/`OVERPAID`; any other
  current invoice state (`HELD`, or already a terminal paid state) is left alone, `false`
  returned, no error.
- **AC3 (R16, named)** — the already-existing persist+outbox-surface behavior, now with its own
  correctly-named test.
- **AC4 (R14 continuity)** — a stale/unknown-payment event touches neither
  `amountDiscrepancy`/`Invoice.state` nor computes a discrepancy.
- **AC5 (L12)** — `PaymentService` makes no `InvoiceService`/`CryptoClient` call;
  `findInvoiceIdByChainAndTxHash` returns only a `Long`, never an entity.
- **AC6 (L6)** — no floating-point type anywhere in the new code (`ArchitectureTest` re-run).
- **AC7 (Finding 2)** — the invoice lookup for `chain.tx.finalized` uses `payment.invoiceId`
  (via the new read method), never `event.watchId()`.
- **AC8 (Finding 5)** — `markOutcomeIfOpenOrExpired(null, ...)` throws `NullPointerException`.

## Required Tests (final)

`shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount` (named, 3 cases), an
`Invoice.state`-from-`OPEN` test and an `Invoice.state`-from-`EXPIRED` test (AC2/Finding 1), a
non-eligible-invoice-state guard test, a null-outcome test (AC8),
`shouldSurfaceAddressPoisoningFlagFromObservation` (named), a stale/unknown-payment continuity
test, an unknown-payment consumer test (AC7's own "no known payment" path), an
`ArchitectureTest` re-run.

## Open Questions

None remaining. Both real design questions (the invoice-lookup mechanism, and the sweep-race
interaction) were resolved above with concrete, reasoned fixes - not deferred.

---

**This is the decision packet for the human-approval gate. Approval needed on:**
1. The Finding 2 redesign — an extra read-only lookup per `chain.tx.finalized` event, trading a
   small efficiency cost for eliminating the watchId-staleness question entirely.
2. The Finding 1 widening — `Invoice.state` can now transition from `EXPIRED` too, closing a
   rare but real stuck-state path stemming from T08's own already-disclosed residual race.
3. The Finding 3 rejection — no new `AmountDiscrepancy` enum type; named `String` constants
   instead.
