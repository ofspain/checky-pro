STATUS: FROZEN

# crypto · T17 · Phase 4 — Frozen Task Brief

**Human Approval gate.** Approved 2026-09-12, all 13 Phase 3 findings as recommended.

## Design-challenge resolution log

| # | Finding | Severity | Disposition | Change made |
|---|---|---|---|---|
| 1 | Idempotency key (`chain:txhash:eventtype`) collapses emissions across watches sharing an address for the same `txHash` — `uq_outbox_idempotency` is `UNIQUE(idempotency_key)` with no watch-scoping column (verified directly against `V1__chain_baseline.sql`) | Critical | **ACCEPTED, resolved without touching the LOCKED key format** | L5/R12 fix the exact key shape — this task does not add `watchId` to it (that would silently deviate from a LOCKED decision). Instead: at most one `chain.tx.*` event of a given type is ever emitted system-wide per `(chain, txHash)`, even if two watches (T15/T16 already permit two watches sharing an address) both independently observe the same transition. The second attempt's duplicate-key violation is caught and swallowed as benign (see #12). Documented as an accepted, disclosed limitation, not silently designed around. |
| 2 | R9's one-shot resolution was buried in Open Questions, not a stated criterion | High | **ACCEPTED** | Promoted to Locked Decision (below) and AC2. |
| 3 | No step logs each provider's raw `FinalityStatus` verbatim before the `FINALITY` quorum decision (L3) | High | **ACCEPTED** | Added: `ObservationLog.record(chain, txHash, provider, FactType.FINALITY, rawJson)` for every provider's finality-poll response, before `QuorumDecisionService.evaluate` for `FINALITY` — identical ordering to every other fact type. |
| 4 | In-memory per-`txHash` snapshot (amount/token/from/to) is unbounded and lost on restart | High | **ACCEPTED, redesigned** | Replaced with durable state: `ChainCursor` gains four new nullable columns (`tx_hash`, `amount`, `from_address`, `to_address`), populated once (via a new `ChainCursor.recordSeenTransaction(...)` method) the moment `EXISTENCE` quorum-decides `AGREED true`. New additive migration `V8__crypto_chain_cursors_tx_snapshot.sql` (`ALTER TABLE` only — no new grant needed; `crypto_app`'s existing broad `UPDATE` on `chain_cursors`, T16's `V7`, already covers new columns on the same table). `tokenContractAddress` is sourced from `Watch.tokenContractAddress()` directly (already durable, no duplication needed). |
| 5 | `EXISTENCE`-before-`CONFIRMATIONS`/`FINALITY` ordering assumed, not guaranteed | High | **ACCEPTED, documented + one new gate** | For `CONFIRMATIONS`: already structurally guaranteed — `evaluateFact`'s existing `requireExists` filter (T16) means a provider only contributes a `CONFIRMATIONS` answer when it also reports `exists=true`, and `EXISTENCE` is evaluated earlier in the same `recordAnswerAndMaybeEvaluate` call — no new code needed, only documentation + a test. For `FINALITY`: the finality poll for a `txHash` starts only once `ChainCursor`'s new snapshot columns are populated (i.e., `EXISTENCE` has already agreed `true`) — an explicit, checkable gate, not an assumption. |
| 6 | Contradictory "never fewer than 3 real answers reach `evaluate`" wording alongside provider-degradation handling | Medium | **ACCEPTED, wording fix** | Clarified: a finality-poll tick with fewer than 3 real `FinalityStatus` answers is skipped entirely (no `evaluate` call, non-responders marked unhealthy/lagging via the existing `ProviderHealthTracker` path) — mirrors `Watcher`'s already-established `EXISTENCE`/`AMOUNT`/`TOKEN`/`CONFIRMATIONS` handling exactly, no new mechanism. |
| 7 | `AddressPoisoningDetector`'s role in event payloads was undefined | Medium | **ACCEPTED, corrected and descoped** | Investigation found the TIB's own dependency listing was factually wrong: the class is `token.AddressPoisoningDetector`, not `provider.AddressPoisoningDetector`, and its API (`detectPoisoning(candidateAddress, previouslySeenAddresses)`) has no established call site or source for `previouslySeenAddresses` in the watcher pipeline. `addressPoisoningFlag` is an **optional** field in the `tx-finalized` schema (not in its `required` array) — this task omits it entirely from all three payloads. Proper wiring is deferred to whichever task actually integrates address-poisoning detection into the watch/observation flow; not invented here. |
| 8 | No field-level sourcing for each event payload | Medium | **ACCEPTED** | Field-mapping table added below. |
| 9 | `ChainCursor.lastFinalizedBlock` advancement claimed but unspecified | Medium | **ACCEPTED** | New `ChainCursor.advanceFinalizedTo(long finalizedBlockNumber, Instant now)` — forward-only guard, mirrors the existing `advanceTo` method exactly. Called once `FINALITY` quorum-decides `AGREED true`. |
| 10 | `EXISTENCE` `AGREED false` outcome undefined | Low | **ACCEPTED, documented** | No lifecycle event is emitted. Per T16's own frozen, unmodified design, `QuorumDecisionService` permits only one `EXISTENCE` decision ever per `(chain, txHash)` — an `AGREED false` outcome is therefore permanent for that transaction hash. This is a pre-existing T16 characteristic, not something T17 introduces or can revisit within its own scope. |
| 11 | Finality-poll scheduling/cancellation underspecified | Medium | **ACCEPTED** | Reuses `Watcher`'s existing private per-watch virtual-thread scheduler (no second scheduling mechanism) at a new `finalityPollIntervalMs` cadence. A `txHash` enters polling once its `ChainCursor` snapshot is populated (#5) and is removed from the in-memory "pending finality" set once `FINALITY` agrees `true`; `Watcher.stop()`'s existing `sweepScheduler.shutdownNow()` cancels all pending poll ticks along with the existing sweep task — no separate cleanup path needed. |
| 12 | Concurrent duplicate publish (multi-replica or multi-watch) unhandled | Low | **ACCEPTED** | `TxLifecyclePublisher` catches `DataIntegrityViolationException` from `OutboxPublisher.publish` and treats it as benign (logged, not propagated) — mirrors `Watcher`'s own established precedent (T16) of swallowing `QuorumDecisionService`'s duplicate-decision `IllegalStateException` as a benign no-op. `OutboxPublisher` itself is unmodified — its own Javadoc explicitly disclaims this responsibility, so the catch lives in the new caller, not the shared component. |
| 13 | No safety net against hand-written payload drift from the eventual contract schema | Low | **ACCEPTED** | Add a unit test asserting each payload's serialized field names/types match `design.md` §4c's documented `tx-finalized` shape (and the analogous seen/confirmed shape) — schema authoring itself stays deferred to task 23. |

