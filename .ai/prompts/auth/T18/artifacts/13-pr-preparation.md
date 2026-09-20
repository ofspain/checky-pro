# auth · T18 — Phase 13: PR / Commit Preparation

Consumes `artifacts/12-specification-verification.md` — **PASS**. Proceeding to prepare for merge.

---

## Commit Title

`auth: add MFA service (begin-enroll, confirm, disable, recovery-code verification)`

## Commit Message

```
auth: add MFA service (begin-enroll, confirm, disable, recovery-code verification)

Implement MfaService and TotpVerifier to turn T16's crypto primitives
(TotpGenerator, MfaSeedEncryption) and T17's persistence (MfaEnrollment,
RecoveryCode) into the actual enroll/confirm/disable business flows:

- beginEnroll: rejects a confirmed enrollment outright, retries an
  abandoned unconfirmed one, generates/encrypts/persists a new secret.
- confirm: verifies the submitted TOTP code, atomically confirms the
  enrollment, generates 10 single-use recovery codes (32 random bytes,
  URL-safe Base64, SHA-256-hashed at rest, returned raw exactly once).
- disable: requires current password + valid TOTP code, removes the
  enrollment and every recovery code, audits mfa.disabled.
- verifyRecoveryCode: single-use redemption for task 20's future login
  integration.

Two concurrency races found in independent review are closed with new
atomic conditional-update repository methods (confirmIfUnconfirmed,
deleteByIdIfUnconfirmed), mirroring RecoveryCodeRepository.markUsed's
existing pattern. AuditService.record is changed to REQUIRES_NEW
propagation so a failure audit (mfa.failed / mfa.disable_failed)
survives the exception it precedes, instead of being rolled back with
it — this affects every existing caller, not just MfaService, and was
an explicit, human-approved trade-off.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files Changed

**Created:**
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/TotpVerifier.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaAlreadyEnrolledException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaNotEnrolledException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/InvalidTotpCodeException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/InvalidRecoveryCodeException.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaCurrentPasswordMismatchException.java`
- `services/auth/src/test/java/com/themistra/auth/mfa/TotpVerifierTest.java`
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaServiceTest.java`
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaServicePersistenceIntegrationTest.java`

**Modified:**
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollmentRepository.java` — added
  `confirmIfUnconfirmed`, `deleteByIdIfUnconfirmed` (atomic conditional update/delete; a
  human-approved override of the Phase 4 frozen brief's Files-NOT-to-Modify list for this file,
  recorded in `artifacts/09-review-resolution.md`).
- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCodeRepository.java` — added
  `deleteByAccountId`.
- `services/auth/src/main/java/com/themistra/auth/audit/AuditService.java` — `record(...)`
  propagation changed to `REQUIRES_NEW` (service-wide effect, see commit message).
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaPersistenceIntegrationTest.java` (T17's
  file) — added three repository-level tests for the two new atomic methods.

## Summary

Implements task 18 in full: `MfaService` (begin-enroll, confirm, disable, recovery-code
verification) and `TotpVerifier` (stateless RFC 6238 TOTP verification with the standard ±1-step
clock-skew tolerance). Went through the full 14-phase pipeline including a Human Approval gate
(Phase 9) that resolved five review findings — most significantly a critical bug where failure
audits were being silently rolled back by the exception they were meant to precede, and two real
concurrency races in the enrollment-confirmation flow, both closed with new atomic repository
methods rather than locking or retries.

## Testing Performed

- **Unit** (plain JUnit + Mockito, fixed `Clock`, no Spring context): `TotpVerifierTest` (10
  tests, including RFC 6238 Appendix B's published known-answer vectors, independently
  recomputed and confirmed before use) and `MfaServiceTest` (25 tests covering every method,
  every failure branch, audit-event content, recovery-code format/entropy, and secret-redaction
  in `toString()`). All passing.
- **ArchUnit**: `ArchitectureTest` passes — no `Account` entity import, no AWS SDK use outside
  `MfaSeedEncryption`, both repositories remain package-private.
- **Integration** (Testcontainers Postgres): `MfaServicePersistenceIntegrationTest` (6 tests,
  including a real concurrent-confirm race test and a failure-audit-survives-rollback test) and
  three new repository-level tests added to `MfaPersistenceIntegrationTest`. All are written and
  compile, but **do not currently execute** — they hit the same pre-existing, already-documented
  `AccountRepository.existsByEmail` Hibernate defect that already blocks T17's own integration
  suite (see the `docker-testcontainers-handshake-issue` memory). Not a defect introduced by this
  task; will pass once that defect is fixed, with no changes of their own required at that point.
- **Regression sweep**: after changing `AuditService.record`'s propagation (a service-wide,
  cross-cutting change), ran a 272-test sweep across `audit`/`account`/`authn`/`token`. Found 18
  pre-existing failures; individually verified each one — via `git stash` A/B comparison for the
  one plausibly-related case and isolated runs for the rest — to reproduce identically with or
  without this task's changes. No regressions introduced.

## Specification References

- **Task:** `spec/auth-service/tasks.md`, task 18 — "MFA service. Implement begin-enroll,
  confirm, disable, and recovery-code generation/verification. Store only hashes of recovery
  codes."
- **Requirements:** R22, R23, R28, R29.
- **LOCKED decisions:** L6 (TOTP algorithm and recovery-code format). L12 (module boundaries)
  also respected, though not task-scoped to T18 specifically.

## Open Items Carried Forward (not blocking this PR)

Recorded in `artifacts/09-review-resolution.md` and `artifacts/12-specification-verification.md`:
the `beginEnroll` no-prior-enrollment insert race, `verifyRecoveryCode`'s missing account-status
check, and the TOTP replay-window/`recordUse` gap (explicitly assigned to task 20). None are
regressions or scope gaps in T18 itself — each was surfaced by review, considered, and knowingly
deferred with a documented reason.
