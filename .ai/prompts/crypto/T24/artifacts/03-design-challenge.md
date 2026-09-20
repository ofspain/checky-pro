<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# crypto · T24 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T24 — Sidecar-as-provider test |
| **Model** | Kimi 2.7 |
| **Consumed** | `artifacts/02-task-implementation-brief.md` · `watch/Watcher.java` · `watch/WatcherTest.java` · `adapter/FakeChainAdapter.java` · `adapter/ProviderSet.java` · `provider/ProviderHealthTracker.java` · `provider/ProviderDegradedPublisher.java` · `attest/KmsSignerArchitectureTest.java` · `spec/crypto-service/design.md` · `spec/crypto-service/agents.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### 1. Proposed `sidecar:<chain>` naming convention conflicts with `ProviderDegradedPublisher`'s colon ban (CRITICAL)

- **Issue:** The TIB requires the fake sidecar's provider name to follow `design.md`'s DDL-comment convention `sidecar:<chain>` (e.g., `sidecar:tron`). However, `ProviderDegradedPublisher.publish` explicitly rejects any `provider` value containing `:` to keep its `aggregateId`/`idempotencyKey` concatenation unambiguous. `ProviderHealthTracker` calls `publisher.publish` when a provider crosses the disagreement threshold or transitions to unhealthy. Therefore, a sidecar-labeled provider that ever disagrees or lags would cause `IllegalArgumentException` instead of a degraded event.
- **Severity:** Critical — the chosen naming convention is incompatible with existing production code and would break R5/provider-degraded signaling for any sidecar.
- **Evidence:** `provider/ProviderDegradedPublisher.java:51-60` and `:64-68`; `provider/ProviderHealthTracker.java:134-142` (`transitionToUnhealthy` calls `publisher.publish`); `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql:28` (DDL comment).
- **Recommended brief amendment:** Either (a) change the convention in the test to a colon-free delimiter such as `sidecar-<chain>` or `sidecar_<chain>` (e.g., `sidecar-ethereum`), or (b) if the colon form must be preserved, explicitly scope out degraded-event signaling for sidecars and add a production-code task to escape/encode provider names in `ProviderDegradedPublisher`. Option (a) is test-only and respects the "no production changes" constraint.

### 2. Java test cannot prove a real TypeScript sidecar has "no signing access" or "no business state" (HIGH)

- **Issue:** AC3 cites `KmsSignerArchitectureTest` as proof that no `ChainAdapter` implementation can reach the KMS SDK. That rule is correct for Java classes, but a real sidecar is a separate TypeScript process. It is not scanned by ArchUnit and its lack of signing access depends on L14/process/credentials, not on this Java unit test. The same limitation applies to AC4's "no business state access" claim.
- **Severity:** High — the acceptance criteria overstate what the proposed test can actually demonstrate.
- **Evidence:** `attest/KmsSignerArchitectureTest.java:54-92` (scans `com.themistra.crypto` only); `spec/crypto-service/package.md:45` (sidecars are separate TS processes); `spec/crypto-service/agents.md:87-88` (sidecars are translation-only).
- **Recommended brief amendment:** Reword AC3 and AC4 to clarify that the Java test proves the *Java core* does not grant special signing/state access to a sidecar-labeled adapter, while the cross-process guarantees remain governed by L14 and sidecar build/deployment controls (out of this task's scope).

### 3. Test assertions do not verify the sidecar answer is actually in the quorum evaluation list (MEDIUM)

- **Issue:** The TIB's test plan verifies agreement/disagreement outcomes by mocking `QuorumDecisionService.evaluate(..., anyList())`. This does not prove the sidecar's answer is included in the list passed to quorum evaluation; a bug that dropped the sidecar answer and evaluated only the two non-sidecar answers could still produce the same mocked outcome.
- **Severity:** Medium — weak assertion allows a false positive for "sidecar treated as just another provider answer."
- **Evidence:** Existing `WatcherTest` pattern at lines 135-138 and 204-217 uses `anyList()` for the answers argument.
- **Recommended brief amendment:** Require the named test (and the AC2 test) to capture the `List<ProviderAnswer>` passed to `quorumDecisionService.evaluate` and assert it contains an entry whose provider name is the sidecar label.

### 4. Example `sidecar:tron` on an Ethereum watch is misleading (LOW)

- **Issue:** The TIB's example sidecar name is `sidecar:tron`, but every existing `WatcherTest` uses an `ETHEREUM` watch. A reader may infer the suffix is arbitrary or that the sidecar chain can differ from the watch chain. The `design.md` DDL comment uses `sidecar:solana` as a chain label, implying the suffix should match the translated chain.
- **Severity:** Low — cosmetic, but could lead to a confusing test.
- **Evidence:** `artifacts/02-task-implementation-brief.md` lines 21, 127; `WatcherTest.java` line 95 (`watch` uses `Chain.ETHEREUM`).
- **Recommended brief amendment:** Use `sidecar:ethereum` in the example and explicitly state the suffix should match the watch's chain (or, if the colon convention is kept for documentation, state that the test name suffix is just a label).

### 5. No coverage for sidecar as the lagging provider or reporting `exists=false` (LOW-MEDIUM)

- **Issue:** The TIB requires only (a) sidecar agreeing in a 2-of-3 majority and (b) sidecar disagreeing in a 2-1 split. It omits two other name-agnostic paths a sidecar could take: being the sole missing answer (lagging) and answering `exists=false` while the majority says `exists=true`.
- **Severity:** Low-Medium — these paths exercise the same generic `Watcher` code, but without explicit sidecar coverage the "indistinguishable from any other provider" claim is incomplete.
- **Evidence:** `Watcher.sweepStaleCorrelations` (lines 419-433) flags every adapter not yet answered; `Watcher.logObservation` (lines 206-213) excludes `exists=false` providers from AMOUNT/TOKEN/CONFIRMATIONS.
- **Recommended brief amendment:** Add optional test scenarios (or fold into AC1/AC2) verifying that a sidecar-labeled provider is marked `LAGGING` when it is the missing third answer, and that a sidecar reporting `exists=false` is excluded from non-existence facts exactly like any other provider.

### 6. `newWatcher` overload contract is unspecified (LOW)

- **Issue:** The TIB says to add "a new overload of the existing `newWatcher(...)` helper accepting an explicit adapters list" but does not state whether the existing overload must continue to use the shared `provider-a/b/c` fields, whether the new overload should accept `List<ProviderSet.NamedAdapter>` or `List<FakeChainAdapter>`, etc.
- **Severity:** Low — implementation can infer, but ambiguity can cause unnecessary churn.
- **Evidence:** `WatcherTest.java` lines 101-105 (existing `newWatcher` uses shared `adapters` field).
- **Recommended brief amendment:** Specify the overload signature, e.g., `private Watcher newWatcher(long correlationWindowMs, List<ProviderSet.NamedAdapter> adapters)` and state that the original overload delegates to it with the shared `adapters` field.
