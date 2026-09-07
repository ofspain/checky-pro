# crypto · T16 · Phase 10 — Test Generation

Test manifest mapping every new/modified test to the acceptance criterion or requirement it verifies.
No production code changed in this phase.

## New test files

| File | Tests | Purpose |
|---|---|---|
| `watch/WatcherTest.java` | 16 | `Watcher`'s per-provider correlation, quorum-evaluation, health-signal, and lifecycle behavior — pure unit tests, 3 `FakeChainAdapter`s wrapped in `ProviderSet.NamedAdapter`, mocked collaborators, a `MutableClock` test double. |
| `adapter/ProviderSetTest.java` | 4 | `ProviderSet`'s chain-grouping and exactly-3-providers validation — real `EthereumAdapter`/`TronAdapter` with mocked transport clients. |
| `watch/WatcherRegistryTest.java` | 4 | `WatcherRegistry`'s ShedLock-backed multi-replica shard exclusivity — `@Testcontainers` real Postgres, real `JdbcTemplateLockProvider`. |

## Traceability matrix

| Test | AC / Finding | What it proves |
|---|---|---|
| `WatcherTest.evaluatesEachFactExactlyOnceOnceAllThreeProvidersAgree` | AC1 | All 4 fact types are evaluated exactly once each when all 3 providers report the same answer. |
| `WatcherTest.doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering` | AC1 | No quorum evaluation fires with only 2 of 3 answers present. |
| `WatcherTest.laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers` | AC1, Phase 4 follow-up correction | A timed-out 3rd provider never triggers evaluation with 2 answers or a fabricated 3rd; the fact stays undecided and the non-responder is marked lagging. |
| `WatcherTest.logsEveryProviderObservationBeforeEvaluatingQuorumForTheSameFact` | AC2 (L3) | `ObservationLog.record` calls for `EXISTENCE` happen, in call order, before `QuorumDecisionService.evaluate`. |
| `WatcherTest.recordsHealthyForEveryProviderThatAnswers` | AC3 | An answering provider is marked healthy. |
| `WatcherTest.recordsDisagreementForTheMinorityProviderInATwoOneSplit` | AC3 | The minority answer in a 2-1 split is flagged disagreeing; the majority two are not. |
| `WatcherTest.doesNotRecordDisagreementForAGenuineThreeWaySplit` | AC3, Phase 7/8 Finding 3 | A true 3-way split (no majority ≥2) flags no one — relies on `HeldFactAlerter` as the sole signal for that case. |
| `WatcherTest.recordsDisagreementAtMostOncePerProviderPerTransactionEvenWhenMultipleFactsDiverge` | AC3, Phase 8 Finding 8 | A provider disagreeing on two different facts of the same transaction is only counted once. |
| `WatcherTest.marksALaggingProviderAtMostOncePerCorrelationAcrossRepeatedSweeps` | AC3, Phase 8 Finding 10 | Repeated sweep ticks over the same stale correlation flag a lagging provider only once. |
| `WatcherTest.excludesProvidersReportingExistsFalseFromAmountTokenAndConfirmationsButNotExistence` | AC4 | A provider's `exists=false` answer is excluded from `AMOUNT`/`TOKEN`/`CONFIRMATIONS` evaluation, but `EXISTENCE` is still evaluated. |
| `WatcherTest.doesNotLogAmountTokenOrConfirmationsForAProviderReportingExistsFalse` | AC4 | Same exclusion, at the observation-logging layer, not just evaluation. |
| `WatcherTest.swallowsADuplicateDecisionExceptionRatherThanPropagatingIt` | AC5 | A duplicate-decision `IllegalStateException` from `QuorumDecisionService` is caught, not propagated. |
| `WatcherTest.advancesTheCursorAfterObservationAndQuorumWritesComplete` | AC6 | `ChainCursor.save` happens after `ObservationLog.record` (call order), and `lastBlock` reflects the observed block. |
| `WatcherTest.aCallbackDeliveredAfterStopPerformsNoWork` | Phase 3 Finding 10 | A callback arriving after `stop()` touches no collaborator. |
| `WatcherTest.removesItsOwnLagGaugeOnStop` | Phase 7/8 Finding 1 | `stop()` deregisters the gauge `start()` registered — no leaked meter. |
| `WatcherTest.theLagGaugeIsTaggedWithWatchIdSoTwoWatchesSharingAnAddressDoNotCollide` | AC10, Phase 8 Finding 5 | The gauge carries a `watchId` tag distinguishing two watches on the same `(chain, address)`. |
| `ProviderSetTest.groupsThreeConfiguredEthereumAdaptersByChainWithTheirProviderNames` | AC7, AC9 (precondition) | `ProviderSet` groups adapters by chain and exposes each one's real `providerName()`, without any `ChainAdapter`-implementation-specific branch. |
| `ProviderSetTest.groupsThreeConfiguredTronAdaptersByChainWithTheirProviderNames` | AC7, AC9 (precondition) | Same, for `TRON`. |
| `ProviderSetTest.rejectsFewerThanThreeConfiguredProvidersForAChain` | AC1 (precondition), Phase 8 Finding 7 | Fewer than 3 configured providers for a chain fails fast at construction, naming the chain. |
| `ProviderSetTest.rejectsMoreThanThreeConfiguredProvidersForAChain` | AC1 (precondition), Phase 8 Finding 7 | More than 3 configured providers for a chain fails fast at construction, naming the chain. |
| `WatcherRegistryTest.onlyOneOfTwoReplicasStartsAWatcherForTheSameRegisteredWatch` | AC9 | Two `WatcherRegistry`s sharing one Postgres-backed `shedlock` table never both start a `Watcher` for the same shard. |
| `WatcherRegistryTest.repeatedReconciliationOnTheOwningReplicaDoesNotStartADuplicateWatcher` | AC9 | The owning replica's own repeated reconciliation ticks do not start a second `Watcher` for an already-running watch. |
| `WatcherRegistryTest.onlyRegisteredWatchesAreEverAssignedToAShard` | Required Tests ("only `REGISTERED` watches are watched") | With no `REGISTERED` watches returned, no shard ever starts a watcher. |
| `WatcherRegistryTest.shutdownReleasesTheShardLockSoAnotherReplicaCanImmediatelyTakeOver` | AC9, Phase 8 Finding 6 | `shutdown()` stops watchers and releases the shard lock so another replica can take over on its very next reconciliation, without waiting out `lockAtMostFor`. |

