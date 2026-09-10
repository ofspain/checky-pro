# crypto · T17 · Phase 2 — Task Implementation Brief

## Task

Emit `chain.tx.seen`, `chain.tx.confirmed`, `chain.tx.finalized` from the existing watcher/quorum
pipeline. Extend `Watcher` to publish on `EXISTENCE`/`CONFIRMATIONS` quorum agreement, and add the
missing finality-polling path (nothing calls `getFinalityStatus`/`FinalityPolicy` today) so `FINALITY`
can itself be quorum-decided before `chain.tx.finalized` is ever emitted.

## Purpose

Give the platform's downstream consumers (Payment Service) the actual lifecycle signal the whole
quorum/watcher pipeline (T06-T16) exists to produce — until this task, quorum decisions are recorded
but nothing tells the outside world about them.

## Scope

**In:**
- `TxLifecyclePublisher` (new, mirrors `ProviderDegradedPublisher`) — one component, three methods
  (`seen`/`confirmed`/`finalized`), each building the aggregate id (`watchId`), deterministic
  idempotency key (`{chain}:{txHash}:{seen|confirmed|finalized}`, R12/L5), and payload, then calling
  `OutboxPublisher.publish`.
- `Watcher.evaluateFact` hook: when `EXISTENCE` quorum-decides `AGREED` with the agreed value `true`,
  publish `chain.tx.seen`. `QuorumDecisionService`'s own pre-flight rejection of a second decision for
  the same `(chain, txHash, EXISTENCE)` already guarantees this fires at most once ever, satisfying
  "first sighting" without new state.
- Same hook for `CONFIRMATIONS`: when it quorum-decides `AGREED`, publish `chain.tx.confirmed` with the
  agreed count. Per the Open Question below, this fires **once**, at `CONFIRMATIONS`'s one existing
  quorum-decision point — not repeatedly as confirmations accumulate.
- A small in-memory, per-`txHash` snapshot inside `Watcher` (`amount`, `tokenContractAddress`,
  `fromAddress`, `toAddress` — captured at the moment each is first known, since each correlation is
  pruned after its one-shot evaluation) so the eventual `chain.tx.finalized` payload can be built
  without re-deriving values from already-pruned state.
- A finality poll: for a transaction already `SEEN` (not yet `FINALIZED`), periodically call
  `ChainAdapter.getFinalityStatus(txHash)` per configured provider, evaluate each against that chain's
  `FinalityPolicy.isFinal(...)`, gather exactly 3 boolean answers into a `FINALITY` fact, and once
  `QuorumDecisionService.evaluate` decides `AGREED`/`true`, publish `chain.tx.finalized` and stop
  polling that transaction. Reuses `Watcher`'s existing per-watch scheduler rather than introducing a
  second scheduling mechanism.
- `FinalityPolicy` selection by chain: inject `List<FinalityPolicy>`, build a `Map<Chain,
  FinalityPolicy>` once (mirrors how `ProviderSet` already groups adapters by chain) — no new
  standalone dispatcher class needed for only two chains.
- `WatcherRegistry` updated to construct `Watcher` with its new dependencies (`TxLifecyclePublisher`,
  `List<FinalityPolicy>`, `AddressPoisoningDetector`).
- `WatcherProperties` gains a `finalityPollIntervalMs` field.

**Out:**
- Authoring `contracts/events/chain/*.v1.schema.json` files or generated models — deferred to task 23,
  matching `ProviderDegradedPublisher`'s own precedent (its payload record is explicitly "a concrete
  implementation... not itself a contract file"). This task's `TxLifecyclePublisher` payloads are built
  to match the `tx-finalized` VERBATIM shape in design.md §4c, not against a generated type.
- `chain.tx.reorged` emission and any cursor walk-back — task 18.
- A schema or architecture change to support genuinely repeated/incremental `chain.tx.confirmed`
  emission as confirmations keep climbing — see Open Questions. Out of this task's proportionate scope;
  T16 already deferred this exact problem here explicitly.
- Any change to `Watch`/`WatchStatus`/watch registration lifecycle.
- The Tron confirmation-count-basis decision (`package.md` Q4) — this task emits whatever count
  `CONFIRMATIONS`'s existing quorum decision already carries, unchanged.

## Business Rules

- **R8.** First quorum-agreed sighting of a transaction → emit `chain.tx.seen`.
- **R9.** Confirmations gained under quorum → emit `chain.tx.confirmed` with the count.
- **R10.** Quorum-agreed finality → emit `chain.tx.finalized`, never before.
- **R12.** Every `chain.tx.*` event carries `chain:txhash:eventtype`.

## Locked Decisions

- **L5.** Deterministic idempotency key `chain:txhash:eventtype` on every emitted event.

## Dependencies

`QuorumDecisionService`, `QuorumDecision`, `QuorumOutcome`, `ProviderAnswer<T>` (`quorum/`);
`FactType` (`observation/`); `FinalityPolicy`, `EthereumFinalityPolicy`, `TronFinalityPolicy`
(`finality/`); `ChainAdapter#getFinalityStatus`, `FinalityStatus`, `TxResult` (`adapter/`);
`OutboxPublisher`, `EventTopics` (`events/`); `Watch`, `ChainCursor`, `Watcher`, `WatcherRegistry`,
`ProviderSet` (`watch/`, `adapter/`); `AddressPoisoningDetector` (`provider/`); `Clock`.

## Inputs

- `QuorumDecisionService.evaluate`'s return value and the `List<ProviderAnswer<T>>` already in scope at
  each `Watcher.evaluateFact` call site (`EXISTENCE`, `CONFIRMATIONS`).
- `ChainAdapter.getFinalityStatus(txHash)` responses, per provider, on a new poll path.

## Outputs

