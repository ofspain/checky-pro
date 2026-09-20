# crypto · T24 · Phase 2 — Task Implementation Brief

## Task

Add a fake sidecar observation source and prove, with a dedicated test, that the Java core treats its
output as just another provider answer subject to the same 2-of-3 quorum — with no quorum authority, no
signing access, and no business state (R25, L14).

## Purpose

R25/L14 describe a constraint the existing `ChainAdapter`/`ProviderSet`/`Watcher` design already
satisfies by construction (confirmed by direct source reading at Phase 0/1, and previously confirmed by
grep-only inspection at T16's own Phase 12). No sidecar ships at launch (`package.md` §2). This task
closes the one gap T16 explicitly deferred: a dedicated, executable negative-proof test, rather than
source inspection alone, that a sidecar-labeled provider is genuinely indistinguishable from any other.

## Scope

**In:**
- One new test proving a sidecar-labeled `FakeChainAdapter` instance (provider name following
  `design.md`'s own DDL-comment convention, `sidecar:<chain>`, e.g. `sidecar:tron`) participates in
  `Watcher`'s real quorum/health/event pipeline identically to a non-sidecar-named provider:
  - 2-of-3 agreement including the sidecar reaches `AGREED`, same as any other 2-of-3 grouping.
  - The sidecar in the minority of a 2-1 split is health-tracked as disagreeing exactly like any other
    minority provider — no exemption.
  - The sidecar counts as exactly one of the three required answers — no override, no tie-break
    privilege, no special weighting.
- A brief documentation note (in the test's own Javadoc, following this codebase's established
  documentation-in-code convention) citing the pre-existing, unmodified `KmsSignerArchitectureTest`
  package-wide rule as the already-sufficient structural proof that no `ChainAdapter` implementation —
  sidecar-labeled or not — can reach `kms:Sign` (AC3/L14 "no signing access"). No new ArchUnit rule.
- Implicit proof of "no business state access" (AC4/L14) via the test's own construction: the
  sidecar-labeled `FakeChainAdapter` is given no repository reference anywhere in its construction or
  in `Watcher`'s wiring — the same true-by-construction property every other `FakeChainAdapter` in
  `WatcherTest` already relies on, made explicit in this test's own commentary rather than
  re-implemented as a separate assertion.

**Out:**
- Building or wiring any real TypeScript sidecar (`package.md` §2: explicitly out of scope at launch).
- Any change to `ChainAdapter`, `ProviderSet`, `Watcher`, `QuorumEvaluator`, or any other production
  class — Phase 0/1 confirmed none is needed.
- Any new ArchUnit rule (the existing `KmsSignerArchitectureTest` rule already covers AC3 package-wide).
- Any change to the 62 pre-existing `WatcherTest` tests or their shared `provider-a/b/c` fixture fields.

## Business Rules

- **R25.** Sidecar output is treated as just another provider answer, subject to the same 2-of-3
  quorum; it is granted no quorum authority, signing access, or business state.

## Locked Decisions

- **L14.** Sidecars are translation-only — no quorum authority, no signing access, no business state.
  No sidecar ships at launch; this task tests the guarantee against a fake stand-in only.

## Dependencies

None new. Reuses `adapter.FakeChainAdapter`, `adapter.ProviderSet.NamedAdapter`, package-private
`watch.Watcher` (test must live in `com.themistra.crypto.watch` to construct it directly), and
`WatcherTest`'s existing mocked collaborators (`ObservationLog`, `QuorumDecisionService`,
`ProviderHealthTracker`, `ChainCursorRepository`, `TxLifecyclePublisher`, `ReorgDetector`,
`SimpleMeterRegistry`) and its private `MutableClock` helper.

## Inputs

None (no HTTP/CLI entry point — this is a pure unit test).

## Outputs

None (test-only; no production behavior changes).

## State Changes

None.

## Files to Create

None.

## Files to Modify

- `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java` — add the named test
  `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` plus its supporting assertions, using a
  sidecar-labeled `FakeChainAdapter` as one of three `NamedAdapter`s in a locally-constructed `Watcher`
  (via a new overload of the existing `newWatcher(...)` helper accepting an explicit adapters list,
  leaving the existing overload and the shared `provider-a/b/c` fields untouched for every pre-existing
  test).

## Files NOT to Modify

- `adapter/ChainAdapter.java`, `adapter/ProviderSet.java`, `watch/Watcher.java`, `quorum/*` — Phase 0/1
  confirmed no production change is needed.
- `attest/KmsSignerArchitectureTest.java` — cited as existing proof, not modified.
- Every existing `WatcherTest` test method and its shared `provider-a`/`provider-b`/`provider-c` fields.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R25, named test).** `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` passes: a sidecar-
  labeled provider's answer, combined with 2 non-sidecar providers agreeing, reaches `AGREED` via the
  same, unmodified `Watcher`/`QuorumDecisionService` path used by every other provider combination.
- **AC2 (R25, no quorum authority).** A sidecar-labeled provider in the minority of a 2-1 disagreement
  is flagged via `ProviderHealthTracker.recordDisagreement` exactly like any other minority provider —
  no exemption, no extra weight, verified by a second test scenario.
- **AC3 (L14, no signing access).** Documented, not re-implemented: the pre-existing, unmodified
  `KmsSignerArchitectureTest` rule already forbids any class outside `attest` — including any
  `ChainAdapter` implementation regardless of provider-name convention — from touching the KMS SDK.
- **AC4 (L14, no business state access).** True by construction: the sidecar-labeled
  `FakeChainAdapter` and its `NamedAdapter` wrapper are never given a repository or persistence
  reference at any point in the test's setup — documented inline, not separately asserted.

## Required Tests

- **Named test (`package.md` §8):** `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` → R25/AC1.
- One additional test method for AC2 (sidecar-in-the-minority disagreement case) — not itself the named
  test, but required to cover "no quorum authority" beyond the agreement-path case alone.

## Constraints

- **Thread-safety/transaction:** None applicable — pure unit test, no Spring context, no database.
- **Module boundaries:** The new test lives in `com.themistra.crypto.watch` (required to construct the
  package-private `Watcher`), matching `WatcherTest`'s own existing placement — no new cross-module
  dependency introduced.
- **Null handling:** N/A — no new production code.
- **Security:** None beyond what AC3 documents (KMS SDK access already structurally impossible for any
  `ChainAdapter` implementation).
- **Naming:** The sidecar-labeled provider's name must follow `design.md`'s own DDL-comment convention
  (`sidecar:<chain>`, e.g. `sidecar:tron`) rather than an arbitrary label, so the test's intent is
  self-evident and consistent with the one place this convention is already documented.

## Open Questions

No blockers.
