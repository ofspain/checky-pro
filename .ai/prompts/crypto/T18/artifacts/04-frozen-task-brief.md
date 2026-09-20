STATUS: FROZEN

# crypto · T18 · Phase 4 — Frozen Task Brief

**Human Approval gate.** Approved 2026-09-12, all 12 Phase 3 findings as recommended, including a
blocking architecture pivot.

## Design-challenge resolution log

| # | Finding | Severity | Disposition | Change made |
|---|---|---|---|---|
| 3 | Reorg detection assumed adapters re-deliver already-seen `txHash`es via `subscribeAddress`'s push mechanism | High | **ACCEPTED — architecture pivot, verified blocking** | Confirmed by reading `EthereumAdapter.pollOnce`/`TronAdapter.pollOnceUnguarded` directly: both scan strictly forward (`fromBlock = lastScannedBlock + 1`) and have no mechanism to re-report or re-check an already-scanned transaction — the Phase 2 design's trigger could never fire. Replaced entirely: `Watcher` gains a new `checkForReorg()` step, run on every existing scheduler tick (the same tick that already runs `pollFinality`), which re-queries `ChainAdapter.getTx(txHash)` — a synchronous **pull**, already VERBATIM, unaffected by the adapters' forward-only scan state — across all 3 configured providers for whatever `txHash` the watch's `ChainCursor` currently holds (if any), gathers exactly 3 fresh answers, and treats a local majority of `exists=false` as the reorg signal. |
| 1 | "Bypasses quorum, a 2-1 split should be `HELD` not decisive" | High | **REJECTED — verified factually wrong** | A 2-1 split for a Boolean fact IS `L1`'s own 2-of-3 agreement rule; `HELD` requires no majority at all, which the pigeonhole principle makes impossible for three booleans (verified identically in T17 Phase 10's own finality-majority reasoning). No quorum discipline is bypassed — the same 2-of-3 computation `QuorumEvaluator` would perform is computed locally instead of persisted, for the same reason T17's finality-poll fix avoided a premature `evaluate()` call. The valid part underneath is kept: the dissenting minority provider (still reporting `exists=true`) is flagged via the existing `recordDisagreementsIfAny` helper, reused as-is. |
| 7 | Race between reorg detection (observation path) and the finality poll (scheduler path) | Medium | **RESOLVED BY THE PIVOT** | Both concerns now run sequentially inside the same scheduler tick, on the same single thread — `checkForReorg()` runs first, before `pollFinalityFor`, so there is no cross-thread race to address. |
| 9 | No test for reorg discovered after `chain.tx.finalized` | Medium | **ACCEPTED, and the pivot makes it natural** | `checkForReorg()` keys off `ChainCursor.txHash() != null`, not `pendingFinality` membership — it keeps running on every tick for as long as the cursor holds a transaction, independent of whether finality was already reached. Added as a required test. |
| 4 | `ReorgDetector` in the new `reorg/` package would import `watch.Watch`/`watch.ChainCursor` entities, violating L15 | High | **ACCEPTED** | `ReorgDetector.reorg(...)` takes only primitive/value arguments (`UUID watchId`, `UUID invoiceUuid`, `String chain`, `String txHash`, `String tokenContractAddress`) and returns nothing. `Watcher` (already holding both entities) performs the actual `ChainCursor.invalidate(...)`/save itself and calls `ReorgDetector` only to emit the event — mirrors `TxLifecyclePublisher`'s shape but adapted for a genuine cross-package boundary, unlike `TxLifecyclePublisher`'s same-package situation in T17. |
| 2 | No recovery path if a reorged transaction is later re-included on the canonical chain | High | **ACCEPTED, documented, not fixed** | Once `chain.tx.reorged` is emitted, no further lifecycle event is ever possible for that `txHash` (the deterministic idempotency key permits only one `reorged` ever, and `EXISTENCE`/`CONFIRMATIONS`/`FINALITY` are already permanently decided). Disclosed as an accepted, out-of-scope limitation — supporting re-inclusion would need an "episode" concept across multiple frozen schemas, a genuine scope expansion beyond this task. |
| 5 | A reorg between the first `EXISTENCE` decision and `SEEN` (before the cursor snapshot exists) is undetectable | Medium | **ACCEPTED, documented** | `checkForReorg()` has nothing to check against until `ChainCursor.txHash()` is populated (at `SEEN`). Added explicitly to Scope/Out. |
| 8 | Full cursor reset (`invalidate`) is broader than "the affected transaction" — acceptable only under the current one-tx-per-watch design | Medium | **ACCEPTED, documented** | Noted explicitly: a future task relaxing the one-transaction-per-watch limitation (T17's own write-once snapshot) would need to replace `invalidate()` with a targeted rewind; not this task's problem. |
| 6 | `chain.tx.reorged` payload shape unspecified | Medium | **ACCEPTED** | Field-sourcing table added below. |
| 10 | Duplicate-key catch in `ReorgDetector` is undiscriminating | Low | **ACCEPTED, documented only** | Same disposition, same justification as T17's identical, already-settled decision (Phase 9 Finding #10/#12 there): `outbox`'s only unique constraint is `idempotency_key`, every other column is validated upstream, `ReorgDetector` is the only caller ever constructing this exact key shape. |
| 11 | Reorg cannot be declared with fewer than 3 real `getTx` answers | Low | **ACCEPTED, documented** | Mirrors the same exactly-3 discipline every other fact type already has; a degraded provider delays reorg detection, never forces a fabricated third answer. |
| 12 | No test for idempotent re-detection after a cursor is already invalidated | Low | **ACCEPTED** | `checkForReorg()` naturally short-circuits once `cursor.txHash()` is `null` post-invalidation — added as a required test. |

## Field-sourcing map — `chain.tx.reorged`

| Field | Source |
|---|---|
| `idempotencyKey` | `"{chain}:{txHash}:reorged"` |
| `watchId` (partition key) | `Watch.watchId()` |
| `invoiceUuid` | `Watch.invoiceUuid()` |
| `chain` | `Watch.chain()` |
| `txHash` | the invalidated transaction's hash |
| `tokenContractAddress` | `Watch.tokenContractAddress()` |
| `occurredAt` | `Clock.instant()` at publish time |

`amount`/`fromAddress`/`toAddress`/`confirmations` are omitted — the transaction is invalidated, these
values are no longer meaningful (mirrors `chain.tx.seen`'s own precedent of omitting fields with no
settled value yet).

## Frozen brief

### Task

Implement `reorg.ReorgDetector` and a new `Watcher.checkForReorg()` step: on every existing scheduler
tick, re-verify (via `ChainAdapter.getTx`, a synchronous pull) that the watch's currently-seen
transaction still exists; if a fresh 2-of-3 majority says it does not, walk `ChainCursor` backward and
emit `chain.tx.reorged`.

### Purpose

Defend against package.md's own threat #3 ("reorg after confirmed") — the one class of event that can
silently invalidate an already-attested payment.

### Scope

**In:**
- `ReorgDetector.reorg(UUID watchId, UUID invoiceUuid, String chain, String txHash, String tokenContractAddress)` — primitive-typed, no `watch/` entity import; builds the idempotency key and payload, calls `OutboxPublisher.publish`, catches `DataIntegrityViolationException` as benign.
- `ChainCursor.invalidate(Instant now)` — resets `lastBlock` to `UNSTARTED_SENTINEL`, `lastFinalizedBlock` to `null`, and the transaction snapshot (`txHash`/`amount`/`fromAddress`/`toAddress`) to `null`.
- `Watcher.checkForReorg()` — runs at the start of every `pollFinality()` tick (same scheduler, same thread as `pollFinalityFor`, eliminating any cross-thread race by construction). Looks up the watch's `ChainCursor`; if it holds a `txHash`, calls `getTx(txHash)` on all 3 configured providers, and on a fresh majority of `exists=false`: flags the dissenting minority via the existing `recordDisagreementsIfAny` helper, invalidates and saves the cursor, removes the `txHash` from `pendingFinality` if present, and calls `ReorgDetector.reorg(...)`. A tick with fewer than 3 real `getTx` answers is skipped entirely (mirrors the finality poll's own exactly-3 discipline). Runs for as long as the cursor holds a `txHash` — independent of whether finality has already been reached, so a reorg discovered after `chain.tx.finalized` is caught too.
- `WatcherRegistry` updated to construct `Watcher` with the new `ReorgDetector` dependency.
- `reorg.ReorgModuleBoundaryTest` — new source-scan test for the new `reorg/` package.
- `WatchModuleBoundaryTest`'s allow-list extended for `Watcher`'s new `reorg.ReorgDetector` import.

