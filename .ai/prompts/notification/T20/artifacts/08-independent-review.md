# notification · T20 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 diff and Phase 7 self-review against the frozen brief.

---

## Finding 1 — No guard verifies that the §8 named tests exist

- **Issue:** This aligns with Phase 7 self-review S1. `everyTestNamedInTheVerificationChecklistExists` only parses citations inside §9. `package.md` §8 lists 20 named tests, and item 1 claims they all exist and pass. A rename of any §8 name (e.g., `shouldWelcomeUserOnUserRegistered`) would leave the spec pointing at nothing without failing the build.
- **Evidence:** `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java` lines 440-464; `spec/notification-service/package.md` §8.
- **Recommendation:** Add `everyTestNamedInTheTestPlanExists` that parses the backtick-quoted names in §8 and asserts each is a JUnit test method (or an ArchUnit rule field run by a canary). This also exercises the currently dead `checkedRule` branch.
- **Confidence:** Medium — the guard's purpose is to prevent spec drift, and §8 is the most likely place for rename drift.

---

## Finding 2 — The `declaresTestMethod` regex assumes `@Test` is the first annotation

- **Issue:** The regex `@(?:Test|ParameterizedTest)\b(?:\s*@[A-Za-z0-9_.]+(?:\([^)]*\))?)*\s+...` requires `@Test` to appear before any other annotation. If a test is written as `@DisplayName("...") @Test void shouldX()`, the guard would falsely report the method missing.
- **Evidence:** `T01SkeletonRegressionTest.java` lines 514-515.
- **Recommendation:** Allow annotations in any order before `void`, e.g. `(?:\s*@[A-Za-z0-9_.]+(?:\([^)]*\))?)*)\s*@(?:Test|ParameterizedTest)\b(?:\s*@[A-Za-z0-9_.]+(?:\([^)]*\))?)*\s+void ...`.
- **Confidence:** Low — current tests put `@Test` first, but the guard should not encode that convention.

---

## Finding 3 — The ArchUnit rule-field branch of `declaresTestMethod` is untested

- **Issue:** This aligns with Phase 7 self-review S2. The `checkedRule` branch handles `shouldPreventCrossModuleEntityImports`, but no §9 citation uses that form. The branch works in theory, but a bug in the regex or in how the canary calls `.check(` would not be caught.
- **Evidence:** `T01SkeletonRegressionTest.java` lines 510-518.
- **Recommendation:** If Finding 1 is accepted, the §8 guard will naturally exercise this branch because §8 lists `shouldPreventCrossModuleEntityImports` (the field name). If not, add a dedicated test that asserts the branch recognises the field/canary pair.
- **Confidence:** Low — defensive robustness.

---

## Finding 4 — The R15 end-to-end test covers only the password-reset token path

- **Issue:** This aligns with Phase 7 self-review S3. `SecretLeakEndToEndIntegrationTest.shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs` proves no reset token leaks for a password-reset event. It does not exercise the verify-email token path, which takes the same renderer to a different link.
- **Evidence:** `T01SkeletonRegressionTest.java` line 98 cites the test; Phase 7 S3.
- **Recommendation:** Extend the test to also emit a `verify_email` event with a second distinctive token and assert it is redacted from both the rendered body and the logs. `SecretSafeLoggingIntegrationTest` covers redaction in isolation, but an end-to-end log capture for verify_email is missing.
- **Confidence:** Low — the isolated redaction test gives high confidence, but R15 claims "messages or logs" for all secret-bearing events.

---

## Finding 5 — Item 1's claim "all 20 §8 names exist and pass" is not fully guarded

- **Issue:** Even if the §8 guard from Finding 1 is added, item 1 still says the names "pass." The current guards prove existence, not passing. A test could be `@Disabled` or failing and still satisfy the guard.
- **Evidence:** `spec/notification-service/package.md` §9 item 1.
- **Recommendation:** Either soften the wording to "all 20 names exist; the full suite passes" (which `mvn verify` proves), or add a guard that no cited test carries `@Disabled`. The suite run is the practical proof.
- **Confidence:** Info — the suite run is the intended proof, but the wording should match the guard.

---

## Finding 6 — No guard verifies the §11 resolved notes stay dated and attributed

- **Issue:** The Q1–Q7 resolved notes are currently accurate and dated. A future editor could remove a date or task citation while resolving a different question, and no test would fail.
- **Evidence:** `spec/notification-service/package.md` §11; `T01SkeletonRegressionTest.java`.
- **Recommendation:** Optional — add a lightweight regex guard that every `- Q<n>.` block in §11 contains a date and a task citation (e.g., `T\d+` or `2026-`). This is a low-cost documentation-integrity check.
- **Confidence:** Info — the notes are correct today.

---

## Finding 7 — No guard verifies Q8 stays open or is later resolved

- **Issue:** Q8 is left as an open follow-up about a repo-root `agents.md`. The spec bump does not tick or resolve it. A future change could silently close or drop it.
- **Evidence:** `spec/notification-service/package.md` §11 Q8.
- **Recommendation:** Either leave it as a manual follow-up and record it in the PR notes, or add a source-text assertion that Q8's line still contains "Open follow-up" or similar.
- **Confidence:** Info — administrative consistency.

---

## Summary

T20's spec bump is honest, well-cited, and well-guarded. The highest-value addition is Finding 1 (a guard for the §8 named-test list), because it closes the exact drift the task was created to prevent. Findings 2–7 are smaller robustness, coverage, and documentation-consistency notes. No production code changes are needed.
