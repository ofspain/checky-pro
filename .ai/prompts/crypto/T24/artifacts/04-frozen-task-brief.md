STATUS: FROZEN

# crypto · T24 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 6 findings accepted. Finding #1's critical claim (the `sidecar:<chain>` naming convention crashing
`ProviderDegradedPublisher`) was independently verified directly against source
(`ProviderDegradedPublisher.java:51-60`'s `requireNoColon` guard) before acceptance.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | `sidecar:<chain>` naming convention conflicts with `ProviderDegradedPublisher`'s colon ban (CRITICAL, verified) | **ACCEPTED (verified)** | Switch to a colon-free `sidecar-<chain>` convention (e.g. `sidecar-ethereum`) everywhere in this task's test — test-only change, no production code touched. `ProviderDegradedPublisher` itself is out of scope to modify. |
| 2 | AC3/AC4 overstate what a Java-only test can prove about a real TS sidecar process's signing/state access | **ACCEPTED** | Reworded: the test and its ACs prove the *Java core* (via the pre-existing, unmodified `KmsSignerArchitectureTest` rule and by construction) grants a sidecar-labeled adapter no special treatment. Cross-process guarantees for a real sidecar remain governed by L14 and sidecar build/deployment controls — explicitly out of this task's scope. |
| 3 | `anyList()`-style assertions don't prove the sidecar's answer is actually included in the evaluated set | **ACCEPTED** | The named test (and the disagreement test) capture the actual `List<ProviderAnswer<T>>` passed to `quorumDecisionService.evaluate` via `ArgumentCaptor` and assert it contains an entry whose `provider()` is the sidecar label. |
| 4 | Example `sidecar:tron` on an `ETHEREUM` watch is chain-mismatched | **ACCEPTED** | Use `sidecar-ethereum` (matching the example watch's `ETHEREUM` chain, and folding in Finding #1's colon-free fix) — the label's chain suffix should match the watch under test. |
| 5 | No coverage for sidecar as the lagging/missing provider, or reporting `exists=false` | **ACCEPTED** | Two additional test scenarios added to Required Tests: a sidecar-labeled provider marked `LAGGING` when it's the missing third answer (`sweepStaleCorrelations`), and a sidecar reporting `exists=false` correctly excluded from AMOUNT/TOKEN/CONFIRMATIONS exactly like any other provider (`logObservation`). |
| 6 | `newWatcher` overload signature unspecified | **ACCEPTED** | Pinned: `private Watcher newWatcher(long correlationWindowMs, List<ProviderSet.NamedAdapter> adapters)`; the existing `newWatcher(long)` overload is changed to delegate to it with the shared `adapters` field, so every pre-existing test's behavior is unchanged. |

## Task

Add a fake, colon-free sidecar-labeled (`sidecar-<chain>`) observation source to `WatcherTest` and prove,
across four scenarios (agreement, minority disagreement, lagging, `exists=false`), that the Java core
treats its output as just another provider answer subject to the same 2-of-3 quorum — with no quorum
authority and no special treatment (R25, L14).

## Purpose

R25/L14 describe a constraint the existing `ChainAdapter`/`ProviderSet`/`Watcher` design already
satisfies by construction (Phase 0/1; previously confirmed by T16's own Phase 12 grep-only inspection).
This task closes the gap T16 explicitly deferred: a dedicated, executable negative-proof test, scoped
accurately to what a Java-only test can actually demonstrate (Finding #2) — not to a real TypeScript
sidecar process's own signing/deployment guarantees, which remain governed by L14 and are out of scope.

## Scope

**In:**
- A sidecar-labeled `FakeChainAdapter` instance, provider name `sidecar-<chain>` (colon-free, Finding
  #1), matching the watch-under-test's own chain (Finding #4) — `sidecar-ethereum` for the existing
  `ETHEREUM`-chain `WatcherTest` fixture.
- `WatcherTest.newWatcher(long correlationWindowMs, List<ProviderSet.NamedAdapter> adapters)` — new
  overload (Finding #6); the existing `newWatcher(long)` delegates to it with the shared
  `provider-a/b/c` fields, unchanged for every pre-existing test.
- **Named test:** `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` — 2-of-3 agreement including
  the sidecar reaches `AGREED` via the real `Watcher`/`QuorumDecisionService` path; an `ArgumentCaptor`
  (Finding #3) confirms the sidecar's own answer is actually present in the evaluated
  `List<ProviderAnswer<T>>`, not merely that some `anyList()` was passed.
- **AC2 test:** sidecar in the minority of a 2-1 disagreement is flagged via
  `ProviderHealthTracker.recordDisagreement` exactly like any other minority provider, captured and
  asserted the same way.
- **Two additional scenarios (Finding #5):** sidecar as the lagging/missing third answer
  (`ProviderHealthTracker.recordUnhealthy(..., LAGGING)` for the sidecar's own name); sidecar reporting
  `exists=false` correctly excluded from AMOUNT/TOKEN/CONFIRMATIONS logging/evaluation, identically to
  a non-sidecar provider reporting the same.
- A brief documentation note (Javadoc on the new test methods) citing the pre-existing, unmodified
  `KmsSignerArchitectureTest` rule as already-sufficient structural proof that no `ChainAdapter`
  implementation can reach `kms:Sign` — scoped honestly per Finding #2 to "the Java core," not to a real
  sidecar process.

**Out:**
- Building or wiring any real TypeScript sidecar (`package.md` §2: explicitly out of scope at launch).
- Any change to `ChainAdapter`, `ProviderSet`, `Watcher`, `QuorumEvaluator`, or `ProviderDegradedPublisher`
  — confirmed unnecessary; `ProviderDegradedPublisher`'s colon ban is worked around in the test's own
  naming choice (Finding #1), not fixed in production code.
- Any new ArchUnit rule (the existing `KmsSignerArchitectureTest` rule already covers "no signing
  access" package-wide).
- Any change to the 62 pre-existing `WatcherTest` tests, their shared `provider-a/b/c` fields, or the
  existing `newWatcher(long)` call sites' observable behavior.
- Any claim, in code comments or artifacts, that this Java test proves anything about a real TypeScript
  sidecar process's own signing/deployment posture (Finding #2) — that remains L14's and sidecar
  build/deployment controls' job.

## Business Rules

- **R25.** Sidecar output is treated as just another provider answer, subject to the same 2-of-3
  quorum; it is granted no quorum authority, signing access, or business state.

## Locked Decisions

- **L14.** Sidecars are translation-only — no quorum authority, no signing access, no business state.
  No sidecar ships at launch; this task tests the guarantee, within a Java-only test's actual reach,
  against a fake stand-in.

## Dependencies

None new. Reuses `adapter.FakeChainAdapter`, `adapter.ProviderSet.NamedAdapter`, package-private
`watch.Watcher`, and `WatcherTest`'s existing mocked collaborators and `MutableClock` helper.

## Inputs

None (pure unit test, no HTTP/CLI entry point).

## Outputs

None (test-only; no production behavior changes).

## State Changes

None.

## Files to Create

None.

## Files to Modify

- `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java` — new `newWatcher(long,
  List<ProviderSet.NamedAdapter>)` overload (existing overload delegates to it); the named test plus
  three supporting test methods (minority disagreement, lagging, `exists=false`) using a
  `sidecar-ethereum`-labeled `FakeChainAdapter`.

## Files NOT to Modify

- `adapter/ChainAdapter.java`, `adapter/ProviderSet.java`, `watch/Watcher.java`, `quorum/*`,
  `provider/ProviderDegradedPublisher.java`, `provider/ProviderHealthTracker.java` — confirmed no
  production change is needed; Finding #1 is resolved entirely in the test's own naming choice.
- `attest/KmsSignerArchitectureTest.java` — cited as existing proof, not modified.
- Every existing `WatcherTest` test method and its shared `provider-a`/`provider-b`/`provider-c` fields;
  the existing `newWatcher(long)` overload's observable behavior for every current call site.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R25, named test).** `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` passes: a
  `sidecar-ethereum`-labeled provider's answer, combined with 2 non-sidecar providers agreeing, reaches
  `AGREED` via the same, unmodified `Watcher`/`QuorumDecisionService` path used by every other provider
  combination — and an `ArgumentCaptor` confirms the sidecar's own answer is genuinely present in the
  evaluated list, not merely that some list was passed (Finding #3).
- **AC2 (R25, no quorum authority — minority case).** A sidecar-labeled provider in the minority of a
  2-1 disagreement is flagged via `ProviderHealthTracker.recordDisagreement` exactly like any other
  minority provider — no exemption, verified via the same capture technique.
- **AC3 (L14, no signing access — scoped to the Java core, Finding #2).** Documented, not
  re-implemented: the pre-existing, unmodified `KmsSignerArchitectureTest` rule already forbids any
  class outside `attest` — including any `ChainAdapter` implementation regardless of provider-name
  convention — from touching the KMS SDK. This test does not, and does not claim to, prove anything
  about a real TypeScript sidecar process.
- **AC4 (L14, no business state access — scoped to the Java core, Finding #2).** True by construction:
  the sidecar-labeled `FakeChainAdapter` and its `NamedAdapter` wrapper are never given a repository or
  persistence reference at any point in the test's setup.
- **AC5 (Finding #5, completeness).** A sidecar-labeled provider that is the missing third answer is
  marked `LAGGING` via `ProviderHealthTracker.recordUnhealthy`, identically to any other provider; a
  sidecar-labeled provider reporting `exists=false` is excluded from AMOUNT/TOKEN/CONFIRMATIONS
  logging/evaluation, identically to any other provider reporting the same.

## Required Tests

- **Named test (`package.md` §8):** `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` → R25/AC1.
- **AC2 test:** sidecar-in-the-minority disagreement case.
- **AC5 tests (2):** sidecar-as-lagging-provider; sidecar-reporting-`exists=false`.

## Constraints

- **Thread-safety/transaction:** None applicable — pure unit test, no Spring context, no database.
- **Module boundaries:** New tests live in `com.themistra.crypto.watch` (required to construct the
  package-private `Watcher`), matching `WatcherTest`'s own existing placement.
- **Null handling:** N/A — no new production code.
- **Security:** None beyond what AC3 documents, honestly scoped per Finding #2.
- **Naming:** `sidecar-<chain>` (colon-free, Finding #1), chain suffix matching the watch under test
  (Finding #4) — `sidecar-ethereum` for this task's tests.

## Open Questions

No blockers.
