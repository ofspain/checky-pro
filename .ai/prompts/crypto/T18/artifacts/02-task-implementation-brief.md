# crypto · T18 · Phase 2 — Task Implementation Brief

## Task

Implement `reorg.ReorgDetector`: given a transaction the system had already recorded as `SEEN` (its
`ChainCursor` snapshot holds its `txHash`), detect that a fresh round of provider answers now says it
does not exist, walk that watch's `ChainCursor` backward (wiping all forward-derived state, L6), and
emit `chain.tx.reorged`.

## Purpose

Give the platform its defense against threat #3 from `package.md`'s own threat model — "reorg after
confirmed" — the one class of event that can silently turn an already-attested payment false.

## Scope

**In:**
- `ReorgDetector.reorg(Watch watch, String txHash)` — mirrors `TxLifecyclePublisher`'s three existing
  methods exactly: builds the aggregate id (`watchId`), the deterministic idempotency key
  (`{chain}:{txHash}:reorged`, L5/R12, unmodified from T17's established format), and a payload, then
  calls `OutboxPublisher.publish`, catching `DataIntegrityViolationException` as benign (same
  established reasoning as `TxLifecyclePublisher`, T17 Phase 9 Finding #12).
- `ReorgDetector.walkBack(ChainCursor cursor, Instant now)` (or equivalent) — resets `lastBlock` to the
  `UNSTARTED_SENTINEL` (`-1L`), `lastFinalizedBlock` to `null`, and the transaction snapshot
  (`txHash`/`amount`/`fromAddress`/`toAddress`) to `null`, so no forward-derived state survives (L6,
  literally: "No forward-derived state survives a reorg that invalidates it"). No `ChainAdapter` method
  can report "the block this reorg reverted to," so this is a full reset to the pre-observation state,
  not a partial rewind to a specific block number.
- `ChainCursor.invalidate(Instant now)` (new mutator) — the actual field-clearing implementation the
  above calls; the only sanctioned way `lastBlock`/`lastFinalizedBlock` are ever set backward, mirroring
  every other cursor mutator's own "no raw setters, named mutators" discipline.
- `Watcher` hook: **detection** happens where every provider answer already converges — once a fresh,
  fully-answered correlation exists for a `txHash` that the watch's own `ChainCursor` already has
  recorded as its seen transaction, the fresh answers' local `EXISTENCE` majority is checked *before*
  attempting `QuorumDecisionService.evaluate` (which would only ever throw the duplicate-decision
  exception for an already-decided fact, giving no usable signal). If that local majority is now
  `false`, this is the reorg: `ReorgDetector.reorg(...)` and the cursor walk-back are triggered, and the
  normal per-fact evaluation for this correlation is skipped entirely (there is nothing left to
  evaluate — the transaction the watch was tracking is gone). If the local majority is still `true`,
  nothing changes: the existing flow proceeds and its own duplicate-decision exception (already caught
  and swallowed, T16) absorbs it exactly as today.
- `WatcherRegistry` updated to construct `Watcher` with the new `ReorgDetector` dependency.
- `reorg.ReorgModuleBoundaryTest` — a new source-scan test for the new `reorg/` package, matching
  `WatchModuleBoundaryTest`/`FinalityModuleBoundaryTest`'s established one-per-package style.
- `WatchModuleBoundaryTest`'s allow-list extended for `Watcher`'s new `reorg.ReorgDetector` import.

**Out:**
- Detecting any reorg *before* a transaction ever reached `SEEN` — a disagreement/instability during the
  original correlation is already `HELD`'s job (L2), unrelated to reorg.
- Any reorg signal finer than "existence flipped to false" — a reorg that merely changes the agreed
  `amount`/`token`/`confirmations` while the transaction still exists is not detected by this task. R11's
  own wording ("previously observed transaction is invalidated") and package.md's threat framing both
  center on a transaction disappearing, not a value changing; no spec text requires the finer-grained
  case, and no infrastructure (block-hash tracking) exists to support it reliably. Disclosed, not solved.
- Re-deciding `CONFIRMATIONS`/`FINALITY` under quorum after a reorg — both remain permanently decided
  per the frozen `quorum_decisions` schema and `QuorumDecisionService`'s own one-decision-ever guarantee;
  `chain.tx.reorged` is an independent signal, not itself a new `QuorumDecision` (mirrors
  `chain.provider.degraded`'s own precedent, T10).
- Repeated `chain.tx.reorged` emission for the same `(chain, txHash)` across multiple reorg episodes —
  the deterministic idempotency key (L5, unmodified) permits only one, ever, matching T17's own identical
  treatment of `chain.tx.seen`/`confirmed`/`finalized`.
- A watch that has already recorded a *different* transaction than the one now reporting `exists=false`
  — T17's own disclosed write-once-snapshot limitation for a watch observing more than one transaction;
  unaffected by this task.
- Any change to `contracts/` — none of the named contract files exist in this repository yet (same
  disclosed gap as every prior task in this package).

## Business Rules

- **R11.** A previously observed transaction invalidated by a reorg triggers a backward cursor walk and a `chain.tx.reorged` emission.

## Locked Decisions

- **L6.** Reorg is a first-class transition: cursor walks backward, `chain.tx.reorged` is emitted, and no forward-derived state survives.
- **L5/R12 (service-wide, unmodified).** `chain.tx.reorged`'s idempotency key is `{chain}:{txHash}:reorged` — the same deterministic format as every other `chain.tx.*` event.

## Dependencies

`Watch`, `ChainCursor`, `ChainCursorRepository`, `Watcher`, `WatcherRegistry`, `TxLifecyclePublisher`
(pattern precedent, not a runtime dependency) (`watch/`); `ObservationSink`, `TxResult` (`adapter/`);
`OutboxPublisher`, `EventTopics` (`events/`); `QuorumDecisionService` (read-only reference to its
one-decision-ever behavior, not modified); `Clock`.

## Inputs

The same fresh, fully-answered `TxCorrelation` `Watcher` already assembles for every other fact type —
no new input source.

## Outputs

- One `OutboxEvent` row: `chain.tx.reorged`.
- `ChainCursor`'s `lastBlock`/`lastFinalizedBlock`/transaction-snapshot fields reset.

## State Changes

- `INSERT` into `outbox` (already-granted, T04).
- `UPDATE` on `chain_cursors` (already covered by T16's `V7` broad `UPDATE` grant — no new migration).
- No new tables, columns, or grants.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/reorg/ReorgDetector.java`
- `services/crypto/src/test/java/com/themistra/crypto/reorg/ReorgModuleBoundaryTest.java`

## Files to Modify

- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java` — reorg-detection hook ahead of the existing `EXISTENCE` evaluation path.
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java` — thread `ReorgDetector` into `Watcher`'s constructor.
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java` — new `invalidate(Instant now)` mutator.
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchModuleBoundaryTest.java` — allow-list extended for `reorg.ReorgDetector`.

## Files NOT to Modify

- `adapter/`, `observation/`, `quorum/` (T06-T09) — called, never changed.
- `watch/TxLifecyclePublisher.java` — pattern precedent only; `chain.tx.reorged` is emitted by the new
  `ReorgDetector`, not added to this class (keeps `reorg/`'s own package boundary meaningful; also
  avoids re-opening a file two other tasks' reviews have already frozen).
- `watch/Watch.java`, `WatchStatus.java`, `WatchService.java`, `WatchController.java`,
  `ChainCursorRepository.java`.
- `V1`-`V8` migrations — frozen; no new migration in this task.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R11).** A fresh, fully-answered correlation for a `txHash` already recorded as the watch's seen
  transaction, whose local `EXISTENCE` majority is now `false`, triggers exactly one `ReorgDetector.reorg`
  call and one cursor walk-back.
- **AC2 (L6).** After the walk-back, `ChainCursor.lastBlock() == -1`, `lastFinalizedBlock() == null`, and
  `txHash()`/`amount()`/`fromAddress()`/`toAddress()` are all `null`.
- **AC3 (scope, task statement).** Both tested independently: a reorg discovered after `chain.tx.seen`
  alone was emitted, and one discovered after `chain.tx.confirmed` was also already emitted.
- **AC4 (L5/R12).** `chain.tx.reorged`'s idempotency key is exactly `{chain}:{txHash}:reorged`.
- **AC5 (scope).** A fresh correlation for the seen `txHash` whose local majority is still `true` (a
  late, harmless re-confirmation) does not trigger `ReorgDetector` and does not alter the cursor.
- **AC6 (L15).** `reorg/`'s new files import no feature-module entity outside `watch`, `adapter`,
  `events`; `Watcher`'s new import is limited to `reorg.ReorgDetector`.

## Required Tests

- `shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg` (named test).
- A test confirming the reorg path is exercised after `seen` alone (AC3).
- A test confirming the reorg path is exercised after `confirmed` was also already emitted (AC3).
- A test confirming a late, still-`true` majority does not trigger a reorg or alter the cursor (AC5).
- A test confirming the idempotency key format (AC4).
- A test confirming a duplicate reorg publish attempt is swallowed, not propagated (mirrors
  `TxLifecyclePublisherTest`'s established `DataIntegrityViolationException` coverage).
- `ReorgModuleBoundaryTest` (AC6).

## Constraints

- **Performance:** no new scheduling, no new thread pool — reorg detection happens synchronously inside
  `Watcher`'s existing observation-handling path.
- **Security:** no new endpoint, no new secret.
- **Thread-safety:** no new shared mutable state beyond what `Watcher`'s existing correlation buffer and
  `ChainCursorRepository` already provide.
- **Transaction:** `ReorgDetector.reorg`'s outbox write and `ChainCursorRepository.save` are each already
  individually transactional (Spring Data); no new outer transaction wraps them together — matches every
  prior task's identical reasoning (T08/T16/T17) for why an outer transaction is unnecessary and would
  be actively wrong (would hold a connection open across unrelated work).
- **Module boundaries:** `reorg/` is a genuinely new top-level package; its own boundary test is required
  (no precedent module to silently extend).
- **Null handling:** the existing per-provider transport-failure handling (excluded from that tick's
  answers, `ProviderHealthTracker.recordUnhealthy`) is unchanged; reorg detection only ever runs once
  exactly 3 real fresh answers exist, mirroring every other fact type's own exactly-3 discipline.

## Open Questions

No blockers. The central Phase 1 open question (what concretely constitutes "invalidated") is resolved
above as "the fresh local `EXISTENCE` majority is now false" — a deliberate, conservative, disclosed
scope choice (Scope/Out), not left unresolved.
