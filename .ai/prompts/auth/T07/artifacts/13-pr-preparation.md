# auth · T07 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable — this task adds two new public endpoints and reuses T05/T06's verification-token and
refresh-token-family machinery, touching no schema and no existing public contract.

## Commit title

```
Add password reset flow endpoints (T07)
```

## Commit message

```
Add password reset flow endpoints (T07)

Extend AccountController with POST /accounts/password-reset-request
and POST /accounts/password-reset, both public. password-reset-request
issues a token and emits auth.email.requested (purpose password_reset)
only for ACTIVE/LOCKED accounts (R13), returning the same
acknowledgement regardless of match (R12/L5) - 200, matching
resend-verification's precedent rather than registration's 202
(Finding 5, human-confirmed). password-reset redeems the token,
updates the password hash, unlocks a LOCKED account (Finding 8,
human-confirmed: successful reset is proof-of-ownership strong enough
to also clear a lockout), revokes every refresh-token family for the
account, and records a password.reset audit event (R14).

Adds VerificationTokenService.consumeForPurpose(rawToken, Purpose) -
purpose-checked before any mutation, self-contained, doesn't touch the
existing purpose-blind consume()/verify(). Closes two bugs found
during design review, not in production:
- Finding 1: without a purpose check, an EMAIL_VERIFY token could
  redeem a password reset.
- The mirror-image regression, already shipped in T06:
  activateFromVerificationToken called the purpose-blind consume(),
  so a PASSWORD_RESET token could activate an account. Fixed in this
  same change (Phase 3/4, human-confirmed to close in T07 rather than
  file a separate task) by switching it to
  consumeForPurpose(rawToken, EMAIL_VERIFY).

Adds RefreshTokenTracker.revokeAllForPrincipal(principalName, reason),
the first caller of the existing (previously unused)
findByPrincipalNameAndRevokedAtIsNull finder.

R14's "policy-compliant new password" clause is intentionally not
enforced here - PasswordPolicy wiring for password-reset (along with
registration and change-password) belongs to task 9 per tasks.md's own
two-step design. Flagged and human-confirmed at Phase 0, not an
oversight; resetPassword currently only requires a non-blank string.

78 unit tests cover both named tests
(shouldEmitPasswordResetEventOnlyWhenEmailExists,
shouldResetPasswordAndRevokeAllFamiliesWithValidToken), every
consumeForPurpose rejection reason (wrong purpose in both directions,
expired, already-used, unusable account, the pre/post-check race),
every resetPassword rejection reason (ineligible status, missing
account, missing token match), the LOCKED-unlock path, family-revoke
isolation/idempotency, DTO validation and toString()/wording guards,
and both new endpoints' status codes and delegation.

Kimi's independent design review (Phase 8) found 13 findings including
the 2 CRITICAL purpose-confusion bugs above; all resolved or
explicitly accepted at Phase 4. Kimi's test review (Phase 11) found 8
coverage gaps (negative-path completeness, encoder call assertions,
no-stray-event assertions); all 8 applied.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production:**
- `services/auth/src/main/java/com/themistra/auth/account/AccountController.java` (modified —
  two new endpoints)
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` (modified —
  `requestPasswordReset`, `resetPassword`, `isPasswordResetEligible`; generalized
  `issueAndEmitVerificationEmail`; fixed `activateFromVerificationToken`'s T06 regression; new
  `RefreshTokenTracker` constructor dependency)
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java`
  (modified — new `consumeForPurpose` method)
- `services/auth/src/main/java/com/themistra/auth/account/dto/PasswordResetRequest.java` (new)
- `services/auth/src/main/java/com/themistra/auth/account/dto/PasswordResetConfirmRequest.java`
  (new)
- `services/auth/src/main/java/com/themistra/auth/account/dto/RegistrationAcknowledgement.java`
  (modified — new `forPasswordReset()` factory)
- `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java` (modified — two
  new entries)
- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenTracker.java` (modified —
  new `revokeAllForPrincipal` method)

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenServiceTest.java`
  (modified — 10 new tests for `consumeForPurpose`, purpose-parameterized `stubToken` overload)
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` (modified —
  constructor fix, 4 stale-stub fixes, 11 new tests for `requestPasswordReset`/`resetPassword`)
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` (modified
  — 3 new tests for the two new endpoints)
- `services/auth/src/test/java/com/themistra/auth/account/dto/PasswordResetDtoTest.java` (new —
  4 tests: DTO validation, `toString()` redaction, acknowledgement wording)
- `services/auth/src/test/java/com/themistra/auth/token/RefreshTokenTrackerTest.java` (modified
  — 4 new tests for `revokeAllForPrincipal`)
- `services/auth/src/test/java/com/themistra/auth/common/PublicEndpointsTest.java` (new — 1 test
  asserting both new paths are registered)

**Process artifacts** (`.ai/prompts/auth/T07/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — full phase trail, including the Phase 0 human
decisions (PasswordPolicy deferral to task 9), the Phase 3/8/11 Kimi reviews, the Phase 4/9
human-approval resolutions (HTTP status, LOCKED-unlock, T06-regression fix-in-place), and the
Phase 12 PASS verdict with full traceability matrix.

