# auth · T09 — Phase 6: Implementation Notes

Implements the frozen brief (`04-frozen-task-brief.md`) exactly per the plan
(`05-implementation-plan.md`). No test files touched in this phase (Phase 10's job), except that
none needed touching yet — production code only, as planned.

## Changes

### `RegisterAccountRequest.java`

Removed `@Size(min = 12, max = 128)` from `password()`. Only `@NotBlank` remains on that field.
Updated the class Javadoc to state `PasswordPolicy` is now the sole content-enforcement point for
both length and breach screening, replacing the old comment that only mentioned breach screening
being separate. `@Email`/`@Size(max = 254)` on `email()` are untouched — out of scope, unrelated to
password policy.

Maps to: frozen brief Finding 5/6 resolution, AC1.

### `AccountService.java` — `register`

Reordered from `existsByEmail → construct+encode → save` to `construct+encode → validate →
existsByEmail → save`:

```java
Account account = Account.register(email, passwordEncoder.encode(request.password()));
passwordPolicy.validate(request.password(), account.getAccountUuid(), account.getAccountUuid());

if (accountRepository.existsByEmail(email)) {
    throw new DuplicateEmailException();
}
```

`Account.register(...)` already assigns `accountUuid` at construction (unchanged, confirmed at
Phase 0/3 — no entity change), so `account.getAccountUuid()` is a real, correlatable UUID at the
point `validate` runs, and would be the actual persisted account's UUID if registration succeeds.
Added a Javadoc paragraph explaining why the ordering is fixed this way (enumeration safety, L5)
and naming the accepted trade-off (an extra BCrypt encode spent on registrations later rejected for
a duplicate email — human-approved at Phase 4 over changing `Account.register`'s signature).

Maps to: frozen brief Finding 1/2 resolution, AC1-AC4.

### `AccountService.java` — `resetPassword`

Inserted `passwordPolicy.validate(newPassword, accountUuid, accountUuid);` immediately after the
existing eligibility check and before the existing `LOCKED` → `unlock()` branch:

```java
if (!isPasswordResetEligible(account)) {
    throw new VerificationTokenRejectedException();
}
passwordPolicy.validate(newPassword, accountUuid, accountUuid);

if (account.getStatus() == AccountStatus.LOCKED) {
    account.unlock();
}
account.changePasswordHash(passwordEncoder.encode(newPassword));
```

`accountUuid` here is the same variable already resolved by `consumeForPurpose` earlier in the
method — no new lookup needed. Everything after `validate` (`unlock`, `changePasswordHash`,
`revokeAllForPrincipal`, `recordAudit`) keeps its existing relative order, now strictly downstream
of a passing policy check. Added a Javadoc paragraph documenting the accepted residual risk (the
reset-token-validity signal, Finding 3) and why it was accepted rather than closed.

Maps to: frozen brief Finding 4 resolution (ordering), Finding 3 (accepted, documented, not
code-changed), AC5-AC8.

## Acceptance criteria — implementation status

| ID | Status |
|---|---|
| AC1 | Done — `@Size` removed, `PasswordPolicy.validate` is the sole length gate for `register` |
| AC2 | Done — same call covers breach screening (R9), no separate code path |
| AC3 | Done — fail-open/audit behavior is entirely internal to `PasswordPolicy.validate`, untouched |
| AC4 | Done — `validate` now runs before `existsByEmail`; a policy violation propagates identically regardless of whether the email is new or already registered |
| AC5 | Done — `validate` added to `resetPassword`, covers length |
| AC6 | Done — same call covers breach screening |
| AC7 | Done — fail-open/audit behavior internal to `validate`, untouched |
| AC8 | Done — `validate` runs before `unlock`/`changePasswordHash`/`revokeAllForPrincipal`/audit |
| AC9 | Done — no new exception type, no new `AccountExceptionHandler` mapping added; `PasswordPolicyViolationException` already maps to `400`/`VALIDATION_ERROR` since T08 |
| AC10 | Done (by inspection) — `changePassword` method body untouched |

Test-side proof of AC1-AC10 (verifying the ordering and propagation described above) is Phase 10's
job, not this phase's — no test files were modified here, matching the plan.

## Deviations from the plan

None. Implementation matches `05-implementation-plan.md`'s Execution order steps 1-3 exactly;
steps 4-6 (test files) are explicitly out of scope for this phase.

## Build verification

`mvn -pl services/auth compile` still cannot run to completion — the pre-existing, unrelated
`token` package compile break (tracked since T03, untouched by this branch) blocks it. Verified
instead by compiling both changed files directly against the module's resolved classpath:

```
javac -cp "$(cat /tmp/auth-cp.txt)" -sourcepath services/auth/src/main/java:services/auth/src/test/java \
  services/auth/src/main/java/com/themistra/auth/account/AccountService.java \
  services/auth/src/main/java/com/themistra/auth/account/dto/RegisterAccountRequest.java
```

Clean compile, no errors, no warnings.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 9.
- Requirements: R8, R9, R10 (`requirements.md`).
- LOCKED decisions: L2 (unchanged content, two new callers), L5 (register's ordering now locks in
  the enumeration-safety guarantee explicitly, per Phase 4).
- Frozen brief: `04-frozen-task-brief.md`, all Files to Modify from that brief's production list
  are covered; no file outside that list was touched.
