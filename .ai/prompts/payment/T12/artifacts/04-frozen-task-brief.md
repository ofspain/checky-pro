# payment · T12 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN** (approved by the user, 2026-10-14 — "Approve as written")

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T12 — Reorg handling |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |

## Disposition of each Phase 3 (Kimi) finding

### Finding 1 (Critical) — ACCEPTED, with a resolution Kimi didn't fully trace through

Confirmed by direct read: `PaymentService.applySeen`'s guard (`existsByInvoiceIdAndStateIn`,
T11 Phase 8 Finding 1) treats *any* `WATCHING` row as blocking a new payment — a reorged-back
row would wrongly block the very replacement transaction Decision 1 exists to allow.

**Resolution (two coordinated changes, both touching previously-frozen T08/T11 code —
disclosed forward, not rewriting those phases' artifacts):**
1. `PaymentService`'s guard changes from "any of `CREATED/WATCHING/SEEN/CONFIRMING`" to "any of
   `CREATED/SEEN/CONFIRMING`, **or** `WATCHING` with `reorgedAt IS NULL`." A fresh (never-reorged)
   `WATCHING` row still blocks (there is no such row in practice today — `WATCHING` is never
   actually persisted pre-T12, per T11's own Decision 1/Finding 5 note — but the condition is
   written generally, not special-cased to "T12 rows only").
2. `InvoiceRepository.findExpiredCandidatesWithoutInFlightPayment` (T08) gets the same
   refinement to its `NOT EXISTS` subquery: a `WATCHING` row only excludes an invoice from
   expiry when `reorged_at IS NULL`.

Change 2 is what actually closes the loop Kimi flagged in its own Finding 3 (see below) — it is
why a registration failure does **not** need a new "force to `HELD`" escape hatch.

### Finding 2 (Medium) — ACCEPTED outright

Both new `PaymentRepository` reversal methods set `reorged_at = :now` in addition to `state` and
`updated_at`. A test asserts `getReorgedAt()` is non-null after either reversal.

### Finding 3 (Medium) — ACCEPTED, but resolved differently than any of Kimi's three listed options

Kimi's own options (accept-and-alert / force to `HELD` / extend expiry) all assumed the invoice
stays genuinely stuck. It doesn't, once Finding 1's change 2 is applied: a `WATCHING(reorged)`
row with a failed re-registration simply rides its invoice's own **original** `expires_at` to
the existing T08 sweep, exactly like any other unresolved invoice. No new state transition, no
`HELD` special-case, no alert-only-no-resolution path. The only real consequence — disclosed,
not hidden — is that a registration failure means no replacement transaction can be detected for
whatever time remains before that `expires_at`; this is a bounded, self-resolving degradation,
the same severity class as the already-logged `crypto-service` watcher gap, not a fresh open
product question. **Kimi's framing ("permanently stuck unless an operator intervenes") is
corrected here: it is not permanent.**

### Finding 4 (Low/Medium) — ACCEPTED outright

`PaymentService.applyReorged` returns `Optional<PaymentState>` (empty = stale/ignored per R14;
present = the state the payment landed in). `ChainEventConsumer.onChainTxReorged` — not
`PaymentService` — calls `cryptoClient.registerWatch` and `invoiceService.updateWatchIdIfOpen`
when the returned state is `WATCHING`, exactly mirroring `onChainTxSeen`'s own existing
`unregisterWatch`-called-by-the-consumer shape. Both the register call and the watchId update
are deliberately **outside** the `@Transactional` boundary that commits the reversal (Decision 2,
Phase 2) — consistent with, not a new departure from, T06's own original `registerWatch`
precedent (runs outside/before any transaction), and the mirror image of `unregisterWatch`'s own
deliberate inside-the-transaction placement (T11 Decision 4) for the opposite reason (register
failing must never erase a true reversal; unregister failing must roll one back).

### Finding 5 (Low) — ACCEPTED outright

New `InvoiceRepository.updateWatchIdIfOpen(Long id, UUID newWatchId, Instant now)`: `UPDATE
invoices SET watch_id = :newWatchId, updated_at = :now WHERE id = :id AND state = 'OPEN'`,
mirroring T08's own `updateStateIfCurrentState` exactly. Zero rows updated is logged at `WARN`
and otherwise ignored (the invoice resolved by some other path in the same window — not an
error).

### Finding 6 (Low) — ACCEPTED, narrowed

Adopted only the cheap half: `PaymentService.applyReorged` asserts the event's `chain`/
`tokenContractAddress` match the already-loaded `Payment`'s own stored values before reversing;
a mismatch is logged at `ERROR` and treated as stale (dropped, no reversal). **Not adopted**:
widening `InvoiceWatchView` to also carry `chain`/`tokenContractAddress` for a second,
invoice-side check — redundant, since `Payment` already stores the authoritative per-transaction
values and `InvoiceWatchView` is widened anyway (Decision 1) for the fields `registerWatch`
itself needs (`chain`, `tokenContractAddress`, `amount`, `expiresAt` — which happen to overlap,
so no further widening beyond what Decision 1 already requires).

## Final Scope

Unchanged from Phase 2 except as refined above. **In:** `ChainEventConsumer.onChainTxReorged`
(new listener), `ChainTxReorged` DTO, `PaymentService.applyReorged`, 2 new `PaymentRepository`
atomic reversal methods (setting `reorged_at`), a refined `PaymentService` existing-payment
guard, a refined `InvoiceRepository` sweep query, a new `InvoiceRepository.updateWatchIdIfOpen`,
`InvoiceWatchView` widened with `chain`/`tokenContractAddress`/`amount`/`expiresAt`.

**Out (unchanged):** `Receipt`/ledger/attestation (16-19), post-`ATTESTED` `HELD` reorg edge,
`R15`/`R16` (task 13), any `HELD`-transition escape hatch (rejected — Finding 3 resolution
above), any second invoice-side chain/token check (rejected — Finding 6 narrowing above).

## Business Rules / Locked Decisions

R11, R12, L11, L1, L5, L12, L14 — unchanged from Phase 1/2, now with Finding-1's guard/sweep
refinement folded in as how L14 ("one transaction per invoice") and R12 coexist without
deadlock.

## Files to Modify (final)

- `ChainEventConsumer.java` — new `onChainTxReorged` listener; calls `registerWatch`/
  `updateWatchIdIfOpen` outside the transaction when applicable.
- `PaymentService.java` — new `applyReorged` (returns `Optional<PaymentState>`); existing-payment
  guard refined (Finding 1).
- `PaymentRepository.java` — `markSeenIfConfirming`, `markWatchingIfSeen` (both set `reorged_at`).
- `InvoiceRepository.java` — `updateWatchIdIfOpen` (new); `findExpiredCandidatesWithoutInFlightPayment`
  refined (Finding 1, change 2).
- `InvoiceWatchView.java`/`InvoiceService.java` — widened.

## Files to Create

- `ChainTxReorged.java` (DTO).
- Test files for all of the above.

## Files NOT to Modify

- All migrations (`V1`-`V8`) — `reorged_at` already exists in `V1`, no new column/grant needed.
- Anything under `spec/`. `Receipt`/ledger (unbuilt).

## Acceptance Criteria (final)

- **AC1 (R11, named)** — `CONFIRMING → SEEN` reversal; `reorged_at` set; no watch action;
  `VerificationRecord` recorded.
- **AC2 (R12, named)** — `SEEN → WATCHING` reversal; `reorged_at` set; `VerificationRecord`
  recorded; consumer calls `registerWatch` with the invoice's own real chain/address/token/amount
  and **original** `expiresAt`; `Invoice.watchId` updated via `updateWatchIdIfOpen`.
- **AC3 (Finding 1)** — a new `chain.tx.seen` for a different transaction, arriving while the
  invoice's only payment is a reorged (`reorged_at` non-null) `WATCHING` row, is accepted and
  creates a new `Payment` row (not dropped by the existing-payment guard).
- **AC4 (Finding 1, change 2)** — an invoice whose only payment is a reorged `WATCHING` row is a
  candidate for `findExpiredCandidatesWithoutInFlightPayment` once its own `expires_at` passes.
- **AC5 (Finding 3)** — a `registerWatch` failure during AC2 does not roll back the DB reversal,
  is logged at `ERROR`, and the invoice remains eligible for AC4's own expiry path (no new state,
  no alert-only dead end).
