# crypto · T26 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T26 — End-to-end integration test |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` + `services/crypto/src/test/java/com/themistra/crypto/watch/EndToEndIntegrationTest.java` |
| **Produces** | `artifacts/08-independent-review.md` |

---

## Findings

### 1. Unstubbed `ObservationSnapshotStore` causes `NullPointerException` on every observation

- **Issue:** `ObservationSnapshotStore` is declared as `@MockBean` but never stubbed. `ObservationLog.record` calls `snapshotStore.store(...)` and immediately invokes `.orElse(null)` on the returned `Optional<String>`. Mockito's default return for a non-stubbed `Optional` method is `null`, so every flow that writes an observation will throw an NPE before any quorum decision is reached.
- **Evidence:** `EndToEndIntegrationTest.java:129` (mock declaration); `observation/ObservationLog.java:55` (`.orElse(null)` on the store result).
- **Recommendation:** Add a `@BeforeEach` stub such as `doReturn(Optional.empty()).when(observationSnapshotStore).store(any(), any(), any(), any(), any(), any())`.
- **Confidence:** High

### 2. No `ChainCursor` placeholder is created after watch registration, so finality polling is skipped

- **Issue:** The test registers a watch and immediately constructs a `Watcher`, but it never inserts a `ChainCursor` row. `Watcher.handleSeenIfAgreed` logs a warning and refuses to add the transaction to `pendingFinality` when no cursor exists; `pollFinality` then has nothing to evaluate. As a result, `QuorumDecisionService.isAgreed(..., FINALITY)` remains false and attestation returns `409`, not `SIGNED`/`BLOCKED`.
- **Evidence:** `EndToEndIntegrationTest.java:285-294, 309-317, 421-427`; `watch/WatchService.java:43-76` (no cursor creation); `watch/Watcher.java:322-345` (cursor-absent path).
- **Recommendation:** After each `registerWatch` call, persist `ChainCursor.placeholder("ETHEREUM", watchId, clock.instant())` via `chainCursorRepository.save(...)` before starting the `Watcher`.
- **Confidence:** High (based on the current `WatchService` and `Watcher` source)

### 3. `WatchAccessor.load()` queries by the wrong primary-key type

- **Issue:** `entityManager.find(Watch.class, watchId)` treats the UUID returned by registration (`watchUuid`) as the entity's primary key. The current `Watch` entity's `@Id` is the auto-generated `Long id`; `watchUuid` is a separate column. `find` will return `null`, so every flow passes a null `Watch` into `newRealWatcher`.
- **Evidence:** `EndToEndIntegrationTest.java:459-469`; `watch/Watch.java:30-36` (`@Id` is `Long id`, `watchUuid` is a different column).
- **Recommendation:** Replace `entityManager.find(Watch.class, watchId)` with `select w from Watch w where w.watchUuid = :watchUuid` (or add a repository query).
- **Confidence:** High

### 4. Hardcoded `txHash` in the `tx(...)` helper causes cross-test contamination

- **Issue:** `tx(...)` always builds a `TxResult` whose `txHash` field is `"0xtxhash"`, regardless of the `txHash` argument passed to `simulateReorg(...)`. Because `TxLifecyclePublisher` and `ReorgDetector` build their idempotency keys from `result.txHash()`, all four flows emit events under the same keys (`ETHEREUM:0xtxhash:seen`, `ETHEREUM:0xtxhash:confirmed`, etc.). The outbox's unique `idempotency_key` constraint causes duplicate-key suppressions after the first flow, and the shared Kafka topic plus `auto.offset.reset=earliest` means later flows read stale messages from earlier flows.
- **Evidence:** `EndToEndIntegrationTest.java:216-219` (helper hardcodes `txHash`); `watch/TxLifecyclePublisher.java:101-103` and `reorg/ReorgDetector.java:68` (idempotency-key format uses `txHash`).
- **Recommendation:** Change `tx(String txHash, boolean exists, ...)` to use the supplied `txHash` for the `TxResult`, and ensure every flow uses a distinct transaction hash consistently in both `simulateReorg`/`scriptTx`/`scriptFinalityStatus` and in assertions.
- **Confidence:** High

### 5. Flow 2 will emit `chain.tx.confirmed`, contradicting its own assertion

