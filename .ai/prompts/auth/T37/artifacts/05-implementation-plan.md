<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T37 · Phase 5 — Implementation Plan

Test-file-only change, no production code. Every planned file traces to the frozen brief's Files to
Modify.

## Files to create

None.

## Files to modify

- `services/auth/src/test/java/com/themistra/auth/audit/AuditTrailIntegrationTest.java`
- `services/auth/src/test/java/com/themistra/auth/authz/RoleAssignmentIntegrationTest.java`

## Change per file

### `AuditTrailIntegrationTest`

`recordMirrorsToKafkaWithoutLeakingIpOrUserAgent` currently starts with `UUID accountUuid =
UUID.randomUUID()` — no account is ever created, so `auditService.record(...)`'s
`repository.save(event)` violates `auth_audit_account_uuid_fkey`. Fix: register and activate a real
account first (matching every newer integration test's own established helper), use its UUID.
Requires autowiring `AccountService` and adding a small `registerAndActivate(String email)` private
helper (mirrors `SessionIntegrationTest`'s/`CleanupIntegrationTest`'s identical helper, one new
private method, no shared base class exists to reuse from — same self-contained convention every
sibling integration test in this module already follows).

**Expected residual, honestly flagged, not hidden**: this test also *waits on the real Kafka topic*
(`auth.security.audit`) via `Awaitility`. The Group C fix only addresses the synchronous FK failure
that currently pre-empts that wait — once fixed, the test will reach the Kafka wait and is expected
to then hit Group A's already-logged producer→broker connectivity issue instead, not turn fully
green in this environment. This is not a new problem the fix introduces; it's the pre-existing Group
A issue becoming visible now that Group C no longer masks it earlier in the same test.

### `RoleAssignmentIntegrationTest`

All three tests use `UUID accountUuid = UUID.randomUUID()` (or inline `UUID.randomUUID()` for the
third). `directAndTemplateExpandedRolesAreUnionedCorrectly` and
`removingATemplateRemovesItsContributionButNotDirectRoles` both call `roleService.assignRole(...)`,
which routes through `auditAssignment(...)` → the same `auth_audit` FK. `accountWithNothingAssignedHasNoEffectiveRoles`
never calls `assignRole` (read-only, already passing, untouched). Fix: add the same
`registerAndActivate` helper, call it once per test method that calls `assignRole`/`assignRoleTemplate`,
use the returned UUID instead of `UUID.randomUUID()`.

**No Kafka dependency in this file** — neither fixed test polls a Kafka topic, only
`roleService.resolveEffectiveRoles(...)` (a synchronous DB read). Both are expected to go fully
green after this fix, unlike `AuditTrailIntegrationTest`.

## Public methods

None — no production API changes.

## Private methods (new, one per file, not shared)

- `AuditTrailIntegrationTest.registerAndActivate(String email)` → `UUID`
- `RoleAssignmentIntegrationTest.registerAndActivate(String email)` → `UUID`

Both identical in body to the pattern already used in `SessionIntegrationTest`/
`CleanupIntegrationTest`: `AccountService.register(new RegisterAccountRequest(email, PASSWORD))`
then `.activateEmail(accountUuid, accountUuid)`.

## Entities used

`Account` (indirectly, via `AccountService` — no direct entity reference).

## Repositories used

None directly — `AccountService`'s own repository usage is unchanged.

## Services used

`AccountService` (`register`, `activateEmail`) — newly autowired into both test files.

## Tests required

None new. This *is* the fix to two existing tests — no additional test authorship.

## Execution order

1. Fix `AuditTrailIntegrationTest` — add `AccountService` autowiring, `registerAndActivate` helper,
   swap the random UUID for a real one.
2. Fix `RoleAssignmentIntegrationTest` — same pattern, applied to the two `assignRole`-calling tests.
3. `mvn -pl services/auth test-compile` — confirm clean compile.
4. `mvn -pl services/auth test -Dtest=RoleAssignmentIntegrationTest` — confirm both previously-failing
   methods now pass (Docker/Testcontainers required).
5. `mvn -pl services/auth test -Dtest=AuditTrailIntegrationTest` — confirm the FK error is gone;
   document whatever the Kafka-wait step now does (expected: Group A's already-logged timeout, not a
   pass, per the residual noted above — confirm this expectation rather than assume it).
6. Full suite re-run (`mvn -pl services/auth verify`) to confirm zero regressions elsewhere and an
   accurate final failure count against the frozen brief's AC1a/AC1b.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
