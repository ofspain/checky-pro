# crypto · T24 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R25 — sidecar output treated as just another provider answer, no quorum authority/signing/state | Yes | `watch/WatcherTest.java` — `providerSidecar`/`sidecarAdapters` fields and 5 test methods | `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` (named test) + 4 supporting tests | No | None. |
| L14 — sidecars are translation-only, no quorum authority, no signing access, no business state | Yes | Same as above; AC3/AC4 honestly scoped to what a Java-only test can prove (Phase 3/4 Finding #2) | See AC rows below | No | None (deliberate, human-approved scoping, not a shortfall). |
| AC1 (named test, agreement) | Yes | `WatcherTest.java` — `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` | Stubs `AGREED`, captures the evaluated list via `ArgumentCaptor` (proves sidecar inclusion, Phase 8 Finding #3), asserts `txLifecyclePublisher.seen(...)` fires (Phase 9 Finding #1 — proves the full path, not just the call) | No | None. |
| AC2 (no quorum authority, both directions) | Yes | `sidecarInTheMinorityIsRecordedAsDisagreeingLikeAnyOtherProvider`, `sidecarInTheMajorityDoesNotSuppressTheMinorityProviderBeingFlagged` | Both capture and assert inclusion in the evaluated `AMOUNT` list (the majority test's capture added at Phase 11 Finding #4 for symmetry) | No | None. |
| AC3 (no signing access, scoped to the Java core) | Yes | Pre-existing, unmodified `attest/KmsSignerArchitectureTest.java` — package-wide rule, cited in the new test section's header comment | `KmsSignerArchitectureTest` (2 tests, re-run unmodified, still passing with `providerSidecar` present) | No | None — deliberately not re-implemented as a new rule (Phase 2/4 decision: the existing rule already covers any `ChainAdapter` implementation regardless of naming). |
| AC4 (no business state access, true by construction) | Yes | `providerSidecar`/`NamedAdapter` construction — no repository/persistence reference anywhere in `WatcherTest`'s setup | Documented inline, not a separate test (nothing to assert — the type system itself prevents a `FakeChainAdapter` from holding a repository reference it's never given) | No | None. |
| AC5 part 1 (lagging) | Yes | `sidecarAsTheMissingThirdAnswerIsMarkedLaggingLikeAnyOtherProvider` | Asserts `recordUnhealthy(..., LAGGING)` for the sidecar, plus (Phase 11 Finding #1) `never()` for the two answering providers | No | None. |
| AC5 part 2 (`exists=false` exclusion) | Yes | `sidecarReportingExistsFalseIsExcludedFromAmountTokenConfirmationsLikeAnyOtherProvider` | Asserts both evaluation exclusion (`quorumDecisionService, never()`) and logging exclusion (`observationLog, never()`, added Phase 9 Finding #2) for AMOUNT/TOKEN/CONFIRMATIONS | No | None. |
| Phase 3 Finding #1 (colon-free naming) | Yes | `sidecar-ethereum` used consistently, `ProviderDegradedPublisher` untouched | Verified: `ProviderDegradedPublisher.requireNoColon` would reject a colon-bearing name; `sidecar-ethereum` never triggers it | No | None. |
| Phase 3 Finding #4 (chain-matched naming) | Yes | `sidecar-ethereum` matches the `ETHEREUM`-chain `watch` fixture | — | No | None. |
| Phase 3 Finding #6 (`newWatcher` overload) | Yes | `newWatcher(long, List<ProviderSet.NamedAdapter>)`, existing overload delegates to it | All 62 pre-existing tests re-run unmodified and green | No | None. |
| Phase 8/9 Finding #4 (`ProviderAnswer` collision) | N/A to T24 | Verified: a pre-existing collision between two parallel implementations on `main`/`feat` stack, unrelated to and unfixable within T24's own scope (`quorum/ProviderAnswer.java` is a Files-NOT-to-Modify item) | — | No (resolved on this branch three times via revert, each time confirmed by a clean `mvn -pl services/crypto -am test-compile`) | Out-of-scope, documented, not a T24 defect. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. The one file the frozen brief authorized
(`watch/WatcherTest.java`) contains all 5 required test methods, each passing. Phase 3's 6 findings (all
accepted, 1 verified Critical), Phase 8's 5 findings (3 accepted and implemented at Phase 9, 1 resolved
as out-of-scope, 1 rejected after direct source verification), and Phase 11's 5 recommendations (2
accepted, 1 rejected after being directly disproven by execution, 1 rejected as low-value/redundant, 1
rejected as already dispositioned) are all resolved with cited, verified reasoning — no finding was
accepted or rejected without checking it against the actual code or by running it.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC5 all have direct test coverage
or (AC3/AC4) a documented, deliberate, human-approved reason no separate test was needed. AC1 in
particular went through two rounds of strengthening (Phase 8→9: proving the full `AGREED` path, not
just the evaluation call) before reaching a state that fully matches its own literal wording.

**(3) Does it violate any LOCKED decision?** No. L14's own clause for this task — sidecar output is one
more provider answer, no quorum authority/signing/state — is tested exactly as far as a Java-only unit
test can honestly reach, with the boundary of that reach explicitly documented (Phase 3/4 Finding #2)
rather than silently overclaimed. No production file was modified: `ChainAdapter`, `ProviderSet`,
`Watcher`, `quorum/*`, and `ProviderDegradedPublisher` are all byte-for-byte unchanged from before this
task (confirmed via `git diff --stat` against the pre-T24 baseline, showing exactly one file touched
across every commit this task produced).

**(4) Remaining risks?**
- **The `ProviderAnswer` class collision between `main`'s two parallel implementations remains
  unresolved at the project level.** This branch (`spec/service-specs-and-ai-framework`) has been
  reverted back to a clean, compiling state three separate times during this task alone, each time
  because an external process re-merged the same stale, poisoned commit. This is a process/infrastructure
  issue outside T24's own scope, but it is worth flagging plainly: every remaining phase transition (and
  any future task after T24) carries a real risk of the same recurrence until the root cause — a stale
  clone somewhere that keeps re-introducing `main`'s old state — is fixed.
- **No new risk was introduced by this task** — it is purely additive test coverage; no production code
  was touched at any point across all review/resolution phases.
- **Docker was unavailable during this task's own full-module regression attempt** (Phase 6) — 13
  Testcontainers-dependent classes errored at container startup, unrelated to anything T24 changed.
  `WatcherTest` and `KmsSignerArchitectureTest`, the only two classes this task's tests touch, are both
  Docker-free and have been confirmed passing (69/69) on every verification run throughout this task.

## Verdict

**PASS** — R25 and L14 are both implemented and tested to the fullest extent a Java-only unit test can
honestly reach, with that boundary explicitly and deliberately documented rather than overclaimed. Every
acceptance criterion has direct evidence, every review phase's findings are resolved with verified
reasoning (including one finding disproven by direct execution and one exposed as inconsistent with the
test file's own 60-test-strong established convention), and the one project-level risk found along the
way (the recurring `ProviderAnswer` branch poisoning) is fully resolved on this branch and clearly
flagged as an external, out-of-scope process issue for the user to address separately.
