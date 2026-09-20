# auth · T17 — Phase 4: Frozen Task Brief

**STATUS: FROZEN**

Approved by: femi (human approval gate) · 2026-08-03
Base: `artifacts/02-task-implementation-brief.md`, challenged in `artifacts/03-design-challenge.md`.
Downstream phases (5+) may not renegotiate this brief.

---

## Task

Implement `MfaEnrollment` and `RecoveryCode` JPA entities and their repositories, mapping the
existing `mfa_enrollments` and `recovery_codes` tables exactly.

## Purpose

Lays the persistence groundwork R22/R23 depend on. No service logic, no controller, no hashing or
encryption — those are tasks 18/19 and T16 (done).

## Scope

**In:**
- `MfaEnrollment` entity mapping `mfa_enrollments` exactly:
  - `id` (`Long`, `@GeneratedValue(IDENTITY)`).
  - `accountId` (`Long`, plain column, `updatable = false` — no `Account` relation, L12).
  - `type` — **an `MfaEnrollment.Type` enum** (`@Enumerated(EnumType.STRING)`, single `TOTP` value
    for now), mirroring `VerificationToken.Purpose`'s exact nested-enum pattern rather than a
    plain `String`. *(Amendment, Finding #8 — ACCEPTED.)* Same underlying `VARCHAR(16)` storage,
    no schema change.
  - `secretEncrypted` (`byte[]`, `@Column(nullable = false)`, maps `BYTEA`).
  - `confirmedAt`, `lastUsedAt` (nullable `Instant`).
  - `createdAt` (`Instant`, `nullable = false, updatable = false`), **caller-supplied at
    construction from the injected `Clock`, following `VerificationToken.create(...)`'s pattern —
    not `Account`'s `@PrePersist`/`Instant.now()` pattern, which the Phase 2 TIB cited incorrectly.**
    *(Amendment, Finding #4 — ACCEPTED; verified directly against `Account.java`'s actual
    `@PrePersist onCreate()` method, which the TIB had mischaracterized.)*
  - Two guarded mutators, matching `Account`'s guarded-transition style
    (`requireStatus`/`InvalidAccountStateException`):
    - `confirm(Instant confirmedAt)` — throws `IllegalStateException` if already confirmed
      (`confirmedAt != null`). Not a single-use race like recovery-code redemption (a one-shot
      user action, not attacker-repeatable), so a plain guarded entity mutator is appropriate —
      no atomic repository-level update needed here.
    - `recordUse(Instant lastUsedAt)` — unconditional, informational-only field, updated on every
      successful TOTP verification (task 20's concern to call, not this task's).
    *(Amendment, Finding #2 — ACCEPTED.)*
- `MfaEnrollmentRepository extends JpaRepository<MfaEnrollment, Long>`, package-private:
  - `findAccountIdByUuid(UUID)` — native query, exact copy of
    `LockoutStateRepository.findAccountIdByUuid`'s pattern.
  - `findByAccountIdAndType(Long, MfaEnrollment.Type)` — plain derived query, `Optional<MfaEnrollment>`.
- `RecoveryCode` entity mapping `recovery_codes` exactly: `id`, `accountId` (plain `Long`,
  `updatable = false`), `codeHash` (`String`, `@Column(nullable = false, length = 64)` — a
  SHA-256 hex digest per L6; not computed here), `usedAt` (nullable, **no mutator** — see below),
  `createdAt` (`Instant`, same caller-supplied-Clock convention as `MfaEnrollment`).
- `RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long>`, package-private:
  - `findByAccountId(Long)` → `List<RecoveryCode>`.
  - `findByAccountIdAndUsedAtIsNull(Long)` → `List<RecoveryCode>` — task 18's verification flow
    will need only unused codes. *(Amendment, Finding #9 — ACCEPTED.)*
  - **`markUsed(Long id, Instant usedAt)`** — `@Modifying @Query("UPDATE RecoveryCode r SET
    r.usedAt = :usedAt WHERE r.id = :id AND r.usedAt IS NULL")`, returning `int` rows affected.
    Mirrors `VerificationTokenRepository.markConsumed` exactly: single-use redemption is an atomic
    conditional `UPDATE`, never a load→set→save cycle, so concurrent redemption attempts on the
    same code cannot double-consume it. **`RecoveryCode` therefore has no `usedAt` mutator at
    all** — same reasoning `VerificationToken` states explicitly in its own Javadoc.
    *(Amendment, Finding #3 — ACCEPTED.)*
  - No UUID-resolution method on this repository — callers already hold `accountId` by the time
    they touch `RecoveryCode` (from an `MfaEnrollment` they just loaded/created). Confirmed as the
    intended design (not renegotiated at this gate).
- **"One confirmed enrollment per account" (task statement's second sentence) is satisfied by the
  existing `UNIQUE(account_id, type)` constraint alone — no new migration.** That constraint
  already guarantees at most one row per `(account_id, type)` regardless of confirmation state,
  which trivially implies at most one *confirmed* row as a corollary of the stronger "at most one
  row, period" guarantee. `design.md` §5's explicit "no breaking schema changes are required
  beyond V5" is the deciding signal that no new partial index was intended. Retrying an abandoned,
  unconfirmed enrollment is an UPDATE-in-place on the existing row (task 18's job, via
  `MfaEnrollment`'s existing fields — no new mutator added here since it wasn't asked for and
  would be speculative scope). *(Finding #1 — RESOLVED as "existing constraint already
  enforces it," not a silent narrowing: this reasoning is the explicit, human-confirmed
  resolution.)*
- **Recovery-code invalidation on MFA disable (R28, task 19) will necessarily be account-level**
  (`WHERE account_id = ...`), not enrollment-scoped, because `recovery_codes` has no FK to a
  specific enrollment. Documented here as an implication for later tasks; no code in this task
  implements it. *(Amendment, Finding #5 — ACCEPTED, informational only.)*
- **Test strategy: real `@SpringBootTest` + Testcontainers-Postgres integration tests**, mirroring
  `LockoutPersistenceIntegrationTest` exactly — the correct proof for persistence/native-query
  behavior. Confirmed Docker Desktop is not currently running in this sandbox (checked directly:
  `docker info`/`docker ps` both fail, socket file absent) — same class of limitation every prior
  task since T15 has hit, not a new problem this task must solve. *(Finding #7 — RESOLVED:
  Testcontainers-style tests are still the target; an embedded-H2 `@DataJpaTest` alternative was
  explicitly rejected as introducing a testing approach never used elsewhere in this
  Postgres-only service, risking false confidence against real Postgres-specific behavior
  including native-query dialect and `CITEXT`/`BYTEA` handling.)*

**Out:**
- No `MfaService`, `RecoveryCodeService`, `TotpVerifier`, or `MfaController` (tasks 18/19).
- No hashing logic (`code_hash` computation) — task 18's job; `RecoveryCode.codeHash` is a plain
  `String` field with no computation behind it here.
- No new Flyway migration — both tables are unchanged since V1 (confirmed above, Finding #1).
- No `FOR UPDATE` / pessimistic-locking repository variant for `MfaEnrollment` — no demonstrated
  concurrent-race comparable to `LockoutStateRepository`'s (login attempts are genuinely
  attacker-repeatable; a TOTP-enrollment confirm is a one-shot user action). `RecoveryCode`'s only
  real race (redemption) is already made safe by the atomic `markUsed` update above, without
  needing row locking. *(Finding #10 — REJECTED, reasoned above, not silently dropped.)*
- No change to `com.themistra.auth.mfa.package-info.java`, `TotpGenerator`, `MfaSeedEncryption`,
  or `MfaProperties` (T16, done, untouched).

## Business Rules

- **R22** (partial — persistence shape only).
- **R23** (partial — persistence shape only).

## Locked Decisions

- **L6.** Governs `RecoveryCode.codeHash`'s expected shape (SHA-256 hex, 64 chars).
- **L12** (widened into scope, as L14 was for T16). No import of
  `com.themistra.auth.account.Account` from either new entity or repository.

## Dependencies

- Spring Data JPA (`JpaRepository`) — existing dependency.
- `accounts.account_uuid` → `accounts.id`, via native query (mirrors
  `LockoutStateRepository.findAccountIdByUuid` exactly).
- No new config keys, no new contracts, no dependency on T16's classes.

## Inputs

- `MfaEnrollment`'s static factory: `accountId` (Long), `secretEncrypted` (byte[]), `createdAt`
  (Instant, caller/`Clock`-supplied).
- `RecoveryCode`'s static factory: `accountId` (Long), `codeHash` (String), `createdAt` (Instant,
  caller/`Clock`-supplied).
- `confirm`/`recordUse`: an `Instant` (caller/`Clock`-supplied).
- `markUsed`: `id` (Long), `usedAt` (Instant, caller/`Clock`-supplied).
- Repositories: standard `JpaRepository` inputs plus `accountUuid` (UUID) for the resolver query.

## Outputs

- Persisted `MfaEnrollment`/`RecoveryCode` rows, retrievable via the repositories' finder methods.
- `markUsed` returns `int` (0 or 1) rows affected — the caller's signal for "was this code
  actually consumed just now" vs. "already used/nonexistent."

## State Changes

Rows created/updated in `mfa_enrollments`/`recovery_codes` (both already exist, V1, unchanged).
No other table touched.

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
- No Flyway migration file — schema is unchanged (Finding #1's resolution).
- No controller, service, or DTO in `mfa/` — tasks 18/19.

## Acceptance Criteria

- **AC1 (R22).** `MfaEnrollment` maps every `mfa_enrollments` column with correct `@Column`
  attributes (length/nullable/updatable matching the DDL, per Finding #6); a new row persists
  with `confirmedAt` null and `secretEncrypted` populated.
- **AC2 (R23).** `MfaEnrollment.confirm(Instant)` sets `confirmedAt` in place and persists via
  normal JPA dirty-checking; calling it twice throws `IllegalStateException`.
- **AC3 (R23, L6).** `RecoveryCode` maps every `recovery_codes` column with correct `@Column`
  attributes; multiple rows (up to 10) persist per `accountId`, each with `usedAt` initially null.
- **AC4 (L12).** Neither entity nor repository imports `com.themistra.auth.account.Account`.
- **AC5 (task statement, Finding #1's resolution).** `MfaEnrollmentRepository.findByAccountIdAndType`
  makes "does this account already have an enrollment of this type" queryable; the DB's own
  `UNIQUE(account_id, type)` constraint is the actual enforcement mechanism, verified by a test
  that a second insert for the same `(account_id, type)` fails.
- **AC6.** `findAccountIdByUuid` returns empty for an unknown UUID.
- **AC7 (Finding #3).** `RecoveryCodeRepository.markUsed` only succeeds (returns 1) the first
  time for a given code; a second call against the same already-used code returns 0 and does not
  overwrite `usedAt`.
- **AC8 (Finding #9).** `findByAccountIdAndUsedAtIsNull` excludes already-used codes.
- **AC9 (Finding #8).** `MfaEnrollment.Type` round-trips correctly via `@Enumerated(EnumType.STRING)`
  (persisted value is the literal string `TOTP`, not an ordinal).

## Required Tests

- No named test is fully satisfiable by this task alone
  (`shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` needs tasks 18/19). T17's own
  suite covers AC1-AC9 via `@SpringBootTest` + Testcontainers-Postgres integration tests mirroring
  `LockoutPersistenceIntegrationTest` (Finding #7's resolution) — real schema, real constraint
  violations, real atomic-update race behavior. Whether these can actually execute in this
  specific sandbox depends on Docker availability at implementation time (currently down;
  confirmed at this gate, not a new finding).

## Constraints

- **Security:** `secretEncrypted` and `codeHash` are already-processed values by the time they
  reach this layer — neither entity logs either field.
- **Thread-safety:** not applicable — JPA entities aren't shared across threads; repositories are
  Spring Data proxies.
- **Transaction:** not exercised directly by this task — task 18 owns transaction boundaries
  around enroll/confirm/redeem. `markUsed`'s atomicity is guaranteed by the single `UPDATE`
  statement itself, independent of any surrounding transaction.
- **Module boundaries (L12):** the one binding constraint — see AC4.
- **Null handling:** `confirmedAt`/`lastUsedAt`/`usedAt` are all legitimately nullable and must
  stay so; no other null-argument case is in scope.

## Open Questions

None outstanding. All ten Phase 3 findings have an explicit disposition above.

## Phase 3 Findings — Disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | "Enforce one confirmed enrollment" narrowed to "queryable" | High | RESOLVED — existing `UNIQUE(account_id, type)` constraint already enforces it (stronger guarantee); no new migration, per design.md §5 |
| 2 | No mutators for `MfaEnrollment` confirm | Medium | ACCEPTED — `confirm(Instant)`/`recordUse(Instant)` added, guarded like `Account`'s transitions |
| 3 | `RecoveryCode.usedAt` race risk | Medium | ACCEPTED — atomic `markUsed` update, no entity mutator, mirrors `VerificationTokenRepository.markConsumed` |
| 4 | Wrong precedent cited (`Account` vs `VerificationToken`) | Low | ACCEPTED — verified `Account` uses `@PrePersist`; corrected to `VerificationToken.create(...)` |
| 5 | Recovery codes not enrollment-scoped | Medium | ACCEPTED — documented as an account-level-invalidation implication for R28/task 19 |
| 6 | JPA annotation detail underspecified | Low | ACCEPTED — exact `@Column` attributes specified above |
| 7 | No test strategy for JPA mapping without Testcontainers | Medium | RESOLVED — Testcontainers-style tests remain the target; embedded-H2 alternative explicitly rejected |
| 8 | `type` as `String` vs enum | Low | ACCEPTED — `MfaEnrollment.Type` enum, mirrors `VerificationToken.Purpose` |
| 9 | No "unused recovery codes" finder | Low | ACCEPTED — `findByAccountIdAndUsedAtIsNull` added |
| 10 | No `FOR UPDATE` variant | Low | REJECTED — no demonstrated race; `RecoveryCode`'s real race is already resolved by #3's atomic update |
