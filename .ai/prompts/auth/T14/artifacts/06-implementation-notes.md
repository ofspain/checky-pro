# auth · T14 — Phase 6: Implementation Notes

Implements the frozen brief (`04-frozen-task-brief.md`) exactly per the plan
(`05-implementation-plan.md`). No test files touched in this phase (Phase 10's job) — production
code only, as planned.

## Changes

### `AccountService.java` — `adminUnlock(UUID, UUID)` (new)

Inserted immediately after the existing `unlock(UUID)`. Calls `unlock(accountUuid)` verbatim
(reusing its existing guard — `LOCKED → ACTIVE` only if currently `LOCKED` — rather than
duplicating the condition), re-fetches via the existing private `getAccount(accountUuid)`, then
unconditionally calls `publishLifecycleEvent(account, "user.unlocked")` and
`recordAudit("account.unlocked", accountUuid, actorUuid)`, then returns
`AccountResponse.from(account)`. Matches `suspend`/`reinstate`/`delete`'s exact four-step shape
(mutate → publish → audit → return) exactly, per the plan.

The self-invocation of `unlock(accountUuid)` from within `adminUnlock` doesn't go through Spring's
AOP proxy (a well-known same-class-call limitation), so `unlock`'s own `@Transactional` is
effectively a no-op in this call path — harmless, since `adminUnlock`'s own `@Transactional`
already owns the transaction boundary for the whole method.

Maps to: frozen brief AC1, AC2, AC3, AC4, AC7.

### `AdminAccountController.java` — `unlock(UUID, Authentication)` (new), constructor changed

Constructor gains a `LockoutService lockoutService` parameter — the first dependency this
controller has ever had on a service from another module. New method:
`@PostMapping("/{accountUuid}/unlock") @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")`,
calling `lockoutService.resetLockout(accountUuid)` (discarding the returned `LockoutDecision` —
the controller only needs the side effect) then returning
`accountService.adminUnlock(accountUuid, actorUuid(authentication))`. Two statements, matching
the plan exactly. Class Javadoc updated to explain the new cross-module dependency and why it
doesn't violate L12 (matches `AccountUserDetailsService`'s already-established precedent, T12
Phase 12).

Maps to: frozen brief AC1-AC5.

## Acceptance criteria — implementation status

| ID | Status |
|---|---|
| AC1 | Done — `resetLockout` clears `lockout_state`; `adminUnlock`'s `unlock(accountUuid)` call transitions `Account.status` |
| AC2 | Done — `unlock(UUID)`'s existing guard makes the status transition a no-op for a non-`LOCKED` account, no exception |
| AC3 | Done — `recordAudit("account.unlocked", accountUuid, actorUuid)` unconditional, `actorUuid` never defaulted to the target |
| AC4 | Done — `publishLifecycleEvent(account, "user.unlocked")` unconditional, alongside AC3 |
| AC5 | Done — `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")` on the new method, matching R20's header exactly |
| AC6 | Done — confirmed via inspection: `AccountService.java`'s new `adminUnlock` method imports nothing from `com.themistra.auth.authn`; only `AdminAccountController` (not `AccountService`) gains the new cross-module dependency |
| AC7 | Done — `adminUnlock`'s publish/audit calls are unconditional regardless of whether `unlock(accountUuid)` actually changed `Account.status`, so two calls produce two audit rows and two lifecycle events with identical `AccountResponse` content — proven at the unit-test layer, Phase 10 |

## Deviations from the plan

None. Every file, method signature, and call-order matches `05-implementation-plan.md` exactly.

**One thing worth flagging (not a deviation, a verification correction):** an initial
`mvn -pl services/auth test-compile` run appeared to succeed even after the constructor change,
which would have contradicted the plan's own expectation that `AdminAccountControllerTest.java`
breaks. Re-ran with `mvn clean test-compile` to rule out stale incremental-compilation output —
the break is real and exactly as expected (`AdminAccountControllerTest.java:31`, constructor
arity mismatch), deferred to Phase 10 per this pipeline's established Phase 6/10 test-authorship
boundary. Caught before it could be mis-reported as "no test impact."

## Build verification

```
mvn -pl services/auth clean compile
```
Zero errors — confirmed on a clean build, not an incremental/stale one.

```
mvn -pl services/auth clean test-compile
```
Fails with exactly the one pre-identified, expected error —
`AdminAccountControllerTest.java:31`, `AdminAccountController`'s constructor now requires
`(AccountService, LockoutService)`, the test still calls it with one argument. Deferred to Phase
10, per plan.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 14.
- Requirements: R20 (`requirements.md`).
- LOCKED decisions: L4 (unchanged), L12 (module boundary — verified clean via inspection:
  `AccountService.java` gains no `authn` import; only the controller does, matching
  `AccountUserDetailsService`'s already-established precedent).
- Frozen brief: `04-frozen-task-brief.md` — both authorized Files to Modify updated exactly as
  scoped; no file outside that list touched.
