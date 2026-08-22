<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T37 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T37 — Run full test suite + Docker image build |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/01-specification-extraction.md` (Phase 2 TIB does not exist yet) |
| **Produces** | `artifacts/03-design-challenge.md` |

**Task statement (verbatim from `spec/auth-service/tasks.md`, task 37):**
> **Run full integration test.** `mvn -pl services/auth verify` must pass. Docker image must build from repo root.

Below are adversarial findings on the Phase 1 specification extraction. The normal Phase 2 Task Implementation Brief is missing; these findings challenge what is known so far. Each finding is presented as **Issue · Severity · Evidence · Recommended brief amendment**. No redesign or implementation is proposed.

---

## Finding 1 — Phase 2 Task Implementation Brief is missing

**Issue.** The Phase 3 design challenge is supposed to consume a frozen Phase 2 Task Implementation Brief, but `artifacts/02-task-implementation-brief.md` does not exist. The pipeline cannot proceed to Phase 4 human approval without a TIB that records the scope decision (Group C only vs. Groups A/B/C), the exact fix approach, and the acceptance criteria fallback for environmental failures.

**Severity.** High — the brief is unfrozen and the implementation plan is undecided.

**Evidence.**
- `artifacts/` directory contains only `00-repository-understanding.md` and `01-specification-extraction.md`.
- Phase 1 Open Questions §70-85 explicitly defer the scope decision to Phase 4.

**Recommended brief amendment.** Author `artifacts/02-task-implementation-brief.md` before freezing. It must contain:
- A clear scope decision (e.g., "Fix Group C; document and defer Groups A and B").
- The exact files to modify (`AuditTrailIntegrationTest.java`, `RoleAssignmentIntegrationTest.java`).
- The exact fix pattern for Group C (register + activate an account via `AccountService` before using its UUID).
- Acceptance criteria revisions if AC1 cannot be fully met due to Group A.

---

## Finding 2 — AC1 may be impossible to meet if Group A is environmental

**Issue.** The task statement says `mvn -pl services/auth verify` must pass with zero failures/errors. Phase 1 reports that AC1 is not met because of three failure groups. Group A is a Kafka producer→broker connectivity problem that has no known code-level fix and was already deferred at T36's human gate. If Group A cannot be fixed from inside `services/auth`, then AC1 as literally stated is unachievable.

**Severity.** High — the acceptance criterion is at risk of being non-achievable.

**Evidence.**
- Phase 1 AC table: AC1 status "Not met" with 1 failure, 8 errors.
- Phase 1 Open Questions §74-78: Group A is "a local Docker/host networking problem" with "no known code-level fix."
- T36 Phase 6 notes independently reproduced the same Kafka connectivity failure on an unrelated test.

**Recommended brief amendment.** Split AC1 into two criteria:
- AC1a: All code-level failures (Group C) are fixed and `mvn -pl services/auth verify` passes when Kafka connectivity is healthy.
- AC1b: Any remaining environmental failures (Group A) and unconfirmed flakiness (Group B) are documented in the task artifact with a human sign-off to defer.

---

## Finding 3 — Group B root cause is speculative

**Issue.** Phase 1 notes that Group B (null-response flakiness, 4 tests) has "no confirmed root cause" and only mentions a "plausible link to Group A's producer-retry contention" that is "unconfirmed." A design challenge cannot accept a fix strategy based on an unconfirmed hypothesis.

**Severity.** Medium — risk of wasted work or incorrect scope.

**Evidence.** Phase 1 Open Questions §79-80: "Group B (null-response flakiness, 4 tests) has no confirmed root cause; today's fresh run is the first time a plausible link to Group A's producer-retry contention was noticed, unconfirmed."

**Recommended brief amendment.** Either:
- Scope Group B out of T37 pending a separate root-cause investigation, or
- Require a time-boxed investigation in the TIB with a clear stop condition (e.g., "if no reproducible cause is found within X hours, document and defer").

---

## Finding 4 — Group C fix changes the semantics of the tests it touches

**Issue.** The proposed fix for Group C is to call `AccountService.register`/`activateEmail` before using an account UUID in `AuditTrailIntegrationTest` and `RoleAssignmentIntegrationTest`. This changes the tests from exercising the services with arbitrary UUIDs to exercising them only with existing accounts. If the production services should reject or handle non-existent account UUIDs gracefully, the fixed tests would no longer cover that path.

**Severity.** Medium — the fix may mask a real service-layer behavior gap.

**Evidence.**
- `AuditTrailIntegrationTest.recordMirrorsToKafkaWithoutLeakingIpOrUserAgent` uses `UUID.randomUUID()` directly.
- `RoleAssignmentIntegrationTest` uses `UUID.randomUUID()` for `accountUuid` in all three tests.
- Phase 1 §44-46: known-good fix pattern is `AccountService.register`/`.activateEmail` before using a UUID.

**Recommended brief amendment.** Add a note:

> "The Group C fix intentionally changes these tests to use real accounts, because the current schema enforces an account FK. A separate task should verify whether `AuditService.record` and `RoleService.assignRole` should validate account existence themselves or accept arbitrary UUIDs; that validation question is out of scope for T37."

---

## Finding 5 — It is unclear whether the FK violations are test bugs or service bugs

**Issue.** The Group C failures are FK violations. It is not stated whether the FK is a legitimate production constraint (in which case the services should validate input) or a test-only artifact. If the services accept non-existent account UUIDs in production and only fail in tests because of test data, then the service has a bug that T37 should not silently paper over.

**Severity.** Medium — risk of fixing the test while leaving a production defect.

**Evidence.**
- `AuditTrailIntegrationTest` calls `auditService.record(...)` with a random UUID.
- `RoleAssignmentIntegrationTest` calls `roleService.assignRole(...)` with a random UUID.
- Phase 1 describes the failures as "FK-violation" but does not state which table has the FK.

**Recommended brief amendment.** Investigate and document which table/column enforces the FK and whether the service has explicit validation. If no validation exists, consider whether T37 should add it (scope expansion) or log an open question for a follow-up task.

---

## Finding 6 — Docker image build command is not specified

**Issue.** The task statement says "Docker image must build from repo root," but Phase 1 says the Dockerfile is at `services/auth/Dockerfile` and AC2 is already met. The exact command is not stated, which could lead to different invocations being used in CI vs. local verification.

**Severity.** Low — likely not a correctness issue, but a reproducibility gap.

**Evidence.**
- Task statement: "Docker image must build from repo root."
- Phase 1 §29: `services/auth/Dockerfile`.
- Phase 1 AC table: AC2 "Met — verified at Phase 0, exit code 0."

**Recommended brief amendment.** Specify the exact build command used for verification, e.g.:

> "Docker build verified with `docker build -f services/auth/Dockerfile services/auth` (or equivalent from repo root)."

---

## Finding 7 — Fixing Group C may introduce cross-module test dependencies

**Issue.** `AuditTrailIntegrationTest` is in the `audit` package and `RoleAssignmentIntegrationTest` is in the `authz` package. The proposed fix imports `AccountService` from the `account` module. While test code is not constrained by `ArchitectureTest`'s module-boundary rules (tests are excluded), adding production imports from another module in test code is still a cross-module dependency that should be explicitly acknowledged.

**Severity.** Low — tests are excluded from ArchUnit analysis, but the design choice should be recorded.

**Evidence.**
- Phase 1 §21-22: "L12 (module boundaries — any fix stays inside the two affected test files, no new cross-module dependency)."
- `ArchitectureTest` line 42: `ImportOption.DoNotIncludeTests.class`.

**Recommended brief amendment.** Clarify:

> "The Group C fix imports `AccountService` and related DTOs from the `account` module into the `audit` and `authz` test files. This is acceptable because (a) test code is excluded from `ArchitectureTest` analysis, and (b) the alternative would require duplicating registration logic. The fix remains a test-only change."

---

## Finding 8 — No rollback strategy means shared DB state will grow

**Issue.** The integration tests in this module do not use per-test rollback. Fixing Group C by creating accounts in `AuditTrailIntegrationTest` and `RoleAssignmentIntegrationTest` will add rows to the `account` table on every run. While random UUIDs prevent collisions, the test database will accumulate state, which could eventually affect performance or mask issues.

**Severity.** Low — consistent with existing tests, but worth noting.

**Evidence.**
- `RoleAssignmentIntegrationTest` and `AuditTrailIntegrationTest` do not use `@Transactional` or cleanup.
- Sibling tests (e.g., `SessionIntegrationTest`, `ApiKeyLifecycleIntegrationTest`) follow the same no-rollback convention.

**Recommended brief amendment.** Note that no additional cleanup is introduced by the Group C fix and that this follows the module's existing convention. If the suite grows large enough that accumulated state becomes a problem, that is a cross-cutting concern outside T37.

---

## Finding 9 — Group A fix may require changes outside `services/auth`

**Issue.** If the Kafka connectivity issue is caused by Testcontainers network configuration, Docker daemon settings, or host-level networking, fixing it may require changes to `TestcontainersConfiguration`, the root `pom.xml`, CI configuration, or developer environment documentation — all outside the stated scope of "files involved."

**Severity.** Medium — scope creep risk.

**Evidence.** Phase 1 §47-49: "Group A ... is environment/Docker networking, not a `services/auth` code dependency."

**Recommended brief amendment.** Explicitly exclude infrastructure/environment changes from T37's scope unless the Phase 4 human gate approves a specific, bounded investigation. Document the Kafka issue as an environment blocker.

---

## Finding 10 — The acceptance criteria lack a definition of "pass" under partial environmental failure

**Issue.** The task statement and Phase 1 use "pass" as a binary outcome. With environmental failures in play, the TIB needs to define what evidence constitutes success (e.g., all tests pass except known-environmental ones, or a green run on a different machine/CI).

**Severity.** Medium — without this, the Phase 4 gate cannot make a confident decision.

**Evidence.** Phase 1 AC table and Open Questions §82-85 frame the trade-off but do not resolve it.

**Recommended brief amendment.** Add to the TIB:

> "Success is defined as: (1) Group C failures are eliminated by code changes in the two identified test files; (2) Groups A and B are reproduced and documented as environmental/flaky with at least one independent witness (e.g., the same failure occurs on an already-merged, unrelated test); (3) Docker image build succeeds from repo root."

---

## Summary

T37's Phase 1 extraction correctly surfaces the three failure groups and the hard scope decision, but it cannot be frozen without a Phase 2 TIB. The highest-priority decisions are:
1. Author the missing Phase 2 TIB.
2. Decide whether AC1 must be literally green or can be partially deferred.
3. Confirm whether Group C's FK violations are test bugs or symptoms of missing service validation.

(End of Phase 3 design challenge.)
