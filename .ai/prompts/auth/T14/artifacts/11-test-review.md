# auth · T14 — Phase 11: Test Review

Reviewed the Phase 10 test additions against the Phase 9-resolved implementation, the frozen brief (`04-frozen-task-brief.md`), and `agents.md`. The new tests correctly encode the Phase 9 narrowing (events/audit only on a real `LOCKED → ACTIVE` transition) and cover the named test `shouldUnlockAccountViaAdminEndpoint`. The gaps below are the cases or assertions still missing from T14's unit-level coverage.

---

## Gap 1 — No-op path is not exercised for `DELETED` or `PENDING_VERIFICATION`

- **Why it matters:** Phase 9 Finding A changed the event/audit guard to "only fire on a real `LOCKED → ACTIVE` transition" precisely because the original no-op applied to *any* non-`LOCKED` status, including `SUSPENDED`, `DELETED`, and `PENDING_VERIFICATION`. The current suite tests `ACTIVE` and `SUSPENDED` no-ops (`adminUnlockOnAlreadyActiveAccountIsANoOpAndDoesNotAuditOrPublish`, `adminUnlockOnSuspendedAccountLeavesStatusUnchangedAndDoesNotAuditOrPublish`) but leaves the other two enum values unproven.
- **Suggested test:** Add tests (or a single parameterized JUnit 5 test over `{ACTIVE, SUSPENDED, DELETED, PENDING_VERIFICATION}`) calling `adminUnlock` on each non-`LOCKED` status and asserting the status is unchanged and `outboxPublisher.publish`/`auditService.record` are never invoked.

---

## Gap 2 — `user.unlocked` lifecycle payload status is not asserted

- **Why it matters:** The Phase 9 decision was triggered by Kimi's observation that `UserLifecycleEventPayload.status` can literally carry `SUSPENDED` or `DELETED` in a `user.unlocked` event. `shouldUnlockAccountViaAdminEndpoint` only verifies the outbox call's aggregate type, event type, and schema version with `any()` for the payload and `anyString()` for the aggregate id. It does not prove the payload contains the expected `AccountStatus.ACTIVE` or the correct account UUID.
- **Suggested test:** In `shouldUnlockAccountViaAdminEndpoint`, capture the payload with an `ArgumentCaptor<UserLifecycleEventPayload>` and assert `payload.accountUuid()` equals the target UUID and `payload.status()` equals `AccountStatus.ACTIVE`.

---

## Gap 3 — AC5 role rejection is verified only by annotation presence, not behavior

- **Why it matters:** The frozen brief's Required Tests list explicitly includes "Authorization rejection for a caller with neither role (AC5)." `ArchitectureTest.admin_controller_handlers_require_preauthorize` only proves the `@PreAuthorize` annotation exists on every admin handler; it does not prove the expression is `hasAnyRole('ADMIN', 'COMPLIANCE')` or that Spring Security rejects a caller with `USER`/`MERCHANT`/no role. A typo in the role list (e.g., `hasAnyRole('ADMIN')`) would pass the ArchUnit test but fail AC5.
- **Suggested test:** Add a Spring `MockMvc`/`WebTestClient` test that calls `POST /admin/accounts/{accountUuid}/unlock` with a principal holding only `ROLE_USER` and expects `403 Forbidden`, matching the security-level testing pattern used elsewhere in the service (or add one if none exists for `AdminAccountController`).

---

## Gap 4 — Duplicate-call test does not assert full response equality

- **Why it matters:** The original AC7 said "Calling the endpoint twice produces the identical `AccountResponse` both times." After Phase 9 the duplicate-call test (`adminUnlockCalledTwiceOnlyAuditsAndPublishesOnce`) correctly narrows the audit/event count to one, but it only asserts `first.status()` and `second.status()` are both `ACTIVE`. It does not compare the full response objects (or at least `email`, `emailVerified`, and timestamps) for equality, so a regression that changed non-status fields between calls would not be caught.
- **Suggested test:** Replace the separate status assertions with `assertThat(first).isEqualTo(second)` (since the same mocked `Account`/`Clock` fixture is used, the responses should be equal), or assert each field explicitly.

---

## Gap 5 — Missing `AccountNotFoundException` boundary for `adminUnlock`

- **Why it matters:** `adminUnlock` calls `getAccount(accountUuid)` and will throw `AccountNotFoundException` for a non-existent target. This is a real boundary that affects the observable response (404 problem+json via `AccountExceptionHandler`). None of the sibling admin lifecycle methods in `AccountServiceTest` test this boundary either, but T14 introduces a new public method that exposes it.
- **Suggested test:** Add a test where `accountRepository.findByAccountUuid` returns `Optional.empty()` and assert that `service.adminUnlock(UUID, ACTOR_UUID)` throws `AccountNotFoundException`, with no calls to `publish` or `record`.

---

## Non-gaps (verified clean)

- **Phase 9 narrowing is correctly encoded:** the two no-op tests and the duplicate-call test together prove events/audit are now conditional on a real transition.
- **Actor threading is asserted:** `shouldUnlockAccountViaAdminEndpoint` and `unlockCallsResetLockoutThenAdminUnlockWithTheAuthenticatedActor` both prove the caller UUID reaches `recordAudit`/`adminUnlock`, not the target UUID.
- **Controller orchestration order is asserted:** `InOrder` verifies `resetLockout` precedes `adminUnlock`.
- **`UnnecessaryStubbingException`:** pre-existing, unrelated to T14; leaving it untouched matches the Phase 10 rationale.
