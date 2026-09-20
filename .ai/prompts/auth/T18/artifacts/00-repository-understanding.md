# auth · T18 — Phase 0: Repository Understanding

No code written. Read-only pass over the repository and `spec/auth-service/`'s five files.

## 1. Architecture Summary

`auth-service` is Themistra's OIDC/OAuth2 issuer (Spring Boot 3.5.4 / Java 21). Package-by-feature
under `com.themistra.auth.<module>`; `ArchitectureTest` (ArchUnit) mechanically enforces several
module-boundary invariants, including — as of T16 — that only `mfa.MfaSeedEncryption` may import
the AWS SDK. Services are constructor-injected, `@Transactional` per public method, and take a
shared `Clock` bean (`SecurityBeansConfig.clock()` → `Clock.systemUTC()`) rather than calling
`Instant.now()` inline anywhere in service/entity code (`agents.md`: "No `java.util.Date`; use
`java.time` with a `Clock`").

Persistence is PostgreSQL (one schema, `auth`), Flyway DDL-only migrations (V1-V5, immutable).
Events publish through an outbox in the same transaction as the write; `AuditService.record(...)`
both persists an `AuditEvent` row and calls `outboxPublisher.publish(...)` to mirror it to Kafka —
this is the established pattern for every security-relevant action, and T18's `mfa.disabled`
(R28) and `mfa.failed` (R29) audit events will go through it identically.

## 2. Existing Code This Task Touches

**Already built, all read-only context for T18 (not modified):**
- `mfa.TotpGenerator` (T16) — `generateSecret()` (20 random bytes) and
  `buildProvisioningUri(byte[] secret, String accountLabel)`. No verification method — T16 only
  covers generation.
- `mfa.MfaSeedEncryption` (T16) — `encrypt(byte[])`/`decrypt(byte[])`, KMS-enveloped AES-GCM.
  `decrypt` is what T18 needs to recover the raw seed before verifying a submitted TOTP code.
- `mfa.MfaEnrollment` (T17) — entity with `create(...)`, `confirm(Instant)` (guarded, throws
  `IllegalStateException` if already confirmed), `recordUse(Instant)`, getters. No `Account`
  import (L12) — `accountId` is a plain `Long`.
- `mfa.MfaEnrollmentRepository` (T17, package-private) — `findAccountIdByUuid(UUID)` (native
  query resolving the internal id), `findByAccountIdAndType(Long, Type)`,
  `findByAccountIdAndTypeAndConfirmedAtIsNotNull(Long, Type)`, `deleteByAccountIdAndType(Long,
  Type)`.
- `mfa.RecoveryCode` (T17) — entity with `create(...)`; deliberately **no mutator for `usedAt`**.
- `mfa.RecoveryCodeRepository` (T17, package-private) — `findByAccountId`,
  `findByAccountIdAndUsedAtIsNull`, `findByAccountIdAndCodeHash`, and the atomic
  `markUsed(Long id, Instant usedAt)` (returns `0`/`1`, mirrors
  `VerificationTokenRepository.markConsumed`'s single-use-redemption pattern exactly).
- `account.AccountService.changePassword` — the established current-password-verification
  pattern T18's `disable` operation (R28) needs: `passwordEncoder.matches(currentPassword,
  account.getPasswordHash())`, throwing `CurrentPasswordMismatchException` on mismatch. `mfa`
  cannot import the `Account` entity (L12); whether T18 calls into `AccountService`'s existing
  public API (a cross-module *service* call, which L12 permits — it only forbids cross-module
  *entity* imports) or needs a new method exposed there is a Phase 2 design question.
- `audit.AuditService.record(RecordAuditEventRequest)` — persists + Kafka-mirrors one audit
  event. `RecordAuditEventRequest` fields: `eventType, outcome (AuditOutcome.SUCCESS/FAILURE),
  accountUuid, actorUuid, ip, rawUserAgent, traceId, details`.

**Not yet built, no code exists for any of it:**
- `mfa.MfaService` (this task) — begin-enroll, confirm, disable.
- `mfa.RecoveryCodeService` (this task, per `design.md` §6's file map) — generation +
  verification, separate from `MfaService` per that file tree, though the task statement bundles
  "recovery-code generation/verification" into the same "MFA service" sentence as
  enroll/confirm/disable — whether that means one class or two (as the file map implies) is a
  Phase 2 question.
- **TOTP *code* verification** (checking a submitted 6-digit code against the decrypted secret,
  HMAC-SHA1/RFC 6238) — needed for the "confirm" step (R23: "the user submits the correct first
  TOTP code") and for "disable" (R28: "...and a valid TOTP code"). `design.md` §6's file map lists
  a `TotpVerifier.java` under `mfa/`, but **no task statement in `tasks.md` names it explicitly** —
  it isn't its own numbered task. Since T18 is the first task that actually needs this capability,
  it's presumably part of T18's scope by necessity, not a separate task — flagged for explicit
  Phase 2 confirmation rather than assumed.

## 3. Established Patterns to Follow

- **Guarded state transitions**: `MfaEnrollment.confirm`/`Account`'s transition methods
  (`activateEmail`, `suspend`, etc.) throw on an invalid transition rather than silently no-op.
- **Single-use redemption is an atomic repository update, never a load-mutate-save cycle**:
  `RecoveryCodeRepository.markUsed`, `VerificationTokenRepository.markConsumed`. T18's
  recovery-code verification must call `markUsed` directly, not load a `RecoveryCode`, check
  `usedAt`, and save it back.
- **Password re-verification**: `AccountService.changePassword`'s
  `passwordEncoder.matches(...)` + dedicated mismatch exception.
- **Audit + Kafka mirror together**: always through `AuditService.record(...)`, never a bare
  repository save of an `AuditEvent`.
- **Exception-per-class, no shared hierarchy**: `RoleNotFoundException`, `AccountNotFoundException`,
  `CurrentPasswordMismatchException`, `MfaEncryptionException` — each a standalone
  `RuntimeException` subclass in its owning module.
- **`Clock` injected everywhere**, never `Instant.now()` inline in service code — matches how
  `AccountService`/`VerificationTokenService` are already built.

## 4. Testing Conventions

- Unit: plain JUnit, mocked repositories/collaborators, no Spring context (`LockoutServiceTest` is
  the closest precedent for a service class sitting above repositories this task will also use).
- Integration: `@SpringBootTest` + Testcontainers-Postgres, mirroring
  `LockoutPersistenceIntegrationTest`/T17's new `MfaPersistenceIntegrationTest`.
- **Testcontainers now actually works in this sandbox** (fixed during T17 — see
  [[docker-testcontainers-handshake-issue]]), but T17's own integration suite is currently blocked
  by a still-undiagnosed Hibernate issue that reproduces specifically when `MfaEnrollment`/
  `RecoveryCode` share the persistence unit with `Account` — i.e., **exactly the combination T18's
  own integration tests would need** (T18's service calls into `AccountService` for password
  verification, meaning `Account` and the `mfa` entities will coexist in the same test context).
  This is worth re-checking early in T18 rather than assuming it's still broken or assuming it's
  fixed — don't re-diagnose from scratch, that memory file has full detail on all three bugs found
  so far.

## 5. Known Gaps / Unknowns

- **`package.md` §8 numbering bug recurs a third time**: both of this task's named tests are
  mismapped — `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` → R20 (should be R23,
  the actual TOTP-confirm-and-recovery-codes requirement) and
  `shouldRequirePasswordAndTotpToDisableMfa` → R25 (R25 is about SAS login requiring TOTP/recovery
  code, not disabling MFA; R28 — "current password and a valid TOTP code to DELETE
  .../mfa/totp" — is the actual match). Same pre-existing, already-flagged, out-of-scope spec bug
  as T16/T17; not fixed here either.
- **O5 (recovery-code hashing primitive) is formally still an OPEN decision** in `design.md` §4b
  ("default to SHA-256 unless changed" — never marked Resolved, unlike O1/L14). In practice this
  is already decided by the schema and by T17's own code: `recovery_codes.code_hash CHAR(64)` and
  `RecoveryCode`'s Javadoc both assume a SHA-256 hex digest; bcrypt's own output format wouldn't
  fit a fixed 64-char column cleanly. Flagged for explicit Phase 2/4 confirmation rather than
  treated as silently pre-decided, but there is effectively only one workable answer given what
  T17 already committed to.
- **Whether "disable" (R28) accepts a recovery code as an alternative to a TOTP code.** R28's text
  says only "a valid TOTP code" (not "TOTP code or recovery code" the way R25's login-flow wording
  does) — worth confirming literally at Phase 1/2 rather than assuming symmetry with login.
- I do not know whether `TotpVerifier`/`RecoveryCodeService` should be separate classes (as
  `design.md`'s file tree implies) or folded into `MfaService` (as the task statement's single
  sentence could be read) — Phase 2 decision, not resolved here.
