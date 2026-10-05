<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# notification · T16 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T16 — ArchUnit module-boundary tests |
| **Spec section** | L2, L8, L11 |
| **Model** | Kimi 2.7 |
| **Consumes** | Implementation + `artifacts/07-self-review.md` + frozen brief |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the T16 implementation and self-review.

---

## Finding 1 (concur with self-review Finding 1) · A FROZEN file path was amended after the freeze

**Severity:** Medium (process)

**Evidence:**
- Phase 4 frozen brief listed `src/test/java/archtestfixtures/RogueHttpClientUser.java`.
- Phase 5 moved it to `src/test/java/com/themistra/notification/channel/RogueHttpClientUser.java`.
- Phase 6 shipped it at the Phase 5 path.

**Assessment:** The correction is correct on the merits. The L2 rule's subject condition `resideInAPackage(ANALYZED_PACKAGE + "..")` would not select a fixture outside `com.themistra.notification`, so the original path would have produced a negative-proof test that proves nothing. However, the Phase 4 freeze was the human-approval gate, and a later design phase changed one of its concrete outputs without returning for a fresh freeze.

**Recommendation:** No code change needed — the current path is the only one that makes the negative proof meaningful. Confirm with the user that a Phase-5 path correction to a FROZEN output is an acceptable exception, or update the freeze discipline documentation to explicitly allow this when the correction is needed to keep a test valid.

---

## Finding 2 (concur with self-review Finding 2) · Frozen constraint text assumes `@AnalyzeClasses`, which no longer exists

**Severity:** Low

**Evidence:**
- Frozen brief constraint: "`@AnalyzeClasses` and every canary's own `ClassFileImporter` must share one single, eagerly-initialized `JavaClasses` instance..."
- Phase 6 removed `@AnalyzeClasses` and `@ArchTest` fields entirely.

**Assessment:** The substance still holds: one eagerly-initialized `JavaClasses` instance is shared by every canary, so no two canaries can analyze different class sets. The literal wording is slightly stale because there is no longer an annotation-driven scan to drift from. The class Javadoc explains this clearly.

**Recommendation:** No code change needed. Note in Phase 12 specification verification that this constraint line should be read as superseded by the Phase 6 Javadoc.

---

## Finding 3 · The L2 negative-proof assertion is weaker than sibling precedents

**Severity:** Low

**Evidence:**
- `shouldMakeNoSynchronousCrossServiceCallActuallyFailsAgainstAGenuineViolation` only asserts the failure message contains `"RogueHttpClientUser"`.
- `services/crypto`'s negative-proof tests assert both the violating class name and the target class/package name (e.g., `"RogueAttestReferencer"` and `"KmsSigner"`/`"KmsClient"`).
- `services/auth`'s entity negative-proof asserts both `"RogueWatchEntityReferencer"` and `"TokenAllowlist"`.

**Assessment:** Asserting only the violating class name could mask a failure for the wrong reason. For example, if `ClassFileImporter` failed to import `RogueHttpClientUser` and threw an unrelated `AssertionError` containing the class name, the test would pass. The rule genuinely fails because `RogueHttpClientUser` depends on `RestTemplate` (`org.springframework.web.client.RestTemplate`), and the failure message contains that dependency description.

**Recommendation:** Strengthen the assertion to also require `"RestTemplate"` or `"org.springframework.web.client"` in the message. This is a one-line change that aligns with established precedent and removes ambiguity about why the rule failed.

---

## Finding 4 · Removal of `@ArchTest`/`@AnalyzeClasses` is a material departure from the brief and sibling services

**Severity:** Medium

**Evidence:**
- The frozen brief explicitly expected `@ArchTest` fields plus `@Test` canaries, mirroring auth and crypto.
- Phase 6 implemented the rules as plain `private static final ArchRule` fields with only `@Test` canaries, due to a discovered Surefire bug where the presence of `@ArchTest` fields causes Surefire to report zero tests for the entire class.
- The class Javadoc documents this in detail and claims auth's and crypto's existing architecture tests are therefore currently silently non-enforced under the same Surefire configuration.

**Assessment:** The departure is well-justified if the Surefire bug claim is accurate, and the canaries still enforce all three rules. However, it means this class no longer matches the structural precedent the brief cited, and it raises a repo-wide concern about auth/crypto architecture tests. This is too significant to be only a self-review footnote.

**Recommendation:**
- Verify the Surefire bug claim independently in an environment with `mvn` available (this workspace does not have it).
- Capture the finding and the reproduction steps in a repo-wide issue or ADR so the same workaround can be applied to auth/crypto if confirmed.
- Update Phase 12 specification verification to record that the brief's structural expectation (`@ArchTest` fields) was superseded by this empirical finding.

---

## Finding 5 · The L2 rule's name does not fully describe its breadth

**Severity:** Very low / informational

**Evidence:**
- Rule name: `shouldMakeNoSynchronousCrossServiceCall`.
- Rule bans both sibling service packages and all listed synchronous HTTP client packages, including potential external HTTP clients, not only cross-service calls.

**Assessment:** The `because` text clarifies the intent, but the rule name reads narrower than the actual check. This could confuse a future developer who adds an external HTTP client for a non-platform integration and is surprised by the failure.

**Recommendation:** Consider renaming to `shouldMakeNoSynchronousHttpCallToSiblingOrExternalServices` or adding a code comment that the rule is intentionally broader than L2's literal wording. Not blocking.

---

## Cross-check against acceptance criteria

| Criterion | Status | Notes |
|---|---|---|
| AC1 — L11 named test exists, backed by canary, resolves all 7 entities | ✅ | `shouldPreventCrossModuleEntityImports` and its canary exist; `FEATURE_MODULES` covers all six modules containing the seven `@Entity` classes. |
| AC2 — L11 negative proofs | ✅ | `RogueChannelEntityReferencer` proves cross-module violation; `RogueUnmappedEntity` proves fail-fast path. |
| AC3 — L11 violations fixed or allowlisted | ✅ | No violations found; no allowlist needed. |
| AC4 — L8 named test exists, mirrors auth | ✅ | `shouldEnforcePublicEndpointAllowlist` and its canary exist. No negative proof, consistent with auth precedent. |
| AC5 — L2 named test exists, whole service, HTTP-client negative proof | ✅ | `shouldMakeNoSynchronousCrossServiceCall` and its canary exist; `RogueHttpClientUser` proves the mechanism. Sibling-service-package half has no compiling fixture, disclosed. |

---

## Verdict

The implementation satisfies the functional acceptance criteria. The three rules are enforced by real `@Test` canaries, the L11 negative/fail-fast paths are proven, and the L2 HTTP-client path is proven. The main concerns are process/documentation (Finding 1, Finding 2, Finding 4) and one test assertion that should be strengthened (Finding 3). No mandatory production code changes. The Surefire `@ArchTest` bug claim should be independently verified and tracked repo-wide if confirmed.
