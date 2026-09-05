# auth · T07 · Phase 11 — Test Review Findings

Reviewed the Phase 10 generated tests against the frozen brief and the acceptance criteria for R12–R15. The suite is comprehensive, but several gaps remain around negative paths, state-change safety, and null/race coverage for the new `consumeForPurpose` primitive.

---

## Gap 1 — `password-reset-request` controller test does not exercise the non-match path

**Why it matters:** R12 requires the same uniform acknowledgement regardless of whether the email matches an account. The test `passwordResetRequestReturnsForPasswordResetAcknowledgementRegardlessOfMatch` is named for this invariant, but it only invokes the controller once with a matching email and verifies the service delegate was called. Because the controller method is trivial, the actual risk is low, but the test does not prove the response is identical when `requestPasswordReset` silently no-ops.

**Suggested test:** Add a second invocation (or a separate test) that does not stub any `AccountService` behavior and asserts the returned `RegistrationAcknowledgement` is still equal to `RegistrationAcknowledgement.forPasswordReset()` — mirroring `resendVerificationAlwaysReturnsTheSameAcknowledgementRegardlessOfMatch`.

---

## Gap 2 — `resetPassword` rejection tests do not assert that the plaintext password is never encoded for ineligible accounts

**Why it matters:** The frozen brief's Constraints section states that the new password must be hashed before persistence and that failure produces no state change whatsoever. `shouldRejectPasswordResetForIneligibleAccountStatuses` verifies `refreshTokenTracker` and `auditService` are not called, but it does not verify that `passwordEncoder.encode(...)` is also not called. A regression that moved encoding before the status check would pass these tests and could create a misleading audit/hash side-effect.

**Suggested test:** In the loop, add:

- `verify(passwordEncoder, never()).encode(anyString())`
- an assertion that each ineligible account's `getPasswordHash()` is unchanged after the rejection.

---

## Gap 3 — `VerificationTokenServiceTest` has no null-argument coverage for the new `consumeForPurpose` method

**Why it matters:** `consumeForPurpose` explicitly checks both arguments for non-null and throws `NullPointerException` (`VerificationTokenService.java:115-116`). The existing `shouldRejectNullArgumentsWithIntentionalException` covers `issue`, `verify`, and `consume`, but not the new method.

**Suggested test:** Add two assertions to `shouldRejectNullArgumentsWithIntentionalException` (or a new test):

- `assertThatThrownBy(() -> service.consumeForPurpose(null, VerificationToken.Purpose.PASSWORD_RESET)).isInstanceOf(NullPointerException.class)`
- `assertThatThrownBy(() -> service.consumeForPurpose("token", null)).isInstanceOf(NullPointerException.class)`

---

## Gap 4 — No race-condition test for `consumeForPurpose` between pre-check and post-check

**Why it matters:** The method mirrors `consume`'s two-account-check design (pre-mutation usability check and post-mutation re-check). `shouldRejectConsumeWhenAccountBecomesUnusableBetweenTheTwoChecks` covers `consume`; `consumeForPurpose` lacks an equivalent test. Removing or reordering the post-check would not be caught.

**Suggested test:** Stub a `PASSWORD_RESET` token with an `ACTIVE` account for the first `accountRepository.findById(...)` call and a `SUSPENDED` account for the second, set `tokenRepository.markConsumed` to return `1`, and assert `consumeForPurpose` returns `Optional.empty()`.

---

## Gap 5 — `consumeForPurpose` has no explicit expiry / already-used coverage

**Why it matters:** Wrong-purpose and account-unusable rejection are covered, but the production path also rejects expired and already-used tokens. The existing `consume` tests exercise this, but `consumeForPurpose` is the method that `resetPassword` actually calls. A subtle ordering bug (for example, moving the purpose check after `markConsumed`) could be masked because the wrong-purpose tests do not intersect with expiry/used states.

**Suggested tests:** Add two focused tests:

- `shouldRejectExpiredPasswordResetTokenViaConsumeForPurpose` — stub a `PASSWORD_RESET` token with `expiresAt` in the past and assert empty.
- `shouldRejectAlreadyUsedPasswordResetTokenViaConsumeForPurpose` — stub a `PASSWORD_RESET` token with `usedAt` set and assert empty, plus `verify(tokenRepository, never()).markConsumed(...)`.

---

## Gap 6 — Success test does not explicitly verify the encoder receives the raw new password

**Why it matters:** The security requirement is that the plaintext password is hashed before persistence. `shouldResetPasswordAndRevokeAllFamiliesWithValidToken` asserts the account ends up with the mocked encoded value, but it does not verify that `passwordEncoder.encode` was actually invoked with the raw input. If `resetPassword` somehow stored a hash of a different string or used a different encoding path, the current assertion would not reveal it.

**Suggested test:** Add to `shouldResetPasswordAndRevokeAllFamiliesWithValidToken`:

- `verify(passwordEncoder).encode("new-correct-horse")`.

---

## Gap 7 — `requestPasswordReset` does not assert no `account` aggregate event is published

**Why it matters:** The reset-request path should only emit `auth.email.requested` under aggregate `verification-token`. The existing test `shouldEmitPasswordResetEventOnlyWhenEmailExists` counts two `verification-token` publications for eligible cases, but does not directly verify that ineligible cases produce no publication of any aggregate and that no `account` lifecycle event is accidentally emitted.

**Suggested test:** Add to the ineligible loop in `shouldEmitPasswordResetEventOnlyWhenEmailExists` (or as a separate test):

- `verify(outboxPublisher, never()).publish(eq("account"), any(), any(), anyInt(), any())` across all six calls.

---

## Gap 8 — No explicit test that `PublicEndpoints.METHOD_SCOPED` contains the new paths

**Why it matters:** L11 (widened) states both new paths must be registered in `PublicEndpoints`. The frozen brief lists `ArchitectureTest` still passes as an acceptance criterion, but `ArchitectureTest` does not currently contain a rule that enumerates the contents of `PublicEndpoints.METHOD_SCOPED`.

**Suggested test:** Add a small unit test in `common` or extend `ArchitectureTest` with a rule that `PublicEndpoints.METHOD_SCOPED` must contain `POST /accounts/password-reset-request` and `POST /accounts/password-reset`, so future removals fail CI. This touches a test file, not production code.

---

## Summary

The Phase 10 tests cover the named tests (`shouldEmitPasswordResetEventOnlyWhenEmailExists`, `shouldResetPasswordAndRevokeAllFamiliesWithValidToken`), the purpose-confusion security fix, the LOCKED→ACTIVE unlock path, the family-revocation isolation, DTO validation, and the toString/wording guards. The gaps above are mostly about negative-path completeness (non-match response, nulls, race conditions, expired/used tokens) and state-change safety assertions (no encoder call on rejection, encoder receives raw password on success, no stray aggregate event). None indicate a production-code deviation from the spec, but closing them would reduce regression risk.
