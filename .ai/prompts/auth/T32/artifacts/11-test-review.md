<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T32 · Phase 11 — Test Review

Consumed `artifacts/10-test-generation.md` and the final `ArchitectureTest.java`. Verified locally:
`mvn -pl services/auth test -Dtest='ArchitectureTest'` reports **2/2 pass** for the JUnit Jupiter
engine and **0 tests** for the ArchUnit engine. The output shows repeated ArchUnit warnings:
`Unsupported class file major version 70`, confirming the ArchUnit 1.3.0 bundled ASM does not
support the Java 24 runtime used here. Findings only — no test or production code changes.

---

## Executive Summary

The Phase 10 suite now contains three members in `ArchitectureTest`: AC1's content assertion, the
named AC2 ArchUnit rule, and a canary `@Test` that drives the same ArchUnit rule through a direct
`ClassFileImporter` so it executes under Surefire despite the broken ArchUnit JUnit 5 engine
integration. The canary successfully closes the immediate CI-enforcement gap for T32, but it is a
localized workaround, not a systemic fix.

---

## Findings

### Gap 1 — The canary test's import configuration duplicates the `@AnalyzeClasses` annotation

**Why it matters:** `shouldEnforcePublicEndpointAllowlistActuallyRunsUnderThisBuild` manually
constructs a `JavaClasses` set with `importPackages("com.themistra.auth")` and
`new ImportOption.DoNotIncludeTests()`. This mirrors the `@AnalyzeClasses` annotation on the class,
but it is not mechanically tied to it. If someone changes the scan packages or import options in
the annotation, the canary could silently check a different class set than the `@ArchTest` rules.

**Suggested test:** Extract the scan configuration into a single source of truth — e.g., a private
static helper that returns the `JavaClasses` set, used by both the canary test and (if feasible) a
custom ArchUnit runner — or add a small assertion in the canary that verifies its imported class
set matches the annotated scope.

**Evidence:** `ArchitectureTest.java:29`, `ArchitectureTest.java:142-145`.

---

### Gap 2 — The canary only exercises the T32 rule, leaving the other ArchUnit rules un-gated

**Why it matters:** The root cause (ArchUnit 1.3.0 ASM cannot read Java 24 class files, major
version 70) affects every `@ArchTest` in this file, not just `shouldEnforcePublicEndpointAllowlist`.
The canary fixes T32's CI enforcement, but the pre-existing rules (`only_the_account_module_may_touch_the_Account_entity`,
`repositories_are_never_public`, etc.) still do not run under `mvn test`. The Phase 6 notes already
surfaced a real bug in one of those rules that has been hidden by this non-execution.

**Suggested test:** Either generalize the canary to invoke all `@ArchTest` rules reflectively (so
one workaround fixes the whole file), or accept that this is a separate follow-up task and document
it explicitly in the Phase 10 manifest rather than implying T32 is the only affected rule.

**Evidence:** `ArchitectureTest.java:32-114`; local `mvn test` output showing `Tests run: 0` for the
ArchUnit engine and the `Unsupported class file major version 70` warnings.

---

### Gap 3 — AC1 has no shipped negative proof

**Why it matters:** Phase 10 acknowledges that removing `/api-keys/token` from
`PublicEndpoints.METHOD_SCOPED` was not separately re-verified. The argument that it follows T07's
pattern is reasonable, but for a task whose entire purpose is "prevent accidental regression of
this allowlist entry," a one-line temporary removal would have taken seconds and would have proven
the assertion is not vacuous.

**Suggested test:** (Optional, performed and reverted) Temporarily remove the `/api-keys/token`
entry from `PublicEndpoints.METHOD_SCOPED` and confirm `mvn test -Dtest='ArchitectureTest'` fails
on `apiKeysTokenExchangeIsInThePublicAllowlist`, then restore it.

**Evidence:** `artifacts/10-test-generation.md:26-30`.

---

### Gap 4 — The workaround test name is implementation-focused, not behavior-focused

**Why it matters:** `shouldEnforcePublicEndpointAllowlistActuallyRunsUnderThisBuild` documents the
workaround but does not describe the behavior being verified. The test's real value is "the named
ArchUnit rule is enforced by `mvn test` even when the ArchUnit engine does not run it."

**Suggested test:** Rename to something like
`shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild`. Low priority.

**Evidence:** `ArchitectureTest.java:142`.

---

## Non-Issues Confirmed

- **AC1 coverage:** `apiKeysTokenExchangeIsInThePublicAllowlist` correctly asserts the presence of
  `POST /api-keys/token` using record equality.
- **AC2 coverage:** `shouldEnforcePublicEndpointAllowlist` correctly targets
  `AuthorizedUrl.permitAll()` and restricts callers to `SecurityChainsConfig`.
- **Canary correctness:** `shouldEnforcePublicEndpointAllowlistActuallyRunsUnderThisBuild` directly
  invokes the rule's `check(...)` and runs under Surefire, so T32's named rule is enforced by
  `mvn test` today.
- **Named-test traceability:** `shouldEnforcePublicEndpointAllowlist` matches `package.md` §8.
- **No production changes:** only `ArchitectureTest.java` is modified.

---

## Traceability Summary

| AC | Covered By | Gap |
|---|---|---|
| AC1 — `/api-keys/token` is in the public list | `ArchitectureTest.apiKeysTokenExchangeIsInThePublicAllowlist` | Gap 3 (no shipped negative proof) |
| AC2 — no new `permitAll()` outside the list | `ArchitectureTest.shouldEnforcePublicEndpointAllowlist` (rule) + `shouldEnforcePublicEndpointAllowlistActuallyRunsUnderThisBuild` (canary ensuring it runs under `mvn test`) | Gap 1 (duplicated scan config), Gap 2 (other ArchUnit rules still not running) |

---

**Phase 11 complete — test review written.** Proceed to Phase 12 (Specification Verification) on approval.
