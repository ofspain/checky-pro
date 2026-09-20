# crypto · T17 · Phase 1 — Specification Extraction

## Business Rules

- **R8.** WHEN a watched transaction is first observed with quorum agreement, THEN the system SHALL emit `chain.tx.seen`.
- **R9.** WHEN a `SEEN` transaction gains confirmations under quorum, THEN the system SHALL emit `chain.tx.confirmed` carrying the confirmation count.
- **R10.** WHEN a transaction meets its chain's finality policy under quorum, THEN the system SHALL emit `chain.tx.finalized`, and SHALL NOT emit it before that policy is met.
- **R12.** WHEN any `chain.tx.*` event is emitted, THEN it SHALL carry the deterministic idempotency key `chain:txhash:eventtype` so consumers can dedupe.

## Locked Decisions

- **L5.** Deterministic idempotency key on every event: `chain:txhash:eventtype`. The same tx will be observed multiple times; consumers dedupe on this key (design.md, `ARCHITECTURE.md` §3.4).

## Files involved

**Existing — read/extend:**
- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java` — the only place a fact's `AGREED` quorum outcome is currently detected (return value of `QuorumDecisionService.evaluate`), and the only place per-provider `ProviderAnswer` values (amount, confirmations) are in hand.
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecisionService.java`, `QuorumDecision.java` — the fact-decision persistence; `QuorumDecision` carries outcome/counts only, no value.
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxPublisher.java`, `EventTopics.java` — sole emission path; `tx-seen`/`tx-confirmed`/`tx-finalized` topics already mapped.
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderDegradedPublisher.java` — direct structural precedent for this task's new publisher(s).
- `services/crypto/src/main/java/com/themistra/crypto/finality/FinalityPolicy.java` (+ `EthereumFinalityPolicy.java`, `TronFinalityPolicy.java`) — never yet called; no dispatcher exists.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java` (`getFinalityStatus`) — VERBATIM, never yet called by production code.
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java` (`lastFinalizedBlock`) — always `null` today; its own Javadoc anticipates this task populating it.
- `services/crypto/src/main/java/com/themistra/crypto/watch/Watch.java` — `invoiceUuid`, `chain`, `tokenContractAddress`, `expectedAmount` needed for the `tx-finalized` payload.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TxResult.java` — `fromAddress`/`toAddress`/`amount`/`confirmations` per-observation values.
- `services/crypto/src/main/java/com/themistra/crypto/provider/AddressPoisoningDetector.java` (T13) — source of the `tx-finalized` payload's `addressPoisoningFlag`.
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — `quorum_decisions.uq_quorum_tx_fact UNIQUE(chain, tx_hash, fact_type)`, read-only reference (immutable, per `agents.md`; a conflicting constraint for R9's repeated-emission semantics — see Open Questions).
- `services/crypto/src/main/resources/application.properties` — any new config keys this task needs (e.g. a finality-poll interval), following `WatcherProperties`'s (T16) established shape.

**New, per spec expectation (exact shape is Phase 3's job, not Phase 1's):**
- One or more `XPublisher` component(s) for `chain.tx.seen`/`chain.tx.confirmed`/`chain.tx.finalized`, mirroring `ProviderDegradedPublisher`.
- A new Flyway migration (`V8__...`) if any schema change is required (e.g. new tracking table/column, or a grant), per Phase 3's resolution of the R9-vs-`uq_quorum_tx_fact` conflict.
- Wiring to detect each transition and trigger the corresponding publish call — location (inside `Watcher`, a new collaborator, or a separate finality-polling component) is a Phase 3 decision.

## Dependencies

- `com.themistra.crypto.quorum.QuorumDecisionService`, `QuorumDecision`, `QuorumOutcome`, `ProviderAnswer<T>` (`quorum/`).
- `com.themistra.crypto.observation.FactType` — `EXISTENCE`, `CONFIRMATIONS`, `FINALITY` are the three relevant values here (`AMOUNT`/`TOKEN` already drive `UNKNOWN_TOKEN`/address-mismatch concerns handled elsewhere, not this task's trigger).
- `com.themistra.crypto.finality.FinalityPolicy`, `EthereumFinalityPolicy`, `TronFinalityPolicy` (T14).
- `com.themistra.crypto.adapter.ChainAdapter#getFinalityStatus`, `adapter.model.FinalityStatus`, `adapter.model.TxResult`.
- `com.themistra.crypto.events.OutboxPublisher`, `EventTopics`, `OutboxEvent`, `OutboxRelay` (T04).
- `com.themistra.crypto.watch.Watch`, `ChainCursor`, `Watcher`, `ProviderSet` (T15/T16).
- `com.themistra.crypto.provider.AddressPoisoningDetector` (T13).
- `Clock` (fixed in tests, per `agents.md`), `MeterRegistry` if any new metric is added (not required by R8-R10/R12/L5 themselves).
- Config: a new `themistra.crypto.*` properties group if finality polling needs its own interval/schedule, following `WatcherProperties`'s pattern.
- Contracts: `contracts/events/chain/tx-finalized.v1.schema.json` (named explicitly in this task's header; does not exist in the repo yet — same gap already disclosed at T16). `contracts/events/chain/tx-seen.v1.schema.json`/`tx-confirmed.v1.schema.json` are implied by R8/R9 but not individually named in the header.

## Acceptance Criteria

- **AC1 (R8).** `chain.tx.seen` is emitted exactly once, only on the transaction's first-ever quorum-agreed `EXISTENCE` decision (`exists=true`) for that `(chain, txHash)` — never on a re-observation, never before quorum agreement.
- **AC2 (R9).** `chain.tx.confirmed` is emitted carrying the agreed confirmation count, only for a transaction that has already been (or is concurrently) marked `SEEN` — never for a transaction that hasn't reached `EXISTENCE` quorum.
- **AC3 (R10).** `chain.tx.finalized` is emitted only once a transaction's finality status, evaluated under the same 2-of-3 quorum discipline as every other fact (L1), meets the per-chain `FinalityPolicy`'s `isFinal` check — and never before.
- **AC4 (R12/L5).** Every `chain.tx.seen`/`chain.tx.confirmed`/`chain.tx.finalized` event carries `idempotencyKey = "{chain}:{txHash}:{eventtype}"` exactly, deterministic (not randomized, unlike `ProviderDegradedPublisher`'s key) so a duplicate emission attempt is caught by the outbox's own `UNIQUE(idempotency_key)` constraint rather than silently double-publishing.
- **AC5 (R10, negative).** No code path can emit `chain.tx.finalized` for a transaction whose finality status has not been quorum-confirmed as final — this must hold even under duplicate/out-of-order observation delivery (established `Watcher` precedent from T16).
- **AC6 (scope/schema).** Whatever mechanism resolves R9's "gains confirmations" repeated-emission requirement must not violate `quorum_decisions.uq_quorum_tx_fact`'s existing `UNIQUE(chain, tx_hash, fact_type)` constraint (immutable migration) — either the design avoids relying on `quorum_decisions` for repeat `CONFIRMATIONS` decisions, or a new, additive schema element is introduced. Exact mechanism is Phase 3's job.

## Tests required

Named (`package.md` §8):
- `shouldEmitChainTxSeenOnQuorumAgreedFirstSighting` → R8.
- `shouldEmitChainTxConfirmedWithConfirmationCount` → R9.
- `shouldEmitChainTxFinalizedOnlyAtPerChainFinality` → R10.
- `shouldCarryDeterministicIdempotencyKeyOnEveryEmittedEvent` → R12/L5.

Implied, following this codebase's own established test-writing pattern (T10/T16 precedent — one test per acceptance criterion/finding, not just the four named ones):
- A test confirming `chain.tx.seen` is never emitted for a `HELD` (disagreement) `EXISTENCE` outcome, and never for `exists=false`.
- A test confirming no duplicate `chain.tx.seen`/`chain.tx.confirmed`/`chain.tx.finalized` emission across repeated/duplicate observation delivery for the same transaction (mirrors `WatcherTest`'s existing duplicate-observation and duplicate-decision coverage from T16 Phase 9/11).
- A test confirming `chain.tx.finalized` is withheld when `FinalityStatus.txBlockNumber() > finalizedBlockNumber()`, and emitted once it is not.
- A test confirming the new publisher(s) pass `watchId` as `aggregateId` (partition-key convention, design.md §4c).
- A test confirming the exact idempotency-key format for all three event types (`{chain}:{txHash}:seen`/`confirmed`/`finalized` or whatever literal `eventtype` token Phase 3 pins — must match `EventTopics`' existing aggregate-type strings or be explicitly reconciled with them).
- A module-boundary check (source-scan, matching `WatchModuleBoundaryTest`'s established style) if any new package is introduced.
- Whatever integration/Testcontainers coverage Phase 3 decides is needed for the outbox row's actual persistence (existing `OutboxPublisherTest`/`ProviderDegradedPublisherTest` precedent suggests plain mocked-repository unit tests suffice for the publisher itself; a full Postgres round-trip is not this codebase's established pattern for outbox rows specifically).

## Open Questions

- **R9 vs. `uq_quorum_tx_fact` (genuine schema conflict, cite `package.md` Q4 adjacently).** T16's own frozen brief already flagged this and deferred it explicitly: "Repeated/incremental CONFIRMATIONS quorum re-evaluation as a tx gains more confirmations — the schema's own `uq_quorum_tx_fact` constraint... allows only one decision ever per `(chain, txHash, factType)`... R9's own 'gains confirmations' repeated framing is task 17's problem, not resolved here." This is not a Phase 1 blocker to resolve, but it is the central design question Phase 3 must address before implementation — not something to guess at here.
- **`package.md` Q4 — Tron confirmation-count semantics.** "Confirm the Tron count basis so the Payment Service displays it consistently" (block depth for Ethereum vs. confirmations toward the solidified block for Tron). Not a blocker for fake-provider tests (per Q4's own text) but affects what value `chain.tx.confirmed`'s `confirmations` field actually means for Tron — Phase 3 should note this as an accepted, disclosed ambiguity rather than silently picking one interpretation.
- **FINALITY as a `FactType`.** `observation.FactType.FINALITY` exists in the enum but has never been quorum-evaluated by any task to date (T16 explicitly deferred it: "a repeated-poll-until-true lifecycle, materially different from this task's one-shot-per-fact model"). Whether R10 requires routing finality through the same `QuorumDecisionService.evaluate` pipeline as the other facts (consistent with L1's "no single provider's answer ever leaves the service as fact") or a different mechanism entirely is unresolved here — Phase 3's central design question, alongside the R9 conflict above.
- **Where does the finality-polling trigger live?** No scheduler/poll loop calls `getFinalityStatus` anywhere today. Whether this extends `Watcher`'s existing per-watch lifecycle or introduces a new component is undetermined — not a blocker to extracting the spec, but the first thing Phase 2's TIB must decide.
- **`fromAddress`/`toAddress`/`addressPoisoningFlag` sourcing at emission time**, as already flagged in Phase 0 — not re-litigated here, carried forward as a live open question for Phase 3.
