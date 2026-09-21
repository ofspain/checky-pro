# crypto · T26 · Phase 2 — Task Implementation Brief

## Task

One new `@SpringBootTest` integration test class, `EndToEndIntegrationTest`, using real
`PostgreSQLContainer` + `KafkaContainer` (Testcontainers), a real Spring context, and
`FakeChainAdapter`s, proving four real, end-to-end flows: (1) register → seen → confirmed → finalized
→ attest returns a signature; (2) two-provider disagreement holds the fact, emits nothing; (3) reorg
after confirmed emits `chain.tx.reorged`; (4) sanctioned counterparty → attest `BLOCKED`.

## Purpose

Every piece of this pipeline (T02-T25) has been proven correct in isolation — unit tests with mocked
collaborators, or Testcontainers tests scoped to one repository/module. Nothing has ever proven the
pieces work correctly *together*, through a real database and a real message broker, the way
`package.md` §9's own verification checklist demands for L1 specifically ("a test asserts every emitted
fact passed 2-of-3 quorum" — read in context as wanting more than the existing unit-level proof). This
task is that proof.

## Scope

**In:**
- `services/crypto/src/test/java/com/themistra/crypto/EndToEndIntegrationTest.java` — one
  `@SpringBootTest` class, `@Testcontainers`, with a static `PostgreSQLContainer` and a static
  `KafkaContainer` (both already-declared dependencies, neither used by any existing test — this is
  the first real Kafka integration test in this codebase).
- A test-scoped `ScreeningClient` bean override (Phase 0/1 finding: no existing implementation can
  return `BLOCKED`) — a `@TestConfiguration` nested class providing a `@Primary` bean, a small,
  real (not Mockito-mocked) implementation letting the test configure which address, if any, is
  "sanctioned" for a given scenario.
- 4 `@Test` methods, one per flow, each registering its own watch via the real `WatchController`/
  `WatchService` (through `MockMvc` or direct service-bean autowiring — a Phase 5 detail) so the
  registration half of the flow is genuinely exercised end-to-end, not skipped.
