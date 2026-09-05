# auth · T17 — Phase 2: Task Implementation Brief

## Task

Implement `MfaEnrollment` and `RecoveryCode` JPA entities and their repositories, mapping the
existing `mfa_enrollments` and `recovery_codes` tables exactly, with the repository surface needed
to make "one confirmed enrollment per account" checkable by later tasks.

## Purpose

Lays the persistence groundwork R22/R23 depend on. No service logic, no controller, no hashing or
encryption — those are tasks 18/19 and T16 (done). T17's job is two mapped entities plus two
repositories that a future `MfaService` can build on.

## Scope

**In:**
- `MfaEnrollment` entity mapping `mfa_enrollments` exactly: `id`, `accountId` (plain `Long`, no
  `Account` relation — L12), `type` (defaults to `"TOTP"`), `secretEncrypted` (`byte[]`),
  `confirmedAt`/`lastUsedAt` (nullable `Instant`), `createdAt` (`Instant`, set at construction from
  a caller-supplied value, not `Instant.now()` — mirrors `Account`'s constructor pattern; the
  `Clock` itself lives in task 18's service layer, not here).
- `MfaEnrollmentRepository extends JpaRepository<MfaEnrollment, Long>`, package-private:
  - `findAccountIdByUuid(UUID)` — native query, exact copy of
    `LockoutStateRepository.findAccountIdByUuid`'s pattern, needed to resolve the FK before
    inserting a new row.
  - `findByAccountIdAndType(Long, String)` — plain derived query (JPQL against `MfaEnrollment`
    only, no join, no `Account` import) returning `Optional<MfaEnrollment>`; at most one row given
    the `UNIQUE(account_id, type)` constraint.
- `RecoveryCode` entity mapping `recovery_codes` exactly: `id`, `accountId` (plain `Long`),
  `codeHash` (`String`, expected to be a 64-character SHA-256 hex digest per L6 — the entity does
  not compute or validate the hash itself, only stores it), `usedAt` (nullable `Instant`),
  `createdAt` (`Instant`, same construction convention as `MfaEnrollment`).
- `RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long>`, package-private:
  - `findByAccountId(Long)` returning `List<RecoveryCode>` — plain derived query.
  - No UUID-resolution method here (see naming/ownership call below) — callers already hold the
    `accountId` by the time they touch `RecoveryCode` (from an `MfaEnrollment` they just
    loaded/created).
- **Naming/ownership call, flagged for explicit Phase 4 confirmation:** the UUID→`accountId`
  resolver lives only on `MfaEnrollmentRepository`, not duplicated on `RecoveryCodeRepository`,
  since `design.md` §5 names `MfaEnrollment` before `RecoveryCode` as jointly "own[ing] the TOTP
  lifecycle" and every realistic call sequence resolves/creates the enrollment first. This isn't
  spelled out anywhere in the spec — a judgment call, not a silent one.

**Out:**
- No `MfaService`, `RecoveryCodeService`, `TotpVerifier`, or `MfaController` (tasks 18/19).
- No hashing logic (`code_hash` computation) — L6 says only that hashes, not raw codes, are
  stored; producing that hash is task 18's job. `RecoveryCode.codeHash` is a plain `String` field
  with no computation behind it here.
- No new Flyway migration — both tables are unchanged since V1.
- No enforcement logic beyond what the schema + the two finder methods above make possible. A
  service-level "reject a second enrollment attempt" check is task 18's responsibility; T17 only
  makes that check *queryable*.
- No change to `com.themistra.auth.mfa.package-info.java`, `TotpGenerator`, `MfaSeedEncryption`,
  or `MfaProperties` (T16, done, untouched).

## Business Rules

- **R22** (partial — persistence shape only): an unconfirmed `MfaEnrollment` must be persistable.
- **R23** (partial — persistence shape only): a `MfaEnrollment` must be confirmable in place, and
  multiple `RecoveryCode` rows must be persistable per account.

## Locked Decisions

- **L6.** Governs `RecoveryCode.codeHash`'s expected shape (SHA-256 hex, 64 chars) — schema
  already reflects this (`CHAR(64)`); no entity-level validation added beyond what the column type
  itself constrains.
- **L12** (widened into scope here, as it was for T16's L14): no import of
  `com.themistra.auth.account.Account` from either new entity or repository.

## Dependencies

- Spring Data JPA (`JpaRepository`) — existing project dependency.
- `accounts.account_uuid` → `accounts.id`, via native query (mirrors
  `LockoutStateRepository.findAccountIdByUuid` exactly).
- No new config keys, no new contracts, no dependency on T16's classes (T17 stores
  already-encrypted/already-hashed values; it doesn't produce them).

## Inputs

- `MfaEnrollment`'s static factory: `accountId` (Long), `secretEncrypted` (byte[]), `createdAt`
  (Instant).
- `RecoveryCode`'s static factory: `accountId` (Long), `codeHash` (String), `createdAt` (Instant).
- Repositories: standard `JpaRepository` inputs plus `accountUuid` (UUID) for the resolver query.

## Outputs

- Persisted `MfaEnrollment`/`RecoveryCode` rows, retrievable via the repositories' finder methods.

## State Changes

Rows created in `mfa_enrollments`/`recovery_codes` (both already exist, V1, unchanged) — a new
row per enrollment attempt and up to 10 rows per confirmed enrollment. No other table touched.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollment.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollmentRepository.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCode.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCodeRepository.java`

## Files to Modify

None.

## Files NOT to Modify

- `com.themistra.auth.mfa.TotpGenerator`, `MfaSeedEncryption`, `MfaProperties`,
  `MfaEncryptionException`, `package-info.java` (T16, done).
- Anything under `.account`, `.authn`, `.authz`, `.apikey`, `.audit`, `.events`, `.token`,
  `.common` — no other module touched.
- No Flyway migration file — schema is unchanged.
- No controller, service, or DTO in `mfa/` — tasks 18/19.

## Acceptance Criteria

- **AC1 (R22).** `MfaEnrollment` maps every `mfa_enrollments` column; a new row persists with
  `confirmedAt` null and `secretEncrypted` populated.
- **AC2 (R23).** An `MfaEnrollment` row can be updated in place to set `confirmedAt`, without a
  new row (respects `UNIQUE(account_id, type)`).
- **AC3 (R23, L6).** `RecoveryCode` maps every `recovery_codes` column; multiple rows (up to 10)
  persist per `accountId`, each with `usedAt` initially null.
- **AC4 (L12).** Neither entity nor repository imports `com.themistra.auth.account.Account`.
- **AC5 (task statement).** `MfaEnrollmentRepository.findByAccountIdAndType` makes "does this
  account already have an enrollment of this type (confirmed or not)" directly queryable, backed
  by the DB's own `UNIQUE(account_id, type)` constraint as the hard enforcement mechanism.
- **AC6.** `findAccountIdByUuid` returns empty for an unknown UUID (mirrors
  `LockoutStateRepository`'s contract) rather than throwing.

## Required Tests

- No named test is fully satisfiable by this task alone
  (`shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` needs the confirm flow + recovery
  code generation from tasks 18/19; see Phase 1). T17's own suite covers AC1-AC6 at whatever level
  Phase 4 confirms (unit-level given this sandbox's Testcontainers limitation — see Open
  Questions).

## Constraints

- **Security:** `secretEncrypted` and `codeHash` are already-processed values by the time they
  reach this layer (T16 encrypts, task 18 hashes) — neither entity logs either field.
- **Thread-safety:** not applicable — JPA entities are not shared across threads; repositories are
  Spring Data proxies, inherently thread-safe.
- **Transaction:** not exercised directly by this task (no multi-step write sequence) — task 18
  owns transaction boundaries around enroll/confirm.
- **Module boundaries (L12):** the one binding constraint — see AC4.
- **Null handling:** `confirmedAt`/`lastUsedAt`/`usedAt` are all legitimately nullable and must
  stay so; no other null-argument case is in scope for a mapping-only task.

## Open Questions

No blockers. One test-scope question carried over from T16's own precedent: whether Testcontainers
is available for a real-Postgres integration test proving the native query and cascade behavior,
or whether (as with T16) the Docker/Testcontainers handshake issue in this sandbox forces a
unit-level-only proof. Flagged for explicit Phase 4 confirmation, not decided here.
