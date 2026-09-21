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
first written — see the Phase 11 additions below, once Kimi's test review lands).
