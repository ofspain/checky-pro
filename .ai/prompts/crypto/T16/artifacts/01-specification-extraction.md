# crypto · T16 · Phase 1 — Specification Extraction

## Business Rules

- **R1.** A verification fact (existence, amount, token, confirmations, finality) must be fetched from N
  independent providers and treated as true only at 2-of-3 agreement. (Quorum logic itself is already
  built and tested, T09; this task is the first real caller that actually gathers N providers' answers
  and hands them to it.)
- **R4.** Every provider response for a fact must be persisted verbatim to the observation log *before*
  the quorum decision for that fact is finalized. (`ObservationLog` itself already enforces this
  internally, T08; this task must call it in the correct sequence — log every provider's answer, then
  evaluate quorum — for the guarantee to hold end-to-end, not just within one class.)
- **R5.** An unhealthy, lagging, or repeatedly-disagreeing provider must be tracked and, on a
  healthy→unhealthy transition, trigger `chain.provider.degraded`, while the system continues with the
  remaining providers if quorum is still achievable. (`ProviderHealthTracker` already implements the
  tracking/alerting itself, T10; this task is the first caller that actually detects and reports these
  three conditions from real watcher behavior — transport failure, missed poll, and answer divergence
  from the agreed value, respectively.)

R2/R3 (disagreement → `HELD`, never auto-resolved) are fully implemented and tested at the
`QuorumEvaluator`/`QuorumDecisionService` level (T09) with their own named tests; this task's only
obligation toward them is to call `QuorumDecisionService.evaluate` correctly and not special-case or
bypass a `HELD` outcome. R25 (sidecar output is just another provider answer) is a design *constraint*
on this task (do not special-case any `ChainAdapter` implementation) but its own named test
(`shouldTreatSidecarOutputAsJustAnotherProviderAnswer`) is explicitly task 24's own scope, not this
task's.

## Locked Decisions

- **L1.** 2-of-3 quorum, no single-provider truth — not a tunable; this task must gather ALL configured
  providers' answers, never fewer, before calling quorum evaluation.
- **L2.** Disagreement → `HELD`, ops-alerted, never auto-resolved — already enforced by
  `QuorumDecisionService`; this task must not work around it (e.g. by suppressing a disagreeing
  provider's answer to force `AGREED`).
- **L3.** Observation log is verbatim and written first — this task must call `ObservationLog.record` for
  every provider's raw response before calling `QuorumDecisionService.evaluate` for the same fact.
- **L14.** Sidecars are translation-only; the Java core treats sidecar output as one more provider answer
  under quorum — this task's design must work identically for any `ChainAdapter` implementation, with no
  code path that distinguishes a real adapter from a sidecar-backed one.
- **L15.** Module boundaries — package-by-feature, no feature module imports another feature module's
  entity; shared plumbing only via `common`.

## Files involved

**Existing, to read/extend (no modification unless explicitly named):**
- `adapter/ChainAdapter.java`, `adapter/ObservationSink.java`, `adapter/model/{TxResult,Subscription}.java`
  — the interfaces this task drives; not modified.
- `adapter/eth/EthereumAdapterConfig.java` (confirms `@Bean List<EthereumAdapter> ethereumAdapters(...)`
  exists), `adapter/tron/TronAdapterConfig.java` (presumed to mirror this shape for Tron — to be
  confirmed by direct read, not assumed, before Phase 2 relies on it) — read-only pattern/dependency
  reference.
- `observation/ObservationLog.java`, `observation/FactType.java` — called, not modified.
- `quorum/QuorumDecisionService.java`, `quorum/ProviderAnswer.java`, `quorum/QuorumOutcome.java` —
  called, not modified.
- `provider/ProviderHealthTracker.java`, `provider/DegradationReason.java` — called, not modified.
- `watch/WatchRepository.java`, `watch/ChainCursor.java`, `watch/ChainCursorRepository.java` — read from
  (and, if cursor advancement is confirmed in scope at Phase 2, written to); `WatchRepository` needs a
  new query method this task adds (no method to list `REGISTERED` watches exists yet).
- `common/config/ProviderProperties.java` — read for provider/threshold config; not modified.
- `net.javacrumbs.shedlock` (dependency, already present) and `chain.shedlock` table (already present,
  `V1__chain_baseline.sql:126-131`) — used by `WatcherRegistry`, not modified.

**New, per `design.md` §6 (`watch/` package) — this task's own named files:**
- `watch/Watcher.java` — subscriptions/polling per provider on virtual threads, feeding observations
  into the quorum pipeline (O2/Q5).
- `watch/WatcherRegistry.java` — multi-replica assignment (O5).
- `provider/ProviderSet.java` — named in `design.md` §6 ("N adapters per chain — O1") but not yet built
  by any prior task; whether this task must build it as a prerequisite, or whether `Watcher` reads
  `ProviderProperties`/the adapter config beans directly, is a Phase 2 design decision.

**Explicitly NOT in this task's own scope** (per the task statement's own literal wording and the
tasks.md ordering):
- `events/*` (chain.tx.seen/confirmed/finalized emission) — task 17.
- `reorg/ReorgDetector.java` (cursor walk-*back*, `chain.tx.reorged`) — task 18.
- The sidecar-as-provider test itself — task 24 (though this task's design must remain compatible with
  it, per L14/R25 above).
