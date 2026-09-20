<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T37 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T37 — Run full test suite + Docker image build |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Reviewed the completed Group C fix in `AuditTrailIntegrationTest.java` and `RoleAssignmentIntegrationTest.java` and the Phase 7 self-review with fresh eyes. Findings only.

---

## Finding 1 — Fix changes the test contract from arbitrary UUIDs to real accounts

**Issue.** The original tests exercised `AuditService.record` and `RoleService.assignRole` with fabricated UUIDs. After the fix, they only run against real accounts. This is necessary to satisfy the schema FK, but it means the tests no longer cover the services' behavior when given a non-existent account UUID. If production callers can pass arbitrary UUIDs, a service-layer validation or handling bug could now go undetected.

**Evidence.**
- `AuditTrailIntegrationTest.java` lines 73-75: comment explains the FK constraint and the move to `registerAndActivate`.
- `RoleAssignmentIntegrationTest.java` lines 49-51 and 72: same pattern.
- `RoleAssignmentIntegrationTest.accountWithNothingAssignedHasNoEffectiveRoles` (line 94-95) still uses `UUID.randomUUID()` because it only reads; this inconsistency highlights that the FK only matters on write paths.

**Recommendation.** Add a code comment in both files noting that the tests now assume the caller provides an existing account UUID, and that service-layer validation for non-existent UUIDs is not covered here. If the services should validate account existence, log a follow-up task or open question.

**Confidence.** High.

---

## Finding 2 — `registerAndActivate` helper is duplicated across the two test files

**Issue.** The identical helper appears in both `AuditTrailIntegrationTest` and `RoleAssignmentIntegrationTest`. The self-review notes this matches the established convention (every sibling file duplicates its own copy), but duplication is still a maintenance hazard if the helper pattern changes.

**Evidence.**
- `AuditTrailIntegrationTest.java` lines 97-101.
- `RoleAssignmentIntegrationTest.java` lines 41-45.
- `SessionIntegrationTest`, `ApiKeyLifecycleIntegrationTest`, and `CleanupIntegrationTest` also contain their own copies.

**Recommendation.** Accept the duplication as consistent with the module's existing convention, but do not introduce a third or fourth copy in future tests without considering a shared test-fixture class. No code change required now.

**Confidence.** High.

---

## Finding 3 — `accountWithNothingAssignedHasNoEffectiveRoles` remains inconsistent with the new helper pattern

**Issue.** The third test in `RoleAssignmentIntegrationTest` still uses a raw `UUID.randomUUID()` because it only reads. This is correct today, but it creates an implicit contract that `resolveEffectiveRoles` never writes or validates the account UUID. A future change that adds audit/logging to `resolveEffectiveRoles` would break this test with the same FK violation the other two tests just fixed.

**Evidence.** `RoleAssignmentIntegrationTest.java` line 94-95.

**Recommendation.** For consistency and future-proofing, change this test to also use `registerAndActivate` with a unique email, even though it only reads. The cost is one extra registration per suite run; the benefit is eliminating a latent FK hazard if the read path ever acquires a write side effect.

**Confidence.** Medium.

---

## Finding 4 — `AuditTrailIntegrationTest` still matches Kafka payload with string contains

**Issue.** The test asserts `record.value().contains("\"eventType\":\"account.suspended\"")` rather than parsing the JSON. This is brittle to formatting changes (e.g., spaces, field order) and inconsistent with the JSON-parsing approach used in newer tests like `EndToEndLifecycleIntegrationTest`.

**Evidence.** `AuditTrailIntegrationTest.java` line 87.

**Recommendation.** Parse `record.value()` with `objectMapper.readTree` and assert `payload.get("eventType").asText().equals("account.suspended")`. This is a low-risk readability/hardening improvement.

**Confidence.** Low.

---

## Finding 5 — Service-layer validation question remains unresolved

**Issue.** The Group C fix treats the FK violation as a test bug, but it may also be a service-layer gap. If `auth_audit.account_uuid` and the audit-write path inside `RoleService.assignRole` enforce an account FK, the services should probably validate that the provided UUID exists before attempting the write. No such validation is visible in the tests, and the self-review does not address it.

**Evidence.**
- `AuditTrailIntegrationTest.java` line 77-80: `auditService.record` called directly.
- `RoleAssignmentIntegrationTest.java` line 63: `roleService.assignRole` called directly.
- Phase 3 design challenge Finding 5 raised this same question.

**Recommendation.** Do not change production code under T37 unless explicitly scoped. Log an open question for a follow-up task: should `AuditService.record` and `RoleService.assignRole` reject non-existent account UUIDs with a domain exception rather than relying on the database FK to throw?

**Confidence.** Medium.

---

## Finding 6 — No cleanup of created accounts or roles

**Issue.** The fixed tests create accounts and roles but do not roll back or delete them. This follows the module's existing convention, but it means the shared Testcontainers database accumulates state. Over many suite runs this could eventually affect test performance or mask issues.

**Evidence.**
- Both fixed tests lack `@Transactional` or teardown logic.
- Sibling integration tests follow the same pattern.

**Recommendation.** Accept this as the established convention for this module. If the suite grows large enough that accumulated state becomes a problem, address it as a cross-cutting test-hygiene task, not under T37.

**Confidence.** Low.

---

## Finding 7 — `registerAndActivate` uses the account as its own actor for activation

**Issue.** The helper calls `accountService.activateEmail(registered.accountUuid(), registered.accountUuid())`. This bypasses the admin-authorization check present on the HTTP endpoint. It is acceptable for test setup, but it means the test does not verify the real activation path's authorization boundary.

**Evidence.**
- `AuditTrailIntegrationTest.java` lines 98-99.
- `RoleAssignmentIntegrationTest.java` lines 42-43.
- `AdminAccountController.activate` requires `hasRole('ADMIN')`.

**Recommendation.** Add a comment in the helper explaining that `AccountService.activateEmail` is used directly as test setup and intentionally bypasses the admin HTTP authorization. This documents the helper's scope.

**Confidence.** Low.

---

## Summary

The Group C fix is minimal, correct, and consistent with the module's conventions. No correctness bug was found. The most material findings are the changed test contract (Finding 1) and the unresolved service-layer validation question (Finding 5), both of which should be documented rather than silently accepted.

(End of Phase 8 independent review.)
