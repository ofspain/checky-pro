STATUS: FROZEN

# crypto · T16 · Phase 4 — Frozen Task Brief

**Human Approval gate.** Approved 2026-09-07, in two rounds: the initial 10-finding Phase 3 packet, and
a follow-up correction discovered while tracing Finding 1's implementation consequences (below). Both
approved as recommended.

## Design-challenge resolution log

| # | Finding | Severity | Disposition | Change made |
|---|---|---|---|---|
| 1 | `ObservationSink.onObservation(TxResult)` carries neither provider identity nor raw JSON; `getTx`'s VERBATIM-frozen return shape (`TxResult`) can't carry raw JSON either | High | **ACCEPTED — architecture pivot** | Abandoned the original `getTx`-fan-out design entirely (it structurally cannot satisfy L3/R4, since the only channel that *can* be extended, `ObservationSink`, is not part of the fan-out path). Replaced with a stateful, per-`(chain, txHash)` correlation buffer fed purely by N independent `subscribeAddress` subscriptions (Phase 1's originally-disfavored option). `ObservationSink` (not VERBATIM — confirmed absent from `design.md` §4c's VERBATIM list) is extended to `onObservation(String provider, TxResult result, String rawResponseJson)`. |
| — | **Follow-up correction (discovered while designing Finding 1's resolution, not a numbered Phase 3 finding):** `QuorumEvaluator.evaluate` hard-requires exactly 3 non-null answers (verified by reading its source) and its own Javadoc explicitly defers the "fewer than 3 reachable" scenario to whichever task actually faces it — which is this task, not T10 as a loose reading suggested. This also means **Kimi's own Finding 9 scenario is unachievable as stated** (2 real answers cannot produce `AGREED`; `evaluate` would throw). | — | **CORRECTED, approved separately** | The watcher never calls `evaluate` until exactly 3 real per-provider answers exist for a fact. A provider that hasn't answered within the correlation window is marked unhealthy/lagging (`ProviderHealthTracker.recordUnhealthy(..., DegradationReason.LAGGING)`) but that fact stays undecided — not evaluated with 2, and no fabricated third answer (rejected as unsafe: a `Boolean` sentinel for `EXISTENCE` would coincidentally match a real answer roughly half the time, silently corrupting quorum — an asymmetry that makes the workaround unsafe even where it might work for other types). |
| 2 | `ProviderSet` in `provider/` violates `ProviderModuleBoundaryTest`'s unconditional ban on `adapter` imports there | High | **ACCEPTED — verified directly against the test** | Relocated to `adapter/ProviderSet.java` (it is fundamentally a `Chain -> List<ChainAdapter>` grouping — a natural fit there; `provider/` stays purely about health tracking, unchanged in shape). |
| 3 | No grant anywhere for `crypto_app` on `chain.shedlock` | High | **ACCEPTED — verified directly (grepped every migration)** | Added to the new grant migration alongside the `chain_cursors` `UPDATE` grant. |
| 4 | `AMOUNT`/`TOKEN`/`CONFIRMATIONS` must exclude providers reporting `exists=false` | Medium | **ACCEPTED** | Only a provider whose `TxResult.exists()==true` contributes an answer to those three facts; `EXISTENCE` always uses every provider's answer regardless of value. |
| 5 | Watcher must treat `QuorumDecisionService`'s duplicate-decision `IllegalStateException` as a benign no-op | Medium | **ACCEPTED** | Caught and logged, not propagated — a backstop for when the in-memory "already evaluated" guard is lost on restart. |
| 6 | No ordering/transaction strategy for `ChainCursor.lastBlock` advancement | Medium | **ACCEPTED** | Cursor advances only *after* observation/quorum/health writes complete, as a separate, best-effort, forward-only write — lag is acceptable, leading ahead is not. |
| 7 | No watcher-lag/provider-disagreement metrics despite `agents.md`'s explicit "paged metrics" requirement | Medium | **ACCEPTED, reinterpreted** | `ChainAdapter` has no "current block" method (the same VERBATIM-freeze constraint behind T14's own `-1` sentinel decision), so a literal block-depth lag metric isn't obtainable. Reinterpreted as a wall-clock staleness gauge (`crypto.watcher.lag.seconds`, tagged `chain`/`address` — time since the last observation was processed) plus a disagreement counter (`crypto.provider.disagreements`, tagged `chain`/`provider`, incremented alongside `recordDisagreement`). |
| 8 | Shard-assignment algorithm for `WatcherRegistry` unspecified | Medium | **ACCEPTED** | `Math.floorMod(watchId.hashCode(), shardCount)` — deterministic, every replica computes the same mapping independently. |
| 9 | No required test for partial fan-out still reaching quorum | Low | **SUPERSEDED by the follow-up correction above** | The originally-suggested scenario is impossible given `QuorumEvaluator`'s exactly-3 requirement. Replaced with: a lagging provider (2 of 3 answer, 1 times out) leaves the fact undecided and marks the non-responder unhealthy/lagging — it does **not** produce `AGREED`. |
| 10 | No lifecycle rule for in-flight callbacks after `Subscription.cancel` | Low | **ACCEPTED** | `Watcher` checks a volatile `running` flag at the top of every sink callback; once `false`, the callback is a no-op (no logging, no correlation update, no evaluation). |

