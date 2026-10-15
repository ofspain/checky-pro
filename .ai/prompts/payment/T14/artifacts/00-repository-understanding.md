# payment · T14 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T14 — Idempotency & ordering tests |
| **Consumes** | — (entry phase) |

## 1. Architecture summary

As of T13, `payment-service` has a complete event-driven pipeline through `FINALIZED` plus
reorg recovery: `ChainEventConsumer` (4 `@KafkaListener`s), `PaymentService`/`PaymentRepository`
(atomic conditional updates only), `InvoiceService`/`InvoiceRepository`, `PaymentStateMachine`,
`VerificationRecordService`. T14 is explicitly and entirely a **test-only** task: no production
code changes. It proves, against the real stack (Postgres + a real Kafka broker — not mocks),
three properties every prior task's own unit tests have only proven at the Mockito level:
duplicate delivery is a no-op (R13), stale/out-of-order events are ignored (R14), and per-payment
ordering is preserved (O6).

## 2. A significant environment finding from this task's own grounding — now resolved

**Docker was down for this entire session until today (2026-10-15).** Since T14's whole scope is
Testcontainers/real-Kafka tests, this was a severe blocker, raised directly to the user before
starting. The user confirmed Docker is now available; started the shared local stack
(`docker compose -f services/auth/compose.local.yaml up -d` — Postgres on 5432, a real Kafka
broker on `localhost:9094`, the same compose file every service in this monorepo shares, already
matching this service's own `application.properties` default
`spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9094}`).

**While Docker was available, also fixed two pre-existing, out-of-scope test defects the user
asked to clear before starting T14** (not part of T14's own scope, logged here for continuity):
`InvoiceRepositoryIntegrationTest`'s own `TransactionRequiredException` (T08's own already-
tracked gap — fixed with a `TransactionTemplate`, not a bare `@Transactional`, since the test's
own assertions read via a separate raw JDBC connection and Spring's test-transaction rollback
default would otherwise hide the real update from them) and `SchemaBaselineMigrationIntegrationTest`'s
own staleness against T11's `V7`/`V8` grants (not a production defect — the grants are correct;
the test had simply never run against a live Postgres since roughly T07). **Full suite now
292/292, the first time this entire module has been fully green this session.**

## 3. Existing code this task touches (read-only — no production class is modified)

- **`ChainEventConsumer`** (T11/T12/T13) — the real `@KafkaListener` wiring this task tests
  end-to-end for the first time. All 4 topics (`chain.tx.seen/confirmed/finalized/reorged`)
  are in scope for R13/R14; O6 is best proven with a realistic `seen → confirmed → finalized`
  sequence on one topic set.
- **`ProcessedEventRepository`** (T11, package-private, lives in `com.themistra.payment.consumer`) —
  the dedupe table this task's own R13 test verifies directly. A new integration test class must
  live in the same package to autowire it (mirrors `ChainEventConsumerTest`'s own existing
  package placement).
- **`payments.payments`/`payments.invoices`** — no JPA entity reachable from outside their own
  packages (`Payment`/`Invoice` are both package-private-adjacent — `PaymentRepository`/
  `InvoiceRepository` themselves are package-private). State must be verified via raw JDBC
  against a real Postgres, mirroring `InvoiceRepositoryIntegrationTest`'s/
  `SchemaBaselineMigrationIntegrationTest`'s own established `adminConnection()` pattern.
- **`InvoiceService.create(...)`** (T05/T06, public) — the legitimate, already-built way to seed
  a real invoice (with a known `watchId`) for a test to correlate `chain.tx.*` events against,
  rather than hand-crafting a raw SQL insert.
- **`CryptoClient`** (T08/T11/T12) — a real dependency of `ChainEventConsumer`'s own constructor.
  Must be replaced with a mock bean in any full-context test (mirrors `InvoiceControllerTest`'s
  own established `@MockBean CryptoClient` convention) — a live `CryptoClient` call would try to
  reach a `crypto-service` that isn't running in this test's own scope. Confirmed safe to
  construct the *real* `CryptoServiceTokenProvider` unmocked if ever needed: it fetches its token
  lazily on first `getAccessToken()` call, never eagerly at construction — not a blocker either
  way, since `@MockBean` replaces `CryptoClient` (its only real caller) anyway.

## 4. Established patterns to follow

**No existing precedent anywhere in this codebase combines Testcontainers-Postgres with a real
Kafka broker** (confirmed by direct search across all four services: zero `KafkaContainer`/
`ConfluentKafkaContainer` usage anywhere). **The established convention, used identically by
every Kafka-consuming service that needs this**, is `notification-service`'s own
`AuthEventConsumerIntegrationTest` (and its sibling `IdempotencyGuardIntegrationTest`):
`@Testcontainers` + `PostgreSQLContainer` only (via `@DynamicPropertySource`, same shape this
module's own three existing integration tests already use); Kafka is **not** containerized —
`spring.kafka.bootstrap-servers` falls through to the already-running local broker at
`localhost:9094`; publish with a real, autowired `KafkaTemplate<String, String>` (this service
already registers one, `KafkaProducerConfig.outboxKafkaTemplate`); consume/verify with
`Awaitility.await().atMost(...).untilAsserted(...)`, **never `Thread.sleep`**; a negative
assertion (proving something did *not* happen) uses `pollDelay(...).atMost(...)` instead of a
plain `atMost`, giving the (absent) side effect time to occur before asserting its absence.
`AuthEventConsumerIntegrationTest`'s own duplicate-delivery test (send twice, assert the
dispatched-call count stays at exactly 1) is directly mirrorable for R13.

**No test anywhere asserts Kafka partition/ordering directly** — O6 is currently only a
documented design guarantee (`ChainEventConsumer`'s own class Javadoc: "every event schema's own
producer-side partition key is `watchId`"). This task's own O6 test will be the first in this
codebase to combine real Kafka + real Postgres *and* assert on a realistic ordered sequence.

## 5. Testing conventions

Fixed `Clock` is the unit-test convention (T09 onward) — not directly applicable here, since
these are real end-to-end tests against the real `Instant.now()`-driven listener wiring, same as
`AuthEventConsumerIntegrationTest`'s own choice. ArchUnit: no new rule expected (no new
production class). This task adds exactly one new test class (or possibly two, if R13/R14 and O6
are better split — a Phase 2 decision), living in `com.themistra.payment.consumer` to reach
`ProcessedEventRepository`.

## 6. Known gaps / unknowns

- **I do not know** yet whether R13/R14/O6 are best proven in one combined test class or split
  across two (one per named test, `shouldDedupeDuplicateChainEventsByEventKey` /
  `shouldIgnoreOutOfOrderOrStaleChainEvents`, with O6 folded into whichever one it fits most
  naturally) — a Phase 2 design decision, informed by `AuthEventConsumerIntegrationTest`'s own
  precedent of one class per consumer covering several named scenarios as separate `@Test`
  methods.
- **I do not know** the exact realistic event sequence to use for O6 — `chain.tx.seen` →
  `chain.tx.confirmed` → `chain.tx.finalized` is the natural, full happy-path sequence already
  named in this service's own `design.md`/task 26 (end-to-end integration test, a later, larger
  task) — this task's own O6 test is narrower: proving per-payment ordering specifically, not a
  full attestation flow (receipts/ledger are unbuilt, tasks 15-19).
- Docker/Kafka availability is a snapshot as of 2026-10-15 — re-check `docker ps` before trusting
  this in a future session; the compose stack is not started automatically.
