# crypto · T26 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T26 — End-to-end integration test |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

---

## Findings

### 1. The brief assumes a `Watch` domain model that does not match the current `Watch` entity

- **Issue:** The TIB says to construct a `Watcher` directly, "mirroring `WatcherTest`'s own established technique." That technique relies on `Watch` exposing `watchId()`, `chain()`, `invoiceUuid()`, `address()`, and `tokenContractAddress()`. The current `Watch` entity exposes `getWatchUuid()`, `getChainId()`, `getCallerReference()`, `getRecipientAddress()`, and `getTokenAddress()`, and has no `invoiceUuid()` at all.
- **Severity:** High
- **Evidence:** `watch/Watch.java:35-65` (entity getters) vs. `watch/Watcher.java:117,341` (`watch.chain()`, `watch.invoiceUuid()`) and `watch/TxLifecyclePublisher.java:66` (`watch.invoiceUuid()`).
- **Recommended brief amendment:** Before T26 can compile, freeze whether `Watch` is the entity shown or a separate domain record with the API `Watcher` expects. Either reconcile `Watch`/`Watcher`/`TxLifecyclePublisher` in a prerequisite task, or explicitly author a test-only `Watch` factory/DTO for T26 and document that the test does not exercise the real entity-to-watcher mapping.

### 2. `AttestationService` calls a `WatchService` method that does not exist

- **Issue:** Flows 1 and 4 exercise `POST /internal/v1/attest`, but `AttestationService.attest()` calls `watchService.findChainCursors(chain, txHash)`. `WatchService` has no such method.
- **Severity:** High
- **Evidence:** `attest/AttestationService.java:79` vs. `watch/WatchService.java:78-98` (no `findChainCursors`).
- **Recommended brief amendment:** Add `WatchService.findChainCursors(chain, txHash)` to the T26 prerequisites, or confirm it already exists on the target branch. Without it the attestation endpoint cannot return any response.

### 3. Flow 2's "two-provider disagreement → HELD" is ambiguous for Boolean facts

- **Issue:** The task statement and AC2 imply a disagreement produces `HELD` and zero Kafka events. With exactly three providers, a Boolean fact (`EXISTENCE`) can never be `HELD`: three booleans always contain a 2-of-3 majority. The only way to get `HELD` for `EXISTENCE` would require values like true / false / neither, but `Watcher` treats `exists=false` as a real answer, not an absence, and `evaluateFact` still evaluates with three answers.
- **Severity:** Medium
- **Evidence:** `quorum/QuorumEvaluator.java:39-44` (always returns `AGREED` for any 3-value boolean grouping) and `watch/Watcher.java:256-280` (`evaluateFact` only skips non-boolean facts when `exists=false`).
- **Recommended brief amendment:** Clarify that Flow 2 targets a *non-Boolean* fact (e.g., `AMOUNT` or `CONFIRMATIONS`) where three distinct values can genuinely produce `HELD`, and state the observable behavior: `seen` is still emitted because `EXISTENCE` agrees, but no `confirmed`/`finalized`/attest follows for the held fact. Alternatively, redefine Flow 2 as a lagging-third-provider scenario (undecided, not `HELD`).

### 4. The Spring context needs more doubles than the brief lists

- **Issue:** The TIB only doubles `KmsSigner` and `ScreeningClient`. A full `@SpringBootTest` will also instantiate `ObservationSnapshotStore` (S3 client), `ChainAdapterRegistry` (requires provider properties), `KafkaProducerConfig` (requires bootstrap-servers), `ShedLockConfig` (requires ShedLock), and possibly `WatcherRegistry` (which may try to start watchers via the real `ProviderSet`). Several of these currently fail to start without external infrastructure or properties.
- **Severity:** Medium
- **Evidence:** `observation/ObservationLog.java:55` (calls snapshot store), `config/CryptoConfiguration.java:42` (builds registry from properties), `events/KafkaProducerConfig.java:26` (requires `spring.kafka.bootstrap-servers`), `common/ShedLockConfig.java:22` (requires LockProvider), `watch/WatcherRegistry.java` (may start watchers).
- **Recommended brief amendment:** List all required test-only beans: `@MockBean ObservationSnapshotStore`, `@MockBean WatcherRegistry`, and test properties for `ChainProviderProperties` / `QuorumProperties`. Alternatively, switch to a sliced `@SpringBootTest` that imports only the configs the four flows actually need.

