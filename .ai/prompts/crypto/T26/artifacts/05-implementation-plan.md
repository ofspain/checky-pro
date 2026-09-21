# crypto · T26 · Phase 5 — Implementation Plan

**Correction to Phase 2's own assumed placement:** the frozen brief assumed `EndToEndIntegrationTest`
would live in the root `com.themistra.crypto` package. Verified this phase: `Watcher`'s constructor
and `ChainCursorRepository` are both package-private in `com.themistra.crypto.watch`, and
`ObservationRepository`/`ScreeningResultRepository` are package-private in `observation`/`screening`
respectively — no single package gives compile-time access to all four. **Resolution:** the test class
is placed in `com.themistra.crypto.watch` (the package that matters most — direct `Watcher`
construction and `ChainCursorRepository` access are this task's core architectural workaround, per
Phase 2's own Constraints section); `Observation`/`ScreeningResult` (both `public` entity classes,
unlike their repositories) are queried via `@PersistenceContext EntityManager` + JPQL instead of their
package-private repository types, for AC5/AC6.

## Files to create

- `services/crypto/src/test/java/com/themistra/crypto/watch/EndToEndIntegrationTest.java`

## Files to modify

None.

## Public methods (signatures)

None — package-private test class, matching every other integration test's own convention.

## Private methods

```java
private Watcher newRealWatcher(Watch watch, List<ProviderSet.NamedAdapter> adapters);
// mirrors WatcherTest.newWatcher(...) exactly, but every collaborator argument is a real,
// @Autowired Spring bean (ObservationLog, QuorumDecisionService, ProviderHealthTracker,
// ChainCursorRepository, TxLifecyclePublisher, ReorgDetector, real MeterRegistry, real Clock,
// real ObjectMapper, real TxLifecyclePublisher) rather than a Mockito mock.

private RegisterWatchRequest newRegisterRequest(String chain, String address, String tokenContractAddress,
        String expectedAmount);
// builds a valid request per the real, verified RegisterWatchRequest shape
// (invoiceUuid, chain, address, tokenContractAddress, expectedAmount, expiresAt).

private List<Observation> findObservations(String chain, String txHash, FactType factType);
// EntityManager.createQuery("select o from Observation o where o.chain = :chain and
// o.txHash = :txHash and o.factType = :factType", Observation.class) - mirrors
// ObservationRepository.findByChainAndTxHashAndFactType's own query semantics without needing its
// package-private type.

private List<ScreeningResult> findScreeningResults(String chain, String address);
// same EntityManager/JPQL technique, against the public ScreeningResult entity.

private void consumeAndAssertTopic(KafkaConsumer<String, String> consumer, String expectedTopic,
        String expectedIdempotencyKeyFragment);
// polls the real consumer, asserts a record was genuinely delivered to the given topic.
```

## Entities used

`Watch`, `ChainCursor` (via the real, package-visible `ChainCursorRepository`), `Observation`,
`ScreeningResult` (via `EntityManager`/JPQL, per the placement correction above), `QuorumDecision` (via
the real, package-visible-from-`watch`... **correction**: `QuorumDecisionRepository` lives in `quorum`,
also package-private — `QuorumDecision`'s `AGREED`/`HELD` outcome is instead verified through
`QuorumDecisionService.isAgreed(chain, txHash, factType)` (public method, already `@Autowired`-available
real bean) rather than a direct repository query, avoiding a fourth package-visibility workaround).

## Repositories used

`ChainCursorRepository` (real bean, directly `@Autowired` — visible from `watch`).

## Services used

