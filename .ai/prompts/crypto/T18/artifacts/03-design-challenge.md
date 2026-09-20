# crypto · T18 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T18 — Reorg detector |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Review of the Phase 2 Task Implementation Brief (TIB). Findings are adversarial only; no redesign or implementation is proposed.

---

## 1. Reorg detection bypasses the quorum-decision service, emitting a lifecycle fact on a local majority alone

- **Issue:** The TIB declares a reorg when a fresh, fully-answered correlation's local `EXISTENCE` majority is `false`, and explicitly skips `QuorumDecisionService.evaluate` because it would only throw a duplicate-decision exception. This means `chain.tx.reorged` can be emitted after 2-of-3 providers report `exists=false` while 1 still reports `exists=true` — i.e., the exact 2-1 split that `QuorumEvaluator` would classify as `HELD` and alert ops on for every other fact type.
- **Severity:** High
- **Evidence:** `spec/crypto-service/agents.md` service-specific rule 2: "No single-provider answer ever leaves the service as fact. Every emitted fact required ≥ 2-of-3 agreement across independent providers. Disagreement → HELD, ops-alerted, never auto-resolved." TIB lines 32-41 describe emitting `chain.tx.reorged` without persisting a `QuorumDecision`.
- **Recommended brief amendment:** Either (a) require a unanimous `exists=false` from all 3 providers before declaring a reorg, or (b) explicitly document that `chain.tx.reorged` is the one exception to the quorum-decision path and justify why a 2-1 `false` majority is treated as decisive while a 2-1 `true` majority for other facts is not. If (b), add an ops-alert path for the minority provider that still reports `exists=true`.

## 2. No recovery path if a reorged transaction is later re-included on the canonical chain

- **Issue:** The TIB states that `CONFIRMATIONS`/`FINALITY` remain permanently decided due to `QuorumDecisionService`'s one-decision-ever guarantee, and repeated `chain.tx.reorged` emission is also impossible due to the deterministic idempotency key. If a transaction is reorged out and then re-included in a later block (a common real-world pattern), the system can emit `chain.tx.reorged` once but can never emit `chain.tx.seen`/`confirmed`/`finalized` again for the same `txHash`, nor emit a second `reorged` if it reorges again.
- **Severity:** High
- **Evidence:** TIB lines 55-61; `spec/crypto-service/design.md` §4c `quorum_decisions` has `uq_quorum_tx_fact` unique on `(chain, tx_hash, fact_type)`; `outbox` has `uq_outbox_idempotency` unique on `idempotency_key`.
- **Recommended brief amendment:** Add a Locked Decision or Open Question: "After `chain.tx.reorged` is emitted for a `txHash`, no further lifecycle events for that `txHash` are ever emitted. This task does not support transactions that are reorged out and later re-included." Add a test that a re-included txHash produces no second `seen`/`confirmed`/`finalized`.

## 3. Reorg detection assumes adapters re-deliver observations for already-seen transactions

- **Issue:** The TIB says detection happens "once a fresh, fully-answered correlation exists for a `txHash` that the watch's own `ChainCursor` already has recorded as its seen transaction." It does not state how such a fresh correlation is formed. If `EthereumAdapter`/`TronAdapter` only poll for *new* transactions at an address and never re-emit already-processed txHashes, the reorg-detection hook will never fire. This is an unstated dependency on adapter behavior.
- **Severity:** High
- **Evidence:** TIB lines 32-41; `spec/crypto-service/design.md` §4c `ChainAdapter.subscribeAddress` contract is not specific about re-delivery of historical txHashes; T17's `Watcher` correlation is pruned after the first full answer set.
- **Recommended brief amendment:** Add an explicit assumption: "Reorg detection depends on `ChainAdapter.subscribeAddress` re-delivering a previously observed `txHash` when the adapter itself detects it is no longer on the canonical chain." If this is not guaranteed by the adapter contract, add an Open Question or a dependency on a future adapter contract change.