### 5. There are two competing `ChainAdapter` abstractions

- **Issue:** `Watcher` and `FakeChainAdapter` use `com.themistra.crypto.adapter.ChainAdapter`/`ProviderSet`, while the Spring configuration uses `com.themistra.crypto.chain.ChainAdapter` and `EvmChainAdapter`. The T26 workaround bypasses the Spring-configured adapter path entirely and manually wires the `adapter` package path. This means the test is *not* end-to-end through the production adapter wiring.
- **Severity:** Medium
- **Evidence:** `watch/Watcher.java:6,75,100` (imports and uses `adapter.ProviderSet`/`ChainAdapter`) vs. `config/ChainAdapterFactory.java:6,83-86` (builds `chain.ChainAdapter` registry).
- **Recommended brief amendment:** State explicitly that T26 bypasses `ChainAdapterRegistry` and `ProviderSet`; the manual `Watcher` construction is the runtime path under test, and this is a documented limitation of the "end-to-end" claim.

### 6. Watch-registration endpoint and DTO mismatch the spec

- **Issue:** The spec/design.md says `POST /internal/v1/watches` with body fields `invoiceUuid`, `chain`, `address`, `tokenContractAddress`, and a decimal-string `expectedAmount`. The actual controller is `/internal/watches` (no `/v1`) and uses `RegisterWatchRequest` with `callerReference`, `chainId`, `recipientAddress`, `tokenAddress`, and `BigInteger expectedAmount`.
- **Severity:** Medium
- **Evidence:** `watch/WatchController.java:29,44-53` and `watch/dto/RegisterWatchRequest.java:20-43` vs. `design.md` §4c internal API snippet.
- **Recommended brief amendment:** Specify the exact path and JSON fields the T26 test will send, and note the spec/implementation mismatch so the test does not 404 or fail bean validation.

### 7. Watch registration alone does not create the `ChainCursor` row `Watcher` expects

- **Issue:** `WatchService.register` persists a `Watch` but never inserts a `ChainCursor`. `Watcher.handleSeenIfAgreed` logs a warning and refuses to start finality polling when no cursor exists. Flows 1, 3, and 4 all need finality polling.
- **Severity:** Medium
- **Evidence:** `watch/WatchService.java:43-76` (no cursor creation) vs. `watch/Watcher.java:322-345` (`cursor.isEmpty()` path).
- **Recommended brief amendment:** Add a per-flow setup step: after registering the watch, persist a `ChainCursor.placeholder(...)` row via `ChainCursorRepository` before delivering observations, mirroring `WatcherTest.seenCursor()`.

### 8. Finality must be driven explicitly; the event alone is not enough to attest

- **Issue:** `AttestationService` gates on `quorumDecisionService.isAgreed(chain, txHash, FINALITY)`. That decision is only persisted when `Watcher.pollFinality()` observes a 2-of-3 majority of `FinalityStatus` values as final. The test must script `FakeChainAdapter.scriptFinalityStatus(...)` and invoke `watcher.pollFinality()` before calling attest.
- **Severity:** Medium
- **Evidence:** `attest/AttestationService.java:51-52,108-114` (required facts include `FINALITY`) and `watch/Watcher.java:574-668` (`pollFinalityFor` only persists `FINALITY` when local majority is final).
- **Recommended brief amendment:** In Flow 1, add explicit steps: (a) script finality status as final for all three providers, (b) call `watcher.pollFinality()`, (c) assert `chain.tx.finalized` delivered, (d) then call attest.

### 9. Kafka consumer subscription timing and offset reset are unstated

- **Issue:** The brief says to use a "plain `KafkaConsumer<String,String>` subscribed to the relevant `EventTopics` topic." If the consumer subscribes after `OutboxRelay.relay()` has already sent messages, or if it uses a consumer group whose offsets are already committed, it will miss the events.
- **Severity:** Medium
- **Evidence:** `events/OutboxRelay.java:76-78` (sends then marks published); consumer behavior is standard Kafka semantics.
- **Recommended brief amendment:** Require the test consumer to use a unique group id per test, `auto.offset.reset=earliest`, and subscribe *before* any action that produces outbox rows. Only call `OutboxRelay.relay()` after the consumer subscription is active.

