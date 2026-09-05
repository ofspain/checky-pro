<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T37 · Phase 9 — Review Resolution

**Human decision:** approve the recommended disposition — accept 1 (a code change), no-action-accept
2 (already Kimi's own recommendation), reject 4.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Fix changes the test contract to real-accounts-only, no longer covering non-existent-UUID behavior | **Rejected — already resolved.** Restates Phase 3 Finding 4/Phase 8's own Finding 5, already dispositioned at the Phase 4 human gate: logged as an out-of-scope follow-up (whether `AuditService.record`/`RoleService.assignRole` should validate account existence), not fixed under T37. No new evidence presented; no further action. |
| 2 | `registerAndActivate` helper duplicated across two files | **Accepted — no code change.** Matches Kimi's own recommendation ("no code change required now") and the module's established convention (every sibling integration test duplicates its own copy). |
| 3 | Extend `registerAndActivate` to the third, read-only test for future-proofing | **Rejected.** Speculative — guards against a hypothetical future change to `resolveEffectiveRoles` (a pure read today) acquiring a write side effect it does not have. Matches this session's standing practice of not designing for hypothetical future requirements with no current signal. The test is already correctly passing as written. |
| 4 | `AuditTrailIntegrationTest` asserts the Kafka payload via string-contains, not JSON parsing | **Accepted.** Replaced the `record.value()).contains("\"eventType\":\"account.suspended\"")` string check with `objectMapper.readTree(record.value())` + `payload.get("eventType").asText()`, matching the technique already established in `EndToEndLifecycleIntegrationTest` this session. The two `doesNotContain` negative checks (IP/user-agent absence) are left as string checks — they're genuinely about absence anywhere in the raw payload, not a specific field's value, so parsing adds no value there. |
| 5 | Service-layer validation question unresolved | **Rejected — already resolved.** Duplicate of Finding 1/Phase 3 Finding 5; same disposition, no new evidence. |
| 6 | No cleanup of created accounts/roles — DB growth | **Accepted — no code change.** Matches Kimi's own recommendation and the module's established no-rollback convention across every sibling integration test. |
| 7 | `registerAndActivate` self-actors the admin-only `activateEmail` path | **Verified accurate, rejected as a T37-scoped action.** Confirmed: `AccountService.activateEmail(UUID, UUID)` is documented as "the admin-initiated path, reachable only via the authenticated admin endpoint" (`AdminAccountController`'s `POST /admin/accounts/{accountUuid}/activate`, `@PreAuthorize("hasRole('ADMIN')")`); the helper passes the account's own UUID as `actorUuid`, which a real HTTP caller never could. But this is not something T37's fix introduces — the identical self-actor pattern already exists, verbatim, in at least four other already-merged helpers (`SasLoginIntegrationTest`, `ApiKeyLifecycleIntegrationTest`, `SessionIntegrationTest`, `CleanupIntegrationTest`'s equivalents). Adding a clarifying comment only to these two newly-touched files would inconsistently single them out while leaving the identical, older pattern uncommented everywhere else — out of scope for a task whose job is "make Group C pass," not "audit test-realism conventions module-wide." |

## Verification after applying fixes

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest=AuditTrailIntegrationTest` — re-run after Finding 4's change:
  same, expected Group A `ConditionTimeout` (unchanged failure signature) — confirms the assertion
  rewrite introduced no regression and the test still correctly reaches (and only fails at) the same
  already-logged environmental point.
- `RoleAssignmentIntegrationTest` unaffected by this phase (no findings applied to it).

---

**Phase 9 complete — review resolved, one fix applied, human-approved.** Proceed to Phase 10 (Test
Generation) on approval.
