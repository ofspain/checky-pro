<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# crypto · T01 · Phase 10 — Test Generation

## Outcome: no tests generated

Re-confirmed against the actual, final state of the task (not just the Phase 1/2 forecast):

- `package.md` §8's 29 named tests all map to R1–R28 or L15 — none names a T01-scoped behavior. No
  requirement or locked decision is *implemented* by this task; T01 is build-graph, dependency, and
  documentation work only.
- No file under `services/crypto/src/main/java` exists — there is no class, method, or endpoint for
  a unit or integration test to target.
- The one piece of genuinely runtime-observable behavior this task produces —
  `spring.threads.virtual.enabled=true` actually taking effect — has no test surface until a
  controller or `@Async`/scheduled method exists to run *on* a virtual thread and be asserted against
  (that's watcher-layer work, T09+). Asserting a raw property value in
  `application.properties` via a test would be a tautology (reading the same file the test itself
  would parse identically), not a meaningful check.
- The four things this task's own acceptance criteria actually gate — the pom resolving, the sibling
  build staying green (modulo pre-existing flakiness, Phase 9), the threat-model table's content, the
  ADR's content — were all verified directly in Phases 6/7/9 (`mvn ... validate`/`dependency:resolve`,
  file re-reads, `mvn -pl services/auth verify` x3). None of these are properties a JUnit test in this
  reactor could check that the Maven build itself doesn't already check more directly.

This matches the established precedent from auth-service's own T01-equivalent skeleton task: no
feature code, no test surface, no test generated.

---

**Phase 10 complete — no tests required, reasoning verified against final task state.** Proceed to
Phase 11 (Test Review) on approval.

## Addendum (post Phase 11) — revised: guard tests added

Kimi's Phase 11 review (`artifacts/11-test-review.md`) agreed the "no feature tests" call above is
correct, but distinguished it from a separate question this phase conflated it with: whether T01's
own acceptance criteria (AC1–AC4 — doc content, pom registration, pom dependency set, ADR linkage,
a config property) are build-gated. They were not — only checked by file reads and `mvn validate`
across Phases 6/7/9, nothing that would fail CI if one silently regressed. Human-gate decision:
add them. Wrote `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java`
— 6 plain-JUnit tests (text/content checks, no XML/Maven-model parsing dependency added), matching
`GapAnalysisDefectRegressionTest`'s (auth-service T38) established style:

1. `threatModelTracksThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched`
2. `rootPomRegistersCryptoServiceAfterAuthServiceWithOrderingComment`
3. `cryptoPomDeclaresChainClientsAndKmsWithoutTheIssuerStarter`
4. `adr0004ExistsAndScopesKmsSigningToTheAttestModule`
5. `virtualThreadsAreEnabled`
6. `sharedDependencyVersionsStayAlignedWithAuthService` (Kimi's optional 6th suggestion — included
   since it was cheap and the alignment is already intentional per Phase 5/6)

**Negative-proof discipline**: each of the 6 was individually mutation-tested — the guarded
file/value was temporarily broken, the specific test re-run to confirm it fails, then reverted
(confirmed clean via `git status`/direct re-read after every revert). Two required a same-content
different-mechanism substitution to isolate the JUnit assertion itself: mutating a real dependency
coordinate/version (tests 3 and 6) fails the build at Maven's dependency-resolution stage, before
Surefire ever runs — an even stronger guard in practice, but not evidence the assertion logic itself
is correct, so test 3's negative half was re-verified via a harmless comment insertion instead, and
test 6 was re-verified via a substitution to a different *real, resolvable* ArchUnit version
(`1.2.1` vs. the pinned `1.3.0`) so resolution succeeded and the assertion itself ran and failed.
All 6 pass clean in the current state (`mvn -pl services/crypto test`).