### 10. The `@Primary` `ScreeningClient` test double needs bean-overriding policy

- **Issue:** The TIB proposes a `@TestConfiguration` class with a `@Primary` `ScreeningClient` bean. If `FailClosedScreeningClient` is already declared in the production context, Spring Boot will fail on duplicate bean definitions unless `spring.main.allow-bean-definition-overriding=true` is set.
- **Severity:** Low
- **Evidence:** General Spring Boot behavior; TIB `Constraints`/`Open Questions` sections do not mention this property.
- **Recommended brief amendment:** Either use `@MockBean ScreeningClient screeningClient` (which replaces the production bean without needing `@Primary`) or add `spring.main.allow-bean-definition-overriding=true` to the test properties and verify the context loads.

### 11. Security scope for the watch endpoint is unclear

- **Issue:** `WatchController` is currently unauthenticated (its own Javadoc says so), and its path does not match the `/internal/v1/...` path used by `ResourceServerConfigIntegrationTest`. The TIB says scope enforcement is "already fully handled by `ResourceServerConfig`," but exercising watch registration via MockMvc may not require a token, while direct service calls bypass security entirely.
- **Severity:** Low
- **Evidence:** `watch/WatchController.java:22-30` (unauthenticated, `/internal/watches`) vs. `common/ResourceServerConfigIntegrationTest.java` (tests `/internal/v1/...`).
- **Recommended brief amendment:** Decide whether Flow 1 exercises R27 (use MockMvc with a service token) or intentionally bypasses security (use direct service calls). State the choice so the test is not interpreted as proving authentication.

### 12. Test data must use valid addresses and a configured chain id

- **Issue:** `WatchService` validates and normalises `chainId`, `recipientAddress`, and `tokenAddress`. An invalid EVM address or unconfigured chain will reject registration before the end-to-end flow starts.
- **Severity:** Low
- **Evidence:** `watch/WatchService.java:55-63` and `chain/ChainAddress.java`.
- **Recommended brief amendment:** Provide concrete sample values in the brief (e.g., `chainId="eip155:1"`, a valid EIP-55 recipient, token address, and `expectedAmount` as base-unit `BigInteger`) and require `ChainProviderProperties` to declare at least one EVM chain so `WatchService.supports(...)` returns true.

### 13. L3 observation-log verification is absent from acceptance criteria

- **Issue:** The four flows assert events and attestation responses, but never assert that provider responses were persisted verbatim in `observations` before the quorum decision. L3 is a LOCKED decision and `package.md` §9 explicitly asks for a test proving every emitted fact passed 2-of-3 quorum and that observations are written first.
- **Severity:** Low
- **Evidence:** `observation/ObservationLog.java:18-61` (writes observations before quorum) and `package.md` §9 verification checklist.
- **Recommended brief amendment:** Add a cross-flow assertion querying `ObservationRepository` for `EXISTENCE`/`AMOUNT`/`TOKEN`/`FINALITY` rows per provider, and add an assertion that a `QuorumDecision` row exists with `AGREED` before the corresponding event is delivered.

### 14. Flow 4 should also assert a persisted `ScreeningResult` row

- **Issue:** `ScreeningClient` contract requires every implementation to persist exactly one `ScreeningResult` row. The TIB only asserts the attest response and zero KMS interactions.
- **Severity:** Low
- **Evidence:** `screening/ScreeningClient.java:8-12` (persistence obligation) and `attest/AttestationService.java:85-99` (calls screening before signing).
- **Recommended brief amendment:** In Flow 4, assert that `ScreeningResultRepository` contains one row with `outcome=BLOCKED` for the sanctioned `fromAddress`.

---

## Open Questions

1. **Which branch's `Watch`/`Watcher` API is the target?** The TIB appears to describe code from a branch where `Watch` is a record with `invoiceUuid()` and `watchId()`, while the current tree has a JPA entity with different accessors. T26 cannot be written until this is reconciled.
2. **Is `WatchService.findChainCursors` intentionally missing?** It is a prerequisite for `AttestationService` to function.
3. **Is the watch endpoint intentionally unauthenticated and at `/internal/watches`?** This conflicts with `design.md` §4c and with `ResourceServerConfigIntegrationTest`'s `/internal/v1/...` assumptions.
