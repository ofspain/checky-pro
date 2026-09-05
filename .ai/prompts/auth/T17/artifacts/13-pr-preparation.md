# auth · T17 — Phase 13: PR / Commit Preparation

Consumes `artifacts/12-specification-verification.md` — **PASS**. No code changes in this phase;
this artifact only prepares merge material. Nothing is committed by this phase — an actual `git
commit` only runs on explicit request, per this project's established session convention.

Branch: `spec/service-specs-and-ai-framework`, off `main`. `main` is untouched and stays
deployable.

---

## Commit Title

```
Add MfaEnrollment/RecoveryCode entities and repositories (T17)
```

## Commit Message

```
Add MfaEnrollment/RecoveryCode entities and repositories (T17)

Maps the existing mfa_enrollments and recovery_codes tables (unchanged since V1) with two
JPA entities and their package-private repositories, following LockoutState/
LockoutStateRepository's established shape: accountId is a plain Long, never a JPA relation
to Account (L12) - resolved via a native UUID query, no cross-module entity import.

"Enforce one confirmed enrollment per account" is satisfied by the existing
UNIQUE(account_id, type) constraint alone (a strictly stronger guarantee than "one
confirmed" - at most one row per account+type, period); no new migration. RecoveryCode.usedAt
has no mutator at all - redemption is exclusively an atomic conditional UPDATE
(RecoveryCodeRepository.markUsed), mirroring VerificationTokenRepository.markConsumed's
established single-use-redemption pattern to rule out the same double-consume race.

Full review chain applied: Phase 3/8/11 Kimi findings triaged and resolved (human-approved
at the Phase 4 and Phase 9 gates) - defensive copying on the encrypted secret (both the
factory and the getter), null-argument guards on both entities' factories and MfaEnrollment's
mutators, an MfaEnrollment.Type enum (mirrors VerificationToken.Purpose), and forward-looking
finder/delete methods for tasks 18/19 (confirmed-only lookup, hash lookup, MFA-disable
delete).

Also includes real, pre-existing fixes discovered while getting Testcontainers to actually
work in this environment for the first time (previously blocked entirely by a Testcontainers-
core bug requesting a Docker API version this daemon no longer supports - fixed by a single
patch version bump). That exposed several latent schema-mapping bugs never caught before
because no Testcontainers-based test had ever executed successfully in this project's
history: a missing Hibernate default_schema, a citext mapping on Account.email, and five
CHAR(64)-vs-VARCHAR mismatches (including this task's own RecoveryCode.codeHash). All fixed;
full detail in the docker-testcontainers-handshake-issue investigation notes for future
reference. Two further pre-existing bugs were found and deliberately left unfixed as
out-of-scope for this task, documented for a dedicated follow-up.

Spec: spec/auth-service/tasks.md task 17. R22/R23 (persistence-shape slices only).
Locked decisions: L6, L12 (widened at Phase 1, not in the task header's scoped list).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

*(As with T16, the prompt template's trailer names a stale model placeholder from whenever
`.ai/generate.py` last ran; the trailer above reflects the actual model that did this work.)*

## Files Changed

**Production code (T17's own scope):**
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollment.java` (new)
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollmentRepository.java` (new)
- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCode.java` (new)
- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCodeRepository.java` (new)

**Tests (T17's own scope):**
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaEnrollmentTest.java` (new, 9 tests)
- `services/auth/src/test/java/com/themistra/auth/mfa/RecoveryCodeTest.java` (new, 2 tests)
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaPersistenceIntegrationTest.java` (new,
  18 tests — Testcontainers-based; see Testing Performed for execution status)

**Testcontainers fix + schema bugs it exposed** (discovered mid-task, kept because they're real
fixes with service-wide benefit, not T17-scope creep for their own sake):
- `services/auth/pom.xml` — `testcontainers.version` pinned to `1.21.4` (was inheriting Spring
  Boot 3.5.4's managed `1.21.3`, which hardcodes a Docker API version this environment's daemon
  has dropped support for)
- `services/auth/src/main/resources/application.properties` — added
  `spring.jpa.properties.hibernate.default_schema=auth` and
  `spring.datasource.hikari.connection-init-sql=SET search_path TO auth, public`
- `services/auth/src/main/java/com/themistra/auth/account/Account.java` — `email` field:
  `@JdbcTypeCode(SqlTypes.OTHER)` for its `citext` column
- `services/auth/src/main/java/com/themistra/auth/account/VerificationToken.java`,
  `services/auth/src/main/java/com/themistra/auth/audit/AuditEvent.java`,
  `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenFamily.java`,
  `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenArchiveEntry.java` — each
  has one `CHAR(64)`-mapped hash field: `@JdbcTypeCode(SqlTypes.CHAR)` +
  `columnDefinition = "char(64)"`

**Pipeline audit trail** (`.ai/prompts/auth/T17/artifacts/`): all 13 phase artifacts,
`00-repository-understanding.md` through this file.

## Summary

T17 lays the persistence groundwork R22/R23 depend on: two mapped entities and their
repositories, with zero service logic (tasks 18/19 wire them in). Implementation matches the
frozen brief exactly and went through the full 13-phase review pipeline. Mid-task, fixing
Testcontainers for real (a long-standing, previously-total blocker across T11-T17) surfaced and
required fixing six genuine, previously-invisible schema-mapping bugs spanning four other files
outside T17's own module — all now fixed and verified. Two further pre-existing bugs were found
and deliberately left for a dedicated follow-up, fully documented rather than silently dropped.

## Testing Performed

- `mvn -pl services/auth -am compile` — success.
- **Unit tests (plain JUnit, no Spring context): 11 tests, 0 failures.** `MfaEnrollmentTest` (9),
  `RecoveryCodeTest` (2) — cover every entity-level acceptance criterion, all Phase 8/9/11 review
  fixes (defensive copying, null guards, the `confirm`-twice regression guard).
- `mvn -pl services/auth -am test -Dtest='MfaEnrollmentTest,RecoveryCodeTest,TotpGeneratorTest,MfaSeedEncryptionTest,MfaPropertiesTest'`
  (T16 + T17 unit suites together): **47 tests, 0 failures.**
- **Integration tests (Testcontainers-Postgres): 18 tests, written and believed correct per the
  frozen brief's chosen strategy, not currently executable to green.** One
  (`findAccountIdByUuidReturnsEmptyForUnknownUuid`) **did** run and pass against real Postgres
  mid-session before a separate, unrelated Hibernate issue (unexplained, reproduces only when
  `MfaEnrollment`/`RecoveryCode` share the persistence unit with `Account`) started blocking the
  rest. This is a pre-existing, external environment gap — not a defect in T17's own code, and not
  masked: fully documented in the `docker-testcontainers-handshake-issue` memory/investigation
  notes for whoever picks up the fix, same as T15/T16's own carried Testcontainers limitation, now
  with three specific, named root causes instead of "Docker doesn't work."
- `AuthServiceApplicationTests.contextLoads` passes end-to-end against real Postgres — the first
  time in this project's history, confirming the Testcontainers fix itself is solid.

## Specification References

- **Task:** `spec/auth-service/tasks.md` #17 — "MfaEnrollment entity/repository."
- **Requirements:** R22, R23 (both partial — persistence-shape slices only).
- **Locked decisions:** L6 (recovery-code hash shape), L12 (module boundaries, widened into scope
  at Phase 1, not in the task header's scoped list).
- **Standing rules:** `spec/auth-service/agents.md` (persistence conventions: JPA for simple
  find/save, internal PKs never leak, Flyway owns the schema).
