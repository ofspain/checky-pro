# crypto · T16 · Phase 2 — Task Implementation Brief

## Task

Implement `Watcher` (per-watch, multi-provider subscription/polling driver feeding the quorum pipeline)
and `WatcherRegistry` (ShedLock-based multi-replica assignment, O5). Add `provider/ProviderSet.java`
(named in `design.md` §6, orphaned — no prior task built it) as this task's own necessary dependency.

## Purpose

Give the fully-built-but-never-called adapter/observation-log/quorum/provider-health pipeline (T06-T10)
its first real caller: watch a `REGISTERED` `Watch`'s address across every configured provider for its
chain, and turn raw per-provider responses into logged observations and quorum decisions.

## Scope

**In:**
- `Watcher` — one instance per actively-watched `Watch`. Subscribes to every configured provider
  (`ChainAdapter.subscribeAddress`) for the watch's chain and address. On any provider's first sighting
  of a new `txHash`, synchronously fans out `getTx(txHash)` to *every* configured provider for that chain
  (not just the one that noticed first) — this is the resolution of Phase 1's correlation-strategy
  question: `subscribeAddress` is discovery-only; `getTx` is the quorum-gathering step, avoiding any
  stateful, timeout-bounded correlation buffer.
- For that first sighting only, decomposes the gathered `TxResult`s into `EXISTENCE` (`Boolean`),
  `AMOUNT` (`BigDecimal`), `TOKEN` (`String`, contract address), and `CONFIRMATIONS` (`Integer`) facts;
  logs every provider's raw response via `ObservationLog.record` *before* calling
  `QuorumDecisionService.evaluate` for each fact (L3/R4); calls `ProviderHealthTracker.recordHealthy`/
  `recordUnhealthy`/`recordDisagreement` per provider based on transport success/failure and
  answer-vs-consensus divergence (R5).
- Advances `ChainCursor.lastBlock` forward as new blocks are observed for a watch (resolves Phase 1's
  cursor-advancement question — see Open Questions for the schema/grant consequence).
- `WatcherRegistry` — a `@Scheduled` reconciliation loop that determines which `REGISTERED` watches this
  replica is responsible for, using ShedLock-leased shards (O5 — see Open Questions/Constraints; this
  specific mechanism requires explicit author approval before being finalized, per the task statement's
  own emphasis), and starts/stops `Watcher` instances accordingly.
- `provider/ProviderSet.java` — a small `Chain -> List<ChainAdapter>` lookup, combining the separately-
  injected `List<EthereumAdapter>`/`List<TronAdapter>` collection beans (T06/T07) into the shape
  `Watcher`/`WatcherRegistry` actually need. No prior task built this despite `design.md` §6 naming it.