- Any change to `contracts/` — none of the named contract files exist in this repository yet (confirmed
  absent, same finding as T14/T15).

## Dependencies

- `Chain`, `ChainAdapter`, `ObservationSink`, `TxResult`, `Subscription` (`adapter/`).
- `ObservationLog`, `FactType` (`observation/`).
- `QuorumDecisionService`, `ProviderAnswer<T>` (`quorum/`).
- `ProviderHealthTracker`, `DegradationReason` (`provider/`).
- `Watch`, `WatchStatus`, `WatchRepository`, `ChainCursor`, `ChainCursorRepository` (`watch/`, T15).
- `ProviderProperties` (`common/config/`), `Clock` (`common/`).
- `net.javacrumbs.shedlock:shedlock-spring`/`shedlock-provider-jdbc-template` (already a dependency) for
  `WatcherRegistry`.
- No contract dependency — none of the named contract files exist yet.

## Acceptance Criteria

- **AC1 (R1, L1).** For a candidate transaction, the watcher gathers an answer from every configured
  provider for its chain (never fewer) before calling `QuorumDecisionService.evaluate` for any fact type
  derived from it.
- **AC2 (R4, L3).** Every provider's raw response is passed to `ObservationLog.record` before
  `QuorumDecisionService.evaluate` is called for the same `(chain, txHash, factType)`.
- **AC3 (R5).** A provider that fails to respond (transport exception/timeout) triggers
  `ProviderHealthTracker.recordUnhealthy`; a provider that responds successfully triggers
  `recordHealthy`; a provider whose answer for a fact differs from the quorum-agreed value triggers
  `recordDisagreement`.
- **AC4 (L14, R25 design constraint).** No code path in `Watcher`/`WatcherRegistry` distinguishes one
  `ChainAdapter` implementation from another — a scripted `FakeChainAdapter` and any future
  sidecar-backed adapter are driven identically.
- **AC5 (L15).** `watch/`'s `Watcher`/`WatcherRegistry` import no other feature module's *entity* (only
  stateless services/records from `observation`, `quorum`, `provider`, and `adapter`).
- **AC6 (O2/Q5, O5 — Phase 2 design decisions, not yet locked).** The watcher's transport/concurrency
  model and the registry's multi-replica assignment mechanism are proposed at Phase 2, challenged at
  Phase 3, and **O5 specifically requires explicit author approval before being finalized** — distinct
  from every other OPEN decision resolved so far in this pipeline (task statement's own emphasis).

## Tests required

No new named test belongs to this task per `package.md` §8 (`shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree`
→ R1, `shouldLogEveryProviderResponseVerbatimToObservationLog` → R4, and
`shouldEmitProviderDegradedWhenAProviderIsUnhealthy` → R5 are all already implemented and owned by
T09/T08/T10 respectively; `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` → R25 is task 24's own).
This task's required tests instead prove its own new integration/wiring logic:

- A test confirming the watcher gathers answers from every configured provider (not a subset) before
  evaluating quorum for a fact — AC1.
- A test confirming `ObservationLog.record` is called, for every provider, before
  `QuorumDecisionService.evaluate` is called for the same fact — AC2 (an ordering assertion, e.g. via a
  spy/mock call-order verification).
- A test confirming a provider transport failure triggers `recordUnhealthy`, a successful response
  triggers `recordHealthy`, and a divergent answer triggers `recordDisagreement` — AC3.
- A test confirming the watcher only watches currently-`REGISTERED` watches, not
  `UNREGISTERED`/`EXPIRED` ones.
- A module-boundary test for `watch/` (mirrors T10/T11/T14/T15's own source-scan precedent) — AC5.
- Whatever tests Phase 2's O2/O5 design decisions themselves require — cannot be enumerated until those
  decisions are made.

## Open Questions

No `package.md` §11 Q-item is a hard blocker for this task beyond Q5 itself (already named inline by the
task statement as feeding O2 — an implementer-proposed-then-challenged decision, not a spec-author
blocker). Three genuine implementer-facing gaps remain, all Phase 2 design territory:

- **How the watcher correlates asynchronous, independently-timed per-provider observations
  (`ObservationSink.onObservation`, one callback per provider) into the synchronous, all-at-once
  `List<ProviderAnswer<T>>` shape `QuorumDecisionService.evaluate` requires is not specified anywhere.**
  Two plausible designs exist given the interfaces already provided (Phase 0 finding): (a) use
  `subscribeAddress` for discovery only, then synchronously call `getTx(txHash)` against every
  configured provider once any one of them notices a candidate transaction; or (b) a stateful,
  timeout-bounded correlation buffer keyed by `(chain, txHash)`. This is squarely part of O2's own scope
  ("Watcher transport & concurrency"), not a separate blocker.
- **Whether `ChainCursor` advancement (`lastBlock`/`lastFinalizedBlock`) is this task's own
  responsibility.** T15 left it as a write-once `-1` placeholder; T18's reorg walk-*back* presupposes
  something walks it forward first, and no other task claims that job. Phase 2 must propose an answer.
- **Whether a `ProviderSet` abstraction (named in `design.md` §6, never built) is a prerequisite this
  task must add, or whether `Watcher` reads provider config/adapter beans directly.** Phase 2 must decide
  and justify either way.
