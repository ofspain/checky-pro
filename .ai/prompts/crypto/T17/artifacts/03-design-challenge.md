# crypto · T17 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T17 — Seen/confirmed/finalized emission |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Review of the Phase 2 Task Implementation Brief (TIB). Findings are adversarial only; no redesign or implementation is proposed.

---

## 1. Idempotency key collapses emissions across multiple watches for the same transaction

- **Issue:** The TIB carries forward L5/R12 literally (`chain:txhash:eventtype`) and uses `watchId` only as the Kafka partition key. If two watches (different invoices/addresses) observe the same `txHash`, only one `chain.tx.seen`/`confirmed`/`finalized` row can ever sit in `outbox` because of `uq_outbox_idempotency`.
- **Severity:** Critical
- **Evidence:** `spec/crypto-service/design.md` §4c states both `partition key = watchId` and `idempotency key = chain:txHash:finalized`. `V1__chain_baseline.sql` enforces `UNIQUE (idempotency_key)` on `outbox`. The TIB AC4 pins the idempotency key to the narrower form without reconciling the partition-key design.
- **Recommended brief amendment:** Either (a) add `watchId` to the idempotency key and accept one event per watch, or (b) explicitly document that T17 intentionally emits at most one lifecycle event per chain+txHash across all watches, with downstream consumers joining to `watches` if they need per-watch context. Do not leave the two design.md statements in conflict.

## 2. R9 "gains confirmations" is resolved as one-shot emission without explicit acceptance-criteria coverage

- **Issue:** `requirements.md` R9 reads "WHEN a SEEN transaction **gains confirmations** under quorum, THEN the system SHALL emit `chain.tx.confirmed` carrying the confirmation count." The natural reading is repeated/incremental emission. The TIB deliberately emits once at the single `CONFIRMATIONS` quorum-decision point and buries this scope limit in Open Questions.
- **Severity:** High
- **Evidence:** `spec/crypto-service/requirements.md` §R9; TIB §Open Questions lines 173-181; TIB AC2.
- **Recommended brief amendment:** Move the one-shot resolution from Open Questions into a **Locked Decision** or an explicit acceptance criterion, e.g. "`chain.tx.confirmed` is emitted exactly once per transaction, at the first `CONFIRMATIONS` quorum decision." Update task 23's contract-authoring note to record this semantic choice in the schema description.

## 3. No explicit requirement to log finality-poll provider responses verbatim before the quorum decision

- **Issue:** L3 requires every provider response to be persisted verbatim in the observation log *before* the quorum decision. The TIB describes calling `getFinalityStatus`, evaluating against `FinalityPolicy.isFinal(...)`, and feeding a `FINALITY` fact into quorum, but never states that the raw `FinalityStatus` per provider is inserted into `observations` first.
- **Severity:** High
- **Evidence:** `spec/crypto-service/agents.md` service-specific rule 3; `spec/crypto-service/design.md` L3; TIB lines 34-38.
- **Recommended brief amendment:** Add an explicit step: "Before each `FINALITY` quorum evaluation, persist one `observations` row per provider containing the verbatim `FinalityStatus` raw response, identical to the EXISTENCE/CONFIRMATIONS path." Add a corresponding test.

## 4. Per-`txHash` in-memory snapshot is unbounded and is lost on restart

- **Issue:** The TIB proposes a small in-memory snapshot inside `Watcher` to retain `amount`, `tokenContractAddress`, `fromAddress`, `toAddress` for the final payload. There is no eviction policy, no size bound, and no persistence/recovery story. A process restart after `SEEN` but before `FINALIZED` loses the snapshot, yet the `FINALITY` quorum may still proceed because the decision state is in the DB.
- **Severity:** High
- **Evidence:** TIB lines 30-33; `spec/crypto-service/design.md` `chain_cursors` schema.
- **Recommended brief amendment:** Either (a) persist the snapshot alongside the watch/chain-cursor so it survives restart, or (b) source finalized-event fields from durable state (e.g., join the latest `observations` rows for AMOUNT/TOKEN/FROM/TO) rather than from an in-memory cache. If a memory-only cache is retained, define eviction, bounds, and a fallback/rebuild path.

## 5. Ordering between EXISTENCE, CONFIRMATIONS, and FINALITY is assumed but not guaranteed

- **Issue:** AC2 says `chain.tx.confirmed` is emitted "never before EXISTENCE has itself agreed true"; AC3 says finality polling "never starts for a transaction that hasn't reached SEEN." The TIB never states how the `Watcher` enforces that `CONFIRMATIONS`/`FINALITY` are evaluated only after `EXISTENCE` is `AGREED`, or how it handles observations that arrive out of order.
- **Severity:** High
- **Evidence:** TIB AC2, AC3, lines 23-29; `spec/crypto-service/requirements.md` §R8-R10.
- **Recommended brief amendment:** Add a decision: "`Watcher` skips `CONFIRMATIONS`/`FINALITY` evaluation for a `txHash` until the `EXISTENCE` quorum decision for that `txHash` is `AGREED true`, recorded durably." Add tests for out-of-order arrival.

## 6. "Never fewer than 3 real answers" contradicts provider-degradation handling

- **Issue:** The TIB Constraints section says a transport failure is excluded from the tick's answer set and then asserts "never fewer than 3 real answers reach `QuorumDecisionService.evaluate`." If one provider is unhealthy, only two answers exist. The brief does not state whether the tick is skipped, the existing 2-of-3 logic runs with 2 answers, or a new guard is introduced.
- **Severity:** Medium
- **Evidence:** TIB lines 163-166; `spec/crypto-service/design.md` L1 (2-of-3 quorum).
- **Recommended brief amendment:** Replace the contradictory wording with an explicit rule, e.g., "If fewer than 3 real `FinalityStatus` answers are available for a poll tick, that tick is skipped and the provider-health path is updated; `QuorumDecisionService.evaluate` is invoked only when ≥3 answers are present."

