# crypto · T24 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T24 — Sidecar-as-provider test |
| **Spec section** | Contracts, sidecar contract, hardening |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Scope reminder:** Review the T24 tests only. No production code was changed; the deliverable is in `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java`.

---

## Summary

The Phase 10 test set is a meaningful improvement over the original Phase 6 tests. The Phase 8 findings (AC1 not proving `AGREED`, missing majority-sidecar case, AC5 part 2 missing logging exclusion) have all been addressed in `WatcherTest.java`.

The remaining gaps are **strengthening opportunities**, not correctness defects. The tests as written will pass and do exercise the R25/L14 contract. This review identifies five ways to make the suite more robust against future regressions and false positives.

---

## Recommendations

### 1. No assertion that healthy providers are *not* marked LAGGING alongside the sidecar

- **Gap:** `sidecarAsTheMissingThirdAnswerIsMarkedLaggingLikeAnyOtherProvider` verifies the sidecar is flagged `LAGGING`, but it does not assert that the two providers that actually answered (`provider-a` and `provider-b`) are *not* flagged.
- **Why it matters:** The test proves the sidecar receives the same negative treatment as a missing provider, but it does not prove the positive corollary — that answering providers are left alone. A broken implementation that flagged every provider would still pass.
- **Suggested test / strengthening:** In the same test, add:
  ```java
  verify(providerHealthTracker, never()).recordUnhealthy(eq("ETHEREUM"), eq("provider-a"), any());
  verify(providerHealthTracker, never()).recordUnhealthy(eq("ETHEREUM"), eq("provider-b"), any());
  ```

### 2. No sidecar-specific at-most-once lagging test

- **Gap:** The existing `marksALaggingProviderAtMostOncePerCorrelationAcrossRepeatedSweeps` exercises non-sidecar providers. There is no equivalent test proving the sidecar label does not cause repeated `LAGGING` calls across multiple sweeps.
- **Why it matters:** The `Watcher` marks a given provider lagging at most once per correlation (`Phase 8 Finding 10`). The sidecar path uses the same code, but a future change to provider-name parsing or health-tracker keying could accidentally special-case the `sidecar-` prefix.
- **Suggested test:** `sidecarLaggingIsRecordedAtMostOnceAcrossRepeatedSweeps` — deliver `providerA` and `providerB`, advance the clock past the correlation window, call `sweepStaleCorrelations()` three times, and assert `recordUnhealthy("ETHEREUM", "sidecar-ethereum", DegradationReason.LAGGING)` exactly once.

### 3. No proof that provider-a/b are still included in AMOUNT/TOKEN/CONFIRMATIONS when sidecar reports `exists=false`

- **Gap:** `sidecarReportingExistsFalseIsExcludedFromAmountTokenConfirmationsLikeAnyOtherProvider` asserts that the sidecar's answers are *not* evaluated or logged for AMOUNT/TOKEN/CONFIRMATIONS, but it does not assert that the two `exists=true` providers *are* evaluated and logged for those fact types.
- **Why it matters:** The test would still pass if the `Watcher` accidentally skipped all providers for those fact types (e.g., a bug that short-circuited the entire fact-evaluation block). The current assertion only proves exclusion, not that the majority of providers still proceed normally.
- **Suggested test / strengthening:** Add positive verifications to the same test:
  ```java
  verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.AMOUNT), anyList());
  verify(observationLog).record(eq("ETHEREUM"), eq(TX_HASH), eq("provider-a"), eq(FactType.AMOUNT), anyString());
  verify(observationLog).record(eq("ETHEREUM"), eq(TX_HASH), eq("provider-b"), eq(FactType.AMOUNT), anyString());
  ```
  (and similarly for TOKEN/CONFIRMATIONS, or a representative one).

### 4. Sidecar-in-majority test does not prove the sidecar answer is in the evaluated list

- **Gap:** `sidecarInTheMajorityDoesNotSuppressTheMinorityProviderBeingFlagged` verifies the minority provider is flagged and the sidecar is not, but it does not use the `ArgumentCaptor` pattern from the minority test to prove the sidecar's answer was actually evaluated.
- **Why it matters:** The minority test proves inclusion *and* correct flagging; the majority test proves only flagging. Adding inclusion proof would make the two directions of AC2 symmetric and stronger.
- **Suggested test / strengthening:** In the same test, capture the `AMOUNT` evaluation list and assert it contains both `"provider-a"` and `"sidecar-ethereum"`.

### 5. New sidecar tests do not stop the `Watcher`

- **Gap:** All five new tests call `watcher.start()` but never `watcher.stop()`. The existing `WatcherTest` methods have the same pattern, so this is not a new regression, but it is a hygiene gap in the newly added tests.
- **Why it matters:** Each started `Watcher` owns a private virtual-thread scheduler. While JUnit's per-method instance isolation prevents cross-test leakage, the tests leave schedulers running until garbage collection, which is inconsistent with the explicit lifecycle `Watcher` exposes.
- **Suggested test / strengthening:** Either wrap each new test body in a try-finally that calls `watcher.stop()`, or add a class-level `@AfterEach` that stops any started watcher. This is a cleanup strengthening, not a new test case.

---

## Confirmations

- The named test `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` is present and now proves the full `AGREED` path (stubbed decision + `txLifecyclePublisher.seen(...)` assertion).
- AC2 is covered in both directions: sidecar minority and sidecar majority.
- AC5 part 2 now covers both evaluation exclusion and observation-log exclusion for the sidecar.
- AC3 and AC4 remain correctly documented rather than over-claimed.
- The sidecar provider name remains colon-free and chain-matched (`sidecar-ethereum`).
- No duplicate or redundant tests were introduced; the sidecar tests are deliberate variants of existing non-sidecar scenarios.

---

## Build note

The Phase 10 artifact reports `69/69` passing for `WatcherTest` + `KmsSignerArchitectureTest`. That pass count **cannot currently be reproduced in `main`** because `main` contains the `feat` stack's non-generic `com.themistra.crypto.quorum.ProviderAnswer`, which conflicts with the generic `ProviderAnswer<T>` that the spec-stack `Watcher`, `QuorumDecisionService`, `HeldFactAlerter`, and `WatcherTest` depend on. Resolving that class collision is a prerequisite for running the T24 tests in `main`; it is not a defect in the T24 tests themselves.