## 4. `reorg/` package would import `watch/` entities, violating L15 module boundaries

- **Issue:** The TIB proposes `ReorgDetector.reorg(Watch watch, String txHash)` and `ReorgDetector.walkBack(ChainCursor cursor, Instant now)`. Both `Watch` and `ChainCursor` are JPA entities belonging to the `watch/` feature module. `spec/crypto-service/agents.md` L15 and the package-layout rules forbid cross-module entity imports; `reorg/` is a new top-level feature module.
- **Severity:** High
- **Evidence:** TIB lines 18-25, 29-30; `spec/crypto-service/design.md` §6 package map lists `Watch.java`/`ChainCursor.java` under `watch/`; `spec/crypto-service/agents.md` L15 ("no feature module imports another feature module's entity").
- **Recommended brief amendment:** Redesign the `ReorgDetector` API to accept only primitive/value arguments (`UUID watchId`, `String chain`, `String txHash`) and return a domain event or command object. Let `Watcher` (in `watch/`) own the `ChainCursor` mutation and call `ReorgDetector.reorg(watchId, chain, txHash)`. Update AC6 accordingly.

## 5. Reorg detection only works after `SEEN`; a pre-confirmation reorg cannot be detected

- **Issue:** The TIB explicitly scopes out reorg detection before a transaction reaches `SEEN` (lines 48-49). However, a transaction could receive an `EXISTENCE` quorum decision, then be reorged out before `CONFIRMATIONS` ever reaches quorum. Because the cursor snapshot is only populated at `SEEN`, this reorg would leave a persisted `AGREED true EXISTENCE` decision and a later `CONFIRMATIONS` evaluation that finds no answers — but no `chain.tx.reorged` event.
- **Severity:** Medium
- **Evidence:** TIB lines 48-49; T17's `handleSeenIfAgreed` populates the cursor snapshot only on `EXISTENCE AGREED true`.
- **Recommended brief amendment:** Document the limitation explicitly: "Reorgs that occur between the first `EXISTENCE` quorum decision and the `SEEN` event (i.e., before the cursor snapshot exists) are not detected by this task." Add it to Scope/Out and consider a future task.

## 6. `chain.tx.reorged` payload shape is unspecified

- **Issue:** The TIB says `ReorgDetector.reorg` "builds ... a payload" but never lists the payload fields. The deferred contract authoring (Scope/Out) means there is no generated type, but the field-sourcing map should still be specified now so tests can assert the right shape.
- **Severity:** Medium
- **Evidence:** TIB lines 18-22; `spec/crypto-service/design.md` §4c says "The other emitted events share this envelope" and lists optional fields including `addressPoisoningFlag`.
- **Recommended brief amendment:** Add a field-sourcing table for `chain.tx.reorged` (e.g., `idempotencyKey`, `watchId`, `invoiceUuid`, `chain`, `txHash`, `tokenContractAddress`, `occurredAt`; omit `amount`/`fromAddress`/`toAddress`/`confirmations` because the transaction is invalidated).

## 7. Race between reorg detection and the finality poll is not addressed

- **Issue:** Reorg detection runs synchronously inside `Watcher.handleObservation` (adapter callback threads), while the finality poll runs on `Watcher`'s private `sweepScheduler`. If a reorg is detected and the cursor is reset while `pollFinalityFor` is concurrently reading the cursor or about to publish `chain.tx.finalized`, the system could emit both `chain.tx.finalized` and `chain.tx.reorged` for the same transaction, or publish finalized with a partially-reset cursor.
- **Severity:** Medium
- **Evidence:** TIB lines 32-41 (reorg hook runs in observation path); T17's `Watcher.pollFinality` runs on `sweepScheduler`; `ChainCursor` is mutable and read/written from both paths.
- **Recommended brief amendment:** Add a thread-safety decision: either (a) `ReorgDetector.reorg` atomically removes the `txHash` from `pendingFinality` before resetting the cursor, and `pollFinalityFor` checks `pendingFinality` membership before publishing, or (b) document that the single-thread `sweepScheduler` and the observation callback are sufficiently synchronized via the existing `running` flag / lifecycle. Add a concurrent test.