## Frozen brief

### Task

Implement `Watcher` (per-watch, multi-provider correlation-buffer driver feeding the quorum pipeline)
and `WatcherRegistry` (ShedLock-sharded multi-replica assignment, O5). Add `adapter/ProviderSet.java`.
Extend `ObservationSink` to carry provider identity and raw JSON, with the consequent updates to
`EthereumAdapter`/`TronAdapter`/`FakeChainAdapter` (and their existing tests) that requires.

### Purpose

Give the fully-built-but-never-called adapter/observation-log/quorum/provider-health pipeline (T06-T10)
its first real caller.

### Scope

**In:**
- `ObservationSink` extended to `onObservation(String provider, TxResult result, String rawResponseJson)`.
  Not VERBATIM (confirmed absent from `design.md` §4c). `rawResponseJson` is each adapter's own
  Jackson-serialized capture of what it parsed from the provider — disclosed as *not* necessarily
  byte-identical to the original wire response (web3j/trident's own typed parsing already discards raw
  bytes; true wire-level capture would require redoing T06/T07's transport layer, out of this task's
  proportionate scope). Phase 5/6 must confirm the exact fields captured per adapter.
- `EthereumAdapter`/`TronAdapter` updated at every `sink.onObservation(...)` call site to supply the
  provider name (already held) and the new JSON parameter.
- `FakeChainAdapter` (test fixture) updated to match; `FakeChainAdapterTest` and any other existing test
  using `ObservationSink`-as-lambda/method-reference (`received::add`-style) updated to the new 3-arg
  shape — a disclosed, necessary ripple fix, not scope creep.
- `adapter/ProviderSet.java` — `Chain -> List<ChainAdapter>`, combining the existing
  `List<EthereumAdapter>`/`List<TronAdapter>` config beans.
