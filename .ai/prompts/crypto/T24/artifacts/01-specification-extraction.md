# crypto · T24 · Phase 1 — Specification Extraction

## Business Rules

- **R25.** Where a TypeScript chain sidecar supplies an observation, the system SHALL treat that
  observation as just another provider answer, subject to the same 2-of-3 quorum, and SHALL NOT grant
  it quorum authority, signing access, or business state.

## Locked Decisions

- **L14.** Sidecars are translation-only: one chain, one process, disposable — no quorum authority, no
  signing access, no business state (`docs/service-languages.pdf` §3.2, not read directly per this
  phase's scoped file list). The Java core treats sidecar output as one more provider answer subject to
  quorum. No sidecar ships at launch (`package.md` §2, "Explicitly Out of Scope") — this task tests the
  architectural guarantee against a fake stand-in, it does not build or wire a real sidecar.

## Files involved

**Existing, read-only source of truth (from Phase 0):**
- `adapter/ChainAdapter.java` — the interface every provider (real, sidecar, or fake) implements; its
  own Javadoc already names all three as equally valid.
- `adapter/ProviderSet.java` (`ProviderSet.NamedAdapter`) — the one place a concrete adapter's identity
  is captured; its own Javadoc states the L14/AC7 constraint verbatim.
- `watch/Watcher.java` — the consumer this task must prove treats every `NamedAdapter` identically;
  read in full at Phase 0, confirmed to contain no `instanceof`/type-switch on any `ChainAdapter`
  implementation.
- `quorum/QuorumEvaluator.java`, `quorum/QuorumDecisionService.java`, `quorum/ProviderAnswer.java` —
  the quorum arithmetic itself; confirmed to operate only on `Comparable` values and opaque
  provider-name strings, with no weighting concept.
- `provider/ProviderHealthTracker.java` — health/disagreement tracking keyed only by provider name.
- `attest/KmsSignerArchitectureTest.java` — the existing, pre-existing ArchUnit rule already forbidding
  any class outside `attest` from touching the KMS SDK (package-wide, already covers any `adapter`
  implementation without modification).
- `adapter/FakeChainAdapter.java` (test-only) — the existing scripted fake, already used by
  `WatcherTest`'s 62 tests, constructed as `new FakeChainAdapter(Chain, String providerName)`.
- `watch/WatcherTest.java` — the direct precedent harness: three named `FakeChainAdapter`s wired into a
  real `Watcher` via `List<ProviderSet.NamedAdapter>`, everything else mocked, a `MutableClock`, and a
  `deliver(...)` helper.
- `design.md`'s `observations` table DDL — `provider VARCHAR(64)`, comment gives the naming convention
  `alchemy | quicknode | trongrid | sidecar:solana`: no dedicated "is-sidecar" column or flag exists
  anywhere in the schema.

**New, this task's own deliverable (exact shape a Phase 2 decision):**
- One new test (class and/or method) implementing the named test
  `shouldTreatSidecarOutputAsJustAnotherProviderAnswer`, most likely alongside or extending
  `WatcherTest`'s existing harness, using a `FakeChainAdapter` instance named with a
  `sidecar:<chain>`-style provider name as one of the three configured providers.
- No new production code is anticipated — R25/L14 describe a constraint the existing `ChainAdapter`/
  `ProviderSet`/`Watcher` design already satisfies by construction (per Phase 0 and T16's own Phase 12
  verification); this task is confirmatory, not additive, at the production-code level.

## Dependencies

None new. Reuses `adapter.FakeChainAdapter`, `adapter.ProviderSet.NamedAdapter`, `watch.Watcher`
(package-private — the new test must live in `com.themistra.crypto.watch` to construct one directly, or
use whatever public seam `WatcherTest` itself uses), and the same mock/fixture set `WatcherTest` already
established (`ObservationLog`, `QuorumDecisionService`, `ProviderHealthTracker`,
`ChainCursorRepository`, `TxLifecyclePublisher`, `ReorgDetector`, `SimpleMeterRegistry`, `MutableClock`).

## Acceptance Criteria

1. **AC1 (R25, named test).** `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` passes: a fake
   sidecar-labeled provider's `TxResult`/observation participates in quorum evaluation identically to a
   non-sidecar provider — e.g., 2 real providers + 1 sidecar agreeing reaches `AGREED`; a sidecar in the
   minority of a 2-1 split is treated exactly like any other minority provider (health/disagreement
   tracking, no special exemption or extra weight).
2. **AC2 (R25, no quorum authority).** The sidecar's answer counts as exactly one of the three required
   answers — same `QuorumEvaluator`/`QuorumDecisionService` path, no branch, override, or
   tie-breaking privilege tied to its identity.
3. **AC3 (L14, no signing access).** No code path exists by which a `ChainAdapter` implementation
   (sidecar-labeled or not) can reach `kms:Sign` — already enforced by the existing, unmodified
   `KmsSignerArchitectureTest` package-wide rule; this task's job is to confirm (not re-implement) that
   this holds for a sidecar-labeled instance too, since the rule is structural and does not special-case
   provider names.
4. **AC4 (L14, no business state access).** The sidecar-labeled `FakeChainAdapter` is given no
   repository, no `Watch`/`ChainCursor`/`QuorumDecision` reference, and no wiring path to acquire one —
   it can only ever produce `TxResult`/`TokenInfo`/`FinalityStatus` values through the `ChainAdapter`
   interface, exactly like every other provider.

## Tests required

- **Named test (`package.md` §8):** `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` → R25.
- No additional ArchUnit rule is anticipated to be *required* (the existing `KmsSignerArchitectureTest`
  rule already covers AC3 package-wide) — whether to add one anyway, scoped specifically to
  `adapter`/sidecar naming, for documentation/regression-lock value, is a Phase 2 design decision, not
  a hard requirement of R25/L14's own text.

## Open Questions

None are genuine blockers. Two Phase 2 design decisions, both already narrowed by Phase 0/1 findings:

- **Whether the sidecar fixture is a new named-constant usage of the existing `FakeChainAdapter`
  (e.g. `new FakeChainAdapter(Chain.TRON, "sidecar:tron")`) or a new, distinct test-fixture class** —
  the existing class's constructor already supports the former with zero code change, and nothing in
  R25/L14/`package.md`/`design.md` calls for a behaviorally different fake; a new class would only be
  justified if the design phase wants to name the concept explicitly in the type system rather than by
  string convention alone.
- **Whether a new, sidecar-specific ArchUnit rule is worth adding for regression-lock value**, given the
  existing `KmsSignerArchitectureTest` rule is already sufficient and package-wide (verified in Phase 0,
  re-confirmed here) — not required by any acceptance criterion, a proportionality call for Phase 2.
