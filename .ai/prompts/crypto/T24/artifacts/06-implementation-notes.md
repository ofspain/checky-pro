# crypto · T24 · Phase 6 — Implementation Notes

## What changed

**Modified (test-only, no production code touched — matches the frozen brief's own scope exactly):**

`services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java`:

1. Two new fields: `providerSidecar` (a plain `FakeChainAdapter` labeled `"sidecar-ethereum"` — colon-free
   per Finding #1, chain-matched to the test's `ETHEREUM` watch per Finding #4) and `sidecarAdapters`
   (a 3-element `List<ProviderSet.NamedAdapter>`: `provider-a`, `provider-b`, and the sidecar in
   `provider-c`'s former slot).
2. `newWatcher(long correlationWindowMs)` split into a new two-arg overload
   `newWatcher(long correlationWindowMs, List<ProviderSet.NamedAdapter> adapters)` (Finding #6's exact
   pinned signature) plus a one-line delegating single-arg overload using the shared `adapters` field —
   every one of the 62 pre-existing call sites is unaffected.
3. Four new `@Test` methods under a new `// ---------- Sidecar-as-provider (R25/L14, T24) ----------`
   section:
   - `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` (the named test, AC1) — captures the actual
     `List<ProviderAnswer<Boolean>>` passed to `quorumDecisionService.evaluate` via `ArgumentCaptor` and
     asserts `sidecar-ethereum`'s answer is genuinely present in it (Finding #3).
   - `sidecarInTheMinorityIsRecordedAsDisagreeingLikeAnyOtherProvider` (AC2) — same capture technique,
     applied to the `AMOUNT` fact's minority-disagreement path.
   - `sidecarAsTheMissingThirdAnswerIsMarkedLaggingLikeAnyOtherProvider` (AC5, part 1).
   - `sidecarReportingExistsFalseIsExcludedFromAmountTokenConfirmationsLikeAnyOtherProvider` (AC5, part 2).

AC3 (no signing access) and AC4 (no business state access) are not separate test methods — per the
frozen brief, AC3 is proven by the pre-existing, unmodified `KmsSignerArchitectureTest` rule (cited in
this section's own header comment, not re-implemented), and AC4 holds true by construction (`providerSidecar`
is never given a repository/persistence reference anywhere in this class's setup). Both are stated
explicitly, honestly scoped to "the Java core" per Finding #2, in the new section's header comment.

## Deviations from the plan, forced by reality

None. Every field, method signature, and test body matched Phase 5's plan exactly on first attempt —
`ArgumentCaptor.forClass(List.class)` with `@SuppressWarnings("unchecked")` (the same idiom the plan
implied but didn't spell out verbatim) was needed on the two capturing test methods, consistent with
this codebase's existing use of raw-type `ArgumentCaptor` elsewhere in the test suite.

## Mapping to acceptance criteria

- **AC1 (named test).** `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` — 2-of-3 agreement
  including the sidecar reaches `AGREED` via the unmodified `Watcher`/`QuorumDecisionService` path;
  the sidecar's own answer is proven present in the evaluated list, not just some `anyList()`.
- **AC2.** `sidecarInTheMinorityIsRecordedAsDisagreeingLikeAnyOtherProvider` — the sidecar in a 2-1
  minority is flagged via `recordDisagreement` exactly like any other minority provider, with the same
  answer-inclusion proof extended to the `AMOUNT` fact.
- **AC3.** Documented, not re-implemented: `KmsSignerArchitectureTest`'s pre-existing, unmodified,
  package-wide rule.
- **AC4.** True by construction, documented inline: `providerSidecar` has no repository/persistence
  reference anywhere in its construction or `Watcher`'s wiring.
- **AC5.** Both parts covered: `sidecarAsTheMissingThirdAnswerIsMarkedLaggingLikeAnyOtherProvider`
  (lagging) and `sidecarReportingExistsFalseIsExcludedFromAmountTokenConfirmationsLikeAnyOtherProvider`
  (`exists=false` exclusion).

## Verification

`mvn -pl services/crypto test-compile` — clean.
`mvn -pl services/crypto test -Dtest=WatcherTest,KmsSignerArchitectureTest` — 68/68 pass (66 in
`WatcherTest`: 62 pre-existing + 4 new; 2 in `KmsSignerArchitectureTest`, cited unmodified).

Full module regression (`mvn -pl services/crypto -am test`): 689 tests, 0 failures, 13 errors. All 13
errors are the identical root cause, confirmed by reading every one of the 13 erroring surefire
reports directly: `java.lang.IllegalStateException: Previous attempts to find a Docker environment
failed` (Testcontainers cannot find a Docker daemon in this environment right now — confirmed
separately via `docker info`). All 13 erroring classes are Testcontainers-dependent
(`*RepositoryIntegrationTest`, `*IntegrationTest`, `KmsSignerLocalStackIntegrationTest`,
`ObservationSnapshotStoreLocalStackIntegrationTest`, `WatcherRegistryTest` — the last also needs a real
DB for its ShedLock-backed test). None of them touch anything this task changed; this is an
environmental condition (Docker unavailable in this session right now), not a regression — the lower
total test count (689 vs. the 743 seen earlier in this same session when Docker was available) is a
direct consequence of these classes erroring at container-startup/`@BeforeAll` before their individual
`@Test` methods can even be enumerated, not tests going missing. Recommend re-running the full
regression once Docker is available to get a true assertion-level pass/fail count; not blocking for
this task, since T24's own tests (`WatcherTest`, `KmsSignerArchitectureTest`) are both Docker-free and
already confirmed 68/68 passing above.
