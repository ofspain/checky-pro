# crypto · T25 · Phase 9 — Review Resolution

Human-approved dispositions for the Phase 7 self-review and Phase 8 (Kimi) independent review, which
overlapped on 2 of 5 total findings.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | `ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES` has no "still exists in code" regression guard (Self-Review #1 = Phase 8 #1) | **ACCEPTED** | Added `allowlistedCrossModuleEntityDependenciesStillExistInCode`, mirroring auth's own `allowlistedControllerServiceDependenciesStillExistInCode` exactly — asserts `AttestationService` still depends on `ChainCursor` and `Watcher` still depends on `QuorumDecision`, failing loudly if either is no longer true. |
| 2 | `Watcher`'s allowlist entry is a plain string literal — a rename would silently stop matching (Phase 8 #2) | **ACCEPTED, resolved by #1's fix** | The new guard test's `analyzedClasses.get("com.themistra.crypto.watch.Watcher")` lookup throws `IllegalArgumentException` the moment that fully-qualified name no longer resolves to a real class — a rename now fails this test loudly rather than silently breaking the allowlist. No production change needed, as Kimi's own recommendation noted. |
| 3 | Inline fully-qualified `TokenAllowlist` reference instead of an import (Self-Review #2 = Phase 8 #3) | **ACCEPTED** | Added `import com.themistra.crypto.token.TokenAllowlist;`, replaced the inline reference. |
| 4 | Allowlisted dependencies have no tracked next step (TODO/ticket) | **ACCEPTED (lightly)** | Sharpened the allowlist's own Javadoc to explicitly state no tracking issue exists in this repository today, and to point at this task's own artifacts (`06-implementation-notes.md`, `12-specification-verification.md`) as where the follow-up candidate is recorded — no fabricated ticket ID, since this repo has no ticket tracker integrated. |
| 5 | Negative-proof only exercises a field-dependency shape, not a method parameter/return type | **ACKNOWLEDGED, not implemented** | Kimi's own finding explicitly calls this optional. The field-dependency shape is sufficient to prove the rule's own logic (`ArchCondition` checks only `getOriginClass()`/module identity, never the dependency's syntactic shape); the real Phase 6 first-run violations already independently proved method-parameter/return-type/method-call dependency shapes are caught by the same underlying `getDirectDependenciesToSelf()` API, before the allowlist even existed — a second fixture would add marginal confidence at real cost, not close a real gap. |

## Files changed in this phase

- `services/crypto/src/test/java/com/themistra/crypto/common/CrossModuleEntityArchitectureTest.java` —
  added the regression-guard test + its `hasDirectDependency` helper (Findings #1/#2), replaced the
  inline FQN reference with an import (Finding #3), sharpened the allowlist's Javadoc (Finding #4). No
  production code changed.

## Verification

`mvn -pl services/crypto test -Dtest=CrossModuleEntityArchitectureTest,KmsSignerArchitectureTest,ResourceServerConfigIntegrationTest`
— 16/16 pass (3 in `CrossModuleEntityArchitectureTest`, up from 2 before this phase's 1 new test; 2 in
`KmsSignerArchitectureTest`; 11 in `ResourceServerConfigIntegrationTest`, both cited unmodified).
