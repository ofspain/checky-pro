# crypto · T24 · Phase 10 — Test Generation

**Process note.** Per this task's own Phase 6 implementation notes (T24 is test-only — its entire
deliverable is a test), tests were written alongside the implementation itself, then strengthened at
Phase 9 per 3 of Kimi's accepted findings. No production code exists for this task. This artifact is
the traceability manifest.

## Test files (this task's own contribution)

| File | New tests | Purpose |
|---|---|---|
| `watch/WatcherTest.java` | 5 (+1 strengthened, +1 strengthened) | Proves a sidecar-labeled `FakeChainAdapter` is treated identically to any other provider across agreement, both directions of disagreement, lagging, and `exists=false` scenarios. |

**Total: 5 new test methods**, all passing, alongside the file's 62 pre-existing tests (67/67 total in
the class).

## Traceability matrix

| Test | AC | What it proves |
|---|---|---|
| `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` (named test) | AC1 | 2-of-3 agreement including the sidecar-labeled provider reaches the same `Watcher`/`QuorumDecisionService` path as any other combination; an `ArgumentCaptor` proves the sidecar's own answer is genuinely present in the evaluated list (Phase 8 Finding #3); stubbed to `AGREED` and asserted to reach `txLifecyclePublisher.seen(...)` (Phase 9 Finding #1) — the full path, not just the call. |
| `sidecarInTheMinorityIsRecordedAsDisagreeingLikeAnyOtherProvider` | AC2 | A sidecar-labeled provider in the minority of a 2-1 split is flagged via `recordDisagreement` exactly like any other minority provider — no exemption — with the same `ArgumentCaptor` inclusion proof applied to the `AMOUNT` fact. |
| `sidecarInTheMajorityDoesNotSuppressTheMinorityProviderBeingFlagged` (Phase 9 addition) | AC2 | The converse direction: a sidecar-labeled provider in the *majority* grants it no immunity-granting power — the genuinely disagreeing non-sidecar minority is still flagged. |
| N/A — documented, not a separate test | AC3 | The pre-existing, unmodified `KmsSignerArchitectureTest` rule (package-wide: no class outside `attest` may touch the KMS SDK) already covers any `ChainAdapter` implementation, sidecar-labeled or not. Cited in the new test section's header comment, honestly scoped (Phase 3/4 Finding #2) to what a Java-only test can prove. |
| N/A — true by construction | AC4 | `providerSidecar` and its `NamedAdapter` wrapper are never given a repository or persistence reference anywhere in `WatcherTest`'s setup. |
| `sidecarAsTheMissingThirdAnswerIsMarkedLaggingLikeAnyOtherProvider` | AC5 (part 1) | A sidecar-labeled provider that never answers is marked `LAGGING` via `ProviderHealthTracker.recordUnhealthy`, identically to any other missing provider. |
| `sidecarReportingExistsFalseIsExcludedFromAmountTokenConfirmationsLikeAnyOtherProvider` | AC5 (part 2) | A sidecar-labeled provider reporting `exists=false` is excluded from AMOUNT/TOKEN/CONFIRMATIONS — both the quorum evaluation itself and the observation-log entries that would otherwise precede it (Phase 9 Finding #2) — identically to any other provider reporting the same. |

## Verification run

`mvn -pl services/crypto test -Dtest=WatcherTest,KmsSignerArchitectureTest` — 69/69 pass (67 in
`WatcherTest`, 2 in `KmsSignerArchitectureTest`, cited unmodified).

Full module regression (`mvn -pl services/crypto -am test`): pending a Docker-available run in this
environment (the last full regression attempt during T24, at Phase 6, showed 0 real failures and 13
Docker-unavailability errors, unrelated to this task — see `06-implementation-notes.md`). `WatcherTest`
and `KmsSignerArchitectureTest` are both Docker-free and already confirmed passing above; nothing this
task touches depends on Testcontainers.

## Gaps

None identified beyond what Phase 7/8/9 already surfaced and resolved (at the time this section was
first written — see the Phase 11 additions below).

## Phase 11 (Kimi Test Review) additions

Per this pipeline's own Phase 11 convention, no separate resolution artifact is written — accepted
findings are folded directly into this artifact and the test suite. Kimi raised 5 strengthening
recommendations (no correctness defects). 2 accepted, 2 rejected (1 verified factually incorrect by
direct execution, 1 already dispositioned at Phase 9), 1 rejected as low-value/redundant.

| Recommendation | Disposition | Resolution |
|---|---|---|
| 1. Lagging test doesn't assert `provider-a`/`provider-b` are *not* also marked unhealthy | **ACCEPTED** | Added `verify(providerHealthTracker, never()).recordUnhealthy(...)` for both answering providers to `sidecarAsTheMissingThirdAnswerIsMarkedLaggingLikeAnyOtherProvider`. |
| 2. No sidecar-specific at-most-once-lagging test | **REJECTED — low value** | The underlying guard (`TxCorrelation.markLaggingFlagged`, a plain `Set<String>.add`) has zero conditional logic on provider-name content — it cannot possibly behave differently for a `sidecar-`-prefixed name. The pre-existing, non-sidecar `marksALaggingProviderAtMostOncePerCorrelationAcrossRepeatedSweeps` already fully proves this property; a sidecar-labeled duplicate would exercise no code path capable of differing by name. |
| 3. No positive proof that `provider-a`/`provider-b` are still evaluated/logged for AMOUNT when the sidecar reports `exists=false` | **REJECTED — verified factually incorrect** | Directly probed by temporarily adding Kimi's exact suggested assertion (`verify(quorumDecisionService).evaluate(..., AMOUNT, ...)`) and running the test: it **fails**. `Watcher.evaluateFact` hard-requires exactly 3 qualifying (`exists=true`) answers before evaluating AMOUNT/TOKEN/CONFIRMATIONS at all (a pre-existing, deliberate design, unrelated to T24) — with only 2 of 3 providers reporting `exists=true` in this scenario, AMOUNT is never evaluated for *anyone*, sidecar or not. The existing `never()`-only assertions are the only correct ones; the identical, pre-existing non-sidecar test (`excludesProvidersReportingExistsFalseFromAmountTokenAndConfirmationsButNotExistence`) makes the same choice for the same reason. |
| 4. Sidecar-in-majority test doesn't prove inclusion via `ArgumentCaptor` (asymmetric with the minority test) | **ACCEPTED** | Added the same `ArgumentCaptor` inclusion proof to `sidecarInTheMajorityDoesNotSuppressTheMinorityProviderBeingFlagged`, asserting the captured `AMOUNT` list contains both `"provider-a"` and `"sidecar-ethereum"`. |
| 5. New tests don't call `watcher.stop()` | **REJECTED — already dispositioned at Phase 9** | Re-raises Phase 8 Finding #5, already verified factually inconsistent with this file's own convention (only 3 of 64 `start()`-calling tests ever call `stop()` — the 3 tests specifically about `stop()`'s own behavior). Kimi's own Phase 11 text already concedes "this is not a new regression." No change. |

**Verification run (Phase 11):**
`mvn -pl services/crypto test -Dtest=WatcherTest,KmsSignerArchitectureTest` — 69/69 pass (67 in
`WatcherTest`, same count as Phase 9 — both accepted findings strengthened existing test bodies rather
than adding new methods; 2 in `KmsSignerArchitectureTest`, cited unmodified).
