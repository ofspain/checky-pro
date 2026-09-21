# crypto · T25 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / Locked Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R22/L11 — `kms:Sign` reachable only from attest path | Yes, confirmed unmodified | `attest/KmsSignerArchitectureTest.java` (built T20, untouched by T25) | `shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild`, `bothRulesActuallyFailAgainstAGenuineViolation` — 2/2 pass | No | None. |
| R27 — internal watch/attest endpoints require `internal.crypto:write` | Yes, confirmed unmodified | `common/ResourceServerConfigIntegrationTest.java` (built T03, untouched by T25) | `shouldRequireInternalScopeForWatchAndAttestEndpoints_*` (3 variants × 3 routes) — 11/11 pass | No | None. |
| L15 — no feature module imports another feature module's entity | Yes, new | `common/CrossModuleEntityArchitectureTest.java` (all new) | `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (named test), `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation`, `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule`, `allowlistedCrossModuleEntityDependenciesStillExistInCode` — 4/4 pass | No | **Documented, approved exception:** 2 pre-existing cross-module entity couplings (`AttestationService`→`ChainCursor`, `Watcher`→`QuorumDecision`, both predating this task) are explicitly, narrowly allowlisted rather than removed — see Phase 6/9 for the discovery and explicit user-approved disposition. |
| Phase 3/4 Finding #1 (colon-free naming — N/A, carried over from T24 context, not this task) | N/A | — | — | — | Not applicable to T25. |
| Phase 3/4 Finding #2 (negative-proof fixture placement) | Yes | `watch/RogueWatchEntityReferencer.java` — placed *inside* `watch`, not a standalone package | `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` exercises the real `entityModule != dependingModule` path | No | None — corrects a copy-paste error the frozen brief itself had inherited from T20's precedent path. |
| Phase 3/4 Finding #3 (`common` gets no exemption) | Yes | `CrossModuleEntityArchitectureTest.java`'s own `featureModuleOf`/`ArchCondition` — no special-casing for `common` anywhere | Implicitly proven by the null-equality logic; explicitly proven by `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule` sharing the identical code path | No | None. |
| Phase 8/9 Finding #1 (allowlist regression guard) | Yes | `allowlistedCrossModuleEntityDependenciesStillExistInCode` | Same | No | None. |
| Phase 11 Finding #1 (fail-fast negative-proof) | Yes | `archtestfixtures/RogueUnmappedEntity.java` | `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule` | No | None. |
| Phase 11 Finding #4 (message-specificity) | Yes | `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation`'s strengthened assertion | Same | No | None. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. All three named tests (`shouldOnlyAllowAttestPathToInvokeKmsSign`,
`shouldRequireInternalScopeForWatchAndAttestEndpoints`, `shouldPreventCrossModuleEntityImports`) exist
and pass. The first two were verification-only, per Phase 0/1's own finding that they were already fully
built in T20/T03; the third is this task's own genuinely new deliverable, built, reviewed across Phase
3/8/11, and strengthened at each review round with verified, not assumed, reasoning.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1-AC4 (frozen brief) all have direct
evidence and passing tests. AC3's "zero violations" requirement was not trivially true on first run: the
real rule, run for real against the actual codebase, surfaced 2 genuine pre-existing cross-module
entity couplings. This was not silently smoothed over — it was surfaced to the user directly, with full
evidence, and resolved via an explicit, human-approved, narrowly-scoped allowlist that keeps the rule
meaningful for any future violation while documenting the two known ones.

**(3) Does it violate any LOCKED decision?** L11/R22 and R27 are unmodified and still enforced exactly
as before. L15's own text ("no feature module imports another feature module's entity") is not violated
silently — the two exceptions are named, narrow, documented in three places (this file, the test's own
Javadoc, `06-implementation-notes.md`), and guarded by a regression test that fails loudly if either
coupling is ever removed without updating the allowlist. No file outside this task's own two authorized
new files was modified — confirmed via `git diff --stat` against the pre-T25 baseline.

**(4) Remaining risks?**
- **The 2 allowlisted couplings are real, standing technical debt**, not fixed by this task (production
  code changes were explicitly out of scope). A future task should decouple `AttestationService` from
  `ChainCursor` and `Watcher` from `QuorumDecision` (e.g., by passing a derived value or a small
  DTO/projection instead of the entity itself), after which the allowlist can be emptied and L15
  enforced with zero exceptions.
- **The recurring branch-poisoning issue, unrelated to this task's own content**, affected this task's
  own development process directly: the external process generating Kimi review commits repeatedly
  re-merged a stale, already-reverted state of this branch (6 separate occurrences during T24-T25 in
  this session), each requiring a diagnose-backup-reset-recreate-verify-push cycle. All 6 were resolved
  cleanly with no work lost, but this remains an open, flagged, unresolved process risk for any future
  task on this branch.
- **No new risk was introduced by this task** — the new rule strictly adds enforcement (catching any
  future cross-module entity coupling beyond the 2 named exceptions); nothing it touches can regress
  existing behavior, since it's a pure static-analysis test with zero runtime/production footprint.

## Verdict

**PASS** — R22/L11 and R27 remain fully intact and verified unmodified; L15 now has a real, working,
independently-verified ArchUnit rule with a genuine negative-proof (proven by directly triggering it,
twice — once for the entity-mismatch path, once for the fail-fast path) and a regression guard against
its own allowlist going stale. The one deviation from L15's literal zero-exception text is a
deliberate, narrow, human-approved, and fully documented exception for 2 pre-existing couplings — not a
silent gap. 17/17 targeted tests pass.