## Field-sourcing map (Finding 8)

| Field | `chain.tx.seen` | `chain.tx.confirmed` | `chain.tx.finalized` | Source |
|---|---|---|---|---|
| `idempotencyKey` | ✓ | ✓ | ✓ | `"{chain}:{txHash}:{seen\|confirmed\|finalized}"` |
| `watchId` (partition key) | ✓ | ✓ | ✓ | `Watch.watchId()` |
| `invoiceUuid` | ✓ | ✓ | ✓ (optional) | `Watch.invoiceUuid()` |
| `chain` | ✓ | ✓ | ✓ | `Watch.chain()` |
| `txHash` | ✓ | ✓ | ✓ | the correlation's `txHash` |
| `tokenContractAddress` | ✓ | ✓ | ✓ (required) | `Watch.tokenContractAddress()` |
| `amount` | — | — | ✓ (required) | `ChainCursor`'s new `amount` snapshot column |
| `fromAddress`/`toAddress` | — | — | ✓ (optional) | `ChainCursor`'s new snapshot columns |
| `confirmations` | ✓ (optional) | ✓ (required for this event) | ✓ (optional) | the agreed `CONFIRMATIONS` value, passed directly at the `evaluateFact` call site |
| `addressPoisoningFlag` | — | — | omitted (optional) | descoped, Finding 7 |
| `occurredAt` | ✓ | ✓ | ✓ | `Clock.instant()` at publish time |

## Frozen brief

### Task

Emit `chain.tx.seen`/`chain.tx.confirmed`/`chain.tx.finalized` from the watcher/quorum pipeline, adding
the previously-nonexistent finality-polling path so `FINALITY` can itself be quorum-decided.

### Purpose

Give downstream consumers (Payment Service) the transaction-lifecycle signal the whole quorum/watcher
pipeline (T06-T16) exists to produce.

### Scope

