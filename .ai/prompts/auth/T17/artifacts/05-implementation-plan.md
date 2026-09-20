# auth · T17 — Phase 5: Implementation Plan

Consumes `artifacts/04-frozen-task-brief.md` (FROZEN). Plan only — no code in this artifact.

---

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollment.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollmentRepository.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCode.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCodeRepository.java`
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaPersistenceIntegrationTest.java`

All five trace directly to the frozen brief's Files-to-Create list plus its Required Tests
section (Testcontainers-style integration coverage). No file the brief doesn't authorize.

## Files to Modify

None — matches the frozen brief exactly.

## Public Methods (signatures)

**`MfaEnrollment`** (`@Entity @Table(name = "mfa_enrollments")`):
```java
public static MfaEnrollment create(Long accountId, Type type, byte[] secretEncrypted, Instant createdAt)
public void confirm(Instant confirmedAt)     // throws IllegalStateException if already confirmed
public void recordUse(Instant lastUsedAt)    // unconditional
public Long getId()
public Long getAccountId()
public Type getType()
public byte[] getSecretEncrypted()
public Instant getConfirmedAt()
public Instant getLastUsedAt()
public Instant getCreatedAt()

public enum Type { TOTP }
```
`create` mirrors `VerificationToken.create(...)`'s exact shape: caller-supplied `createdAt`
(sourced from a `Clock` at the call site — task 18's concern, not this task's), no
`Instant.now()` inline, no `@PrePersist`. `Type` is required explicitly (not defaulted), matching
`VerificationToken.Purpose`'s treatment even though only one value exists today.

**`MfaEnrollmentRepository`** (package-private, `interface ... extends JpaRepository<MfaEnrollment, Long>`):
```java
Optional<Long> findAccountIdByUuid(UUID accountUuid)                          // native query
Optional<MfaEnrollment> findByAccountIdAndType(Long accountId, MfaEnrollment.Type type)  // derived
```

**`RecoveryCode`** (`@Entity @Table(name = "recovery_codes")`):
```java
public static RecoveryCode create(Long accountId, String codeHash, Instant createdAt)
public Long getId()
public Long getAccountId()
public String getCodeHash()
public Instant getUsedAt()
public Instant getCreatedAt()
```
No `usedAt` mutator, per the frozen brief (Finding #3) — redemption is exclusively the
repository's atomic conditional update below.

**`RecoveryCodeRepository`** (package-private):
```java
List<RecoveryCode> findByAccountId(Long accountId)
List<RecoveryCode> findByAccountIdAndUsedAtIsNull(Long accountId)

@Modifying
@Query("UPDATE RecoveryCode r SET r.usedAt = :usedAt WHERE r.id = :id AND r.usedAt IS NULL")
int markUsed(Long id, Instant usedAt)   // returns 0 or 1
```

## Private Methods

None. Both entities are thin mappings (a factory + guarded/plain accessors); both repositories
are declarative Spring Data interfaces. No internal helper logic is needed anywhere in this task
— unlike T16's `MfaSeedEncryption`, there is no algorithmic work here.

## Entities Used

`MfaEnrollment` (new), `RecoveryCode` (new). Neither references `Account` (L12) — `accountId` is
a plain `Long`, resolved via `MfaEnrollmentRepository.findAccountIdByUuid`'s native query, the
same pattern `LockoutStateRepository` already established.

## Repositories Used

`MfaEnrollmentRepository` (new), `RecoveryCodeRepository` (new). Both package-private, both
`extends JpaRepository<..., Long>`.

## Services Used

None — T17 has no service layer (task 18's `MfaService`/`RecoveryCodeService` will be the first
consumers of these repositories).

## Unit / Integration Tests Required

Per the frozen brief's Finding #7 resolution, the primary proof is a Testcontainers-Postgres
integration test — plain-JUnit unit tests would need to fake the JPA/Hibernate layer to test
anything meaningful here, which proves nothing about real persistence behavior (exactly the gap
Finding #7 identified). One combined test class, mirroring `LockoutPersistenceIntegrationTest`'s
shape (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, `AccountService`
autowired to create a real account row to FK against):

**`MfaPersistenceIntegrationTest`**:
- `MfaEnrollment` maps every column correctly: a created row round-trips with `confirmedAt`/
  `lastUsedAt` null and `secretEncrypted` intact (AC1).
- `confirm(Instant)` persists `confirmedAt` in place via ordinary JPA dirty-checking (no new
  row); calling it a second time throws `IllegalStateException` (AC2).
- A second `MfaEnrollment` for the same `(accountId, TOTP)` violates the DB's
  `UNIQUE(account_id, type)` constraint (AC5 — the actual "one confirmed enrollment" enforcement
  mechanism per the frozen brief's Finding #1 resolution).
- `findAccountIdByUuid` returns empty for a random, non-existent UUID (AC6).
- `RecoveryCode` maps every column correctly; 10 rows for one account persist and are all
  returned by `findByAccountId` (AC3).
- `findByAccountIdAndUsedAtIsNull` excludes a code already marked used (AC8).
- `markUsed` returns `1` and sets `usedAt` the first time, `0` and leaves `usedAt` unchanged the
  second time against the same id (AC7) — this is the test that actually proves the atomic
  conditional update works as intended, the crux of Finding #3's fix.
- `MfaEnrollment.Type` persists and reloads as the literal string `TOTP`, not an ordinal (AC9) —
  assertable either via the mapped entity's `getType()` after a reload, or a native-query check
  of the raw column value.

Whether this test class can actually execute in this sandbox depends on Docker availability at
implementation time — confirmed down as of Phase 4; not a new decision, tracked the same way
T15/T16 tracked it.

## Execution Order

1. `MfaEnrollment.java` — no dependency on anything new; can be written and reasoned about
   standalone.
2. `MfaEnrollmentRepository.java` — depends on step 1.
3. `RecoveryCode.java` — independent of steps 1-2, but grouped next since it's the same task.
4. `RecoveryCodeRepository.java` — depends on step 3.
5. `MfaPersistenceIntegrationTest.java` — depends on all four; validates the whole task at once
   against a real schema, per the frozen brief's chosen test strategy.

No schema/migration step exists in this order (unlike a typical DAO-first task) because the
frozen brief's Finding #1 resolution settled that no new Flyway migration is needed — both tables
are unchanged since V1.

## Open Questions

None. All Phase 3 findings were already resolved at Phase 4; nothing new surfaced while planning.