Real, Spring-autowired: `WatchService` (indirectly, via `MockMvc` → `WatchController`), `ObservationLog`,
`QuorumDecisionService`, `ProviderHealthTracker`, `TxLifecyclePublisher`, `ReorgDetector`,
`OutboxRelay` (its `relay()` called directly per Phase 2's own determinism decision), `KafkaTemplate`
(indirectly, via the real `OutboxPublisher`→`OutboxRelay` path).
Mocked (`@MockBean`): `WatcherRegistry` (Frozen Brief Finding #15), `ObservationSnapshotStore`
(Finding #4), `KmsSigner` (Phase 2), `ScreeningClient` (Phase 2/Finding #10), `HeldFactAlerter`
(for Flow 2's interaction-verification, optional convenience over a pure state-based assertion).

## Unit/integration tests required

1. **`endToEndFlowRegistersObservesAndAttestsWithASignature`** (Flow 1, AC1/AC5). Register via
   `MockMvc` with a valid `internal.crypto:write` JWT → construct a real `Watcher` with 3
   `FakeChainAdapter`s → deliver agreeing `EXISTENCE`/`AMOUNT`/`TOKEN`/`CONFIRMATIONS` → assert real
   Kafka delivery of `chain.tx.seen`/`chain.tx.confirmed` → assert `Observation` rows exist per
   provider/fact-type (AC5) before their respective `QuorumDecisionService.isAgreed(...)` becomes
   `true` → script finality final for all 3, call `watcher.pollFinality()`, assert `chain.tx.finalized`
   delivered → `MockMvc` `POST /internal/v1/attest`, assert `200 { outcome: "SIGNED" }` from the mocked
   `KmsSigner`.
2. **`disagreementOnANonBooleanFactHoldsAndEmitsNothingFurther`** (Flow 2, AC2, redefined per Finding
   #3). `EXISTENCE` agrees (assert `chain.tx.seen` still delivered); 3 distinct `AMOUNT` values
   deliver no majority → assert `QuorumDecisionService.isAgreed(..., AMOUNT)` is `false`,
   `HeldFactAlerter` invoked once, and no `chain.tx.confirmed`/`chain.tx.finalized` record ever
   appears on the real consumer within a bounded poll window.
3. **`reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor`** (Flow 3, AC3). As Flow 1 through
   `CONFIRMED` → `FakeChainAdapter.simulateReorg(txHash, tx(false, ...))` → call
   `watcher.pollFinality()` (drives `checkForReorg`) → assert `chain.tx.reorged` delivered and
   `ChainCursorRepository.findByWatchId(...)`'s row has a null `txHash` (invalidated) → assert no
   `chain.tx.finalized` record ever appears afterward.
4. **`sanctionedCounterpartyIsBlockedWithNoSignature`** (Flow 4, AC4/AC6). As Flow 1 through finality
   → mocked `ScreeningClient.screen(...)` configured to return `BLOCKED` for the scenario's
   `fromAddress` and to persist a `ScreeningResult` row (the mock's own `thenAnswer` persists via the
   real `EntityManager`, mirroring the real contract) → `MockMvc` attest call asserts
   `200 { outcome: "BLOCKED", reason }` → assert zero `KmsSigner.sign(...)` interactions → assert one
   `ScreeningResult` row with `outcome=BLOCKED` exists for the address (AC6).

## Execution order

1. Container/context setup: `@Testcontainers` with static `PostgreSQLContainer` + `KafkaContainer`;
   `@DynamicPropertySource` wiring both into `spring.datasource.*`/`spring.kafka.bootstrap-servers`;
   `@MockBean` declarations for `WatcherRegistry`, `ObservationSnapshotStore`, `KmsSigner`,
   `ScreeningClient`.
2. Write and compile-check the class skeleton (fields, `@BeforeEach`, helper methods) before any test
   body, confirming the Spring context itself loads cleanly against both real containers — this alone
   validates several of Phase 3's accepted findings (the `@MockBean` list) without yet running a flow.
3. Implement Flow 1 first (the most foundational — every other flow reuses its setup shape). Run it in
   isolation before writing Flow 2/3/4.
4. Implement Flow 2, 3, 4 in turn, each run in isolation before moving to the next.
5. Run the full class together, then `KmsSignerArchitectureTest`/`ResourceServerConfigIntegrationTest`/
   `CrossModuleEntityArchitectureTest` alongside it to confirm no cross-test interference, then the
   full module regression.
6. **Given Docker's unavailability in this development environment (Phase 0/1/2, unresolved)**, steps
   2-5 cannot actually execute here. The class will be written and self-reviewed to the same rigor as
   if it could run, with every claim about its behavior explicitly caveated as "expected, not yet
   confirmed by a real run" until a Docker-available environment is reached — stated plainly in Phase 6
   rather than silently asserting a false pass.
