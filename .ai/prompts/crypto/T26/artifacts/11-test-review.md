# crypto · T26 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T26 — End-to-end integration test |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

---

## Summary

The four flows cover the task acceptance criteria and exercise real Postgres + Kafka + fake providers through the pipeline. After Phase 8/9 fixes, the test correctly isolates per-flow txHash values, loads the Watch by its UUID column, uses TransactionTemplate for the screening test double, and cleans up the Watcher in `@AfterEach`.

The remaining gaps are **Kafka consumer isolation**, an **unstubbed snapshot-store mock**, a **hidden assumption about ChainCursor creation**, and several **weak or missing assertions** on event payload content, ordering, and R27 negative paths.

---

## Recommendations

### 1. Kafka consumer reads stale messages from earlier flows

- **Gap:** `setUp()` creates a new consumer group with `AUTO_OFFSET_RESET_CONFIG = "earliest"` and only calls `consumer.poll(...)` to force assignment. Because the `KafkaContainer` is class-scoped, earlier test methods' messages remain on the topics; the earliest-reset consumer will read them and may satisfy assertions for the wrong flow.
- **Why it matters:** Flow 2's `noRecordAppearsOnTopic("chain.tx.finalized")` could see Flow 1's finalized message and fail. More generally, `awaitRecordOnTopic(...).isNotNull()` could pass using a stale record rather than the current flow's record.
- **Suggested test:** After assignment, call `consumer.seekToEnd(consumer.assignment())` (or switch to `auto.offset.reset=latest` and ensure assignment completes before producing). Then only messages produced after the seek are visible to the test.

### 2. `ObservationSnapshotStore` mock is never stubbed

- **Gap:** `@MockBean ObservationSnapshotStore observationSnapshotStore` is declared but no stub is configured. `ObservationLog.record` calls `snapshotStore.store(...)` and immediately `.orElse(null)` on the returned `Optional`. Mockito's default return is `null`, so every flow throws a `NullPointerException` before reaching quorum.
- **Why it matters:** No observation can be persisted in any flow until this mock returns an Optional.
- **Suggested test:** Add a `@BeforeEach` stub such as `doReturn(Optional.empty()).when(observationSnapshotStore).store(any(), any(), any(), any(), any(), any())`.

### 3. `ChainCursor` placeholder existence is assumed but not asserted

- **Gap:** The test registers a watch and immediately constructs a Watcher, but it never asserts that a ChainCursor row exists. `Watcher.handleSeenIfAgreed` skips finality polling when the cursor is absent, and `pollFinality` then has nothing to evaluate.
- **Why it matters:** Flows 1, 3, and 4 depend on finality polling. If the production `WatchService` does not create the cursor as a side effect of registration, all three flows will fail at the attest/no-finalized assertions. The test currently hides this assumption.
- **Suggested test:** After `registerWatch`, assert `chainCursorRepository.findByWatchId(watchId).isPresent()`. If the production code does not create it, create the placeholder explicitly in the test setup.

### 4. Kafka assertions only check record presence, not event content

- **Gap:** Every `awaitRecordOnTopic(...)` assertion is `isNotNull()`. It does not validate the record key, idempotency key, payload fields, or event type.
- **Why it matters:** R8–R12 and the event schema require deterministic idempotency keys (`chain:txHash:eventType`), `watchId` as the Kafka partition key, amount as a decimal string, etc. A non-null assertion would pass even if the payload were empty or wrong.
- **Suggested test:** Parse each consumed record's JSON and assert: `record.key()` equals `watchId.toString()`; `idempotencyKey` equals `chain + ":" + txHash + ":" + eventType`; payload contains the correct `watchId`, `txHash`, `tokenContractAddress`, and (for finalized) `amount` as a string, `fromAddress`, and `toAddress`.

### 5. No negative R27 assertions for the internal endpoints

- **Gap:** The test exercises `POST /internal/v1/watches` and `POST /internal/v1/attest` only with a valid JWT bearing `internal.crypto:write`.
- **Why it matters:** R27 requires rejecting unauthenticated or under-scoped callers. The success path alone does not prove the scope enforcement works end-to-end.
- **Suggested test:** Add small MockMvc assertions that call each endpoint without a JWT and with a JWT missing the `internal.crypto:write` scope, expecting `401`/`403`.

### 6. No lifecycle-ordering assertions

