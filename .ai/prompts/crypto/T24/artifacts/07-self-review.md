# crypto · T24 · Phase 7 — Self Review

Self-review of the Phase 6 diff (the sole modified file, `WatcherTest.java`) against the frozen brief
(`artifacts/04-frozen-task-brief.md`) and `agents.md`. Findings only — no fixes applied here (Phase 9),
per this phase's own rule. One finding identified — cosmetic, not functional.

## 1. New citations use "Frozen Brief Finding #N" instead of this file's own established "Phase N
   Finding #N" convention

- **Issue:** Every pre-existing citation in `WatcherTest.java` (contributed across T16-T18) follows the
  form `Phase <N> Finding <N>` (e.g. `Phase 8 Finding 8`, `T17 Phase 9 Finding #10`, `Phase 11 Finding
  15`) — referring to the numbered pipeline phase that raised the finding. This task's own new comments
  instead say `Frozen Brief Finding #N` (lines 76, 77, 118, 323, 335) — a different phrase for the same
  concept, since the frozen brief *is* this pipeline's Phase 4 artifact. No functional difference, but
  it breaks the file's own established citation convention that every other task's contribution follows.
- **Severity:** Low (cosmetic/consistency only — every citation still resolves to a real, correct
  finding; nothing is ambiguous or wrong, just differently phrased).
- **Evidence:** `watch/WatcherTest.java:76-77, 118, 323, 335` (new); contrast with `watch/WatcherTest.java:255,
  269, 485, 496, 515, 541, 599, 616, 632, 687, 730, 745, 760, 823, 850` (pre-existing, all
  `Phase N Finding N` form).
- **Recommendation:** Reword the 5 new citations to `Phase 4 Finding #N` (matching the pipeline's own
  phase numbering, where Phase 4 is the frozen-task-brief/Human-Approval-gate phase) for consistency
  with every other task's contribution to this same shared file.

---

No correctness, thread-safety, or scope-boundary defects were found. Specifically checked and found
clean:
- **Frozen-brief conformance:** only `WatcherTest.java` was touched, matching the brief's sole
  authorized file; no production code, no `ChainAdapter`/`ProviderSet`/`Watcher`/`quorum/*`/
  `ProviderDegradedPublisher` change.
- **Naming convention (Findings #1/#4):** `sidecar-ethereum` is colon-free and chain-matched to the
  `ETHEREUM` watch under test, used consistently across all 4 new tests and the `sidecarAdapters` field.
- **Inclusion proof (Finding #3):** both `ArgumentCaptor`-based tests correctly narrow to the specific
  fact type under test (`EXISTENCE`/`AMOUNT`) via `eq(FactType...)`, so the captured list is
  unambiguous; the raw-type `ArgumentCaptor.forClass(List.class)` + generically-typed local variable is
  Mockito's standard idiom for this, safe under type erasure.
- **Non-interference with existing tests:** `newWatcher(long)`'s new one-line delegating body is
  behaviorally identical to its prior direct-construction body; all 62 pre-existing tests re-ran
  unmodified and green (confirmed in Phase 6, 66/66 in the class).
- **Every new `@link` Javadoc reference** (`recordsDisagreementForTheMinorityProviderInATwoOneSplit`,
  `laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers`,
  `excludesProvidersReportingExistsFalseFromAmountTokenAndConfirmationsButNotExistence`) resolves to a
  real, correctly-named existing method.
- **AC3/AC4 scoping honesty (Finding #2):** the new section's header comment explicitly disclaims proof
  of a real TypeScript sidecar process's own guarantees, attributing AC3 to the pre-existing
  `KmsSignerArchitectureTest` rule and AC4 to construction — no overclaiming.