**Repository state note:** `VerificationTokenServiceTest.java`, `AccountServiceTest.java`,
`AccountControllerTest.java` (Phase 11 gap-fix edits), `PublicEndpointsTest.java`, and
`12-specification-verification.md` are currently uncommitted working-tree changes on top of the
already-committed Phase 0–11 work (commits `58e2027`..`c8b019c`). Nothing has been committed by
this phase — Phase 13 only documents the intended commit; committing remains an explicit,
separate step.

## Summary

Implements `tasks.md` task 7: the self-service password-reset flow, reusing T05's
`VerificationTokenService` and T02's `RefreshTokenTracker`/`RefreshTokenFamily` session-tracking
rather than introducing new machinery. Three things worth a reviewer's attention: (1) the
purpose-confusion vulnerability class - an `EMAIL_VERIFY` token could reset a password, and
(discovered while designing the fix) the mirror-image bug was already live in T06's shipped code -
both closed by the same `consumeForPurpose` primitive, caught in adversarial design review before
either half was implemented; (2) `LOCKED` accounts are deliberately included as valid reset targets
and are unlocked on success (human-confirmed reasoning: email possession is at least as strong an
identity proof as a successful login, which already clears lockout); (3) R14's password-policy
enforcement is intentionally out of scope here, deferred to task 9's dedicated sweep across all
three password-setting paths - flagged and human-confirmed at Phase 0, documented again in Phase
12's traceability matrix as the one partial line item, not a silently dropped requirement.

## Testing performed

Same situation as every task in this chain: `mvn -pl services/auth test` cannot run to completion
due to the pre-existing, unrelated `token` package compile failure (`SecurityChainsConfig`,
`ReuseDetectingAuthorizationService`, `AuthorizationServiceConfig` — tracked since T03, still
unfixed, not touched by this branch). Verified by compiling the new/changed test classes and their
real transitive dependency chain directly with `javac` against the module's resolved test-scope
classpath, then executing via the JUnit Platform `Launcher` API — the same engine Surefire
delegates to.

**Result: 78/78 tests passing**, ~860ms, no Spring context, no database.

`ArchitectureTest` was also re-run in isolation (compiling the full main tree except the three
files chained to the pre-existing break) specifically to verify the new `account → token`
dependency doesn't cross a module boundary: 6/7 rules passed, including every rule capable of
catching that. The 1 failing rule (`only_the_account_module_may_touch_the_Account_entity`, tripped
by `AccountResponse.from(Account)`) was confirmed via `git log` to predate T03 - unrelated to this
branch, logged as an Open Question in `10-test-generation.md` rather than fixed here.

Kimi's independent design review (Phase 8) found 13 findings, 2 CRITICAL (the purpose-confusion
bug and its T06-side mirror); all resolved or explicitly accepted at Phase 4/9. Kimi's independent
test review (Phase 11) found 8 coverage gaps; all 8 applied, with one correction along the way -
Kimi's suggested assertion for the already-used-token case (`markConsumed` never called) was
checked against the actual `consumeForPurpose` source and found incorrect (the method has no
explicit `usedAt` check and always attempts the mutation, relying on the DB-level filter), so the
test was written to match real behavior instead of the literal suggestion. Full
requirement-to-evidence-to-test traceability is in `12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 7 — "Password reset flow."
- **Requirements:** R12, R13, R15 (`requirements.md`) fully; R14 partially (password
  update/family-revocation/audit clauses implemented and tested; policy-enforcement clause
  deferred to task 9, human-confirmed at Phase 0).
- **LOCKED decisions:** L5 (`design.md` §4a); L11 widened in at Phase 1 as directly operative
  (both new paths registered in `PublicEndpoints.METHOD_SCOPED`, same precedent as T06); L12
  (module boundaries) re-verified via isolated `ArchitectureTest` run.
- **Named tests:** `shouldEmitPasswordResetEventOnlyWhenEmailExists`,
  `shouldResetPasswordAndRevokeAllFamiliesWithValidToken` (`package.md` §8) — both implemented and
  passing.
