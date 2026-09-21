# crypto · T24 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T24 — Sidecar-as-provider test |
| **Spec section** | Contracts, sidecar contract, hardening |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Scope reminder:** Review the T24 implementation (Phase 6) and self-review (Phase 7) only. No production code was changed; the task modified `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java` only.

---

## Summary

The T24 implementation correctly follows the frozen brief's scope and addresses the required acceptance criteria (AC1, AC2, AC5). The self-review identified one cosmetic citation inconsistency. This independent review found **no correctness or security defects** in the test logic itself, but it identified **three coverage/completeness gaps** in the new tests and one **project-level blocker**: the current `main` branch cannot compile these tests because a different `ProviderAnswer` class (from the `feat` stack) now shadows the generic spec-stack class the T24 code depends on.

---

## Findings

### 1. AC1 named test does not prove the outcome reaches `AGREED`

- **Issue:** `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` verifies that `quorumDecisionService.evaluate(...)` is invoked with the sidecar's answer present, but because `quorumDecisionService` is a Mockito mock, it does not exercise the real quorum logic and does not assert that the sidecar's answer contributes to an `AGREED` outcome.
- **Evidence:** `WatcherTest.java:347-352` captures the evaluated list and asserts `extracting(ProviderAnswer::provider).contains("sidecar-ethereum")`; there is no stubbing of `quorumDecisionService.evaluate(...)` to return `AGREED`, and no assertion on the resulting decision or on downstream behavior (e.g., `txLifecyclePublisher.seen(...)`).
- **Recommendation:** Either stub the mock to return an `AGREED` `QuorumDecision` and assert that the `Watcher` emits the expected lifecycle event, or run the named test with a real `QuorumDecisionService` + `QuorumEvaluator` so the `AGREED` path is genuinely exercised. This would close the gap between the test's current assertion and the wording of AC1 ("reaches `AGREED` via the same, unmodified `Watcher`/`QuorumDecisionService` path").
- **Confidence:** Medium

### 2. AC5 part 2 only asserts evaluation exclusion, not logging exclusion

- **Issue:** The frozen brief requires that a sidecar reporting `exists=false` is excluded from "AMOUNT/TOKEN/CONFIRMATIONS logging/evaluation." The new test `sidecarReportingExistsFalseIsExcludedFromAmountTokenConfirmationsLikeAnyOtherProvider` verifies only the evaluation half (`quorumDecisionService.evaluate` is never called for those fact types). It does not verify the logging half (`observationLog.record(...)` is never called for those fact types).
- **Evidence:** `WatcherTest.java:415-421` checks `quorumDecisionService, never()` for AMOUNT/TOKEN/CONFIRMATIONS, but has no corresponding `verify(observationLog, never())` assertions. The existing non-sidecar test `doesNotLogAmountTokenOrConfirmationsForAProviderReportingExistsFalse` (`WatcherTest.java:306-317`) covers logging, but with only a single provider and not in the mixed-provider scenario.
- **Recommendation:** Add `verify(observationLog, never()).record(...)` assertions for `FactType.AMOUNT`, `TOKEN`, and `CONFIRMATIONS` to the sidecar-specific test, mirroring the existing non-sidecar logging test.
- **Confidence:** Medium

### 3. Missing coverage for sidecar in the majority of a disagreement

- **Issue:** AC2 covers the case where the sidecar is in the minority and is flagged as disagreeing. R25/L14 require "no special treatment" in both directions, so a sidecar in the majority should be treated like any other majority provider, with the non-sidecar minority flagged.
- **Evidence:** No new test puts the sidecar-labeled adapter on the winning side of a 2-1 disagreement and asserts that the losing non-sidecar provider is flagged.
- **Recommendation:** Add a test where `providerSidecar` + `providerA` agree and `providerB` disagrees, then assert `recordDisagreement("ETHEREUM", "provider-b")` is called and the sidecar is not flagged. This would strengthen the "no special treatment" proof.
- **Confidence:** Low

### 4. T24 cannot currently be validated in `main` due to a `ProviderAnswer` class collision

- **Issue:** `mvn -pl services/crypto test-compile` fails in the current `main` branch. The spec stack's `com.themistra.crypto.quorum.ProviderAnswer` is generic (`ProviderAnswer<T>`), but the current `main` contains a non-generic `ProviderAnswer` record contributed by the `feat` stack. T24's implementation and the existing spec-stack production code (`QuorumDecisionService`, `HeldFactAlerter`, `Watcher`, and `WatcherTest`) all depend on the generic version.
- **Evidence:** Compile errors include:
  - `QuorumDecisionService.java:60:83` — `type com.themistra.crypto.quorum.ProviderAnswer does not take parameters`
  - `HeldFactAlerter.java:30:94` — same
  - `Watcher.java:369:103` — same
  - `WatcherTest.java:347-352` uses `ArgumentCaptor<List<ProviderAnswer<Boolean>>>` and `ProviderAnswer::provider`, which do not exist on the non-generic class.
- **Recommendation:** Treat this as a prerequisite, not a T24 fix. The `feat` and `spec` `ProviderAnswer` types must be reconciled (or the `feat` stack removed from `main`) before T24 can be compiled and its tests executed. Until then, the claimed `WatcherTest` 66/66 pass cannot be reproduced in `main`.
- **Confidence:** High

### 5. New tests do not stop the `Watcher` (cleanup gap)

- **Issue:** Each new test calls `watcher.start()`, which starts a private virtual-thread scheduler. None of the new tests call `watcher.stop()`. JUnit creates a new test instance per method, so the leak does not cross-test, but it still leaves a scheduler running until garbage collection and is inconsistent with the explicit cleanup pattern encouraged by `Watcher#stop`.
- **Evidence:** `WatcherTest.java:339-340`, `362-363`, `389-390`, `408-409` call `start()`; no `stop()` or try-with-resources follows.
- **Recommendation:** Wrap the new tests in a try-finally or use an `@AfterEach` to call `watcher.stop()`. This is a hygiene issue, not a functional defect.
- **Confidence:** Low

---

## Confirmations (no defects found)

- **Scope respected:** Only `WatcherTest.java` was modified; no production code, `spec/`, or unrelated tests were touched.
- **Colon-free sidecar naming:** `sidecar-ethereum` avoids the `ProviderDegradedPublisher` colon ban (`WatcherTest.java:81`).
- **Chain matching:** The sidecar label's chain suffix matches the `ETHEREUM` watch fixture (`WatcherTest.java:81`, `108`).
- **AC3 (no signing access):** Correctly documented as out of scope for a Java unit test; the pre-existing, unmodified `KmsSignerArchitectureTest` rule is cited (`WatcherTest.java:326-328`).
- **AC4 (no business state access):** True by construction; `providerSidecar` is never given a repository/persistence reference.
- **Inclusion proof (Finding #3):** `ArgumentCaptor` correctly narrows to `FactType.EXISTENCE` and `FactType.AMOUNT` and asserts the sidecar answer is present in the evaluated list.
- **No duplicate-provider risk:** The sidecar list replaces `provider-c` rather than adding a fourth provider, so `QuorumEvaluator`'s exactly-3 invariant is preserved.

---

## Open Questions

- None specific to T24. The project-level reconciliation of the two `ProviderAnswer` classes is the only blocker.
