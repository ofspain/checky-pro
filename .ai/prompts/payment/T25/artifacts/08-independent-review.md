# payment · T25 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 implementation and Phase 7 self-review against the frozen brief (`artifacts/04-frozen-task-brief.md`), `spec/payment-service/agents.md`, and the existing `ArchitectureTest`/`PublicEndpointsTest`.

---

## Finding 1 — Violation canary assertion is weaker than it needs to be

- **Issue:** `shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation` asserts the failure message contains `"permitAll"`, but the rule's own description always contains that word. The discriminating assertion is `hasMessageContaining("RoguePermitAllUser")`. The `"permitAll"` check adds no value and could give a false sense of specificity.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` lines 376-385.
- **Recommendation:** Replace the `permitAll` assertion with one that pins the offending method, e.g., `hasMessageContaining("RoguePermitAllUser.openUp")`, or drop the redundant assertion and keep only the class-name check.
- **Confidence:** Low — the test still fails correctly, but the assertion quality can be improved.

---

## Finding 2 — Rule name over-promises what it enforces

- **Issue:** The rule is named `shouldEnforcePublicEndpointAllowlist`, but it only bans `permitAll()` calls outside `ResourceServerConfig`. It does not verify the allowlist contents, the single-scoped `permitAll()` inside `ResourceServerConfig`, or runtime 401 behavior — those are `PublicEndpointsTest`'s job. The Javadoc now clarifies this (Phase 9), but the rule name still reads like a broader guarantee.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` lines 241-247.
- **Recommendation:** Rename the rule to `shouldBanStrayPermitAllCalls` so its name matches its actual scope, or leave the name and rely on the detailed Javadoc. If renaming, update the test method names accordingly.
- **Confidence:** Low — naming clarity only.

---

## Finding 3 — The rule covers only `permitAll()`, not other unauthenticated-access mechanisms

- **Issue:** The rule documents that it does not cover `AuthorizedUrl.anonymous()` or `WebSecurityCustomizer.web.ignoring()`. Neither is used today, but the task is framed as closing the L10 public-endpoint allowlist gap. A future developer could introduce one of those mechanisms and believe L10 is still enforced by this rule.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` lines 233-235.
- **Recommendation:** Either expand the rule to ban `anonymous()` and `web.ignoring()` (with matching fixtures/canaries), or keep the documented limitation and add a short note to `agents.md` L10 that the enforced mechanism is specifically `permitAll()`.
- **Confidence:** Low — no current vulnerability.

---

## Finding 4 — The rule and fixture are tightly coupled to Spring Security inner-class names

- **Issue:** The rule and `RoguePermitAllUser` both name `AuthorizeHttpRequestsConfigurer.AuthorizedUrl` and `AuthorizationManagerRequestMatcherRegistry`. A future Spring Security upgrade that renames or restructures these classes will break compilation of both the rule and the fixture. The Javadoc acknowledges this, but there is no CI-level reminder.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` lines 237-239 and 241-244; `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RoguePermitAllUser.java` lines 4 and 17.
- **Recommendation:** Add a comment near the Spring Security version in `services/payment/pom.xml` (or wherever the version is declared) noting that an upgrade requires a mechanical check of `ArchitectureTest` and `RoguePermitAllUser`.
- **Confidence:** Low — upgrade failures are loud, not silent.

---

## Finding 5 — No canary proves the rule is not vacuous on `ResourceServerConfig`

- **Issue:** The real-code canary passes because `ResourceServerConfig` is excluded. The violation canary passes because `RoguePermitAllUser` is not excluded. Neither test proves that removing the `doNotBelongToAnyOf(ResourceServerConfig.class)` clause would make the real-code canary fail. The Phase 6 mutation check did this empirically, but it is not part of the committed test suite.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` lines 275-278 and 376-385; Phase 6 notes mention the mutation check.
- **Recommendation:** Add a one-off test or a code comment documenting the mutation result: "Removing the ResourceServerConfig exclusion causes this rule to fail at ResourceServerConfig:57." If a committed guard is desired, add a test that imports only `ResourceServerConfig` and asserts the rule fails without the exclusion (but this duplicates the rule logic and may be brittle).
- **Confidence:** Low — the mutation check already verified correctness, but the suite itself does not encode that proof.

---

## Summary

T25 is implemented correctly: the new rule follows the sibling pattern, the fixture is well-placed, and the canaries prove the rule can fail. The most useful Phase 9 touch is Finding 1 (tighten the canary assertion). Findings 2–5 are scope/clarity/documentation notes that should be dispositioned at the Phase 9 gate. No production code changes are needed.
