<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T37 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T37 — Run full test suite + Docker image build |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the Phase 10 test manifest and the current state of the two fixed test files. Findings are recommendations only, formatted as **Gap · Why it matters · Suggested test**.

---

## Gap 1 — AC1 is still not fully satisfied

**Why it matters.** The task statement says `mvn -pl services/auth verify` must pass with zero failures/errors. The manifest correctly reports 702 tests, 1 failure, 6 errors after the Group C fix. Groups A and B are deferred per the Phase 4 human gate, but the literal acceptance criterion remains unmet. A future reviewer reading only the task statement may not understand why the suite is considered complete.

**Suggested test.** This is a scope/documentation issue, not a missing test. Add a prominent comment in the Phase 10 manifest (or the final task summary) stating that AC1 has been split into AC1a (code-level failures fixed) and AC1b (environmental/flaky failures deferred with evidence), and reference the corroborating runs documented in the manifest.

---

## Gap 2 — `accountWithNothingAssignedHasNoEffectiveRoles` is inconsistent with the FK fix

**Why it matters.** The other two tests in `RoleAssignmentIntegrationTest` were fixed to use real accounts because `assignRole` triggers an audit write with an FK to `accounts`. The third test still uses `UUID.randomUUID()` because it only reads. This is correct today, but if a future change adds audit/logging to `resolveEffectiveRoles`, this test will fail with the same FK violation.

**Suggested test.** Update `accountWithNothingAssignedHasNoEffectiveRoles` to use `registerAndActivate` as well, even though it only reads. This makes all three tests in the class follow the same precondition pattern and removes the latent FK hazard.

```java
@Test
void accountWithNothingAssignedHasNoEffectiveRoles() {
    UUID accountUuid = registerAndActivate("role-empty-" + UUID.randomUUID() + "@example.com");
    assertThat(roleService.resolveEffectiveRoles(accountUuid)).isEmpty();
}
```

---

## Gap 3 — No test covers the non-existent-account-UUID failure path

**Why it matters.** The Group C fix assumes that callers of `AuditService.record` and `RoleService.assignRole` always provide an existing account UUID. There is no test verifying what happens when they do not. If a production caller passes a non-existent UUID, the current behavior is a database FK violation rather than a domain exception.

**Suggested test.** Add a focused test in either `AuditTrailIntegrationTest` or `RoleAssignmentIntegrationTest` that passes a random UUID and asserts the expected failure mode (e.g., a domain exception or a DataIntegrityViolationException). If the expected behavior is undefined, document it as an open question instead of adding a test.

---

## Gap 4 — Docker image build is not automated in the test suite

**Why it matters.** The task's second clause requires the Docker image to build from the repo root. The manifest mentions verification performed but does not state that the build is automated or reproducible via a test. A manual `docker build` can pass in one environment and fail in another without CI catching it.

**Suggested test.** This may be out of scope for the Java test suite. If so, add a CI-stage command to the manifest (e.g., `docker build -f services/auth/Dockerfile services/auth`) and record the exit code. If a Maven-based image build is desired, consider binding a Smoke/IT phase or a shell-based Maven exec plugin invocation.

---

## Gap 5 — No regression test specifically guards the FK fix

**Why it matters.** The fixed tests now pass, but there is no test whose name or assertion explicitly documents that the FK violation is the behavior being guarded against. A future refactor could reintroduce a fabricated UUID in a new test method without a clear signal that this is forbidden.

**Suggested test.** Add a short code comment above each `registerAndActivate` call explaining that the account must exist because the audit write path has a real FK to `accounts`. This is documentation, not a new test, but it preserves the intent. Alternatively, rename the helper to `registerAndActivateExistingAccount` to make the precondition explicit.

---

## Gap 6 — `AuditTrailIntegrationTest` does not assert the row is written to `auth_audit`

**Why it matters.** The test verifies the Kafka mirror but does not assert that the underlying `auth_audit` row was actually inserted. A regression that wrote only to Kafka (or that swallowed the record silently) would not be caught.

**Suggested test.** Add an assertion before the Kafka wait:

```java
assertThat(auditService.list(accountUuid, Pageable.unpaged()).getContent())
        .anySatisfy(event -> assertThat(event.eventType()).isEqualTo("account.suspended"));
```

This proves the row exists in the database before checking its mirror. Note: `AuditEventRepository` is package-private, so `AuditService.list` is the appropriate public API.

---

## Gap 7 — Group A/B deferral evidence is narrative, not reproducible

**Why it matters.** The manifest describes independent corroborating evidence for Groups A and B, but it is written as prose. A reviewer cannot reproduce the observations without rerunning the suite and comparing signatures manually.

**Suggested test.** Capture the failure signatures as artifacts (e.g., stack-trace excerpts or log snippets) and attach them to the Phase 10 manifest or the task summary. This makes the deferral auditable without requiring a live reproduction.

---

## Summary

The two fixed tests now correctly satisfy AC1a for Group C, and the JSON-parsing improvement in `AuditTrailIntegrationTest` addresses a real brittleness gap. The remaining gaps are mostly about consistency (`accountWithNothingAssignedHasNoEffectiveRoles`), coverage of failure paths (non-existent UUIDs, DB row write), and documentation of the deferred environmental failures. None of these are blockers for the scoped T37 deliverable.

(End of Phase 11 test review.)
