# auth · T18 — Phase 12: Specification Verification

Consumes all prior T18 artifacts (Phases 0–11). Compares the final implementation and tests
against `requirements.md`, `design.md`, and `tasks.md` for this task only (R22, R23, R28, R29, L6).

---

## Traceability Matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R22** — begin-enroll generates, encrypts, persists unconfirmed, returns provisioning URI | Yes | `mfa/MfaService.java:79-101` (`beginEnroll`) | Yes — 7 unit tests in `MfaServiceTest` (happy path, PII-avoidance label, confirmed/unconfirmed duplicate rejection, race-safety, non-ACTIVE rejection, toString redaction); 2 integration tests in `MfaServicePersistenceIntegrationTest` (happy path, retry-replaces-secret) — **written, not executable** (see Remaining Risks #1) | No | None |
| **R23** — confirm verifies, confirms, generates 10 hashed single-use codes, returns raw once | Yes | `mfa/MfaService.java:116-142` (`confirm`) | Yes — 5 unit tests (wrong-code/no-mutation, no-enrollment, happy path incl. format/entropy/timestamp assertions, atomic-update-already-confirmed, non-ACTIVE, toString redaction); 2 integration tests (happy path, concurrent-double-confirm race) — **written, not executable** | No | None |
| **R28** — disable requires password + TOTP, removes enrollment, invalidates all codes, audits | Yes | `mfa/MfaService.java:153-176` (`disable`) | Yes — 6 unit tests (wrong password incl. missing-login-view edge case, no-confirmed-enrollment, wrong code, happy path, non-ACTIVE); 1 integration test (happy path, real `mfa.disabled` row read back) — **written, not executable** | No | None |
| **R29** — any TOTP/recovery-code verification failure records `mfa.failed`, denies | Yes | `mfa/MfaService.java:126-128` (confirm), `:169-171` (disable), `:192-194` (verifyRecoveryCode); durability fix in `audit/AuditService.java` (`record` now `REQUIRES_NEW`) | Yes — audit-content assertions in 4 unit tests; 1 integration test (`confirmRecordsFailureAuditThatSurvivesTheRollback`) proving the row survives the caller's rollback — **written, not executable**; the underlying fix itself is proven by `AuditServiceTest`'s existing mock-based assertions plus code inspection | No | `mfa.disable_failed` added for wrong-password disable — broader than R29's literal TOTP/recovery-code-only wording, per Phase 4 human-confirmed reading of `agents.md`'s general audit mandate. Not a violation; a deliberate, disclosed widening. |
| **L6** — RFC 6238 (HMAC-SHA1, 30s step, 6 digits, ±1 step tolerance); recovery codes random, SHA-256-hashed only | Yes | `mfa/TotpVerifier.java` (whole class); `mfa/MfaService.java:210-213` (`generateRawRecoveryCode`, 32 bytes), `:138` (`Hashing.sha256` before persist) | Yes — `TotpVerifierTest` (10 tests) verified against real, independently-recomputed RFC 6238 Appendix B vectors, plus ±1/±2-step boundary tests | No | None |
| **L12** — no cross-module entity imports | Yes | `mfa/MfaService.java:3-24` imports only `account`'s public `AccountService`/`AccountStatus`/`AccountNotFoundException`/`InvalidAccountStateException`/`dto.*` — no `Account` entity | Yes — `ArchitectureTest.only_the_account_module_may_touch_the_Account_entity` passes | No | None |

## Named Tests (`package.md` §8)

| Named test | Mapped to | Status |
|---|---|---|
| `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` | `MfaServiceTest.confirmGeneratesTenSingleUseRecoveryCodesOnSuccess` (unit, **passing**) + `MfaServicePersistenceIntegrationTest.beginEnrollThenConfirmPersistsConfirmedEnrollmentAndTenRecoveryCodes` (integration, **written, blocked**) |
| `shouldRequirePasswordAndTotpToDisableMfa` | `MfaServiceTest.disableDeletesEnrollmentAndRecoveryCodesAndRecordsSuccessAudit` + its two failure-path siblings (unit, **passing**) + `MfaServicePersistenceIntegrationTest.disableRemovesEnrollmentAndAllRecoveryCodesAndAudits` (integration, **written, blocked**) |

