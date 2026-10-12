# payment · T04 · Phase 0 — Repository Understanding

## 1. Architecture summary (planned, per spec — T01/T02/T03 built skeleton + schema + config)

Per `agents.md` "Events & messaging": every state change other services care about publishes
through the outbox, in the same transaction as the DB write; a relay delivers to Kafka; no direct
producer call from domain code. `design.md` §4c names the exact `EventTopics` mapping: `invoice`
→ `payments.invoice.created`, `payment-seen` → `payments.payment.seen`, `payment-finalized` →
`payments.payment.finalized`, `receipt` → `payments.receipt.issued`.

Current real state: `services/payment` has a pom (T01), the `payments` schema + `payment_app`
role migrated for real (T02), and a full config/resource-server baseline (T03, 68/68 tests
green). No entity, no Kafka producer, nothing event-related exists yet — this is the first task
to touch Kafka at all.

## 2. Existing code this task touches

Nothing in `services/payment/src/main/java` beyond `PaymentServiceApplication.java` and the
`common`/`common.config` packages (T03) exists yet. This task adds a new `events` package.

## 3. Established patterns to follow — AND one real, load-bearing schema difference found by
direct comparison, not assumed

- **Structural template, confirmed real and complete in `services/crypto`** (the only sibling
  with a comparable outbox-publishing role — `notification-service` has **no** `outbox` table
  and **no** `Outbox*` class anywhere, confirmed by direct search; it's primarily a Kafka
  *consumer*, per its own `agents.md`, not a useful T04 precedent here): `OutboxEvent` (`@Entity`),
  `OutboxEventRepository` (package-private `JpaRepository`), `OutboxPublisher` (`@Component`,
  serializes + saves inside the caller's own transaction, no `@Transactional` of its own — joins
  whatever's already open), `OutboxRelay` (`@Scheduled`, at-least-once, batch of 100, send-then-
  mark ordering, continues past a single row's failure, no ShedLock), `EventTopics` (a small,
  explicit `Map`, fails loudly on an unmapped aggregate type), `KafkaProducerConfig` (explicit
  `ProducerFactory`/`KafkaTemplate<String,String>` beans, avoiding Boot's ambiguous
  autoconfigured `KafkaTemplate<Object,Object>`).
- **Real, load-bearing finding, not assumed: payment's own already-migrated `outbox` table has
  no `idempotency_key` column.** Verified directly against `V1__payments_baseline.sql` (T02,
  immutable per Flyway convention): `id, aggregate_type, aggregate_id, event_type, payload,
  created_at, published_at` — seven columns, no eighth. Crypto's own `OutboxEvent`/
  `OutboxPublisher` require a deterministic idempotency key as a mandatory parameter (its own
  L5), backed by a `UNIQUE` column crypto's own `outbox` table actually has. **Payment's own task
  4 text itself confirms this is intentional, not an oversight**: it says "Add `OutboxPublisher`...
  and `EventTopics`" with no mention of "deterministic idempotency key," unlike crypto's
  otherwise near-identical task-4 sentence, which explicitly names it. This matches
  `agents.md`'s own, more precisely-scoped wording for this service: *"Consumers are idempotent
  and dedupe on the event key"* — about **consumed** `chain.tx.*` events (L5's own `processed_events`
  table, a separate mechanism, not built until task 11), not published ones. Payment's own L8
  ("Outbox for every publish") carries no idempotency-key requirement of its own. **Conclusion:
  payment's own `OutboxPublisher.publish(...)` signature must NOT take an idempotency-key
  parameter** — mirroring crypto's structure, not its exact method signature. This is the same
  "diff the real target schema before assuming a 1:1 port" lesson crypto's own T04 already
  learned once (auth's `OutboxEvent` vs. crypto's own table), now independently re-confirmed a
  second time for a different pair of services.
- **`OutboxRelay`'s own relay-interval property** (`themistra.crypto.outbox.relay-interval-ms`,
  crypto's own naming) implies this task also needs its own `themistra.payment.outbox.relay-
  interval-ms` config key — not named in `design.md` §4c's own five property groups (T03's own
  scope), and not named in this task's own statement either. Flagging for Phase 1/2, not assumed.

## 4. Testing conventions

Task 4's own text names exactly one thing to test: "Unit-test aggregate→topic routing (R30)" —
the named test is `shouldRouteEachPublishedEventToItsPaymentsTopic` (header's own
`package.md` §8 citation). This is a plain-JUnit test of `EventTopics.forAggregateType(...)`
directly, no Spring context, no Docker — mirroring crypto's own `EventTopics` test shape (not yet
read in full; Phase 1/5 will confirm). `OutboxPublisher`/`OutboxRelay`/`KafkaProducerConfig`
themselves are infrastructure this task also builds (per the T01-style "necessary implied
infrastructure" precedent — crypto's own `OutboxRelay`/`KafkaProducerConfig` were built in its T04
despite not being named in its own one-sentence task text either) but are not the task's own
*named* test target; whether they get their own tests here or are deferred is for Phase 1/2 to
decide, not assumed.

## 5. Known gaps / unknowns

- **I do not know** whether `OutboxRelay`'s own relay-interval config key belongs in this task
  (since it's infrastructure this task builds) or was implicitly expected to already exist from
  T03 (it wasn't — confirmed, T03's own five property groups don't include it). Flagging for
  Phase 1/2.
- **I do not know** whether this task should also add a Testcontainers-backed integration test
  proving an `OutboxEvent` actually reaches a real Kafka topic (mirroring crypto's own eventual
  `OutboxTransactionIntegrationTest`-style precedent, built across its own T04 lineage) or whether
  that's deferred to a later task, since task 4's own text names only the one unit test. Flagging
  rather than assuming either way.
