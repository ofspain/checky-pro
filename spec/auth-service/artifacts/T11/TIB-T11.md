# Task 11 — Implementation Brief: Lockout State Machine

This document extracts everything required to implement Task 11 from the auth-service Phase 1 spec. It does not contain a design or implementation plan.

## Task Statement

From `tasks.md` §"Lockout and authentication", Task 11:

> **Lockout state machine.** Implement `LockoutStateMachine` with the 5-attempt / 30-min / 15-min rules. Unit-test boundaries.

## Business Rules

Derived from `requirements.md` §"Login, lockout, and unlock", `design.md` §4a, and `target-design.md` §4.

| # | Rule |
|---|---|
| BR1 | If a password login attempt fails for an `ACTIVE` account, increment the per-account failed-attempt counter. |
| BR2 | When the failed-attempt counter reaches 5 failed attempts within a rolling 30-minute window, transition the account to `LOCKED` for 15 minutes, increment `lock_count`, and record an `account.locked` audit event. |
| BR3 | When a locked account's lockout interval has elapsed, allow the next authentication attempt; if it succeeds, transition the account to `ACTIVE` and reset the failed-attempt counter and `lock_count`. |
| BR4 | If the failed-attempt counter does not reach 5 within 30 minutes of the last failure, the counter decays to zero. |
| BR5 | When an admin calls `POST /admin/accounts/{accountUuid}/unlock`, transition the account to `ACTIVE` and clear the failed-attempt counter and `lock_count`. |
| BR6 | If an account is `LOCKED`, `SUSPENDED`, `DELETED`, or does not exist, password authentication must fail with a response indistinguishable from bad credentials. |
| BR7 | Login failure must record a `login.failed` audit event. |
| BR8 | Lock/unlock are security-relevant actions and must be appended to `auth_audit` and mirrored to `auth.security.audit` via the outbox. |
| BR9 | The response to a locked login must be indistinguishable from bad credentials (no enumeration oracle). |

## Locked Decisions

From `design.md` §4a:

- **L4. Brute-force lockout.** 5 failed attempts within a rolling 30-minute window transition an `ACTIVE` account to `LOCKED` for 15 minutes. Each subsequent lock doubles the effective duration via `lock_count` until it is reset. Counter decays 30 minutes after the last failure.
- **L5. Enumeration-safe responses.** Login, registration, password-reset request, password-reset confirmation, and email verification endpoints return uniform responses that do not reveal whether an email exists, whether an account is locked/suspended/deleted, or whether a token is invalid.
- **L13. Secrets discipline.** No secret, credential, or signing key material is committed to the repo.

## Files involved

### Existing files that must be read and/or extended

| File | Role |
|---|---|
| `services/auth/src/main/java/com/themistra/auth/account/Account.java` | Aggregate with `lock()`/`unlock()` methods and `AccountStatus` transitions. |
| `services/auth/src/main/java/com/themistra/auth/account/AccountStatus.java` | Enum values: `PENDING_VERIFICATION`, `ACTIVE`, `LOCKED`, `SUSPENDED`, `DELETED`. |
| `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` | Currently handles register/activate/suspend/reinstate/delete/get; lockout integration will touch this layer. |
| `services/auth/src/main/java/com/themistra/auth/authn/AccountUserDetailsService.java` | Sets `accountLocked` based on `AccountStatus.LOCKED`; principal name is account UUID. |
| `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java` | SAS + application security filter chains; authentication-failure/success hooks land here or in an `AuthenticationEventPublisher` listener. |
| `services/auth/src/main/java/com/themistra/auth/audit/AuditService.java` | Must be called to record `login.failed` / `account.locked` / `account.unlocked` events. |
| `services/auth/src/main/java/com/themistra/auth/common/SecurityBeansConfig.java` | Provides injectable `Clock` bean used for lockout windows. |
| `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql` | Defines `lockout_state` table and `Account` status check constraint. |
| `services/auth/src/test/java/com/themistra/auth/account/AccountTest.java` | Existing `Locking` nested test for `Account.lock()`/`unlock()`. |
| `services/auth/src/test/java/com/themistra/auth/authn/AccountUserDetailsServiceTest.java` | Existing test showing `LOCKED` maps to `isAccountNonLocked() == false`. |

