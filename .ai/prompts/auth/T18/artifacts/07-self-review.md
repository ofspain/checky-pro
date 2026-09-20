# auth · T18 — Phase 7: Self Review

Reviews the Phase 6 diff (`MfaService.java`, `TotpVerifier.java`, five exceptions,
`RecoveryCodeRepository.deleteByAccountId`) against the frozen brief and `agents.md`. No rewrites
here — findings only; fixes are Phase 9.

---

1. **Issue · The `mfa.failed`/`mfa.disable_failed` failure audit is rolled back by the very
   exception it precedes — R29's "record and deny" never actually persists the record.**
   **Severity:** Critical.
   **Evidence:** `MfaService.java:111-114` (`confirm`), `:142-145` (`disable`, wrong password),
   `:152-155` (`disable`, wrong code), `:175-178` (`verifyRecoveryCode`), all calling
   `recordAudit(...)` (`:200-203`) immediately before `throw`. `recordAudit` calls
   `auditService.record(...)`, and `AuditService.record` (`audit/AuditService.java:46-47`) is
   `@Transactional` with default (`REQUIRED`) propagation — it joins the caller's already-open
   transaction rather than opening its own. Every one of these four call sites sits inside a
   `@Transactional` `MfaService` method (`:71`, `:101`, `:136`, `:168`) that then throws an
   unchecked exception. Spring's default rollback rule rolls back the *entire physical
   transaction* on an unchecked exception — including the audit `INSERT` and its outbox row that
   were just written moments earlier in the same transaction. The audit record for every MFA
   failure this task is required to produce is silently discarded. No existing precedent in this
   codebase combines "record a FAILURE audit, then throw within the same transactional method" —
   `PasswordPolicy.recordBreachCheckFailedAudit` (`account/PasswordPolicy.java:77-84`) records and
   then *returns normally* (fail-open, no throw); `LoginFailureHandler.auditFailure`
   (`authn/LoginFailureHandler.java:104-108`) isn't inside a Spring-managed transaction at all.
   T18 is the first place this exact combination occurs, and it doesn't work.
   **Recommendation:** The failure-audit write must survive the caller's rollback. Options:
   commit the audit in its own transaction (e.g. `@Transactional(propagation =
   Propagation.REQUIRES_NEW)` on a dedicated audit-recording path used only by these failure
   sites, or on `AuditService.record` itself if every other caller's semantics still tolerate
   independent-transaction commit), or restructure `MfaService`'s failure branches so the audit
   write happens after the enclosing transaction has already committed (e.g. via
   `TransactionSynchronization.afterCommit`, though that changes atomicity guarantees the outbox
   pattern otherwise relies on). Needs an explicit design call, not a silent pick, since it
   affects `AuditService`'s general contract.

