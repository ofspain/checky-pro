<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T16 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T16 — ArchUnit module-boundary tests |
| **Spec section** | L2, L8, L11 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + final test class + fixtures |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T16 regression-guard tests against the acceptance criteria.

---

## What is covered

- **Three named `@Test` canaries (AC1, AC4, AC5)** — one per rule, each invoking the corresponding `ArchRule` against the eagerly-initialized, shared `JavaClasses` set.
- **L11 in-module violation (AC2)** — `RogueChannelEntityReferencer` (in `channel`) references `delivery.DeliveryLog`; the test asserts both class names appear in the failure message.
- **L11 fail-fast path (AC2)** — `RogueUnmappedEntity` (outside `com.themistra.notification`) triggers the fail-fast message; the test asserts both the offending class name and the generic fail-fast phrase.
- **L2 HTTP-client violation (AC5)** — `RogueHttpClientUser` (in `channel`) declares a `RestTemplate` field; the test asserts both the violating class name and `RestTemplate` appear in the failure message.
- **Phase 10 strengthening** — the L11 fail-fast test now asserts `"RogueUnmappedEntity"` (closing the same class of weak-assertion gap the L2 negative-proof test already fixed).

---

## Gap 1 · Maven verification claim cannot be confirmed in this environment

**Why it matters:** `artifacts/10-test-generation.md` states `mvn -pl services/notification test -Dtest=ArchitectureTest` ran 6/6 and `mvn -pl services/notification clean verify` ran 368 tests, 0 failures. This environment does not have `mvn` available.

**Evidence:**
- `shell`/`mvn` returns `command not found` in this workspace.
- The test class and fixtures are syntactically consistent, but no local execution was performed.

**Suggested action:** Run the Maven commands in an environment where `mvn` is available before considering the task fully verified.

---

## Gap 2 · `featureModuleOf`'s `startsWith(modulePackage + ".")` branch is untested

**Why it matters:** The helper has two match branches (`equals` and `startsWith`). All 7 real `@Entity` classes live directly in their module's top-level package, so only the `equals` branch is exercised by any existing test. A regression that broke the sub-package branch would not be caught.

**Evidence:**
- `featureModuleOf` at lines 69–78 uses `packageName.equals(modulePackage) || packageName.startsWith(modulePackage + ".")`.
- `grep` confirms no `@Entity` in this service lives in a sub-package.
- `services/auth` and `services/crypto` have the same idiom and the same gap.

**Assessment:** This was found in Phase 10 and deliberately left open, with the rationale that the idiom is standard, already-proven elsewhere, and no real entity uses sub-packages. This is consistent with T15's precedent for not fixing currently-unreachable gaps speculatively.

**Suggested action:** Accept as disclosed. If a future entity is placed in a sub-package, the real canary will exercise this branch, and any regression will surface then.

---

## Gap 3 · No negative proof for the sibling-service-package half of L2

**Why it matters:** `shouldMakeNoSynchronousCrossServiceCall` bans both sibling service packages (`com.themistra.auth..`, `com.themistra.crypto..`, `com.themistra.payment..`) and synchronous HTTP clients. Only the HTTP-client half has a genuine negative proof.

**Evidence:**
- `services/notification/pom.xml` has no dependency on `services/auth`, `services/crypto`, or `services/payment` source, so no compiling fixture can import a class from those packages.
- The brief disclosed this limitation explicitly.

**Assessment:** Acceptable. The HTTP-client negative proof demonstrates the rule mechanism works, and the sibling-package half is structurally enforced by the same rule.

---

## Gap 4 · No negative proof for L8

**Why it matters:** `shouldEnforcePublicEndpointAllowlist` has only a canary (passes on clean code), not a test proving it fails when another class calls `permitAll`.

**Evidence:**
- The L8 test only checks the rule against the current codebase.
- `services/auth`'s identical rule also has no negative-proof test.

**Assessment:** Acceptable per the brief's stated precedent. The rule is simple and the canary is sufficient for this task's scope.

---

## Gap 5 · No test exercises the other banned HTTP-client packages

**Why it matters:** The L2 rule bans four HTTP-client package trees. The negative proof only uses `org.springframework.web.client.RestTemplate`.

**Evidence:**
- Banned packages: `org.springframework.web.client..`, `org.springframework.web.reactive.function.client..`, `java.net.http..`, `org.apache.http..`.
- Only `RestTemplate` is exercised.

**Assessment:** The rule is expressed in terms of package patterns, so exercising one package is sufficient to prove the mechanism. Testing all four would be gold-plating. Not a blocking gap.

---

## Gap 6 · No independent verification of the Surefire `@ArchTest` bug claim

**Why it matters:** The implementation's central departure from the brief and sibling services — removing `@ArchTest`/`@AnalyzeClasses` entirely — rests on an empirical claim about Surefire behavior. If that claim is wrong or environment-specific, this class would be the outlier rather than the correct pattern.

**Evidence:**
- The Javadoc describes a detailed reproduction involving `mvn test` and the JUnit Platform `Launcher` API.
- This environment cannot run `mvn` to confirm or refute the claim.

**Suggested action:** Verify the claim in a Maven-enabled environment and, if confirmed, raise a repo-wide issue so `services/auth` and `services/crypto` can apply the same workaround.

---

## Summary

T16's test suite is complete for its scope. All three named rules have canaries, both L11 negative paths are proven, and the L2 HTTP-client negative path is proven. Phase 10 closed the weak-assertion gap in the L11 fail-fast test. The remaining gaps are disclosed limitations (no compiling fixture for the L2 sibling-package half, no L8 negative proof per precedent, untested `startsWith` branch) or environmental verification (Maven run, Surefire bug claim). No additional tests are required to satisfy the acceptance criteria.
