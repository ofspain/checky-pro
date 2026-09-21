# crypto · T24 · Phase 5 — Implementation Plan

## Files to create

None — the frozen brief authorizes no new files.

## Files to modify

- `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java`

## Public methods (signatures)

None — `WatcherTest` is a top-level test class with no public API; all new members are package-private
(`@Test` methods) or `private` (helper/field), matching the file's existing convention throughout.

## Private methods

New fields (instance, alongside the existing `providerA`/`providerB`/`providerC`/`adapters`):
```java
private final FakeChainAdapter providerSidecar = new FakeChainAdapter(Chain.ETHEREUM, "sidecar-ethereum");
private final List<ProviderSet.NamedAdapter> sidecarAdapters = List.of(
        new ProviderSet.NamedAdapter("provider-a", providerA),
        new ProviderSet.NamedAdapter("provider-b", providerB),
        new ProviderSet.NamedAdapter("sidecar-ethereum", providerSidecar));
```
(Reuses the already-shared `providerA`/`providerB` fields; `providerC` is not part of this list — exactly
3 adapters total, `QuorumEvaluator`'s own hard requirement, with the third slot given to the sidecar.)

New/changed helper method (Frozen Brief Finding #6 — exact signature pinned):
```java
private Watcher newWatcher(long correlationWindowMs, List<ProviderSet.NamedAdapter> adapters) {
    return new Watcher(watch, adapters, observationLog, quorumDecisionService, providerHealthTracker,
            chainCursorRepository, correlationWindowMs, meterRegistry, clock, objectMapper,
            txLifecyclePublisher, List.of(finalityPolicy), FINALITY_POLL_INTERVAL_MS, reorgDetector);
}

private Watcher newWatcher(long correlationWindowMs) {
    return newWatcher(correlationWindowMs, adapters);
}
```
(The existing single-arg overload's body — currently the direct `new Watcher(...)` call — moves into the
new two-arg overload; the single-arg overload becomes a one-line delegation to it using the shared
`adapters` field, so every one of the 62 pre-existing call sites is unaffected.)

## Entities used

None (no JPA entities touch this test).

## Repositories used

`ChainCursorRepository` — already-mocked, reused unchanged (`when(chainCursorRepository.findByWatchId(any())).thenReturn(Optional.empty())`, set in the existing `@BeforeEach setUp()`).

## Services used

None new. Reuses the existing mocked collaborators: `ObservationLog`, `QuorumDecisionService`,
`ProviderHealthTracker`, `TxLifecyclePublisher`, `ReorgDetector`, and the existing `MutableClock`/
`SimpleMeterRegistry` test doubles.

## Unit/integration tests required

All four are new `@Test` methods appended after the existing `// ---------- AC4: exists=false
exclusion ----------` section (or a new `// ---------- Sidecar-as-provider (R25/L14, T24) ----------`
section), each constructing its own local `Watcher` via `newWatcher(_, sidecarAdapters)` rather than
reusing the shared `watcher`/`adapters` fields other tests rely on:

1. **`shouldTreatSidecarOutputAsJustAnotherProviderAnswer`** (named test, AC1). 2-of-3 agreement
   (`providerA`, `providerB`, `providerSidecar` all report the same `TxResult`) reaches quorum
   evaluation for `EXISTENCE`. An `ArgumentCaptor<List>` on `quorumDecisionService.evaluate(...)`'s
   final argument asserts the captured list contains an entry whose `provider()` equals
   `"sidecar-ethereum"` (Finding #3) — not merely that some `anyList()` was passed.
2. **`sidecarInTheMinorityIsRecordedAsDisagreeingLikeAnyOtherProvider`** (AC2). `providerA`/`providerB`
   agree on one amount; `providerSidecar` reports a different amount. Asserts
   `providerHealthTracker.recordDisagreement("ETHEREUM", "sidecar-ethereum")` fires exactly once, and
   `providerA`/`providerB` are never flagged — mirrors `recordsDisagreementForTheMinorityProviderInATwoOneSplit`'s
   exact structure with the minority provider swapped for the sidecar.
3. **`sidecarAsTheMissingThirdAnswerIsMarkedLaggingLikeAnyOtherProvider`** (AC5, part 1).
   `providerA`/`providerB` answer, `providerSidecar` never does; advance the clock past
   `correlationWindowMs` and call `watcher.sweepStaleCorrelations()` directly (package-private test
   seam, mirrors `laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers`). Asserts
   `providerHealthTracker.recordUnhealthy("ETHEREUM", "sidecar-ethereum", DegradationReason.LAGGING)`
   and `verifyNoInteractions(quorumDecisionService)`.
4. **`sidecarReportingExistsFalseIsExcludedFromAmountTokenConfirmationsLikeAnyOtherProvider`** (AC5,
   part 2). `providerA`/`providerB` report `exists=true`, `providerSidecar` reports `exists=false` —
   mirrors `excludesProvidersReportingExistsFalseFromAmountTokenAndConfirmationsButNotExistence`'s exact
   structure. Asserts `EXISTENCE` is still evaluated but `AMOUNT`/`TOKEN`/`CONFIRMATIONS` are never
   evaluated (`verify(..., never())`), proving the sidecar's negative answer is excluded exactly like
   any other provider's would be.

Each new test method's Javadoc/inline comment states the AC3/AC4 scoping honestly (Finding #2): these
tests prove the Java core (`Watcher`/`QuorumDecisionService`/`ProviderHealthTracker`) grants a
sidecar-labeled adapter no special treatment; they do not and cannot prove anything about a real
TypeScript sidecar process's own signing/deployment posture. A one-sentence citation of the pre-existing,
unmodified `KmsSignerArchitectureTest` rule (no class outside `attest` may touch the KMS SDK, package-wide,
already covering any `ChainAdapter` implementation regardless of naming) stands in for AC3 — no new test
method for it, per the frozen brief's explicit "no new ArchUnit rule."

## Execution order

1. Add the two new fields (`providerSidecar`, `sidecarAdapters`).
2. Change `newWatcher(long)` into the two-arg `newWatcher(long, List<ProviderSet.NamedAdapter>)` plus a
   one-line delegating single-arg overload (Finding #6) — compile and re-run the full existing
   `WatcherTest` suite (62 tests) immediately after this step, before writing any new test, to prove the
   refactor is behavior-preserving for every pre-existing test.
3. Add test 1 (named test, AC1) — run it alone to confirm the `ArgumentCaptor` technique compiles and
   the sidecar's answer is genuinely captured.
4. Add test 2 (AC2, minority disagreement).
5. Add test 3 (AC5, lagging).
6. Add test 4 (AC5, `exists=false` exclusion).
7. Run the full `WatcherTest` class (66 tests expected: 62 pre-existing + 4 new) plus
   `KmsSignerArchitectureTest` (cited, unmodified) — then the full module regression
   (`mvn -pl services/crypto -am test`) to confirm zero regressions elsewhere.