## 8. Cursor reset to `UNSTARTED_SENTINEL` wipes all progress, not just the affected transaction

- **Issue:** The TIB's `invalidate` mutator resets `lastBlock` to `-1` and `lastFinalizedBlock` to `null`. This is defensible while each watch tracks only one transaction (T17's write-once snapshot), but the reset is far broader than "the block this reorg reverted to." If a future task relaxes the one-transaction-per-watch limitation, this reset would incorrectly discard progress on unrelated transactions.
- **Severity:** Medium
- **Evidence:** TIB lines 23-28; `spec/crypto-service/design.md` L6 says "walk the affected watcher cursor backward" — "affected" implies a targeted walk, not a full reset.
- **Recommended brief amendment:** Document that the full reset is acceptable only because T17's snapshot is write-once-per-watch, and that any future multi-transaction-per-watch design must replace `invalidate` with a targeted block-number rewind.

## 9. No test scenario for a reorg discovered after `chain.tx.finalized` was emitted

- **Issue:** AC3 requires testing reorg after `seen` and after `confirmed`. The actual threat model (`package.md` threat #3, "reorg after confirmed") is most severe after `chain.tx.finalized`, because that is when attestation/signing can occur. The TIB's required tests omit the post-`finalized` case.
- **Severity:** Medium
- **Evidence:** TIB AC3 lines 130-132; `spec/crypto-service/package.md` lines 16 and 137-138 (threat #3); TIB Required Tests lines 140-147.
- **Recommended brief amendment:** Add a required test: "A reorg discovered after `chain.tx.finalized` was already emitted triggers `chain.tx.reorged` and resets the cursor, even though `FINALITY` remains permanently decided."

## 10. Duplicate-key catch in `ReorgDetector` is undiscriminating

- **Issue:** The TIB replicates T17's `DataIntegrityViolationException` swallow without the stricter constraint that the catch must not mask non-idempotency causes. Because `outbox`'s only unique constraint is `idempotency_key`, this may be safe in practice, but the brief does not test or document the assumption.
- **Severity:** Low
- **Evidence:** TIB lines 21-22; T17 Phase 8 Finding 8 and T17 frozen brief Constraints.
- **Recommended brief amendment:** Either add a test that a non-duplicate-key `DataIntegrityViolationException` is propagated, or copy T17's disclosed justification into the TIB Constraints section.

## 11. Reorg detection is skipped if fewer than 3 providers answer

- **Issue:** The TIB says reorg detection "only ever runs once exactly 3 real fresh answers exist." If a provider is down and only two answers arrive, both saying `exists=false`, the reorg is not detected. This is consistent with the exactly-3 discipline but is a conservative choice that should be disclosed.
- **Severity:** Low
- **Evidence:** TIB lines 162-164.
- **Recommended brief amendment:** Add a note under Constraints or Open Questions: "A reorg cannot be declared with fewer than 3 real answers; a temporarily degraded provider delays reorg detection until the third provider responds."

## 12. No behavior specified when the cursor has already been invalidated

- **Issue:** If a reorg was already detected and the cursor reset, a subsequent fresh correlation for the same `txHash` (e.g., a delayed adapter re-delivery) could trigger `ReorgDetector.reorg` again. The deterministic idempotency key would cause `OutboxPublisher` to throw, and the catch would swallow it, but the cursor would be reset again harmlessly. This is benign but untested.
- **Severity:** Low
- **Evidence:** TIB lines 18-22, 59-61.
- **Recommended brief amendment:** Add a test: "A second reorg detection for the same `(chain, txHash)` is a no-op: no exception propagates, and the cursor remains invalidated."

---

(End of design challenge review. Findings are for human fold-in during Phase 4.)