- `Watcher` — one instance per actively-watched `Watch`. Subscribes to every `ProviderSet`-supplied
  adapter for the watch's chain and address. Each callback: (1) logs the observation via
  `ObservationLog.record` — one row per applicable fact type (`EXISTENCE` always; `AMOUNT`/`TOKEN`/
  `CONFIRMATIONS` only if that provider's `TxResult.exists()==true`), all sharing the same
  `rawResponseJson`; (2) feeds `(provider, TxResult)` into a per-`(chain, txHash)` correlation entry
  (`ConcurrentHashMap`-backed, process-local, not persisted — mirrors `ProviderHealthTracker`'s own
  precedent for a similar non-persisted concern); (3) once exactly 3 real answers exist for a fact,
  calls `QuorumDecisionService.evaluate` exactly once for it, guarded by an in-memory "already
  evaluated" flag plus a catch of `QuorumDecisionService`'s duplicate-decision `IllegalStateException`
  as a benign no-op backstop; (4) calls `recordHealthy` for each provider that answers within the
  correlation window, `recordUnhealthy(..., LAGGING)` for one that doesn't, and `recordDisagreement` for
  one whose answer differs from the 2-of-3 agreed value once a fact is `AGREED`.
- Advances `ChainCursor.lastBlock` forward-only, as a separate write *after* the observation/quorum/
  health writes for a poll tick complete.
- `WatcherRegistry` — `@Scheduled` reconciliation loop; a watch's shard is
  `Math.floorMod(watch.watchId().hashCode(), watcherProperties.shardCount())`; ShedLock
  (`@SchedulerLock(name = "watcher-shard-" + shardIndex)`) guards which replica runs the `Watcher`
  instances for a given shard, starting/stopping them as shard ownership changes.
- Two new Micrometer metrics: `crypto.watcher.lag.seconds` (gauge, tagged `chain`/`address`) and
  `crypto.provider.disagreements` (counter, tagged `chain`/`provider`) — wired in `Watcher` and
  `ProviderHealthTracker.recordDisagreement` respectively.
- One new grant migration (`chain_cursors` `UPDATE`; `chain.shedlock` full DML for `crypto_app`).
- New query methods: `WatchRepository.findByStatus(WatchStatus)` (or equivalent); `ChainCursorRepository
  .findByWatchId(UUID)`.
- New `WatcherProperties` (`@ConfigurationProperties`): `shardCount`, `reconciliationIntervalMs`,
  `correlationWindowMs`, ShedLock `lockAtLeastFor`/`lockAtMostFor` durations.

**Out:**
- Any `chain.tx.*` event emission — task 17.
- `ReorgDetector`/cursor walk-*back* — task 18. This task only ever advances the cursor forward.
- Quorum-evaluating `FINALITY` — a repeated-poll-until-true lifecycle, materially different from this
  task's one-shot-per-fact model; deferred.
- Repeated/incremental `CONFIRMATIONS` quorum re-evaluation as a tx gains more confirmations — the
  schema's own `uq_quorum_tx_fact` constraint (T02/T09, frozen) allows only one decision ever per
  `(chain, txHash, factType)`; `CONFIRMATIONS` is evaluated once, at whatever moment its 3rd real answer
  arrives, like the other three one-shot facts. R9's own "gains confirmations" repeated-event framing
  is task 17's problem, not resolved here.
- Evaluating any fact with fewer than exactly 3 real per-provider answers, under any circumstance — see
  the follow-up correction above.
- The sidecar-as-provider test itself — task 24.
- Any change to `contracts/` — none of the named contract files exist in this repository yet.

### Business Rules

R1, R4, R5 (see Phase 1 extraction for full text) — quorum fan-out, verbatim-first logging, provider
health tracking, respectively.

### Locked Decisions

L1 (2-of-3, not tunable — enforced by the exactly-3-answers design above), L2 (disagreement → `HELD`,
untouched), L3 (log before decide), L14 (no `ChainAdapter`-implementation-specific code path), L15
(module boundaries).

### Dependencies

`Chain`, `ChainAdapter`, `ObservationSink` (extended), `TxResult`, `Subscription` (`adapter/`);
`ProviderSet` (`adapter/`, new); `ObservationLog`, `FactType` (`observation/`); `QuorumDecisionService`,
`ProviderAnswer<T>` (`quorum/`); `ProviderHealthTracker`, `DegradationReason` (`provider/`); `Watch`,
`WatchStatus`, `WatchRepository`, `ChainCursor`, `ChainCursorRepository` (`watch/`); `ProviderProperties`,
new `WatcherProperties` (`common/config/`); `Clock`; `MeterRegistry` (Micrometer, already a dependency
via `spring-boot-starter-actuator`/`micrometer-registry-prometheus`); `List<EthereumAdapter>`/
`List<TronAdapter>` beans; `net.javacrumbs.shedlock`.

### Inputs

- `REGISTERED` rows from `WatchRepository`.
- `(provider, TxResult, rawResponseJson)` callbacks via the extended `ObservationSink`, one per provider,
  per subscribed address.

### Outputs

- `Observation` rows (one per applicable fact type per provider response).
- `QuorumDecision` rows, exactly one per `(chain, txHash, factType)` for `EXISTENCE`/`AMOUNT`/`TOKEN`/
  `CONFIRMATIONS`, only ever created once exactly 3 real answers exist.
- `ProviderHealth` transitions and `chain.provider.degraded` events (via the existing, unmodified-in-
  logic `ProviderHealthTracker`/`ProviderDegradedPublisher`), now with a disagreement counter alongside.
- `ChainCursor.lastBlock` advanced forward.
- Two new Micrometer metrics.

### State Changes

`INSERT` into `observations`/`quorum_decisions` (already-granted); `INSERT`/`UPDATE` on
`provider_health` (already-granted); **new** `UPDATE` on `chain_cursors`; **new** full DML on
`chain.shedlock` via the ShedLock library (not hand-written SQL); no change to `watches` itself
(`SELECT`-only).

### Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/adapter/ProviderSet.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/WatcherProperties.java`
- `services/crypto/src/main/resources/db/migration/V7__crypto_app_watcher_grants.sql`

### Files to Modify

- `services/crypto/src/main/java/com/themistra/crypto/adapter/ObservationSink.java` — signature change.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java`,
  `adapter/tron/TronAdapter.java` — every `sink.onObservation(...)` call site updated.
- `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapter.java` and
  `FakeChainAdapterTest.java` — updated for the new signature (ripple fix; other tests using
  `FakeChainAdapter`'s subscription mechanism must be checked and fixed too if affected).
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealthTracker.java` — add the
  disagreement-counter metric increment inside `recordDisagreement` (small, additive; no logic change).
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchRepository.java`,
  `watch/ChainCursorRepository.java` — new query methods.
- `services/crypto/src/main/resources/application.properties` — new `WatcherProperties` keys.

### Files NOT to Modify

- `ChainAdapter.java` itself (VERBATIM, frozen — no method added or changed).
- `observation/ObservationLog.java`, `quorum/QuorumDecisionService.java`, `quorum/QuorumEvaluator.java`
  — called, never changed (the exactly-3 constraint is respected, not worked around).
- `watch/Watch.java`, `WatchStatus.java`, `WatchService.java`, `WatchController.java`.
- `V1`-`V6` migrations — frozen; only a new `V7` is added.
- Any file under `spec/`.

### Acceptance Criteria

- **AC1 (R1, L1, corrected).** A fact is quorum-evaluated only when exactly 3 real per-provider answers
  exist for it; never fewer, never a fabricated stand-in.
- **AC2 (R4, L3).** Every provider's raw response is logged before any quorum evaluation it contributes
  to.
- **AC3 (R5).** Provider that answers within the window → `recordHealthy`; provider that doesn't →
  `recordUnhealthy(..., LAGGING)`; provider whose answer diverges from an `AGREED` fact →
  `recordDisagreement` (and the new counter increments).
- **AC4 (scope, exists=false exclusion).** `AMOUNT`/`TOKEN`/`CONFIRMATIONS` only ever include a
  provider's answer when that provider's `TxResult.exists()==true`.
- **AC5 (scope).** Each fact type is quorum-evaluated at most once per transaction; a duplicate attempt
  (in-memory guard lost, e.g. after restart) is caught and treated as a no-op, never propagated.
- **AC6 (scope).** `ChainCursor.lastBlock` only ever increases, and only after the corresponding
  observation/quorum/health writes for that tick have completed.
- **AC7 (L14).** No code path distinguishes one `ChainAdapter` implementation from another.
- **AC8 (L15).** `watch/`'s and `adapter/ProviderSet.java`'s new code imports no other feature module's
  entity.
- **AC9 (O5, requires explicit author approval — confirmed, not just the normal Phase 4 gate).**
  `WatcherRegistry`'s ShedLock-sharded assignment (`Math.floorMod` shard function) guarantees no watch is
  driven by more than one replica simultaneously, and a lost shard lease is picked up by another replica
  within one reconciliation interval.
- **AC10 (agents.md).** `crypto.watcher.lag.seconds` and `crypto.provider.disagreements` are emitted.

### Required Tests

- A test confirming quorum evaluation never fires with fewer than 3 real answers (AC1) — including the
  corrected lagging-provider scenario (2 answer, 1 times out → fact stays undecided, no `AGREED`, no
  exception).
- A test confirming observation-log-before-quorum-evaluation ordering (call-order verification) — AC2.
- Tests for `recordHealthy`/`recordUnhealthy(LAGGING)`/`recordDisagreement`, each independently — AC3.
- A test confirming a provider reporting `exists=false` contributes to `EXISTENCE` only, never to the
  other three facts — AC4.
- A test confirming a duplicate evaluation attempt (post-restart guard loss, simulated) is swallowed, not
  propagated — AC5.
- A test confirming `ChainCursor.lastBlock` only increases and only after the preceding writes succeed —
  AC6.
- A test confirming only `REGISTERED` watches are watched.
- A `WatcherRegistry` test confirming two registry instances sharing one Postgres-backed `shedlock` table
  never claim the same shard simultaneously — AC9.
- A test confirming a callback arriving after `Watcher.stop()` is a complete no-op (no log, no
  correlation update, no evaluation) — Finding 10.
- Metrics tests confirming both new meters are emitted under the expected conditions — AC10.
- A source-scan module-boundary test for the new `watch/` files and `adapter/ProviderSet.java` — AC8.
- Updated `FakeChainAdapterTest` (and any other now-broken test) confirmed green after the
  `ObservationSink` signature ripple fix.

### Constraints

- **Performance:** correlation-window sweeps and per-provider subscriptions run on virtual threads (T01's
  own stated beneficiary).
- **Security:** no new endpoint, no new secret.
- **Thread-safety:** the correlation buffer and the "already evaluated" guard must be safe under
  concurrent virtual-thread callback delivery from multiple provider subscriptions.
- **Transaction:** `ObservationLog`/`QuorumDecisionService`/`ProviderHealthTracker` calls are not wrapped
  in an outer transaction (each is already individually transactional); the cursor update is a separate,
  later, best-effort write, never inside the same transaction as those.
- **Module boundaries:** no feature-module entity import outside `watch`/`adapter`.
- **Null handling:** a provider transport failure inside its own adapter's polling loop is already
  swallowed-and-retried internally (T06/T07's own established behavior) and never surfaces to the sink —
  the watcher's only signal for "provider didn't answer" is the correlation-window timeout, not a thrown
  exception.

### Open Questions

No blockers. All findings above resolved and approved, including the follow-up correction to Finding 9.
