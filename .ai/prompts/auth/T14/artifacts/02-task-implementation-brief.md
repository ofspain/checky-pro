# auth · T14 · Phase 2 — Task Implementation Brief

## Task

Add `AdminAccountController.unlock(UUID, Authentication)` — `POST /admin/accounts/{accountUuid}/
unlock`, `ADMIN` or `COMPLIANCE` — plus one new `AccountService.adminUnlock(UUID, UUID)` method
that audits the action and returns the fresh `AccountResponse`, orchestrated alongside the
already-existing `LockoutService.resetLockout(UUID)` (T12).

## Purpose

`LockoutService.resetLockout` (T12) already performs R20's actual mutation (clears
`lockout_state`, transitions `Account.status` `LOCKED → ACTIVE` via the existing guarded
`AccountService.unlock`) but has no caller and no audit trail. This task exposes it through an
authenticated admin endpoint and adds the missing, admin-scoped audit event — without auditing
every other caller of the same underlying guarded `unlock()` (T13's login-flow-driven unlocks must
stay silent, as already decided).

## Scope

**In:**
- `AdminAccountController.unlock(UUID accountUuid, Authentication authentication)` — same shape as
  the file's existing `suspend`/`reinstate` methods exactly: `@PostMapping("/{accountUuid}/unlock")`,
  `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")`, actor resolved via the file's own private
  helper. Calls `lockoutService.resetLockout(accountUuid)` (clears counters, transitions status via
  its own existing internal call) then `accountService.adminUnlock(accountUuid, actorUuid)`
  (audits, returns `AccountResponse`).
- **`AccountService.adminUnlock(UUID accountUuid, UUID actorUuid)` (new, proposed here, subject to
  Phase 3 challenge):** reuses the same guard `unlock(UUID)` already applies (`LOCKED → ACTIVE`
  only if currently `LOCKED`, otherwise a no-op — redundant-but-harmless if
  `LockoutService.resetLockout`'s own internal call already did it, and a safety net if it
  somehow didn't), records `"account.unlocked"` via the existing private `recordAudit` helper with
  `actorUuid` as the caller (not the target account — the one place this task's audit shape
  differs from every self-service call site), returns `AccountResponse.from(account)`. This keeps
  `AccountService`'s dependency graph unchanged — it still never imports anything from `authn` —
  resolving Phase 1's Q1 without inverting the established `authn → account` direction (L12).
- `AdminAccountController`'s constructor gains a `LockoutService` dependency (new — the first time
  this controller needs it).

**Out:**
- Any change to `LockoutService.resetLockout`'s existing behavior (T12, frozen) or
  `AccountService.unlock`'s existing guard (T13, frozen).
- Any new Testcontainers integration test (Phase 1 Q2, resolved here): `AdminAccountControllerTest`'s
  established convention is unit-level (mocked services, no Spring context) — this task's
  underlying mechanics (`resetLockout`, `unlock`) are already integration-tested by T12/T13's own
  suites. A redundant admin-endpoint-specific Testcontainers test adds a third unexecuted-in-this-
  environment test to a codebase that already carries two — disproportionate for this task's
  surface. Unit-level coverage only.
- `contracts/api/auth.yaml` authorship — out of scope, same already-logged gap as T11-T13.

## Business Rules

- R20 — admin unlock transitions `LOCKED → ACTIVE`, clears the counter and `lock_count`. Fully
  covered by `LockoutService.resetLockout` (T12, unchanged); this task exposes and audits it.
