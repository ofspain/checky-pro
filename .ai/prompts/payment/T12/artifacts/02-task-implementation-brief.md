# payment · T12 · Phase 2 — Task Implementation Brief

## Task

Wire `chain.tx.reorged` to the backward transitions (R11, R12) and record the reversal; assert
no receipt exists to revoke (L11).

## Purpose

Closes the loop T09 opened: both reversal edges in the fixed state graph finally have a real
caller, and a reorg-invalidated payment doesn't leave its invoice permanently stuck.

## Decision 1 (the real question carried from Phase 0/1) — `SEEN → WATCHING` registers a new watch

**Proposed, to invite genuine Phase 3 challenge and a real Phase 4 decision — comparable in
weight to T11's own `R6`/Finding-1 decisions, not a default:**

`CONFIRMING → SEEN` (R11) needs **no watch action** — a `SEEN` payment correlates by
`(chain, txHash)`, independent of any live watch.

`SEEN → WATCHING` (R12) **registers a brand-new watch** (`CryptoClient.registerWatch`, reusing
the invoice's own `chain`/`payToAddress`/`tokenContractAddress`/`amount`/**original**
`expiresAt`) and stores the new `watchId` back onto the `Invoice` row. Rationale: the original
watch was already unregistered on first `SEEN` (R7a/L14) — `WatchService.register` always mints
a fresh `watchId` (confirmed by direct read of `crypto-service`; there is no "reactivate" path).
Without a new watch, the invoice has no way to ever detect a replacement transaction and is
permanently stuck: `InvoiceExpirySweepJob`'s own candidate query (T08) excludes any invoice with
a payment in `WATCHING` — a reorged payment left in `WATCHING` with no active watch can neither
resolve (no further events possible) nor expire (sweep excludes it). Registering a fresh watch is
the only way to give the invoice a real chance to resolve.

**Expiry uses the invoice's own original `expires_at`, not a freshly computed one** — extending
an invoice's total lifetime is a product decision beyond this task's own scope (the 60-minute
TTL, Q3, is a fixed, already-decided product fact). A consequence, disclosed not hidden: an
invoice already very close to (or past) its own expiry by the time a reorg is detected will have
`registerWatch` reject the call (`crypto-service`'s own `validateExpiresAt` requires `expiresAt`
to be strictly in the future) — handled per Decision 2 below, not treated as a crash.

**Why `InvoiceExpirySweepJob` (T08) never races this**: its own candidate query already excludes
any invoice with a payment in `WATCHING` — by the time a reorg event arrives for a `SEEN`
payment, the invoice is guaranteed still `OPEN` (protected by that same exclusion the whole time
the payment was non-terminal), so no extra invoice-state guard is needed before re-registering.

## Decision 2 — re-registration failure never rolls back the DB-side reversal

**A deliberate asymmetry with T11's own `unregisterWatch` design (Decision 4), disclosed with
rationale, not an oversight.** `unregisterWatch` failing rolls back the whole `SEEN` transition
(a safety invariant: never commit `SEEN` while the watch is still live). Here, the underlying
fact — a reorg happened — remains true regardless of whether re-registration succeeds; refusing
to record it because `crypto-service` is briefly unavailable (or the invoice's own expiry window
is too tight) would leave the `Payment` row claiming a state it's no longer really in. Proposed:
the DB-side reversal (`PaymentRepository` atomic update, `VerificationRecordService.record`)
commits unconditionally; `CryptoClient.registerWatch` is attempted *after*, as best-effort — a
failure is logged at `ERROR` (operationally significant: the invoice is now stuck with no active
watch) and swallowed, never propagated to roll back the already-correct DB state.

## Scope

**In:** `ChainEventConsumer.onChainTxReorged` (4th listener), `ChainTxReorged` DTO,
`PaymentService.applyReorged`, 2 new `PaymentRepository` atomic reversal methods, widening
`InvoiceWatchView` with `chain`/`tokenContractAddress`/`amount`/`expiresAt`, a new
`InvoiceRepository`/`InvoiceService` atomic `watchId` update.

**Out:** `Receipt`/ledger/attestation (tasks 16-19). The post-`ATTESTED` `HELD` reorg edge (no
caller exists). `R15`/`R16` (task 13).

## Business Rules

