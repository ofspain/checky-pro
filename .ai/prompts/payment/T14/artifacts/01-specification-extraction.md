# payment · T14 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T14 — Idempotency & ordering tests |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

- **R13** — the same `chain.tx.*` event (identified by `chain:txhash:eventtype`), delivered more
  than once, is applied at most once; every subsequent delivery is a no-op. Proven here against
  the real `processed_events` table and a real redelivered Kafka message, not a mocked
  repository call.
- **R14** — a stale/out-of-order `chain.tx.*` event (e.g. `seen` after `finalized`, or a lower
  confirmation count than already recorded) is ignored without changing state, and the ignore is
  itself recorded. Proven here by actually delivering events out of their natural order over a
  real broker and confirming the payment's own final state reflects only the legitimate
  transitions.
- **O6** (design.md, already resolved at T11 — not re-opened here) — per-payment ordering is
  preserved because every consumed topic's own partition key is `watchId`. This task proves the
  *consequence* of that guarantee (a realistic in-order sequence is handled correctly end-to-end
  by the real listener wiring), not the Kafka-level partitioning guarantee itself (out of this
  service's own control — it's the producer/broker's property, not something this service's own
  test suite can meaningfully assert on).

## Locked Decisions

- **L5** — "every consumed event is deduplicated on its deterministic key... inside the same
  transaction that applies the effect." This task's own R13 test is the first real (non-mocked)
  proof that the dedupe row and the effect genuinely commit together against a live Postgres.
- **L1/L12/L14** (context, continuity) — no new state-machine edge, no new cross-module access
  pattern, no new guard; this task writes zero production code, so none of these can be violated
  by construction.

## Files involved

**New:**
- A new Testcontainers-based integration test class (exact name/package settled in Phase 2),
  living in `com.themistra.payment.consumer` to reach the package-private
  `ProcessedEventRepository` directly, mirroring `ChainEventConsumerTest`'s own existing package
  placement.

**Existing, read-only (no production file is modified by this task):**
- `ChainEventConsumer.java` (T11-T13) — the real listener wiring under test.
- `ProcessedEventRepository.java` (T11) — the dedupe table, verified directly.
- `InvoiceService.create(...)` (T05/T06) — used to seed a real invoice to correlate events
  against, rather than hand-crafted SQL.
- `KafkaProducerConfig.java` (T04) — the already-registered `KafkaTemplate<String, String>` bean
  this task's own test publishes through.
- `payments.payments`/`payments.invoices` (schema, `V1`) — verified via raw JDBC, no entity
  reachable from the `consumer` package.

**Not involved:** any `src/main/java` file at all. Any migration. Any contract file (R13/R14/O6
are all already-contracted consumer behavior, not a new wire shape).

## Dependencies

`ChainEventConsumer`, `ProcessedEventRepository`, `InvoiceService`, `KafkaTemplate<String,
String>` (`KafkaProducerConfig`), `CryptoClient` (mocked out — must not make a real call to a
`crypto-service` that isn't running in this test's own scope, mirrors `InvoiceControllerTest`'s
own established `@MockBean CryptoClient` convention), Testcontainers `PostgreSQLContainer`,
Awaitility, the already-running local Kafka broker (`localhost:9094`, started this session via
`services/auth/compose.local.yaml`).

## Acceptance Criteria

- **AC1 (R13, named `shouldDedupeDuplicateChainEventsByEventKey`)** — the same `chain.tx.*`
  message, published twice to the real broker with the same idempotency key, produces exactly
  one applied effect (one `processed_events` row, one payment-state transition) — the second
  delivery is a genuine no-op proven against live Postgres, not a mocked `insertIfNew` return
  value.
- **AC2 (R14, named `shouldIgnoreOutOfOrderOrStaleChainEvents`)** — a `chain.tx.*` event that is
  stale relative to the payment's real current state (delivered out of its natural order over
  the real broker) leaves the payment's own state unchanged.
- **AC3 (O6)** — a realistic in-order `chain.tx.seen` → `chain.tx.confirmed` →
  `chain.tx.finalized` sequence, published for the same `watchId` (the real partition key), ends
  with the payment in `FINALIZED` with the expected field values — proving the real listener
  wiring handles the full forward sequence correctly end-to-end, not just each transition in
  isolation (already proven at the unit level by T11-T13's own tests).
- **AC4 (continuity)** — none of this task's own tests touch, mock, or assume anything about
  `CryptoClient`'s own real behavior beyond "it was called" / "it was not called" where relevant
  — the mock bean exists only to prevent a real outbound call, not to be a behavior under test
  here (that's T08/T11/T12's own already-closed scope).

## Tests required

- `shouldDedupeDuplicateChainEventsByEventKey` (named, AC1).
- `shouldIgnoreOutOfOrderOrStaleChainEvents` (named, AC2).
- An O6 ordering test (AC3) — exact name settled at Phase 2, following this codebase's own
  descriptive-name convention (not literally named in `package.md` §8, since O6 is a design
  decision, not a numbered requirement with its own named-test mapping).

## Open Questions

**None remaining as genuine blockers.** The real environmental blocker this task's own grounding
found (Docker unavailable) was resolved directly with the user before Phase 0 even completed —
not re-opened here. One real design question remains for Phase 2 to settle explicitly (not a
blocker): whether R13/R14/O6 fit most naturally in one combined test class (mirrors
`AuthEventConsumerIntegrationTest`'s own single-class, multiple-`@Test`-methods shape) or are
better split — reasoned through at Phase 2, informed by the established precedent.
