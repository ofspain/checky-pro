STATUS: FROZEN

# crypto · T26 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

Kimi's Phase 3 review ran against the wrong codebase entirely — verified directly: it describes a
`Watch` JPA entity with `getWatchUuid()`/`getChainId()`/`getCallerReference()`, a `WatchController` at
`/internal/watches` (no `/v1`), and a `com.themistra.crypto.chain.ChainAdapter` package. None of these
exist on this branch's real code (re-confirmed at this freeze: `Watch.watchId()`/`invoiceUuid()`,
`WatchController` at `/internal/v1/watches`, no `chain/` package anywhere) — this is the `feat` stack's
separate, parallel implementation, which has repeatedly poisoned this branch via an external process
this session (7 occurrences to date). 11 of 14 findings are **NOT APPLICABLE** for this reason alone.

While independently verifying each finding against the real codebase (not taking the review at face
value in either direction), 6 findings' underlying concerns proved genuinely valid regardless of which
codebase they were phrased against, and one additional real risk was found that Kimi's review missed
entirely (it couldn't have found it — `WatcherRegistry` doesn't exist in the `feat` stack it was
actually looking at).

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | `Watch` API mismatch | **NOT APPLICABLE** | Describes the wrong `Watch` class entirely (`feat` stack). Real `Watch.watchId()`/`invoiceUuid()`/`chain()`/`address()`/`tokenContractAddress()` confirmed unchanged and exactly as this task's own Phase 1/2 already assumed. |
| 2 | `WatchService.findChainCursors` missing | **NOT APPLICABLE (verified false)** | `WatchService.findChainCursors(chain, txHash)` exists exactly as `AttestationService` calls it — confirmed directly at `watch/WatchService.java:92`. |
| 3 | Flow 2's "disagreement → HELD" ambiguous for Boolean facts | **ACCEPTED (verified, codebase-independent)** | Three booleans always have a 2-of-3 majority (pigeonhole principle) — `EXISTENCE` can never be `HELD`. Flow 2 is redefined to target `AMOUNT` (a non-Boolean fact): `EXISTENCE` agrees (so `chain.tx.seen` still fires), but three distinct `AMOUNT` values genuinely produce `HELD`, and no `chain.tx.confirmed`/`finalized`/attest ever follows for that transaction. |
| 4 | Spring context needs more test doubles than the brief listed | **ACCEPTED IN PART (verified against real code)** | The specific classes Kimi named (`ChainAdapterRegistry`, `KafkaProducerConfig` needing "doubling") are wrong-stack or wrong-remedy (`KafkaProducerConfig` should point at the *real* Testcontainers Kafka, not be mocked). But the underlying concern is real: `@MockBean WatcherRegistry` is now required (see Finding #15 below, found independently) and `@MockBean ObservationSnapshotStore` is required (`observation/ObservationSnapshotStore.java` is a real S3 client this task's own infra list doesn't include LocalStack for). |
| 5 | Two competing `ChainAdapter` abstractions | **NOT APPLICABLE** | Describes the wrong stack's parallel `chain.ChainAdapter`/`EvmChainAdapter`. Only one `ChainAdapter` (`adapter.ChainAdapter`) exists on this branch. |
| 6 | Watch-registration endpoint/DTO mismatch with spec | **NOT APPLICABLE** | Describes the wrong stack's `WatchController`/`RegisterWatchRequest`. The real ones already match `design.md` §4c exactly (confirmed T15/T23). |
| 7 | Watch registration doesn't create a `ChainCursor` row | **NOT APPLICABLE (verified false)** | `WatchService.register(...)` already calls `chainCursorRepository.save(ChainCursor.placeholder(...))` — confirmed directly at `watch/WatchService.java:55`. No extra setup step needed. |
| 8 | Finality must be driven explicitly via `pollFinality()` | **ACCEPTED (verified, codebase-independent)** | Confirmed against the real `Watcher.pollFinalityFor`: `FINALITY` is only persisted once `pollFinality()` observes a local 2-of-3 majority of final `FinalityStatus` values. Flow 1 now has an explicit step: script all three providers' finality as final, call `watcher.pollFinality()`, assert `chain.tx.finalized` delivered, then call attest. |
| 9 | Kafka consumer subscription timing/offset reset unstated | **ACCEPTED (generic Kafka correctness, codebase-independent)** | The test's Kafka consumer uses a unique group id per test method, `auto.offset.reset=earliest`, and subscribes *before* any action that could produce an outbox row; `OutboxRelay.relay()` is only called after the subscription is confirmed active. |
| 10 | `@Primary` bean-override needs `allow-bean-definition-overriding` | **ACCEPTED (generic Spring correctness, codebase-independent)** | Both `ScreeningClient` and `KmsSigner` are doubled via `@MockBean`, which replaces the production bean cleanly with no `@Primary`/override-property needed — simpler than the TIB's original `@TestConfiguration` proposal, adopted here. |
| 11 | Security scope for watch endpoint unclear | **NOT APPLICABLE** | Describes the wrong stack's unauthenticated `WatchController`. The real one requires `internal.crypto:write` (R27, T03), already proven by `ResourceServerConfigIntegrationTest`. This task drives it via `MockMvc` with a valid service-scoped JWT, exercising R27 for real rather than bypassing it. |
| 12 | Test data must use valid addresses/configured chain | **PARTIALLY APPLICABLE, folded into implementation detail** | Generic, correct advice (use a real EIP-55 address, a real configured `Chain.ETHEREUM`) but the specific property names cited (`ChainProviderProperties`) are wrong-stack. Folded into Phase 5's own concrete test-data design. |
| 13 | L3 observation-log verification absent from ACs | **ACCEPTED (real spec gap, codebase-independent)** | Added as new AC5: assert `ObservationRepository` rows exist for each provider/fact-type combination the flow exercises, persisted before the corresponding `QuorumDecision` row. |
| 14 | Flow 4 should assert a persisted `ScreeningResult` row | **ACCEPTED (verified against real `ScreeningClient` contract)** | `ScreeningClient`'s real contract (confirmed at Phase 0, `FailClosedScreeningClient.java`) requires every call to persist exactly one `ScreeningResult` row. The test-double `ScreeningClient` mirrors this obligation; Flow 4 asserts a `BLOCKED` row exists for the sanctioned address. |
| 15 (found independently, not by Kimi) | `WatcherRegistry.reconcile()` is `@Scheduled` and would start a real, RPC-backed `Watcher` for the same watch row this test registers, racing the test's own manually-constructed fake-adapter `Watcher` | **ACCEPTED, new finding** | `WatcherRegistry` is `@MockBean`-doubled in this test's Spring context — the real one never runs, so its scheduled `reconcile()` can never start a competing real `Watcher`. This is a hard requirement, not optional: without it, this test would make real RPC calls in CI, violating `agents.md`'s explicit rule. |

## Task

One `@SpringBootTest` integration test class, `EndToEndIntegrationTest`, using real
`PostgreSQLContainer` + `KafkaContainer`, a real (but `WatcherRegistry`/`ObservationSnapshotStore`/
`KmsSigner`/`ScreeningClient`-doubled) Spring context, and manually-constructed `Watcher` instances
wired with `FakeChainAdapter`s and real, Spring-autowired repository/service/publisher collaborators,
proving four real end-to-end flows.

## Purpose

Unchanged from Phase 2.

## Scope

**In (updated per accepted findings):**
- `EndToEndIntegrationTest` in `services/crypto/src/test/java/com/themistra/crypto/`, with
  `@MockBean WatcherRegistry`, `@MockBean ObservationSnapshotStore`, `@MockBean KmsSigner`,
  `@MockBean ScreeningClient` — no `@TestConfiguration`/`@Primary` needed (Finding #10).
- Watch registration via `MockMvc` against the real `WatchController`, with a valid
  `internal.crypto:write`-scoped JWT (Finding #11) — exercising R27 for real, not bypassing it.
- **Flow 1:** register → deliver 2-of-3 agreeing `EXISTENCE`/`AMOUNT`/`TOKEN`/`CONFIRMATIONS` answers
  via a manually-constructed `Watcher` + `FakeChainAdapter`s → assert `chain.tx.seen`/`confirmed`
  delivered to real Kafka topics → script finality final for all 3 providers, call
  `watcher.pollFinality()` (Finding #8), assert `chain.tx.finalized` delivered → call attest via
  `MockMvc`, assert `200 { outcome: "SIGNED" }` (with `KmsSigner` mocked to return a fixed
  `SignatureResult`).
- **Flow 2 (redefined per Finding #3):** deliver 3 distinct `AMOUNT` values (genuine 3-way split, no
  majority) after `EXISTENCE` agrees — assert `chain.tx.seen` still fires, the `AMOUNT`
  `QuorumDecision` is `HELD`, `HeldFactAlerter` invoked, and no `chain.tx.confirmed`/`finalized`/attest
  success ever follows for that transaction.
- **Flow 3:** as Flow 1 through `CONFIRMED`, then `FakeChainAdapter.simulateReorg` reporting
  `exists=false` → assert `chain.tx.reorged` delivered and `ChainCursor` invalidated, no
  `chain.tx.finalized` ever follows.
- **Flow 4:** as Flow 1 through finality, with the mocked `ScreeningClient` configured to return
  `BLOCKED` for the scenario's `fromAddress` and to persist a `ScreeningResult` row (Finding #14) →
  call attest, assert `200 { outcome: "BLOCKED", reason }`, zero `KmsSigner.sign(...)` interactions.
- **AC5 (new, Finding #13):** at least one flow (Flow 1) asserts real `ObservationRepository` rows
  exist for each provider/fact-type combination, persisted before the corresponding `QuorumDecision`
  row (L3).
- A real Kafka consumer (unique group id per test, `auto.offset.reset=earliest`, subscribed before any
  outbox-producing action — Finding #9) verifying genuine message delivery to each real topic.

**Out:** unchanged from Phase 2 (no production code change; no shared Testcontainers base class; no
real LocalStack KMS round-trip).

## Business Rules / Locked Decisions

Unchanged from Phase 1, plus L3 now has an explicit AC (Finding #13) rather than being implicit.

## Files to Create

- `services/crypto/src/test/java/com/themistra/crypto/EndToEndIntegrationTest.java`

## Files to Modify / NOT to Modify

Unchanged from Phase 2.

## Acceptance Criteria

AC1-AC4 unchanged in substance from Phase 2, with Flow 2 redefined (Finding #3) and Flow 1 gaining the
explicit `pollFinality()` step (Finding #8) and the mocked-bean list corrected (Findings #4/#10/#15).
Plus:
- **AC5 (new, L3/Finding #13).** Real `Observation` rows exist for every provider/fact-type
  combination Flow 1 exercises, each persisted before its corresponding `QuorumDecision` row.
- **AC6 (new, Finding #14).** Flow 4's `ScreeningResultRepository` contains one row with
  `outcome=BLOCKED` for the sanctioned `fromAddress`.

## Constraints

Unchanged from Phase 2's architectural-constraint discussion (manual `Watcher` construction bypassing
`ProviderSet`/`WatcherRegistry`), plus:
- **`WatcherRegistry` must be `@MockBean`-doubled** (Finding #15) — its real, `@Scheduled` `reconcile()`
  would otherwise start a second, real-RPC-backed `Watcher` for the same watch row this test registers,
  a direct violation of `agents.md`'s "real RPC providers are never called in tests or CI."
- **Kafka consumer correctness** (Finding #9): unique group id, `earliest` offset reset, subscribe
  before producing.
- Docker unavailability in this environment remains unresolved — execution is deferred, not claimed.

## Open Questions

No blockers remaining. The three questions Kimi's review raised (which branch's `Watch`/`Watcher` API
is the target; is `findChainCursors` missing; is the watch endpoint intentionally unauthenticated) are
all resolved by direct verification against the real branch: the answer to all three is that the review
was looking at the wrong codebase, not that this task's own design has a real gap.
