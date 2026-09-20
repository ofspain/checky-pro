# auth · T18 — Phase 9: Review Resolution

Human Approval gate. Consumes `artifacts/07-self-review.md` and `artifacts/08-independent-review.md`.
Every Kimi finding was re-verified against the current source (per this project's standing
practice of checking cited `file:line` before accepting/rejecting an external review) before being
put to the human for a decision. Decisions below are the human's; only accepted items were applied.

---

## 1. Failure-audit rollback (Self-review #1 = Kimi #1) — **ACCEPTED**

**Decision:** "Change `AuditService.record` globally" (not the narrower per-call-site helper).

**Change made:** `audit/AuditService.java` — `record(...)` is now
`@Transactional(propagation = Propagation.REQUIRES_NEW)` (was default `REQUIRED`). A Javadoc note
was added explaining why and naming the accepted trade-off: an audit row can now commit even if
the caller's own transaction later rolls back for an unrelated reason. This affects every existing
caller of `AuditService.record` (`AccountService`, `LockoutService`-adjacent code,
`LoginFailureHandler`, `PasswordPolicy`, `ReuseDetectingAuthorizationService`), not just
`MfaService` — this was disclosed as the trade-off of this option before the decision was made.
No other file changed for this fix; `MfaService`'s four failure-audit call sites needed no
modification since the fix is entirely inside `AuditService`.

## 2 & 3. Concurrent-confirm double-recovery-codes (Kimi #3) and confirmed-row deleted by a race (Kimi #5) — **ACCEPTED (scope amended)**

**Decision:** "Amend scope: add one atomic repository method" — explicitly overriding the frozen
brief's Files-NOT-to-Modify entry for `MfaEnrollmentRepository.java` for this one, narrow reason.
`MfaEnrollment.java` itself was **not** touched (still off-limits; its existing `confirm(Instant)`
mutator is simply no longer called by `MfaService`, left in place for T17's own entity tests).

**Change made:**
- `MfaEnrollmentRepository.java` — added two atomic conditional queries:
  `confirmIfUnconfirmed(Long id, Instant confirmedAt)` (`@Modifying @Query ... WHERE confirmedAt
  IS NULL`, returns rows affected) and `deleteByIdIfUnconfirmed(Long id)` (same shape, for
  delete). Both mirror `RecoveryCodeRepository.markUsed`'s established conditional-update pattern.
- `MfaService.confirm` — replaced `enrollment.confirm(clock.instant())` with
  `mfaEnrollmentRepository.confirmIfUnconfirmed(enrollment.getId(), now)`; a `0`-row result throws
  `MfaAlreadyEnrolledException` **before** any recovery code is generated or persisted. This closes
  Kimi #3 (no window remains where two concurrent calls can both pass the conditional update) and,
  as a side effect, also resolves **self-review #2 / Kimi #2** (the raw `IllegalStateException` from
  a double-confirm): that code path is no longer reachable at all, since `MfaService` never calls
  the entity's `confirm(Instant)` mutator anymore.
- `MfaService.beginEnroll` — replaced the unconditional
  `deleteByAccountIdAndType` unconfirmed-row delete with
  `mfaEnrollmentRepository.deleteByIdIfUnconfirmed(enrollment.getId())`; a `0`-row result (meaning
  a concurrent transaction confirmed the row between this method's read and its delete) throws
  `MfaAlreadyEnrolledException` instead of silently proceeding to replace a now-confirmed
  enrollment. This closes Kimi #5.

**Consequence for the "Other fixes" question:** the "Fix double-confirm exception (Finding 2)"
option the human selected separately is satisfied by this same change — no separate edit was
needed for it.

## 4. `beginEnroll` insert-race / `DataIntegrityViolationException` (Kimi #4) — **REJECTED (deferred)**

**Decision:** Not selected in the "Other fixes" multi-select.

**Disposition:** No change made. This is a distinct race from #2/#3 above (two concurrent
`beginEnroll` calls when **no** enrollment row exists yet at all, both reaching the final `save`,
the second failing the `UNIQUE(account_id, type)` constraint as an uncaught
`DataIntegrityViolationException`) — the atomic-query fix above does not touch this path. Left as
a known gap; `AccountService.register`'s `catch (DataIntegrityViolationException)` pattern remains
the reference fix if this is picked up later.

