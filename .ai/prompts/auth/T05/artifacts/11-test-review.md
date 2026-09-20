# auth · T05 — Phase 11: Test Review Findings

Reviewed `VerificationTokenServiceTest.java` and `VerificationTokenPropertiesTest.java` against the
frozen brief acceptance criteria and named tests. The suite is broad and uses the right tooling
(fixed `Clock`, Mockito, JSR-380 `Validator`, no Spring context). The gaps below are about
behavioral coverage and weak assertions.

---

## Gap 1 — The reissue-invalidating test only verifies the repository method is called, not that the prior token becomes invalid (MEDIUM)

**Why it matters:** `shouldInvalidatePriorActiveTokenBeforeIssuingANewOne` checks that
`tokenRepository.invalidateActive(...)` is invoked with the right arguments, but it does not prove
the query actually suppresses the prior token. A bug in the query (e.g., wrong purpose or missing
`usedAt IS NULL` clause) that still allowed the method to be called would pass this test while
leaving stale tokens redeemable.

**Suggested test:** Issue token A, issue token B for the same `(account, purpose)`, then assert:
- `service.verify(tokenA.rawToken())` is empty.
- `service.verify(tokenB.rawToken())` resolves to the account.
Stub the repositories so the real `expire`/`used` semantics of `invalidateActive` are represented
(e.g., have a fake in-memory repository that implements the query's predicate).

---

## Gap 2 — `consume()` is not explicitly exercised for expired, already-used, or deleted-account tokens (MEDIUM)

**Why it matters:** `shouldNotRevealAccountExistenceForInvalidVerificationToken` covers those
failure modes for `verify()` and only covers `consume()` for "not found" and "suspended account."
`consume()` has a different code path (`markConsumed` returning 0 vs. pre-check rejection), so the
R5 uniform shape is not proven for the redeem path under the other invalid reasons.

**Suggested test:** Add `consume()` variants to the invalid-token test for:
- expired token (`markConsumed` returns 0 or the stub token has `expiresAt` in the past),
- already-used token (`stubToken(..., usedAt=...)`), and
- deleted-account token (pre-check rejects and `markConsumed` is never called).

---

## Gap 3 — The valid-token test does not assert the full contents of the persisted token (LOW/MEDIUM)

**Why it matters:** `shouldActivateAccountWithValidVerificationToken` checks the issued result's
account UUID, purpose, SHA-256 hash, and expiry. It does not assert `createdAt`, `accountId`, or
that the stored `purpose` matches. A regression that, for example, used the wrong account ID in the
entity would be caught only when integration tests fail.

**Suggested test:** Extend the valid-token assertions to:
- `assertThat(issued.token().getCreatedAt()).isEqualTo(NOW);`
- `assertThat(issued.token().getAccountId()).isEqualTo(ACCOUNT_ID);`
- `assertThat(issued.token().getPurpose()).isEqualTo(VerificationToken.Purpose.EMAIL_VERIFY);`

---

## Gap 4 — Raw-token URL-safety assertion is weaker than the requirement (LOW)

**Why it matters:** `shouldGenerateUrlSafeRawTokenOfExpectedLength` decodes the raw token with
`Base64.getUrlDecoder()` and asserts length 43. Passing the URL decoder only confirms the character
set is valid URL-safe Base64; it does not prove there is no padding (`=`) or that standard
Base64 characters `+`/`/` are absent. A non-padded standard-base64 string could still decode with
the URL decoder if it happens not to contain `+`/`/`.

**Suggested test:** Assert the raw token matches `^[A-Za-z0-9_-]{43}$` and explicitly assert it does
not contain `+`, `/`, or `=`.

---

## Gap 5 — Issue failure path does not verify no token is persisted (LOW)

**Why it matters:** `shouldThrowAccountNotFoundExceptionWhenIssuingForUnknownAccount` asserts the
exception, but it does not verify that `tokenRepository.invalidateActive` and `saveAndFlush` are
never invoked for a missing account. A future regression could attempt to insert a token before the
account lookup and still throw the right exception type.

**Suggested test:** Add `verifyNoInteractions(tokenRepository)` after the assertion.

---

## Gap 6 — `VerificationTokenPropertiesTest` does not assert which constraint is violated (LOW)

**Why it matters:** The validation tests use `assertThat(violations).isNotEmpty()`, which is correct
for a single-field record. If a second field is ever added to the properties record, a regression
that makes the wrong field invalid could still satisfy the assertion.

**Suggested test:** For each invalid-value test, also assert that the violating property path is
`ttlMinutes` and that the constraint annotation is the expected one (e.g., `Min`/`Max`). This keeps
the tests resilient to future property additions.

---

## Summary

The T05 test suite now covers the named tests, TTL boundaries, atomic double-consume, collision
fallback, null handling, purpose round-trip, config validation, and raw-token non-leakage. The
biggest remaining gap is Gap 1: reissue invalidation is asserted at the method-call level but not
behaviorally proven. Gaps 2 and 3 tighten coverage on the `consume()` path and the issued entity's
contents; the rest are defensive-assertion improvements.