- **Gap:** The test does not assert that `chain.tx.seen` is delivered before `chain.tx.confirmed`, that both precede `chain.tx.finalized`, or that `chain.tx.reorged` in Flow 3 is delivered after `chain.tx.confirmed`.
- **Why it matters:** R8–R10 describe a strict lifecycle. A test that only checks presence cannot detect a bug that emits events out of order.
- **Suggested test:** Capture the offset or timestamp of each event as it is consumed and assert monotonic ordering per flow (`seen < confirmed < finalized`; `confirmed < reorged` in Flow 3).

### 7. No assertion that `KmsSigner.sign` is invoked in Flow 1

- **Gap:** Flow 4 verifies `KmsSigner.sign` is never called, but Flow 1 does not verify it is called.
- **Why it matters:** AC1 requires a signature. A mocked signer returning a fixed value without ever being invoked could still produce the expected response if the service short-circuits signing.
- **Suggested test:** After the successful attest call in Flow 1, add `verify(kmsSigner, times(1)).sign(any())` and optionally use an `ArgumentCaptor<byte[]>` to assert the digest equals the parsed receipt digest.

### 8. No assertion that `Attestation` rows are persisted

- **Gap:** The test asserts the HTTP response shape but never queries the `Attestation` table.
- **Why it matters:** R20/R21 require every attestation outcome to be persisted. A response could be fabricated without a durable audit row.
- **Suggested test:** After the attest call in Flows 1 and 4, query `AttestationRepository` (or use `EntityManager`) for the row matching chain + txHash and assert `outcome` is `SIGNED` or `BLOCKED`.

### 9. No assertion that `HeldFactAlerter` is not invoked in healthy flows

- **Gap:** `heldFactAlerter` is mocked and verified in Flow 2, but Flows 1, 3, and 4 do not assert it was not called.
- **Why it matters:** A bug that alerts on a healthy flow would be silently allowed.
- **Suggested test:** Add `verify(heldFactAlerter, never()).alert(anyString(), anyString(), any(), any())` to Flows 1, 3, and 4.

### 10. `TOKEN` quorum decision is not asserted in Flow 1

- **Gap:** `AttestationService` gates on `EXISTENCE`, `AMOUNT`, `TOKEN`, and `FINALITY`. The test asserts `EXISTENCE` and `FINALITY` are agreed but not `TOKEN`.
- **Why it matters:** A missing or wrong `TOKEN` decision would still be caught indirectly by the attest failure, but an explicit assertion makes the contract clearer and debug failures faster.
- **Suggested test:** Add `assertThat(quorumDecisionService.isAgreed("ETHEREUM", txHash, FactType.TOKEN)).isTrue()` in Flow 1.

### 11. `chain.tx.seen` is not asserted in Flows 3 and 4

- **Gap:** Flows 3 and 4 wait for `chain.tx.confirmed` (and `finalized`), but they do not assert that `chain.tx.seen` was also delivered.
- **Why it matters:** `seen` is the first lifecycle event and a prerequisite for the cursor snapshot that `finalized` and attestation depend on. Its absence would be masked by the later-event assertions.
- **Suggested test:** Add `assertThat(awaitRecordOnTopic("chain.tx.seen", ...)).isNotNull()` in Flows 3 and 4 before awaiting later events.

### 12. No assertion that `ObservationSnapshotStore.store` is called before the database insert

- **Gap:** The test counts `Observation` rows but does not assert the L3 ordering: verbatim response persisted to S3 before the Postgres `Observation` row.
- **Why it matters:** L3 is a LOCKED decision; the observation log must be written before the quorum decision.
- **Suggested test:** Replace the `@MockBean ObservationSnapshotStore` with a spy or ordered mock and use `Mockito.inOrder(observationSnapshotStore, observationRepository)` to assert `store` is called before `save` for the first observation in a flow.

---

## Confirmations

- The four flows map directly to the task statement's four scenarios.
- Real Testcontainers Postgres and Kafka are used; fake providers are used throughout.
- Flow 1 exercises R1/R8/R9/R10/R18/R20/R23/R27 and L1/L3/L4.
- Flow 2 exercises R2/R3/L2 and correctly uses three distinct values for both AMOUNT and CONFIRMATIONS.
- Flow 3 exercises R11/L6 and correctly invalidates the ChainCursor after a reorg.
- Flow 4 exercises R21/L12 and verifies `KmsSigner.sign` is never invoked.
- Docker availability remains the only known blocker to a real runtime green/red result.
