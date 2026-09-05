# auth · T14 — Phase 5: Implementation Plan

Plans the frozen brief (`04-frozen-task-brief.md`). No code below — signatures and call-order only.

## Files to create

None — this task authorizes zero new files per the frozen brief.

## Files to modify

1. `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — add
   `adminUnlock(UUID, UUID)`.
2. `services/auth/src/main/java/com/themistra/auth/account/AdminAccountController.java` — add
   `unlock(UUID, Authentication)`; constructor gains `LockoutService`.
3. `services/auth/src/test/java/com/themistra/auth/account/AdminAccountControllerTest.java` —
   constructor updated for the new `LockoutService` dependency; new test methods.
4. `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` — new test
   methods for `adminUnlock`.

Both authorized by the frozen brief's Files to Modify list.

## Public methods (signatures)

**`AccountService.java`** (existing class, one new method, inserted immediately after the
existing `unlock(UUID)` at line 330 — same neighborhood as the other `authn`-adjacent guard
methods):
- `@Transactional public AccountResponse adminUnlock(UUID accountUuid, UUID actorUuid)` — calls
  the existing `unlock(accountUuid)` verbatim (reusing its guard, no duplicated condition; a
  same-class self-invocation, so `unlock`'s own `@Transactional` is a no-op in this context —
  harmless, `adminUnlock`'s own annotation already owns the transaction), re-fetches via the
  existing private `getAccount(accountUuid)` helper (Hibernate's first-level cache makes this a
  no-cost re-read within the same persistence context, not a second round-trip), then
  `publishLifecycleEvent(account, "user.unlocked")`, then `recordAudit("account.unlocked",
  accountUuid, actorUuid)` — both unconditional, matching AC3/AC4/AC7 (recorded on the no-op
  branch too) — then `return AccountResponse.from(account)`. Mirrors `suspend`/`reinstate`/
  `delete`'s exact four-line shape (mutate → publish → audit → return), the only difference being
  the mutation is `unlock(accountUuid)` (a full method call reusing existing guard logic) rather
  than a bare `account.<verb>()` entity call.

**`AdminAccountController.java`** (existing class):
- Constructor signature changes from `AdminAccountController(AccountService accountService)` to
  `AdminAccountController(AccountService accountService, LockoutService lockoutService)` — the
  first new dependency this controller has ever needed (T12/T13 precedent: `authn` depending on
  `account`, not the reverse; here the controller itself, in the `account` package, depends on a
  service from `authn` — the same shape `AccountUserDetailsService` already established, not
  novel per T12 Phase 12's correction).
- `@PostMapping("/{accountUuid}/unlock") @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')") public
  AccountResponse unlock(@PathVariable UUID accountUuid, Authentication authentication)` — calls
  `lockoutService.resetLockout(accountUuid)` (discarding the returned `LockoutDecision` — the
  controller doesn't need it, `AccountResponse` is the return type), then `return
  accountService.adminUnlock(accountUuid, actorUuid(authentication))`. Exactly two statements,
  matching every sibling method's single-expression-body brevity as closely as the two-service
  orchestration allows.

## Private methods

None new. `adminUnlock` reuses three existing private/public helpers (`unlock`, `getAccount` via
`unlock`'s own call, `publishLifecycleEvent`, `recordAudit`) without adding any new private
method to either class.

## Entities used

- `Account` — read/mutated via the existing `unlock(UUID)` method's own guard; `adminUnlock`
  itself never touches `Account` fields directly, only through that existing call and the existing
  `getAccount`/`AccountResponse.from` helpers. No change to `Account.java`.

## Repositories used

- `AccountRepository` — indirectly, via the existing `getAccount`/`unlock` helpers. No new
  repository method, no direct repository access from the new code.
- `LockoutStateRepository` — indirectly, via `LockoutService.resetLockout` (T12, unchanged). Not
  touched by this task's own new code.

## Services used

- `LockoutService.resetLockout(UUID)` — T12, unchanged, called once from the controller.
- `AuditService.record(...)` — indirectly, via the existing `recordAudit` helper, unchanged.
- `OutboxPublisher.publish(...)` — indirectly, via the existing `publishLifecycleEvent` helper,
  unchanged.

## Unit/integration tests required

Per the frozen brief's Scope > Out (Phase 2, reaffirmed at Phase 4): unit-level only, no new
Testcontainers test. Both existing test files gain new methods; matches
`AccountServiceTest`/`AdminAccountControllerTest`'s established Mockito-based, no-Spring-context
convention.

**`AccountServiceTest.java`** (existing file, new tests):
- `shouldUnlockAccountViaAdminEndpoint` (named) — stub a `LOCKED` account, call `adminUnlock`,
  assert `account.getStatus() == ACTIVE`, `publishLifecycleEvent`'s outbox call captured with
  `eventType = "user.unlocked"` (via `outboxPublisher` mock, matching `AccountServiceTest`'s
  existing pattern for `suspend`/`reinstate`), and `recordAudit`'s underlying `auditService.record`
  captured with `eventType = "account.unlocked"`, `accountUuid` = target, `actorUuid` = the
  distinct caller UUID (AC3 — the one assertion proving `actorUuid` isn't silently defaulted to
  the target, unlike every self-service call site in this file).
- `adminUnlockOnAlreadyActiveAccountIsANoOpOnStatusButStillAuditsAndPublishes` (AC2/AC3/AC4/AC7) —
  stub an `ACTIVE` account, call `adminUnlock`, assert `account.getStatus()` stays `ACTIVE`
  (`Account.unlock()`'s own guard never throws `InvalidAccountStateException` because `unlock`'s
  own `if (status == LOCKED)` check skips it), but `publishLifecycleEvent`/`recordAudit` still
  fire — proving Finding 6/7's "status-transition idempotent, not side-effect idempotent"
  characterization at the unit layer directly.
- `adminUnlockCalledTwiceProducesTwoAuditRowsAndTwoLifecycleEvents` (AC7) — call `adminUnlock`
  twice in a row against the same stubbed account; assert `auditService.record`/
  `outboxPublisher.publish` are each invoked exactly twice, with identical `AccountResponse`
  content returned both times.

**`AdminAccountControllerTest.java`** (existing file, new tests):
- `unlockDelegatesToLockoutServiceThenAccountService` — verify `lockoutService.resetLockout(
  accountUuid)` is called, then `accountService.adminUnlock(accountUuid, actorUuid)` is called
  with the authenticated caller's UUID (not the target), and the controller returns exactly what
  `accountService.adminUnlock(...)` returned (matching `suspend`/`reinstate`'s existing
  delegation-proof test shape in this file).
- Authorization is proven by the existing `ArchitectureTest.admin_controller_handlers_require_
  preauthorize` rule (compile-time-verified, not a new runtime test) plus a direct assertion that
  the new `unlock` method carries `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")` — matching
  how this file (if at all) already proves annotation presence for sibling methods; if no such
  reflection-based check exists for `suspend`/`reinstate` today, none is added new for `unlock`
  either, to avoid introducing a testing pattern this file doesn't already use (confirmed absent
  or present at Phase 6/10, not assumed here).

## Execution order

1. `AccountService.java` — add `adminUnlock(UUID, UUID)` (depends on nothing new; the mutation
   path reuses `unlock(UUID)`, T13, already present).
2. `AdminAccountController.java` — add `unlock(UUID, Authentication)`; constructor gains
   `LockoutService` (depends on step 1 existing).
3. **First build checkpoint**: `mvn -pl services/auth compile` — this task lands after T13 made
   this possible for the first time; re-confirm it still holds after this task's own two-file
   change.
4. `AccountServiceTest.java` — new tests for step 1.
5. `AdminAccountControllerTest.java` — new tests for step 2, including the constructor update.
6. `mvn -pl services/auth test-compile` then `test` (or the isolated `javac` + JUnit Platform
   Launcher workaround if the module-wide run still surfaces the six pre-existing, unrelated
   issues T13 Phase 10 discovered and explicitly left unfixed — those remain out of this task's
   scope regardless of whether they resurface here).

No new Flyway migration, no new config keys, no new Testcontainers test — confirmed at Phase 0/1/2,
reconfirmed here.
