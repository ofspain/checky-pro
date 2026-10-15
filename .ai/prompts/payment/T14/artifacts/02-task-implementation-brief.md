# payment · T14 · Phase 2 — Task Implementation Brief

## Task

Idempotency & ordering tests. Testcontainers: duplicate delivery is a no-op (R13);
stale/out-of-order events are ignored (R14); per-payment ordering is preserved (O6).

## Purpose

The first real (non-mocked) proof that `ChainEventConsumer`'s own wiring — dedupe, state
transitions, staleness handling — behaves correctly against a live Postgres and a real Kafka
broker, closing the gap every prior task's own Mockito-level tests could never close.

## Decision 1 — one combined test class

`ChainEventConsumerIdempotencyAndOrderingIntegrationTest`, in `com.themistra.payment.consumer`
(package-private access to `ProcessedEventRepository`), mirroring
`AuthEventConsumerIntegrationTest`'s own single-class/multiple-`@Test`-methods shape exactly —
shared Testcontainers setup, shared seeding helpers, one `KafkaTemplate`, one dedicated
`spring.kafka.consumer.group-id`.

## Decision 2 (a genuine finding, flagged for real Phase 3 scrutiny) — R14's test scenario must be cross-event-type, not same-event-type

**Found by actually trying to design the test, not assumed:** every `chain.tx.*` schema's own
idempotency key is **fixed per `(chain, txHash, eventtype)`** — confirmed by direct read of all
four schema files (`...:confirmed`, `...:reorged`, `...:finalized`, `...:seen`, no occurredAt/
payload component). `chain.tx.confirmed`'s own schema description additionally states it is
"emitted via the outbox **exactly once**" per transaction. This means a *second*
`chain.tx.confirmed` for the same transaction — the natural way to exercise
`PaymentService.applyConfirmed`'s own "a lower confirmation count is stale, ignore it" branch —
would carry the **identical** idempotency key as the first, and dedupe would reject it as a
plain duplicate *before ever reaching the confirmation-count comparison at all*. A real,
dedupe-respecting end-to-end test cannot reach that specific branch by delivering two real
`chain.tx.confirmed` messages — only a mocked-repository unit test (already covered, T11) can,
by stubbing `insertIfNew` to return 1 twice.

**Resolution:** R14's own real, end-to-end-reachable staleness scenarios are **cross-event-type**
ones, where dedupe never blocks the second message because its idempotency key genuinely
differs:
1. `chain.tx.finalized` delivered for a `(chain, txHash)` with **no corresponding payment yet**
   (the "out-of-order" case named in the task's own text) — `applyFinalized`'s own "no known
   payment" branch (R14-adjacent, already named behavior).
2. `chain.tx.reorged` delivered for a payment that has **already reached `FINALIZED`** (stale
   relative to current state, R14's own literal wording) — `applyReorged`'s own stale branch.

Both are genuinely reachable via real, dedupe-respecting Kafka delivery and directly test R14's
own stated intent ("ignore it without changing state... record that it was ignored"), unlike the
same-event-type scenario, which real delivery structurally cannot reach.

## Decision 3 — seeding and collaborators

- **Seed a real invoice** via `@Autowired InvoiceService` + `InvoiceService.create(...)` (public,
  already does the right thing: validation, `watchId` assignment, the `invoice.created` outbox
  write) — not hand-crafted SQL.
- **`@MockBean CryptoClient cryptoClient`** — mirrors `InvoiceControllerTest`'s own established
  convention. An unstubbed Mockito mock's `unregisterWatch(UUID)` call (made by the real
  `onChainTxSeen` listener, inside the same transaction) is a silent no-op, not a thrown
  exception — safe by default, no stubbing needed unless a test wants to assert the call
  happened.
- **Dedupe verification**: `@Autowired ProcessedEventRepository` (package-private, same-package
  access) — `existsById(idempotencyKey)`.
- **Payment/invoice state verification**: raw JDBC against `payments.payments`/
  `payments.invoices` (no entity reachable from the `consumer` package), mirroring
  `InvoiceRepositoryIntegrationTest`'s/`SchemaBaselineMigrationIntegrationTest`'s own established
  `adminConnection()` pattern.
- **Publish**: `@Autowired KafkaTemplate<String, String>` (the already-registered
  `outboxKafkaTemplate` bean), keyed by `watchId` (the real partition key, O6), mirroring
  `AuthEventConsumerIntegrationTest`'s own `kafkaTemplate.send(topic, key, json)` shape exactly.
- **Verify**: `Awaitility.await().atMost(...).untilAsserted(...)` for positive assertions;
  `pollDelay(...).atMost(...)` for negative ones (proving something did *not* happen) — never
  `Thread.sleep`.

## Scope

**In:** one new test class (3+ `@Test` methods covering AC1-AC3). Zero production code.

**Out:** any new production class, migration, or contract file. A full attestation/receipt
end-to-end flow (task 26's own, much larger, later scope). Kafka partition-assignment testing
itself (O6's own Kafka-level guarantee is out of this service's control to test — only the
*consequence*, a correctly-handled in-order sequence, is in scope, per Phase 1's own framing).

## Business Rules / Locked Decisions

R13, R14, O6, L5 — unchanged from Phase 1, now with Decision 2's own cross-event-type resolution
folded in as how R14 is actually, reachably tested.

## Files to Create

- `services/payment/src/test/java/com/themistra/payment/consumer/ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java`

## Files to Modify / NOT to Modify

None modified — this task touches zero existing files. Nothing under `src/main` may be touched
at all (test-only task, Phase 6 production-code guardrail applies with zero exceptions here).

## Acceptance Criteria

- **AC1 (R13, named `shouldDedupeDuplicateChainEventsByEventKey`)** — the identical
  `chain.tx.seen` JSON published twice produces exactly one `processed_events` row for that key
  and exactly one `Payment` row for that `(chain, txHash)`.
- **AC2 (R14, named `shouldIgnoreOutOfOrderOrStaleChainEvents`)** — both cross-event-type
  scenarios from Decision 2: (a) `chain.tx.finalized` for an unknown payment leaves no payment
  row created; (b) `chain.tx.reorged` for an already-`FINALIZED` payment leaves its state
  unchanged.
- **AC3 (O6)** — a real `chain.tx.seen` → `chain.tx.confirmed` → `chain.tx.finalized` sequence,
  same `watchId`/partition key, ends with the payment in `FINALIZED` with the expected
  `amount`/`fromAddress`/`toAddress` field values, proving the real wiring handles the full
  forward sequence correctly end-to-end.

## Required Tests

Exactly the three above (AC1-AC3) plus whatever helper/setup methods Phase 5 plans — no
additional named tests required by this task's own scope.

## Constraints

- No `Thread.sleep` anywhere — `Awaitility` only.
- Each test uses its own freshly-generated `UUID`s (invoiceUuid, watchId, txHash) — no shared
  mutable state between test methods, mirrors `AuthEventConsumerIntegrationTest`'s own
  established convention for exactly this reason (test-order independence).
- `@SpringBootTest` with **no** custom `classes = ...` override (unlike
  `InvoiceRepositoryIntegrationTest`'s own narrow `TestConfig`) — this task needs the real,
  full `ChainEventConsumer` bean and its real `@KafkaListener` wiring, not a minimal JPA-only
  slice.

## Open Questions

**Decision 2 (the cross-event-type resolution for R14) is flagged for genuine Phase 3 Kimi
challenge** — a real finding about a tension between L5's own fixed idempotency-key design and
R14's own "lower confirmation count" framing, reasoned through explicitly, not assumed. No other
open questions remain.
