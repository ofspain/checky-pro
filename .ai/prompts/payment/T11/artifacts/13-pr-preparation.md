# payment · T11 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch. This artifact is the consolidated summary of that
already-committed history — `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T11: idempotent consumers (ChainEventConsumer)`

## Commit message

```
payment-service T11: idempotent consumers (ChainEventConsumer)

Adds ChainEventConsumer: one @KafkaListener per chain.tx.{seen,confirmed,
finalized} topic, resolving design.md's own open O6 question (each
schema's own producer-side partition key is watchId, so per-payment
ordering is preserved within each topic). Dedupes via processed_events
(native INSERT...ON CONFLICT DO NOTHING, mirroring notification-
service's own proven IdempotencyGuard exactly) before any other effect,
inside the same transaction.

Adds the Payment entity (born already SEEN - CREATED/WATCHING are real
PaymentStateMachine edges no code ever actually transitions through,
since R6's own watch-ack requirement is a confirmed, disclosed
specification gap no task in this 29-task breakdown implements - decided
directly by the user, not assumed). Every state/field change goes
through PaymentRepository's own atomic, conditional UPDATE queries,
never a load-mutate-save - chain.tx.seen/confirmed/finalized are three
separate Kafka topics with no cross-topic ordering guarantee, so a
detached-entity merge would reintroduce T08's own lost-update race.

Two Kimi review passes found a genuine architectural gap needing a real
decision (R6, above) and a confirmed high-severity omission: the frozen
brief's own design explicitly named the race where a second chain.tx.seen
could create a competing Payment row for an invoice already in flight,
but the check was never actually implemented until Kimi's Phase 8 review
caught it. Self-review (Phase 7) separately caught and fixed a genuine
audit-trail race: an earlier design let a concurrent confirmation
transition corrupt the recorded fromState in the verification log.

InvoiceWatchView (new) closes a real ArchUnit violation Phase 6 found by
running the existing T09 rule against this task's own first attempt:
receiving the real Invoice entity and calling its own methods from a
different feature module violates L12 regardless of how the instance
arrived - InvoiceService.findByWatchId now returns this plain projection
instead.

Spec: spec/payment-service/tasks.md task 11. Requirements: R7, R7a, R8,
R9, R10, R13. Locked decisions: L1, L5, L12, L14.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/payment/src/main/java/com/themistra/payment/payment/Payment.java`
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java`
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java`
- `services/payment/src/main/java/com/themistra/payment/payment/event/{PaymentSeenPayload,PaymentFinalizedPayload}.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/{ProcessedEvent,ProcessedEventRepository}.java`
- `services/payment/src/main/java/com/themistra/payment/consumer/dto/{ChainTxSeen,ChainTxConfirmed,ChainTxFinalized}.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceWatchView.java` (disclosed addition — Phase 6)
- `services/payment/src/main/resources/db/migration/V7__payment_app_payments_table_write_grant.sql`
- `services/payment/src/main/resources/db/migration/V8__payment_app_processed_events_grant.sql`
- Corresponding test files: `PaymentServiceTest.java`, `ChainEventConsumerTest.java`, `ChainEventDtoDeserializationTest.java`

**Modified**
- `services/payment/src/main/java/com/themistra/payment/invoice/{InvoiceRepository,InvoiceService}.java`
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`

**Process artifacts (not application code)**
- `.ai/prompts/payment/T11/artifacts/00-*.md` through `12-*.md`.

## Summary

Payments now actually move: a real on-chain sighting creates a tracked payment, confirmations
accumulate, and finalization persists the real observed amount/addresses — the first task where
this service reacts to the outside world rather than only reacting to its own merchant-facing
API. Two real defects (one architectural gap needing a human decision, one confirmed
implementation omission) were found and resolved before merge, not shipped silently.

## Testing performed

- `mvn -pl services/payment -am compile` / `test-compile` — clean throughout.
- 33 new tests added this task.
- **229/229 non-Docker-dependent tests passing**, up from 196 before this task.
- Mutation-verified the Finding-1 existing-payment-row guard; the Phase 7 self-caught `fromState`
  race fix is covered directly by `shouldTransitionToFinalizedFromConfirming`/
  `...FromSeenWhenNeverConfirmed`, each asserting the exact, correct `fromState`.
- Real repository-level Testcontainers proof deferred — Docker unavailable this session, same
  disclosed, carried-forward gap as T08/T09/T10's own equivalents.

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 11 — "Idempotent consumers."
- **Requirement IDs:** R7, R7a, R8, R9, R10, R13.
- **LOCKED decisions:** L1, L5, L12, L14.

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T11.**
