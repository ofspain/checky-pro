# crypto · T24 · Phase 9 — Review Resolution

Human-approved dispositions for the Phase 7 self-review and Phase 8 (Kimi) independent review.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | AC1 named test doesn't prove the outcome reaches `AGREED` (only that the sidecar's answer is included in the evaluated list) | **ACCEPTED** | `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` now stubs `quorumDecisionService.evaluate(...)` to return `agreed(FactType.EXISTENCE)` and asserts `txLifecyclePublisher.seen(...)` fires — the test now proves the sidecar's answer genuinely participates in, and does not block, the real `AGREED` path all the way to the emitted lifecycle event. |
| 2 | AC5 part 2 only asserted evaluation exclusion, not logging exclusion | **ACCEPTED** | Added `verify(observationLog, never())` assertions for `AMOUNT`/`TOKEN`/`CONFIRMATIONS` (scoped to `"sidecar-ethereum"`) to `sidecarReportingExistsFalseIsExcludedFromAmountTokenConfirmationsLikeAnyOtherProvider`, mirroring `doesNotLogAmountTokenOrConfirmationsForAProviderReportingExistsFalse`. |
| 3 | Missing coverage for sidecar in the *majority* of a disagreement (no special immunity-granting power over a genuinely disagreeing minority) | **ACCEPTED** | New test `sidecarInTheMajorityDoesNotSuppressTheMinorityProviderBeingFlagged`: sidecar + `provider-a` agree, `provider-b` disagrees; asserts `provider-b` is flagged and neither `provider-a` nor the sidecar is. |
| 4 | T24 cannot currently be validated because a different, non-generic `ProviderAnswer` class (from a separate `feat` stack) collides with the generic one this task's code depends on | **RESOLVED, no T24 action** | Verified: this is a pre-existing collision between `main`'s two parallel implementations, not something T24 introduced or can fix within its own scope (`quorum/ProviderAnswer.java` is explicitly a Files-NOT-to-Modify item). The branch this task lives on (`spec/service-specs-and-ai-framework`) has twice had this exact collision introduced by an external merge and twice been reverted back to a clean, compiling state (confirmed: `mvn -pl services/crypto -am test-compile` succeeds, `WatcherTest` 67/67 pass). The underlying two-implementation reconciliation on `main` remains a separate, project-level decision outside this task. |
| 5 | New tests call `watcher.start()` but never `watcher.stop()` (resource-cleanup hygiene) | **REJECTED — verified factually inconsistent with this file's own convention** | Checked directly: of this file's 64 `start()`-calling tests (pre- and post-T24), only 3 ever call `stop()` — precisely the 3 tests whose entire purpose is testing `stop()`'s own cleanup behavior (`stopShutsDownItsOwnPrivateSweepScheduler`, `removesItsOwnLagGaugeOnStop`, and the callback-after-stop test). Every other one of the ~60 pre-existing tests follows the identical "start, never stop" pattern the new sidecar tests use — not a T24-introduced gap, and fixing it only for the 4 new tests (or file-wide, out of scope per "no unrelated refactoring") would itself be the inconsistency. |

## Files changed in this phase

- `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java` — strengthened the named
  test (Finding #1), added 3 `observationLog` assertions to the `exists=false` test (Finding #2), and
  added 1 new test method (Finding #3). No production code changed.

No public API, class name, or method signature changed beyond Phase 6's own additions. No refactoring
beyond what the three accepted findings required.

## Verification

`mvn -pl services/crypto test -Dtest=WatcherTest,KmsSignerArchitectureTest` — 69/69 pass (67 in
`WatcherTest`: 62 pre-existing + 5 sidecar tests, was 66 before this phase's 1 new test method; 2 in
`KmsSignerArchitectureTest`, cited unmodified).
`mvn -pl services/crypto -am test-compile` — clean, confirming the branch remains unpoisoned after this
phase's own two reverts of the recurring `ProviderAnswer`-collision merge.
