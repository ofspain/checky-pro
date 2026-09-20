<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T32 · Phase 9 — Review Resolution

Consumes `artifacts/07-self-review.md` (1 Low finding) and `artifacts/08-independent-review.md`
(Kimi, 4 findings). All findings verified against actual source before disposition. femi decided
the two findings with genuine weight via human gate; the remaining two are dispositioned directly
(one reaffirms an already-frozen decision, one is accepted as documented with no action).

## Comment resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| Self-review Finding 1 | `shouldEnforcePublicEndpointAllowlist` breaks the file's snake_case convention | **Accepted as documented, no change.** Field name must match `package.md` §8's exact named-test string. | None — already documented in Phase 7. |
| Kimi Finding 1 | The rule restricts *who* may call `.permitAll()`, not *which paths* it's called with — a future in-`SecurityChainsConfig` call to an undeclared path would still pass | **REJECTED, femi's gate decision.** Accepted as a documented residual, out of scope. Matches Phase 4's own frozen AC2 wording ("no class other than SecurityChainsConfig may call permitAll()") — a narrower, already-decided guarantee. Verifying path-string arguments would need fragile bytecode dataflow analysis or a new runtime/integration test, and any such future change still requires touching `SecurityChainsConfig` itself, i.e. still goes through human review. | None. |
| Kimi Finding 2 | ArchUnit's engine doesn't execute under `mvn test` in this environment, so `shouldEnforcePublicEndpointAllowlist` currently enforces nothing in a real build | **ACCEPTED, femi's gate decision.** Added a plain JUnit Jupiter test (proven to execute under Surefire) that re-imports the same classes `@AnalyzeClasses` would and directly invokes the existing rule's own `.check(...)` — narrowly closes the CI-enforcement gap for *this task's own rule* without attempting to fix the broader Surefire/ArchUnit engine-integration issue (still tracked as a separate follow-up). | `ArchitectureTest.java`: added `shouldEnforcePublicEndpointAllowlistActuallyRunsUnderThisBuild()` (new imports: `JavaClasses`, `ClassFileImporter`). Re-verified with a fresh negative-proof run: reintroduced the same scratch `.permitAll()` violation used in Phase 6 — this time `mvn test -Dtest='ArchitectureTest'` correctly produced **BUILD FAILURE** with the exact violation message. Reverted the scratch probe; confirmed clean build afterward (`git status` shows only `ArchitectureTest.java` modified). |
| Kimi Finding 3 | Other exposure vectors (`WebSecurityCustomizer.ignoring()`, `@PermitAll`) aren't covered | **REJECTED, reaffirms an already-frozen decision.** Identical to Phase 3 Finding 3, already resolved at the Phase 4 gate: scope stays narrow to `.permitAll()` per the task's literal wording; neither mechanism exists anywhere in this codebase today. No new information in Kimi's restatement to warrant reopening. | None. |
| Kimi Finding 4 | The rule is brittle if security wiring is ever extracted into a helper class outside `SecurityChainsConfig` | **Accepted as documented limitation, no action.** Kimi's own confidence is Low; a legitimate future refactor moving permit-list logic elsewhere would correctly need this rule updated too — that's the rule working as designed (an explicit, visible failure demanding a conscious update), not a silent gap. | None. |

## Summary

One code change applied: a plain-JUnit canary test added to `ArchitectureTest.java` that makes
`shouldEnforcePublicEndpointAllowlist` (the task's own named test) actually gate `mvn test` today,
independent of the separate, still-open Surefire/ArchUnit engine-integration issue. No production
code changed. `apiKeysTokenExchangeIsInThePublicAllowlist` (AC1) is unaffected.

Verification after the change: `mvn clean test-compile` clean; `mvn test -Dtest='ArchitectureTest'`
green (3 JUnit-Jupiter-engine tests: the two from Phase 6 plus the new canary); a fresh
negative-proof rerun (reintroduce → confirm real `BUILD FAILURE` → revert → confirm clean) passed
exactly as expected.

---

**Phase 9 complete — resolution log written; sign-off recorded.** Proceed to Phase 10 (Test
Generation) on approval.
