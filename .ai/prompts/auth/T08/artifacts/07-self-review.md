# auth · T08 — Phase 7: Self-Review

Findings only, against the frozen brief (`04-frozen-task-brief.md`) and `agents.md`. No fixes
applied here — Phase 9 handles remediation after independent review (Phase 8).

---

## Finding 1 — `AccountServiceTest.java` no longer compiles (HIGH)

**Issue:** `AccountService`'s constructor gained a new `PasswordPolicy` parameter. The existing
`AccountServiceTest.setUp()` still constructs it with the old 7-argument signature. Confirmed by
direct compilation:

```
error: constructor AccountService in class AccountService cannot be applied to given types;
  required: AccountRepository,PasswordEncoder,OutboxPublisher,AuditService,VerificationTokenService,RefreshTokenTracker,PasswordPolicy,Clock
  found:    AccountRepository,PasswordEncoder,OutboxPublisher,AuditService,VerificationTokenService,RefreshTokenTracker,Clock
```

**Severity:** HIGH — blocks compilation of the module's existing test suite; expected per the
frozen brief's own Constraints/Phase 6 notes, same class of issue as every prior task's
constructor addition in this chain.

**Evidence:** `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java:68`;
`services/auth/src/main/java/com/themistra/auth/account/AccountService.java` (constructor).

**Recommendation:** Add a mocked `PasswordPolicy` to the test's `setUp()` constructor call.

---

## Finding 2 — `PasswordPolicyTest.java` no longer compiles at 11 call sites (HIGH)

**Issue:** `PasswordPolicy.validate` gained two required `UUID` parameters (`accountUuid`,
`actorUuid`). Every existing call in `PasswordPolicyTest.java` still uses the old 1-argument form.
Confirmed by direct compilation — 11 distinct errors, one per call site
(`PasswordPolicyTest.java:49,51,61,62,69,71,73,83,94,111,119`).

**Severity:** HIGH — blocks compilation of `PasswordPolicy`'s own existing unit tests, including
the named test `shouldRejectPasswordShorterThan12OrLongerThan128` that T08's own scope depends on.

**Evidence:** `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java`
(11 call sites); `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java`
(`validate`'s new signature).

**Recommendation:** Update all 11 call sites to pass two `UUID` arguments (any non-null test
fixture UUIDs suffice for the tests that don't specifically assert on them); add the AC10
assertion (breach-check-failure audit carries the real `accountUuid`/`actorUuid`) called for in
the frozen brief's Required Tests.

---

## Finding 3 — `PasswordPolicy`'s HIBP network call now executes inside a live `@Transactional` method for the first time in production (Medium)

**Issue:** `PasswordPolicy.validate` → `validateNotBreached` → `BreachCheckClient.isBreached`
makes a real outbound HTTP call (bounded by `themistra.auth.password.breach-check.timeout-ms`,
currently `3000`) to the Have I Been Pwned range API. Before this task, `PasswordPolicy` had zero
production callers — this network call has never actually executed inside a live transaction.
`AccountService.changePassword` is `@Transactional` and calls `passwordPolicy.validate` *after*
loading the `Account` entity but *before* the entity mutation, meaning a slow (up to 3s) or
timed-out breach-check call now holds a pooled database connection open for that duration on every
`change-password` request.

**Severity:** Medium — not a correctness defect (the fail-open/audit behavior is unchanged and
already covered by `PasswordPolicyTest`), and the timeout is bounded, not unbounded. But this is a
new class of operational exposure this task introduces to a real request path for the first time:
under HIBP latency/outages, `POST /accounts/me/password` requests could tie up connection-pool
capacity for up to 3 seconds each.

**Evidence:** `AccountService.java` (`changePassword`'s call to `passwordPolicy.validate`, inside
`@Transactional`); `application.properties:65`
(`themistra.auth.password.breach-check.timeout-ms=3000`); `BreachCheckClient.java` (synchronous
`RestClient` call).

**Recommendation:** Not a blocker for this task — the frozen brief explicitly authorized wiring
`PasswordPolicy` in as-is, and this exposure is inherent to `PasswordPolicy`'s existing design
(T03), not something T08 introduces incorrectly. Flagging so a human can consciously accept it (or
note it for task 9, which will make the same call from `register`/`resetPassword` too) rather than
it going unnoticed simply because this is the first call site to actually exercise it.

---

## Dimensions checked with no findings

- **Correctness (check ordering):** `changePassword`'s four gates — account-status, current-
  password match, new-password policy, then mutation — run in the exact fixed order the frozen
  brief specifies; each gate's exception is thrown before any subsequent gate or mutation runs.
- **Boundary conditions (status gate placement):** the `ACTIVE`-only check happens strictly before
  `passwordEncoder.matches`, so a `DELETED` account's `null` `passwordHash` is never passed to the
  encoder — the exact NPE risk Kimi's Phase 3 Finding 4 identified is closed.
- **Null-safety:** `@NotBlank` on both `ChangePasswordRequest` fields prevents blank/`null` values
  from ever reaching `AccountService`; `PasswordEncoder.matches`'s handling of its arguments is
  unchanged library behavior, not something this task alters.
- **Thread-safety:** no shared mutable state; per-request entity instances via JPA, consistent
  with every other service method in this module.
- **Transaction boundaries:** single `@Transactional` method; see Finding 3 for the one caveat
  otherwise, ordering and scope match the established `resetPassword` pattern.
- **Module boundaries:** no new cross-module dependency — everything touched
  (`account`, `common`) was already within this task's authorized file list.
- **Idempotency:** not applicable in the usual sense — a repeated call with the same
  current/new password pair after a successful first call correctly fails the second time (the
  "current" password has changed), which is the intended behavior, not a bug.
- **Money types:** N/A.
- **Enumeration-safety/secret-handling:** `ChangePasswordRequest.toString()` correctly omits both
  `currentPassword` and `newPassword`; no log statement in any changed file references either raw
  value. Not enumeration-sensitive per L5's own scope (confirmed at Phase 0/1), consistent with the
  frozen brief.
- **Readability/complexity:** `changePassword`'s body is linear, one gate per line, matching the
  established shape of `resetPassword`/`activateFromVerificationToken`.
- **Consistency:** `changePassword` reuses `getAccount` and `recordAudit` unchanged, exactly like
  every other `AccountService` method — no parallel/duplicate helper introduced.
