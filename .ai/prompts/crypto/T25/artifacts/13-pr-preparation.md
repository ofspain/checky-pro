# crypto · T25 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Enforce cross-module entity boundaries with a new ArchUnit rule (T25)
```

## Commit message

```
Enforce cross-module entity boundaries with a new ArchUnit rule (T25)

Closes the last of three module-boundary/security guarantees this
spec package requires. Two of the three - the KMS-signer package ban
(R22/L11, KmsSignerArchitectureTest) and the internal-scope
requirement on watch/attest endpoints (R27,
ResourceServerConfigIntegrationTest) - already existed, built in T20
and T03 respectively; this task confirms both remain intact,
unmodified, still passing. The third, L15 ("no feature module imports
another feature module's entity"), had no equivalent anywhere: the 6
existing per-module boundary tests are plain text-based source scans,
explicitly noted in their own code as a T09 stand-in ("deferred
introducing ArchUnit itself to a future dedicated task") - this is
that task, scoped specifically to L15's own narrower concern.

CrossModuleEntityArchitectureTest mirrors auth-service's own
ArchitectureTest.shouldPreventCrossModuleEntityImports technique
exactly (L15's own text: "mirroring the auth service"): a
FEATURE_MODULES list, a featureModuleOf(JavaClass) helper, an
ArchCondition over getDirectDependenciesToSelf() (dependency-based,
not access-based, so a declared-but-unused field of a forbidden
entity type is still caught), and a plain-@Test canary, since
@ArchTest fields don't execute under this repo's Surefire setup.

Running the new rule for real, for the first time, against the actual
codebase surfaced 2 genuine pre-existing cross-module entity couplings
predating this task (AttestationService using watch's ChainCursor;
Watcher using quorum's QuorumDecision) - not bugs, but real violations
of L15 as literally stated. Rather than silently expanding this task's
scope into an unrelated production refactor, or leaving the rule
permanently red, both are resolved as a narrow, named allowlist
(mirroring auth's own identical pattern for its controller-service
rule), approved explicitly by the author. The allowlist is guarded by
its own regression test - if either coupling is ever removed without
updating the allowlist, the guard fails loudly rather than leaving
silent dead configuration.

The rule earns its trust from two directions, not one: a negative-proof
fixture placed genuinely inside a real feature module (watch) proves
the entity-mismatch violation path fires, and a second fixture placed
entirely outside every known module proves the separate fail-fast path
also fires rather than silently under-enforcing - both added only
after review caught that the first version of the first fixture would
have proven the wrong path entirely.

No production code changed anywhere in this task - three new test/
fixture files are the entire diff.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created:**
- `services/crypto/src/test/java/com/themistra/crypto/common/CrossModuleEntityArchitectureTest.java` (+208 lines)
- `services/crypto/src/test/java/com/themistra/crypto/watch/RogueWatchEntityReferencer.java` (+19 lines)
- `services/crypto/src/test/java/archtestfixtures/RogueUnmappedEntity.java` (+16 lines)

**Confirmed unmodified (re-run only):**
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/common/ResourceServerConfigIntegrationTest.java`

**3 files changed, +243 lines.** No production code, no migration, no schema change, no contract change
— this task adds enforcement/test coverage only.

## Summary

Closes a gap T09 itself explicitly deferred: `crypto-service` had no real ArchUnit rule enforcing L15's
"no feature module imports another feature module's entity," only 6 hand-rolled, per-module text scans
covering a different, broader concern. This task adds that rule, mirroring auth-service's own
established technique exactly, and — unlike a purely mechanical port — actually ran it against the real
codebase before declaring success.

That real run mattered: it surfaced 2 genuine, previously-undetected architectural facts about this
codebase (an `attest`-to-`watch` and a `watch`-to-`quorum` entity coupling, both several tasks old).
Rather than silently patching over them, silently expanding scope into an unrelated refactor, or
shipping a permanently-red rule, the finding was brought to the author directly with full evidence, and
resolved as an explicit, narrow, regression-guarded exception — the same pattern auth-service itself
already uses for its own analogous rule. The rule still catches anything new; only these two named,
approved, tracked exceptions are exempt.

The review pipeline (Phases 3, 8, 11) caught and fixed a genuine design flaw before it shipped: the
first version of the negative-proof fixture would have been placed *outside* every known feature module,
which would have proven only the rule's fail-fast path, never the actual cross-module-entity violation
L15 targets. Caught at Phase 3, before any code was written. Two further review rounds added a
regression guard for the allowlist itself, closed a rename-safety gap for free as a side effect of that
guard, and added a second, complementary negative-proof for the fail-fast path this first fixture no
longer covered once it was correctly relocated.

## Testing performed

- `mvn -pl services/crypto -am test-compile` — clean, on every verification run throughout this task.
- `mvn -pl services/crypto test -Dtest=CrossModuleEntityArchitectureTest,KmsSignerArchitectureTest,ResourceServerConfigIntegrationTest`
  — 17/17 pass (4 in `CrossModuleEntityArchitectureTest`: the named test, both negative-proofs, and the
  allowlist regression guard; 2 in `KmsSignerArchitectureTest`; 11 in `ResourceServerConfigIntegrationTest`,
  the latter two cited unmodified).
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`:
  `artifacts/12-specification-verification.md` — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 25 ("ArchUnit/module boundaries").
- **Requirements:** R22 (KMS-signer package ban, confirmed unmodified), R27 (internal-scope
  requirement, confirmed unmodified).
- **LOCKED decisions:** L11 (confirmed unmodified), L15 (the clause this task enacts).
- **Flagged, not fixed, for a future task:** the 2 allowlisted cross-module entity couplings
  (`AttestationService`→`ChainCursor`, `Watcher`→`QuorumDecision`) are real, standing technical debt —
  a future task should pass a derived value or small DTO instead of the entity itself, after which the
  allowlist can be emptied and L15 enforced with zero exceptions. Also flagged: the recurring
  branch-poisoning issue affecting this branch's development process (6 occurrences across T24-T25 in
  this session), an external process risk unrelated to this task's own content.