- R43 — security-relevant actions are audited via the outbox. `AuditService.record` (pre-existing)
  already implements the mechanics; `AccountService.adminUnlock`'s `recordAudit("account.unlocked",
  ...)` call is this task's one new call site.

## Locked Decisions

- L4 — unchanged; no new lockout arithmetic.
- L12 — module boundaries. `AccountService` gains no dependency on `authn`/`LockoutService` — the
  new `adminUnlock` method only calls the existing private `recordAudit` helper and the existing
  guard logic, both already inside `AccountService`. The orchestration (calling both
  `LockoutService.resetLockout` and `AccountService.adminUnlock`) happens in the controller, which
  already legitimately depends on both packages' services (matching `AccountUserDetailsService`'s
  own precedent of a class depending on services from two modules, corrected as non-novel at T12
  Phase 12).

## Dependencies

- `LockoutService.resetLockout(UUID accountUuid)` — T12, unchanged.
- `AccountService.adminUnlock(UUID accountUuid, UUID actorUuid)` — new, this task.
- `AccountService.getAccount`/private `recordAudit` helpers — pre-existing, reused inside the new
  method, not exposed further.

## Inputs

- `accountUuid` — path variable, the target account.
- `authentication` — the authenticated admin/compliance caller; `actorUuid` resolved via the
  controller's existing `actorUuid(authentication)` helper (`UUID.fromString(authentication
  .getName())`).

## Outputs

- `AccountResponse` — same shape every sibling `AdminAccountController` method returns.

## State Changes

- `lockout_state` zeroed (via `LockoutService.resetLockout`, T12, unchanged).
- `accounts.status` transitions `LOCKED → ACTIVE` if currently `LOCKED` (via the existing guarded
  `unlock()` logic, reached twice — once inside `resetLockout`, once inside the new
  `adminUnlock` — both idempotent, no double-transition risk).
- New `auth_audit` row + outbox mirror: `eventType = "account.unlocked"`, `accountUuid` = target,
  `actorUuid` = caller.

## Files to Create

None.

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — add
  `adminUnlock(UUID, UUID)`.
- `services/auth/src/main/java/com/themistra/auth/account/AdminAccountController.java` — add
  `unlock(UUID, Authentication)`; constructor gains `LockoutService`.

## Files NOT to Modify

- `LockoutService.java`, `LockoutStateMachine.java` (T11/T12, frozen).
- `Account.java`, `AccountStatus.java`.
- Every other `AdminAccountController` method (`get`, `activate`, `suspend`, `reinstate`, `delete`)
  — unchanged.
- Anything under `spec/`.

## Acceptance Criteria

- **AC1 (→ R20).** `POST /admin/accounts/{accountUuid}/unlock` on a `LOCKED` account by an
  `ADMIN`/`COMPLIANCE` caller transitions it to `ACTIVE` and zeroes `lockout_state`.
- **AC2 (→ R20).** The same call on a non-`LOCKED` account (e.g. `ACTIVE`) is a safe no-op on
  `Account.status` — no exception, no corruption.
- **AC3 (→ R43).** Records `auth_audit`/outbox with `eventType = "account.unlocked"`,
  `accountUuid` = target, `actorUuid` = the caller (never equal to the target unless the caller
  happens to be unlocking their own account, an edge case not specially handled or forbidden).
- **AC4 (→ security).** A caller without `ADMIN` or `COMPLIANCE` role is rejected by
  `@PreAuthorize` before the handler body runs.
- **AC5 (→ L12).** `AccountService.java` gains no import from `com.themistra.auth.authn`.

## Required Tests

- `shouldUnlockAccountViaAdminEndpoint` (named).
- Non-`LOCKED`-account no-op boundary (AC2).
- Missing-`lockout_state`-row boundary (matches `resetLockout`'s existing T12 no-op behavior).
- Authorization rejection for a caller with neither role (AC4).
- Audit shape: `actorUuid` is the caller, not the target (AC3) — the test most specific to this
  task's own new behavior.

## Constraints

- **Security:** `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")`, matching R20's header
  exactly and this file's own `suspend`/`reinstate` precedent.
- **Module boundaries (L12):** as stated above — verified by `grep` in Phase 6/7, not just
  asserted.
- **Null handling:** `accountUuid`/`actorUuid` never null at any new method boundary (Spring
  guarantees non-null path variables and an authenticated `Authentication`'s resolved UUID).
- **Idempotency:** calling the endpoint twice in a row is safe — the second call is a no-op on
  `Account.status` (already `ACTIVE`) but still records a second audit event (matching how every
  other admin action in this file behaves — `suspend` on an already-`SUSPENDED` account would hit
  `Account.suspend()`'s own guard and throw, by contrast; `unlock`'s guard is deliberately
  softer, matching `LockoutService.resetLockout`'s own no-op-not-throw convention, T12).

## Open Questions

No blockers. Phase 1's Q1 (audit call site) resolved above; Q2 (integration test scope) resolved
in Scope > Out.