- One new grant migration adding `UPDATE` on `chain.chain_cursors` (T15's own `V6` migration explicitly
  anticipated this: "task 16's Watcher will need its own UPDATE grant when it starts advancing the cursor
  for real, not granted preemptively here").
- One new query method on `WatchRepository` (find all `REGISTERED` watches) and one on
  `ChainCursorRepository` (find by `watchId`) — neither exists yet.

**Out:**
- Any `chain.tx.*` event emission (`chain.tx.seen`/`confirmed`/`finalized`) — task 17.
- `ReorgDetector` and cursor walk-*back* — task 18. This task only ever advances the cursor forward.
- Quorum-evaluating the `FINALITY` fact — finality is a repeated-poll-until-true lifecycle
  (`FinalityPolicy`, T14), materially different from the one-shot fan-out this task performs for the
  other four fact types; deferred to whichever future task owns the finality-quorum + event loop.
- Any repeated/incremental quorum re-evaluation of `CONFIRMATIONS` as a transaction gains more
  confirmations over time — see Open Questions; this task quorum-evaluates each fact type, including
  `CONFIRMATIONS`, **exactly once** per transaction (its first-sighting value), matching the schema's own
  `uq_quorum_tx_fact UNIQUE (chain, tx_hash, fact_type)` constraint and `QuorumDecisionService`'s own
  pre-flight rejection of a second evaluation for the same tuple (T09, both frozen, neither modified).
- The sidecar-as-provider test itself — task 24 (this task's design must remain compatible with it, per
  L14/R25, but does not write that test).
- Any change to `contracts/` — none of the named contract files exist in this repository yet.

## Business Rules

- **R1.** A fact is true only at 2-of-3 provider agreement — this task gathers every configured
  provider's answer before calling the already-built quorum evaluator.
- **R4.** Every provider response is logged verbatim before its quorum decision.
- **R5.** An unhealthy/lagging/repeatedly-disagreeing provider is tracked and reported; the system
  continues with the remaining providers if quorum is still achievable.

## Locked Decisions

- **L1.** 2-of-3 quorum, not a tunable — this task must never evaluate quorum on fewer than all
  configured providers' answers.
- **L2.** Disagreement → `HELD`, never auto-resolved — already enforced by `QuorumDecisionService`; this
  task must not bypass it.
- **L3.** Observation log written first, before the quorum decision.
- **L14.** Sidecars are just another provider under quorum — no code path in `Watcher`/`WatcherRegistry`
  may special-case a specific `ChainAdapter` implementation.
- **L15.** Module boundaries — no feature-module-entity imports outside `watch`.

## Dependencies

`ChainAdapter`, `ObservationSink`, `TxResult`, `Subscription`, `Chain` (`adapter/`); `ObservationLog`,
`FactType` (`observation/`); `QuorumDecisionService`, `ProviderAnswer<T>` (`quorum/`);
`ProviderHealthTracker`, `DegradationReason` (`provider/`); `Watch`, `WatchStatus`, `WatchRepository`,
`ChainCursor`, `ChainCursorRepository` (`watch/`, T15); `ProviderProperties`, a new `WatcherProperties`
(`common/config/`); `Clock`; `List<EthereumAdapter>`/`List<TronAdapter>` config beans (T06/T07);
`net.javacrumbs.shedlock` (already a dependency) for `WatcherRegistry`.

## Inputs

- `REGISTERED` rows from `WatchRepository` (new query method).
- Async `TxResult` callbacks via `ObservationSink.onObservation`, per provider, per subscribed address.
- Synchronous `TxResult` responses from `ChainAdapter.getTx(txHash)`, fanned out to every configured
  provider once a candidate transaction is discovered.

## Outputs

- `Observation` rows (via `ObservationLog`), one per provider per fact-bearing response.
- `QuorumDecision` rows (via `QuorumDecisionService`), one per `(chain, txHash, factType)` for
  `EXISTENCE`/`AMOUNT`/`TOKEN`/`CONFIRMATIONS`, evaluated exactly once per transaction.
- `ProviderHealth` state transitions and, on healthy→unhealthy, a `chain.provider.degraded` event (via
  the already-built `ProviderHealthTracker`/`ProviderDegradedPublisher`).
- `ChainCursor.lastBlock` advanced forward (never backward — that is task 18's exclusive job).

## State Changes

- `INSERT` into `observations` (append-only, already-enforced grant).
- `INSERT` into `quorum_decisions` (append-only, already-enforced grant).
- `INSERT`/`UPDATE` on `provider_health` (already-enforced grant, T10).
- `UPDATE` on `chain_cursors.last_block` — **new**, requires the new grant migration in this task's own
  scope (T15's `V6` deliberately did not grant this).
- `SELECT`-only on `watches` (no state change to `Watch` itself).
- ShedLock's own `chain.shedlock` row lifecycle (acquire/release/expire), driven by
  `shedlock-provider-jdbc-template`, not hand-written SQL.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderSet.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/WatcherProperties.java`
- `services/crypto/src/main/resources/db/migration/V7__crypto_app_chain_cursors_update_grant.sql`

## Files to Modify

- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchRepository.java` — add a method to find
  all `REGISTERED` watches.
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursorRepository.java` — add a method
  to find a cursor by `watchId`.
- `services/crypto/src/main/resources/application.properties` — new `WatcherProperties` keys (shard
  count, poll/reconciliation interval, ShedLock lease durations).

## Files NOT to Modify

- `adapter/`, `observation/`, `quorum/`, `provider/ProviderHealthTracker.java` and its collaborators
  (T06-T10) — called, never changed.
- `watch/Watch.java`, `watch/WatchStatus.java`, `watch/WatchService.java`, `watch/WatchController.java`
  (T15) — this task only adds repository query methods, never touches watch lifecycle logic itself.
- `V1__chain_baseline.sql` and every prior grant migration (`V2`-`V6`) — frozen; only a new `V7` is added.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R1, L1).** For each first-sighted transaction, the watcher gathers an answer from every
  configured provider for its chain before calling `QuorumDecisionService.evaluate` for any fact derived
  from it.
- **AC2 (R4, L3).** Every provider's raw response is logged via `ObservationLog.record` before
  `QuorumDecisionService.evaluate` is called for the same `(chain, txHash, factType)`.
- **AC3 (R5).** Transport failure → `recordUnhealthy`; successful response → `recordHealthy`; an answer
  diverging from the quorum-agreed value → `recordDisagreement`.
- **AC4 (scope).** Each of `EXISTENCE`/`AMOUNT`/`TOKEN`/`CONFIRMATIONS` is quorum-evaluated exactly once
  per transaction; a second sighting of an already-decided transaction does not re-invoke
  `QuorumDecisionService.evaluate` for any of its four facts.
- **AC5 (scope).** `ChainCursor.lastBlock` only ever increases for a given watch; nothing in this task
  ever decreases it.
- **AC6 (L14).** No code path distinguishes one `ChainAdapter` implementation from another.
- **AC7 (L15).** `watch/`'s new files import no other feature module's entity.
- **AC8 (O5, requires explicit author approval — not just the normal Phase 4 gate).** `WatcherRegistry`'s
  ShedLock-sharded assignment guarantees no watched address is driven by more than one replica
  simultaneously, and a shard whose lease is lost (crash/restart) is picked up by another replica within
  one reconciliation interval.

## Required Tests

- A test confirming the watcher gathers all configured providers' answers (never a subset) before
  evaluating quorum — AC1.
- A test confirming call order: `ObservationLog.record` before `QuorumDecisionService.evaluate`, for
  every provider, for the same fact — AC2 (mock/spy call-order verification).
- Tests confirming `recordHealthy`/`recordUnhealthy`/`recordDisagreement` are each triggered by the
  correct condition — AC3.
- A test confirming a second observation of an already-decided transaction does not throw (the watcher
  itself must guard against re-invoking `evaluate` — `QuorumDecisionService`'s own `IllegalStateException`
  is a backstop, not this task's primary mechanism) — AC4.
- A test confirming only `REGISTERED` watches (not `UNREGISTERED`/`EXPIRED`) are watched.
- A test confirming `ChainCursor.lastBlock` advances and never regresses.
- A `WatcherRegistry` test confirming two "replicas" (two registry instances sharing one Postgres-backed
  `shedlock` table) never run a `Watcher` for the same shard simultaneously.
- A source-scan module-boundary test for the new `watch/` files (mirrors T10/T11/T14/T15 precedent).

## Constraints

- **Performance:** per-transaction provider fan-out is O(N) `getTx` calls on virtual threads, run
  concurrently, not sequentially — the whole point of T01's global virtual-thread enablement, whose own
  comment names this task as the intended beneficiary.
- **Security:** no new endpoint, no new secret; relies on already-provisioned provider credentials.
- **Thread-safety:** `Watcher`/`WatcherRegistry` are the service's first genuinely concurrent, long-running
  components — the in-memory "already-processed transaction" guard (a `ConcurrentHashMap`-backed set,
  process-local, not persisted, mirroring `ProviderHealthTracker`'s own precedent for a similar
  non-persisted concern) must be safe under concurrent virtual-thread access.
- **Transaction:** `ObservationLog.record`/`QuorumDecisionService.evaluate`/`ProviderHealthTracker.*` are
  each already individually transactional (T08/T09/T10); this task does not wrap them in a further outer
  transaction (doing so would hold a connection open across the S3 call inside `ObservationLog`, the
  exact anti-pattern T08's own Javadoc already rejected).
- **Module boundaries:** `watch/`'s new files depend on `observation`, `quorum`, `provider`, `adapter`,
  and `common` — no entity import from any of them, only stateless services/records.
- **Null handling:** a provider transport failure during fan-out must not abort the other providers'
  calls — each `getTx` call is isolated, failures recorded via `ProviderHealthTracker.recordUnhealthy`
  and excluded from that fact's answer list (quorum then evaluates against however many providers
  actually answered — if that drops below `quorumThreshold`, `QuorumEvaluator`'s own existing logic
  already handles an insufficient-answer-count list, per T09).

## Open Questions

Not blockers for this task's own scope, but disclosed per this pipeline's own "if something looks wrong,
log it, don't deviate silently" guardrail:

- **R9's literal text ("WHEN a `SEEN` transaction gains confirmations under quorum... emit
  `chain.tx.confirmed`") implies a *repeated*, incremental confirmations fact, but the frozen,
  already-tested `quorum_decisions` schema (`uq_quorum_tx_fact UNIQUE (chain, tx_hash, fact_type)`) and
  `QuorumDecisionService`'s own pre-flight rejection allow only ONE quorum decision ever, per
  `(chain, txHash, factType)` tuple — including `CONFIRMATIONS`.** This task resolves the tension for its
  own narrow scope by quorum-evaluating `CONFIRMATIONS` exactly once (the first-sighting value), like the
  other three one-shot facts. Whether R9's own "gains confirmations" repeated-event semantics require a
  schema or mechanism change beyond what T02/T09 already shipped is task 17's own problem to resolve, not
  this task's — flagged here rather than silently designed around.
- **O5's ShedLock-sharded design (Scope/AC8) is a Phase 2 proposal, not yet locked**, and per the task
  statement's own explicit wording requires the human's direct approval before being finalized — beyond
  the normal Phase 4 gate every other task in this pipeline has used so far.
