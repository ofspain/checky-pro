# crypto · T18 · Phase 1 — Specification Extraction

## Business Rules

- **R11.** IF a previously observed transaction is invalidated by a chain reorg, THEN the system SHALL walk the affected watcher cursor backward and emit `chain.tx.reorged`.

## Locked Decisions

- **L6.** Reorg is a first-class transition: a reorg walks the watcher cursor/checkpoint backward and emits `chain.tx.reorged`. No forward-derived state survives a reorg that invalidates it.

## Files involved

**Existing — read/extend:**
- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java` — the sole existing consumer of `ObservationSink` callbacks; the sole place a late/contradicting observation for an already-resolved `txHash` currently arrives (and is currently absorbed as a benign duplicate-decision no-op, T16).
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java` — every existing mutator (`advanceTo`, `advanceFinalizedTo`, `recordSeenTransaction`) is forward-only or write-once; none walks backward.
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursorRepository.java` — `findByWatchId`/`save`, already sufficient for a backward mutator without new query methods.
- `services/crypto/src/main/java/com/themistra/crypto/watch/TxLifecyclePublisher.java` (T17) — direct structural precedent for `chain.tx.reorged`'s own publishing method.
- `services/crypto/src/main/java/com/themistra/crypto/events/EventTopics.java` — `"tx-reorged" → "chain.tx.reorged"` already mapped, never called.
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecisionService.java`, `QuorumDecision.java` — read-only reference: one-decision-ever-per-fact constraint that a reorg cannot un-decide.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java`, `adapter/model/TxResult.java` — VERBATIM; carry no reorg/block-hash signal.

**New, per design.md §6's own package map (exact shape is Phase 3's job, not Phase 1's):**
- `services/crypto/src/main/java/com/themistra/crypto/reorg/ReorgDetector.java` — named explicitly: "cursor walk-back + chain.tx.reorged — L6, R11."
- Whatever payload record formalizes `chain.tx.reorged`, mirroring `TxLifecyclePublisher`'s existing three.
- A new backward-walk `ChainCursor` mutator.

## Dependencies

`Watcher`, `ChainCursor`, `ChainCursorRepository`, `TxLifecyclePublisher`, `Watch` (`watch/`);
`ObservationSink`, `TxResult`, `ChainAdapter` (`adapter/`); `EventTopics`, `OutboxPublisher` (`events/`);
`QuorumDecisionService`, `QuorumDecision` (`quorum/`, read-only reference for the one-shot-per-fact
constraint a reorg must work around, not through); `Clock`.

## Acceptance Criteria

- **AC1 (R11).** A previously observed, quorum-agreed transaction that is subsequently invalidated by a
  reorg triggers `ReorgDetector`, which walks the affected watch's `ChainCursor` backward and emits
  `chain.tx.reorged` — exactly once per invalidating reorg episode.
- **AC2 (L6).** No forward-derived state (the `ChainCursor` snapshot/`lastBlock`/`lastFinalizedBlock`
  populated by an invalidated observation) survives the walk-back; whatever the correct post-reorg state
  is, it does not retain the reorg-invalidated values.
- **AC3 (scope, task statement).** A reorg discovered after `chain.tx.seen` has already been emitted, and
  one discovered after `chain.tx.confirmed` has already been emitted, are both independently exercised —
  the task statement's own explicit testing instruction, directly matching package.md's "threat #3:
  reorg after confirmed" framing.
- **AC4 (idempotency, inferred from L5/R12's service-wide rule, unmodified in this task).** Whatever
  emits `chain.tx.reorged` carries the same deterministic `{chain}:{txHash}:{eventtype}` idempotency-key
  shape every other `chain.tx.*` event already uses (T17 precedent) — L5/R12 are service-wide rules, not
  scoped to T17 alone, so they continue to bind here even though this task's own header only scopes L6.

## Tests required

Named (`package.md` §8):
- `shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg` → R11.

Implied by the task statement's own explicit wording and this codebase's established one-test-per-
scenario style:
- A scripted reorg discovered after `chain.tx.seen` was already emitted for the transaction.
- A scripted reorg discovered after `chain.tx.confirmed` was already emitted for the transaction.
- A test confirming `chain.tx.reorged`'s idempotency key matches the established format.
- A test confirming no forward-derived cursor state survives the walk-back (L6, AC2).
- A module-boundary test for the new `reorg/` package (matching `WatchModuleBoundaryTest`/
  `FinalityModuleBoundaryTest`'s established source-scan style), if `reorg/` is confirmed as a genuinely
  new top-level package.

## Open Questions

Genuine blockers for Phase 2/3 to resolve, not guessed at here (per Phase 0's own disclosed unknowns):

- **No reorg signal exists anywhere in `ChainAdapter`/`TxResult`/`FinalityStatus`.** What concretely
  constitutes "invalidated" (R11's own word) — `exists` flipping to `false`, a changed `blockNumber`, a
  `confirmations` count resetting lower than previously agreed, or some combination — is not specified
  anywhere in `requirements.md`, `design.md`, or `package.md`. This is the central design question Phase
  3 must resolve.
- **Where `ReorgDetector` plugs into the existing pipeline** — a second `ObservationSink` consumer
  alongside `Watcher`, a component `Watcher` itself calls when it notices a contradicting late
  observation, or an entirely separate path — undetermined.
- **What "walk the cursor backward" concretely targets** — no `ChainAdapter` method returns "the block
  this chain reverted to." Undetermined.
- **Interaction with the one-shot `CONFIRMATIONS`/`FINALITY` quorum decisions** — neither can be
  re-decided once persisted (frozen `quorum_decisions` schema); `chain.tx.reorged` is most likely an
  independent signal, not itself a new `QuorumDecision` (mirrors `chain.provider.degraded`'s own
  precedent of never being a `QuorumDecision`), but this is Phase 3's call, not assumed here.
- **package.md's own Definition-of-Done checklist item** ("Reorg walks the cursor backward and emits
  chain.tx.reorged; no forward state survives a reorg it invalidates (L6)") restates R11/L6 verbatim and
  raises no additional requirement beyond what's already extracted above.
