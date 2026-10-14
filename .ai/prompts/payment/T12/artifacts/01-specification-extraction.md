# payment · T12 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T12 — Reorg handling |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

- **R11** — a `chain.tx.reorged` event for a payment in `CONFIRMING` walks it back to `SEEN` and
  records the reversal. No watch action — `SEEN` correlates by `(chain, txHash)`, independent of
  any live watch.
- **R12** — a `chain.tx.reorged` event for a payment in `SEEN` (not yet confirmed) walks it back
  to `WATCHING` and records the reversal. **Open question carried from Phase 0**: whether this
  also requires registering a new watch, since the original was already unregistered on first
  `SEEN` (R7a/L14) and there is no "reactivate" path.

## Locked Decisions

- **L11** — reorg is a first-class transition, not an error. Because no receipt exists before
  `FINALIZED`, a reorg before finality never requires revoking a receipt — this task's own two
  reversals (`CONFIRMING→SEEN`, `SEEN→WATCHING`) are both pre-finality, so this holds trivially
  (no `Receipt` entity/mechanism exists anywhere in this codebase yet — tasks 16-19's own scope).
  The post-`ATTESTED` half of L11 (`ATTESTED→HELD`, ops-alerted) has no caller until task 19
  exists — confirmed out of this task's own scope (`R11`/`R12` name only the two pre-finality
  reversals).
- **L1** (context, T09) — both reversal edges already exist in `PaymentStateMachine`'s own fixed
  graph; this task is their first real caller.
- **L5** (context) — same dedupe-first, same-transaction discipline as every other
  `ChainEventConsumer` listener.
- **L12** (context) — same module-boundary discipline (`InvoiceWatchView`, never the real
  `Invoice` entity, if a watch re-registration touches `Invoice`-sourced data).
- **L14** (real tension, carried from Phase 0) — "one transaction per invoice." If `R12`'s own
  resolution is to re-register a watch, this re-opens the possibility of a *replacement*
  transaction being matched for the same invoice — which is not a violation of L14's own intent
  (still at most one *successful* transaction satisfies the invoice; the original, reorged-out
  transaction no longer counts), but needs to be reasoned through explicitly in Phase 2, not
  assumed.

## Files involved

**New (this task's own scope):**
- No new entity/repository/service class — this task extends existing T11 infrastructure only.
- Test files for the new listener method and repository methods.

**Existing, to extend:**
- `ChainEventConsumer.java` — new `onChainTxReorged` listener (4th topic).
- `ChainTxReorged.java` (new DTO, `consumer/dto` package) — mirrors the existing 3 DTOs, matching
  `contracts/events/chain/tx-reorged.v1.schema.json`'s own field set exactly (no `confirmations`,
  no addresses, no amount — confirmed at T11's own Phase 0).
- `PaymentService.java` — a new `applyReorged` method.
- `PaymentRepository.java` — new atomic conditional `UPDATE` method(s) for the two reversals.
- `InvoiceWatchView.java`/`InvoiceService.java`/`InvoiceRepository.java` — likely widened or
  extended, **contingent on Phase 2's own resolution of the re-registration question**.
- `CryptoClient.java` — its existing `registerWatch` method is the likely call site if
  re-registration is the resolved design; no new method expected there.

**Not involved:**
- `Receipt`/ledger/attestation (tasks 16-19).
- The post-`ATTESTED` `HELD` reorg path (no caller exists; out of scope).
- `R15`/`R16` (amount/poisoning — task 13, unaffected by a reversal).

## Dependencies

`PaymentStateMachine` (T09), `VerificationRecordService` (T10), `PaymentRepository` (T11,
extended), `CryptoClient` (T08, possibly its `registerWatch` method for the first time since
T06), `InvoiceWatchView`/`InvoiceService` (T11, possibly extended).

## Acceptance Criteria

- **AC1 (R11, named `shouldWalkBackwardConfirmingToSeenOnReorg`)** — a `chain.tx.reorged` event
  for a `CONFIRMING` payment moves it to `SEEN`, records the reversal, no watch action.
- **AC2 (R12, named `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm`)** — a
  `chain.tx.reorged` event for a `SEEN` payment moves it to `WATCHING`, records the reversal, and
  (pending Phase 2's own resolution) possibly registers a new watch.
- **AC3 (R14, general dedupe/stale continuity from T11)** — any other current state (including
  `FINALIZED`/`ATTESTED`/`HELD`, and a not-yet-existing payment) is stale: `recordIgnored`/`WARN`
  + drop, mirroring T11's own established pattern exactly.
- **AC4 (L11)** — a test asserting no receipt-revocation code path is ever reached for either
  reversal (trivially true given no `Receipt` mechanism exists — a structural/documentation-level
  proof, not new runtime behavior).
- **AC5 (L5/R13, continuity)** — dedupe-first, same transaction, mirroring the other 3 listeners.

## Tests required

- `shouldWalkBackwardConfirmingToSeenOnReorg` (named, R11).
- `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` (named, R12).
- A stale/unknown-payment test (AC3).
- A dedupe test for the 4th topic (AC5, mirrors T11's own 3 identical tests).
- A DTO deserialization test (mirrors the 3 existing ones).
- An `ArchitectureTest` re-run (no new canary expected — no new entity, no new module).
- Whatever Phase 2 resolves for AC2's own watch-registration question needs its own dedicated
  test(s).

## Open Questions

**One real, significant architectural question, carried forward from Phase 0, not resolved
here — Phase 2's own job, inviting genuine Phase 3 Kimi challenge and likely a real Phase 4
decision:** does `SEEN → WATCHING` (R12) require registering a new watch for the invoice? No
`package.md` §11 entry covers this (confirmed by direct read — Q1-Q8 are all unrelated). The
disclosed crypto-service gap (Phase 0) does not block this task's own design work — this task's
job is to decide and implement the *correct* behavior given the event arrives, independent of
how reliably `crypto-service` actually delivers it in production.