**In:**
- `TxLifecyclePublisher` (new) — `seen(Watch, txHash)`, `confirmed(Watch, txHash, confirmations)`,
  `finalized(Watch, txHash, ChainCursor)` methods; each builds `aggregateId = watchId`, the deterministic
  idempotency key, and the payload per the field-sourcing map above, then calls
  `OutboxPublisher.publish`, catching and swallowing `DataIntegrityViolationException` (#12).
- `Watcher.evaluateFact` hooks: on `EXISTENCE` `AGREED true` → populate `ChainCursor`'s new snapshot
  columns (#4) and publish `seen`; on `CONFIRMATIONS` `AGREED` → publish `confirmed` (exactly once, #2).
- New finality poll inside `Watcher`: for any `txHash` with a populated snapshot (i.e., seen) not yet
  finalized, on `finalityPollIntervalMs`, call `getFinalityStatus` per configured provider, log each
  raw response verbatim (#3), evaluate against that chain's `FinalityPolicy.isFinal(...)`, gather exactly
  3 boolean answers (skip the tick if fewer, #6), quorum-evaluate `FactType.FINALITY`, and on
  `AGREED true`: advance `ChainCursor.lastFinalizedBlock` (#9) and publish `finalized`.
- `FinalityPolicy` selection by chain: inject `List<FinalityPolicy>`, build a `Map<Chain,
  FinalityPolicy>` once in `Watcher`'s constructor.
- `ChainCursor`: four new nullable columns + `recordSeenTransaction(...)` + `advanceFinalizedTo(...)`.
- `WatcherRegistry` updated to construct `Watcher` with `TxLifecyclePublisher` and `List<FinalityPolicy>`.
- `WatcherProperties` gains `finalityPollIntervalMs`.
- New migration `V8__crypto_chain_cursors_tx_snapshot.sql` (additive columns only, no new grant).

**Out:**
- Authoring `contracts/events/chain/*.v1.schema.json` — deferred to task 23 (#13 adds a same-task
  safety-net test instead, not the contract file itself).
- `chain.tx.reorged` and cursor walk-back — task 18.
- Genuinely repeated/incremental `chain.tx.confirmed` emission as confirmations keep climbing (#2) —
  emits once, at `CONFIRMATIONS`'s one existing quorum-decision point.
- `addressPoisoningFlag` sourcing/computation (#7) — descoped entirely, omitted from all payloads.
- Adding `watchId` to the idempotency key, or any other change to L5/R12's literal format (#1).
- The Tron confirmation-count-basis decision (`package.md` Q4) — passthrough only.
- Any change to `Watch`/`WatchStatus`/watch registration lifecycle.

### Business Rules

R8, R9 (one-shot, #2), R10, R12 — see Phase 1 extraction for full text.

### Locked Decisions

- **L5/R12.** Idempotency key is exactly `{chain}:{txHash}:{eventtype}` — literal, unmodified, even
  though this permits at most one event per `(chain, txHash, eventtype)` system-wide (#1).
- **R9 one-shot (new, this brief).** `chain.tx.confirmed` is emitted exactly once per transaction, at
  the first (and only) `CONFIRMATIONS` quorum decision — not repeatedly as confirmations accumulate.
- L1 (2-of-3, exactly 3 real answers — extends to `FINALITY`), L3 (log before decide — extends to
  `FINALITY`), L14 (no `ChainAdapter`-implementation-specific code path), L15 (module boundaries,
  extended to permit `finality` imports in `watch/`).

### Dependencies

`QuorumDecisionService`, `ProviderAnswer<T>` (`quorum/`); `FactType` (`observation/`); `FinalityPolicy`,
`EthereumFinalityPolicy`, `TronFinalityPolicy` (`finality/`); `ChainAdapter#getFinalityStatus`,
`FinalityStatus` (`adapter/`); `OutboxPublisher`, `EventTopics` (`events/`); `Watch`, `ChainCursor`,
`Watcher`, `WatcherRegistry`, `ProviderSet` (`watch/`, `adapter/`); `Clock`.
`token.AddressPoisoningDetector` is **not** a dependency of this task (#7, corrected from Phase 2).

### Inputs

`QuorumDecisionService.evaluate`'s return value and in-scope `ProviderAnswer<T>` values at each
`Watcher.evaluateFact` call site; `ChainAdapter.getFinalityStatus(txHash)` responses on the new poll path.

### Outputs

`OutboxEvent` rows for `chain.tx.seen`/`confirmed`/`finalized`; `QuorumDecision` rows for
`FactType.FINALITY` (new); `Observation` rows for `FactType.FINALITY` (new); `ChainCursor`'s new
snapshot columns and `lastFinalizedBlock`, all forward-only/write-once as specified.

### State Changes

`INSERT` into `outbox` (already-granted), `quorum_decisions` (already-granted), `observations`
(already-granted); `UPDATE` on `chain_cursors`' new columns and `last_finalized_block` (already covered
by T16's `V7` broad `UPDATE` grant — no new grant in `V8`).

### Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/watch/TxLifecyclePublisher.java`
- `services/crypto/src/main/resources/db/migration/V8__crypto_chain_cursors_tx_snapshot.sql`

### Files to Modify

- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/WatcherProperties.java`
- `services/crypto/src/main/resources/application.properties`

### Files NOT to Modify

`adapter/`, `observation/ObservationLog.java` and collaborators, `quorum/QuorumEvaluator.java`,
`quorum/QuorumDecisionService.java`, `finality/` (T06-T14, called never changed); `events/OutboxPublisher.java`
(called, never changed — the duplicate-key catch lives in the new `TxLifecyclePublisher`, #12);
`watch/Watch.java`, `WatchStatus.java`, `WatchService.java`, `WatchController.java`,
`ChainCursorRepository.java`, `WatchRepository.java`; `V1`-`V7` migrations; any file under `spec/`.

### Acceptance Criteria

- **AC1 (R8).** `chain.tx.seen` emitted exactly once, only on `EXISTENCE` `AGREED true`.
- **AC2 (R9, one-shot).** `chain.tx.confirmed` emitted exactly once, carrying the agreed count, only on
  `CONFIRMATIONS` `AGREED`, only after `EXISTENCE` has already agreed `true` for the same `txHash`.
- **AC3 (R10).** `chain.tx.finalized` emitted only on `FINALITY` `AGREED true`; the finality poll never
  starts before `EXISTENCE` has agreed `true` for that `txHash`.
- **AC4 (R12/L5).** Idempotency key is exactly `{chain}:{txHash}:{eventtype}` on every emission.
- **AC5 (scope).** `TxLifecyclePublisher` uses `watchId` as `aggregateId` for all three event types.
- **AC6 (scope, #1/#12).** A duplicate publish attempt for the same `(chain, txHash, eventtype)` —
  whether from a re-observation, a second watch sharing an address, or concurrent replicas — never
  propagates an exception and never produces a second outbox row.
- **AC7 (scope, #6).** A finality-poll tick with fewer than 3 real answers is skipped entirely; no
  `QuorumDecisionService.evaluate` call is made for it.
- **AC8 (scope, #9).** `ChainCursor.lastFinalizedBlock` only ever increases, set only on `FINALITY`
  `AGREED true`.
- **AC9 (L15).** `watch/`'s modified files import no feature-module entity outside `adapter`,
  `observation`, `quorum`, `provider`, `finality`, `events`.

### Required Tests

`shouldEmitChainTxSeenOnQuorumAgreedFirstSighting`, `shouldEmitChainTxConfirmedWithConfirmationCount`,
`shouldEmitChainTxFinalizedOnlyAtPerChainFinality`, `shouldCarryDeterministicIdempotencyKeyOnEveryEmittedEvent`
(all four named); plus: no `seen` on `EXISTENCE` `HELD`/`AGREED false`; verbatim `FINALITY` observation
logged before its quorum decision; finality poll gated on a populated snapshot and stopped after
`FINALITY` agrees; a skipped tick with <3 answers never calls `evaluate`; duplicate publish attempts
swallowed without exception or a second row; `TxLifecyclePublisher` uses `watchId` as `aggregateId`;
payload field-name/type shape test (#13); `ChainCursor` unit tests for the two new mutators
(`recordSeenTransaction`, `advanceFinalizedTo`, both forward-only/write-once where applicable); module
boundary test extension for the new `finality` import allowance.

### Constraints

Same as Phase 2 TIB (performance: reuses `Watcher`'s existing virtual-thread scheduler; security: no
new endpoint/secret; module boundaries extended to permit `finality`; transaction: no new outer
transaction), plus: the `TxLifecyclePublisher`'s duplicate-key catch must not mask any *other*
`DataIntegrityViolationException` cause beyond the idempotency-key conflict — tested explicitly.

### Open Questions

No blockers. `package.md` Q4 (Tron confirmation-count basis) remains an upstream, disclosed ambiguity,
passthrough only, not this task's to resolve.