- R11 — `CONFIRMING → SEEN` reversal, no watch action, record the reversal.
- R12 — `SEEN → WATCHING` reversal, register a new watch (Decision 1), record the reversal.

## Locked Decisions

- **L11** — held trivially for both reversals (no `Receipt` mechanism exists anywhere yet); the
  post-`ATTESTED` half is out of scope (no caller).
- **L1** — both reversal edges already exist in `PaymentStateMachine`'s fixed graph.
- **L5** — dedupe-first, same transaction, mirrors the other 3 listeners exactly.
- **L12** — `InvoiceWatchView` widened, never the real `Invoice` entity.
- **L14** — held: still at most one *successful* transaction satisfies the invoice; a
  reorged-out transaction no longer counts, so a replacement being matched next is consistent
  with, not a violation of, "one transaction per invoice."

## Dependencies

`PaymentStateMachine` (T09), `VerificationRecordService` (T10), `PaymentRepository`/
`InvoiceWatchView`/`InvoiceService` (T11, extended), `CryptoClient.registerWatch` (T08, its
first caller since T06).

## Inputs/Outputs

Raw Kafka `String` on `chain.tx.reorged`. Outputs: `Payment` state reversal, a
`VerificationRecord`, and (R12 only) a new `Invoice.watchId` plus a `registerWatch` REST call.

## State Changes

`Payment.state` reversed (`CONFIRMING→SEEN` or `SEEN→WATCHING`); `Invoice.watchId` updated
(R12 only, best-effort); new `processed_events`/`verification_records` rows.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxReorged.java`
- Test files mirroring the above.

## Files to Modify

- `ChainEventConsumer.java` — `onChainTxReorged`.
- `PaymentService.java` — `applyReorged`.
- `PaymentRepository.java` — `markSeenIfConfirming`, `markWatchingIfSeen`.
- `InvoiceWatchView.java` — widened (4 new fields).
- `InvoiceService.java`/`InvoiceRepository.java` — `updateWatchId`.

## Files NOT to Modify

- `V1`-`V8` migrations (immutable; no new grant needed — `registerWatch` is a REST call, not a
  DB write, and `Invoice`/`payments.invoices` already has its own `UPDATE` grant since T08's
  `V6`).
- Anything under `spec/`. `Receipt`/ledger (unbuilt).

## Acceptance Criteria

- **AC1 (R11, named)** — `CONFIRMING → SEEN` reversal, no `CryptoClient` call, `VerificationRecord`
  recorded.
- **AC2 (R12, named)** — `SEEN → WATCHING` reversal, `VerificationRecord` recorded, `registerWatch`
  called with the invoice's own real parameters and *original* `expiresAt`, `Invoice.watchId`
  updated to the new value.
- **AC3 (Decision 2)** — a `registerWatch` failure during AC2 does not roll back the DB reversal;
  logged at `ERROR`.
- **AC4 (R14 continuity)** — any other current state is stale: `recordIgnored`/`WARN` + drop.
- **AC5 (L11)** — a test/comment proving no receipt-revocation code path exists to even reach.
- **AC6 (L5 continuity)** — dedupe-first, same transaction.

## Required Tests

`shouldWalkBackwardConfirmingToSeenOnReorg` (named), `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm`
(named, including the `registerWatch` call and `Invoice.watchId` update), a
registerWatch-failure-doesn't-roll-back test, a stale/unknown-payment test, a dedupe test, a DTO
test, `ArchitectureTest` re-run.

## Constraints

- **Transactions**: `onChainTxReorged` is `@Transactional`, mirroring the other 3 listeners —
  but `registerWatch` (R12 only) is deliberately best-effort/non-rolling-back (Decision 2), a
  genuine, disclosed exception to "every `CryptoClient` call affects transaction outcome."
- **Expiry**: reuses the invoice's own original `expires_at`, never recomputed.
- **No second enforcement of L1**: `PaymentService` still never duplicates `PaymentStateMachine`'s
  edge set — the atomic `UPDATE`'s own `WHERE` clause is the enforcement, with the same
  defensive `isPermitted` sanity check T11 already established.

## Open Questions

**Decision 1 and Decision 2 above are both real, significant architectural proposals — not
assumed defaults. Both are flagged for genuine Phase 3 Kimi challenge; Decision 1 in particular
is likely to warrant a real Phase 4 `AskUserQuestion`, not a rubber stamp.**
