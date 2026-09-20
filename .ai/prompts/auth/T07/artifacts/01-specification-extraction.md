# auth · T07 — Phase 1: Specification Extraction

## Business Rules

- **R12.** `password-reset-request` returns the identical acknowledgement regardless of whether
  the submitted email belongs to any account.
- **R13.** When the email matches an `ACTIVE` or `LOCKED` account (not `DELETED`/`SUSPENDED`), an
  `auth.email.requested` event with purpose `password_reset` is emitted in the same transaction as
  issuing the token.
- **R14.** A valid, unused, unexpired token plus a new password to `password-reset` updates the
  password hash, revokes *all* refresh-token families for that account, and records a
  `password.reset` audit event. (Password-*policy* compliance is explicitly out of this task's
  scope — see Phase 0's resolved finding (a); task 9 wires `PasswordPolicy` in separately.)
- **R15.** Every rejection reason (invalid, expired, already used, or a deleted/suspended-account
  token) produces the identical failure response.

**Wording note on R15 (not a blocker, flagged for transparency):** the requirement's literal text
says the failure response must be "indistinguishable from a valid token." Read literally this
would mean failure and success produce the same response, which contradicts R14 (success updates
the password and returns a distinct outcome) and the established L5/R5 pattern from T06 (failure is
uniform *among rejection reasons*, not indistinguishable from success). Interpreting this the same
way R5 was interpreted for `verify-email`: R15 means "indistinguishable from [the response for]
*any other invalid* token" — i.e., no rejection reason is distinguishable from another. This
mirrors T06's resolved precedent exactly and is the only reading consistent with R14 existing at
all.

## Locked Decisions

- **L5.** Enumeration-safe responses — governs both endpoints' response shapes.
- **L11** (widened, directly operative, as in T06). Both new public paths must be registered in
  `PublicEndpoints`.

## Files involved

**Existing, to extend:**
- `account/AccountController.java` — add `POST /accounts/password-reset-request`,
  `POST /accounts/password-reset`.
- `account/AccountService.java` — new methods wiring `VerificationTokenService`
  (`PASSWORD_RESET` purpose), `Account.changePasswordHash`, the new refresh-family revocation
  capability, and the `password.reset` audit record.
- `common/PublicEndpoints.java` — two new entries.
- `token/RefreshTokenTracker.java` — new method to revoke all families for a principal (Phase 0
  finding (b): no existing method does this; `RefreshTokenFamilyRepository
  .findByPrincipalNameAndRevokedAtIsNull` already exists, unused, and looks scaffolded for exactly
  this).

**Existing, to read/reuse, not modify:**
- `account/VerificationTokenService.issue`/`.consume` (T05) — `PASSWORD_RESET` purpose, unused
  until now.
- `account/Account.changePasswordHash(String)` (pre-existing) — guarded only against `DELETED`.
- `account/AccountService.VerificationTokenRejectedException` (T06) — its own javadoc already
  describes it generically ("every reason a verification token redemption can fail"), not
  verify-email-specific; the expected reuse target for R15's uniform rejection rather than a new
  parallel exception type.
- `common/ProblemTypes.INVALID_TOKEN` (T06) — expected reuse target, not a new constant.
- `account/event/EmailRequestedEventPayload` (T06) — already purpose-generic (`purpose` is a plain
  `String`); this task supplies `"password_reset"` where T06 supplied `"verify_email"`.
- `events/EventTopics` — already has the `"verification-token" -> "auth.email.requested"` mapping
  from T06; no change needed.
- `token/RefreshTokenFamily.revoke(String reason, Instant now)` (pre-existing, used by
  reuse-detection) — the mutation the new tracker method will call per matched family.
- `common.SecurityBeansConfig.passwordEncoder()` (existing bean, already injected into
  `AccountService`) — hashes the new raw password before `changePasswordHash`.

**New, per `design.md` §6's package map:**
- `account/dto/PasswordResetRequest.java` (the request-step DTO — email).
- `account/dto/PasswordResetConfirmRequest.java` (the confirm-step DTO — token + new password,
  per Phase 0's reading of R14's single-endpoint wording).

**Not touched by T07** (confirmed via Phase 0's investigation of task 9's actual scope):
`account/PasswordPolicy.java`, `PasswordPolicyProperties.java`.

## Dependencies

- `VerificationTokenService.issue(accountUuid, VerificationToken.Purpose.PASSWORD_RESET)` /
  `.consume(rawToken)`.
- `AccountRepository.findByEmail`, `.findByAccountUuid`.
- `Account.changePasswordHash(String)`, `Account.getStatus()`.
- `PasswordEncoder.encode(String)` (existing bean).
- A new `RefreshTokenTracker` method (exact name/signature is a Phase 2/5 decision) — revokes every
  unrevoked family for a given principal (= account UUID string, confirmed via
  `ReuseDetectingAuthorizationService`'s own comment: *"principalName is the account UUID for
  interactive grants"*).
- `AuditService` (existing, via `AccountService`'s existing `recordAudit` helper) for
  `password.reset`.
- `OutboxPublisher`, `EventTopics` (no changes needed — reusing T06's mapping).
- `AccountService.VerificationTokenRejectedException`, `common.ProblemTypes.INVALID_TOKEN` (both
  reused from T06, no new type expected).

## Acceptance Criteria

- **R12** — `password-reset-request` returns the same acknowledgement for a matching email, a
  non-matching email, and an email belonging to a `PENDING_VERIFICATION`/`DELETED`/`SUSPENDED`
  account.
- **R13** — only a matching email whose account is `ACTIVE` or `LOCKED` results in a token being
  issued and `auth.email.requested` (purpose `password_reset`, aggregate ID = account UUID,
  mirroring T06 Finding 6) being emitted. `PENDING_VERIFICATION`, `DELETED`, and `SUSPENDED`
  accounts must **not** trigger issuance — this is a materially different status filter from T06's
  `resend-verification` (which only fires for `PENDING_VERIFICATION`), worth calling out
  explicitly so Phase 5/6 doesn't copy T06's filter by habit.
- **R14** — a valid token plus a new password updates the password hash (via the injected
  `PasswordEncoder`), revokes every unrevoked refresh-token family for that account (and *only*
  that account — a different account's family must be untouched), and records a `password.reset`
  audit event.
- **R15** — every rejection reason (not found, expired, used, `DELETED`/`SUSPENDED` account)
  produces the identical response — reusing T06's existing `VerificationTokenRejectedException`/
  `INVALID_TOKEN` mapping.

## Tests required

**Named (`package.md` §8):**
- `shouldEmitPasswordResetEventOnlyWhenEmailExists` — read literally this only mentions "exists,"
  but R13's actual condition is existence *and* `ACTIVE`/`LOCKED` status; the test should cover
  both dimensions (nonexistent email *and* existing-but-wrong-status email both produce no event).
- `shouldResetPasswordAndRevokeAllFamiliesWithValidToken` — password updated, all families revoked,
  audit recorded, all in one assertion sweep.

**Boundary/implied tests:**
- `password-reset-request` for a `LOCKED` account — must still issue+emit (distinguishing this
  task's filter from T06's `PENDING_VERIFICATION`-only one).
- `password-reset-request` for a `PENDING_VERIFICATION` account — must **not** issue+emit (a
  registered-but-unverified user has no password to reset via this flow in the same sense; R13
  only names `ACTIVE`/`LOCKED`).
- `password-reset` with an expired/already-used/deleted-account token — uniform rejection,
  identical to T06's equivalent tests in shape.
- Revoke-all-families isolation: an account with multiple active families has all of them revoked;
  a *different* account's family is untouched.
- `password-reset`'s new-password path never logs or echoes the raw new password.

## Open Questions

No blockers. Two implementation-detail decisions are explicitly left open for Phase 2/5, not
treated as blockers here: the exact name/signature of the new `RefreshTokenTracker` revoke-all
method, and the exact revocation-reason string passed to `RefreshTokenFamily.revoke(...)`
(`"REUSE_DETECTED"` is the only existing precedent; a parallel value such as `"PASSWORD_RESET"` is
the obvious choice but not fixed by any requirement text found).
