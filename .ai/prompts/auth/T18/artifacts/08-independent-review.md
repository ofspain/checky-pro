# auth · T18 · Phase 8 — Independent Code Review

Consumed the Phase 6 implementation (`MfaService.java`, `TotpVerifier.java`, `RecoveryCodeRepository.java`, the five new exceptions) and `artifacts/07-self-review.md`.

Findings below are independent of the Phase 7 self-review; overlaps are re-derived from the actual code.

---

1. **Issue · Failure audits in `confirm`, `disable`, and `verifyRecoveryCode` are silently rolled back by the exception that follows them.**
   **Evidence:** Every failure branch records an audit and then throws inside the same `@Transactional` method: `MfaService.java:112-113` (`confirm`), `:143-144` (`disable` wrong password), `:153-154` (`disable` wrong code), `:176-177` (`verifyRecoveryCode`). `AuditService.record` is `@Transactional` with default `REQUIRED` propagation, so it joins the caller's transaction. Spring rolls back the whole physical transaction on an unchecked exception, discarding the audit `INSERT` and its outbox row. R29 requires "record and deny"; AC3/AC9/AC10 require the failure audit to persist.
   **Recommendation:** Commit failure audits independently of the caller's rollback. The cleanest fix is a dedicated `AuditService` method annotated `@Transactional(propagation = REQUIRES_NEW)` used only for these failure-audit sites, or a `TransactionTemplate` that commits before the throw. Any fix needs an explicit design sign-off because it changes `AuditService`'s propagation contract.
   **Confidence:** High.

2. **Issue · Re-confirming an already-confirmed enrollment surfaces a raw `IllegalStateException`.**
   **Evidence:** `MfaService.confirm` loads the enrollment with `findByAccountIdAndType` (`:106-108`), which returns confirmed or unconfirmed, then calls `enrollment.confirm(clock.instant())` unconditionally (`:116`). `MfaEnrollment.confirm` throws `IllegalStateException` if `confirmedAt` is already set. None of T18's mapped exceptions wrap this, so a double-submit or retry within the 90s window becomes a 500 with internal detail, violating `agents.md`'s RFC 9457 rule.
   **Recommendation:** Check `enrollment.getConfirmedAt() != null` immediately after loading and throw a mapped exception (`MfaAlreadyEnrolledException` reused, or a new `MfaAlreadyConfirmedException`) before decrypting/verifying.
   **Confidence:** High.

3. **Issue · Concurrent `confirm` calls can leave more than 10 recovery codes for an account.**
   **Evidence:** `confirm` reads the unconfirmed enrollment, verifies the code, marks it confirmed, and inserts 10 recovery codes — all without a row-level lock (`:106-125`). Two concurrent transactions can both see the row as unconfirmed, both pass verification, both call `enrollment.confirm`, and both insert 10 codes. R23 requires exactly 10 single-use recovery codes.
   **Recommendation:** Serialize confirmation per enrollment. Prefer an atomic repository update `UPDATE MfaEnrollment SET confirmedAt = :now WHERE id = :id AND confirmedAt IS NULL` that returns rows affected (mirrors `RecoveryCodeRepository.markUsed`). If zero rows are updated, throw an already-confirmed exception. If T17's "no atomic repository update for MfaEnrollment" locked decision is held, the risk must be explicitly accepted and a concurrent-confirm test added.
   **Confidence:** Medium.

4. **Issue · Concurrent `beginEnroll` calls can propagate an unhandled `DataIntegrityViolationException`.**
   **Evidence:** `beginEnroll` checks for an existing enrollment and then saves (`:76-88`). Two concurrent calls for an account with no enrollment can both see no row under READ COMMITTED and both attempt `save`, causing the second to violate `UNIQUE(account_id, type)` as a raw `DataIntegrityViolationException`. The same race exists after deleting an unconfirmed row. `AccountService.register` already catches and translates this exact pattern.
   **Recommendation:** Wrap the final `save` (or use `saveAndFlush`) in `try/catch (DataIntegrityViolationException)` and throw `MfaAlreadyEnrolledException`, mirroring `AccountService.register`.
   **Confidence:** High.

5. **Issue · `beginEnroll` can delete an enrollment that became confirmed in a concurrent transaction.**
   **Evidence:** `beginEnroll` checks `existing.getConfirmedAt() != null` inside `ifPresent` (`:78-81`) and deletes only if unconfirmed. Another transaction could confirm the same row between T1's check and its delete, causing T1 to delete a confirmed enrollment and replace it with a fresh unconfirmed one. This undermines the "confirmed enrollment blocks re-enrollment" invariant.
   **Recommendation:** Lock the existing row before the check/delete (e.g., `SELECT FOR UPDATE` via a repository method annotated `@Lock(LockModeType.PESSIMISTIC_WRITE)`), or reject any existing row outright and require explicit disable first. If the delete-and-retry behavior is kept, it must be race-safe.
   **Confidence:** Medium.

6. **Issue · `TotpVerifier` accepts the same TOTP code repeatedly within the 90s tolerance window.**
   **Evidence:** `TotpVerifier.verify` only checks whether the submitted code matches any of the three time steps; it does not track the last accepted counter. Within the 90s window, a valid code can be replayed against `disable` and (if the first confirm fails) `confirm`. Task 20's login flow will have no replay resistance either unless the caller tracks the last accepted step.
   **Recommendation:** Track the last accepted time step on `MfaEnrollment` and reject codes from steps at or before it, or document the accepted replay window explicitly and ensure task 20 implements its own replay guard.
   **Confidence:** Medium (the brief is silent on replay; the behavior is technically correct per the brief but a standard TOTP security expectation is missing).

7. **Issue · `verifyRecoveryCode` performs no account-state check, allowing redemption for a suspended account if called directly.**
   **Evidence:** The method is public and resolves the account via `findAccountIdByUuid` without calling `requireActiveAccount`. A `SUSPENDED` account's recovery codes remain in the table and could be marked used. The javadoc says the caller establishes usability, but nothing enforces that.
   **Recommendation:** Add the same `ACTIVE` check as the other flows, or make the method package-private and clearly document that only a caller that has already verified account usability may invoke it.
   **Confidence:** Medium.

8. **Issue · `TotpVerifier.constantTimeEquals` returns early on length mismatch, weakening side-channel resistance.**
   **Evidence:** `constantTimeEquals` returns `false` immediately if `a.length() != b.length()`. An attacker with precise timing could infer whether the prefix of a longer submitted code matches the expected code before the length mismatch is detected. TOTP codes are fixed at 6 digits, but the method is generic.
   **Recommendation:** Remove the early return and compare over the full expected length regardless of input length (e.g., pad/trim internally or iterate the full length).
   **Confidence:** Low.

9. **Issue · New audit event type `mfa.disable_failed` has no stated schema/consumer coverage.**
   **Evidence:** The frozen brief introduces `mfa.disable_failed` for wrong-password disable attempts. `AuditService` mirrors every event as `security.<eventType>` on `auth.security.audit`. Downstream consumers and any contract may not expect this type.
   **Recommendation:** Add `mfa.disable_failed` to the security-audit event contract/schema and notify consumers, or confirm that the audit topic is consumed generically and event types are not validated.
   **Confidence:** Low.
