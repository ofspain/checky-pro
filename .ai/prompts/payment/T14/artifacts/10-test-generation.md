# payment · T14 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

T14's own task statement is itself entirely test infrastructure — Phase 6 already wrote the
real tests (explicitly sanctioned for a test-only task), and Phases 7/8/9 already reviewed and
strengthened them through two real review rounds. This phase's own job is the test manifest
itself; no further new test code is needed beyond what Phase 9 already closed out.

## Test manifest

| Test | Verifies |
|---|---|
| `shouldDedupeDuplicateChainEventsByEventKey` (named) | AC1/R13 — the identical `chain.tx.seen` message, redelivered over the real broker, produces exactly one `Payment` row and one `processed_events` row; the real dedupe row is checked directly via `ProcessedEventRepository`, not a mocked `insertIfNew` return value |
| `shouldIgnoreFinalizedForAnUnknownPayment` | AC2/R14 (scenario a) — `chain.tx.finalized` with no prior `seen` creates no payment row; the out-of-order case named literally in the task's own text |
| `shouldIgnoreReorgedForAnAlreadyFinalizedPayment` | AC2/R14 (scenario b) — `chain.tx.reorged` for an already-`FINALIZED` payment leaves its state unchanged |
| `shouldIgnoreOutOfOrderOrStaleChainEvents` (named) | AC2/R14 (scenario c) — `chain.tx.confirmed` for an already-`FINALIZED` payment leaves state unchanged; the real cross-topic race (`chain.tx.confirmed`/`chain.tx.finalized` are separate topics with no cross-topic ordering guarantee) — the only same-event-type-adjacent staleness scenario actually reachable via real, dedupe-respecting delivery |
| `shouldHandleTheFullForwardSequenceAndReachFinalized` | AC3/O6 — the real `seen → confirmed → finalized` sequence reaches `FINALIZED` with the correct `amount`/`from_address`/`to_address` persisted, and the invoice's own real state transition (`PAID`, via `InvoiceService.markOutcomeIfOpenOrExpired`, T13) also completes correctly |

## Why no additional tests are needed

- **R13**: the named test is the single canonical proof. No further case exists to add — the
  "duplicate delivery" scenario has exactly one shape (same key, redelivered).
- **R14**: all three realistically-reachable staleness scenarios (Phase 2 Decision 2, refined at
  Phase 4/8/9) are covered. Same-event-type staleness (e.g. a lower confirmation count, or a
  second genuine reorg) is contractually unreachable via real, dedupe-respecting Kafka delivery
  — already-dispositioned, not a gap (Phase 2 Decision 2, Phase 3 Finding 8).
- **O6**: the forward-sequence test is the realistically-testable proof available to this
  consumer. Kafka's own cross-topic/partition ordering guarantee itself is the broker's
  property, confirmed out of this service's own control to test (Phase 1's own framing,
  reaffirmed at Phase 4 Finding 6's own rewording of AC3).
- **Repository-level/Testcontainers-Kafka-container gaps**: none — this is the first task in
  the pipeline to actually run against the real stack rather than defer to it.

## Verification

`mvn -o compile`, `mvn -o test-compile`: clean. `mvn -o test -Dtest=ChainEventConsumerIdempotencyAndOrderingIntegrationTest`:
**5/5 passing**, confirmed stable across 3 consecutive runs post-split (12 total consecutive
passes across this task's own Phases 6/7/9/10 combined, counting both the pre-split 3-method and
post-split 5-method shapes). `mvn -o test` (full suite): **297/297 passing**.