## 5. `verifyRecoveryCode` missing account-status check (Kimi #7) — **REJECTED (deferred)**

**Decision:** Not selected in the "Other fixes" multi-select.

**Disposition:** No change made. `verifyRecoveryCode` still has no `requireActiveAccount` guard;
the frozen brief's own rationale (task 20's login flow establishes usability before calling this)
stands as the documented justification, unchanged.

## 6. `TotpVerifier.constantTimeEquals` early-return length check (Kimi #8) — **REJECTED (deferred)**

**Decision:** Not selected in the "Other fixes" multi-select.

**Disposition:** No change made. Kimi itself rated this Low confidence; the human declined it.

## 7. TOTP replay within the 90s tolerance window / unused `MfaEnrollment.recordUse` (Kimi #6) — **REJECTED (deferred, documented)**

**Decision:** "Defer to task 20, document now."

**Disposition:** No code change. Recorded here as an explicit, human-reviewed open item for task
20: `TotpVerifier.verify` has no replay/step tracking, and `MfaEnrollment.recordUse(Instant)`
(built in T17 specifically to record "this enrollment's TOTP code was just used") is not called
anywhere in T18. Task 20's login-flow integration is the natural owner of both a last-used-step
comparison and wiring up `recordUse` — T18's `confirm`/`disable` are single-shot,
already-authenticated actions, not the repeated-login surface replay defenses are chiefly for.

## 8. `mfa.disable_failed` audit-contract coverage (Kimi #9) — **REJECTED (factually incorrect)**

**Decision:** No human input needed — verified false premise, not put to a vote.

**Disposition:** No change made. Verified via direct filesystem search:
`contracts/events/auth/security-audit.v1.schema.json` (and every other contract file this task's
header lists) does not exist anywhere in this repository — `contracts/` currently contains only
`README.md`, an empty `api/.gitkeep`, and `events/auth/user-lifecycle.v1.schema.json`. This matches
every prior MFA task's own Phase 1 finding ("the four referenced contracts don't exist in the repo").
There is no schema to add `mfa.disable_failed` to, and `AuditService` mirrors every event generically
(`"security." + eventType`) with no enum-style validation on `eventType`. Kimi's finding is rejected
as moot, not merely deferred.

## Self-review-only findings not covered above

- **Self-review #4** (null-guard gaps for `submittedCode`/`rawCode`/`currentPassword`): not put to
  the human as a separate question — already explicitly out-of-scope per the frozen brief's
  Constraints section, re-flagged there only as a note for task 19's request-DTO validation. No
  change.
- **Self-review #5** (`resolveAccountId`'s `AccountNotFoundException` branch being unreachable
  from `beginEnroll`/`confirm`/`disable`): readability-only, no functional issue, no change.

## Files Changed in This Phase

- `services/auth/src/main/java/com/themistra/auth/audit/AuditService.java` — `record(...)`
  propagation changed to `REQUIRES_NEW`.
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollmentRepository.java` — added
  `confirmIfUnconfirmed` and `deleteByIdIfUnconfirmed` (scope amendment, human-approved override of
  the frozen brief's Files-NOT-to-Modify list for this file only).
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java` — `beginEnroll` and
  `confirm` updated to use the two new atomic repository methods instead of the plain entity
  mutator / unconditional delete; added the `java.util.Optional` import this required.

No public API signatures changed (`MfaService`'s four public method signatures and both result
records are untouched); no classes renamed; no refactoring beyond what each accepted finding
required.

## Verification

- `mvn compile` — clean.
- `mvn test -Dtest=ArchitectureTest,MfaEnrollmentTest,RecoveryCodeTest` — all green (no
  T17 regressions; module-boundary rules still hold).

## Open Questions Carried Forward

- Finding 4 (beginEnroll insert race) and Finding 5/Kimi-#7 (verifyRecoveryCode status check) are
  deliberately unresolved — logged here, not in memory, as the durable record of the decision not
  to fix them in T18.
- Finding 7/Kimi-#6 (replay window, unused `recordUse`) is explicitly assigned to task 20 above —
  whoever picks up task 20 should treat this section as that task's Phase 1/2 input, not rediscover
  it from scratch.
