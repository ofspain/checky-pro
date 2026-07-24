# auth · T05 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable throughout — this task adds new, unwired domain code only (no endpoint changes, no
schema changes, no existing-file behavior change beyond an additive config block), so it carries
no runtime behavior change until T06/task 7 wire it in.

## Commit title

```
Add verification token service (T05)
```

## Commit message

```
Add verification token service (T05)

Add VerificationToken, VerificationTokenRepository, and
VerificationTokenService: single-use, hashed, TTL'd tokens for email
verification and password reset (R3-R5, L5), purpose-generic by design
per design.md so task 7's password-reset flow can reuse it unchanged.

Not wired to any endpoint or AccountService mutation yet - that's T06
(self-service verification endpoints) and task 7 (password-reset flow).
This task adds the domain, persistence, and config only, per tasks.md
task 5.

Key design decisions locked at the Phase 4 human-approval gate and
Phase 9 review resolution:
- Raw tokens are 32 bytes from SecureRandom, URL-safe Base64, 43 chars;
  only the SHA-256 hash is ever persisted.
- consume() is a single atomic conditional UPDATE (usedAt IS NULL AND
  expiresAt > :now in one statement) - closes a real double-consume
  race that a read-then-write approach would have allowed.
- verify()/consume() both return Optional<UUID>, uniform across every
  failure reason (not found, expired, used, unusable account) per R5.
- issue() invalidates any prior unexpired token for the same
  (account, purpose) before creating a new one.
- The original "retry up to 3 times" collision-handling spec was
  simplified to single-attempt fail-fast after self-review and
  independent review both found same-transaction retry unimplementable
  against PostgreSQL (a failed statement aborts the whole transaction);
  documented and human-approved, not a silent deviation.

19 unit tests cover both named tests, TTL boundaries, atomic
double-consume, a behavioral (not just call-level) proof that reissue
invalidates the prior token, collision fail-fast, null handling,
purpose round-trip, config validation, and raw-token non-leakage via a
custom toString().

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production:**
- `services/auth/src/main/java/com/themistra/auth/account/VerificationToken.java` (new)
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenRepository.java` (new)
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java` (new)
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenProperties.java` (new)
- `services/auth/src/main/resources/application.properties` (modified — appended the
  `themistra.auth.verification-token.*` config block)

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenServiceTest.java` (new)
- `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenPropertiesTest.java` (new)

**Process artifacts** (`.ai/prompts/auth/T05/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — the full phase trail, including the Phase 0
investigation into T06's scope (resolving the named-test overlap without repeating T03/T04's full
redundancy), the Phase 3/8/11 Kimi reviews, and the Phase 4/9 human-approval resolutions.

## Summary

Implements `tasks.md` task 5 (Foundation): a purpose-generic (`EMAIL_VERIFY`/`PASSWORD_RESET`),
single-use, hashed, TTL'd verification-token domain service. Pure domain + persistence + config
addition; no controller, no `AccountService` change (that's T06/task 7). Three things worth a
reviewer's attention: (1) both of T05's named tests are implemented at the *service* level by
design — their full end-to-end (HTTP/account-activation) realization is intentionally deferred to
T06 and task 10, decided after investigating T06's own scope at Phase 0, not discovered as a
surprise later; (2) `consume`'s atomicity relies on a single conditional SQL `UPDATE`
(`markConsumed`), not application-level locking — this is the load-bearing fix for a real
double-redemption race; (3) the collision-retry mechanic in the frozen brief was simplified after
two independent reviews found the original "retry 3 times" design silently broken against
PostgreSQL's transaction-abort semantics — this is flagged explicitly in Phase 12's verification
as a documented deviation, not hidden.

## Testing performed

Same situation as T03: `mvn -pl services/auth test` cannot run to completion due to the
pre-existing, unrelated `token` package compile failure (untouched by this branch, tracked since
T03). Verified instead by compiling the new/changed test classes and their real transitive
dependency chain directly with `javac` against the module's resolved test-scope classpath, then
executing via the JUnit Platform `Launcher` API (`junit-platform-launcher` matching the project's
resolved `junit-platform-engine` version) — the same engine Surefire delegates to.

**Result: 19/19 tests passing**, ~700ms, no Spring context, no database. Two real bugs were caught
and fixed while *writing* the tests (not production defects): a classic Mockito nested-stubbing
mistake, and a missing mock stub that silently defaulted an account ID to `0L`. Both fixed before
the test suite was considered done (`10-test-generation.md`).

A Kimi independent code review (Phase 8) and test review (Phase 11) both ran against this
implementation; all accepted findings are folded in (`09-review-resolution.md`, `10-test-
generation.md`'s Phase 11 addendum). Full requirement-to-evidence-to-test traceability is in
`12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 5 — "Verification token service."
- **Requirements:** R3, R4, R5 (`requirements.md`) — R3/R4 scoped partially, per Phase 0/1's
  investigation into T06's overlapping scope.
- **LOCKED decisions:** L5 (`design.md` §4a); L1 widened (V1 migration immutability, directly
  operative since `verification_tokens` already exists).
- **Named tests:** `shouldActivateAccountWithValidVerificationToken`,
  `shouldNotRevealAccountExistenceForInvalidVerificationToken` (`package.md` §8) — implemented at
  the service level; their full HTTP-level realization is T06's and task 10's, respectively, per
  the Phase 0 scoping decision confirmed with the human.
