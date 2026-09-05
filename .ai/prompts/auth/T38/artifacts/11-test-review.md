<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T38 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T38 — Review against gap analysis defect catalogue |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the Phase 10 test manifest for T38. This is a zero-code-change, verification-only task, so the review focuses on whether the existing test/mechanism coverage is sufficient and whether the verification record is complete.

---

## Gap 1 — No automated guard prevents reintroduction of AC3 or AC5 defects

**Why it matters.** AC3 (shared model artifact) and AC5 (`allow-circular-references`) are listed as "absence by construction." There is no test that fails if a future PR adds a `commons-netra`-style dependency to `pom.xml` or sets `spring.main.allow-circular-references=true`. Relying on manual grep during T38 is correct for today, but it is not a durable regression guard.

**Suggested test.** Add two lightweight, permanent tests:

```java
@Test
void noSharedModelArtifactDependency() {
    // This is a structural assertion; in practice, ArchUnit or a simple file read of pom.xml
    // can enforce that no cross-service shared entity artifact is introduced.
}

@Test
void noAllowCircularReferences() {
    // Assert that no properties file or SpringApplication setup enables circular references.
}
```

If adding tests is out of scope, document this as a known gap in the final verification record.

---

## Gap 2 — No test asserts that `PublicEndpoints` excludes `/admin/**`

**Why it matters.** AC2 is enforced by `ArchitectureTest.shouldEnforcePublicEndpointAllowlist` (no `.permitAll()` outside the allowlist) and `admin_controller_handlers_require_preauthorize` (methods in `Admin*` controllers require `@PreAuthorize`). However, there is no test that the allowlist itself does not contain an `/admin/**` path. A future edit to `PublicEndpoints` could add one and the existing ArchUnit rule would not fail.

**Suggested test.** Add a plain JUnit test in `ArchitectureTest`:

```java
@Test
void publicEndpointAllowlistContainsNoAdminPaths() {
    assertThat(PublicEndpoints.METHOD_SCOPED).noneSatisfy(
            endpoint -> assertThat(endpoint.path()).startsWith("/admin"));
    assertThat(PublicEndpoints.PATH_SCOPED).noneSatisfy(
            endpoint -> assertThat(endpoint.path()).startsWith("/admin"));
}
```

---

## Gap 3 — gitleaks CI gate status is still not explicitly confirmed

**Why it matters.** The manifest says `.github/workflows/ci.yml` was scanned, but it does not state whether a gitleaks step is actually present and running. The gap-analysis identifies gitleaks as the durable defense against committed secrets. Without confirmation, the long-term half of AC1 is unverified.

**Suggested test/action.** Inspect `.github/workflows/ci.yml` and record the exact finding: either "gitleaks step present at line X" or "gitleaks step absent; carried as out-of-scope infrastructure follow-up." If absent, note it in the final verification record.

---

## Gap 4 — The verification evidence is spread across multiple artifacts

**Why it matters.** T38's deliverable is the verification record itself, but the evidence lives in Phases 0, 1, 4, 6, 8, and 10. A reviewer who only reads the final artifact may not see the full picture.

**Suggested test/action.** Produce a single consolidated verification summary (e.g., in `12-specification-verification.md` or the final PR description) that lists each AC, the exact command or file evidence, and the result. This is documentation, not a test, but it is the task's primary output.

---

## Gap 5 — Exact grep commands for AC1 are not recorded

**Why it matters.** The AC1 source scan is heuristic and produced two known-benign false positives. Without recording the exact command, a future reviewer cannot reproduce or extend the scan.

**Suggested test/action.** Record the exact grep/regex used in the final verification summary, e.g.:

```
grep -RniE "(password|secret|token|key|private)\s*=\s*["'][^"']{8,}[\"']" services/auth/src/main/java
```

and note the two false positives (`ACR_API_KEY` URI, `PasswordResetConfirmRequest.toString()`).

---

## Gap 6 — Docker image build verification is not re-run in Phase 10

**Why it matters.** The task statement requires the Docker image to build from repo root. The manifest reports only the source-level defect checks; it does not state that the Docker build was re-verified in this phase.

**Suggested test/action.** Either re-run `docker build -f services/auth/Dockerfile ...` and record the exit code, or explicitly state that the Docker-build result is carried forward from Phase 0/1 and not re-run here.

---

## Gap 7 — No test verifies `@Value` fail-on-missing behavior

**Why it matters.** AC4 now correctly notes that two `@Value` annotations exist and have no default fallback. This is a manual verification observation, not an automated test. A future edit could add a default value to one of them and silently introduce the defect class.

**Suggested test.** Add an ArchUnit or plain JUnit test that scans for `@Value` annotations with default values (e.g., `${prop:default}`) or raw `Environment` lookups in production code. If out of scope, document the limitation.

---

## Summary

The Phase 10 manifest is accurate: the five defect classes are absent and the existing mechanisms cover AC1, AC2, and AC4. The main weakness is the lack of durable regression guards for AC3 and AC5 ("absence by construction") and the allowlist contents for AC2. Because T38 is scoped as no-new-tests, these should be documented as recommended follow-ups rather than blockers.

(End of Phase 11 test review.)
