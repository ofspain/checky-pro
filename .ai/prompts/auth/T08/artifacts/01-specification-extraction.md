# auth · T08 — Phase 1: Specification Extraction

## Business Rules

- **R11.** WHEN an authenticated caller submits their current password and a new password meeting
  policy to `POST /accounts/me/password`, THEN the system SHALL update the password hash.

No other requirement ID is in scope. R8–R10 (password policy itself) and R21 (indistinguishable
login response) are referenced only as context for the Open Questions below, not implemented here.

## Locked Decisions

- **L2.** NIST 800-63B password policy: 12–128 characters, no composition rules, no forced
  rotation; HIBP k-anonymity breach screening; fail-open with an audit event if the range API is
  unreachable. Governs what "a new password meeting policy" must mean *if* this task enforces it
  (see Open Questions) — the policy's content itself is not renegotiable regardless.
- **L3.** BCrypt via the existing delegating `PasswordEncoder` (`{bcrypt}`, strength 12,
  `SecurityBeansConfig`) — both encoding the new password and verifying the current one must go
  through this same encoder.

**Note, not a scoped LOCKED decision but directly relevant:** L5 (enumeration-safe responses)
lists login, registration, password-reset request/confirm, and email verification —
`POST /accounts/me/password` is not on that list (confirmed at Phase 0). Nothing here requires this
endpoint's failure responses to be uniform with anything else.

## Files involved

**Existing — read/extend:**
- `account/AccountController.java` — new `POST /accounts/me/password` handler, following the
  existing `/me` (GET) pattern for deriving the caller's UUID from `Authentication`.
- `account/AccountService.java` — new method; constructor already carries `PasswordEncoder`,
  `AuditService`, `OutboxPublisher`, `Clock`.
- `account/AccountExceptionHandler.java` — new mapping for a wrong-current-password rejection.
- `account/Account.java` — reuse `changePasswordHash(String)` as-is; no entity change expected.
- `common/SecurityBeansConfig.java` — read only, confirms the `PasswordEncoder` bean shape.
- `account/PasswordPolicy.java` / `PasswordPolicyProperties.java` — read only pending the Open
  Questions below; no change to either expected regardless of the wiring decision.

**New — spec-expected (`design.md` §6):**
- `account/dto/ChangePasswordRequest.java` — fields not specified verbatim in the spec; R11's own
  wording ("their current password and a new password") implies `currentPassword`/`newPassword`.

## Dependencies

- `PasswordEncoder.matches(rawCurrentPassword, account.getPasswordHash())` — not used anywhere
  else in this module yet; this task is its first caller.
- `PasswordEncoder.encode(newPassword)` — same encoder already used by `register`/`resetPassword`.
- `AccountRepository.findByAccountUuid(UUID)` — existing finder, same one `getByUuid`/
  `activateEmail` etc. already use.
- `Account.changePasswordHash(String)` — existing guarded mutator (T07).
- `AuditService.record(RecordAuditEventRequest)` — pending Open Questions (no named event in spec).
- `OutboxPublisher` / `EventTopics` — likely **not** needed; R11 names no event, unlike R3/R13/R14.
- `PasswordPolicy.validate(String)` — pending Open Questions (wiring decision).
- No new `@ConfigurationProperties` keys implied by R11's text.

## Acceptance Criteria

| # | Criterion | Requirement |
|---|---|---|
| AC1 | An authenticated caller presenting the correct current password and *some* new password gets the account's password hash updated. | R11 |
| AC2 | An authenticated caller presenting an incorrect current password is rejected; the password hash is unchanged. | R11 ("protected by current password") |
| AC3 | The new password is hashed via the same `PasswordEncoder`/`{bcrypt}` path as every other credential write. | L3 |
| AC4 | *(Conditional on Open Question 1)* A new password outside the 12–128 length bound, or one that appears in the HIBP breach range, is rejected. | R11 ("meeting policy"), L2 |
| AC5 | The caller's own identity comes from the JWT `sub` (`Authentication.getName()`), never a path/body-supplied account identifier. | Established pattern (`/me`), implicit in "own password" |

## Tests required

- **Named test:** `shouldRejectPasswordShorterThan12OrLongerThan128` (`package.md` §8, mapped to
  R11). **This is the crux of Open Question 1** — see below; an identically-named test already
  exists in `PasswordPolicyTest.java` (T03), testing `PasswordPolicy.validate()` directly, with no
  dependency on `AccountService` at all. Whether this task needs its *own* test of this name
  (exercising the policy through the new change-password path) depends entirely on whether this
  task wires `PasswordPolicy` in.
- Boundary tests implied by AC1/AC2 regardless of the Open Question 1 outcome:
  - Success: correct current password + valid new password → hash updated, encoder called with
    the raw new password (not a hash of it).
  - Wrong current password → rejected, `passwordEncoder.encode(newPassword)` never called,
    `Account.changePasswordHash` never invoked (hash provably unchanged).
  - Controller-level: correct status code(s) for success/failure, delegation to the right
    `AccountService` method with the right arguments (mirrors `AccountControllerTest`'s existing
    pattern for every other self-service endpoint).
  - `AccountExceptionHandler`-level: the new wrong-current-password exception maps to its intended
    HTTP status/problem type (pending Open Question 2's answer on whether it's a distinct or
    generic shape).
- If Open Question 1 resolves to "wire `PasswordPolicy` in this task": add rejection tests for
  too-short/too-long new passwords and a breached new password, mirroring
  `PasswordPolicyTest`/`BreachCheckClientTest`'s existing fixtures rather than duplicating them.
- If Open Question 3 resolves to "revoke sessions on change": a
  `RefreshTokenTracker.revokeAllForPrincipal` verification test, mirroring T07's
  `shouldResetPasswordAndRevokeAllFamiliesWithValidToken`.

## Open Questions

1. **Does T08 wire `PasswordPolicy` into change-password, or is that task 9's job?**
   `tasks.md` task 9 is explicitly "Password policy enforcement. Apply `PasswordPolicy` to
   registration, change-password, and password-reset" — change-password is named there by name.
   T07 hit the identical tension for password-reset and got an explicit human decision to defer to
   task 9. Pulling the opposite way: this task's own named test
   (`shouldRejectPasswordShorterThan12OrLongerThan128`) is mapped to **R11** specifically in
   `package.md` §8, not to R8–R10 — the only named test anywhere in the spec that ties a
   policy-length assertion to *this* requirement. If T08 defers policy wiring like T07 did, this
   named test would have no home *inside T08's own scope* (it would only exist, already satisfied,
   over in `PasswordPolicyTest.java`). This is a real blocker for Phase 2's design — recommend
   raising it explicitly (not assuming the T07 answer carries over) before implementation planning.
2. **Should a wrong-current-password rejection be a distinct, informative error, or folded into a
   generic shape?** Not enumeration-sensitive per L5's own scope (Phase 0), but the spec doesn't
   say either way. Affects the exception design in Phase 2.
3. **Does a successful change revoke refresh-token families, like T07's reset does?** R11's text
   doesn't say so (unlike R14, which names it explicitly). Genuine ambiguity, not an oversight to
   silently resolve either way.
4. **Is a `password.changed` (or similarly named) audit event expected?** No name appears anywhere
   in `requirements.md`/`design.md`/`package.md` for this action, unlike R14's explicit
   `password.reset`. `agents.md`'s general audit rule suggests one probably should exist, but the
   exact event name/shape isn't spec-verbatim.

No item in `package.md` §11 (Q1–Q6) bears on this task.
