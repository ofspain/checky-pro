# auth · T17 · Phase 8 — Independent Code Review

Consumes Phase 6 implementation and Phase 7 self-review. Findings only — no rewrites.

---

### 1. `MfaEnrollment.getSecretEncrypted()` returns the internal mutable byte array

- **Issue:** The getter hands out a live reference to the encrypted seed. A caller can mutate it in place, corrupting the entity's in-memory state and potentially bypassing JPA dirty-checking for `byte[]` fields.
- **Evidence:** `MfaEnrollment.java:98-100`: `return secretEncrypted;`.
- **Recommendation:** Return `secretEncrypted.clone()` from the getter. Document that defensive copies are used on both input and output because the array contents are key material.
- **Confidence:** High
- **Severity:** Medium

---

### 2. `MfaEnrollment.create(...)` also stores the caller's byte array by reference

- **Issue:** Even if the getter is fixed, the factory itself captures the caller's `secretEncrypted` reference. A caller that retains the array could mutate it before the entity is persisted, corrupting the value that JPA writes.
- **Evidence:** `MfaEnrollment.java:58`: `enrollment.secretEncrypted = secretEncrypted;`.
- **Recommendation:** Defensively copy the array in `create(...)`: `enrollment.secretEncrypted = secretEncrypted.clone();`. This pairs with the cloned getter so the entity owns its own copy of the encrypted seed.
- **Confidence:** High
- **Severity:** Medium

---

### 3. `MfaEnrollment.confirm(null)` and `recordUse(null)` are silently accepted

- **Issue:** `confirm(null)` passes the already-confirmed guard because `this.confirmedAt == null`, then leaves the entity still unconfirmed with no failure signal. `recordUse(null)` unconditionally overwrites `lastUsedAt`, erasing a previously recorded timestamp.
- **Evidence:** `MfaEnrollment.java:70-80`.
- **Recommendation:** Add `Objects.requireNonNull(confirmedAt, ...)` at the start of `confirm(...)` and `Objects.requireNonNull(lastUsedAt, ...)` at the start of `recordUse(...)`. This matches the brief's intent that the *column* can be null, while the mutator must receive a real timestamp.
- **Confidence:** High
- **Severity:** Medium

---

### 4. `create(...)` static factories do not reject null required arguments

- **Issue:** Passing a null `accountId`, `type`, `secretEncrypted`, `codeHash`, or `createdAt` to either factory will create an entity that fails only at `EntityManager.persist/flush` time with a database error, rather than failing fast with a clear message.
- **Evidence:** `MfaEnrollment.java:54-61` and `RecoveryCode.java:49-55`.
- **Recommendation:** Add `Objects.requireNonNull` guards for every non-nullable field in both factories. This is the natural place to enforce the contract before the entity is handed off to JPA.
- **Confidence:** High
- **Severity:** Low–Medium

---

### 5. `RecoveryCodeRepository` lacks a hash-based lookup

- **Issue:** R25 (task 18) requires verifying a recovery code by its hash. The repository currently offers `findByAccountId`, `findByAccountIdAndUsedAtIsNull`, and `markUsed`, but no way to look up a specific code hash for an account.
- **Evidence:** `RecoveryCodeRepository.java:16-30`.
- **Recommendation:** Add `Optional<RecoveryCode> findByAccountIdAndCodeHash(Long accountId, String codeHash)` now, or explicitly defer it to task 18 with a note that verification requires either this method or an in-memory scan of all codes.
- **Confidence:** High
- **Severity:** Medium

---

### 6. `MfaEnrollmentRepository` lacks a confirmed-only finder and a deletion method

- **Issue:** Task 18 will need to (a) distinguish confirmed from unconfirmed enrollments when enforcing mandatory MFA and (b) delete the enrollment on MFA disable (R28). The current repository surface provides neither.
- **Evidence:** `MfaEnrollmentRepository.java:14-21`.
- **Recommendation:** Add `Optional<MfaEnrollment> findByAccountIdAndTypeAndConfirmedAtIsNotNull(...)` and `void deleteByAccountIdAndType(Long accountId, MfaEnrollment.Type type)` now, or document them as task-18 additions.
- **Confidence:** Medium
- **Severity:** Low–Medium

---

### 7. `MfaEnrollment.confirm` exception message can say "MfaEnrollment null is already confirmed"

- **Issue:** The exception uses the raw `id` field, which is null for a transient entity. The resulting message is confusing and leaks implementation detail.
- **Evidence:** `MfaEnrollment.java:71-73`.
- **Recommendation:** Include `accountId` (always available and meaningful) in the message instead of, or in addition to, `id`. Example: `"MfaEnrollment for account " + accountId + " is already confirmed"`.
- **Confidence:** Medium
- **Severity:** Low

---

### 8. `RecoveryCodeRepository.markUsed` is not idempotently documented for callers

- **Issue:** `markUsed` correctly returns `0` when the code is already used, but there is no guidance on what the service should do with that return value. A naive caller might treat `0` as success, letting a replayed recovery code pass.
- **Evidence:** `RecoveryCodeRepository.java:23-30`.
- **Recommendation:** Add a brief Javadoc note for task 18: "Callers must check the return value and throw/reject authentication when `0` rows are affected." This is implicit to anyone reading the method, but making it explicit prevents a future footgun.
- **Confidence:** Medium
- **Severity:** Low

---

### 9. No explicit `@Transactional` on `markUsed`, relying on the caller

- **Issue:** `markUsed` is a `@Modifying` query. It will work when called inside a service `@Transactional` method, but will throw `TransactionRequiredException` if a future caller omits the transaction boundary.
- **Evidence:** `RecoveryCodeRepository.java:28-30`; same pattern as `VerificationTokenRepository.markConsumed`.
- **Recommendation:** Either add `@Transactional` to `markUsed` (defensive) or add a Javadoc note that the method must be invoked within a transactional context. Adding `@Transactional` is safer and has no downside for callers that are already transactional.
- **Confidence:** Medium
- **Severity:** Low

---

## Summary

The implementation is clean, respects L12, and aligns with established patterns (`VerificationToken`, `LockoutStateRepository`). The two highest-priority fixes are the defensive-copy story on `secretEncrypted` (factory + getter) and the null-argument guards on `confirm`/`recordUse`. Adding the recovery-code hash lookup and the enrollment confirmed-only/deletion finder methods would also save task 18 from having to reopen the repository.
