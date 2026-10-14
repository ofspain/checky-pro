# payment · T12 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T12 — Reorg handling |
| **Consumes** | — (entry phase) |

## 1. Architecture summary

As of T11, `payment-service` has a full event-driven pipeline: `ChainEventConsumer` (one
`@KafkaListener` per `chain.tx.{seen,confirmed,finalized}` topic), `Payment`/`PaymentRepository`
(atomic conditional updates only, no load-mutate-save), `PaymentService` (the business logic for
each transition), `PaymentStateMachine` (T09, enforces the fixed 10-edge graph), and
`VerificationRecordService` (T10, the audit trail). T12 is the natural extension: a 4th listener
for `chain.tx.reorged`, exercising `PaymentStateMachine`'s own two reversal edges
(`CONFIRMING→SEEN`, `SEEN→WATCHING`) for the first time since T09 defined them.

## 2. A significant cross-service finding from this task's own grounding — logged, not a blocker

**While grounding this task, found and verified by direct code read (not assumed) a likely
severe gap in `crypto-service`'s own `Watcher`/`WatcherRegistry`: unregistering a watch
(`WatchService.unregister` → `WatcherRegistry.reconcile`'s own `stopWatchersNoLongerRegistered`
→ `Watcher.stop()`) stops the ENTIRE watcher — both the address subscription for new matches
AND the same scheduled task that runs `checkForReorg()`/`pollFinalityFor()` for a transaction
already matched.** Since `payment-service`'s own R7a/L14 (T11) unregisters the watch the moment a
payment first reaches `SEEN`, this means `chain.tx.confirmed`/`chain.tx.finalized`/
`chain.tx.reorged` may never actually be emitted by `crypto-service` past that point in
production, once its own reconciliation loop catches up — a gap spanning T9-T12's own shared
assumption, not a `payment-service` code defect. **Put directly to the user; decided: log as a
tracked cross-service follow-up (saved to memory:
`crypto-service-watcher-unregister-stops-reorg-finality-tracking`), continue T12 as planned.**
T12's own job — correctly handling `chain.tx.reorged` *given that it arrives* — is unaffected by
whether `crypto-service` reliably emits it; that reliability gap is entirely on the emission
side, out of this task's own scope to fix.

## 3. Existing code this task touches

- **`PaymentStateMachine`/`PaymentState`** (T09) — both reversal edges already defined, never
  exercised by real code until now.
- **`VerificationRecordService`** (T10) — `record(...)` for the two reversal shapes; no new
  capability needed.
- **`ChainEventConsumer`** (T11) — needs a 4th `@KafkaListener` (`chain.tx.reorged`), mirroring
  the existing 3 exactly (dedupe first, same transaction).
- **`PaymentService`/`PaymentRepository`** (T11) — needs new atomic conditional `UPDATE` methods
  for the two reversals, mirroring `markConfirmingIfSeen`'s own exact shape.
- **`contracts/events/chain/tx-reorged.v1.schema.json`** (already read at T11's own Phase 0):
  required fields are `idempotencyKey`, `watchId`, `chain`, `txHash`, `tokenContractAddress`,
  `occurredAt` — no `confirmations`, no addresses, no amount. Correlation is by `(chain, txHash)`
  against the already-created `Payment` row (same as `confirmed`/`finalized`), not by `watchId`.
- **A real, consequential design question this task must resolve (not T11's, not assumed
  away): does `SEEN → WATCHING` (R12) require re-registering a new watch?** The original watch
  was already unregistered on first `SEEN` (R7a/L14) — if a reorg now invalidates that same
  transaction, the system has no active way to detect a replacement transaction for the same
  invoice unless it registers a brand-new watch (`WatchService.register` always mints a fresh
  `watchId`, confirmed by direct read — there is no "reactivate" path). `CONFIRMING → SEEN` (R11)
  needs no such action — a `SEEN` payment still correlates by `(chain, txHash)`, independent of
  any live watch. **A real Phase 2/3/4-weight decision, comparable to T11's own biggest
  findings — to confirm explicitly, not silently assumed either way.**
- **`InvoiceWatchView`** (T11) currently exposes only `id`/`invoiceUuid`/`state`/`payToAddress`/
  `watchId` — if re-registration is the resolved design, `CryptoClient.registerWatch` also needs
  `chain`/`tokenContractAddress`/`amount`/`expiresAt`, none of which this view currently carries.
  Widening it (or adding a second, narrower view) is this task's own likely consequence, not
  scope creep — to confirm in Phase 2.
- **`InvoiceRepository`/`InvoiceService`** would need a new atomic `watchId` update if
  re-registration is the resolved design — `Invoice` currently has no mutator for `watchId` at
  all (only set once, at creation).
- **"assert no receipt exists to revoke" (L11, task's own text)**: no `Receipt` entity/table
  grant exists anywhere in this codebase yet (confirmed — tasks 16-19's own scope). This reads as
  a *test assertion* proving the pre-finality reversal scope this task covers can never
  encounter a receipt to revoke at all (not a new runtime behavior) — the post-`ATTESTED` reorg
  edge (`ATTESTED→HELD`, L11's own other half) has no caller until task 19 exists, confirmed out
  of this task's own scope (`R11`/`R12` only name the two pre-finality reversals).

## 4. Established patterns to follow

Identical to T11's own Phase 0 findings: `AuthEventConsumer`-style listener shape, atomic
conditional `UPDATE` queries (never load-mutate-save, T08's own lesson), `InvoiceWatchView` for
any cross-module invoice data `consumer`/`payment` need (never the real `Invoice` entity).

## 5. Known gaps / unknowns

- **I do not know** whether re-registration (if that's the resolved design) should use the
  invoice's own *original* `expiresAt`, or compute a fresh one from "now" — the invoice could be
  close to its original expiry by the time a reorg is detected, potentially leaving a very short
  or already-passed window. A real design question for Phase 2.
- **I do not know** the exact shape of `PaymentRepository`'s own new reversal methods — single
  combined method or two separate ones, mirroring `applyConfirmed`'s own `markConfirmingIfSeen`
  precedent. Phase 2's job.
- Both the Phase 0/1 "`R6` orphaned" and "`Payment` born at `SEEN`" findings from T11 (already
  decided) remain in force here unchanged — not re-litigated.
