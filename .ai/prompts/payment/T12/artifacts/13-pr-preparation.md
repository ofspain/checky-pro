# payment · T12 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** this task, like T09/T10/T11 before it, was executed directly on the
long-running `spec/service-specs-and-ai-framework` branch rather than a fresh per-task branch
off `main` — the established practice for this entire 14-phase pipeline across every prior
payment-service task this session. `main` itself was never touched and stays deployable. The
attribution trailer below uses this session's own current identity (Claude Sonnet 5), not the
generic placeholder text baked into this prompt template's own header.

## Commit title

`payment T12: wire chain.tx.reorged to the backward state transitions (R11, R12)`

## Commit message

```
payment T12: wire chain.tx.reorged to the backward state transitions (R11, R12)

Add the fourth ChainEventConsumer listener, onChainTxReorged, completing the
state machine's own two reversal edges (CONFIRMING -> SEEN, SEEN -> WATCHING)
first defined in T09 but never exercised by real code until now.

PaymentService.applyReorged returns an Optional<ReorgOutcome> (paymentId,
invoiceId, newState) rather than calling CryptoClient/InvoiceService itself,
keeping the service a pure payment-state coordinator; ChainEventConsumer owns
the best-effort replacement-watch registration a SEEN -> WATCHING reversal
needs, since the original watch was already unregistered on first SEEN
(R7a/L14, T11) and crypto-service has no "reactivate" path. The registration
runs after the DB-side reversal commits (an explicit TransactionTemplate, not
a self-invoked @Transactional method, since self-invocation bypasses Spring's
AOP proxy), looked up by the payment's own stable invoiceId rather than the
reorg event's own watchId, which can go stale across a cross-partition-delayed
delivery once a different reorg on the same invoice has already rotated it.

Folds in a guard refinement T11's own existing-payment check needed but didn't
have: a reorged-back WATCHING row (reorgedAt set) no longer blocks a
replacement chain.tx.seen, and the T08 expiry sweep treats such a row the same
way, so a failed or never-attempted replacement-watch registration degrades to
the invoice's own natural expiry rather than a permanent stuck state.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Production code:**
- `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java` — new `onChainTxReorged` listener, `registerReplacementWatch` helper, `TransactionTemplate` field/constructor parameter.
- `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxReorged.java` (new) — DTO matching `tx-reorged.v1.schema.json`.
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java` — new `applyReorged`; `applySeen`'s existing-payment guard split.
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java` — new `markSeenIfConfirming`, `markWatchingIfSeen`, `existsByInvoiceIdAndStateInAndReorgedAtIsNull`.
- `services/payment/src/main/java/com/themistra/payment/payment/ReorgOutcome.java` (new) — `applyReorged`'s own result record.
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java` — refined expiry-sweep query; new `updateWatchIdIfOpen`.
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java` — new `updateWatchIdIfOpen`, `findByIdAsWatchView` passthroughs.
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceWatchView.java` — widened with `chain`/`tokenContractAddress`/`amount`/`expiresAt`.

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/payment/PaymentServiceTest.java` — 8 new tests (both named reversals plus mismatch/stale/unknown/null/guard-refinement coverage).
- `services/payment/src/test/java/com/themistra/payment/consumer/ChainEventConsumerTest.java` — 9 new tests (dedupe, malformed payload, replacement-watch registration and its three failure/edge paths).
- `services/payment/src/test/java/com/themistra/payment/consumer/dto/ChainEventDtoDeserializationTest.java` — 1 new test.

No migration file — `payments.reorged_at` and `invoices.watch_id` already existed in `V1`.

## Summary

Reorg handling was the one gap left in the state machine T09 defined: both backward edges
existed but had no caller. This task gives `chain.tx.reorged` a real consumer, completing the
`SEEN ⇄ CONFIRMING ⇄ FINALIZED` pipeline's own error-recovery path. The harder half of the work
was `SEEN → WATCHING` (R12): recovering from a reorg means re-registering a watch crypto-service
has no way to simply "turn back on," which in turn meant widening the invoice-lookup view and
fixing a T11 guard that would otherwise have silently defeated the whole recovery path by
treating the reorged payment's own leftover row as still blocking.

## Testing performed

- `mvn -o compile`, `mvn -o test-compile`: clean.
- `mvn -o test -Dtest='!*IntegrationTest,!*IT'`: **246/246 passing** (229 pre-T12 baseline + 17
  new tests across Phases 6/10/11).
- Derived Spring Data query (`existsByInvoiceIdAndStateInAndReorgedAtIsNull`) verified directly
  via a standalone `PartTree` construction (Phase 7) — no test in this module's own suite boots
  a full Spring context without Docker, so this closed a real verification gap that compiling as
  plain Java alone would not have caught.
- Full repository/entity integration coverage (Testcontainers) deferred — Docker unavailable in
  this environment, consistent with every prior task's own precedent; recorded as a remaining
  risk in Phase 12, not a blocker to this PASS.

## Specification references

- **Task:** T12 — Reorg handling (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R11, R12 (scoped); R13, R14 (continuity, already in force since T11).
- **LOCKED decisions:** L11 (scoped); L1, L5, L12, L14 (continuity — all reconfirmed in Phase
  12's own traceability matrix, L5/L14 with disclosed mechanism differences, not violations).
