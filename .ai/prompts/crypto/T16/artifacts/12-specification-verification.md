# crypto · T16 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R1 / L1 — 2-of-3 quorum, exactly 3 real answers, never fewer, never fabricated | Yes | `Watcher.evaluateFact` size check `watch/Watcher.java:215`; guard `watch/Watcher.java:178` | `WatcherTest.evaluatesEachFactExactlyOnceOnceAllThreeProvidersAgree`, `.doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering`, `.laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers` | No | None — corrected from the original design (Phase 4 follow-up, human-approved) to respect `QuorumEvaluator`'s hard exactly-3 requirement, verified by reading its source. |
| R4 / L3 — verbatim log before quorum decision | Yes | `Watcher.logObservation` `watch/Watcher.java:163-169`, called before `evaluateFact` in `recordAnswerAndMaybeEvaluate` `watch/Watcher.java:172-197` | `WatcherTest.logsEveryProviderObservationBeforeEvaluatingQuorumForTheSameFact` (Mockito `InOrder`) | No | None. |
| R5 — health signal (healthy/lagging/disagreement) + degraded event on divergence | Yes | `recordAnswerAndMaybeEvaluate` `watch/Watcher.java:176` (healthy); `sweepStaleCorrelations` `watch/Watcher.java:282-296` (lagging); `recordDisagreementsIfAny` `watch/Watcher.java:244-263` (disagreement) — `chain.provider.degraded` emission itself is T10's existing, unmodified `ProviderDegradedPublisher`, invoked transitively via `ProviderHealthTracker` | `WatcherTest.recordsHealthyForEveryProviderThatAnswers`, `.recordsDisagreementForTheMinorityProviderInATwoOneSplit`, `.doesNotRecordDisagreementForAGenuineThreeWaySplit`, `.recordsDisagreementAtMostOncePerProviderPerTransactionEvenWhenMultipleFactsDiverge`, `.marksALaggingProviderAtMostOncePerCorrelationAcrossRepeatedSweeps` | No | None. |
| AC4 (scope) — `exists=false` excluded from `AMOUNT`/`TOKEN`/`CONFIRMATIONS`, included in `EXISTENCE` | Yes | `logObservation` `watch/Watcher.java:165` (`if (result.exists())`); `evaluateFact`'s `requireExists` filter `watch/Watcher.java:209` | `WatcherTest.excludesProvidersReportingExistsFalseFromAmountTokenAndConfirmationsButNotExistence`, `.doesNotLogAmountTokenOrConfirmationsForAProviderReportingExistsFalse` | No | None. |
| AC5 (scope) — duplicate-decision exception swallowed | Yes | in-memory guard `evaluatedFacts.add(...)` `watch/Watcher.java:202`; catch `watch/Watcher.java:228` | `WatcherTest.swallowsADuplicateDecisionExceptionRatherThanPropagatingIt` | No | None. |
| AC6 (scope) — cursor forward-only, advances only after preceding writes | Yes | `advanceCursorIfNeeded` called last in `handleObservation` `watch/Watcher.java:145`; guard `ChainCursor.advanceTo` `watch/ChainCursor.java:80-86` | `WatcherTest.advancesTheCursorAfterObservationAndQuorumWritesComplete`; `ChainCursorTest.advanceToMovesLastBlockForwardWhenGivenAHigherBlockNumber`, `.advanceToIsANoOpWhenGivenABlockNumberAtOrBelowTheCurrentLastBlock` | No | None. |
| AC7 / L14 — no code path distinguishes one `ChainAdapter` implementation from another (sidecars are "one more provider answer") | Yes | `Watcher`/`WatcherRegistry` operate solely on `ChainAdapter`/`ProviderSet.NamedAdapter`; grepped both files for `EthereumAdapter`/`TronAdapter`/`instanceof` — the only two hits are inside a comment (`watch/Watcher.java:149-150`), not code | `ProviderSetTest` (both adapter types flow through the same abstract path); no dedicated negative test (an absence is not independently testable beyond source inspection) | No | None. |
| AC8 / L15 — module boundaries: no feature-module entity import outside `watch`/`adapter` | Yes | `adapter/ProviderSet.java` (relocated here at Phase 4 specifically to satisfy `ProviderModuleBoundaryTest`'s ban on `adapter` imports inside `provider/`) | `WatchModuleBoundaryTest` (source-scan, updated this task to an explicit `ALLOWED_IMPORTS_BY_PREFIX` map covering `watch/`'s new files); `ProviderModuleBoundaryTest` (existing, unmodified) | No | No dedicated boundary test scans `adapter/`'s own outbound imports (none of `adapter/`'s existing files needed one); disclosed at Phase 10/11 as out of this task's proportionate scope — `ProviderSet`'s imports are visibly in-module by inspection. |
| AC9 / O5 — ShedLock-sharded exclusivity, lost lease picked up within one reconciliation interval | Yes | `WatcherRegistry.ownsShard` `watch/WatcherRegistry.java:124-137` (acquire-or-renew, stop-on-failure); `shardOf` `watch/WatcherRegistry.java:188-190`; `shutdown` `watch/WatcherRegistry.java:90-98` | `WatcherRegistryTest.onlyOneOfTwoReplicasStartsAWatcherForTheSameRegisteredWatch`, `.repeatedReconciliationOnTheOwningReplicaDoesNotStartADuplicateWatcher`, `.shutdownReleasesTheShardLockSoAnotherReplicaCanImmediatelyTakeOver`, `.aReplicaThatLosesItsShardLockStopsItsRunningWatchers`, `.onlyWatchesInAnOwnedShardAreEverStarted` — real Postgres-backed `shedlock` table for the first three, mocked `LockProvider` for the renewal/multi-shard cases | No | Human-approved at Phase 4 (O5's explicit author-approval gate, beyond the normal Phase 4 gate). |
| AC10 / `agents.md` "paged metrics" (`agents.md:54`) — watcher lag + provider-disagreement rate | Yes | `crypto.watcher.lag.seconds` gauge `watch/Watcher.java:103-107`; `crypto.provider.disagreements` counter `provider/ProviderHealthTracker.java:109-115` | `WatcherTest.removesItsOwnLagGaugeOnStop`, `.theLagGaugeIsTaggedWithWatchIdSoTwoWatchesSharingAnAddressDoNotCollide`; disagreement counter's own increment covered by `Watcher`'s `recordDisagreement`-calling tests plus `ProviderHealthTrackerTest`'s pre-existing metrics assertions (T10) | No | Reinterpreted at Phase 4 (Finding 7) from a literal block-depth lag to a wall-clock staleness gauge, since `ChainAdapter` exposes no "current block" method (same VERBATIM-freeze constraint behind T14's `-1` sentinel) — human-approved. |
| Grants — `chain_cursors` `UPDATE`, `chain.shedlock` full DML, both scoped to `crypto_app` only | Yes | `db/migration/V7__crypto_app_watcher_grants.sql` | `WatchRepositoryIntegrationTest.cryptoAppCanInsertSelectAndUpdateButNotDeleteOnChainCursors`; `ChainBaselineMigrationIntegrationTest` (Flyway version list + `shedlock` removed from `UNGRANTED_TABLES`) | No | None. |
| Finding 10 (Phase 3) — post-`stop()` callback is a complete no-op | Yes | `running` flag check `watch/Watcher.java:135-139` | `WatcherTest.aCallbackDeliveredAfterStopPerformsNoWork` | No | None. |
| Phase 7/8 Finding 1 — sweep task + gauge fully released on `stop()` | Yes | `Watcher.stop()` (`sweepFuture.cancel`, `sweepScheduler.shutdownNow()`, `meterRegistry.remove`) | `WatcherTest.removesItsOwnLagGaugeOnStop`, `.stopShutsDownItsOwnPrivateSweepScheduler` (Phase 11 addition) | No | None. |
| Phase 8 Finding 4 — internal failures never propagate into the adapter's polling loop | Yes | `catch (RuntimeException e)` in `handleObservation` `watch/Watcher.java:146-155` | `WatcherTest.swallowsADuplicateDecisionExceptionRatherThanPropagatingIt`, `.aFailureLoggingOneProvidersObservationDoesNotPreventTheOtherTwoFromReachingQuorum` (Phase 11 addition) | No | None. |
| Out-of-scope items (explicitly deferred at Phase 4) | N/A | — | — | Deliberately not implemented: `chain.tx.*` event emission (task 17), `ReorgDetector`/cursor walk-back (task 18), quorum-evaluating `FINALITY`, repeated `CONFIRMATIONS` re-evaluation, sidecar-as-provider test (task 24) | Documented, human-approved scope boundary, not a gap. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every in-scope file listed in the Phase 4 frozen brief ("Files to
Create"/"Files to Modify") exists and is wired in; every required test from the frozen brief's "Required
Tests" section has a corresponding, passing test; Phase 9's review-resolution findings and Phase 11's
test-review findings are both closed (one Phase 11 finding rejected after direct verification showed it
was already covered).

**(2) Does it satisfy every acceptance criterion?** Yes, AC1-AC10 all have implementation evidence and
test coverage per the matrix above. AC7 and AC8 rely partly on source inspection (an absence of
implementation-specific branching, an absence of illegal imports) rather than a positive assertion, which
is the correct verification method for a "must NOT do X" criterion.

**(3) Does it violate any LOCKED decision?** No. L1 (exactly-3, corrected per the Phase 4 follow-up,
human-approved), L2 (untouched — `HeldFactAlerter` still the sole `HELD` signal, confirmed by the
genuine-3-way-split test deliberately relying on it), L3 (log-before-decide, tested with call ordering),
L14 (no adapter-implementation-specific path, confirmed by source grep), L15 (module boundaries, confirmed
by both boundary test suites) are all respected. No `V1`-`V6` migration was touched; only a new `V7` was
added, matching the "Files NOT to Modify" list.

**(4) Remaining risks?**
- A correlation created from a late/duplicate observation for an already-fully-resolved transaction is
  not evicted by any TTL beyond the normal per-tx pruning (Phase 9 Finding 2's disclosed residual
  limitation) — rare and bounded in practice, not fixed in this task's scope.
- `rawResponseJson` is each adapter's own Jackson-serialized capture of what it already parsed, not a
  byte-identical wire-level capture (disclosed at Phase 4 scope) — true wire capture would require
  redoing T06/T07's transport layer.
- No dedicated module-boundary test scans `adapter/`'s own outbound imports (AC8's evidence row) — a
  latent gap for any *future* file added to `adapter/`, not for anything this task added.
- `agents.md`'s "paged" requirement for these two metrics is satisfied by *emission*; actual paging/alert
  routing configuration is an operational concern outside this codebase's scope.

## Verdict

**PASS** — all in-scope requirements, acceptance criteria, and LOCKED decisions are implemented, tested,
and traced to evidence; the full module regression (538 tests) shows zero regressions and only the
same 6 pre-existing, disclosed, unrelated failures.
