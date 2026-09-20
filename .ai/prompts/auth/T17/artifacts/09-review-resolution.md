# auth · T17 — Phase 9: Review Resolution

**Human Approval gate.** Consumes `artifacts/07-self-review.md` and `artifacts/08-independent-review.md`.
Decisions below were made by femi via explicit sign-off; this log records each comment's
disposition and the exact change made.

---

## 1. Kimi #1 / self-review #2 — `getSecretEncrypted()` returns the mutable internal array

**Decision:** ACCEPTED.
**Change made:** `MfaEnrollment.getSecretEncrypted()` now returns `secretEncrypted.clone()`.

## 2. Kimi #2 — `create(...)` also captures the caller's array by reference

**Decision:** ACCEPTED.
**Reason:** closes the other half of Finding #1 that my own Phase 7 self-review missed — cloning
only the getter still leaves the entity's stored array aliased to whatever the caller holds before
persistence.
**Change made:** `MfaEnrollment.create(...)` now stores `secretEncrypted.clone()` instead of the
raw reference.

## 3. Kimi #3 / self-review #1 — `confirm(null)`/`recordUse(null)` silently accepted

**Decision:** ACCEPTED.
**Change made:** both methods now call `Objects.requireNonNull(...)` on their argument before
doing anything else.

## 4. Kimi #4 — `create(...)` factories don't reject other null required arguments

**Decision:** ACCEPTED.
**Reason:** unlike T16's analogous question (where the human explicitly discussed and rejected
this class of validation at Phase 4), this exact question was never actually deliberated for
T17 — the frozen brief's "no other null-argument case is in scope" was boilerplate carried over
from the Phase 2 draft, not a considered decision specific to factory validation. Weighed on its
own merits: cheap, fails fast with a clear message instead of a confusing DB error at flush time.
**Change made:** `MfaEnrollment.create(...)` now validates `accountId`, `type`, `secretEncrypted`,
`createdAt`; `RecoveryCode.create(...)` now validates `accountId`, `codeHash`, `createdAt`.

## 5. Kimi #5 — `RecoveryCodeRepository` lacks a hash-based lookup

**Decision:** ACCEPTED.
**Change made:** added `findByAccountIdAndCodeHash(Long, String)`, needed for R25's verification
flow (task 18).

## 6. Kimi #6 — `MfaEnrollmentRepository` lacks a confirmed-only finder and a deletion method

**Decision:** ACCEPTED.
**Change made:** added `findByAccountIdAndTypeAndConfirmedAtIsNotNull(Long, MfaEnrollment.Type)`
(mandatory-MFA enforcement, R24) and `deleteByAccountIdAndType(Long, MfaEnrollment.Type)` (MFA
disable, R28).

## 7. Kimi #7 — `confirm`'s exception message uses the null `id` of a transient entity

**Decision:** ACCEPTED.
**Change made:** the message now reads `"MfaEnrollment for account " + accountId + " is already
confirmed"`.

## 8. Kimi #8 — `markUsed`'s return-value contract isn't documented

**Decision:** ACCEPTED.
**Change made:** added a Javadoc paragraph to `RecoveryCodeRepository.markUsed` stating that
callers must check the return value and reject authentication on `0`.

## 9. Kimi #9 — add `@Transactional` to `markUsed` defensively

**Decision:** REJECTED — no code change.
**Reason:** `VerificationTokenRepository.markConsumed`, the exact established precedent this
method mirrors (explicitly, in its own Javadoc), doesn't have `@Transactional` either. Adding it
only to `markUsed` would make the two atomic-update methods inconsistent for no T17-specific
reason. If this is a real concern, it applies equally to the already-shipped
`VerificationTokenRepository` and is out of scope for this task.

---

## Verification Summary

- `mvn -pl services/auth -am compile`: success.
- `mvn -pl services/auth -am test -Dtest='AuthServiceApplicationTests'`: **passes** — the full
  Spring context boots against real Postgres (Testcontainers, now working — see
  [[docker-testcontainers-handshake-issue]]), which means Spring Data JPA validated all three new
  derived-query repository methods (`findByAccountIdAndCodeHash`,
  `findByAccountIdAndTypeAndConfirmedAtIsNotNull`, `deleteByAccountIdAndType`) against the real
  entity metamodel and schema at startup, not just at compile time.
- `mvn -pl services/auth -am test -Dtest='TotpGeneratorTest,MfaSeedEncryptionTest,MfaPropertiesTest'`:
  36 tests, 0 failures (T16's suite, unaffected by T17's changes, reconfirmed clean).

## Open Questions

None. All 9 comments across both reviews have an explicit accepted/rejected disposition above.
