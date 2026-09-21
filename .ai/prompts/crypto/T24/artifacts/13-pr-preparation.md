# crypto · T24 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Prove sidecar output is just another provider answer to the quorum core (T24)
```

## Commit message

```
Prove sidecar output is just another provider answer to the quorum
core (T24)

Closes the gap T16's own Phase 12 explicitly deferred here: R25/L14
describe a constraint the ChainAdapter/ProviderSet/Watcher design
already satisfies by construction (any implementation - real,
sidecar-backed, or fake - is indistinguishable to the quorum core),
but T16 only ever confirmed this by source inspection, not by a
dedicated, executable negative-proof test.

Adds a sidecar-ethereum-labeled FakeChainAdapter to WatcherTest,
replacing provider-c across five scenarios: 2-of-3 agreement (the
named test, shouldTreatSidecarOutputAsJustAnotherProviderAnswer),
minority disagreement, majority (the sidecar grants no immunity to a
genuinely disagreeing minority provider), lagging, and exists=false
exclusion. Each disagreement/agreement test captures the actual
evaluated list via ArgumentCaptor and asserts the sidecar's own answer
is genuinely present, not merely that some anyList() was passed. The
named test stubs an AGREED decision and asserts the downstream
txLifecyclePublisher.seen(...) fires, proving the full path end to
end.

sidecar-ethereum, not sidecar:ethereum: design.md's own DDL comment
suggests a colon-separated sidecar naming convention, but
ProviderDegradedPublisher (a later task) rejects any colon-bearing
provider name to keep its own idempotency-key concatenation
unambiguous. Kimi's design review caught this before any code was
written - worked around entirely in this test's own naming choice,
never touching that production class.

AC3 (no signing access) and AC4 (no business state access) are
deliberately not new tests - AC3 is already proven, package-wide, by
the pre-existing, unmodified KmsSignerArchitectureTest rule; AC4 holds
true by construction (the sidecar adapter is never given a repository
reference). Both are honestly scoped in the new tests' own
documentation to what a Java-only unit test can actually prove -
neither this test nor any Java code can certify a real TypeScript
sidecar process's own signing/deployment posture, which remains L14's
and sidecar build/deployment controls' job.

No production code changed anywhere in this task - WatcherTest.java is
the only file touched across all 14 phases.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Modified:**
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java` (+172 lines)

**1 file changed, +172 lines.** No production code, no migration, no schema change, no contract change
— this task adds test coverage only, for a guarantee the existing production code already provides.

## Summary

Closes a gap disclosed by T16's own Phase 12: `crypto-service`'s adapter/watcher/quorum architecture was
already designed so that no code path can distinguish a sidecar-backed `ChainAdapter` from a real or
fake one, but this was only ever confirmed by reading the source, never by a dedicated, executable test.
This task adds that test — five scenarios proving a sidecar-labeled provider is treated identically to
any other provider across agreement, both directions of disagreement, lagging, and negative-existence
answers, with `ArgumentCaptor`-based inclusion proofs rather than loose `anyList()` matching.

The review pipeline caught one design-time defect before any code was written: the naming convention
`design.md` itself suggests for sidecars (`sidecar:<chain>`) would crash a later task's production code
(`ProviderDegradedPublisher`'s colon-rejection guard) the moment a sidecar-labeled provider ever
disagreed or went unhealthy. Caught at Phase 3 (Kimi's design challenge), verified directly against
source, and resolved entirely in the test's own naming choice — `sidecar-ethereum`, never touching the
production class it would have broken.

The independent and test review phases (8, 11) together raised 10 recommendations; 5 were accepted and
implemented (strengthening the named test to prove the full `AGREED` path, adding a majority-side
disagreement test, adding logging-exclusion assertions, and adding two inclusion-proof captures for
symmetry), and 5 were rejected — two of them only after being actively checked against reality rather
than accepted or dismissed on the reviewer's word alone: one suggestion was directly disproven by
temporarily adding it and running the test (it asserted a call that provably never happens, regardless
of provider identity, because `Watcher.evaluateFact` requires exactly 3 qualifying answers); another was
shown to be inconsistent with an established convention already followed by 60+ of this file's other
tests, not a gap introduced by this task.

A recurring infrastructure issue was also found and resolved three separate times during this task: an
external process kept re-merging a stale, already-reverted commit (bringing in an entirely separate,
incompatible parallel implementation of this service from a different branch) into this branch. Each
occurrence was diagnosed, reverted cleanly (preserving all real work), and verified to compile and pass
before continuing — flagged plainly in `12-specification-verification.md` as an unresolved, out-of-scope
process risk for future phases and tasks.

## Testing performed

- `mvn -pl services/crypto -am test-compile` — clean, on every verification run throughout this task
  (including three re-verifications after reverting the recurring branch-poisoning merge).
- `mvn -pl services/crypto test -Dtest=WatcherTest,KmsSignerArchitectureTest` — 69/69 pass (67 in
  `WatcherTest`: 62 pre-existing + 5 new, all pre-existing tests re-run unmodified and green; 2 in
  `KmsSignerArchitectureTest`, cited unmodified).
- Full module regression (`mvn -pl services/crypto -am test`) attempted once (Phase 6): 0 real failures,
  13 errors, all traced directly to Docker being unavailable in that environment at that time
  (confirmed via `docker info` and by reading every erroring class's surefire report) — unrelated to
  this task, since neither file this task's tests touch depends on Testcontainers.
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`:
  `artifacts/12-specification-verification.md` — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 24 ("Sidecar-as-provider test").
- **Requirements:** R25 (sidecar output is just another provider answer, no quorum authority/signing/
  state).
- **LOCKED decisions:** L14 (sidecars are translation-only).
- **Flagged, not fixed, for a future task:** the `ProviderAnswer` class collision between this branch's
  spec-driven implementation and a separate, parallel implementation on `main`/`feat/crypto-quorum-and-
  evm-adapter` — a project-level reconciliation decision, out of this task's own scope, that will keep
  causing this exact branch-poisoning pattern to recur until resolved.