## 7. `AddressPoisoningDetector` is injected but its role in event payloads is undefined

- **Issue:** The TIB lists `AddressPoisoningDetector` as a new `Watcher` dependency and the `tx-finalized` schema includes `addressPoisoningFlag`, but the brief never says when poisoning detection runs, where the flag is stored, or which events carry it.
- **Severity:** Medium
- **Evidence:** TIB line 44; `spec/crypto-service/design.md` §4c `tx-finalized.v1.schema.json`; `spec/crypto-service/agents.md` L9.
- **Recommended brief amendment:** State whether `addressPoisoningFlag` is computed at `SEEN` time and cached in the per-`txHash` snapshot, and confirm it is included in `chain.tx.seen`, `chain.tx.confirmed`, and `chain.tx.finalized` payloads (per schema description).

## 8. Several required payload fields have no stated source in the brief

- **Issue:** The `tx-finalized` schema requires `watchId` and `chain`/`txHash`/`tokenContractAddress`/`amount`/`occurredAt`, and optionally includes `invoiceUuid`, `fromAddress`, `toAddress`, `confirmations`, and `addressPoisoningFlag`. The TIB's snapshot cache only covers `amount`, `tokenContractAddress`, `fromAddress`, `toAddress`, and says nothing about `invoiceUuid`, `confirmations`, or `occurredAt` sourcing for the three event types.
- **Severity:** Medium
- **Evidence:** `spec/crypto-service/design.md` §4c `tx-finalized.v1.schema.json`; TIB lines 30-33.
- **Recommended brief amendment:** Provide a field-level mapping table for `chain.tx.seen`, `chain.tx.confirmed`, and `chain.tx.finalized` payloads: source of `invoiceUuid`, `watchId`, `confirmations` (if any), `occurredAt`, and `addressPoisoningFlag`.

## 9. `chain_cursors.last_finalized_block` advancement is claimed but not specified

- **Issue:** TIB Outputs states "`ChainCursor.lastFinalizedBlock` advanced (already-migrated column, currently always `null`)." The Scope/State Changes sections do not describe when or how this column is updated during the finality poll.
- **Severity:** Medium
- **Evidence:** TIB lines 89, 95-97.
- **Recommended brief amendment:** Either (a) remove the output claim if cursor advancement is not in this task's scope, or (b) specify the update rule (e.g., "after `FINALITY` agrees true, set `last_finalized_block` to the finalized block number returned by the per-chain policy").

## 10. `AGREED false` for EXISTENCE is undefined

- **Issue:** AC1 says `chain.tx.seen` is emitted only when `EXISTENCE` quorum-decides `AGREED` with value `true`, "never on an `AGREED false`." The brief does not say what happens when ≥2 providers agree the transaction does not exist, or whether such an outcome can occur for a watched `txHash`.
- **Severity:** Low
- **Evidence:** TIB AC1; `spec/crypto-service/design.md` §4c `QuorumOutcome` enum.
- **Recommended brief amendment:** Add a note: "If `EXISTENCE` reaches `AGREED` with value `false`, no lifecycle event is emitted and the watch may be logged/alerted per existing provider-disagreement handling." Add a test case.

## 11. Finality poll scheduling and cancellation are underspecified

- **Issue:** The TIB says the poll "reuses `Watcher`'s existing per-watch scheduler rather than introducing a second scheduling mechanism" and "stops polling that transaction" after finalization, but does not describe the scheduling primitive, the key used to cancel the recurring poll, or what happens on watch unregistration.
- **Severity:** Medium
- **Evidence:** TIB lines 34-39, 152-153.
- **Recommended brief amendment:** State the scheduling primitive (e.g., `ScheduledFuture` returned by a `VirtualThreadPerTaskExecutor` or `Scheduler.scheduleAtFixedRate`), the cancellation trigger (`FINALITY AGREED` or watch `UNREGISTERED`), and the cleanup of any per-`txHash` scheduling state.

## 12. Multi-replica double-drive could produce redundant publish attempts

- **Issue:** The TIB defers reorg/watcher assignment concerns to task 18 and O5, but if two replicas concurrently drive the same watch, both may call `TxLifecyclePublisher` for the same event. The outbox unique constraint prevents duplicate rows, yet the brief does not say how the resulting `DataIntegrityViolationException` is handled.
- **Severity:** Low
- **Evidence:** `spec/crypto-service/design.md` O5; TIB AC6.
- **Recommended brief amendment:** Add a constraint note: "If a duplicate publish call reaches the outbox due to concurrent scheduling, the unique-key violation is swallowed and treated as successful idempotency; no downstream event is emitted twice." Test this path.

## 13. No contract-validation safety net while hand-writing JSON payloads

- **Issue:** The TIB defers authoring `contracts/events/chain/*.v1.schema.json` to task 23 and instructs `TxLifecyclePublisher` to hand-build payloads matching the `tx-finalized` VERBATIM shape. Without a generated model or contract test in T17, drift between hand-written JSON and the eventual schema is likely.
- **Severity:** Low
- **Evidence:** TIB lines 47-51; `spec/crypto-service/agents.md` platform rule: "Models are generated from `contracts/` — never hand-written."
- **Recommended brief amendment:** Add a T17 unit test that asserts the produced payload JSON matches the field names/types documented in `design.md` §4c, even though the schema file itself is authored later.

---

(End of design challenge review. Findings are for human fold-in during Phase 4.)