- `OutboxEvent` rows: `chain.tx.seen`, `chain.tx.confirmed`, `chain.tx.finalized`.
- `QuorumDecision` rows for `FactType.FINALITY` (new — never evaluated before this task).
- `ChainCursor.lastFinalizedBlock` advanced (already-migrated column, currently always `null`).

## State Changes

- `INSERT` into `outbox` (already-granted, T04).
- `INSERT` into `quorum_decisions` for `FINALITY` (already-granted generically, T02).
- `UPDATE` on `chain_cursors.last_finalized_block` (already covered by T16's `V7` broad `UPDATE`
  grant — no new migration needed).
- No new tables, columns, or grants.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/watch/TxLifecyclePublisher.java`

## Files to Modify

- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java` — publish hooks, per-txHash
  snapshot cache, finality poll loop, `FINALITY` quorum evaluation.
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java` — thread the new
  dependencies into `Watcher`'s constructor.
- `services/crypto/src/main/java/com/themistra/crypto/common/config/WatcherProperties.java` — add
  `finalityPollIntervalMs`.
- `services/crypto/src/main/resources/application.properties` — new property value.

## Files NOT to Modify

- `adapter/`, `observation/`, `quorum/QuorumEvaluator.java`, `quorum/QuorumDecisionService.java`,
  `finality/` (T06-T14) — called, never changed.
- `watch/Watch.java`, `WatchStatus.java`, `WatchService.java`, `WatchController.java`,
  `ChainCursorRepository.java`, `WatchRepository.java`.
- `V1`-`V7` migrations — frozen; no new migration in this task.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R8).** `chain.tx.seen` is emitted exactly once, only when `EXISTENCE` quorum-decides `AGREED`
  with value `true` — never on `HELD`, never on an `AGREED false`.
- **AC2 (R9).** `chain.tx.confirmed` is emitted exactly once, carrying the agreed count, only when
  `CONFIRMATIONS` quorum-decides `AGREED` — never before `EXISTENCE` has itself agreed `true`.
- **AC3 (R10).** `chain.tx.finalized` is emitted only once `FINALITY` quorum-decides `AGREED` with
  value `true`; never before; the finality poll never starts for a transaction that hasn't reached
  `SEEN`.
- **AC4 (R12/L5).** Every emitted event's `idempotencyKey` is exactly `{chain}:{txHash}:{eventtype}`.
- **AC5 (scope).** `TxLifecyclePublisher` passes `watchId` as `aggregateId` for all three event types.
- **AC6 (scope).** A duplicate/repeated observation or poll result never causes a second publish call
  for the same `(chain, txHash, eventtype)` — relies on the same one-shot quorum-decision guarantee
  `Watcher` already established in T16.

## Required Tests

- `shouldEmitChainTxSeenOnQuorumAgreedFirstSighting` (R8).
- `shouldEmitChainTxConfirmedWithConfirmationCount` (R9).
- `shouldEmitChainTxFinalizedOnlyAtPerChainFinality` (R10).
- `shouldCarryDeterministicIdempotencyKeyOnEveryEmittedEvent` (R12/L5).
- A test confirming no `chain.tx.seen` on `EXISTENCE` `HELD` or `AGREED false`.
- A test confirming the finality poll only starts after `SEEN`, stops after `FINALIZED`, and never
  double-publishes across repeated poll ticks.
- A test confirming `TxLifecyclePublisher` uses `watchId` as `aggregateId` for each event type.
- A `TxLifecyclePublisherTest` mirroring `ProviderDegradedPublisherTest`'s mocked-`OutboxPublisher`
  style.

## Constraints

- **Performance:** finality polling runs on the same per-watch virtual-thread scheduler `Watcher`
  already owns; no new thread pool.
- **Security:** no new endpoint, no new secret.
- **Thread-safety:** the new per-txHash snapshot cache and "seen"/"finalized" in-memory tracking must
  be safe under the same concurrent virtual-thread delivery `Watcher`'s existing correlation buffer
  already handles.
- **Transaction:** `TxLifecyclePublisher.publish` calls join `OutboxPublisher`'s existing transactional
  behavior; no new outer transaction.
- **Module boundaries:** `watch/`'s new/modified files import no feature-module entity outside
  `adapter`, `observation`, `quorum`, `provider`, `finality`, `events` — same allowance shape
  `WatchModuleBoundaryTest` already grants, extended to include `finality`.
- **Null handling:** a provider transport failure during a finality poll tick is recorded via the
  existing `ProviderHealthTracker` path and excluded from that tick's answer set, mirroring `Watcher`'s
  established EXISTENCE/AMOUNT/TOKEN/CONFIRMATIONS handling — never fewer than 3 real answers reach
  `QuorumDecisionService.evaluate`.

## Open Questions

Not blockers for this task's own narrow scope as defined above, but disclosed per this pipeline's
guardrail:

- **R9's "gains confirmations" (plural/repeated) framing vs. the one-shot `uq_quorum_tx_fact`
  constraint.** This task emits `chain.tx.confirmed` once, at `CONFIRMATIONS`'s single existing
  quorum-decision point (matching T16's own established one-shot design), not repeatedly as
  confirmations accumulate. A genuinely repeated/incremental implementation would require a schema
  change (a new table permitting multiple confirmation-count decisions per tx) and a change to
  `Watcher`'s own "evaluate each fact exactly once" internal guard — both beyond this task's
  proportionate scope. T16's frozen brief already named this as "task 17's own problem, not resolved
  here"; this brief resolves it for now as a disclosed, deliberate scope-limiting choice, not a defect.
  Phase 3 should confirm or override this.
- **`package.md` Q4 (Tron confirmation-count basis)** remains unresolved upstream; this task passes
  through whatever count `CONFIRMATIONS`'s quorum decision already carries, without interpreting it.