### New files expected by the spec

| File | Location |
|---|---|
| `LockoutStateMachine.java` | `services/auth/src/main/java/com/themistra/auth/authn/` |
| `LockoutService.java` | `services/auth/src/main/java/com/themistra/auth/authn/` |
| `LockoutProperties.java` | `services/auth/src/main/java/com/themistra/auth/authn/` |
| (optional lockout repository if needed) | `services/auth/src/main/java/com/themistra/auth/authn/` |
| `LockoutStateMachineTest.java` | `services/auth/src/test/java/com/themistra/auth/authn/` |

## Dependencies

### Existing dependencies in `services/auth/pom.xml`

- `spring-boot-starter-web`
- `spring-boot-starter-oauth2-authorization-server`
- `spring-boot-starter-oauth2-resource-server`
- `spring-boot-starter-data-jpa`
- `flyway-core`, `flyway-database-postgresql`
- `postgresql`
- `spring-boot-starter-test`
- `spring-security-test`

### Runtime/data dependencies

- PostgreSQL `auth.lockout_state` table (V1 migration).
- `Account` aggregate and `AccountService` for loading accounts and invoking `lock()`/`unlock()`.
- `AuditService` for recording `login.failed`, `account.locked`, and `account.unlocked`.
- Spring Security authentication success/failure events for hooking lockout counter rotation.
- Inject `Clock` for unit-testable time windows.

### Configuration keys (verbatim from `design.md` §4c)

```properties
themistra.auth.lockout.max-attempts=5
themistra.auth.lockout.window-minutes=30
themistra.auth.lockout.base-lock-minutes=15
```

## Acceptance Criteria

Mapped from `requirements.md` R16–R21 and `package.md` §8 test names.

- **AC1.** Increment per-account failed-attempt counter on a failed login for an `ACTIVE` account (R16).
- **AC2.** Transition to `LOCKED` exactly at 5 failed attempts within a rolling 30-minute window (R17).
- **AC3.** Lock duration is 15 minutes multiplied by `2^(lock_count)` after the base lock (L4).
- **AC4.** Failed-attempt counter decays to zero 30 minutes after the last failure if the threshold was not reached (R19).
- **AC5.** When the lock expires and the next login succeeds, the account returns to `ACTIVE` and both counter and `lock_count` reset (R18).
- **AC6.** Admin unlock endpoint transitions `LOCKED → ACTIVE` and clears counter and `lock_count` (R20).
- **AC7.** All lock/unlock/login-failure actions are recorded in `auth_audit` and mirrored to `auth.security.audit` (R43).
- **AC8.** Locked accounts produce login failures that are indistinguishable from bad credentials (R21 / L5).

## Tests Required

From `package.md` §8, the named tests relevant to Task 11:

- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` → R17
- `shouldResetLockoutCounterOnSuccessfulLogin` → R18
- `shouldUnlockAccountViaAdminEndpoint` → R20
- `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` → R18/R21

Additional tests implied by the task and existing conventions:

- Unit tests for `LockoutStateMachine` using a fixed `Clock`:
  - Counter increments on failure.
  - Lock triggers exactly at max attempts.
  - Lock duration scales with `lock_count`.
  - Counter decays after window elapsed.
  - Successful login after expiry resets state.
  - No lock if the 5 attempts span more than 30 minutes.
- Existing `AccountTest.Locking` already covers `Account.lock()`/`unlock()`.
- Existing `AccountUserDetailsServiceTest.lockedMapsToAccountLocked` already covers the mapping to Spring Security's locked flag.

## Open Questions

From `package.md` §11 relevant to lockout:

- **Q5. Lockout event publication.** Is lock/unlock published only as an `auth.security.audit` mirror, or also as a lifecycle event on `auth.user.lifecycle`? The schema currently only has status enum values.

Other open questions surfaced by this brief:

- Should `LockoutStateMachine` be a pure state-machine class that returns a decision/event, or should it also mutate the `Account` / `LockoutState` entities? The current spec says "pure domain logic" in `design.md` §6, but this is left to implementer.
- Should admin unlock come from a separate `AdminUnlockController` or an extension of `AdminAccountController`? The spec lists both options in `design.md` §6.