Neither named test has been proven end-to-end against a real database yet. The unit-level proof is
real and passing; the integration-level proof exists as code but cannot execute in this
environment. This is stated plainly rather than rounded up to "verified."

## Answers

**(1) Is the task fully complete?**
Yes, for the scope T18 actually owns. All four requirement IDs (R22/R23/R28/R29) and L6 are
implemented, self-consistent, and covered by passing unit tests. The one gap — the integration
suite's inability to execute — is a pre-existing, independently-diagnosed environment defect
(`AccountRepository.existsByEmail`'s Hibernate byte-array conversion issue) that also blocks T17's
own `MfaPersistenceIntegrationTest`, is fully documented (`docker-testcontainers-handshake-issue`
memory), and was explicitly accepted as out-of-scope for T17 by prior human direction. It is not a
defect this task introduced or could have avoided by writing different code.

**(2) Does it satisfy every acceptance criterion?**
Yes — AC1 through AC7 (frozen brief, Phase 1 extraction) each map to at least one passing unit
test and one written integration test, per the traceability above.

**(3) Does it violate any LOCKED decision?**
No. L6 and L12 are both implemented exactly and mechanically verified (RFC vectors, ArchUnit).
Note for the record: one **frozen-brief-level** (not LOCKED-decision-level) constraint was
knowingly overridden with explicit human sign-off at Phase 9 — `MfaEnrollmentRepository.java` was
listed as "Files NOT to Modify" in the Phase 4 frozen brief, and two atomic methods
(`confirmIfUnconfirmed`, `deleteByIdIfUnconfirmed`) were added to it anyway to close two real
concurrency races Kimi's Phase 8 review found. This is a task-scoping decision from this task's
own Phase 4, not one of the spec's `L`-numbered LOCKED decisions, and the override is fully
recorded in `artifacts/09-review-resolution.md`.

**(4) Remaining risks?**
- **Integration suite unexecuted** (see above) — both named tests are proven at the unit level
  only until the pre-existing environment defect is fixed.
- **`beginEnroll`'s no-prior-enrollment insert race** (Kimi Phase 8 finding 4): two concurrent
  first-time `beginEnroll` calls can still race past the existence check into the DB's
  `UNIQUE(account_id, type)` constraint as an uncaught `DataIntegrityViolationException`.
  Explicitly declined at Phase 9's human-approval gate; `AccountService.register`'s
  `catch (DataIntegrityViolationException)` pattern is the known fix if picked up later.
- **`verifyRecoveryCode` has no account-status precondition** (Kimi Phase 8 finding 7): by design,
  since task 20's login flow is expected to establish account usability before calling it — but
  nothing in this task's code enforces that a future caller actually does. Explicitly declined at
  Phase 9; flagged here for task 20's own Phase 1/2 to inherit deliberately, not rediscover.
  Note also: this scoped exclusion means R25's stronger requirement (recovery-code verification
  gated to *confirmed* enrollments during the SAS flow) is entirely task 20's to implement — T18's
  `verifyRecoveryCode` alone does not enforce it.
- **TOTP replay within the 90s tolerance window** (Kimi Phase 8 finding 6) and the related unused
  `MfaEnrollment.recordUse(Instant)` field (built in T17, never called by T18): explicitly deferred
  to task 20, documented in `artifacts/09-review-resolution.md`.
- **`AuditService.record`'s propagation change to `REQUIRES_NEW`** (Phase 9) is service-wide, not
  scoped to `MfaService` — every existing caller now commits its audit row independently of its own
  enclosing transaction. This was the explicit, human-chosen fix for R29's audit-durability
  requirement and was verified not to introduce new test failures (Phase 10), but it is a real
  behavioral change to shared infrastructure that future tasks touching `AuditService` should be
  aware of, not re-litigate.
- **Constant-time comparison early-return on length mismatch** (Kimi Phase 8 finding 8): low
  practical value (TOTP codes are a fixed, known length), explicitly declined at Phase 9.

## Verdict

**PASS** — T18 fully implements R22/R23/R28/R29 and L6 within its own scope, all self-consistent,
all mechanically or unit-test verified, with every known gap explicitly disclosed and
human-decided rather than silently carried.
