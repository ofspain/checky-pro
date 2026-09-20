# auth · T17 — Phase 6: Implementation Notes

Consumes `artifacts/05-implementation-plan.md`. Four production files created, exactly as planned
— no test files (Phase 10's job).

## What Changed

- **`MfaEnrollment.java`** (new) — `@Entity` mapping `mfa_enrollments` exactly: `id`, `accountId`
  (plain `Long`, no `Account` relation), `type` (`MfaEnrollment.Type` enum,
  `@Enumerated(EnumType.STRING)`), `secretEncrypted` (`byte[]`), `confirmedAt`/`lastUsedAt`
  (nullable `Instant`), `createdAt` (`Instant`, caller-supplied). `create(...)` static factory;
  `confirm(Instant)` (throws `IllegalStateException` if already confirmed);
  `recordUse(Instant)`.
- **`MfaEnrollmentRepository.java`** (new) — package-private, `JpaRepository<MfaEnrollment, Long>`.
  `findAccountIdByUuid` (native query, exact copy of `LockoutStateRepository`'s pattern);
  `findByAccountIdAndType` (plain derived query).
- **`RecoveryCode.java`** (new) — `@Entity` mapping `recovery_codes` exactly: `id`, `accountId`
  (plain `Long`), `codeHash` (`String`, `length = 64`), `usedAt` (nullable, **no mutator**),
  `createdAt` (`Instant`, caller-supplied). `create(...)` static factory only.
- **`RecoveryCodeRepository.java`** (new) — package-private. `findByAccountId`,
  `findByAccountIdAndUsedAtIsNull` (derived queries); `markUsed` — `@Modifying @Query("UPDATE
  RecoveryCode r SET r.usedAt = :usedAt WHERE r.id = :id AND r.usedAt IS NULL")`, the atomic
  conditional update that is `RecoveryCode`'s *only* redemption path, mirroring
  `VerificationTokenRepository.markConsumed` exactly.

No file outside these four was touched. No Flyway migration (frozen brief's Finding #1
resolution — both tables unchanged since V1). No entity/repository imports
`com.themistra.auth.account.Account` (L12).

## Mapping to Plan and Acceptance Criteria

| AC | Where | Verified how |
|---|---|---|
| AC1 | `MfaEnrollment` fields/annotations | Manual JPA round-trip check (below): created with `confirmedAt` null, `secretEncrypted` intact |
| AC2 | `MfaEnrollment.confirm(Instant)` | Manual check: sets `confirmedAt` in place; second call throws `IllegalStateException` |
| AC3 | `RecoveryCode` fields/annotations | Manual check: multiple rows persist per `accountId`, `usedAt` initially null |
| AC4 | No `Account` import anywhere in `mfa/` | Confirmed by direct inspection of all four new files' imports |
| AC5 | `findByAccountIdAndType` + DB's `UNIQUE(account_id, type)` | Deferred to Phase 10 — needs a real constraint-violation test against Postgres |
| AC6 | `findAccountIdByUuid` returns empty for unknown UUID | Deferred to Phase 10 — needs the real `accounts` table (native query), not exercisable via the H2 check below |
| AC7 | `RecoveryCodeRepository.markUsed`'s atomic conditional update | Manual check: first call returns `1` and sets `usedAt`; second call on the same id returns `0` and leaves it unchanged |
| AC8 | `findByAccountIdAndUsedAtIsNull` | Manual check: correctly excludes a code already marked used |
| AC9 | `MfaEnrollment.Type` as `@Enumerated(STRING)` | Manual check: raw column value is the literal string `TOTP`, not an ordinal |

**Build verification:** `mvn -pl services/auth -am compile` succeeds.

**Manual verification beyond compilation:** since Testcontainers/Docker is unavailable in this
sandbox (see below), I built a throwaway, non-deliverable Spring Boot context (H2 in-memory,
`PostgreSQL` compatibility mode, `ddl-auto=create`, scoped only to `com.themistra.auth.mfa`) to
exercise real JPA persistence end-to-end before writing these notes: entity mapping, the
`confirm()` guard, the enum-as-string round trip, and — most importantly — `markUsed`'s atomic
conditional update all behaved exactly as designed. This did **not** exercise
`findAccountIdByUuid` (needs a real `accounts` table/native-query dialect, which H2 can't
faithfully stand in for) or the `UNIQUE(account_id, type)` constraint violation (schema-specific).
Those two remain genuinely unverified until either Testcontainers works or Phase 10's tests run
against real Postgres. This check was purely to build implementation-time confidence — it is not
the task's test artifact and doesn't change the frozen brief's Testcontainers-based test strategy.

## Testcontainers/Docker Investigation (this phase, off the original plan)

Before writing this implementation, Docker Desktop was confirmed running (unlike at Phase 4), so
I re-investigated the long-standing [[docker-testcontainers-handshake-issue]] rather than
assuming it was still simply "Docker is down." Findings, all now recorded in that memory file:

- Docker's daemon/socket are completely healthy — direct `curl --unix-socket` calls to `/info`,
  `/version`, `/_ping` all return clean `200 OK` with real data.
- The actual failure is `docker-java` (bundled in Testcontainers 1.21.3) getting an HTTP 400 whose
  body is a fully-formed but entirely blanked/zeroed `SystemInfo` JSON — a client/proxy
  interaction bug, not a connectivity problem.
- **Tried:** pinning `docker-java-api`/`docker-java-transport-zerodep` forward from 3.4.2 to 3.7.1
  via `dependencyManagement` in `services/auth/pom.xml` (confirmed via `dependency:tree` it
  actually took effect). **Result: no change** — byte-for-byte identical failure. Reverted this
  pin (confirmed via `git diff` it's back to the original state) since it provided no benefit and
  would only add unexplained complexity.
- femi explicitly declined the remaining option (a Testcontainers 2.0.x major-version jump,
  touching every existing integration test in the service) as disproportionate for this task.
  Testcontainers-based tests remain the frozen brief's stated target for Phase 10; whether they
  can execute in this specific sandbox is unresolved, same status as T15/T16.

This investigation happened but changed nothing about T17's own deliverables — noted here per this
phase's "flag deviations forced by reality" instruction, since real effort went into it mid-task
even though the net result was "confirmed still blocked, for a more precise reason than before."

## Deviations From the Plan

None. All four files match the Phase 5 plan's signatures exactly.

## Open Questions

None new. AC5/AC6's real-Postgres verification remains pending Phase 10, exactly as the frozen
brief anticipated when it resolved Finding #7.