## AC10 (metrics) coverage note

`crypto.watcher.lag.seconds` emission is covered by `removesItsOwnLagGaugeOnStop` (registered on `start()`,
confirmed present) and `theLagGaugeIsTaggedWithWatchIdSoTwoWatchesSharingAnAddressDoNotCollide`.
`crypto.provider.disagreements` is not re-tested here: its increment lives inside
`ProviderHealthTracker.recordDisagreement` (T10, already covered by `ProviderHealthTrackerTest`'s own
existing metrics assertions) — `Watcher`'s own tests verify it calls `recordDisagreement`, not the
counter's internal wiring, avoiding duplicate coverage of the same increment from two suites.

## AC8 (module boundaries) coverage note

No new module-boundary test was added in this phase. `WatchModuleBoundaryTest` (existing, already
covers `watch/`'s new files `Watcher.java`/`WatcherRegistry.java` under its `ALLOWED_IMPORTS_BY_PREFIX`
map) and `ProviderModuleBoundaryTest` (existing, unconditional ban on `adapter` imports inside
`provider/`, which is what motivated relocating `ProviderSet` into `adapter/` at Phase 4) together already
enforce AC8 for every file this task touches; `adapter/ProviderSet.java` itself has no dedicated
boundary test because no existing suite scans `adapter/`'s own outbound imports (none of `adapter/`'s
existing files needed one), and adding one is outside this task's proportionate scope — `ProviderSet`'s
own imports (`EthereumAdapter`, `TronAdapter`, `ChainAdapter`, `Chain`, all within `adapter/`) are visibly
in-module by inspection.

## Verification run (this phase)

- `mvn -pl services/crypto test -Dtest=WatcherTest,ProviderSetTest,WatcherRegistryTest` — 24/24 pass.
- `mvn -pl services/crypto -am test` (full module regression) — 529 tests, 6 failures, all the same
  pre-existing, disclosed, unrelated set from Phase 9's own regression run (JSON-spacing assertion in
  `ObservationRepositoryIntegrationTest`, three DB-permission-vs-Hibernate-exception-wrapping mismatches,
  one `TokenAllowlistRepositoryIntegrationTest` pre-existing gap). 505 + 24 = 529. Zero regressions.

## Fixes applied while writing `WatcherRegistryTest` (test-only, no production code changed)

Three real ShedLock/Testcontainers integration issues were found and fixed entirely within the test file:

1. **Missing `search_path` on the test's `crypto_app` datasource.** ShedLock's own SQL references the
   unqualified table name `shedlock`; the real application sets this via
   `connection-init-sql=SET search_path TO chain, public` (`application.properties`), which the test's
   plain `DriverManagerDataSource` didn't have. Fixed by appending `&currentSchema=chain` to the JDBC URL.
2. **`Clock.fixed` at a past instant breaks real shard exclusivity.** Verified by decompiling
   `SqlStatementsSource.params()` that ShedLock's own expiry check binds `:now` to
   `ClockProvider.now()` — real wall-clock time, independent of any `Clock` passed into
   `WatcherRegistry`. A fixed clock parked in the past made every computed `lock_until` already
   "expired" by that real check, so both replicas could always acquire the lock. Fixed by using
   `Clock.systemUTC()` for the registries under test (this dimension is otherwise untested — the fixed
   `NOW` constant is only used for unrelated `Watch` fixture timestamps).
3. **Cross-test lock contamination.** All 4 tests share one static Postgres container/table; a shard
   lock row inserted by one test would still be held (real `lockAtMostFor`) when the next test ran,
   since every test here uses `shardCount=1` (same lock name `watcher-shard-0`). Fixed with an
   `@AfterEach` that clears `chain.shedlock` between tests.

A fourth, real ShedLock behavior (not a bug) was worked around rather than fixed: `lockAtLeastFor` is a
genuine minimum hold duration honored even across an explicit `unlock()`, to guard against flapping
faster than cross-node clock skew could tolerate. `shutdownReleasesTheShardLockSoAnotherReplicaCanImmediatelyTakeOver`
runs in milliseconds, well under `properties()`'s 1000ms floor, so that one test alone uses a
`WatcherProperties` with a negligible `lockAtLeastForMs` to isolate `shutdown()`'s own release behavior
from the (separately correct, untested-here) floor guarantee.