- **AC6 (Finding 4)** — `registerWatch`/`updateWatchIdIfOpen` are called from
  `ChainEventConsumer`, never from `PaymentService`; `applyReorged` itself makes no outbound call.
- **AC7 (Finding 6)** — a `chain.tx.reorged` event whose `chain`/`tokenContractAddress` don't
  match the loaded `Payment`'s own stored values is dropped (stale), not reversed.
- **AC8 (R14 continuity)** — any other current state (including a not-yet-existing payment) is
  stale: `recordIgnored`/`WARN` + drop.
- **AC9 (L11)** — a test/comment proving no receipt-revocation code path exists to even reach.
- **AC10 (L5 continuity)** — dedupe-first, same transaction.

## Required Tests (final)

`shouldWalkBackwardConfirmingToSeenOnReorg` (named, AC1), `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm`
(named, AC2 — including the `registerWatch`/`updateWatchIdIfOpen` calls), a Finding-1 guard test
(AC3), a Finding-1 sweep test (AC4), a registerWatch-failure-doesn't-roll-back test (AC5), a
layering test confirming `PaymentService` makes no `CryptoClient`/`InvoiceService` calls (AC6,
likely an ArchUnit or Mockito no-interactions assertion), a chain/token-mismatch test (AC7), a
stale/unknown-payment test (AC8), a dedupe test (AC10), a DTO deserialization test, an
`ArchitectureTest` re-run.

## Open Questions

None remaining unresolved. All six Phase 3 findings have an explicit disposition above; the two
real design questions carried from Phase 0/1/2 (watch re-registration, failure handling) are both
resolved as part of those dispositions, not deferred.

---

**This is the decision packet for the human-approval gate. Approval needed on:**
1. The Finding-1 resolution touching previously-frozen T08 (`InvoiceRepository`) and T11
   (`PaymentService`) code.
2. The Finding-3 resolution (no `HELD` escape hatch; rely on the existing expiry sweep).
3. The Finding-6 narrowing (payment-side check only, no invoice-side duplicate check).