2. **Issue · A correct-code re-confirm of an already-confirmed enrollment surfaces
   `MfaEnrollment`'s internal `IllegalStateException`, not one of T18's mapped exceptions.**
   **Severity:** High.
   **Evidence:** `MfaService.java:106-116` — `confirm` loads the `(accountId, TOTP)` row
   regardless of its confirmation state and calls `enrollment.confirm(clock.instant())`
   unconditionally on a valid code. `MfaEnrollment.confirm` (`MfaEnrollment.java:78-84`) throws a
   plain `IllegalStateException` if `confirmedAt` is already set. This is reachable by an entirely
   ordinary double-submit (a user's client retries a slow "confirm" request, or a user re-opens
   the enrollment screen and resubmits a code that's still within the 90s window) — not just an
   adversarial probe. None of the five new exception types wrap this, so a future controller
   (task 19) has nothing to map it to and it surfaces as a raw, unmapped 500 — inconsistent with
   `agents.md`'s "Errors are RFC 9457 ... no internal detail" rule. `MfaAlreadyEnrolledException`
   already exists and models exactly this condition for `beginEnroll`; `confirm` has no equivalent
   guard before attempting the mutation.
   **Recommendation:** Check `enrollment.getConfirmedAt() != null` before calling
   `enrollment.confirm(...)` and throw a mapped exception (`MfaAlreadyEnrolledException` reused, or
   a new one if its message wording is judged wrong for this call site) instead of relying on the
   entity's defensive guard to be reached from a service caller.

3. **Issue · Concurrent `beginEnroll` calls for the same account can race past the
   existence check and hit the DB's `UNIQUE(account_id, type)` constraint unguarded.**
   **Severity:** Medium.
   **Evidence:** `MfaService.java:76-88` — two concurrent `beginEnroll` calls for the same account
   (e.g. a double-submitted "start MFA setup" click) can both observe no existing row under
   READ COMMITTED, both proceed to `mfaEnrollmentRepository.save(enrollment)` (`:88`), and the
   second insert fails the `UNIQUE(account_id, type)` constraint as an uncaught
   `DataIntegrityViolationException`. `AccountService.register` handles the exact same class of
   race explicitly (`account/AccountService.java:93-98`, catching `DataIntegrityViolationException`
   and translating it to `DuplicateEmailException`); `beginEnroll` has no equivalent.
   **Recommendation:** Wrap the `save` (or a `saveAndFlush`) in a `try/catch
   (DataIntegrityViolationException)` and translate to `MfaAlreadyEnrolledException`, mirroring
   `AccountService.register`'s established pattern.

4. **Issue · Secret-bearing parameters (`submittedCode`, `rawCode`, `currentPassword`) have no
   null guard and fail with an undomained exception type if null.**
   **Severity:** Low.
   **Evidence:** `TotpVerifier`'s `constantTimeEquals` calls `b.length()` on `submittedCode`
   (NPE if null); `MfaService.java:171` calls `Hashing.sha256(rawCode)` (NPE if null);
   `MfaService.java:142` calls `passwordEncoder.matches(currentPassword, ...)` — Spring Security's
   `BCryptPasswordEncoder.matches` throws `IllegalArgumentException` for a null raw password. None
   of these map to one of T18's exception types. This matches the frozen brief's Constraints
   section ("no null-argument case beyond what the entities/repositories already enforce — mirrors
   T17's precedent scope"), so this is not a deviation — flagging for the record since task 19's
   controller will be the actual boundary that must prevent nulls from ever reaching here (e.g.
   `@NotBlank` on the request DTO fields), and that dependency should be explicit rather than
   assumed.
   **Recommendation:** No change to `MfaService` itself; note as a task 19 requirement (request
   DTOs must `@NotBlank`-validate `submittedCode`/`rawCode`/`currentPassword` before they reach
   this service).

5. **Issue · `resolveAccountId`'s `AccountNotFoundException` branch is dead code in
   `beginEnroll`/`confirm`/`disable`.**
   **Severity:** Low (readability only).
   **Evidence:** `MfaService.java:73-74`, `:103-104`, `:138-139` all call
   `requireActiveAccount(accountUuid, ...)` before `resolveAccountId(accountUuid)`.
   `requireActiveAccount` (`:181-187`) already calls `accountService.getByUuid`, which throws
   `AccountNotFoundException` for any UUID that doesn't resolve to a row in the same `accounts`
   table `resolveAccountId`'s native query (`MfaEnrollmentRepository.findAccountIdByUuid`) reads.
   Within the same transaction, if the first lookup succeeds the second cannot fail for the same
   reason — the `orElseThrow(() -> new AccountNotFoundException(accountUuid))` at `:191` is
   unreachable from these three call sites (it is reachable, and needed, from
   `verifyRecoveryCode`, which doesn't call `requireActiveAccount`).
   **Recommendation:** No functional fix needed; optional readability note only if Phase 9 wants
   to add a comment clarifying that the second lookup's failure branch exists for
   `verifyRecoveryCode`'s sake.

## Reviewed, No Issues Found

- **Module boundaries (L12):** no `Account` entity import; `ArchitectureTest` passes; only
  `AccountService`'s public methods and `account.dto`/`account` public exception/enum types are
  used.
- **Secret-handling:** `BeginEnrollResult`/`ConfirmResult` both override `toString()`; no secret
  material appears in any exception message; no logging statements added.
- **RFC 6238 correctness:** `TotpVerifier`'s dynamic truncation and modulus match RFC 4226 §5.3;
  the ±1-step loop matches L6's 90s tolerance window as locked in the frozen brief.
- **Recovery-code entropy/hashing:** 32 `SecureRandom` bytes (matches
  `VerificationTokenService.RAW_TOKEN_BYTES`), URL-safe unpadded Base64, `Hashing.sha256` before
  every persisted row — the raw code is never stored.
- **Transaction boundaries (success paths):** each public method's success path is a single
  atomic unit, matching the plan.
- **Thread-safety:** `MfaService`/`TotpVerifier` hold no mutable instance state beyond the shared
  `SecureRandom` field, whose use here matches the existing `TotpGenerator`/`MfaSeedEncryption`/
  `VerificationTokenService` precedent.

## Open Questions

None beyond the five findings above, all of which need a Phase 9 decision rather than a silent
Phase 6 fix — Finding 1 in particular changes `AuditService`'s propagation contract and shouldn't
be decided without sign-off.