**Out:**
- Recovery/re-inclusion after a reorg (Finding #2) — once `chain.tx.reorged` fires, no further lifecycle event is ever possible for that `txHash`.
- Reorgs between the first `EXISTENCE` decision and `SEEN` (Finding #5) — nothing exists yet to check against.
- Any reorg signal finer than "existence flipped to false" (unchanged from Phase 2).
- A targeted, block-number-precise cursor rewind (Finding #8) — `invalidate()` is a full reset, acceptable only under T17's own one-transaction-per-watch design.
- Re-deciding `CONFIRMATIONS`/`FINALITY` under quorum after a reorg — both remain permanently decided.
- Any change to `contracts/` — none of the named contract files exist in this repository yet.

### Business Rules

R11 — see Phase 1 extraction for full text.

### Locked Decisions

L6 (reorg is a first-class transition, cursor walks backward, no forward-derived state survives); L5/R12
(service-wide, unmodified — `{chain}:{txHash}:reorged` idempotency key); L1 (2-of-3 quorum — the local
majority check computes the identical rule, not a bypass of it, per Finding #1's resolution).

### Dependencies

`Watch`, `ChainCursor`, `ChainCursorRepository`, `Watcher`, `WatcherRegistry` (`watch/`); `ChainAdapter#getTx`,
`ProviderSet.NamedAdapter`, `TxResult` (`adapter/`); `OutboxPublisher`, `EventTopics` (`events/`);
`ProviderHealthTracker` (existing `recordDisagreement`/`recordUnhealthy`, unmodified); `Clock`.

### Inputs

`ChainAdapter.getTx(txHash)` responses, per configured provider, for whatever `txHash` the watch's
`ChainCursor` currently holds.

### Outputs

One `OutboxEvent` row (`chain.tx.reorged`) per invalidating reorg episode; `ChainCursor`'s
`lastBlock`/`lastFinalizedBlock`/transaction-snapshot fields reset.

### State Changes

`INSERT` into `outbox` (already-granted); `UPDATE` on `chain_cursors` (already covered by T16's `V7`
broad grant — no new migration).

### Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/reorg/ReorgDetector.java`
- `services/crypto/src/test/java/com/themistra/crypto/reorg/ReorgModuleBoundaryTest.java`

### Files to Modify

- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java`
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchModuleBoundaryTest.java`

### Files NOT to Modify

`adapter/`, `observation/`, `quorum/`, `watch/TxLifecyclePublisher.java` (pattern precedent only),
`watch/Watch.java`, `WatchStatus.java`, `WatchService.java`, `WatchController.java`,
`ChainCursorRepository.java`; `V1`-`V8` migrations; any file under `spec/`.

### Acceptance Criteria

- **AC1 (R11).** A fresh, 3-answer `getTx` majority of `exists=false` for the watch's currently-seen `txHash` triggers exactly one `ReorgDetector.reorg` call and one cursor walk-back.
- **AC2 (L6).** After the walk-back, `lastBlock() == -1`, `lastFinalizedBlock() == null`, and the transaction-snapshot fields are all `null`.
- **AC3 (scope).** Independently tested: reorg discovered after `seen` alone, after `confirmed`, and after `finalized`.
- **AC4 (L5/R12).** Idempotency key is exactly `{chain}:{txHash}:reorged`.
- **AC5 (scope).** A fresh majority still `exists=true` triggers nothing and leaves the cursor untouched.
- **AC6 (L15).** `reorg/`'s files import no `watch/`/`adapter/` entity; `Watcher`'s only new import is `reorg.ReorgDetector`.
- **AC7 (scope).** A `getTx` tick with fewer than 3 real answers never declares a reorg.
- **AC8 (scope).** The dissenting minority provider in a 2-1 reorg-detecting split is flagged via `recordDisagreementsIfAny`.
- **AC9 (scope).** Re-checking after the cursor is already invalidated (`txHash() == null`) is a no-op — no exception, no further adapter calls.

### Required Tests

`shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg` (named); reorg after seen/confirmed/finalized
(AC3, three tests); idempotency key format (AC4); no-op on a still-`true` majority (AC5); no-op below 3
real answers (AC7); dissenting-minority disagreement flagged (AC8); no-op re-check after invalidation
(AC9); duplicate-publish swallowed (mirrors `TxLifecyclePublisherTest`); `ReorgModuleBoundaryTest` (AC6).

### Constraints

Same as T17's identical reasoning: no new thread pool (reuses `Watcher`'s existing scheduler); no new
endpoint/secret; no new outer transaction; `reorg/` needs its own boundary test (no precedent module to
extend); the exactly-3 discipline applies to `getTx`-based reorg checks exactly as it does to every
other fact type.

### Open Questions

No blockers. All 12 Phase 3 findings resolved above, including the blocking architecture pivot
(Finding #3), verified directly against `EthereumAdapter`/`TronAdapter` source before redesigning around
it.