- **Issue:** Flow 2 supplies `confirmations=3` to all three providers. `Watcher` evaluates `CONFIRMATIONS` independently of `AMOUNT`; because all three values match, `CONFIRMATIONS` reaches `AGREED`, and `handleConfirmedIfAgreed` emits `chain.tx.confirmed`. The test then asserts `noRecordAppearsOnTopic("chain.tx.confirmed")`.
- **Evidence:** `EndToEndIntegrationTest.java:346-357`; `watch/Watcher.java:349-359` (emits confirmed when `CONFIRMATIONS` decision is `AGREED`).
- **Recommendation:** Make confirmations disagree across providers (e.g., `1`, `2`, `3`) so both `AMOUNT` and `CONFIRMATIONS` are `HELD`, or change the assertion to expect `seen` only and accept that `confirmed` is emitted.
- **Confidence:** High

### 6. Flow 2's `heldFactAlerter` verification uses the wrong `txHash`

- **Issue:** Because of Finding #4, the actual `HELD` decision (and therefore the `HeldFactAlerter.alert` call) is for `txHash="0xtxhash"`, while the test verifies the call was made for `"0xtxhash2"`.
- **Evidence:** `EndToEndIntegrationTest.java:355` (`eq("0xtxhash2")`) vs. the `TxResult` built with `"0xtxhash"` at line 217.
- **Recommendation:** Fix the `txHash` helper first; then align the `verify(...)` argument with the actual transaction hash.
- **Confidence:** High

### 7. Watch-registration request may not match the production DTO/controller

- **Issue:** The test constructs `new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM", ..., "1000000", ...)` and posts to `/internal/v1/watches`. In the current tree, `RegisterWatchRequest` expects `String callerReference` and `BigInteger expectedAmount`, and `WatchController` maps `/internal/watches` (no `/v1`). Either the target branch has a different DTO/controller, or the test will not compile/run.
- **Evidence:** `EndToEndIntegrationTest.java:205-207`; `watch/dto/RegisterWatchRequest.java:20-43`; `watch/WatchController.java:29,44`.
- **Recommendation:** Confirm the target DTO/controller shape and align the constructor arguments and path. If the DTO uses `BigInteger`, pass `new BigInteger("1000000")`; if it uses a decimal string, update the parameter type accordingly.
- **Confidence:** Medium (depends on the branch the PR targets)

### 8. Flow 4's mocked `ScreeningClient` persists outside a known transaction

- **Issue:** The `doAnswer` block calls `entityManager.persist(result)`. `AttestationService` is not `@Transactional`, and the test method is not wrapped in a transaction, so `EntityManager.persist` may throw `TransactionRequiredException`.
- **Evidence:** `EndToEndIntegrationTest.java:431-439`; `attest/AttestationService.java:48-70` (no `@Transactional`).
- **Recommendation:** Autowire `ScreeningResultRepository` and call `screeningResultRepository.save(result)` instead of `entityManager.persist(result)`, or wrap the mock answer in a `@Transactional` test helper method.
- **Confidence:** Medium

### 9. `Watcher.stop()` is not invoked if a flow fails mid-test

- **Issue:** Each test calls `watcher.stop()` only after all assertions. If an assertion throws earlier, the `Watcher`'s private virtual-thread scheduler and Kafka consumer remain active, leaking threads across tests.
- **Evidence:** `EndToEndIntegrationTest.java:330-331, 360-361, 398-399, 453-454`; no `try/finally` or `@AfterEach` cleanup.
- **Recommendation:** Keep a `private Watcher activeWatcher` field, set it in each test, and call `activeWatcher.stop()` (with a null check) in `@AfterEach`.
- **Confidence:** Low

### 10. `noRecordAppearsOnTopic` can return a stale record from an earlier `awaitRecordOnTopic` poll

- **Issue:** `awaitRecordOnTopic` buffers every polled record and only removes a record when the caller explicitly returns it, but it does not remove records from the buffer after a match. `noRecordAppearsOnTopic` simply delegates to `awaitRecordOnTopic`. If a record for the queried topic was already fetched while waiting for a different topic, `noRecordAppearsOnTopic` will report it as "appeared" even though it arrived before the intentional wait window.
- **Evidence:** `EndToEndIntegrationTest.java:259-281`.
- **Recommendation:** Clear matched records from `receivedRecords` when returning them, or make `noRecordAppearsOnTopic` scan only records whose timestamp/offset is after the window start.
- **Confidence:** Low

---

## Open Questions

1. **Which branch's `RegisterWatchRequest` / `WatchController` is the target?** The current test code does not match the current tree's DTO and controller path.
2. **Does the target `WatchService` create a `ChainCursor` on registration?** If yes, Finding #2 may be moot, but the current tree does not do so.
3. **Is `Watch.watchUuid` the entity's primary key in the target branch?** If yes, Finding #3 is moot; if not, it is a hard blocker.
