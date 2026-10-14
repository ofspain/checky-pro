# payment · T11 · Phase 7 — Self Review

Re-read every AC/Constraint in `artifacts/04-frozen-task-brief.md` against the real code.

## A real, self-caught defect: `applyFinalized`'s own `fromState` could be wrong under a race

**Found and fixed.** The original `markFinalizedIfCurrentState` took a two-state
`IN (SEEN, CONFIRMING)` list, with the caller recording a `priorState` read *before* the atomic
update ran. If a concurrent event changed the real state from `SEEN` to `CONFIRMING` in the
window between that read and the update, the update would still match the `IN` list and succeed
— but the `VerificationRecord` would record the *stale pre-read* `fromState` (`SEEN`), not the
real one (`CONFIRMING`) the row was actually in when the update matched it. This is exactly the
category of bug the atomic-update design (Phase 5) was meant to close, reintroduced at the
audit-trail layer by relying on a read instead of the update's own match.

**Fixed:** `markFinalizedIfCurrentState` now takes exactly one `expectedCurrentState`.
`applyFinalized` tries `CONFIRMING` then `SEEN` as two separate atomic attempts; whichever one
succeeds is the *exactly known* `fromState` — never inferred from a read that could already be
stale by the time the update runs. `FINALIZABLE_STATES` (no longer needed) removed.

## AC1/AC1a (Finding 1)

`applySeen` checks `invoice.state() != OPEN` first, before anything else, logging `WARN` and
returning `Optional.empty()` — confirmed by direct read. `ChainEventConsumer` correctly treats an
empty result as "nothing to unregister a watch for."

## AC2/Finding 6 (`unregisterWatch` last, inside the transaction)

Confirmed: `ChainEventConsumer.onChainTxSeen` calls `cryptoClient.unregisterWatch` as the last
statement in the method body, after `paymentService.applySeen` has already saved the `Payment`,
recorded the `VerificationRecord`, and published the outbox event.

## AC3/AC4/AC4a/AC4b/AC5 (the three-way confirmed/finalized split)

Traced `applyConfirmed`/`applyFinalized` end-to-end: the transition, refresh, and stale paths
each produce exactly the row/record shape the frozen brief specifies. The finalize-side bug above
was the only defect found in this logic.

## AC6/AC7 (idempotency)

`ChainEventConsumer.dedupe` runs first in every listener method, before any other effect —
confirmed. All three listener methods and all three `PaymentService` methods are `@Transactional`.

## AC8 (L1, no second enforcement)

`PaymentService` never imports `IllegalPaymentStateTransitionException`; its one `isPermitted`
call is the documented, defensive "our own invariant about T09's edge set still holds" guard, not
a redundant legality re-check.

## AC9 (Finding 5 — no `CREATED`/`WATCHING` row)

Confirmed: `Payment.createSeen` is the only factory method, and it hardcodes `state = SEEN`.

## AC10 (Finding 2 — `consumer`/`payment` never touch `InvoiceRepository`)

Confirmed by direct `grep`: neither package references `InvoiceRepository` anywhere. The deeper
issue Phase 6 caught (never receiving the `Invoice` entity itself either) is the real, stronger
form of this guarantee — `InvoiceWatchView` closes it structurally, not just by import-list
inspection.

## AC11/AC12

`ArchitectureTest`'s own 5 canaries pass (re-confirmed this phase). DTO deserialization tests
don't exist yet — Phase 10's own job, expected at this point in the pipeline.

## Verification performed this phase

- `mvn -pl services/payment -am compile` / `test-compile` — clean after the fix.
- Full non-Docker suite: 196/196 (unchanged — the fix is production-only, no test yet proves it;
  Phase 10 will add a dedicated regression test for the corrected `fromState` tracking).

## Net result

One real, self-caught defect fixed (the `fromState` race in `applyFinalized`). No other findings.
Proceeding to Phase 8 — per the Kimi handoff/wait discipline, stopping here to hand off.
