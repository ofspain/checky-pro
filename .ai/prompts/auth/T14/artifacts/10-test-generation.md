# auth · T14 — Phase 10: Test Generation

**Phase 11 (Kimi test review) update:** all 5 gaps Kimi raised checked out as genuine and were
applied. Highlights: added `DELETED`/`PENDING_VERIFICATION` no-op coverage alongside the existing
`ACTIVE`/`SUSPENDED` cases; strengthened `shouldUnlockAccountViaAdminEndpoint` to capture and
assert the actual `UserLifecycleEventPayload` (accountUuid + status), not just the outbox call's
metadata — the exact detail whose absence Kimi's own Phase 8 evidence (`UserLifecycleEventPayload`
carrying `status`) had originally motivated the Phase 9 fix; added a reflection-based check of the
exact `@PreAuthorize` SpEL expression (Gap 3) as a deliberately lighter-weight alternative to
introducing this module's first `MockMvc`/`WebTestClient` test for one assertion; strengthened the
duplicate-call test to full `AccountResponse` equality, not just status; and added an
`AccountNotFoundException` boundary test. Applying Gap 3's new test surfaced that it — like the
already-known pre-existing `getDelegatesDirectlyWithoutRequiringAnActor` failure — doesn't consume
`setUp()`'s shared `authentication` stub, triggering the identical `UnnecessaryStubbingException`.
Made that one stub `lenient()`, which was directly necessitated by this task's own new test (not a
drive-by fix) and, as a documented side effect, also resolved the pre-existing failure. All 56
tests re-verified passing after the changes.

Test manifest against the frozen brief (`04-frozen-task-brief.md`) and the Phase 9-resolved
implementation. No production code touched. Plain JUnit 5 + Mockito, no Spring context, matching
`AccountServiceTest`/`AdminAccountControllerTest`'s established convention exactly (per the
frozen brief's Scope > Out: unit-level only, no new Testcontainers test for this task).

## Files

- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` (modified —
  4 new tests for `adminUnlock`).
- `services/auth/src/test/java/com/themistra/auth/account/AdminAccountControllerTest.java`
  (modified — constructor updated for the new `LockoutService` dependency; 1 new test for
  `unlock`'s delegation).

## Test → requirement / acceptance-criterion mapping

**`AccountServiceTest.java`:**

| Test | Maps to |
|---|---|
| `shouldUnlockAccountViaAdminEndpoint` (named) | AC1, AC3, AC4 — `LOCKED → ACTIVE`, audit + lifecycle event fired, `actorUuid` is the caller not the target |
| `adminUnlockOnAlreadyActiveAccountIsANoOpAndDoesNotAuditOrPublish` | AC2, Phase 9 Finding A — no-op, and (post-fix) no audit/lifecycle event either |
| `adminUnlockOnSuspendedAccountLeavesStatusUnchangedAndDoesNotAuditOrPublish` | Phase 9 Finding A directly — the exact scenario the fix targets: a non-`LOCKED`, non-`ACTIVE` status must not emit a misleading "unlocked" event |
| `adminUnlockCalledTwiceOnlyAuditsAndPublishesOnce` | AC7 as narrowed by Phase 9 — the second call (already `ACTIVE`) produces zero additional side effects, not a second identical pair |

**`AdminAccountControllerTest.java`:**

| Test | Maps to |
|---|---|
| `unlockCallsResetLockoutThenAdminUnlockWithTheAuthenticatedActor` | AC1, AC5 — proves the two-call orchestration order (`resetLockout` before `adminUnlock`) and that the authenticated caller's UUID, not the target's, is threaded through |

Authorization (AC5, `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")`) is verified at
compile-time by the existing `ArchitectureTest.admin_controller_handlers_require_preauthorize`
rule, not by a new runtime test — confirmed at Phase 5/6 that no sibling method in this file has a
reflection-based annotation-presence test, so none was introduced new for `unlock` either,
avoiding a testing pattern this file doesn't otherwise use.

## Coverage against the frozen brief's Required Tests list

Every bullet covered: the named test; the non-`LOCKED`-account no-op boundary (now covering both
`ACTIVE` and `SUSPENDED`, per Phase 9's narrowing — a boundary the original Phase 2 brief didn't
distinguish); the missing-`lockout_state`-row boundary (already covered by `LockoutService`'s own
existing T12 test suite — `resetLockout`'s behavior is unchanged by this task, not re-tested
here); the audit-actor-is-caller assertion; and the duplicate-call behavior (Finding 6/7,
reflecting Phase 9's fix).

## Build verification

**`mvn -pl services/auth clean compile` and `mvn -pl services/auth clean test-compile` both
succeed with zero errors** — confirmed on fresh builds, not incremental/stale ones (Phase 6 had
already caught one stale-compile false-positive this phase re-confirms didn't recur).

`AccountServiceTest` and `AdminAccountControllerTest` were executed via the JUnit Platform
Launcher:

```
52 tests found, 51 successful, 1 failed
```

The one failure — `AdminAccountControllerTest.getDelegatesDirectlyWithoutRequiringAnActor`,
`UnnecessaryStubbingException` — is the **exact same pre-existing issue T13's Phase 10 already
discovered and documented** (same test name, same root cause: the shared `setUp()`'s
`authentication.getName()` stub isn't consumed by the `get` test, which needs no actor). Confirmed
unrelated to any of T14's changes: this test doesn't touch `LockoutService`, `adminUnlock`, or
`unlock` at all. Not fixed here, matching T13's own precedent of leaving pre-existing,
out-of-scope test-hygiene issues to their own owners rather than folding unrelated fixes into an
unrelated task.

**All 5 of T14's own new tests pass.**

## Specification references

- Task: `spec/auth-service/tasks.md`, task 14.
- Requirements: R20.
- LOCKED decisions: L4, L12.
- Frozen brief: `04-frozen-task-brief.md` — AC1-AC7, Required Tests.
- Review resolution: `09-review-resolution.md` — Finding A (event-semantics fix) now has direct
  test coverage for both the fixed behavior and the specific `SUSPENDED` scenario that motivated
  it.