- Real Kafka consumption assertions (a plain `KafkaConsumer<String,String>` subscribed to the relevant
  `EventTopics` topic, or Spring Kafka's own test listener utilities) — proving an event was genuinely
  delivered to the real broker, not merely that an `OutboxEvent` row exists. The task statement's own
  explicit inclusion of "+ Kafka" in its infrastructure list is the signal this is wanted, not just
  Postgres-level proof (which `OutboxRelay`'s own existing unit test already covers).

**Out:**
- Any production code change. This task is proof, not construction.
- A real LocalStack KMS round-trip for the "attest returns a signature" clause (Flow 1). The task
  statement's own infrastructure list is explicitly "Testcontainers Postgres + Kafka + fake providers"
  — it does not mention KMS/LocalStack at all, and T20's `KmsSignerLocalStackIntegrationTest` already
  independently, exhaustively proves real KMS signing works correctly in isolation. `KmsSigner` is
  mocked (`@MockBean`) in this test — Flow 1 proves the *pipeline* reaches a genuine `SIGNED` response
  with the correct shape, not that KMS itself works (already proven elsewhere, out of proportion to
  re-prove here).
- Introducing a shared Testcontainers base class for the other 8 existing integration tests — no
  precedent for one exists, and retrofitting all 8 would be unrelated-refactoring scope creep. This
  task's own class declares its own containers, matching the established per-class convention.
- Modifying `ProviderSet`, `WatcherRegistry`, or any other production wiring class to make fake
  adapters injectable through the real Spring-managed path (see Constraints below for why, and the
  chosen workaround).

## Business Rules / Locked Decisions

Unchanged from Phase 1: R1/R2/R3/R6/R7/R8/R9/R10/R11/R12/R18/R20/R21/R23; L1-L6, L10-L12.

## Dependencies

None new. `org.testcontainers:kafka`, `org.testcontainers:postgresql`, `spring-boot-testcontainers`,
`org.testcontainers:junit-jupiter` — all already declared.

## Inputs / Outputs / State Changes

No inputs/outputs beyond what the test itself drives (HTTP calls to the real controllers, real DB
writes, real Kafka messages). No production state changes.

## Files to Create

- `services/crypto/src/test/java/com/themistra/crypto/EndToEndIntegrationTest.java`

## Files to Modify

None.

## Files NOT to Modify

- `adapter/ProviderSet.java`, `watch/WatcherRegistry.java` — see Constraints below; both stay exactly
  as they are, worked around rather than changed.
- Every production class this task exercises.
- Any file under `spec/`.

## Acceptance Criteria

Unchanged from Phase 1's AC1-AC4, restated with the KMS-mocking decision folded in:

- **AC1 (Flow 1).** Real watch registration → real quorum-agreed `seen`/`confirmed`/`finalized`
  events, each genuinely delivered to its real Kafka topic → `POST /internal/v1/attest` returns `200
  { outcome: "SIGNED", ... }` (with `KmsSigner` mocked to return a fixed `SignatureResult`).
- **AC2 (Flow 2).** A genuine 2-provider disagreement persists the fact `HELD`, invokes
  `HeldFactAlerter`, and delivers zero events to Kafka for that fact.
- **AC3 (Flow 3).** After `CONFIRMED`, a simulated reorg delivers `chain.tx.reorged` to its real topic
  and invalidates the `ChainCursor` — no `chain.tx.finalized` ever follows for that transaction.
- **AC4 (Flow 4).** With the test-double `ScreeningClient` configured to return `BLOCKED` for the
  scenario's counterparty address, once quorum+finality are genuinely satisfied,
  `POST /internal/v1/attest` returns `200 { outcome: "BLOCKED", reason }` — `KmsSigner.sign(...)` is
  never invoked (verified via the mock's own zero-interactions check).

## Required Tests

The 4 `@Test` methods themselves are this task's entire required-test list — there is no separate
named test from `package.md` §8.

## Constraints

- **Real signing execution/verification is deferred, not silently claimed.** Docker is unavailable in
  this development environment right now (Phase 0 finding). This test will be written to be correct by
  construction and reviewed accordingly, but its actual green/red run must wait for a Docker-available
  environment — this will be stated plainly in every subsequent phase's own verification section rather
  than asserting a false pass.
- **Architectural constraint discovered this phase: `ProviderSet`'s constructor is hard-typed to
  `List<EthereumAdapter>, List<TronAdapter>` (concrete adapter classes), not `List<ChainAdapter>`
  (the interface).** `FakeChainAdapter` cannot be substituted into a real, Spring-wired `ProviderSet`
  bean without either changing that constructor's signature (a production change, out of scope) or
  bean-overriding `EthereumAdapter`/`TronAdapter` themselves with fakes that aren't actually instances
  of those concrete types (impossible — Java is nominally typed). **Resolution:** this test does not
  route through the real `WatcherRegistry`/`ProviderSet` wiring path for the watcher layer. Instead,
  each test method constructs its own `Watcher` instance directly (mirroring `WatcherTest`'s own
  established technique exactly), but using **real, Spring-autowired collaborators** (`ObservationLog`,
  `QuorumDecisionService`, `ProviderHealthTracker`, `ChainCursorRepository`, `TxLifecyclePublisher`,
  `ReorgDetector` — all real beans backed by the real Postgres container and real Kafka via the real
  outbox) rather than `WatcherTest`'s Mockito mocks, plus a manually-built
  `List<ProviderSet.NamedAdapter>` wrapping `FakeChainAdapter`s. This is the only viable way to
  simultaneously satisfy "fake providers" (`agents.md`) and "real infrastructure" (the task's own
  stated goal) given `ProviderSet`'s current, unmodified constructor. Watch registration itself
  (`WatchController`/`WatchService`) is unaffected by this constraint and is exercised fully for real.
- **Thread-safety/transaction:** standard `@SpringBootTest` + Testcontainers semantics; each test
  method uses its own distinct `txHash`/watch to avoid cross-method interference within the shared
  container instances (mirrors the existing 8 integration tests' own convention).
- **Kafka relay determinism:** `OutboxRelay.relay()` is `public` and directly invokable (mirrors
  `Watcher.sweepStaleCorrelations()`'s own testability convention) — call it directly after each
  action that should produce an outbox row, rather than waiting on its `@Scheduled` interval, for a
  fast, deterministic test.

## Open Questions

No blockers — both of Phase 1's open questions are resolved above (Docker: deferred execution, stated
plainly; screening test double: a `@TestConfiguration`/`@Primary` bean override).
